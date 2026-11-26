package utils;
import DataObject.*;
import IO.*;
import algo.WUIP.WUIPAlgo;
import algo.TNUFPHybridSolution.TNUFPHybridAlgo;
import algo.TUFP.TUFPAlgo;
import tools.MemoryLogger;
import java.io.IOException;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.*;
/** Shared protocol: input parsing and output writing are outside the measured mining interval. */
public final class ExperimentRunner {
    private record Run(ArrayList<Itemset> results,MiningStats stats,double seconds,double memory){}
    private static Run mine(String algorithm,Database db,int sampleMs){
        MemoryLogger memory=MemoryLogger.getInstance();memory.reset();memory.checkMemory();
        ScheduledExecutorService sampler=Executors.newSingleThreadScheduledExecutor(r->{Thread t=new Thread(r,"heap-sampler");t.setDaemon(true);return t;});
        sampler.scheduleAtFixedRate(memory::checkMemory,sampleMs,sampleMs,TimeUnit.MILLISECONDS);
        ArrayList<Itemset> results;MiningStats stats;long started=System.nanoTime();double seconds;
        try{
            switch(algorithm){
                case "NTUFP"->{var solver=new WUIPAlgo();results=solver.work(db,null);stats=solver.getStats();}
                case "TidNTUFP"->{var solver=new TNUFPHybridAlgo();results=solver.work(db,null);stats=solver.getStats();}
                case "TUFP"->{var solver=new TUFPAlgo(db);results=solver.getResults();stats=solver.getStats();}
                default->throw new IllegalArgumentException("Unknown algorithm: "+algorithm);
            }
            seconds=(System.nanoTime()-started)/1e9;
        }finally{
            memory.checkMemory();sampler.shutdownNow();
            try{if(!sampler.awaitTermination(5,TimeUnit.SECONDS))throw new IllegalStateException("Heap sampler did not stop");}
            catch(InterruptedException e){Thread.currentThread().interrupt();throw new IllegalStateException("Interrupted",e);}
        }
        return new Run(results,stats,seconds,memory.getMaxMemory());
    }
    public static void run(String algorithm,String[] args)throws IOException{
        Set<String> allowed=Set.of("--input","--k","--output","--runs","--warmup","--sample-ms");
        Map<String,String> options=new HashMap<>();
        for(int i=0;i<args.length;i++){
            if(args[i].equals("--help")){System.out.println("--input FILE --k N [--output FILE] [--runs N] [--warmup N] [--sample-ms N]");return;}
            if(!allowed.contains(args[i]) || i+1>=args.length)throw new IllegalArgumentException("Invalid option: "+args[i]);
            String key=args[i],value=args[++i];if(options.putIfAbsent(key,value)!=null)throw new IllegalArgumentException("Repeated option: "+key);
        }
        Path input=Path.of(options.getOrDefault("--input",Path.of(Constant.datasetBasePath,Constant.inputFileName+".hui").toString()));
        int k=Integer.parseInt(options.getOrDefault("--k",Integer.toString(Constant.KValue)));
        int runs=Integer.parseInt(options.getOrDefault("--runs",Integer.toString(Constant.numberOfRuns)));
        int warmup=Integer.parseInt(options.getOrDefault("--warmup","0"));
        int sampleMs=Integer.parseInt(options.getOrDefault("--sample-ms","5"));
        if(k<1 || runs<1 || warmup<0 || sampleMs<1)throw new IllegalArgumentException("Require k,runs,sample-ms >= 1 and warmup >= 0");
        Constant.KValue=k;Database db=new Input().input(input.toString());
        for(int i=0;i<warmup;i++)mine(algorithm,db,sampleMs);
        System.out.println("Algorithm: "+algorithm);System.out.println("Score: "+(algorithm.equals("NTUFP")?"B":"ExpSup"));
        System.out.println("MemoryMetric: SampledMaxUsedHeapMiB");System.out.println("SampleIntervalMs: "+sampleMs);
        System.out.println("TimingScope: solver construction + mining; excludes input/output and sampler setup");
        double seconds=0,memory=0;ArrayList<Itemset> results=null;
        for(int i=0;i<runs;i++){
            Run run=mine(algorithm,db,sampleMs);
            if(results!=null && !sameResults(results,run.results))throw new IllegalStateException("Results differ across repeated runs");
            results=run.results;seconds+=run.seconds;memory+=run.memory;
            System.out.println("Run: "+(i+1));System.out.println("Size: "+results.size());System.out.println("Time: "+run.seconds);
            System.out.println("SampledMaxUsedHeapMiB: "+run.memory);System.out.println("Stats: "+run.stats.toJson());
        }
        System.out.println("Size: "+results.size());System.out.println("AvgTime: "+seconds/runs);
        System.out.println("AvgMemory: "+memory/runs);System.out.println("RESULT_END");
        String defaultName=algorithm+"_"+input.getFileName()+"_"+k+".txt";
        Output.writeToPath(results,Path.of(options.getOrDefault("--output",Path.of("pattern",defaultName).toString())));
    }
    private static boolean sameResults(List<Itemset> a,List<Itemset> b){
        if(a.size()!=b.size())return false;for(int i=0;i<a.size();i++)if(ResultRanking.BEST_FIRST.compare(a.get(i),b.get(i))!=0)return false;return true;
    }
}
