import DataObject.*;
import WNTree.*;
import CUP.*;
import algo.WUIP.WUIPAlgo;
import algo.TNUFPHybridSolution.TNUFPHybridAlgo;
import algo.TUFP.TUFPAlgo;
import java.io.*;
import java.math.*;
import java.util.*;

public class AuditRunner {
  static PrintStream real=System.out;
  static String quote(String s){return "\""+s.replace("\\","\\\\").replace("\"","\\\"").replace("\n","\\n").replace("\r","\\r")+"\"";}
  static String results(Collection<Itemset> xs){
    List<String> out=new ArrayList<>();
    for(Itemset x:xs){List<Integer> key=new ArrayList<>(x);Collections.sort(key);out.add("{\"items\":"+key+",\"score\":"+quote(x.getExpSup().toPlainString())+"}");}
    return "["+String.join(",",out)+"]";
  }
  public static void main(String[] args)throws Exception{
    System.setOut(new PrintStream(OutputStream.nullOutputStream()));
    if(args.length>0&&args[0].equals("file")){
      ConstantConfig(args[2]);Database db=new IO.Input().input(args[1]);
      real.println("{\"records\":"+db.size()+",\"n\":"+Operation.numberTransaction+",\"m\":"+utils.Constant.numberItems+"}");
      for(String name:List.of("w","h","t")){try{real.println("{\"algo\":"+quote(name)+",\"output\":"+run(name,db)+"}");}catch(Throwable e){real.println("{\"algo\":"+quote(name)+",\"error\":"+quote(e.toString())+"}");}}
      return;
    }
    if(args.length>0&&args[0].equals("unit")){units();return;}
    BufferedReader br=new BufferedReader(new InputStreamReader(System.in));String line;
    while((line=br.readLine())!=null){
      try{
        String[] p=line.split("\\|",-1);String id=p[0],mode=p[1];
        utils.Constant.KValue=Integer.parseInt(p[2]);Operation.numberTransaction=Integer.parseInt(p[3]);utils.Constant.numberItems=Integer.parseInt(p[4]);
        Database db=new Database();if(!p[5].isEmpty())for(String r:p[5].split(";")){String[] q=r.split(",");db.add(new Transaction(Integer.parseInt(q[0]),Integer.parseInt(q[1]),q[2]));}
        if(mode.equals("reuse")){
          WUIPAlgo w=new WUIPAlgo();w.work(db,null);String first=results(w.getTopKUFPs());w.work(db,null);
          TNUFPHybridAlgo h=new TNUFPHybridAlgo();h.work(db,null);String hf=results(h.getTopKUFPs());h.work(db,null);
          real.println("{\"id\":"+quote(id)+",\"w_first\":"+first+",\"w_second\":"+results(w.getTopKUFPs())+",\"h_first\":"+hf+",\"h_second\":"+results(h.getTopKUFPs())+"}");
        }else real.println("{\"id\":"+quote(id)+",\"output\":"+run(mode,db)+"}");
      }catch(Throwable e){real.println("{\"error\":"+quote(e.toString())+"}");}
    }
  }
  static void ConstantConfig(String k){utils.Constant.KValue=Integer.parseInt(k);}
  static String run(String mode,Database db){
    if(mode.equals("w")){WUIPAlgo a=new WUIPAlgo();List<Itemset> returned=a.work(db,null);return "{\"heap\":"+results(a.getTopKUFPs())+",\"return_size\":"+returned.size()+",\"stats\":"+a.getStats().toJson()+"}";}
    if(mode.equals("h")){TNUFPHybridAlgo a=new TNUFPHybridAlgo();List<Itemset> returned=a.work(db,null);return "{\"heap\":"+results(a.getTopKUFPs())+",\"return_size\":"+returned.size()+",\"stats\":"+a.getStats().toJson()+"}";}
    if(mode.equals("t")){TUFPAlgo a=new TUFPAlgo(db);return "{\"heap\":"+results(a.getTopKUFPs())+",\"stats\":"+a.getStats().toJson()+"}";}
    throw new IllegalArgumentException(mode);
  }
  static void units(){
    TPNList l=new TPNList();l.add(new TPNCode(3,1,new BigDecimal("0.4"),new BigDecimal("0.5")));
    real.println("{\"unit\":\"exponent\",\"r\":3,\"calExpSup\":"+quote(Operation.calExpSup(l,3).toPlainString())+",\"getExpSup\":"+quote(l.getExpSup(3).toPlainString())+"}");
    TPNList child=new TPNList(),parent=new TPNList();child.add(new TPNCode(3,1,new BigDecimal("0.4"),new BigDecimal("0.2")));parent.add(new TPNCode(1,4,new BigDecimal("0.3"),new BigDecimal("0.9")));
    TPNList join=Operation.Intersection(child,parent);
    real.println("{\"unit\":\"proxy\",\"child_proxy\":\"0.2\",\"parent_proxy\":\"0.9\",\"joined_proxy\":"+quote(join.get(0).getpProxy().toPlainString())+"}");
    CUPList a=new CUPList(new Itemset(1)),b=new CUPList(new Itemset(2));
    a.addUP(new UP(1,new BigDecimal("0.1"),BigDecimal.ONE));b.addUP(new UP(1,new BigDecimal("0.2"),BigDecimal.ONE));
    real.println("{\"unit\":\"tinyBound\",\"ratio\":"+quote(new BigDecimal("0.000000000001").divide(BigDecimal.ONE).toPlainString())+"}");
  }
}
