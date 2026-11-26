package utils;

import DataObject.*;
import algo.TNUFPHybridSolution.TNUFPHybridAlgo;
import tools.MemoryLogger;
import java.math.*;
import java.util.*;

/** Exact TidNTUFP v2: unchanged canonical search and node ancestry semantics.
 * Uses flat CSR primitive arrays, int[] probabilities for depth <= 9,
 * pre-allocated scratch buffers, cached thresholds, endPre intervals with
 * adaptive galloping match, and original-decimal-fallback on overflow.
 */
public final class OptimizedTidNTUFP {
    private static final int MAX_LONG_DEPTH = 18;

    private final int k;
    private final String mode;
    private final boolean early;
    private final boolean capEnabled;
    private TopKBuffer top;
    private MiningStats stats;
    private String backend = "tenths-long";
    private String fallbackReason = "";
    private long bitmapBlocks, arrayBlocks, sharedTidReuses, earlyRejections, probabilityLookups;
    private long capPowerCacheHits;

    private Nodes nodes;
    private Column[] columns;
    private int[] treeTids;
    private byte[] treeProbs;
    private int[] nodeOffset;
    private long[][] nodeBitmaps;
    private int[] nodeBitmapBase;

    private int[] matched = new int[64];
    private int[] scratchNodes = new int[64];
    private int[] scratchOffsets = new int[65];
    private long[] scratchSums = new long[64];
    private int[] scratchQ32 = new int[1024];
    private long[] scratchQ64 = new long[1024];

    private final long[][] powers = new long[11][19];
    private final long[] cachedThresholds = new long[MAX_LONG_DEPTH + 1];
    private BigDecimal cachedTopThreshold = null;

    public OptimizedTidNTUFP(int k, String mode, boolean early) {
        this(k, mode, early, true);
    }

    public OptimizedTidNTUFP(int k, String mode, boolean early, boolean capEnabled) {
        if (k < 1) throw new IllegalArgumentException("k must be positive");
        if (!Set.of("array", "bitmap", "adaptive").contains(mode)) {
            throw new IllegalArgumentException("Unknown TID mode");
        }
        this.k = k;
        this.mode = mode;
        this.early = early;
        this.capEnabled = capEnabled;
        for (int p = 0; p <= 10; p++) {
            powers[p][0] = 1;
            for (int d = 1; d < 19; d++) {
                powers[p][d] = Math.multiplyExact(powers[p][d - 1], p);
            }
        }
    }

    public ArrayList<Itemset> mine(Database db) {
        top = new TopKBuffer(k);
        stats = new MiningStats();
        cachedTopThreshold = null;
        try {
            return fastMine(db);
        } catch (ArithmeticException unsupported) {
            backend = "original-decimal-fallback";
            fallbackReason = unsupported.getMessage();
            nodes = null;
            columns = null;
            treeTids = null;
            treeProbs = null;
            nodeOffset = null;
            nodeBitmaps = null;
            nodeBitmapBase = null;
            top = null;
            Constant.KValue = k;
            TNUFPHybridAlgo original = new TNUFPHybridAlgo();
            ArrayList<Itemset> result = original.work(db, null);
            stats = original.getStats();
            return result;
        }
    }

    private record Column(byte[] dense, int[] tids, byte[] values) {
        int get(int tid) {
            if (dense != null) return dense[tid] & 255;
            int at = Arrays.binarySearch(tids, tid);
            return at < 0 ? 0 : values[at] & 255;
        }
    }

    private static final class Candidate {
        final Itemset items;
        final int[] blockNodes;
        final int[] blockOffsets; // null at depth 1 (uses nodeOffset[blockNodes[z]])
        final int[] q32;          // non-null for 2 <= depth <= 9
        final long[] q64;         // non-null for depth >= 10
        final long[] blockSums;
        final long sum;
        final int depth;
        final int anchor;

        Candidate(Itemset items, int[] blockNodes, int[] blockOffsets, int[] q32, long[] q64,
                  long[] blockSums, long sum, int depth, int anchor) {
            this.items = items;
            this.blockNodes = blockNodes;
            this.blockOffsets = blockOffsets;
            this.q32 = q32;
            this.q64 = q64;
            this.blockSums = blockSums;
            this.sum = sum;
            this.depth = depth;
            this.anchor = anchor;
        }
    }

    /** Primitive edge dictionary; key is (parent node, dense item rank). */
    private static final class Edges {
        long[] keys = new long[1024];
        int[] values = new int[1024];
        int size;

        static int hash(long x) {
            x ^= x >>> 33;
            x *= 0xff51afd7ed558ccdl;
            x ^= x >>> 33;
            return (int) x;
        }

        int get(long key) {
            int mask = keys.length - 1, i = hash(key) & mask;
            while (keys[i] != 0) {
                if (keys[i] == key) return values[i];
                i = (i + 1) & mask;
            }
            return 0;
        }

        void put(long key, int value) {
            if ((size + 1) * 10L > keys.length * 7L) grow();
            int mask = keys.length - 1, i = hash(key) & mask;
            while (keys[i] != 0) i = (i + 1) & mask;
            keys[i] = key;
            values[i] = value;
            size++;
        }

        void grow() {
            long[] old = keys;
            int[] vals = values;
            keys = new long[old.length * 2];
            values = new int[keys.length];
            size = 0;
            for (int i = 0; i < old.length; i++) {
                if (old[i] != 0) put(old[i], vals[i]);
            }
        }
    }

    private static final class Nodes {
        int size = 1;
        int[] parent = new int[1024], first = new int[1024], next = new int[1024], rank = new int[1024];
        int[] head = new int[1024], tail = new int[1024], count = new int[1024];
        int[] pre = new int[1024], endPre = new int[1024];
        long[] cap = new long[1024];
        byte[] proxy = new byte[1024];

        int add(int par, int item) {
            if (size == parent.length) grow();
            int n = size++;
            parent[n] = par;
            rank[n] = item;
            next[n] = first[par];
            first[par] = n;
            return n;
        }

        void grow() {
            int n = parent.length * 2;
            parent = Arrays.copyOf(parent, n);
            first = Arrays.copyOf(first, n);
            next = Arrays.copyOf(next, n);
            rank = Arrays.copyOf(rank, n);
            head = Arrays.copyOf(head, n);
            tail = Arrays.copyOf(tail, n);
            count = Arrays.copyOf(count, n);
            pre = Arrays.copyOf(pre, n);
            endPre = Arrays.copyOf(endPre, n);
            cap = Arrays.copyOf(cap, n);
            proxy = Arrays.copyOf(proxy, n);
        }
    }

    private ArrayList<Itemset> fastMine(Database db) {
        Map<Integer, Long> sums = new HashMap<>();
        Map<Integer, Integer> frequencies = new HashMap<>();
        int n = Math.max(0, db.getDeclaredTransactions());
        for (Transaction r : db) {
            int u = r.getProbabilityDecimal().movePointRight(1).intValueExact();
            if (u < 1 || u > 10) throw new ArithmeticException("Probability is not an exact positive tenth");
            n = Math.max(n, r.getTid());
            sums.merge(r.getItemId(), (long) u, Math::addExact);
            frequencies.merge(r.getItemId(), 1, Integer::sum);
        }
        if (db.getDeclaredItems() >= 0 && sums.size() != db.getDeclaredItems()) {
            throw new IllegalArgumentException("Wrong distinct item count");
        }
        List<Integer> order = new ArrayList<>(sums.keySet());
        order.sort((a, b) -> {
            int c = Long.compare(sums.get(b), sums.get(a));
            return c != 0 ? c : Integer.compare(a, b);
        });
        Map<Integer, Integer> ranks = new HashMap<>();
        for (int i = 0; i < order.size(); i++) ranks.put(order.get(i), i);
        int m = order.size();
        columns = new Column[m];
        boolean dense = ((long) m * (n + 1L)) <= 256L * 1024 * 1024;
        int[] columnAt = new int[m];
        for (int a = 0; a < m; a++) {
            columns[a] = dense
                ? new Column(new byte[n + 1], null, null)
                : new Column(null, new int[frequencies.get(order.get(a))], new byte[frequencies.get(order.get(a))]);
        }

        int[] offsets = new int[n + 2];
        for (Transaction r : db) {
            if (r.getTid() < 1 || (db.getDeclaredTransactions() >= 0 && r.getTid() > db.getDeclaredTransactions())) {
                throw new IllegalArgumentException("TID outside header");
            }
            offsets[r.getTid() + 1]++;
        }
        for (int t = 1; t < offsets.length; t++) offsets[t] += offsets[t - 1];
        int[] fill = offsets.clone();
        long[] rows = new long[db.size()];
        for (Transaction r : db) {
            int a = ranks.get(r.getItemId());
            int u = r.getProbabilityDecimal().movePointRight(1).intValueExact();
            rows[fill[r.getTid()]++] = ((long) a << 8) | u;
        }

        nodes = new Nodes();
        Edges edges = new Edges();
        int[] nextRecord = new int[rows.length];
        int[] recordTid = new int[rows.length];
        for (int tid = 1; tid <= n; tid++) {
            int from = offsets[tid], to = offsets[tid + 1];
            Arrays.sort(rows, from, to);
            int current = 0, m1 = 0, m2 = 0, previous = -1;
            for (int at = from; at < to; at++) {
                int a = (int) (rows[at] >>> 8), u = (int) (rows[at] & 255);
                if (a == previous) throw new IllegalArgumentException("Duplicate TID/item");
                previous = a;
                Column col = columns[a];
                if (col.dense != null) col.dense[tid] = (byte) u;
                else {
                    int ci = columnAt[a]++;
                    col.tids[ci] = tid;
                    col.values[ci] = (byte) u;
                }
                long key = ((long) current << 32) | (a + 1L);
                int node = edges.get(key);
                if (node == 0) {
                    node = nodes.add(current, a);
                    edges.put(key, node);
                }
                nodes.cap[node] += u * (long) m1;
                nodes.proxy[node] = (byte) Math.max(nodes.proxy[node] & 255, m2);
                recordTid[at] = tid;
                if (nodes.head[node] == 0) nodes.head[node] = at + 1;
                else nextRecord[nodes.tail[node] - 1] = at + 1;
                nodes.tail[node] = at + 1;
                nodes.count[node]++;
                current = node;
                if (u >= m1) { m2 = m1; m1 = u; }
                else if (u > m2) m2 = u;
            }
        }

        // Flatten tree TIDs and base probabilities into global CSR arrays in preorder.
        int[] itemBlockCounts = new int[m];
        int[] nodeToItemBlock = new int[nodes.size];
        treeTids = new int[rows.length];
        treeProbs = new byte[rows.length];
        nodeOffset = new int[nodes.size + 1];
        boolean allowBitmap = !mode.equals("array");
        if (allowBitmap) {
            nodeBitmaps = new long[nodes.size][];
            nodeBitmapBase = new int[nodes.size];
        }

        int node = 0, pre = 0, tidCursor = 0;
        while (true) {
            nodes.pre[node] = pre++;
            if (node != 0) {
                int itemRank = nodes.rank[node];
                nodeToItemBlock[node] = itemBlockCounts[itemRank]++;
                nodeOffset[node] = tidCursor;
                int firstTid = 0, lastTid = 0, cnt = nodes.count[node];
                for (int r = nodes.head[node]; r != 0; r = nextRecord[r - 1]) {
                    int tid = recordTid[r - 1];
                    if (tidCursor == nodeOffset[node]) firstTid = tid;
                    lastTid = tid;
                    treeTids[tidCursor] = tid;
                    treeProbs[tidCursor] = (byte) (rows[r - 1] & 255);
                    tidCursor++;
                }
                int span = cnt == 0 ? 0 : lastTid - firstTid + 1;
                int words = (int) ((span + 63L) / 64);
                boolean useBitmap = mode.equals("bitmap") || (mode.equals("adaptive") && cnt >= 64 && 8L * words <= 2L * cnt);
                if (useBitmap) {
                    long[] bits = new long[words];
                    for (int kIdx = nodeOffset[node]; kIdx < tidCursor; kIdx++) {
                        int p = treeTids[kIdx] - firstTid;
                        bits[p >>> 6] |= 1L << (p & 63);
                    }
                    nodeBitmaps[node] = bits;
                    nodeBitmapBase[node] = firstTid;
                    bitmapBlocks++;
                } else {
                    arrayBlocks++;
                }
            }
            if (nodes.first[node] != 0) {
                node = nodes.first[node];
                continue;
            }
            while (true) {
                nodes.endPre[node] = pre - 1;
                if (node == 0) break;
                if (nodes.next[node] != 0) {
                    node = nodes.next[node];
                    break;
                }
                node = nodes.parent[node];
            }
            if (node == 0) break;
        }

        // Fix nodeOffsetEnd: since node IDs are allocation-order (not preorder), store end offset separately or build nodeEnd!
        // Wait: nodeOffset[node] is start, and count[node] is length! Let's store nodeEnd[node] = nodeOffset[node] + count[node]
        // by storing count in nodeEnd before nulling count, OR simply keeping nodeEnd[].
        int[] nodeEnd = new int[nodes.size];
        for (int v = 1; v < nodes.size; v++) nodeEnd[v] = nodeOffset[v] + nodes.count[v];

        // Build per-item blockNodes and blockSums in preorder!
        int[][] itemBlockNodes = new int[m][];
        long[][] itemBlockSums = new long[m][];
        for (int a = 0; a < m; a++) {
            itemBlockNodes[a] = new int[itemBlockCounts[a]];
            itemBlockSums[a] = new long[itemBlockCounts[a]];
        }
        // Sort nodes 1..nodes.size-1 by preorder? Actually nodeToItemBlock[v] is already in preorder!
        // Even simpler: compute blockSum during DFS or iterate v=1..nodes.size-1 and place at nodeToItemBlock[v]!
        for (int v = 1; v < nodes.size; v++) {
            int a = nodes.rank[v];
            int idx = nodeToItemBlock[v];
            itemBlockNodes[a][idx] = v;
            long s = 0;
            int end = nodeEnd[v];
            for (int pIdx = nodeOffset[v]; pIdx < end; pIdx++) s += treeProbs[pIdx] & 255;
            itemBlockSums[a][idx] = s;
        }
        // Repack nodeOffset & nodeEnd? Actually nodeEnd[v] can be stored in nodes.count[v] (keep nodes.count)!
        // Let's keep nodes.count as length, and null out only build-only arrays:
        nodes.parent = null;
        nodes.first = null;
        nodes.next = null;
        nodes.head = null;
        nodes.tail = null;

        List<Candidate> initial = new ArrayList<>(m);
        for (int a = 0; a < m; a++) {
            long sigma = sums.get(order.get(a));
            Itemset items = new Itemset(order.get(a));
            initial.add(new Candidate(items, itemBlockNodes[a], null, null, null, itemBlockSums[a], sigma, 1, a));
            top.offer(items, BigDecimal.valueOf(sigma, 1));
            stats.heapOffers++;
        }
        MemoryLogger.getInstance().checkMemory();
        search(initial);
        MemoryLogger.getInstance().checkMemory();
        return top.sortedResults();
    }

    private long threshold(int depth) {
        BigDecimal t = top.threshold();
        if (t != cachedTopThreshold) {
            cachedTopThreshold = t;
            if (t.signum() == 0) {
                Arrays.fill(cachedThresholds, 0L);
            } else {
                for (int d = 1; d <= MAX_LONG_DEPTH; d++) {
                    try {
                        cachedThresholds[d] = t.movePointRight(d).setScale(0, RoundingMode.CEILING).longValueExact();
                    } catch (ArithmeticException overflow) {
                        cachedThresholds[d] = -1L;
                    }
                }
            }
        }
        if (depth > MAX_LONG_DEPTH) {
            return t.movePointRight(depth).setScale(0, RoundingMode.CEILING).longValueExact();
        }
        long val = cachedThresholds[depth];
        if (val < 0) throw new ArithmeticException("Threshold scale overflow");
        return val;
    }

    private void search(List<Candidate> candidates) {
        for (int i = 0; i < candidates.size(); i++) {
            Candidate a = candidates.get(i);
            stats.maxDepth = Math.max(stats.maxDepth, a.depth);
            if (a.sum < threshold(a.depth)) {
                stats.branchPrunes++;
                continue;
            }
            List<Candidate> next = new ArrayList<>();
            int depth = a.depth + 1;
            for (int j = i + 1; j < candidates.size(); j++) {
                Candidate b = candidates.get(j);
                if (b.sum < threshold(b.depth)) {
                    stats.branchPrunes++;
                    continue;
                }
                long theta = threshold(depth);
                stats.metadataJoins++;
                stats.eligiblePairs++;
                int count = match(a.blockNodes, b.blockNodes);
                stats.metadataPairs += count;
                if (count == 0) continue;
                if (capEnabled) stats.capChecks++;
                if (capEnabled && !capPass(b.blockNodes, count, depth, theta)) {
                    stats.capRejections++;
                    stats.capRejectedPairs += count;
                    continue;
                }
                stats.exactCandidateJoins++;
                Candidate joined = materialize(a, b, count, depth, theta);
                if (joined == null) continue;
                top.offer(joined.items, BigDecimal.valueOf(joined.sum, depth));
                stats.heapOffers++;
                next.add(joined);
            }
            MemoryLogger.getInstance().checkMemory();
            if (!next.isEmpty()) search(next);
        }
    }

    private int match(int[] parent, int[] child) {
        int pLen = parent.length, cLen = child.length;
        if (pLen == 0 || cLen == 0) return 0;
        if ((pLen << 3) < cLen) return matchGallopChild(parent, child);
        if ((cLen << 3) < pLen) return matchGallopParent(parent, child);

        int i = 0, j = 0, count = 0;
        int[] pre = nodes.pre, endPre = nodes.endPre;
        while (i < cLen && j < pLen) {
            int c = child[i], p = parent[j];
            int cPre = pre[c], pPre = pre[p];
            if (cPre <= pPre) {
                i++;
            } else if (cPre <= endPre[p]) {
                if (count == matched.length) matched = Arrays.copyOf(matched, count * 2);
                matched[count++] = i++;
            } else {
                j++;
            }
        }
        return count;
    }

    private int matchGallopChild(int[] parent, int[] child) {
        int pLen = parent.length, cLen = child.length, i = 0, count = 0;
        int[] pre = nodes.pre, endPre = nodes.endPre;
        for (int j = 0; j < pLen && i < cLen; j++) {
            int p = parent[j];
            int pPre = pre[p], pEnd = endPre[p];
            if (pre[child[i]] <= pPre) {
                // Gallop to first child with pre > pPre
                int step = 1;
                while (i + step < cLen && pre[child[i + step]] <= pPre) {
                    i += step;
                    step <<= 1;
                }
                int lo = i + 1, hi = Math.min(cLen, i + step + 1);
                while (lo < hi) {
                    int mid = (lo + hi) >>> 1;
                    if (pre[child[mid]] <= pPre) lo = mid + 1;
                    else hi = mid;
                }
                i = lo;
            }
            while (i < cLen && pre[child[i]] <= pEnd) {
                if (count == matched.length) matched = Arrays.copyOf(matched, count * 2);
                matched[count++] = i++;
            }
        }
        return count;
    }

    private int matchGallopParent(int[] parent, int[] child) {
        int pLen = parent.length, cLen = child.length, j = 0, count = 0;
        int[] pre = nodes.pre, endPre = nodes.endPre;
        for (int i = 0; i < cLen && j < pLen; i++) {
            int cPre = pre[child[i]];
            if (cPre <= pre[parent[j]]) continue;
            // Advance j to the largest index with pre[parent[j]] < cPre
            if (j + 1 < pLen && pre[parent[j + 1]] < cPre) {
                int step = 1;
                while (j + step < pLen && pre[parent[j + step]] < cPre) {
                    j += step;
                    step <<= 1;
                }
                int lo = j, hi = Math.min(pLen - 1, j + step);
                while (lo < hi) {
                    int mid = (lo + hi + 1) >>> 1;
                    if (pre[parent[mid]] < cPre) lo = mid;
                    else hi = mid - 1;
                }
                j = lo;
            }
            if (cPre <= endPre[parent[j]]) {
                if (count == matched.length) matched = Arrays.copyOf(matched, count * 2);
                matched[count++] = i;
            }
        }
        return count;
    }

    private boolean capPass(int[] childNodes, int count, int depth, long theta) {
        if (theta == 0) return true;
        int exponent = depth - 2;
        if (exponent >= 19) throw new ArithmeticException("Fixed-point cap scale overflow");
        long cap = 0;
        for (int z = 0; z < count; z++) {
            int node = childNodes[matched[z]];
            capPowerCacheHits++;
            long base = nodes.cap[node], weight = powers[nodes.proxy[node] & 255][exponent], needed = theta - cap;
            if (weight > 0 && base >= (needed - 1) / weight + 1) return true;
            cap += base * weight;
        }
        return false;
    }

    private Candidate materialize(Candidate a, Candidate b, int count, int depth, long theta) {
        if (scratchNodes.length < count) {
            scratchNodes = new int[count];
            scratchOffsets = new int[count + 1];
            scratchSums = new long[count];
        }
        boolean check = early && theta > 0;
        long upper = 0;
        if (check) {
            for (int z = 0; z < count; z++) upper = Math.addExact(upper, b.blockSums[matched[z]]);
            upper = Math.multiplyExact(upper, 10);
            if (upper < theta) {
                earlyRejections++;
                return null;
            }
        }

        boolean use32 = depth <= 9;
        Column original = columns[a.anchor];
        int[] bNodes = b.blockNodes, bOffsets = b.blockOffsets;
        int[] bQ32 = b.q32;
        long[] bQ64 = b.q64;
        long total = 0;
        int qPos = 0;

        for (int z = 0; z < count; z++) {
            int mIdx = matched[z];
            int node = bNodes[mIdx];
            int cnt = nodes.count[node];
            scratchNodes[z] = node;
            scratchOffsets[z] = qPos;
            if (use32 && qPos + cnt > scratchQ32.length) {
                scratchQ32 = Arrays.copyOf(scratchQ32, Math.max(scratchQ32.length * 2, qPos + cnt));
            } else if (!use32 && qPos + cnt > scratchQ64.length) {
                scratchQ64 = Arrays.copyOf(scratchQ64, Math.max(scratchQ64.length * 2, qPos + cnt));
            }

            stats.tidPairMerges++;
            sharedTidReuses++;
            long blockSum = 0;
            int bStart = bOffsets == null ? nodeOffset[node] : bOffsets[mIdx];
            int tidStart = nodeOffset[node];
            long[] bits = nodeBitmaps == null ? null : nodeBitmaps[node];

            if (bits == null) {
                for (int at = 0; at < cnt; at++) {
                    int tid = treeTids[tidStart + at];
                    int p = original.get(tid);
                    if (p == 0) throw new IllegalStateException("Broken prefix/TID subset invariant");
                    long qc = bOffsets == null ? (treeProbs[bStart + at] & 255L) : (bQ32 != null ? bQ32[bStart + at] : bQ64[bStart + at]);
                    long value = Math.multiplyExact(qc, p);
                    if (use32) scratchQ32[qPos + at] = (int) value;
                    else scratchQ64[qPos + at] = value;
                    blockSum = Math.addExact(blockSum, value);
                    if (check) upper -= Math.multiplyExact(qc, 10 - p);
                    stats.tidRecordsMerged++;
                    stats.probabilityMultiplications++;
                    probabilityLookups++;
                    if (check && ((at + 1) & 63) == 0 && upper < theta) {
                        earlyRejections++;
                        return null;
                    }
                }
            } else {
                int base = nodeBitmapBase[node], at = 0;
                for (int wi = 0; wi < bits.length; wi++) {
                    long word = bits[wi];
                    while (word != 0) {
                        int bit = Long.numberOfTrailingZeros(word);
                        int tid = base + (wi << 6) + bit;
                        word &= word - 1;
                        int p = original.get(tid);
                        if (p == 0) throw new IllegalStateException("Broken prefix/TID subset invariant");
                        long qc = bOffsets == null ? (treeProbs[bStart + at] & 255L) : (bQ32 != null ? bQ32[bStart + at] : bQ64[bStart + at]);
                        long value = Math.multiplyExact(qc, p);
                        if (use32) scratchQ32[qPos + at] = (int) value;
                        else scratchQ64[qPos + at] = value;
                        blockSum = Math.addExact(blockSum, value);
                        if (check) upper -= Math.multiplyExact(qc, 10 - p);
                        at++;
                        stats.tidRecordsMerged++;
                        stats.probabilityMultiplications++;
                        probabilityLookups++;
                        if (check && (at & 63) == 0 && upper < theta) {
                            earlyRejections++;
                            return null;
                        }
                    }
                }
            }

            qPos += cnt;
            scratchSums[z] = blockSum;
            total = Math.addExact(total, blockSum);
            if (check && upper < theta) {
                earlyRejections++;
                return null;
            }
        }
        scratchOffsets[count] = qPos;
        if (total < theta || total == 0) return null;

        return new Candidate(
            Itemset.extended(a.items, b.items.get(b.items.size() - 1)),
            Arrays.copyOf(scratchNodes, count),
            Arrays.copyOf(scratchOffsets, count + 1),
            use32 ? Arrays.copyOf(scratchQ32, qPos) : null,
            use32 ? null : Arrays.copyOf(scratchQ64, qPos),
            Arrays.copyOf(scratchSums, count),
            total,
            depth,
            b.anchor
        );
    }

    private static String quote(String s) {
        return "\"" + s.replace("\\", "\\\\").replace("\"", "\\\"").replace("\n", "\\n") + "\"";
    }

    public String statsJson() {
        String base = stats.toJson();
        return base.substring(0, base.length() - 1)
            + ",\"backend\":" + quote(backend)
            + ",\"fallbackReason\":" + quote(fallbackReason == null ? "" : fallbackReason)
            + ",\"tidMode\":" + quote(mode)
            + ",\"earlyEnabled\":" + early
            + ",\"capEnabled\":" + capEnabled
            + ",\"bitmapBlocks\":" + bitmapBlocks
            + ",\"arrayBlocks\":" + arrayBlocks
            + ",\"sharedTidReuses\":" + sharedTidReuses
            + ",\"earlyRejections\":" + earlyRejections
            + ",\"probabilityLookups\":" + probabilityLookups
            + ",\"capPowerCacheHits\":" + capPowerCacheHits
            + "}";
    }
}
