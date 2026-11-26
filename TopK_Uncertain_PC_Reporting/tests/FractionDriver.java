package itufp;
import java.nio.file.*;
import java.util.*;
/** Batch test bridge; not part of the mining JAR. */
public final class FractionDriver {
    record State(int i,int j,int n,boolean complete){}
    static long observed;
    static void inspect(ITUFP.UPList l,List<Map<Integer,java.math.BigDecimal>> db,Map<ITUFP.UPList,State> states){
        OracleTest.audit(l,db); // audit includes every descendant; independent Fraction oracle checks rankings below.
        capture(l,states);
    }
    static void capture(ITUFP.UPList l,Map<ITUFP.UPList,State> states){
        observed++;
        if(l.left!=null){
            states.put(l,new State(l.next1,l.next2,l.records.size(),l.complete));
            if(!l.complete){
                int nextTid=l.left.records.get(l.next1).tid();
                if(l.next2>0 && l.right.records.get(l.next2-1).tid()>=nextTid)throw new AssertionError("right cursor skipped match");
                long processed=l.left.records.subList(0,l.next1).stream().filter(x->l.right.records.stream().anyMatch(y->x.tid()==y.tid())).count();
                if(processed!=l.records.size())throw new AssertionError("missing processed common tuple");
            }
        }
        for(var c:l.children.values())capture(c,states);
    }
    static Map<ITUFP.UPList,State> audit(ITUFP s,List<Map<Integer,java.math.BigDecimal>> db,Map<ITUFP.UPList,State> old){
        Map<ITUFP.UPList,State> states=new IdentityHashMap<>();
        for(var l:s.roots)inspect(l,db,states);
        for(var e:old.entrySet()){
            State a=e.getValue(),b=states.get(e.getKey());
            if(b==null||b.i<a.i||b.j<a.j||b.n<a.n||a.complete&&!a.equals(b))throw new AssertionError("PUT rewound/changed complete list");
        }
        return states;
    }
    static void emit(int c,int i,String mode,int k,List<ITUFP.Result> r){
        StringJoiner j=new StringJoiner(";");
        for(var x:r)j.add(String.join(" ",x.items().stream().map(String::valueOf).toList())+":"+x.support().toPlainString());
        System.out.println(c+"\t"+i+"\t"+mode+"\t"+k+"\t"+j);
    }
    public static void main(String[] args)throws Exception{
        int c=0;
        for(String line:Files.readAllLines(Path.of(args[0]))){
            String[] f=line.split("\t");var db=Database.read(Path.of(f[0])).group();
            ITUFP s=new ITUFP(db,()->{});Map<ITUFP.UPList,State> old=Map.of();int i=0;
            for(String q:f[1].split(",")){
                int k=Integer.parseInt(q);var r=s.query(k);old=audit(s,db,old);emit(c,i,"interactive",k,r);
                var fresh=new ITUFP(db,()->{});var r2=fresh.query(k);audit(fresh,db,Map.of());emit(c,i,"fresh",k,r2);
                // Published example, Fig. 3: BC partial at k=4, complete at k=6.
                if(c==0 && i<2){var b=s.roots.stream().filter(x->x.suffix==2).findFirst().orElseThrow();var bc=b.children.get(3);
                    if(bc==null||bc.complete!=(i==1)||bc.expSup.compareTo(new java.math.BigDecimal(i==0?"0.38":"1.19"))!=0||i==0&&(bc.next1!=2||bc.next2!=3))throw new AssertionError("Fig.3 BC state");}
                i++;
            }c++;
        }
        System.err.println("list_state_observations="+observed);
    }
}
