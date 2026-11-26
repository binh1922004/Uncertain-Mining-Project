package algo.TUFP;
import CUP.*;
import DataObject.*;
import utils.*;
import tools.MemoryLogger;
import java.math.BigDecimal;
import java.util.*;
/** Corrected uploaded CUP-list baseline implementation; not an upstream reference implementation. */
public class TUFPAlgo {
    private TopKBuffer top;
    private MiningStats stats;
    private PreparedDatabase data;
    public List<Itemset> ufps=new ArrayList<>();
    public TUFPAlgo(Database db){work(db);}
    public ArrayList<Itemset> work(Database db){
        top=new TopKBuffer(Constant.KValue);stats=new MiningStats();data=new PreparedDatabase(db);
        List<CUPList> initial=new ArrayList<>();
        for(int item:data.itemOrder){
            CUPList list=new CUPList(new Itemset(item));
            for(var transaction:data.transactions.entrySet()){
                BigDecimal p=transaction.getValue().get(item);
                if(p!=null)list.addUP(new UP(transaction.getKey(),p,BigDecimal.ONE));
            }
            initial.add(list);top.offer(list.getItemset(),list.getExpSup());stats.heapOffers++;
        }
        MemoryLogger.getInstance().checkMemory();search(initial);MemoryLogger.getInstance().checkMemory();
        ufps=top.sortedResults();return top.sortedResults();
    }
    private void search(List<CUPList> candidates){
        for(int i=0;i<candidates.size();i++){
            CUPList a=candidates.get(i);stats.maxDepth=Math.max(stats.maxDepth,a.getItemset().size());
            if(a.getExpSup().compareTo(top.threshold())<0){stats.branchPrunes++;continue;}
            List<CUPList> next=new ArrayList<>();
            for(int j=i+1;j<candidates.size();j++){
                CUPList b=candidates.get(j);
                if(b.getExpSup().compareTo(top.threshold())<0){stats.branchPrunes++;continue;}
                stats.capChecks++;stats.eligiblePairs++;
                if(a.getExpSup().multiply(b.getMaxProbabilities()).compareTo(top.threshold())<0){stats.capRejections++;continue;}
                Itemset items=Itemset.extended(a.getItemset(),b.getItemset().get(b.getItemset().size()-1));
                stats.exactCandidateJoins++;
                CUPList joined=intersection(a,b,items);
                if(joined.getUpList().isEmpty() || joined.getExpSup().compareTo(top.threshold())<0)continue;
                top.offer(items,joined.getExpSup());stats.heapOffers++;next.add(joined);
            }
            MemoryLogger.getInstance().checkMemory();if(!next.isEmpty())search(next);
        }
    }
    private CUPList intersection(CUPList a,CUPList b,Itemset items){
        CUPList out=new CUPList(items);int i=0,j=0;stats.tidPairMerges++;
        while(i<a.getUpList().size() && j<b.getUpList().size()){
            UP x=a.getUpList().get(i),y=b.getUpList().get(j);
            if(x.getTid().equals(y.getTid())){
                // q_A * p(new item) avoids rounded division by a tiny common-prefix product.
                BigDecimal p=data.transactions.get(x.getTid()).get(b.getItemset().get(b.getItemset().size()-1));
                out.addUP(new UP(x.getTid(),x.getProbabilities().multiply(p),x.getProbabilities()));
                stats.tidRecordsMerged++;stats.probabilityMultiplications++;i++;j++;
            }else if(x.getTid()<y.getTid())i++;else j++;
        }
        return out;
    }
    public PriorityQueue<Itemset> getTopKUFPs(){return top.snapshotHeap();}
    public ArrayList<Itemset> getResults(){return top.sortedResults();}
    public MiningStats getStats(){return stats;}
}
