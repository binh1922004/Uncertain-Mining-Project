package WNTree;
import DataObject.TidProb;
import java.math.BigDecimal;
import java.util.List;
/** Immutable TID payload; preProb is the original probability of the anchor item. */
public class HybridTPNCode extends TPNCode {
    private final List<TidProb> tidList;
    private final BigDecimal exactExpSup;
    public HybridTPNCode(int pre,int post,BigDecimal cap,BigDecimal proxy){this(pre,post,cap,proxy,List.of());}
    public HybridTPNCode(int pre,int post,BigDecimal cap,BigDecimal proxy,List<TidProb> tids){
        super(pre,post,cap,proxy);tidList=List.copyOf(tids);BigDecimal sum=BigDecimal.ZERO;int previous=0;
        for(TidProb entry:tidList){
            if(entry.getTid()<=previous || entry.getProb().signum()<=0 || entry.getPreProb().signum()<=0)
                throw new IllegalArgumentException("TIDs must be positive, sorted, unique, with positive probabilities");
            previous=entry.getTid();sum=sum.add(entry.getProb());
        }
        exactExpSup=sum;
    }
    public HybridTPNCode(int pre,int post,BigDecimal cap,BigDecimal proxy,List<TidProb> tids,BigDecimal exact){
        this(pre,post,cap,proxy,tids);
        if(exactExpSup.compareTo(exact)!=0)throw new IllegalArgumentException("Inconsistent exact support cache");
    }
    public List<TidProb> getTidList(){return tidList;}
    public BigDecimal getExactExpSup(){return exactExpSup;}
}
