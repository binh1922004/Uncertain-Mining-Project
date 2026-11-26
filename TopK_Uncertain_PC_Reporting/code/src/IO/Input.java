package IO;
import DataObject.*;
import utils.Constant;
import utils.PreparedDatabase;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.ArrayList;
public class Input {
    /** Header n m, then records TID item probability. m is the distinct item count. */
    public Database input(String filename) throws IOException {
        Database db=new Database();
        try(BufferedReader reader=Files.newBufferedReader(Path.of(filename),StandardCharsets.UTF_8)) {
            String line;int number=0;boolean header=false;
            while((line=reader.readLine())!=null) {
                number++;line=line.trim();
                if(line.isEmpty() || line.startsWith("#")) continue;
                String[] f=line.split("\\s+");
                try {
                    if(!header) {
                        if(f.length!=2) throw new IllegalArgumentException("Header requires n m");
                        db.setDeclaredCounts(Integer.parseInt(f[0]),Integer.parseInt(f[1]));header=true;
                    } else {
                        if(f.length!=3) throw new IllegalArgumentException("Record requires TID item probability");
                        db.add(new Transaction(Integer.parseInt(f[0]),Integer.parseInt(f[1]),f[2]));
                    }
                } catch(IllegalArgumentException e) { throw new IOException(filename+":"+number+": "+e.getMessage(),e); }
            }
            if(!header) throw new IOException(filename+": missing header");
        }
        try {
            PreparedDatabase p=new PreparedDatabase(db);
            Operation.numberTransaction=p.transactionCount;Constant.numberItems=p.itemOrder.size();
        } catch(IllegalArgumentException e) { throw new IOException(filename+": "+e.getMessage(),e); }
        return db;
    }
    public ArrayList<Double> inputWeight(String filename) throws IOException {
        ArrayList<Double> result=new ArrayList<>();
        for(String line:Files.readAllLines(Path.of(filename),StandardCharsets.UTF_8)) {
            if(line.isBlank()) continue;
            double v=Double.parseDouble(line.trim());
            if(!Double.isFinite(v)) throw new IOException("Non-finite legacy weight");result.add(v);
        }
        return result;
    }
}
