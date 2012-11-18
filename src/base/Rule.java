/**
 * 
 */
package base;

import java.util.ArrayList;
import java.util.List;

import sun.reflect.generics.reflectiveObjects.NotImplementedException;

import edu.stanford.nlp.util.Index;

/**
 * Represent a rule without any probability associated, e.g X -> [Y Z] or X -> [_a _b _c]
 * Memory saving is achieved by using integers.
 * @author Minh-Thang Luong, 2012
 *
 */
public abstract class Rule {
  protected int mother;
  protected List<Integer> children;
  
  protected Rule(String motherStr, List<String> childStrs, 
      Index<String> motherIndex, Index<String> childIndex) {
    this.mother = motherIndex.indexOf(motherStr, true);
    this.children = new ArrayList<Integer>(childStrs.size());
    for(String child : childStrs){
      this.children.add(childIndex.indexOf(child, true));
    }
  }
  
  protected Rule(int mother, List<Integer> children){
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
  
  public boolean equals(Object o){
    throw new NotImplementedException();
  }
  
  protected boolean equals(Rule otherRule){
    // compare mother
    if (mother != otherRule.getMother()){
      return false;
    }
    
    // compare children
    List<Integer> thisChildren = getChildren();
    List<Integer> otherChildren = otherRule.getChildren();
    if (thisChildren == null || otherChildren == null || 
        thisChildren.size() != otherChildren.size()) {
      return false;
    } 
    for (int i = 0; i < thisChildren.size(); i++) { // compare individual child
      if ((int) thisChildren.get(i) != (int) otherChildren.get(i)){
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
  
  // Y Z
  protected String rhsString(Index<String> childIndex){
    StringBuffer sb = new StringBuffer();
    for (int child : children){
      sb.append(getChildStr(childIndex, child) + " ");   
    }
    if(children.size() > 0){
      sb.delete(sb.length()-1, sb.length());
    }
    return sb.toString();
  }
  
  // (X (_ Y) (_ Z)) for TagRule or (X (_ _a) (_ _b) (_ _c)) for TerminalRule 
  public String schemeString(Index<String> tagIndex, Index<String> wordIndex) {
    StringBuffer sb = new StringBuffer();
    sb.append("(" + tagIndex.get(mother) + " ");
    for (int child : children){
      if(this instanceof TagRule){
        sb.append("(_ " + getChildStr(tagIndex, child) + ") ");
      } else {
        sb.append("(_ " + getChildStr(wordIndex, child) + ") ");
      }
    }
    
    if(children.size() > 0){
      sb.delete(sb.length()-1, sb.length());
      sb.append(")");
    }
    return sb.toString();
  }
  
  // String read by Mark's IO code
  // X --> Y Z, or X --> a b c
  public String markString(Index<String> tagIndex, Index<String> wordIndex) {
    StringBuffer sb = new StringBuffer();
    sb.append(tagIndex.get(mother) + " -->");
    for (int child : children){
      if(this instanceof TagRule){
        sb.append(" " + tagIndex.get(child));
      } else {
        sb.append(" _" + wordIndex.get(child));
      }
    }
    
    return sb.toString();
  }
  
  // X->[Y Z] for TagRule or X->[_a _b _c] for TerminalRule
  public String toString(Index<String> tagIndex, Index<String> wordIndex){
    if(this instanceof TagRule){
      return lhsString(tagIndex) + "->[" + rhsString(tagIndex) + "]";
    } else {
      return lhsString(tagIndex) + "->[" + rhsString(wordIndex) + "]";
    }
    
  }
  
  protected abstract String getChildStr(Index<String> childIndex, int child);
}
