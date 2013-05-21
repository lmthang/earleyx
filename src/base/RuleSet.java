/**
 * 
 */
package base;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import edu.stanford.nlp.util.Index;
import edu.stanford.nlp.util.Pair;

/**
 * Keep track of all rules and their probabilities.
 * 
 * @author Minh-Thang Luong, 2012
 *
 */
public class RuleSet {
  private Index<String> tagIndex;
  private Index<String> wordIndex;
  
  boolean hasSmoothRules; // if it has a rule X->[_UNK...]
  private int numRules = 0;
  private List<ProbRule> allRules;
  
  // sublists of all rules
  protected Collection<ProbRule> terminalRules; // X -> _a
  protected List<ProbRule> tagRules; // allRules - terminalRules
  
//  protected Collection<ProbRule> multiTerminalRules; // X -> _a _b _c
//  protected Collection<ProbRule> fragmentRules; // X -> _a B _c
  protected int numMultipleTerminalRules;
  protected int numFragmentRules; // includes numMultipleTerminalRules
  
  // unary rules
  protected List<ProbRule> unaryRules;
//  protected Map<Integer, Set<Integer>> unaryMap; // unaryMap.get(Y): set of Z for which there's a unary Y -> Z
  
  /** for Viterbi decoding **/
  // list of pairs (X_1->...->X_k, score) in which X_1->...->X_k is the highest-scoring chain
  // connecting X_1 and X_k together with score
  protected List<Pair<List<Integer>, Double>> unaryChains;
  // unaryChainStartMap.get(Z).get(Y): returns the index in unaryChains of the chain Z->Y
  protected Map<Integer, Map<Integer, Integer>> unaryChainStartMap;
  // unaryChainEndMap.get(Y).get(Z): returns the index in unaryChains of the chain Z->Y
  protected Map<Integer, Map<Integer, Integer>> unaryChainEndMap;
  
  private Map<Rule, Integer> ruleMap; // map FragmentRule to indices in allRules
//  private Map<Integer, List<Integer>> tag2ruleIndices; // map tag to a set of rule indices
  
  public RuleSet(Index<String> tagIndex, Index<String> wordIndex){
    this.tagIndex = tagIndex;
    this.wordIndex = wordIndex;
    
    allRules = new ArrayList<ProbRule>();
    ruleMap = new HashMap<Rule, Integer>();
//    tag2ruleIndices = new HashMap<Integer, List<Integer>>();
    
    // sublists of all rules
    tagRules = new ArrayList<ProbRule>();
    terminalRules = new ArrayList<ProbRule>();
//    multiTerminalRules = new ArrayList<ProbRule>();
//    fragmentRules = new ArrayList<ProbRule>();
    numFragmentRules = 0;
    numMultipleTerminalRules = 0;
    
    // unary rules
    unaryRules = new ArrayList<ProbRule>();

    // for Viterbi decoding
    unaryChains = new ArrayList<Pair<List<Integer>,Double>>();
    unaryChainStartMap = new HashMap<Integer, Map<Integer,Integer>>();
    unaryChainEndMap = new HashMap<Integer, Map<Integer,Integer>>();
    
    hasSmoothRules = false;
  }
  
  public int add(ProbRule probRule){
    allRules.add(probRule);
    
    Rule rule = probRule.getRule();
    
    // ruleMap
    if(ruleMap.containsKey(rule)){
      System.err.println("! Duplicate rule " + rule.toString(tagIndex, wordIndex));
      int ruleId = ruleMap.get(rule);
      if(Math.abs(allRules.get(ruleId).getProb() - probRule.getProb())<1e-10){
        return ruleId;
      } else {
        System.err.println("! adding rule " + probRule.toString(tagIndex, wordIndex) 
            + " conflicts with existing rule " + allRules.get(ruleId).toString(tagIndex, wordIndex));
        System.exit(1);
      }
    }
    
    int ruleId = numRules++;
    ruleMap.put(rule, ruleId);
    
    // tag2ruleIndices
//    int tag = rule.getMother();
//    if(!tag2ruleIndices.containsKey(tag)){
//      tag2ruleIndices.put(tag, new ArrayList<Integer>());
//    }
//    tag2ruleIndices.get(tag).add(ruleId);
    
    // sublist of all rules
    if(rule.numChildren()==1 && rule.numTags() == 0){ // X -> _a
      terminalRules.add(probRule);
      
      if(rule.getChildStr(tagIndex, wordIndex, 0).startsWith("_UNK")){
        hasSmoothRules = true;
      }
    } else { // other rules
      tagRules.add(probRule);
      
      if(rule.numChildren()==1){ // unary
        unaryRules.add(probRule);
        
        // update unary chain
        List<Integer> chain = new ArrayList<Integer>();
        chain.add(probRule.getMother());
        chain.add(probRule.getChild(0));
        addChain(probRule.getMother(), probRule.getChild(0), chain, probRule.getProb());
        updateUnaryChain(probRule.getMother(), probRule.getChild(0), chain, probRule.getProb());
      } else if(rule.numTags()<rule.numChildren()){ // fragment rule
        numFragmentRules++;
        
        if(rule.numTags()==0){ // multiple terminal rules
          numMultipleTerminalRules++;
        }
      }
    }
    
    return ruleId;
  }
  
  public boolean hasSmoothRule(){
    return hasSmoothRules;
  }
  
  private void updateUnaryChain(int Z, int Y, List<Integer> chain, double prob){
    // go through any chain ends at Z
    if(unaryChainEndMap.containsKey(Z)){
      for(int zPrime : unaryChainEndMap.get(Z).keySet()) {// chain Z' -> Z
        if(zPrime == Y){
          continue;
        }
        
        // consider new chain Z' -> Z -> Y
        Pair<List<Integer>, Double> zPrimeZPair = unaryChains.get(unaryChainEndMap.get(Z).get(zPrime));
        double newProb = zPrimeZPair.second * prob;
        
        checkAndAddChain(zPrime, Y, zPrimeZPair.first, chain, newProb);
      }
    }
    
    // go through any chain starts at Y
    if(unaryChainStartMap.containsKey(Y)){
      for(int yPrime : unaryChainStartMap.get(Y).keySet()) {// chain Y -> Y'
        if(Z == yPrime){
          continue;
        }
        
        // consider new chain Z -> Y -> Y'
        Pair<List<Integer>, Double> yYPrimePair = unaryChains.get(unaryChainStartMap.get(Y).get(yPrime));
        double newProb = prob*yYPrimePair.second;
        
        checkAndAddChain(Z, yPrime, chain, yYPrimePair.first, newProb);
      }
    }
  }

  private List<Integer> combineChains(List<Integer> prevChain, List<Integer> nextChain){
    List<Integer> chain = new ArrayList<Integer>();
    assert(prevChain.size()>=2 && nextChain.size()>=2);
    assert(prevChain.get(prevChain.size()-1).equals(nextChain.get(0)));
    
    for(int value : prevChain){
      chain.add(value);
    }
    chain.remove(chain.size()-1);
    
    for(int value : nextChain){
      chain.add(value);
    }
    
    return chain;
  }
  
  private void checkAndAddChain(int startTag, int endTag, List<Integer> prevChain, 
      List<Integer> nextChain, double prob){
    if(unaryChainStartMap.containsKey(startTag) 
        && unaryChainStartMap.get(startTag).containsKey(endTag)){ // chain start->end
      int chainIndex = unaryChainStartMap.get(startTag).get(endTag);
      
      if(prob > unaryChains.get(chainIndex).second){ // better chain start->end
        List<Integer> chain = combineChains(prevChain, nextChain);
        unaryChains.set(chainIndex, new Pair<List<Integer>, Double>(chain, prob));
      }
    } else {
      List<Integer> chain = combineChains(prevChain, nextChain);
      addChain(startTag, endTag, chain, prob);
      updateUnaryChain(startTag, endTag, chain, prob); // recursively update
    }
  }
  
  private void addChain(int startTag, int endTag, List<Integer> chain, double score){
//    System.err.println("Add chain: " + unaryChain(chain, tagIndex));
    int chainIndex = unaryChains.size(); // this line needs to come before add
    unaryChains.add(new Pair<List<Integer>, Double>(chain, score));
    
    addMap(unaryChainStartMap, startTag, endTag, chainIndex);
    addMap(unaryChainEndMap, endTag, startTag, chainIndex);
  }
  
  private void addMap(Map<Integer, Map<Integer, Integer>> map, int key1, int key2, int value){
    if(!map.containsKey(key1)){
      map.put(key1, new HashMap<Integer, Integer>());
    }
    map.get(key1).put(key2, value);
  }
  
//  public boolean isUnary(int mother, int child){
//    return (unaryMap.containsKey(mother) && unaryMap.get(mother).contains(child));
//  }
  
  public void addAll(Collection<ProbRule> probRules){
    for(ProbRule probRule : probRules){
      add(probRule);
    }
  }

//  public boolean hasMultiTerminalRule(){
//    return (multiTerminalRules.size()>0);
//  }
  
  public double getProb(int ruleId){
    return allRules.get(ruleId).getProb();
  }
  
  public void setProb(int ruleId, double prob){
    allRules.get(ruleId).setProb(prob);
  }
  
  public void setProb(Rule rule, double prob){
    if(!ruleMap.containsKey(rule)){
      System.err.println("! setProb: ruleSet doesn't contain rule " + rule.toString(tagIndex, wordIndex));
      System.exit(1);
    }
    int ruleId = ruleMap.get(rule);
    setProb(ruleId, prob);
  }
  
  public int size(){
    return numRules;
  }
  
  public boolean contains(Rule rule){
    return ruleMap.containsKey(rule);
  }
  
  public int indexOf(Rule rule){
    if(contains(rule)){
      return ruleMap.get(rule);
    } else {
      return -1;
    }
  }

  /************************/
  /** Getters & Setters **/
  /************************/
  public ProbRule get(int i){
    return allRules.get(i);
  }
    
  public List<ProbRule> getAllRules(){
    return allRules;
  }
  
//  public Map<Integer, List<Integer>> getTag2ruleIndices(){
//    return tag2ruleIndices;
//  }

  public int getMother(int ruleId){
    return allRules.get(ruleId).getMother();
  }

  public double getBias(int ruleId){
    return allRules.get(ruleId).getBias();
  }
  
  public List<ProbRule> getOtherRules() {
    return tagRules;
  }
  
//  public List<ProbRule> getTagRules() {
//    return tagRules;
//  }

  public Collection<ProbRule> getTerminalRules() {
    return terminalRules;
  }

//  public Collection<ProbRule> getMultiTerminalRules() {
//    return multiTerminalRules;
//  }

  public int numFragmentRules(){
    return numFragmentRules;
  }
  
  public int numMultipleTerminalRules(){
    return numMultipleTerminalRules;
  }
  
  public List<ProbRule> getUnaryRules() {
    return unaryRules;
  }
  
  public List<Integer> getUnaryChain(int startTag, int endTag){
    if(unaryChainStartMap.containsKey(startTag) && unaryChainStartMap.get(startTag).containsKey(endTag)){
      return unaryChains.get(unaryChainStartMap.get(startTag).get(endTag)).first;
    } else {
      return null;
    }
  }
  
  private String unaryChain(List<Integer> chain, Index<String> tagIndex){
    StringBuffer sb = new StringBuffer();
    
    assert(chain.size()>=2);
    sb.append(tagIndex.get(chain.get(0)));
    for (int i = 1; i < chain.size(); i++) {
      sb.append("->" + tagIndex.get(chain.get(i)));
    }
    
    return sb.toString();
  }
  
  public String sprintUnaryChains(){
    StringBuffer sb = new StringBuffer("\n# Unary chains\n");
    for(Pair<List<Integer>, Double> pair : unaryChains){
      sb.append(unaryChain(pair.first, tagIndex) + " : " + pair.second + "\n");
    }
    
    // Sanity check
    for(int start : unaryChainStartMap.keySet()){
      for(int end : unaryChainStartMap.get(start).keySet()){
        int chainIndex = unaryChainStartMap.get(start).get(end);
        List<Integer> chain = unaryChains.get(chainIndex).first;
        assert(start == chain.get(0));
        assert(end == chain.get(chain.size()-1));
      }
    }
    
    for(int end : unaryChainEndMap.keySet()){
      for(int start : unaryChainEndMap.get(end).keySet()){
        int chainIndex = unaryChainEndMap.get(end).get(start);
        List<Integer> chain = unaryChains.get(chainIndex).first;
        assert(start == chain.get(0));
        assert(end == chain.get(chain.size()-1));
      }
    }
    return sb.toString();
  }
  
  public String toString(Index<String> tagIndex, Index<String> wordIndex){
    StringBuffer sb = new StringBuffer("\n# Ruleset\n");
    for (ProbRule probRule : allRules) {
      if(probRule.getProb() > 0.0){
        sb.append(probRule.toString(tagIndex, wordIndex) + "\n");
      }
    }
    return sb.toString();
  }
}
