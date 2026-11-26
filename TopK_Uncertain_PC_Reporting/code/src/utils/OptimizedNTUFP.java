package utils;

import DataObject.*;
import tools.MemoryLogger;
import java.math.*;
import java.util.*;

/** Optimized NTUFP (surrogate ExpSuppCap top-k miner).
 * Uses tenths-long exact arithmetic, flat SoA tree nodes, endPre intervals
 * with adaptive galloping match, and tighter anti-monotone branch envelope
 * U'(X) = min(sigma(anchor(X)), |T_X|). Falls back to TPNMiningEngine on
 * non-tenth decimals or long overflow.
 */
public final class OptimizedNTUFP {
    private static final int MAX_LONG_DEPTH = 18;

    private final int k;
    private TopKBuffer top;
    private PriorityQueue<Itemset> fallbackHeap;
    private MiningStats stats;
    private String backend = "tenths-long";
    private String fallbackReason = "";

    private Nodes nodes;
    private int[] matched = new int[64];
    private final long[][] powers = new long[11][19];
    private final long[] cachedThresholds = new long[MAX_LONG_DEPTH + 1];
    private BigDecimal cachedTopThreshold = null;

    public OptimizedNTUFP(int k) {
        if (k < 1) throw new IllegalArgumentException("k must be positive");
        this.k = k;
        for (int p = 0; p <= 10; p++) {
            powers[p][0] = 1;
            for (int d = 1; d < 19; d++) {
                powers[p][d] = Math.multiplyExact(powers[p][d - 1], p);
            }
        }
    }

    public ArrayList<Itemset> mine(Database db) {
        top = new TopKBuffer(k);
        fallbackHeap = null;
        stats = new MiningStats();
        cachedTopThreshold = null;
        try {
            return fastMine(db);
        } catch (ArithmeticException unsupported) {
            backend = "original-decimal-fallback";
            fallbackReason = unsupported.getMessage();
            nodes = null;
            top = null;
            TPNMiningEngine fallback = new TPNMiningEngine(k, false);
            ArrayList<Itemset> result = fallback.mine(db);
            stats = fallback.stats();
            fallbackHeap = fallback.heap();
            top = null;
            return result;
        }
    }

    private static final class Candidate {
        final Itemset items;
        final int[] blockNodes;
        final long envelope; // scale 10^1: min(sigmaTenths(anchor), |T_X| * 10)
        final int depth;

        Candidate(Itemset items, int[] blockNodes, long envelope, int depth) {
            this.items = items;
            this.blockNodes = blockNodes;
            this.envelope = envelope;
            this.depth = depth;
        }
    }

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
        int[] count = new int[1024], pre = new int[1024], endPre = new int[1024];
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
            count = Arrays.copyOf(count, n);
            pre = Arrays.copyOf(pre, n);
            endPre = Arrays.copyOf(endPre, n);
            cap = Arrays.copyOf(cap, n);
            proxy = Arrays.copyOf(proxy, n);
        }
    }

    private ArrayList<Itemset> fastMine(Database db) {
        Map<Integer, Long> sums = new HashMap<>();
        int n = Math.max(0, db.getDeclaredTransactions());
        for (Transaction r : db) {
            int u = r.getProbabilityDecimal().movePointRight(1).intValueExact();
            if (u < 1 || u > 10) throw new ArithmeticException("Probability is not an exact positive tenth");
            n = Math.max(n, r.getTid());
            sums.merge(r.getItemId(), (long) u, Math::addExact);
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
        for (int tid = 1; tid <= n; tid++) {
            int from = offsets[tid], to = offsets[tid + 1];
            Arrays.sort(rows, from, to);
            int current = 0, m1 = 0, m2 = 0, previous = -1;
            for (int at = from; at < to; at++) {
                int a = (int) (rows[at] >>> 8), u = (int) (rows[at] & 255);
                if (a == previous) throw new IllegalArgumentException("Duplicate TID/item");
                previous = a;
                long key = ((long) current << 32) | (a + 1L);
                int node = edges.get(key);
                if (node == 0) {
                    node = nodes.add(current, a);
                    edges.put(key, node);
                }
                nodes.cap[node] += u * (long) m1;
                nodes.proxy[node] = (byte) Math.max(nodes.proxy[node] & 255, m2);
                nodes.count[node]++;
                current = node;
                if (u >= m1) { m2 = m1; m1 = u; }
                else if (u > m2) m2 = u;
            }
        }

        int[] itemBlockCounts = new int[m];
        int[] nodeToItemBlock = new int[nodes.size];
        int node = 0, pre = 0;
        while (true) {
            nodes.pre[node] = pre++;
            if (node != 0) {
                int itemRank = nodes.rank[node];
                nodeToItemBlock[node] = itemBlockCounts[itemRank]++;
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

        int[][] itemBlockNodes = new int[m][];
        for (int a = 0; a < m; a++) itemBlockNodes[a] = new int[itemBlockCounts[a]];
        for (int v = 1; v < nodes.size; v++) {
            itemBlockNodes[nodes.rank[v]][nodeToItemBlock[v]] = v;
        }
        nodes.parent = null;
        nodes.first = null;
        nodes.next = null;

        List<Candidate> initial = new ArrayList<>(m);
        for (int a = 0; a < m; a++) {
            long sigma = sums.get(order.get(a));
            Itemset items = new Itemset(order.get(a));
            initial.add(new Candidate(items, itemBlockNodes[a], sigma, 1));
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
            if (a.envelope < threshold(1)) {
                stats.branchPrunes++;
                continue;
            }
            List<Candidate> next = new ArrayList<>();
            int depth = a.depth + 1;
            int exponent = depth - 2;
            if (exponent >= 19) throw new ArithmeticException("Fixed-point cap scale overflow");
            for (int j = i + 1; j < candidates.size(); j++) {
                Candidate b = candidates.get(j);
                if (b.envelope < threshold(1)) {
                    stats.branchPrunes++;
                    continue;
                }
                stats.metadataJoins++;
                int count = match(a.blockNodes, b.blockNodes);
                stats.metadataPairs += count;
                if (count == 0) continue;

                long score = 0;
                long txCount = 0;
                int[] matchedNodes = new int[count];
                int[] bNodes = b.blockNodes;
                for (int z = 0; z < count; z++) {
                    int node = bNodes[matched[z]];
                    matchedNodes[z] = node;
                    txCount += nodes.count[node];
                    long term = Math.multiplyExact(nodes.cap[node], powers[nodes.proxy[node] & 255][exponent]);
                    score = Math.addExact(score, term);
                }
                Itemset items = Itemset.extended(a.items, b.items.get(b.items.size() - 1));
                if (score > 0) {
                    top.offer(items, BigDecimal.valueOf(score, depth));
                    stats.heapOffers++;
                }
                long env = Math.min(b.envelope, Math.multiplyExact(txCount, 10L));
                if (env >= threshold(1)) {
                    next.add(new Candidate(items, matchedNodes, env, depth));
                }
            }
            MemoryLogger.getInstance().checkMemory();
            if (!next.isEmpty()) {
                long th = threshold(1);
                if (th > 0) {
                    next.removeIf(c -> c.envelope < th);
                }
                if (!next.isEmpty()) search(next);
            }
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

    public PriorityQueue<Itemset> heap() {
        if (top != null) return top.snapshotHeap();
        return fallbackHeap != null ? fallbackHeap : new PriorityQueue<>(ResultRanking.BEST_FIRST.reversed());
    }

    public MiningStats stats() {
        return stats;
    }

    public String backend() {
        return backend;
    }
}
