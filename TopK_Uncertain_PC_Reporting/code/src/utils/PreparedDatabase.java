package utils;
import DataObject.*;
import java.math.BigDecimal;
import java.util.*;
/** Validated grouping, independent of record order and identifier gaps. */
public final class PreparedDatabase {
    public final NavigableMap<Integer,NavigableMap<Integer,BigDecimal>> transactions=new TreeMap<>();
    public final Map<Integer,BigDecimal> singletonSupports=new HashMap<>();
    public final List<Integer> itemOrder;
    public final Map<Integer,Integer> rank=new HashMap<>();
    public final int transactionCount;
    public PreparedDatabase(Database db) {
        Objects.requireNonNull(db,"database");int maxTid=0;
        for(Transaction r:db) {
            Objects.requireNonNull(r,"record");int tid=r.getTid(),item=r.getItemId();
            BigDecimal p=r.getProbabilityDecimal();
            if(tid<1 || (db.getDeclaredTransactions()>=0 && tid>db.getDeclaredTransactions()))
                throw new IllegalArgumentException("TID outside header range: "+tid);
            if(p==null || p.signum()<=0 || p.compareTo(BigDecimal.ONE)>0)
                throw new IllegalArgumentException("Probability outside (0,1]");
            var t=transactions.computeIfAbsent(tid,k->new TreeMap<>());
            if(t.putIfAbsent(item,p)!=null) throw new IllegalArgumentException("Duplicate (TID,item): "+tid+","+item);
            singletonSupports.merge(item,p,BigDecimal::add);maxTid=Math.max(maxTid,tid);
        }
        if(db.getDeclaredItems()>=0 && singletonSupports.size()!=db.getDeclaredItems())
            throw new IllegalArgumentException("Header item count differs from actual distinct count");
        transactionCount=db.getDeclaredTransactions()>=0?db.getDeclaredTransactions():maxTid;
        var order=new ArrayList<>(singletonSupports.keySet());
        order.sort((a,b)->{int c=singletonSupports.get(b).compareTo(singletonSupports.get(a));return c!=0?c:Integer.compare(a,b);});
        itemOrder=List.copyOf(order);
        for(int i=0;i<itemOrder.size();i++) rank.put(itemOrder.get(i),i);
    }
}
