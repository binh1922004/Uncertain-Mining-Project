package utils;
import DataObject.*;
import java.math.BigDecimal;
import java.util.*;
/** Direct decimal reference helper, independent of TID input order. Tests use a separate Fraction oracle. */
public class ExactExpSupCalculator {
    private final PreparedDatabase data;
    public ExactExpSupCalculator(Database db){data=new PreparedDatabase(db);}
    public BigDecimal computeExactExpSup(Itemset items){
        if(items==null || items.isEmpty())return BigDecimal.ZERO;
        if(new HashSet<>(items).size()!=items.size())throw new IllegalArgumentException("Repeated item");
        BigDecimal sum=BigDecimal.ZERO;
        for(var transaction:data.transactions.values()){
            BigDecimal product=BigDecimal.ONE;
            for(int item:items){BigDecimal p=transaction.get(item);if(p==null){product=BigDecimal.ZERO;break;}product=product.multiply(p);}
            sum=sum.add(product);
        }
        return sum;
    }
}
