package algo.UP;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

public class UPNode {
    private Integer id;
    private ArrayList<UPNode> children = new ArrayList<>();
    private UPNode parent;
    private ArrayList<Double> M;
    private HashMap<Integer, Double> tpList;
    private boolean isDelete = false;

    public boolean isDelete() {
        return isDelete;
    }

    public void setDelete(boolean delete) {
        isDelete = delete;
    }

    public UPNode(Integer id, UPNode parent) {
        this.id = id;
        this.parent = parent;
        M = new ArrayList<>();
        for (int i = 0; i < 5; i++){
            M.add(0.0);
        }
        this.tpList = new HashMap<>();
    }
    public Integer getId() {
        return id;
    }

    public void setId(Integer id) {
        this.id = id;
    }

    public UPNode getParent() {
        return parent;
    }

    public void setParent(UPNode parent) {
        this.parent = parent;
    }

    public ArrayList<Double> getM() {
        return M;
    }

    public void setM(ArrayList<Double> m) {
        M = m;
    }

    public HashMap<Integer, Double> getTpList() {
        return tpList;
    }

    public UPNode getChild(Integer item) {
        for (var child : children) {
            if (child.getId().equals(item)) {
                return child;
            }
        }
        UPNode newChild = new UPNode(item, this);
        children.add(newChild);
        return newChild;
    }

    public void updateM(ArrayList<Double> m) {
        for (int i = 0; i < 5; i++){
            this.M.set(i, Math.max(this.M.get(i), m.get(i)));
        }
    }

    public void updateTpList(Integer preItem, double probaPreItem, double probaItem) {
        if (tpList.containsKey(preItem)){
            double val = tpList.get(preItem);
            val += probaPreItem * probaItem;
            tpList.put(preItem, val);
        }
        else{
            tpList.put(preItem, probaPreItem * probaItem);
        }
    }

    private void updateTpList(Integer item, double proba) {
        if (tpList.containsKey(item)){
            double val = tpList.get(item);
            System.out.println(val + " " + proba);
            val += proba;
            tpList.put(item, val);
        }
        else{
            tpList.put(item, proba);
        }
    }

    public void removeChild(Integer id) {
        children.removeIf(child -> child.getId().equals(id));
    }


    public void addChildren(ArrayList<UPNode> newChildren, Integer oldItem) {
        for (var child: newChildren){
            child.setParent(this);
            this.children.add(child);
        }
    }

    public ArrayList<UPNode> getChildren() {
        return children;
    }


    @Override
    public UPNode clone() {
        UPNode copy = new UPNode(this.id, null);

        copy.id = this.id;
        copy.isDelete = this.isDelete;

        if (this.M != null)
            copy.M = new ArrayList<>(this.M);

        return copy;
    }

    @Override
    public String toString() {
        return "UPNode{" +
                "id=" + id + "}";
    }
}
