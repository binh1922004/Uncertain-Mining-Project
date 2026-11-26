package WNTree;
import java.math.BigDecimal;
import java.util.Objects;
/** Immutable metadata of the anchor node. Joins never mix different nodes' proxies. */
public class TPNCode {
    private final int pre, post;
    private final BigDecimal tPCap, pProxy;
    public TPNCode(int pre,int post,BigDecimal cap,BigDecimal proxy) {
        this.pre=pre;this.post=post;tPCap=Objects.requireNonNull(cap);pProxy=Objects.requireNonNull(proxy);
        if(cap.signum()<0 || proxy.signum()<0 || proxy.compareTo(BigDecimal.ONE)>0)
            throw new IllegalArgumentException("Invalid node metadata");
    }
    public int getPre(){return pre;}
    public int getPost(){return post;}
    public BigDecimal gettPCap(){return tPCap;}
    public BigDecimal getpProxy(){return pProxy;}
    public String toString(){return "{"+pre+", "+post+", "+tPCap+", "+pProxy+"}";}
}
