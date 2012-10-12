package parser;

import java.util.List;

import edu.stanford.nlp.util.Index;

/**
 * Representation of a PCFG rule 
 *
 * @author Minh-Thang Luong, 2012
 */

public class Rule {
  protected BaseEdge edge; // X->[a b c]
  protected double score;
  
  public Rule(BaseEdge edge, double score){
    this.edge = edge;
    this.score = score;
  }
  public Rule(String motherStr, List<String> childStrs, double score, 
      Index<String> motherIndex, Index<String> childIndex) {
    this(new BaseEdge(motherStr, childStrs, motherIndex, childIndex), score);
  }
  
  public Rule(int mother, List<Integer> children, double score){
    this(new BaseEdge(mother, children), score);
  }
  
  public BaseEdge getEdge(){
    return edge;
  }
  
  public int getMother(){
    return edge.getMother();
  }
  public List<Integer> getChildren(){
    return edge.getChildren();
  }
  public int getChild(int pos){
    return edge.getChild(pos);
  }
  
  public Edge getChildEdge(int pos){
    return Edge.createTagEdge(edge.getChild(pos));
  }
  
  public Edge getMotherEdge(){
    return Edge.createTagEdge(edge.getMother());
  }
  
  public boolean isUnary(){
    return edge.isUnary();
  }
  
  public double getScore(){
    return score;
  }
  
  public Edge toEdge(){
    return new Edge(edge, 0);
  }
  
  public boolean equals(Object o) {
    if (this == o){ // compare pointer
      return true;
    }
    
    if (!(o instanceof Rule)) { // check class
      return false;
    } 

    Rule otherRule = (Rule) o;
    
    // compare edge, score
    if (!edge.equals(otherRule.getEdge()) || score!=otherRule.getScore()){
      return false;
    }
    return true;
  }

  public int hashCode() {
    return edge.hashCode() + (int) score << 16;
  }
  
  // X->[a b c] : 0.1
  public String toString(Index<String> motherIndex, Index<String> childIndex) {
    return edge.lhsString(motherIndex) + "->[" + edge.rhsString(childIndex, false) + "] : " + score;
  }
  public String schemeString(Index<String> motherIndex, Index<String> childIndex) {
    return edge.schemeString(motherIndex, childIndex, false);
  }
}