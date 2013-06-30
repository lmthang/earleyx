package base;

import edu.stanford.nlp.util.Index;

/**
 * Representation of a PCFG rule 
 *
 * @author Minh-Thang Luong, 2012
 */

public class ProbRule {
  protected Rule rule; // X->[a b c]
  protected double prob;
  
  public ProbRule(Rule rule, double prob){
    this.rule = rule;
    this.prob = prob;
  }
  
  public Rule getRule(){
    return rule;
  }
  
  public int getMother(){
    return rule.getMother();
  }
  public int[] getChildren(){
    return rule.getChildren();
  }
  public int getChild(int pos){
    return rule.getChild(pos);
  }
  public boolean isTag(int pos){
    return rule.isTag(pos);
  }
  
  public int numChildren(){
    return rule.numChildren();
  }
  
  // Return dot rule: X -> . a b c
  public Edge getEdge(){
    return new Edge(rule, 0);
  }
  
  public double getBias(){
    return 0.0;
  }
  
  public boolean isUnary(){
    return rule.isUnary();
  }
  
  public void setProb(double prob){
    this.prob = prob;
  }
  
  public double getProb(){
    return prob;
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
    if (!rule.equals(otherRule.getRule()) || prob!=otherRule.getProb()){
      return false;
    }
    return true;
  }

  public int hashCode() {
    return rule.hashCode() + (int) prob << 16;
  }
  
  // X->[a b c] : 0.1
  public String toString(Index<String> tagIndex, Index<String> wordIndex) {
    return String.format("%s : %g", rule.toString(tagIndex, wordIndex), prob);
  }
  
  public String schemeString(Index<String> tagIndex, Index<String> wordIndex) {
    return rule.schemeString(tagIndex, wordIndex);
  }
  
  // format read by Mark's code
  public String markString(Index<String> tagIndex, Index<String> wordIndex) {
    return String.format("%g", prob) + " " + rule.markString(tagIndex, wordIndex);
  }

  // format read by Tim's code
  public String timString(Index<String> tagIndex, Index<String> wordIndex) {
    return String.format("%g", Math.log(prob)) + " " + rule.timString(tagIndex, wordIndex);
  }
  
  public String toString(){
    return String.format("%s : %g", rule.toString(), prob);
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

//public ProbRule(String motherStr, List<String> childStrs, double score, 
//Index<String> motherIndex, Index<String> childIndex) {
//this(new Rule(motherStr, childStrs, motherIndex, childIndex), score);
//}
//
//public ProbRule(int mother, List<Integer> children, double score){
//this(new Rule(mother, children), score);
//}