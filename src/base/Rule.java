package base;

import java.util.ArrayList;
import java.util.List;

import edu.stanford.nlp.util.Index;

/**
 * An Edge represent a rule without any probability associated, e.g X -> [a b c]
 * Memory saving is achieved by using integers.
 * @author Minh-Thang Luong, 2012
 *
 */
public class Rule {
  protected int mother;
  protected List<Integer> children;
  
  public Rule(String motherStr, List<String> childStrs, 
      Index<String> motherIndex, Index<String> childIndex) {
    this.mother = motherIndex.indexOf(motherStr, true);
    this.children = new ArrayList<Integer>(childStrs.size());
    for(String child : childStrs){
      this.children.add(childIndex.indexOf(child, true));
    }
  }
  
  public Rule(int mother, List<Integer> children){
    this.mother = mother;
    this.children = new ArrayList<Integer>(children.size());
    for(int child:children){
      this.children.add(child);
    }
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
  
  public void setMother(int mother){
    this.mother = mother;
  }
  
  /** Setters and getters **/
  public int getMother(){
    return mother;
  }
  
  public List<Integer> getChildren(){
    return children;
  }
  
  public int getChild(int pos){
    return children.get(pos);
  }
  // get children after the dot position
  public List<Integer> getChildren(int dot){
    return children.subList(dot, children.size());
  }
  
  public int numChildren(){
    return children.size();
  }
  
  public boolean equals(Object o) {
    if (this == o){ // compare pointer
      return true;
    }
    
    if (!(o instanceof Rule)) { // check class
      return false;
    } 

    Rule otherEdge = (Rule) o;
    
    // compare mother
    if (mother != otherEdge.getMother()){
      return false;
    }
    
    // compare children
    List<Integer> thisChildren = getChildren();
    List<Integer> otherChildren = otherEdge.getChildren();
    if (thisChildren == null || otherChildren == null || 
        thisChildren.size() != otherChildren.size()) {
      return false;
    } 
    for (int i = 0; i < thisChildren.size(); i++) { // compare individual child
      if (thisChildren.get(i) != otherChildren.get(i)){
        return false;
      }
    } 
    return true;
  }
  
  public int hashCode() {
    int result = mother;
    for(int child : getChildren()){
      result = result<<4 + child;
    }
    return result;
  }
  
  /** String output methods **/
  // X
  public String lhsString(Index<String> motherIndex){
    return motherIndex.get(mother);
  }
  
  // a b c or _a _b _c (if isTerminal is true)
  public String rhsString(Index<String> childIndex, boolean isTerminal){
    StringBuffer sb = new StringBuffer();
    for (int child : children){
      if (isTerminal){
        sb.append("_" + childIndex.get(child) + " ");
      } else {
        sb.append(childIndex.get(child) + " ");    
      }
    }
    if(children.size() > 0){
      sb.delete(sb.length()-1, sb.length());
    }
    return sb.toString();
  }
  
  // (X (_ a) (_ b) (_ c)) or (X (_ _a) (_ _b) (_ _c)) (if isTerminal is true) 
  public String schemeString(Index<String> motherIndex, Index<String> childIndex, boolean isTerminal) {
    StringBuffer sb = new StringBuffer();
    sb.append("(" + motherIndex.get(mother) + " ");
    for (int child : children){
      if (isTerminal){
        sb.append("(_ _" + childIndex.get(child) + ") ");
      } else {
        sb.append("(_ " + childIndex.get(child) + ") ");
      }
    }
    
    if(children.size() > 0){
      sb.delete(sb.length()-1, sb.length());
      sb.append(")");
    }
    return sb.toString();
  }
}
