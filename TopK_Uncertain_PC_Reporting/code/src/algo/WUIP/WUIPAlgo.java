package algo.WUIP;
import DataObject.*;
import utils.*;
import java.util.*;
/** Compatibility class name for the NTUFP surrogate-score miner. */
public class WUIPAlgo {
    private OptimizedNTUFP engine;
    public ArrayList<Itemset> work(Database db,ArrayList<Double> unusedWeights){
        engine=new OptimizedNTUFP(Constant.KValue);return engine.mine(db);
    }
    public PriorityQueue<Itemset> getTopKUFPs(){return engine==null?new PriorityQueue<>(ResultRanking.BEST_FIRST.reversed()):engine.heap();}
    public MiningStats getStats(){return engine==null?new MiningStats():engine.stats();}
}
