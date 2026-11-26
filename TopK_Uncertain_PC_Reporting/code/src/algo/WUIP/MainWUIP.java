package algo.WUIP;
import java.io.IOException;
public class MainWUIP {
    public static void main(String[] args)throws IOException{utils.ExperimentRunner.run("NTUFP",args);}
    public static String findToPath(String name){return java.nio.file.Path.of(utils.Constant.datasetBasePath,name).toAbsolutePath().toString();}
}
