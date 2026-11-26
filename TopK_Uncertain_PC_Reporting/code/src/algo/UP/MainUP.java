package algo.UP;
import DataObject.Database;
import java.io.IOException;
/** Legacy exploratory prototype. This is not a validated top-k miner. */
public class MainUP {
    public static void main(String[] args)throws IOException{
        if(args.length<1 || args.length>2)throw new IllegalArgumentException("Usage: MainUP INPUT [CONDITIONAL_ITEM]");
        Database db=new IO.Input().input(args[0]);UPTree tree=new UPTree(db,null);
        if(args.length==2)tree.buildConditionalTree(tree,Integer.parseInt(args[1]),1);
    }
}
