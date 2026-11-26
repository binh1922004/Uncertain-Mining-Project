package utils;
import DataObject.*;
import WNTree.*;
import tools.MemoryLogger;
import java.math.BigDecimal;
import java.util.*;
/** Canonical prefix-equivalence-class search, retaining the uploaded TPN architecture. */
public final class TPNMiningEngine {
    private record Candidate(Itemset items,TPNList list,BigDecimal score,BigDecimal envelope){}
    private final boolean exact;
    private final TopKBuffer top;
    private final MiningStats stats=new MiningStats();
    public TPNMiningEngine(int k,boolean exact){this.exact=exact;top=new TopKBuffer(k);}
    public ArrayList<Itemset> mine(Database db){
        TPNTree tree=exact?new HybridTPNTree(db):new TPNTree(db);
        Map<Integer,BigDecimal> singleton=tree.getExpSup1sz();List<Candidate> initial=new ArrayList<>();
        for(var entry:tree.getItem1sz_list()){
            BigDecimal sigma=singleton.get(entry.getItem().get(0));
            initial.add(new Candidate(entry.getItem(),entry.getTpnList(),sigma,sigma));
            top.offer(entry.getItem(),sigma);stats.heapOffers++;
        }
        MemoryLogger.getInstance().checkMemory();search(initial);MemoryLogger.getInstance().checkMemory();return top.sortedResults();
    }
    private void search(List<Candidate> candidates){
        for(int i=0;i<candidates.size();i++){
            Candidate a=candidates.get(i);stats.maxDepth=Math.max(stats.maxDepth,a.items.size());
            // Exact support is anti-monotone. The surrogate B is NOT: use sigma(anchor) for its subtree.
            if((exact?a.score:a.envelope).compareTo(top.threshold())<0){stats.branchPrunes++;continue;}
            List<Candidate> next=new ArrayList<>();
            for(int j=i+1;j<candidates.size();j++){
                Candidate b=candidates.get(j);
                if((exact?b.score:b.envelope).compareTo(top.threshold())<0){stats.branchPrunes++;continue;}
                stats.metadataJoins++;var matches=Operation.joinMetadata(a.list,b.list);stats.metadataPairs+=matches.size();
                if(matches.isEmpty())continue;
                Itemset items=Itemset.extended(a.items,b.items.get(b.items.size()-1));
                BigDecimal cap=Operation.capFromMatches(matches,items.size());TPNList list;BigDecimal score;
                if(exact){
                    stats.capChecks++;
                    if(cap.compareTo(top.threshold())<0){stats.capRejections++;stats.capRejectedPairs+=matches.size();continue;}
                    list=Operation.materializeExact(matches,stats);score=((HybridTPNList)list).getExactExpSup();
                    if(score.compareTo(top.threshold())<0)continue;
                }else{list=Operation.childMetadata(matches);score=cap;}
                if(score.signum()>0){top.offer(items,score);stats.heapOffers++;}
                // Even B(items) < theta cannot exclude a descendant with a larger surrogate.
                if((exact?score:b.envelope).compareTo(top.threshold())>=0)
                    next.add(new Candidate(items,list,score,b.envelope));
            }
            MemoryLogger.getInstance().checkMemory();if(!next.isEmpty())search(next);
        }
    }
    public PriorityQueue<Itemset> heap(){return top.snapshotHeap();}
    public MiningStats stats(){return stats;}
}
