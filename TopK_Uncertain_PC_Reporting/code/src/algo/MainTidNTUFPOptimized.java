package algo;
import DataObject.*;
import IO.*;
import utils.*;
import tools.MemoryLogger;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.*;

/** Same measurement scope as ExperimentRunner; all compact structure construction
 * is timed, while the unchanged input parser and output writer remain outside. */
public final class MainTidNTUFPOptimized {
    private record Run(ArrayList<Itemset> output,double seconds,double memory,String stats) {}
    private static Run mine(Database db,int k,int interval,String mode,boolean early)throws Exception {
        MemoryLogger memory=MemoryLogger.getInstance();memory.reset();memory.checkMemory();
        ScheduledExecutorService sampler=Executors.newSingleThreadScheduledExecutor(r->{Thread t=new Thread(r,"heap-sampler");t.setDaemon(true);return t;});
        sampler.scheduleAtFixedRate(memory::checkMemory,interval,interval,TimeUnit.MILLISECONDS);
        OptimizedTidNTUFP solver;ArrayList<Itemset> output;double seconds;long started=System.nanoTime();
        try {solver=new OptimizedTidNTUFP(k,mode,early);output=solver.mine(db);seconds=(System.nanoTime()-started)/1e9;}
        finally {memory.checkMemory();sampler.shutdownNow();if(!sampler.awaitTermination(5,TimeUnit.SECONDS))throw new IllegalStateException("Sampler did not stop");}
        return new Run(output,seconds,memory.getMaxMemory(),solver.statsJson());
    }
    public static void main(String[] args)throws Exception {
        Set<String> allowed=Set.of("--input","--k","--output","--runs","--warmup","--sample-ms","--tid-mode","--early");Map<String,String> o=new HashMap<>();
        for(int i=0;i<args.length;i++){if(!allowed.contains(args[i])||i+1==args.length)throw new IllegalArgumentException("Invalid option: "+args[i]);String key=args[i],value=args[++i];if(o.putIfAbsent(key,value)!=null)throw new IllegalArgumentException("Repeated option");}
        int k=Integer.parseInt(o.getOrDefault("--k","100")),runs=Integer.parseInt(o.getOrDefault("--runs","1")),warmup=Integer.parseInt(o.getOrDefault("--warmup","0")),interval=Integer.parseInt(o.getOrDefault("--sample-ms","5"));
        if(k<1||runs<1||warmup<0||interval<1)throw new IllegalArgumentException("Invalid numeric protocol");
        String mode=o.getOrDefault("--tid-mode","adaptive"),earlyValue=o.getOrDefault("--early","true");
        if(!Set.of("true","false").contains(earlyValue))throw new IllegalArgumentException("Invalid early flag");boolean early=Boolean.parseBoolean(earlyValue);
        Constant.KValue=k;Database db=new Input().input(o.get("--input"));
        for(int i=0;i<warmup;i++)mine(db,k,interval,mode,early);
        System.out.println("Algorithm: TidNTUFP-Optimized");System.out.println("Score: ExpSup");System.out.println("MemoryMetric: SampledMaxUsedHeapMiB");System.out.println("SampleIntervalMs: "+interval);
        System.out.println("TimingScope: solver construction + mining; excludes input/output and sampler setup");
        double seconds=0,heap=0;ArrayList<Itemset> output=null;
        for(int i=0;i<runs;i++) {
            Run run=mine(db,k,interval,mode,early);
            if(output!=null){if(output.size()!=run.output.size())throw new IllegalStateException("Repeated outputs differ");for(int j=0;j<output.size();j++)if(ResultRanking.BEST_FIRST.compare(output.get(j),run.output.get(j))!=0)throw new IllegalStateException("Repeated outputs differ");}
            output=run.output;seconds+=run.seconds;heap+=run.memory;
            System.out.println("Run: "+(i+1));System.out.println("Size: "+output.size());System.out.println("Time: "+run.seconds);System.out.println("SampledMaxUsedHeapMiB: "+run.memory);System.out.println("Stats: "+run.stats);
        }
        System.out.println("AvgTime: "+seconds/runs);System.out.println("AvgMemory: "+heap/runs);System.out.println("RESULT_END");
        Output.writeToPath(output,Path.of(o.getOrDefault("--output","TidNTUFP_optimized.txt")));
    }
}
