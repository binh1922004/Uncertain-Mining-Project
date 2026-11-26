package itufp;
import java.math.BigDecimal;
import java.util.*;

/** Independent exhaustive global itemset enumeration; no ITUFP bounds or joins. */
public class OracleTest {
    static long cases,queries,states,ppf,resumed,pufp,reused;
    static List<ITUFP.Result> oracle(List<Map<Integer,BigDecimal>> db) {
        TreeSet<Integer> set=new TreeSet<>(); for(var t:db)set.addAll(t.keySet());
        List<Integer> ids=new ArrayList<>(set); List<ITUFP.Result> out=new ArrayList<>();
        for(int mask=1;mask<(1<<ids.size());mask++) {
            List<Integer> x=new ArrayList<>();for(int i=0;i<ids.size();i++)if((mask&(1<<i))!=0)x.add(ids.get(i));
            BigDecimal sum=BigDecimal.ZERO;
            for(var t:db){BigDecimal q=BigDecimal.ONE;for(int id:x){BigDecimal p=t.get(id);if(p==null){q=BigDecimal.ZERO;break;}q=q.multiply(p);}sum=sum.add(q);}
            if(sum.signum()>0)out.add(new ITUFP.Result(x,sum));
        }
        // Separate comparator (no ITUFP comparator dependency).
        out.sort((a,b)->{int z=b.support().compareTo(a.support());if(z!=0)return z;
            z=Integer.compare(a.items().size(),b.items().size());if(z!=0)return z;
            return Arrays.compare(a.items().stream().mapToInt(Integer::intValue).toArray(), b.items().stream().mapToInt(Integer::intValue).toArray());});
        return out;
    }
    static void check(List<ITUFP.Result> a,List<ITUFP.Result> b,int k){
        if(a.size()!=Math.min(b.size(),k))throw new AssertionError("size k="+k);
        for(int i=0;i<a.size();i++)if(!a.get(i).items().equals(b.get(i).items())||a.get(i).support().compareTo(b.get(i).support())!=0)
            throw new AssertionError("rank "+i+" k="+k+" got="+a+" expected="+b.subList(0,Math.min(k,b.size())));
        queries++;
    }
    static void audit(ITUFP.UPList l,List<Map<Integer,BigDecimal>> db) {
        states++; BigDecimal sum=BigDecimal.ZERO,mj=BigDecimal.ZERO,mc=BigDecimal.ZERO;int prev=-1;
        if(l.left!=null){if(!l.left.complete||!l.right.complete||l.next1<0||l.next2<0||l.next1>l.left.records.size()||l.next2>l.right.records.size())throw new AssertionError("cursors");
            if(l.complete!=(l.next1==l.left.records.size()||l.next2==l.right.records.size()))throw new AssertionError("completion");}
        ArrayList<Integer> expected=new ArrayList<>();
        for(int tid=0;tid<db.size();tid++)if(db.get(tid).keySet().containsAll(l.ids))expected.add(tid);
        for(var r:l.records){if(r.tid()<=prev)throw new AssertionError("TID order");prev=r.tid();BigDecimal q=BigDecimal.ONE;for(int x:l.ids)q=q.multiply(db.get(r.tid()).get(x));if(q.compareTo(r.probability())!=0)throw new AssertionError("joint");sum=sum.add(q);mj=mj.max(q);mc=mc.max(db.get(r.tid()).get(l.suffix));}
        if(l.records.size()>expected.size()||l.complete&&l.records.size()!=expected.size())throw new AssertionError("completeness");
        for(int i=0;i<l.records.size();i++)if(l.records.get(i).tid()!=expected.get(i))throw new AssertionError("prefix");
        if(sum.compareTo(l.expSup)!=0||mj.compareTo(l.maxJoint)!=0||mc.compareTo(l.maxConditional)!=0)throw new AssertionError("metadata");
        for(var child:l.children.values())audit(child,db);
    }
    static void run(List<Map<Integer,BigDecimal>> db,int[] ks) {
        cases++;var expected=oracle(db);ITUFP s=new ITUFP(db,()->{});
        for(int k:ks){check(s.query(k),expected,k);for(var r:s.roots)audit(r,db);ppf+=s.stats.ppfStops;resumed+=s.stats.resumedLists;pufp+=s.stats.pufpRejections;reused+=s.stats.reusedComplete;
            ITUFP fresh=new ITUFP(db,()->{});check(fresh.query(k),expected,k);for(var r:fresh.roots)audit(r,db);}
    }
    public static void main(String[] args)throws Exception {
        int[] ks={1,2,4,3,6,8,2,12,20,20};
        for(int code=0;code<729;code++){int n=code;List<Map<Integer,BigDecimal>> db=new ArrayList<>();for(int t=0;t<2;t++){Map<Integer,BigDecimal> tx=new HashMap<>();for(int i=1;i<=3;i++){int p=n%3;n/=3;if(p>0)tx.put(i,new BigDecimal(p==1?"0.1":"1"));}db.add(tx);}run(db,ks);}
        Random r=new Random(4172);String[] ps={"0.01","0.1","0.2","0.3","0.5","0.9","1"};
        for(int c=0;c<150;c++){List<Map<Integer,BigDecimal>> db=new ArrayList<>();int m=1+r.nextInt(10),n=1+r.nextInt(12);for(int t=0;t<n;t++){Map<Integer,BigDecimal> tx=new HashMap<>();for(int i=1;i<=m;i++)if(r.nextBoolean())tx.put(i,new BigDecimal(ps[r.nextInt(ps.length)]));db.add(tx);}run(db,ks);}
        run(Main.load(java.nio.file.Path.of("examples/table1.txt")),new int[]{4,6,8,3,15,2,16});
        // Recursive PUFP counterexample: P=.1, A=B=1; PA and PB each .1; PAB=.1.
        var counter=new ArrayList<Map<Integer,BigDecimal>>();counter.add(Map.of(1,new BigDecimal("0.1"),2,BigDecimal.ONE,3,BigDecimal.ONE));counter.add(Map.of(1,BigDecimal.ONE));counter.add(Map.of(1,BigDecimal.ONE));run(counter,ks);
        System.out.printf("PASS cases=%d queries=%d list_states=%d ppf_stops=%d resumed=%d pufp_rejections=%d reused_complete=%d%n",cases,queries,states,ppf,resumed,pufp,reused);
        if(ppf==0||resumed==0||pufp==0||reused==0)throw new AssertionError("coverage");
    }
}
