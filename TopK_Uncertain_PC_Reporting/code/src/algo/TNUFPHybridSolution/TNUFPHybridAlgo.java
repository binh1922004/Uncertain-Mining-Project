package algo.TNUFPHybridSolution;
import DataObject.*;
import utils.*;
import java.util.*;
/** Compatibility class name for the exact TidNTUFP miner. */
public class TNUFPHybridAlgo {
    private TPNMiningEngine engine;
    public ArrayList<Itemset> work(Database db,ArrayList<Double> unusedWeights){
        engine=new TPNMiningEngine(Constant.KValue,true);return engine.mine(db);
    }
    public PriorityQueue<Itemset> getTopKUFPs(){return engine==null?new PriorityQueue<>(ResultRanking.BEST_FIRST.reversed()):engine.heap();}
    public MiningStats getStats(){return engine==null?new MiningStats():engine.stats();}
}
