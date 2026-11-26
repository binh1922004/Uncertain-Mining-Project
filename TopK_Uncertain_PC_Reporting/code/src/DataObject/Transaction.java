package DataObject;
import java.math.BigDecimal;
import java.util.Objects;
/** A decimal probability record; mining never converts it to binary floating point. */
public class Transaction {
    private int tid, itemId;
    private BigDecimal probability;
    public Transaction(int tid,int item,double p) { this(tid,item,decimal(p)); }
    public Transaction(int tid,int item,String p) { this(tid,item,new BigDecimal(p)); }
    public Transaction(int tid,int item,BigDecimal p) { setTid(tid);itemId=item;setProbability(p); }
    private static BigDecimal decimal(double p) {
        if (!Double.isFinite(p)) throw new IllegalArgumentException("Non-finite probability");
        return BigDecimal.valueOf(p);
    }
    public int getTid() { return tid; }
    public void setTid(int value) {
        if (value<1) throw new IllegalArgumentException("TID must be positive");
        tid=value;
    }
    public int getItemId() { return itemId; }
    public void setItemId(int value) { itemId=value; }
    /** Legacy UP-prototype accessor. All three paper miners use getProbabilityDecimal. */
    public double getProbability() { return probability.doubleValue(); }
    public BigDecimal getProbabilityDecimal() { return probability; }
    public void setProbability(double p) { setProbability(decimal(p)); }
    public void setProbability(BigDecimal p) {
        Objects.requireNonNull(p,"probability");
        if (p.signum()<=0 || p.compareTo(BigDecimal.ONE)>0)
            throw new IllegalArgumentException("Recorded probability must be in (0,1]: "+p);
        probability=p;
    }
}
