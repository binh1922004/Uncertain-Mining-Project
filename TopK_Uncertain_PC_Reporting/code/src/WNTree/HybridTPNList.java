package WNTree;
import java.math.BigDecimal;
/** Code caches are immutable. Summing them avoids stale list-level caches after mutation. */
public class HybridTPNList extends TPNList {
    private static final long serialVersionUID=1L;
    public BigDecimal getExactExpSup(){
        BigDecimal sum=BigDecimal.ZERO;
        for(TPNCode code:this){
            if(!(code instanceof HybridTPNCode))throw new IllegalStateException("Expected HybridTPNCode");
            sum=sum.add(((HybridTPNCode)code).getExactExpSup());
        }
        return sum;
    }
}
