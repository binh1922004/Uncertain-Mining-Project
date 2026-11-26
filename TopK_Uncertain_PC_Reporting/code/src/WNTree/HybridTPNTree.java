package WNTree;

import DataObject.Database;
import DataObject.Itemset;
import DataObject.TPNListItemset;
import DataObject.TidProb;

import java.math.BigDecimal;

public class HybridTPNTree extends TPNTree {

    public HybridTPNTree(Database db) {
        super(db);
    }

    @Override
    protected TPNNode createRootNode() {
        return new HybridTPNNode(-1);
    }

    @Override
    protected TPNListItemset createTPNListItemset(int item) {
        return new TPNListItemset(new Itemset(item), new HybridTPNList());
    }

    @Override
    protected void onVisitNode(TPNNode child, int tid, int item, BigDecimal proba) {
        if (child instanceof HybridTPNNode) {
            ((HybridTPNNode) child).addTidProb(new TidProb(tid, proba, proba));
        }
    }

    @Override
    protected TPNCode createTPNCode(TPNNode v) {
        if (v instanceof HybridTPNNode) {
            return new HybridTPNCode(
                v.getPre(),
                v.getPos(),
                v.gettPCap(),
                v.getpProxy(),
                ((HybridTPNNode) v).getTidList()
            );
        }
        return super.createTPNCode(v);
    }
}
