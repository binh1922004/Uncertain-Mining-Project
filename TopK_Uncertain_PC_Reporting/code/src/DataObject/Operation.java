package DataObject;
import WNTree.*;
import utils.MiningStats;
import java.math.BigDecimal;
import java.util.*;
public class Operation {
    /** Legacy globals retained for callers; the miners derive dimensions from Database. */
    public static double minVal;
    public static final BigDecimal infi=BigDecimal.valueOf(Double.MAX_VALUE);
    public static int numberTransaction;
    public record MetadataMatch(TPNCode parent,TPNCode child){}
    /** Metadata only: no probability multiplication or TID payload creation here. */
    public static List<MetadataMatch> joinMetadata(TPNList parent,TPNList child){
        List<MetadataMatch> out=new ArrayList<>();int i=0,j=0;
        while(i<child.size() && j<parent.size()){
            TPNCode c=child.get(i),p=parent.get(j);
            if(c.getPre()<=p.getPre())i++;
            else if(c.getPost()<p.getPost()){out.add(new MetadataMatch(p,c));i++;}
            else j++;
        }
        return out;
    }
    public static TPNList childMetadata(List<MetadataMatch> matches){
        TPNList result=new TPNList();for(var pair:matches){TPNCode c=pair.child();result.add(new TPNCode(c.getPre(),c.getPost(),c.gettPCap(),c.getpProxy()));}return result;
    }
    public static BigDecimal capFromMatches(List<MetadataMatch> matches,int size){
        if(size<2)throw new IllegalArgumentException("A join has cardinality >= 2");
        BigDecimal sum=BigDecimal.ZERO;
        for(var pair:matches){TPNCode c=pair.child();sum=sum.add(c.gettPCap().multiply(binpow(c.getpProxy(),size-2)));}
        return sum;
    }
    /** Called only after the metadata cap passes. Child TIDs are a subset of parent TIDs. */
    public static HybridTPNList materializeExact(List<MetadataMatch> matches,MiningStats stats){
        HybridTPNList result=new HybridTPNList();
        for(var pair:matches){
            HybridTPNCode p=(HybridTPNCode)pair.parent(),c=(HybridTPNCode)pair.child();
            List<TidProb> tids=new ArrayList<>();int j=0;stats.tidPairMerges++;
            for(TidProb ct:c.getTidList()){
                while(j<p.getTidList().size() && p.getTidList().get(j).getTid()<ct.getTid())j++;
                if(j==p.getTidList().size() || p.getTidList().get(j).getTid()!=ct.getTid())
                    throw new IllegalStateException("Broken prefix/TID subset invariant");
                TidProb pt=p.getTidList().get(j);
                BigDecimal probability=ct.getProb().multiply(pt.getPreProb());
                tids.add(new TidProb(ct.getTid(),probability,ct.getPreProb()));
                stats.tidRecordsMerged++;stats.probabilityMultiplications++;
            }
            result.add(new HybridTPNCode(c.getPre(),c.getPost(),c.gettPCap(),c.getpProxy(),tids));
        }
        return result;
    }
    /** Legacy argument order: child, parent. New search uses the two stages explicitly. */
    public static TPNList Intersection(TPNList child,TPNList parent){
        var matches=joinMetadata(parent,child);
        return child instanceof HybridTPNList && parent instanceof HybridTPNList
            ?materializeExact(matches,new MiningStats()):childMetadata(matches);
    }
    public static HybridTPNList intersectHybrid(HybridTPNList child,HybridTPNList parent){return materializeExact(joinMetadata(parent,child),new MiningStats());}
    public static BigDecimal binpow(BigDecimal base,int exponent){
        if(exponent<0)throw new IllegalArgumentException("Negative exponent");
        BigDecimal value=BigDecimal.ONE;
        while(exponent>0){if((exponent&1)!=0)value=value.multiply(base);exponent>>>=1;if(exponent>0)base=base.multiply(base);}return value;
    }
    public static BigDecimal calExpSup(TPNList list,int size){return list.getExpSup(size);}
    public static List<TidProb> intersectTidProbLists(List<TidProb> child,List<TidProb> parent){
        List<TidProb> out=new ArrayList<>();int i=0,j=0;
        while(i<child.size() && j<parent.size()){
            TidProb c=child.get(i),p=parent.get(j);
            if(c.getTid()==p.getTid()){out.add(new TidProb(c.getTid(),c.getProb().multiply(p.getPreProb()),c.getPreProb()));i++;j++;}
            else if(c.getTid()<p.getTid())i++;else j++;
        }
        return out;
    }
}
