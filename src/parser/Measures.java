package parser;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import edu.stanford.nlp.math.ArrayMath;
import edu.stanford.nlp.util.DoubleList;

public class Measures {
  public static final String PREFIX = "prefix";
  public static final String MULTI_PREFIX = "multiprefix";
  public static final String PCFG_PREFIX = "pcfgprefix";
  
  public static final String SURPRISAL = "surprisal";
  public static final String ENTROPY = "entropy";
  public static final String ENTROPY_REDUCTION = "entropyreduction"; // max(0.0, current entropy - prev entropy)
  
  public static final String MULTI_RULE_COUNT = "multirulecount";
  public static final String PCFG_RULE_COUNT = "pcfgrulecount";
  public static final String MULTI_FUTURE_LENGTH_COUNT = "multifuturelengthcount";
  public static final String PCFG_FUTURE_LENGTH_COUNT = "pcfgfuturelengthcount";
  
  // to be remove
  public static final String MULTI_RHS_LENGTH = "multirhslength";
  public static final String MULTI_RHS_LENGTH_COUNT = "multirhslengthcount";
  public static final String MULTI_FUTURE_LENGTH = "multifuturelength";
  public static final String PCFG_FUTURE_LENGTH = "pcfgfuturelength";
  public static final String ALL_FUTURE_LENGTH = "allfuturelength";
  
  
  
  
  
  public static final String STRINGPROB = "stringprob";
  
  // map objective names, e.g., "prefix", "synPrefix", etc., to a list of values
  
  // objectiveMap.get("prefix")[position]: return an objective value at a particular position
  private Map<String, DoubleList> measureMap;
  
  // if autoCompressSize > 0, automatically sum values of a measure if the double list size is equal to the autoCompressSize
  private int autoCompressSize = -1; 
  
  // contains measures whose values are stored in log domain
  private Set<String> logMeasures;
  public Measures(Set<String> objectives) {
    measureMap = new HashMap<String, DoubleList>();
    logMeasures = new HashSet<String>();
    
    for (String obj : objectives) {
      measureMap.put(obj, new DoubleList());
    }
  }
  
  public Measures(Set<String> objectives, int numWords) {
    measureMap = new HashMap<String, DoubleList>();
    logMeasures = new HashSet<String>();
    
    for (String obj : objectives) {
      DoubleList values = new DoubleList(numWords+1);
      for (int i = 0; i <= numWords; i++) {
        values.add(0.0);
      }
      measureMap.put(obj, values);
    }
  }
  
  public void setAutoCompressSize(int size){
    autoCompressSize = size;
  }
  
  public void addMeasures(Set<String> objectives, int numWords){
    for (String obj : objectives) {
      DoubleList values = new DoubleList(numWords+1);
      for (int i = 0; i <= numWords; i++) {
        values.add(0.0);
      }
      measureMap.put(obj, values);
    }
  }
  
  public int numValues(String obj){
    return measureMap.get(obj).size();
  }
  
  public void addLogMeasure(String measure){
    logMeasures.add(measure);
  }
  /*** Getters & Setters ***/
  public List<Double> getSentList(String obj){
    List<Double> values = new ArrayList<Double>();
    DoubleList dl = measureMap.get(obj);
    for (int i = 1; i < dl.size(); i++) {
      values.add(dl.get(i));
    }
    return values;
  }
  
  public void addValue(String obj, double value){
    DoubleList dl = measureMap.get(obj); 
    dl.add(value);
    
    if(dl.size() == autoCompressSize){
      measureMap.put(obj, compressDoubleList(obj, dl));
    }
  }
  
  private DoubleList compressDoubleList(String obj, DoubleList dl){
    double totalValue = 0.0;
    if(logMeasures.contains(obj)){
      totalValue = ArrayMath.logSum(dl.toArray());
    } else {
      totalValue = ArrayMath.sum(dl.toArray());
    }
    DoubleList newDL = new DoubleList();
    newDL.add(totalValue);
    
    return newDL;
  }
  public void setValue(String obj, int pos, double value){
    measureMap.get(obj).set(pos, value);
  }
  
  public double getValue(String obj, int pos){
    return measureMap.get(obj).get(pos);
  }
  
  public double getTotalValue(String obj){
    DoubleList dl = compressDoubleList(obj, measureMap.get(obj));
    measureMap.put(obj, compressDoubleList(obj, dl));
    return dl.get(0);
  }
  
  public double[] getValueArray(String obj){
    return measureMap.get(obj).toArray();
  }
  
  // prefix
//  public int numPrefixValues(){
//    return numValues(PREFIX);
//  }
//  public void addPrefix(double value){
//    addValue(PREFIX, value);
//  }
//  
//  public void setPrefix(int pos, double value){
//    setValue(PREFIX, pos, value);
//  }
//  
//  public double getPrefix(int pos){
//    return getValue(PREFIX, pos);
//  }
//  
//  public double[] getPrefixArray(){
//    return getValueArray(PREFIX);
//  }
  
  // entropy
  public void addEntropy(double value){
    addValue(ENTROPY, value);
  }
  
  public double getEntropy(int pos){
    return getValue(ENTROPY, pos);
  }
}
