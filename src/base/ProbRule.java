package base;

import java.util.List;


import edu.stanford.nlp.util.Index;

/**
 * Representation of a PCFG rule 
 *
 * @author Minh-Thang Luong, 2012
 */

public class ProbRule {
  protected Rule rule; // X->[a b c]
  protected double score;
  
  public ProbRule(Rule rule, double score){
    this.rule = rule;
    this.score = score;
  }
  public ProbRule(String motherStr, List<String> childStrs, double score, 
      Index<String> motherIndex, Index<String> childIndex) {
    this(new Rule(motherStr, childStrs, motherIndex, childIndex), score);
  }
  
  public ProbRule(int mother, List<Integer> children, double score){
    this(new Rule(mother, children), score);
  }
  
  public Rule getRule(){
    return rule;
  }
  
  public int getMother(){
    return rule.getMother();
  }
  public List<Integer> getChildren(){
    return rule.getChildren();
  }
  public int getChild(int pos){
    return rule.getChild(pos);
  }
  
  // Return dot rule: X -> . a b c
  public Edge getEdge(){
    return new Edge(rule, 0);
  }
  
  public boolean isUnary(){
    return rule.isUnary();
  }
  
  public double getScore(){
    return score;
  }
  
  public boolean equals(Object o) {
    if (this == o){ // compare pointer
      return true;
    }
    
    if (!(o instanceof ProbRule)) { // check class
      return false;
    } 

    ProbRule otherRule = (ProbRule) o;
    
    // compare edge, score
    if (!rule.equals(otherRule.getRule()) || score!=otherRule.getScore()){
      return false;
    }
    return true;
  }

  public int hashCode() {
    return rule.hashCode() + (int) score << 16;
  }
  
  // X->[a b c] : 0.1
  public String toString(Index<String> motherIndex, Index<String> childIndex) {
    return rule.lhsString(motherIndex) + "->[" + rule.rhsString(childIndex, false) + "] : " + score;
  }
  public String schemeString(Index<String> motherIndex, Index<String> childIndex) {
    return rule.schemeString(motherIndex, childIndex, false);
  }
}

/** Unused code **/
//public Edge getChildEdge(int pos){
//return Edge.createTagEdge(rule.getChild(pos));
//}
//
//public Edge getMotherEdge(){
//return Edge.createTagEdge(rule.getMother());
//}