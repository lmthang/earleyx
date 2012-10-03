package parser;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import edu.stanford.nlp.math.SloppyMath;
import edu.stanford.nlp.parser.lexparser.IntTaggedWord;
import edu.stanford.nlp.util.Index;
import edu.stanford.nlp.util.Pair;

/**
 * Trie structure customized for prefix parser.
 * Values are stored in log-form. 
 * At each trie node, when a new value is added, which has pair id matches
 * existing pair ids, values are accummulated and SloopyMath.logAdd is used.
 * Check the code for details.
 * 
 * @author lmthang
 *
 */
public class TrieSurprisal extends Trie<IntTaggedWord, Pair<Integer, Double>> {
  
  private boolean isLogScore = true;
  //protected List<Pair<Integer, Double>> prefixValueList; // list of values associated with the trie which forms a prefix of somestring or could be a complete string itself
  private Map<Integer, Double> prefixValueMap; // map id of a pair to its index in the value list
  private Map<Integer, Double> valueMap; // for valueList, keep track of only complete strings
  protected List<TrieSurprisal> trieList;
  
  public TrieSurprisal(){
    trieList = new ArrayList<TrieSurprisal>();
    keyList = new ArrayList<IntTaggedWord>();
    size = 0;
    isEnd = false;
    valueList = null;// we don't initialize valueList
    
    prefixValueMap = new HashMap<Integer, Double>();
    valueMap = new HashMap<Integer, Double>();
  }
  
  public TrieSurprisal(boolean isLogScore){
    this();
    this.isLogScore = isLogScore;
  }
  
  @Override
  protected TrieSurprisal insertChild(IntTaggedWord element) {
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
  
  protected void addValuePair(Pair<Integer, Double> pair, Map<Integer, Double> pairIdMap){
    if(pairIdMap.containsKey(pair.first)){ // contains in valueList
      double currentScore = pairIdMap.get(pair.first);
      
      if(isLogScore){
        assert(currentScore <= 0 && pair.second <= 0);
        currentScore = SloppyMath.logAdd(currentScore, pair.second); // aggregate
      } else {
        currentScore += pair.second;
      }
      pairIdMap.put(pair.first, currentScore);
    } else { // new pair, append at the end, update map id
      pairIdMap.put(pair.first, pair.second);
    }
  }
  
  /**
   * Instead of simply appending to the list of values, we aggregate the values
   * @param elements
   * @param values values passed should have scores in log forms
   * @param i
   */
  @Override
  protected void insert(List<IntTaggedWord> elements, List<Pair<Integer, Double>> values, int i) {
    // update value for all prefixes
    // we aggregate the result
    if(i>0){
      for (Pair<Integer, Double> pair : values) {
        addValuePair(pair, prefixValueMap);
      }
    }
    
    // we've gotten to the end and 
    // the trie passed to us is empty (not null!)
    // setEnd and setValue
    int length = elements.size();
    if (i >= length) {
      setEnd(true);
      for (Pair<Integer, Double> pair : values) {
        addValuePair(pair, valueMap);
      }
      return;
    }
    
    //System.err.println("\n# " + i + "\n" + this);
    TrieSurprisal nextT = insertChild(elements.get(i));
    nextT.insert(elements, values, i + 1);
  }
  
  public Map<Integer, Double> findAllMap(List<IntTaggedWord> elements) {
    TrieSurprisal curTrie = findTrie(elements);
    
    if(curTrie == null){
      return null;
    }
    return curTrie.getValueMap(); // always return no matter if the curTrie ends
  }

  public Map<Integer, Double> findAllPrefixMap(List<IntTaggedWord> elements) {
    TrieSurprisal curTrie = findTrie(elements);
    
    if(curTrie == null){
      return null;
    }
    return curTrie.getPrefixValueMap(); // always return no matter if the curTrie ends
  }
  
  protected TrieSurprisal findTrie(List<IntTaggedWord> elements){
    TrieSurprisal curTrie = this;
    
    // go through each element
    for(IntTaggedWord element : elements){
      curTrie = curTrie.findChild(element);
      
      if(curTrie == null){ // not found, not end of the string s
        return null;
      }
    }
    
    return curTrie;
  }
  @Override
  protected TrieSurprisal findChild(IntTaggedWord element) {
    for (int i = 0; i < size; i++) {
      if (keyList.get(i).equals(element)) {
        return trieList.get(i);
      }
    }
    return null;
  }
  
  public String toString(Index<String> wordIndex){
    StringBuffer sb = new StringBuffer();
    
    if(prefixValueMap.size() > 0){
      sb.append("prefix=" + prefixValueMap);
    }
    if(valueMap.size() > 0){
      sb.append(", end=" + valueMap);
    }
    
    int i=0;
    for(IntTaggedWord element : keyList){
      trieList.get(i).setIndent(indent + " ");
      sb.append("\n" + indent + element.wordString(wordIndex) + ":" + trieList.get(i).toString(wordIndex));
      ++i;
    }
    return sb.toString();
  }
  
  protected Map<Integer, Double> getValueMap() {
    return valueMap;
  }
  
  protected Map<Integer, Double> getPrefixValueMap() {
    return prefixValueMap;
  }
}
