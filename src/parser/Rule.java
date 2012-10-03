package parser;

import java.util.ArrayList;
import java.util.List;

import edu.stanford.nlp.util.Index;

/**
 * Representation of CFG rules. Memory saving is achieved by using integers.
 *
 * @author Minh-Thang Luong
 */

class Rule {
  private int mother;
  private List<Integer> children;
  private double score;
  
  public Rule(String motherStr, List<String> childStrs, double score, 
      Index<String> motherIndex, Index<String> childIndex) {
    this.mother = motherIndex.indexOf(motherStr, true);
    this.children = new ArrayList<Integer>(childStrs.size());
    for(String child : childStrs){
      this.children.add(childIndex.indexOf(child, true));
    }
    this.score = score;
  }
  
  public Rule(int mother, List<Integer> children, double score){
    this.mother = mother;
    this.children = new ArrayList<Integer>(children.size());
    for(int child:children){
      this.children.add(child);
    }
    this.score = score;
  }
  
  /** Setters and getters **/
  public int getMother(){
    return mother;
  }
  
  public List<Integer> getChildren(){
    return children;
  }
  
  public double getScore(){
    return score;
  }
  
  public boolean equals(Object o) {
    if (this != o || !(o instanceof Rule)) {
      return false;
    } else {
      Rule otherRule = (Rule) o;
      List<Integer> otherChildren = otherRule.getChildren();
      if (children == null || otherChildren == null || 
          children.size() != otherChildren.size() || mother != otherRule.getMother()) {
        return false;
      } else {
        
        for (int i = 0; i < children.size(); i++) { // compare individual child
          if (children.get(i) != otherChildren.get(i)){
            return false;
          }
        }
        return true;
      } 
    }
  }

  public int hashCode() {
    int result = mother;
    for(int child : children){
      result = result<<4 + child;
    }
    return result;
  }

  
  /**
   * Get the reverse view of the children
   * 
   * @return
   */
  public List<Integer> getReverseChildren(){
    int numChildren = children.size();
    List<Integer> reverseChildren = new ArrayList<Integer>();
    for (int i = 0; i < numChildren; i++) {
      reverseChildren.set(i, children.get(numChildren-1-i));
    }
    return reverseChildren;
  }
  
  public boolean isUnary() {
    return children.size() == 1;
  }
  
  /** String output methods **/
  public String toString(Index<String> motherIndex, Index<String> childIndex) {
    StringBuffer sb = new StringBuffer();
    sb.append(motherIndex.get(mother) + "->[");
    sb.append(rhsString(childIndex));
    sb.append("] : " + score);
    return sb.toString();
  }
  
  public String rhsString(Index<String> childIndex){
    StringBuffer sb = new StringBuffer();
    for (int child : children){
      sb.append(childIndex.get(child) + " ");
    }
    if(children.size() > 0){
      sb.delete(sb.length()-1, sb.length());
    }
    return sb.toString();
  }


  public String schemeString(Index<String> motherIndex, Index<String> childIndex) {
    StringBuffer sb = new StringBuffer();
    sb.append("(" + motherIndex.get(mother) + " ");
    for (int child : children){
      sb.append("(X " + childIndex.get(child) + ") ");
    }
    
    if(children.size() > 0){
      sb.delete(sb.length()-1, sb.length());
      sb.append(")");
    }
    return sb.toString();
  }

}

/*** Unused code ***/
//private boolean isRHSTag = true; // indicate if the RHS are tags or words
//this.isRHSTag = isRHSTag;
//for (String child : children) {
//  if(isRHSTag){ // tag
//    this.children.add(new IntTaggedWord(IntTaggedWord.ANY, child));
//  } else { // word
//    this.children.add(new IntTaggedWord(child, IntTaggedWord.ANY));
//  }
//}
//public Rule(String mother, List<String> children, double score) {
//  this(mother, children, score, true);
//}

//public Edge getMotherEdge(){
//return new Edge(mother, new ArrayList<IntTaggedWord>());
//}
//
//public Edge getChildEdge(int i){
//return new Edge(children.get(i), new ArrayList<IntTaggedWord>());
//}


/*
public Edge[] toEdges() {
Edge[] edges = new Edge[dtrs.size()];
for (int i = 0; i < dtrs.size(); i++) {
List thisDtrs = dtrs.subList(i,dtrs.size());
edges[i] = new Edge(mother,thisDtrs);
}
return edges;
}
*/

//public Edge toEdge() {
//return new Edge(mother, children);
//}
