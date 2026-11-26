package itufp;
import java.io.*;
import java.math.BigDecimal;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.regex.*;

public final class Main {
    static final Pattern TOKEN=Pattern.compile("([+-]?\\d+)\\(([^()]+)\\)");
    static void put(Map<Integer,BigDecimal> t,String id,String prob) {
        int i=Integer.parseInt(id); BigDecimal p=new BigDecimal(prob);
        if(p.signum()<=0 || p.compareTo(BigDecimal.ONE)>0) throw new IllegalArgumentException("probability outside (0,1]");
        if(t.putIfAbsent(i,p)!=null) throw new IllegalArgumentException("duplicate item "+i);
    }
    /** Accepted formats: item(prob) tokens or items : probabilities, whitespace separated. */
    public static List<Map<Integer,BigDecimal>> load(Path file) throws IOException {
        List<Map<Integer,BigDecimal>> db=new ArrayList<>(); int lineNo=0;
        try(BufferedReader in=Files.newBufferedReader(file)) {
            String line; while((line=in.readLine())!=null) {
                lineNo++; line=line.strip();
                if(line.isEmpty()||line.startsWith("#")||line.startsWith("%")||line.startsWith("@")) continue;
                Map<Integer,BigDecimal> t=new LinkedHashMap<>();
                try {
                    if(line.contains(":")) {
                        String[] halves=line.split(":",-1);
                        if(halves.length!=2) throw new IllegalArgumentException("expected two fields");
                        String[] ids=halves[0].strip().split("\\s+"), ps=halves[1].strip().split("\\s+");
                        if(ids.length!=ps.length) throw new IllegalArgumentException("item/probability length mismatch");
                        for(int i=0;i<ids.length;i++) put(t,ids[i],ps[i]);
                    } else for(String s:line.split("\\s+")) {
                        Matcher m=TOKEN.matcher(s); if(!m.matches()) throw new IllegalArgumentException("invalid token "+s);
                        put(t,m.group(1),m.group(2));
                    }
                } catch(RuntimeException e) {throw new IllegalArgumentException("line "+lineNo+": "+e.getMessage(),e);}
                db.add(t);
            }
        }
        return db;
    }
    /** Same fixed-rate schedule and synchronized used-heap maximum as the historical miners. */
    static final class HeapSampler implements AutoCloseable {
        private long peak;
        final ScheduledExecutorService executor;
        HeapSampler() {
            check();
            executor=Executors.newSingleThreadScheduledExecutor(r->{Thread t=new Thread(r,"heap-sampler");t.setDaemon(true);return t;});
            executor.scheduleAtFixedRate(this::check,5,5,TimeUnit.MILLISECONDS);
        }
        synchronized void check(){Runtime r=Runtime.getRuntime();peak=Math.max(peak,r.totalMemory()-r.freeMemory());}
        synchronized long peak(){return peak;}
        public void close() throws InterruptedException {
            executor.shutdownNow();if(!executor.awaitTermination(5,TimeUnit.SECONDS))throw new IllegalStateException("Heap sampler did not stop");
        }
    }
    public static void main(String[] args) throws Exception {
        if(args.length!=3) {System.err.println("Usage: java -jar itufp.jar INPUT K[,K...] OUTPUT.tsv");System.exit(2);}
        int[] ks=Arrays.stream(args[1].split(",",-1)).mapToInt(Integer::parseInt).toArray();
        for(int k:ks) if(k<1)throw new IllegalArgumentException("invalid k");
        Path out=Path.of(args[2]); if(Files.exists(out)) throw new IOException("output exists: "+out);
        Path input=Path.of(args[0]);
        // The official benchmark requires the historical .hui format. Legacy text is demo-only.
        Database raw=input.toString().endsWith(".hui")?Database.read(input):null;
        var demo=raw==null?load(input):null;
        ITUFP solver=null;
        HeapSampler[] active=new HeapSampler[1];
        for(int idx=0;idx<ks.length;idx++) {
            int k=ks[idx]; Path target=ks.length==1?out:Path.of(out+".q"+(idx+1)+".k"+k+".tsv");
            if(Files.exists(target)) throw new IOException("output exists: "+target);
            HeapSampler sampler=new HeapSampler(); active[0]=sampler; List<ITUFP.Result> result; long elapsed;
            try(sampler) {
                long start=System.nanoTime();
                if(solver==null) solver=new ITUFP(raw==null?demo:raw.group(),()->active[0].check());
                result=solver.query(k); elapsed=System.nanoTime()-start; sampler.check();
            }
            try(BufferedWriter w=Files.newBufferedWriter(target,StandardOpenOption.CREATE_NEW)) {
                for(var r:result){w.write(String.join(" ",r.items().stream().map(String::valueOf).toList()));w.write('\t');w.write(r.support().stripTrailingZeros().toPlainString());w.newLine();}
            }
            System.out.printf(Locale.ROOT,"RESULT_BEGIN k=%d%nRuntime_seconds=%.9f%nHeap_MiB=%.9f%nSize=%d%n%s%nRESULT_END%n",k,elapsed/1e9,sampler.peak()/1048576.0,result.size(),solver.stats);
        }
    }
}
