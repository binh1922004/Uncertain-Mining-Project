package DataObject;

import WNTree.HybridTPNList;
import WNTree.TPNCode;
import WNTree.TPNList;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

public class TPNListItemset {
    private Itemset item = new Itemset();
    private TPNList tpnList = new TPNList();

    /**
     * Optional legacy tidProbList (kept for compatibility if needed).
     */
    private List<TidProb> tidProbList = new ArrayList<>();

    private BigDecimal exactExpSup = null;

    public TPNListItemset(Itemset itemset) {
        this.item = itemset;
    }

    public TPNListItemset(int item) {
        this.item = new Itemset(item);
    }

    public TPNListItemset(Itemset item, TPNList tpnList) {
        this.item = item;
        this.tpnList = tpnList;
    }

    public TPNListItemset(Itemset item, TPNList tpnList, List<TidProb> tidProbList) {
        this.item = item;
        this.tpnList = tpnList;
        this.tidProbList = tidProbList;
        this.exactExpSup = null;
    }

    public void addTPNCode(TPNCode tpnCode) {
        this.tpnList.add(tpnCode);
    }

    public Itemset getItem() {
        return item;
    }

    public void setItem(Itemset item) {
        this.item = item;
    }

    public TPNList getTpnList() {
        return tpnList;
    }

    public void setTpnList(TPNList tpnList) {
        this.tpnList = tpnList;
        this.exactExpSup = null;
    }

    public List<TidProb> getTidProbList() {
        return tidProbList;
    }

    public void setTidProbList(List<TidProb> tidProbList) {
        this.tidProbList = tidProbList;
        this.exactExpSup = null;
    }

    public void addTidProb(TidProb tidProb) {
        this.tidProbList.add(tidProb);
        this.exactExpSup = null;
    }

    public BigDecimal getExactExpSup() {
        if (tpnList instanceof HybridTPNList) return ((HybridTPNList)tpnList).getExactExpSup();
        BigDecimal sum=BigDecimal.ZERO;
        for(TidProb entry:tidProbList) sum=sum.add(entry.getProb());
        return sum;
    }

    @Override
    public String toString() {
        return "TPNListItemset{" +
                "item=" + item +
                ", tpnList=" + tpnList +
                '}';
    }
}
