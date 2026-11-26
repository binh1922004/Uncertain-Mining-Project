package DataObject;

import java.math.BigDecimal;

/**
 * Lightweight pair that stores a transaction ID and its exact probability product
 * for a given itemset. Used to incrementally compute exact expSup during
 * TPNList intersection, avoiding the need to rescan the database.
 *
 * The list of TidProb is sorted by tid (ascending) to support efficient
 * merge-based intersection.
 */
public class TidProb {
    private final int tid;
    private final BigDecimal prob;
    private final BigDecimal preProb;

    public TidProb(int tid, BigDecimal prob, BigDecimal preProb) {
        this.tid = tid;
        this.prob = prob;
        this.preProb = preProb;
    }

    public int getTid() {
        return tid;
    }

    public BigDecimal getProb() {
        return prob;
    }

    public BigDecimal getPreProb() {
        return preProb;
    }

    @Override
    public String toString() {
        return "(" + tid + ", " + prob + ", " + preProb + ")";
    }
}
