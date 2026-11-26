package CUP;
import java.math.BigDecimal;
import java.util.Objects;
/** Immutable CUP entry: q(X,t), q(prefix(X),t). */
public final class UP {
    private final int tid;
    private final BigDecimal probabilities,preProbabilities;
    public UP(Integer tid,BigDecimal probability,BigDecimal prefixProbability){
        this.tid=Objects.requireNonNull(tid);probabilities=Objects.requireNonNull(probability);preProbabilities=Objects.requireNonNull(prefixProbability);
        if(tid<1 || probability.signum()<=0 || probability.compareTo(prefixProbability)>0 || prefixProbability.compareTo(BigDecimal.ONE)>0)
            throw new IllegalArgumentException("Invalid CUP entry");
    }
    public UP(int tid,Double probability,double prefix){this(tid,BigDecimal.valueOf(probability),BigDecimal.valueOf(prefix));}
    public UP(int tid,BigDecimal probability,double prefix){this(tid,probability,BigDecimal.valueOf(prefix));}
    public Integer getTid(){return tid;}
    public BigDecimal getProbabilities(){return probabilities;}
    public BigDecimal getPreProbabilities(){return preProbabilities;}
    public String toString(){return "("+tid+", "+probabilities+", "+preProbabilities+")";}
}
