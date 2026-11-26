import DataObject.*;
import WNTree.*;
import CUP.*;
import utils.*;
import algo.WUIP.WUIPAlgo;
import algo.TNUFPHybridSolution.TNUFPHybridAlgo;
import algo.TUFP.TUFPAlgo;
import algo.UP.*;
import java.math.BigDecimal;
import java.nio.file.*;
import java.util.*;

/** Assertions throw regardless of JVM -ea, and are independent of the Python oracle. */
public class RegressionProbe {
    static int checked;
    static void require(boolean condition,String label){checked++;if(!condition)throw new AssertionError(label);}
    static BigDecimal d(String value){return new BigDecimal(value);}
    static Database database(){
        Database db=new Database();db.add(new Transaction(2,20,"0.8"));db.add(new Transaction(1,10,"0.9"));
        db.add(new Transaction(1,20,"0.8"));db.add(new Transaction(2,10,"0.9"));return db;
    }
    static String canonical(Collection<Itemset> items){
        var sorted=new ArrayList<>(items);sorted.sort(ResultRanking.BEST_FIRST);StringBuilder out=new StringBuilder();
        for(Itemset x:sorted){for(int i=0;i<x.size();i++)out.append(x.identifierAt(i)).append(',');out.append(':').append(x.getExpSup().stripTrailingZeros()).append(';');}return out.toString();
    }
    static void sorted(List<Itemset> items){for(int i=1;i<items.size();i++)require(ResultRanking.BEST_FIRST.compare(items.get(i-1),items.get(i))<=0,"best-first API order");}
    public static void main(String[] args)throws Exception{
        Constant.KValue=3;Database db=database();
        WUIPAlgo w=new WUIPAlgo();var wr=w.work(db,null);require(wr.size()==3,"nonempty WUIP return");sorted(wr);
        String wf=canonical(wr);require(wf.equals(canonical(w.getTopKUFPs())),"return equals heap");
        wr.clear();require(w.getTopKUFPs().size()==3,"returned list defensive");
        var external=w.getTopKUFPs();external.element().clear();external.clear();require(canonical(w.getTopKUFPs()).equals(wf),"WUIP getter defensive");
        require(canonical(w.work(db,null)).equals(wf),"WUIP repeated work reset");
        TNUFPHybridAlgo h=new TNUFPHybridAlgo();var hr=h.work(db,null);String hf=canonical(hr);require(hr.size()==3,"nonempty Hybrid return");sorted(hr);
        require(canonical(h.work(db,null)).equals(hf),"Hybrid repeated work reset");
        external=h.getTopKUFPs();external.element().setExpSup(BigDecimal.ZERO);require(canonical(h.getTopKUFPs()).equals(hf),"Hybrid getter defensive");
        TUFPAlgo t=new TUFPAlgo(db);require(canonical(t.getResults()).equals(hf),"TUFP exact matches Hybrid");
        require(canonical(t.work(db)).equals(hf),"TUFP repeated work reset");sorted(t.getResults());
        external=t.getTopKUFPs();external.element().clear();require(canonical(t.getResults()).equals(hf),"TUFP getter defensive");
        Constant.KValue=1;require(w.work(db,null).size()==1,"reusing instance with new k");Constant.KValue=3;
        require(w.work(new Database(),null).isEmpty(),"reusing WUIP on empty DB");
        require(h.work(new Database(),null).isEmpty(),"reusing Hybrid on empty DB");
        require(t.work(new Database()).isEmpty(),"reusing TUFP on empty DB");
        Itemset pair=new Itemset(new int[]{20,10});
        require(new ExactExpSupCalculator(db).computeExactExpSup(pair).compareTo(d("1.44"))==0,"unsorted TID direct calculator");
        Database tiny=new Database();tiny.add(new Transaction(1,1,"1e-400"));tiny.add(new Transaction(1,2,"0.12345678901234567890123456789"));
        require(new ExactExpSupCalculator(tiny).computeExactExpSup(new Itemset(new int[]{1,2})).compareTo(d("1e-400").multiply(d("0.12345678901234567890123456789")))==0,"decimal helper precision");
        require(new Transaction(1,1,"1e-400").getProbabilityDecimal().signum()>0,"no decimal underflow");
        TPNList list=new TPNList();list.add(new TPNCode(3,1,d("0.4"),d("0.5")));
        require(list.getExpSup(3).compareTo(d("0.2"))==0,"r-2 exponent");
        require(Operation.calExpSup(list,3).compareTo(list.getExpSup(3))==0,"unified score API");
        list.setSingletonSupport(d("0.7"));require(list.getExpSup(1).compareTo(d("0.7"))==0,"singleton cap uses sigma");
        TPNList parent=new TPNList(),child=new TPNList();parent.add(new TPNCode(1,4,d("0.3"),d("0.9")));child.add(new TPNCode(3,1,d("0.4"),d("0.2")));
        require(Operation.Intersection(child,parent).get(0).getpProxy().compareTo(d("0.2"))==0,"anchor proxy retained");
        require(Operation.binpow(BigDecimal.ZERO,0).equals(BigDecimal.ONE),"pair zero-to-zero factor");
        HybridTPNList hl=new HybridTPNList();HybridTPNCode hc=new HybridTPNCode(2,1,d("0.2"),d("0.1"),List.of(new TidProb(1,d("0.3"),d("0.3"))));
        hl.add(hc);require(hl.getExactExpSup().compareTo(d("0.3"))==0,"Hybrid code sum");hl.clear();require(hl.getExactExpSup().signum()==0,"no stale list cache after clear");
        hl.add(hc);hl.subList(0,1).clear();require(hl.getExactExpSup().signum()==0,"no stale list cache through subList");
        boolean failed=false;try{hc.getTidList().clear();}catch(UnsupportedOperationException e){failed=true;}require(failed,"immutable TID payload");
        TPNListItemset wrapper=new TPNListItemset(new Itemset(1),hl);hl.add(hc);require(wrapper.getExactExpSup().compareTo(d("0.3"))==0,"wrapper sees current list");hl.clear();require(wrapper.getExactExpSup().signum()==0,"wrapper no stale cache");
        Itemset key=new Itemset(new int[]{3,1});require(key.identifierAt(0)==1,"sorted identifier key");key.subList(0,1).set(0,0);require(key.identifierAt(0)==0,"subList set invalidates key");
        key.add(7);require(key.identifierAt(2)==7,"add invalidates key");key.remove(Integer.valueOf(1));require(key.identifierAt(1)==7,"remove invalidates key");
        Itemset extension=Itemset.extended(new Itemset(new int[]{20,10}),30);require(extension.equals(List.of(20,10,30)) && extension.identifierAt(0)==10,"canonical tuple separated from tie key");
        Path output=Path.of(args[0],"probe-output.txt");Itemset unsorted=new Itemset(new int[]{20,10});unsorted.setExpSup(d("1.44"));
        IO.Output.writeToPath(List.of(unsorted),output);require(unsorted.equals(List.of(20,10)),"output does not mutate items");require(Files.readString(output).startsWith("10 20 : 1.44"),"sorted output identifiers");
        TopKBuffer top=new TopKBuffer(3);top.offer(new Itemset(1),d("0.9"));require(top.threshold().signum()==0,"underfilled threshold zero");top.offer(new Itemset(2),d("0.8"));top.offer(new Itemset(3),d("0.7"));require(top.threshold().compareTo(d("0.7"))==0,"full threshold root");
        TopKBuffer tied=new TopKBuffer(2);tied.offer(new Itemset(new int[]{2,3}),BigDecimal.ONE);tied.offer(new Itemset(3),BigDecimal.ONE);tied.offer(new Itemset(1),BigDecimal.ONE);require(canonical(tied.sortedResults()).equals("1,:1;3,:1;"),"complete deterministic tie ranking");
        CUPList cup=new CUPList(new Itemset(1));cup.addUP(new UP(1,d("1e-400"),BigDecimal.ONE));require(cup.getMaxProbabilities().compareTo(d("1e-400"))==0,"CUP tiny conditional bound exact");cup.setUpList(List.of());require(cup.getExpSup().signum()==0 && cup.getMaxProbabilities().signum()==0,"CUP replacement recomputes metadata");
        tools.MemoryLogger memory=tools.MemoryLogger.getInstance();memory.reset();new TUFPAlgo(db);require(memory.getMaxMemory()>0,"TUFP samples construction/mining for small k");
        // Prototype regression: building a conditional tree must leave the source header untouched.
        UPTree prototype=new UPTree(db,null);var header=prototype.getHeaderTable();var counts=new HashMap<Integer,Integer>();for(var e:header.entrySet())counts.put(e.getKey(),e.getValue().size());
        UPTree conditional=prototype.buildConditionalTree(prototype,20,1);require(conditional!=prototype,"conditional prototype returns new tree");
        for(var e:counts.entrySet())require(prototype.getHeaderTable().get(e.getKey()).size()==e.getValue(),"prototype source tree unchanged");
        prototype.visitTree();for(var e:counts.entrySet())require(prototype.getHeaderTable().get(e.getKey()).size()==e.getValue(),"prototype header not duplicated on revisit");
        System.out.println("PROBE_CHECKS="+checked);
    }
}
