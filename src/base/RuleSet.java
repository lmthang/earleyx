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

/**
 * @author Minh-Thang Luong, 2012
 *
 */
public class RuleSet {
  private Index<String> tagIndex;
  private Index<String> wordIndex;
  
  private int numRules = 0;
  private List<ProbRule> allRules;
  
  private Map<Rule, Integer> ruleMap; // map Rule to indices in allRules
  private Map<Integer, List<Integer>> tag2ruleIndices; // map tag to a set of rule indices
  
  
  public RuleSet(Index<String> tagIndex, Index<String> wordIndex){
    this.tagIndex = tagIndex;
    this.wordIndex = wordIndex;
    
    allRules = new ArrayList<ProbRule>();
    ruleMap = new HashMap<Rule, Integer>();
    tag2ruleIndices = new HashMap<Integer, List<Integer>>();
  }
  
  public int add(ProbRule probRule){
    allRules.add(probRule);
    
    Rule rule = probRule.getRule();
    if(ruleMap.containsKey(rule)){
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
    
    // ruleMap
    if(ruleMap.containsKey(rule)){
      System.err.println("! Duplicate rule " + rule.toString(tagIndex, wordIndex));
    }
    ruleMap.put(rule, ruleId);
    
    // tag2ruleIndices
    int tag = rule.getMother();
    if(!tag2ruleIndices.containsKey(tag)){
      tag2ruleIndices.put(tag, new ArrayList<Integer>());
    }
    tag2ruleIndices.get(tag).add(ruleId);
    
    return ruleId;
  }
  
  public void addAll(Collection<ProbRule> probRules){
    for(ProbRule probRule : probRules){
      add(probRule);
    }
  }

  public double getProb(int ruleId){
    return allRules.get(ruleId).getProb();
  }
  
  public void setProb(int ruleId, double newProb){
    allRules.get(ruleId).setProb(newProb);
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
  
  public ProbRule get(int i){
    return allRules.get(i);
  }
    
  public List<ProbRule> getAllRules(){
    return allRules;
  }
  
  public Map<Integer, List<Integer>> getTag2ruleIndices(){
    return tag2ruleIndices;
  }
}
