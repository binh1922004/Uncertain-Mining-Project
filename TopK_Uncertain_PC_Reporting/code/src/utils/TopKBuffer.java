package utils;
import DataObject.Itemset;
import java.math.BigDecimal;
import java.util.*;
public final class TopKBuffer {
    private final int k;
    private final PriorityQueue<Itemset> heap=new PriorityQueue<>(ResultRanking.BEST_FIRST.reversed());
    public TopKBuffer(int k) { if(k<1)throw new IllegalArgumentException("k must be at least 1");this.k=k; }
    public BigDecimal threshold() { return heap.size()<k?BigDecimal.ZERO:heap.element().getExpSup(); }
    public void offer(Itemset x,BigDecimal score) {
        if(score.signum()<=0)throw new IllegalArgumentException("Only supported positive-score patterns may enter the heap");
        Itemset r=new Itemset(x,score);
        if(heap.size()<k)heap.add(r);
        else if(ResultRanking.BEST_FIRST.compare(r,heap.element())<0){heap.poll();heap.add(r);}
    }
    public ArrayList<Itemset> sortedResults() {
        ArrayList<Itemset> out=new ArrayList<>();
        for(Itemset x:heap)out.add(new Itemset(x,x.getExpSup()));out.sort(ResultRanking.BEST_FIRST);return out;
    }
    public PriorityQueue<Itemset> snapshotHeap() {
        PriorityQueue<Itemset> out=new PriorityQueue<>(ResultRanking.BEST_FIRST.reversed());out.addAll(sortedResults());return out;
    }
}
