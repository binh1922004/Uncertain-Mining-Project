import DataObject.*;
import utils.OptimizedTidNTUFP;
import java.io.*;
import java.util.*;
public final class OptimizedAuditRunner {
    public static void main(String[] args)throws Exception {
        BufferedReader reader=new BufferedReader(new InputStreamReader(System.in));String line;
        while((line=reader.readLine())!=null) {
            try {
                String[] p=line.split("\\|",-1);String[] mode=p[1].split(":");Database db=new Database();
                if(!p[5].isEmpty())for(String row:p[5].split(";")){String[] r=row.split(",");db.add(new Transaction(Integer.parseInt(r[0]),Integer.parseInt(r[1]),r[2]));}
                OptimizedTidNTUFP miner=new OptimizedTidNTUFP(Integer.parseInt(p[2]),mode[0],mode.length==1||Boolean.parseBoolean(mode[1]),mode.length<3||Boolean.parseBoolean(mode[2]));
                var out=miner.mine(db);
                System.out.println("{\"id\":"+AuditRunner.quote(p[0])+",\"heap\":"+AuditRunner.results(out)+",\"stats\":"+miner.statsJson()+"}");
            }catch(Throwable error){System.out.println("{\"error\":"+AuditRunner.quote(error.toString())+"}");}
        }
    }
}
