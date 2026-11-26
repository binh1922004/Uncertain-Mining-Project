package algo;
import DataObject.*;
import IO.Input;
import IO.Output;
import utils.*;
import tools.MemoryLogger;
import algo.TUFP.TUFPAlgo;
import itufp.ITUFP;
import java.math.BigDecimal;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.*;
import java.io.*;

/** One-shot benchmark. Common raw input validation, timing and sampler. */
public final class UnifiedBenchmark {
    public static void main(String[] args) throws Exception {
        if (args.length != 8) throw new IllegalArgumentException("ALGO INPUT K OUTPUT MODE EARLY CAP SAMPLE_MS");
        String algo=args[0],mode=args[4]; int k=Integer.parseInt(args[2]),ms=Integer.parseInt(args[7]);
        boolean early=Boolean.parseBoolean(args[5]),cap=Boolean.parseBoolean(args[6]);
        if(k<1||ms<1||!Set.of("true","false").contains(args[5])||!Set.of("true","false").contains(args[6]))throw new IllegalArgumentException("Bad options");
        Path out=Path.of(args[3]); if(Files.exists(out))throw new IOException("Output exists");
        Constant.KValue=k; Database db=new Input().input(args[1]);
        MemoryLogger mem=MemoryLogger.getInstance(); mem.reset();mem.checkMemory();
        var sampler=Executors.newSingleThreadScheduledExecutor(r->{Thread t=new Thread(r,"heap-sampler");t.setDaemon(true);return t;});
        sampler.scheduleAtFixedRate(mem::checkMemory,ms,ms,TimeUnit.MILLISECONDS);
        ArrayList<Itemset> results=null;List<ITUFP.Result> ituResults=null;String stats,backend;double seconds;
        long started=System.nanoTime();
        try {
            switch(algo) {
                case "NTUFP" -> {var solver=new OptimizedNTUFP(k);results=solver.mine(db);seconds=(System.nanoTime()-started)/1e9;stats=solver.stats().toJson();backend=solver.backend();}
                case "TidNTUFP" -> {var solver=new OptimizedTidNTUFP(k,mode,early,cap);results=solver.mine(db);seconds=(System.nanoTime()-started)/1e9;stats=solver.statsJson();backend=stats.contains("original-decimal-fallback")?"original-decimal-fallback":"tenths-long";}
                case "TUFP" -> {var solver=new TUFPAlgo(db);results=solver.getResults();seconds=(System.nanoTime()-started)/1e9;stats=solver.getStats().toJson();backend="BigDecimal";}
                case "ITUFP" -> {
                    List<Map<Integer,BigDecimal>> tx=new ArrayList<>(Collections.nCopies(db.getDeclaredTransactions(),null));
                    for(Transaction r:db){int i=r.getTid()-1;var t=tx.get(i);if(t==null){t=new LinkedHashMap<>();tx.set(i,t);}t.put(r.getItemId(),r.getProbabilityDecimal());}
                    for(int i=0;i<tx.size();i++)if(tx.get(i)==null)tx.set(i,Map.of());
                    var solver=new ITUFP(tx,mem::checkMemory);ituResults=solver.query(k);seconds=(System.nanoTime()-started)/1e9;
                    var s=solver.stats;stats="{\"eligiblePairs\":"+s.eligiblePairs+",\"exactCandidateJoins\":"+s.exactCandidateJoins+",\"pufpRejections\":"+s.pufpRejections+",\"ppfStops\":"+s.ppfStops+",\"probabilityMultiplications\":"+s.matchedTuples+",\"newLists\":"+s.newLists+",\"resumedLists\":"+s.resumedLists+",\"reusedComplete\":"+s.reusedComplete+"}";backend="BigDecimal";
                }
                default -> throw new IllegalArgumentException("Unknown algorithm");
            }
        }finally{mem.checkMemory();sampler.shutdownNow();if(!sampler.awaitTermination(5,TimeUnit.SECONDS))throw new IllegalStateException("sampler did not stop");}
        double heap=mem.getMaxMemory();
        if(ituResults!=null){try(var w=Files.newBufferedWriter(out,StandardOpenOption.CREATE_NEW)){for(var r:ituResults){w.write(String.join(" ",r.items().stream().map(String::valueOf).toList())+" : "+r.support().stripTrailingZeros().toString());w.newLine();}}}
        else Output.writeToPath(results,out);
        int size=ituResults!=null?ituResults.size():results.size();
        System.out.println("RESULT_JSON {\"algorithm\":\""+algo+"\",\"backend\":\""+backend+"\",\"k\":"+k+",\"size\":"+size+",\"seconds\":"+seconds+",\"heap_mib\":"+heap+",\"stats\":"+stats+"}");
        System.out.println("RESULT_END");
    }
}
