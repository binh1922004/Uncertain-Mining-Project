package DataObject;
import java.math.BigDecimal;
import java.util.*;
import java.util.function.*;
/** Canonical enumeration tuple plus an independent cached identifier key for ties. */
public class Itemset extends ArrayList<Integer> {
    private static final long serialVersionUID=1L;
    private BigDecimal expSup=BigDecimal.ZERO;
    private transient int[] identifierKey;
    public Itemset() {}
    public Itemset(Integer i) { super.add(i);identifierKey=new int[]{i}; }
    public Itemset(Integer i,BigDecimal s) { this(i);expSup=s; }
    public Itemset(ArrayList<Integer> x) { super(x); }
    public Itemset(int[] x) { for(int i:x) super.add(i); }
    public Itemset(Itemset x,BigDecimal s) { super(x);expSup=s;identifierKey=x.key().clone(); }
    private int[] key() {
        if(identifierKey==null) { identifierKey=stream().mapToInt(Integer::intValue).toArray();Arrays.sort(identifierKey); }
        return identifierKey;
    }
    public int identifierAt(int i) { return key()[i]; }
    public static Itemset extended(Itemset prefix,int item) {
        int[] old=prefix.key();int at=Arrays.binarySearch(old,item);
        if(at>=0) throw new IllegalArgumentException("Repeated extension item");at=-at-1;
        Itemset out=new Itemset(prefix,BigDecimal.ZERO);out.add(item);out.identifierKey=new int[old.length+1];
        System.arraycopy(old,0,out.identifierKey,0,at);out.identifierKey[at]=item;
        System.arraycopy(old,at,out.identifierKey,at+1,old.length-at);return out;
    }
    public BigDecimal getExpSup() { return expSup; }
    public void setExpSup(BigDecimal s) { expSup=Objects.requireNonNull(s); }
    public void add(Itemset x) { addAll(x); }
    @Override public boolean add(Integer x) { identifierKey=null;return super.add(x); }
    @Override public void add(int i,Integer x) { identifierKey=null;super.add(i,x); }
    @Override public boolean addAll(Collection<? extends Integer> x) { identifierKey=null;return super.addAll(x); }
    @Override public boolean addAll(int i,Collection<? extends Integer> x) { identifierKey=null;return super.addAll(i,x); }
    @Override public Integer set(int i,Integer x) { identifierKey=null;return super.set(i,x); }
    @Override public Integer remove(int i) { identifierKey=null;return super.remove(i); }
    @Override public boolean remove(Object x) { identifierKey=null;return super.remove(x); }
    @Override public void clear() { identifierKey=null;super.clear(); }
    @Override public boolean removeAll(Collection<?> x) { identifierKey=null;return super.removeAll(x); }
    @Override public boolean retainAll(Collection<?> x) { identifierKey=null;return super.retainAll(x); }
    @Override public boolean removeIf(Predicate<? super Integer> x) { identifierKey=null;return super.removeIf(x); }
    @Override public void replaceAll(UnaryOperator<Integer> x) { identifierKey=null;super.replaceAll(x); }
    @Override protected void removeRange(int a,int b) { identifierKey=null;super.removeRange(a,b); }
    @Override public List<Integer> subList(int from,int to) {
        List<Integer> view=super.subList(from,to);
        return new AbstractList<>() {
            public Integer get(int i){return view.get(i);}
            public int size(){return view.size();}
            public Integer set(int i,Integer value){identifierKey=null;return view.set(i,value);}
            public void add(int i,Integer value){identifierKey=null;view.add(i,value);modCount++;}
            public Integer remove(int i){identifierKey=null;Integer old=view.remove(i);modCount++;return old;}
        };
    }
    @Override public Itemset clone() { return new Itemset(this,expSup); }
}
