package DataObject;
import java.util.ArrayList;
public class Database extends ArrayList<Transaction> {
    private static final long serialVersionUID=1L;
    private int declaredTransactions=-1, declaredItems=-1;
    public void setDeclaredCounts(int n,int m) {
        if(n<0 || m<0) throw new IllegalArgumentException("Negative header count");
        declaredTransactions=n;declaredItems=m;
    }
    public int getDeclaredTransactions() { return declaredTransactions; }
    public int getDeclaredItems() { return declaredItems; }
}
