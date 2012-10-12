package utility;

import java.text.DecimalFormat;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;


import edu.stanford.nlp.math.SloppyMath;
import edu.stanford.nlp.util.Index;
import edu.stanford.nlp.util.Pair;

/**
 * Trie structure customized for a prefix parser.
 * Values are stored in log-form. To store in normal form inilialized with isLogScore set to false. 
 * Values accumulated are group by tag indices, and the trie is capable of answering two questions
 *   (a) what pre-terminals expand a sequence of terminals. 
 *     If we have 2 rules X -> a b c [0.1] and Y -> a b c [0.2] in the trie, findAllMap([a b c]) 
 *     will returns {X: 0.1, Y: 0.2}.
 *   (b) what pre-terminals expand a sequence of terminals as prefixes
 *     If we have 2 rules X -> a b c [0.1] X -> a b d [0.3] and Y -> a b c [0.2] in the trie, 
 *     findAllPrefixMap([a b]) returns {X: 0.4, Y:0.2}
 * Check the code for details.
 * 
 * @author Minh-Thang Luong, 2012
 *
 */
public class TrieSurprisal extends Trie<Integer, Pair<Integer, Double>> {
  
  private boolean isLogScore = true;
  private Map<Integer, Double> prefixValueMap; // map a tag id to a value
  private Map<Integer, Double> completeValueMap; // keep track of only complete strings
  protected List<TrieSurprisal> trieList;
  private DecimalFormat df = new DecimalFormat("0.0");
  
  public TrieSurprisal(){
    trieList = new ArrayList<TrieSurprisal>();
    keyList = new ArrayList<Integer>();
    size = 0;
    isEnd = false;
    
    prefixValueMap = new HashMap<Integer, Double>();
    completeValueMap = new HashMap<Integer, Double>();
  }
  
  public TrieSurprisal(boolean isLogScore){
    this();
    this.isLogScore = isLogScore;
  }
  
  @Override
  protected TrieSurprisal insertChild(Integer element) {
    // is element already there?
    for (int i = 0; i < size; i++) {
      if (keyList.get(i).equals(element)) {
        TrieSurprisal result = trieList.get(i);
        return result;
      }
    }
    
    // element is not in trie
    TrieSurprisal result = new TrieSurprisal(isLogScore);
    keyList.add(element);
    trieList.add(result);
    size++;
    return result;
  }
  
  private void updateValue(Pair<Integer, Double> pair, Map<Integer, Double> valueMap){
    double score = pair.second;
    if(valueMap.containsKey(pair.first)){ // contains in valueList
      double currentScore = valueMap.get(pair.first);
      
      if(isLogScore){
        score = SloppyMath.logAdd(currentScore, score); // aggregate log scores
      } else {
        score +=currentScore;
      }
    }
    valueMap.put(pair.first, score);
  }
  
  /**
   * Instead of simply appending to the list of values, we aggregate the values
   * @param elements
   * @param values values passed should have scores in log forms
   * @param i
   */
  @Override
  protected void insert(List<Integer> elements, List<Pair<Integer, Double>> values, int i) {
    // update value for all prefixes
    // we aggregate the result
    if(i>0){
      for (Pair<Integer, Double> pair : values) {
        updateValue(pair, prefixValueMap);
      }
    }
    
    // we've gotten to the end and 
    // the trie passed to us is empty (not null!)
    // setEnd and setValue
    int length = elements.size();
    if (i >= length) {
      setEnd(true);
      for (Pair<Integer, Double> pair : values) {
        updateValue(pair, completeValueMap);
      }
      return;
    }
    
    //System.err.println("\n# " + i + "\n" + this);
    TrieSurprisal nextT = insertChild(elements.get(i));
    nextT.insert(elements, values, i + 1);
  }
  
  public Map<Integer, Double> findAllMap(List<Integer> elements) {
    TrieSurprisal curTrie = findTrie(elements);
    
    if(curTrie == null){
      return null;
    }
    return curTrie.getValueMap(); // always return no matter if the curTrie ends
  }

  public Map<Integer, Double> findAllPrefixMap(List<Integer> elements) {
    TrieSurprisal curTrie = findTrie(elements);
    
    if(curTrie == null){
      return null;
    }
    return curTrie.getPrefixValueMap(); // always return no matter if the curTrie ends
  }
  
  @Override
  public TrieSurprisal findTrie(List<Integer> elements){
    TrieSurprisal curTrie = this;
    
    // go through each element
    for(Integer element : elements){
      curTrie = curTrie.findChild(element);
      
      if(curTrie == null){ // not found, not end of the string s
        break;
      }
    }
    
    return curTrie;
  }
  @Override
  protected TrieSurprisal findChild(Integer element) {
    for (int i = 0; i < size; i++) {
      if (keyList.get(i).equals(element)) {
        return trieList.get(i);
      }
    }
    return null;
  }
  
  public String toString(Index<String> wordIndex, Index<String> tagIndex){
    StringBuffer sb = new StringBuffer();
    
    if(prefixValueMap.size() > 0){
      sb.append("prefix={");
      for (int iT : prefixValueMap.keySet()) {
        sb.append(tagIndex.get(iT) + "=" + df.format(prefixValueMap.get(iT)) + ", ");
      }
      sb.delete(sb.length()-2, sb.length());
      sb.append("}");
    }
    if(completeValueMap.size() > 0){
      sb.append(", end={");
      for (int iT : completeValueMap.keySet()) {
        sb.append(tagIndex.get(iT) + "=" + df.format(completeValueMap.get(iT)) + ", ");
      }
      sb.delete(sb.length()-2, sb.length());
      sb.append("}");
    }
    
    int i=0;
    for(Integer element : keyList){
      trieList.get(i).setIndent(indent + " ");
      sb.append("\n" + indent + wordIndex.get(element) 
          + ":" + trieList.get(i).toString(wordIndex, tagIndex));
      ++i;
    }
    return sb.toString();
  }
  
  public Map<Integer, Double> getValueMap() {
    return completeValueMap;
  }
  
  public Map<Integer, Double> getPrefixValueMap() {
    return prefixValueMap;
  }
}
