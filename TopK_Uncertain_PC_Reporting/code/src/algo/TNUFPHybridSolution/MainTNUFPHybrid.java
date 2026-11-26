package algo.TNUFPHybridSolution;
import java.io.IOException;
public class MainTNUFPHybrid {
    public static void main(String[] args)throws IOException{utils.ExperimentRunner.run("TidNTUFP",args);}
    public static String findToPath(String name){return java.nio.file.Path.of(utils.Constant.datasetBasePath,name).toAbsolutePath().toString();}
}
