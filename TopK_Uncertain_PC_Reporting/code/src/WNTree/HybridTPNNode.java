package WNTree;

import DataObject.TidProb;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

public class HybridTPNNode extends TPNNode {
    private List<TidProb> tidList = new ArrayList<>();

    public HybridTPNNode(int item_name) {
        super(item_name);
    }

    public HybridTPNNode(int item_name, int pre, int pos, BigDecimal tPCap, BigDecimal pProxy) {
        super(item_name, pre, pos, tPCap, pProxy);
    }

    public void addTidProb(TidProb tidProb) {
        this.tidList.add(tidProb);
    }

    public List<TidProb> getTidList() {
        return tidList;
    }

    public void setTidList(List<TidProb> tidList) {
        this.tidList = tidList;
    }

    @Override
    protected TPNNode createNewNode(int name) {
        return new HybridTPNNode(name);
    }
}
