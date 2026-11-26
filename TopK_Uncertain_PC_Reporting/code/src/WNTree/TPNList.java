package WNTree;
import DataObject.Operation;
import java.math.BigDecimal;
import java.util.ArrayList;
/** Nodes are in increasing preorder and form an antichain for one anchor label. */
public class TPNList extends ArrayList<TPNCode> {
    private static final long serialVersionUID=1L;
    private BigDecimal singletonSupport;
    public void setSingletonSupport(BigDecimal support){singletonSupport=support;}
    public BigDecimal getExpSupLower2(){
        BigDecimal sum=BigDecimal.ZERO;
        for(TPNCode c:this)sum=sum.add(c.gettPCap());return sum;
    }
    /** Argument is the itemset cardinality, never an already shifted exponent. */
    public BigDecimal getExpSupHigher2(int size){
        if(size<3)throw new IllegalArgumentException("Expected cardinality >= 3");
        BigDecimal sum=BigDecimal.ZERO;
        for(TPNCode c:this)sum=sum.add(c.gettPCap().multiply(Operation.binpow(c.getpProxy(),size-2)));
        return sum;
    }
    public BigDecimal getExpSup(int size){
        if(size<1)throw new IllegalArgumentException("Empty itemsets are outside the search domain");
        if(size==1){if(singletonSupport==null)throw new IllegalStateException("Singleton support is not set");return singletonSupport;}
        return size==2?getExpSupLower2():getExpSupHigher2(size);
    }
}
