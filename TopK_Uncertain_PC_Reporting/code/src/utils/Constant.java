package utils;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.BitSet;

public class Constant {
    public static double twu;
    public static ArrayList<Double> twuList;
    public static int numberItems = 0;
    public static String inputFileName = "foodmart";
    public static double minFreqSup = 0.18;
    public static double minRareSup = 70 / 100.0;
    public static int KValue = 900;
    public static Integer numberTransactions;
    public static boolean haveZero = false;
    public static HashMap<Integer, BitSet> maxList = new HashMap<>();
    public static HashMap<Integer, Integer> order = new HashMap<>();
    public static int numberOfRuns = 1;
    public static String datasetBasePath = ".";
}