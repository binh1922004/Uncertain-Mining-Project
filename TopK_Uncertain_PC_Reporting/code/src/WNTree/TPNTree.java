package WNTree;
import DataObject.*;
import utils.PreparedDatabase;
import java.math.BigDecimal;
import java.util.*;
/** Prefix tree with one deterministic global order: decreasing sigma, then item ID. */
public class TPNTree {
    protected final ArrayList<Integer> arr1_item=new ArrayList<>();
    protected final HashMap<Integer,Integer> pos=new HashMap<>();
    protected final ArrayList<TPNListItemset> item1sz_list=new ArrayList<>();
    protected int pre,post;
    protected TPNNode root;
    protected final HashMap<Integer,BigDecimal> expSup=new HashMap<>();
    protected TPNNode createRootNode(){return new TPNNode(-1);}
    protected TPNListItemset createTPNListItemset(int item){return new TPNListItemset(item);}
    protected void onVisitNode(TPNNode node,int tid,int item,BigDecimal p){}
    protected TPNCode createTPNCode(TPNNode node){return new TPNCode(node.getPre(),node.getPos(),node.gettPCap(),node.getpProxy());}
    public TPNTree(Database db){init(db);}
    protected void init(Database db){
        PreparedDatabase prepared=new PreparedDatabase(db);
        root=createRootNode();expSup.putAll(prepared.singletonSupports);arr1_item.addAll(prepared.itemOrder);
        for(int item:arr1_item){
            pos.put(item,item1sz_list.size());TPNListItemset entry=createTPNListItemset(item);
            entry.getTpnList().setSingletonSupport(expSup.get(item));item1sz_list.add(entry);
        }
        for(var transaction:prepared.transactions.entrySet()){
            var ordered=new ArrayList<>(transaction.getValue().keySet());ordered.sort(Comparator.comparingInt(pos::get));
            TPNNode current=root;BigDecimal m1=BigDecimal.ZERO,m2=BigDecimal.ZERO;
            for(int item:ordered){
                BigDecimal p=transaction.getValue().get(item);TPNNode child=current.getchild(item);
                child.addtPCap(p.multiply(m1));child.maxpProxy(m2);
                onVisitNode(child,transaction.getKey(),item,p);current.addChild(child);current=child;
                if(p.compareTo(m1)>=0){m2=m1;m1=p;}else if(p.compareTo(m2)>0)m2=p;
            }
        }
        pre=post=0;dfs(root);
    }
    protected void dfs(TPNNode node){
        node.setPre(pre++);
        var children=new ArrayList<>(node.getChild_list());children.sort(Comparator.comparingInt(c->pos.get(c.getItem_name())));
        for(TPNNode child:children){dfs(child);item1sz_list.get(pos.get(child.getItem_name())).addTPNCode(createTPNCode(child));}
        node.setPos(post++);
    }
    public HashMap<Integer,BigDecimal> getExpSup1sz(){return new HashMap<>(expSup);}
    public ArrayList<TPNListItemset> getItem1sz_list(){return item1sz_list;}
}
