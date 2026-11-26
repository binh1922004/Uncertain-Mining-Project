package algo.TUFP;
import java.io.IOException;
public class MainTUFP {
    public static void main(String[] args)throws IOException{utils.ExperimentRunner.run("TUFP",args);}
    public static String findToPath(String name){return java.nio.file.Path.of(utils.Constant.datasetBasePath,name).toAbsolutePath().toString();}
}
