package utils;
import DataObject.Itemset;
import java.util.Comparator;
public final class ResultRanking {
    private ResultRanking() {}
    public static final Comparator<Itemset> BEST_FIRST=(a,b)->{
        int c=b.getExpSup().compareTo(a.getExpSup());if(c!=0)return c;
        c=Integer.compare(a.size(),b.size());if(c!=0)return c;
        for(int i=0;i<a.size();i++) { c=Integer.compare(a.identifierAt(i),b.identifierAt(i));if(c!=0)return c; }
        return 0;
    };
}
