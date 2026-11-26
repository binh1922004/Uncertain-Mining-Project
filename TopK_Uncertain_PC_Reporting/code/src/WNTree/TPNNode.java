package WNTree;

import DataObject.Operation;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.HashMap;

public class TPNNode {
    private int item_name;
    private final ArrayList<TPNNode> child_list = new ArrayList<>();
    private final HashMap<Integer,TPNNode> childrenByItem = new HashMap<>();
    private int pre;
    private int pos;
    private BigDecimal tPCap = BigDecimal.ZERO;
    private BigDecimal pProxy = BigDecimal.ZERO;

    public TPNNode(int item_name) {
        this.item_name = item_name;
    }

    public TPNNode(int item_name, int pre, int pos, BigDecimal tPCap, BigDecimal pProxy) {
        this.item_name = item_name;
        this.pre = pre;
        this.pos = pos;
        this.tPCap = tPCap;
        this.pProxy = pProxy;
    }

    // Getter & Setter
    public int getItem_name() {
        return item_name;
    }

    public void setItem_name(int item_name) {
        this.item_name = item_name;
    }

    public ArrayList<TPNNode> getChild_list() {
        return new ArrayList<>(child_list);
    }

    public void setChild_list(ArrayList<TPNNode> child_list) {
        var snapshot = new ArrayList<>(child_list);
        this.child_list.clear();
        childrenByItem.clear();
        for (TPNNode child : snapshot) addChild(child);
    }

    public int getPre() {
        return pre;
    }

    public void setPre(int pre) {
        this.pre = pre;
    }

    public int getPos() {
        return pos;
    }

    public void setPos(int pos) {
        this.pos = pos;
    }

    public BigDecimal gettPCap() {
        return tPCap;
    }

    public void settPCap(BigDecimal tPCap) {
        this.tPCap = tPCap;
    }

    public BigDecimal getpProxy() {
        return pProxy;
    }

    public void setpProxy(BigDecimal pProxy) {
        this.pProxy = pProxy;
    }

    // Logic methods
    protected TPNNode createNewNode(int name) {
        return new TPNNode(name);
    }

    public TPNNode getchild(int name) {
        TPNNode child=childrenByItem.get(name);
        return child==null?createNewNode(name):child;
    }

    public void addtPCap(BigDecimal value) {
        this.tPCap = this.tPCap.add(value);
    }

    public void maxpProxy(BigDecimal value) {
        if (this.pProxy.compareTo(value) < 0)
            this.pProxy = value;
    }

    public void addChild(TPNNode child) {
        if(childrenByItem.putIfAbsent(child.getItem_name(),child)==null)child_list.add(child);
    }
}
