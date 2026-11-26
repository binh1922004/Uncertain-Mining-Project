package IO;
import DataObject.Itemset;
import utils.ResultRanking;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;
public class Output {
    public static void writeItemsetsToFile(List<Itemset> itemsets,String filename)throws IOException{
        writeToPath(itemsets,Path.of("pattern").resolve(filename));
    }
    /** Deterministic best-first output; never sorts or otherwise mutates caller-owned patterns. */
    public static void writeToPath(Collection<Itemset> itemsets,Path path)throws IOException{
        var sorted=new ArrayList<Itemset>();for(Itemset x:itemsets)sorted.add(new Itemset(x,x.getExpSup()));
        sorted.sort(ResultRanking.BEST_FIRST);Path parent=path.toAbsolutePath().getParent();if(parent!=null)Files.createDirectories(parent);
        try(BufferedWriter writer=Files.newBufferedWriter(path,StandardCharsets.UTF_8)){
            for(Itemset x:sorted){
                for(int i=0;i<x.size();i++){if(i>0)writer.write(" ");writer.write(Integer.toString(x.identifierAt(i)));}
                writer.write(" : "+x.getExpSup().stripTrailingZeros().toString());writer.newLine();
            }
        }
    }
}
