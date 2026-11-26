package algo.UP;

import DataObject.Database;
import DataObject.Operation;
import DataObject.Transaction;
import utils.Constant;
import utils.PreparedDatabase;

import java.util.*;

/** Legacy exploratory prototype; not an exact or surrogate top-k implementation. */
public class UPTree {
    private HashMap<Integer, Double> iList;
    private UPNode root;
    private Double minSup;

    Map<Integer,ArrayList<Integer>> transactions = new TreeMap<>();
    Map<Integer,Map<Integer,Double>> probalities = new TreeMap<>();

    HashMap<Integer, Double> expSup = new HashMap<>();
    ArrayList<Integer> arr1_item = new ArrayList<>();

    private HashSet<Integer> isDeleted = new HashSet<>();
    private HashMap<Integer, List<UPNode>> headerTable = new HashMap<>();
    public UPTree(Database db, ArrayList<Double> weigth){
        PreparedDatabase prepared = new PreparedDatabase(db);
        this.minSup = Constant.minFreqSup * prepared.transactionCount;
        iList = new HashMap<>();
        root = new UPNode(-1, null);

        for (Transaction line : db) {
            int tid = line.getTid();
            int item = line.getItemId();
            double proba = line.getProbability();
            if (proba == 0.0 || !Double.isFinite(proba))
                throw new IllegalArgumentException("Legacy UP prototype cannot represent this decimal probability as double");
            transactions.computeIfAbsent(tid, key -> new ArrayList<>()).add(item);
            probalities.computeIfAbsent(tid, key -> new HashMap<>());
            if (expSup.containsKey(item))
                expSup.put(item, expSup.get(item) + proba);
            else
                expSup.put(item, proba);
            probalities.get(tid).put(item, proba);
        }

        for (int i : prepared.itemOrder){
            if (!expSup.containsKey(i))
                continue;
            double count = expSup.get(i);
            if (Double.compare(count, minSup) >= 0)
                arr1_item.add(i);

        }
        arr1_item.sort(Comparator.comparingInt(prepared.rank::get));
        for (int i : transactions.keySet()){
            if (transactions.get(i).isEmpty())
                continue;
            transactions.get(i).removeIf((a) -> expSup.get(a) < minSup);
            transactions.get(i).sort(Comparator.comparingInt(prepared.rank::get));
            insertEdge(i, transactions.get(i));
//            break;
        }
        visitTree(root);
        System.out.println("Map: " + iList);
        System.out.println(headerTable.get(5));
        for (Map.Entry<Integer, Double> entry : iList.entrySet()) {
            if (Double.compare(entry.getValue(), minSup) < 0) {
                isDeleted.add(entry.getKey());
                for (UPNode node : headerTable.get(entry.getKey())) {
                    node.setDelete(true);
                }
            }
        }

        printTree(root);
    }

    public UPTree() {
        this.minSup = Constant.minFreqSup * Operation.numberTransaction;
        iList = new HashMap<>();
        root = new UPNode(-1, null);
    }
    public void visitTree() {
        headerTable.clear();
        visitTree(root);
    }
    private void visitTree(UPNode node) {
        for (var entry: node.getChildren()) {
            if (!entry.isDelete()){
                if (headerTable.containsKey(entry.getId())) {
                    headerTable.get(entry.getId()).add(entry);
                }
                else{
                    List<UPNode> list = new ArrayList<>();
                    list.add(entry);
                    headerTable.put(entry.getId(), list);
                }
            }
            visitTree(entry);
        }
    }

    void insertEdge(int tid, ArrayList<Integer> t){
        UPNode current = root;
        ArrayList<Double> M = new ArrayList<>();
        for (int i = 0; i < 5; i++)
            M.add(0.0);
        for (int i = 0; i < t.size(); i++){
            Integer item = t.get(i);
            double probaItem = probalities.get(tid).get(item);
            UPNode child = current.getChild(item);
            child.updateM(M);
            double probMax = 0;
            for (int j = 0 ; j < i; j++){
                Integer preItem = t.get(j);
                double probaPreItem = probalities.get(tid).get(preItem);
                child.updateTpList(preItem, probaPreItem, probaItem);
                probMax = Math.max(probMax, probaPreItem * probaItem);
            }

            if (probMax > 0.0){
                if (iList.containsKey(item)){
                    double val = iList.get(item);
                    val += probMax;
                    iList.put(item, val);
                }
                else{
                    iList.put(item, probMax);
                }
            }
            updateM(M, probaItem);
            current = child;
        }
    }
    private void printTree(UPNode node) {
        if (node.getId() != -1 || node.isDelete()) { // Skip printing the root node
            System.out.println("Node ID: " + node.getId() + ", M: " + node.getM() + ", TPList: " + node.getTpList());
        }
        for (var entry: node.getChildren()) {
            printTree(entry);
        }
    }

    private void updateM(ArrayList<Double> M, Double newItem){
        if (M.get(M.size()-1) >= newItem){
            return;
        }
        int i = 0;
        for (; i < 5; i++){
            if (M.get(i) < newItem){
                break;
            }
        }
        for (int j = 4; j > i; j--){
            M.set(j, M.get(j - 1));
        }
        M.set(i, newItem);
    }

    public HashMap<Integer, List<UPNode>> getHeaderTable() {
        return headerTable;
    }



    public UPTree buildConditionalTree(UPTree currentTree, int item, int size){
        UPTree upTree = new UPTree();
        if (currentTree.getHeaderTable().containsKey(item)){
            HashMap<Integer, Double> iList = new HashMap<>();
            for (var node: currentTree.getHeaderTable().get(item)){
                for (var entry: node.getTpList().entrySet()){
                    if (iList.containsKey(entry.getKey())){
                        double val = iList.get(entry.getKey());
                        val += entry.getValue();
                        iList.put(entry.getKey(), val);
                    }
                    else{
                        iList.put(entry.getKey(), entry.getValue());
                    }
                }
                UPNode parent = node.getParent();
                ArrayList<UPNode> path = new java.util.ArrayList<>();
                while (parent != null && parent.getId() != -1){
                    if (!parent.isDelete()){
                        path.add(parent);
                    }
                    parent = parent.getParent();
                }
                Collections.reverse(path);
                System.out.println(path);
                if (size == 1){
                    upTree.addTransaction(path, null);
                }
                else {
                    ArrayList<Double> mFromNode = new ArrayList<>(node.getM());
                    upTree.addTransaction(path, mFromNode);
                }
            }
            upTree.iList = iList;
            upTree.visitTree();
            System.out.println(upTree.getHeaderTable());
            for (Map.Entry<Integer, Double> entry : upTree.iList.entrySet()) {
                if (Double.compare(entry.getValue(), minSup) < 0) {
                    upTree.isDeleted.add(entry.getKey());
                    System.out.println("Delete item: " + entry.getKey() + " with sup: " + entry.getValue());
                    for (UPNode node : upTree.headerTable.getOrDefault(entry.getKey(), List.of())) {
                        node.setDelete(true);
                    }
                }
            }
            System.out.println("Conditional Tree for item " + item);
            upTree.printTree(upTree.root);
        }
        return upTree;
    }


    private void addTransaction(ArrayList<UPNode> path, ArrayList<Double> mFromNode) {
        UPNode current = root;
        for (UPNode upNode : path) {
            Integer item = upNode.getId();
            UPNode child = current.getChild(item);
            if (mFromNode == null)
                child.updateM(new ArrayList<>(upNode.getM()));
            else
                child.updateM(new ArrayList<>(mFromNode));
            current = child;
        }
    }

    private void removeNode(UPNode node){
        if (node.getParent() != null){
            node.getParent().removeChild(node.getId());
        }
        node.setDelete(true);
        iList.remove(node.getId());
        node.getParent().addChildren(node.getChildren(), node.getId());
    }

}
