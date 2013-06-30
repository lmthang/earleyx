package parser;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import edu.stanford.nlp.util.DoubleList;

public class Measures {
  public static final String PREFIX = "prefix";
  public static final String SURPRISAL = "surprisal";
  public static final String ENTROPY = "entropy";
  public static final String ENTROPY_REDUCTION = "entropyreduction"; // max(0.0, current entropy - prev entropy)
  public static final String MULTI_RULE_COUNT = "multirulecount";
  public static final String MULTI_RHS_LENGTH_COUNT = "multirhslengthcount";
  public static final String MULTI_FUTURE_LENGTH_COUNT = "multifuturelengthcount";
  public static final String MULTI_RHS_LENGTH = "multirhslength";
  public static final String MULTI_FUTURE_LENGTH = "multifuturelength";
  public static final String STRINGPROB = "stringprob";
  
//map a measure to True if it's computation could be done in log-form, e.g., surprisal: True, entropy: False
  public static final Map<String, Boolean> measureLogFlagMap
                  = new HashMap<String, Boolean>(){
                    private static final long serialVersionUID = 1L;
                  {
                    put(PREFIX, true); 
                    put(ENTROPY, false);
                    put(MULTI_RULE_COUNT, false);
                    put(MULTI_RHS_LENGTH_COUNT, false);
                    put(MULTI_FUTURE_LENGTH_COUNT, false);
                    put(MULTI_RHS_LENGTH, false);
                    put(MULTI_FUTURE_LENGTH, false);
                    }};
  
  // map objective names, e.g., "prefix", "synPrefix", etc., to a list of values
  
  // objectiveMap.get("prefix")[position]: return an objective value at a particular position
  private Map<String, DoubleList> measureMap;
  
  
  public Measures(Set<String> objectives) {
    measureMap = new HashMap<String, DoubleList>();
    
    for (String obj : objectives) {
      measureMap.put(obj, new DoubleList());
    }
  }
  
  public Measures(Set<String> objectives, int numWords) {
    measureMap = new HashMap<String, DoubleList>();
    
    for (String obj : objectives) {
      DoubleList values = new DoubleList(numWords+1);
      for (int i = 0; i <= numWords; i++) {
        values.add(0.0);
      }
      measureMap.put(obj, values);
    }
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
  
  public int numPrefixValues(){
    return numValues(PREFIX);
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
    measureMap.get(obj).add(value);
  }
  
  public void setValue(String obj, int pos, double value){
    measureMap.get(obj).set(pos, value);
  }
  
  public double getValue(String obj, int pos){
    return measureMap.get(obj).get(pos);
  }
  
  public double[] getValueArray(String obj){
    return measureMap.get(obj).toArray();
  }
  
  // prefix
  public void addPrefix(double value){
    addValue(PREFIX, value);
  }
  
  public void setPrefix(int pos, double value){
    setValue(PREFIX, pos, value);
  }
  
  public double getPrefix(int pos){
    return getValue(PREFIX, pos);
  }
  
  public double[] getPrefixArray(){
    return getValueArray(PREFIX);
  }
  
  // entropy
  public void addEntropy(double value){
    addValue(ENTROPY, value);
  }
  
  public double getEntropy(int pos){
    return getValue(ENTROPY, pos);
  }
}
