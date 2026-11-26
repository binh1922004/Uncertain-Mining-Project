package CUP;
import DataObject.Itemset;
import java.math.BigDecimal;
import java.util.*;
/** Sorted unique TIDs, exact products, and a safe maximum conditional probability. */
public class CUPList {
    private Itemset itemset;
    private BigDecimal maxProbabilities=BigDecimal.ZERO,expSup=BigDecimal.ZERO;
    private final List<UP> upList=new ArrayList<>();
    public CUPList(Itemset itemset){this.itemset=Objects.requireNonNull(itemset);}
    /** An empty list cannot have nonzero support or maximum probability. */
    public CUPList(Itemset itemset,BigDecimal max,BigDecimal support){
        this(itemset);if(max.signum()!=0 || support.signum()!=0)throw new IllegalArgumentException("Empty CUP list must have zero metadata");
    }
    public Itemset getItemset(){return itemset;}
    public void setItemset(Itemset itemset){this.itemset=Objects.requireNonNull(itemset);}
    public BigDecimal getMaxProbabilities(){return maxProbabilities;}
    public BigDecimal getExpSup(){return expSup;}
    public List<UP> getUpList(){return Collections.unmodifiableList(upList);}
    public void setUpList(List<UP> entries){
        // Validate in a temporary list so a failed replacement leaves this object intact.
        CUPList checked=new CUPList(itemset);for(UP entry:entries)checked.addUP(entry);
        upList.clear();upList.addAll(checked.upList);maxProbabilities=checked.maxProbabilities;expSup=checked.expSup;
    }
    public void addUP(UP entry){
        Objects.requireNonNull(entry);
        if(!upList.isEmpty() && upList.get(upList.size()-1).getTid()>=entry.getTid())throw new IllegalArgumentException("CUP TIDs must be sorted and unique");
        // For valid CUP entries q(X,t)/q(prefix(X),t)=p(last(X),t): exact terminating decimal.
        BigDecimal conditional=entry.getProbabilities().divide(entry.getPreProbabilities());
        if(conditional.compareTo(BigDecimal.ONE)>0)throw new IllegalArgumentException("Conditional probability exceeds 1");
        upList.add(entry);expSup=expSup.add(entry.getProbabilities());maxProbabilities=maxProbabilities.max(conditional);
    }
    public String toString(){return "CUPList{"+itemset+", "+expSup+", "+upList+"}";}
}
