package itufp;

import java.io.*;
import java.math.BigDecimal;
import java.nio.file.*;
import java.nio.charset.StandardCharsets;
import java.util.*;

/** Input compatible with the uncertain .hui files used by the other miners. */
public record Database(int transactionCount, int itemCount, List<Occurrence> rows) {
    public record Occurrence(int tid, int item, BigDecimal probability) {}

    /** Group validated raw occurrences inside the measured method-specific preparation.
     * Positions are TID-1; missing TIDs remain empty, and no probabilities are changed. */
    public List<Map<Integer,BigDecimal>> group() {
        List<Map<Integer,BigDecimal>> tx = new ArrayList<>(Collections.nCopies(transactionCount, null));
        for (Occurrence r : rows) {
            int index=r.tid()-1;
            Map<Integer,BigDecimal> t=tx.get(index);
            if(t==null){t=new LinkedHashMap<>();tx.set(index,t);}
            t.put(r.item(),r.probability());
        }
        for(int i=0;i<tx.size();i++) if(tx.get(i)==null)tx.set(i,Map.of());
        return tx;
    }

    public static Database read(Path path) throws IOException {
        int n = -1, m = -1, lineNumber = 0;
        List<Occurrence> rows = new ArrayList<>();
        Set<Long> seen = new HashSet<>();
        Set<Integer> items = new HashSet<>();
        try (BufferedReader in = Files.newBufferedReader(path, StandardCharsets.UTF_8)) {
            for (String line; (line = in.readLine()) != null;) {
                ++lineNumber;
                line = line.trim();
                if (line.isEmpty() || line.startsWith("#")) continue;
                String[] f = line.split("\\s+");
                try {
                    if (n < 0) {
                        if (f.length != 2) throw new IllegalArgumentException("expected header n m");
                        n = Integer.parseInt(f[0]); m = Integer.parseInt(f[1]);
                        if (n < 0 || m < 0) throw new IllegalArgumentException("negative count");
                    } else {
                        if (f.length != 3) throw new IllegalArgumentException("expected TID item probability");
                        int tid = Integer.parseInt(f[0]), item = Integer.parseInt(f[1]);
                        BigDecimal p = new BigDecimal(f[2]);
                        if (tid < 1 || tid > n || p.signum() <= 0 || p.compareTo(BigDecimal.ONE) > 0)
                            throw new IllegalArgumentException("invalid TID or probability");
                        long key = ((long) tid << 32) | (item & 0xffffffffL);
                        if (!seen.add(key)) throw new IllegalArgumentException("duplicate (TID,item)");
                        items.add(item); rows.add(new Occurrence(tid,item,p));
                    }
                } catch (IllegalArgumentException e) {
                    throw new IOException(path+":"+lineNumber+": "+e.getMessage(),e);
                }
            }
        }
        if (n < 0 || items.size() != m) throw new IOException("Missing header or incorrect distinct-item count: "+path);
        return new Database(n,m,List.copyOf(rows));
    }
}
