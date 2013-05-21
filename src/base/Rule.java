/**
 * 
 */
package base;

import edu.stanford.nlp.util.Index;

/**
 * Represents fragment rule such as ADJP -> RBR _competitive
 * @author Minh-Thang Luong, 2013
 *
 */
public abstract class Rule {
  protected int mother;
  protected int[] children;
  
  public Rule(){
    super();
  }
  
  public Rule(int mother, int[] children) {
    this.mother = mother;
    this.children = children;
  }
  
  public boolean isUnary() {
    return children.length == 1 && isTag(0);
  }

  public int numChildren(){
    return children.length;
  }

  /**
   * @return true if X -> _y _z _t
   */
  public boolean isTerminalRule(){
    return numTags() == 0;
  }
  
  public Rule buildMotherRule(){
    return new MotherRule(mother);
  }
  
  public Rule buildViaRule(int dot){
    return new MotherRule(children[dot]);
  }
  
  
  public boolean[] getTagFlagsAfterDot(int dot) {
    boolean[] newFlags = new boolean[children.length-dot];
    for (int i = 0; i < newFlags.length; i++) {
      newFlags[i] = isTag(i+dot);
    }
    return newFlags;
  }
  
  /** Setters and getters **/
  public void setMother(int mother){
    this.mother = mother;
  }
  
  public int getMother(){
    return mother;
  }
  
  public int[] getChildren(){
    return children;
  }
  
  public int getChild(int pos){
    return children[pos];
  }
  
  // get children after the dot position
  public int[] getChildren(int dot){
    int[] newChildren = new int[children.length-dot];
    for (int i = 0; i < newChildren.length; i++) {
      newChildren[i] = children[i+dot];
    }
    return newChildren;
  }

  
  /************ Equal & Hashcode **************/
  public boolean equals(Object o) {
    if (this == o){ // compare pointer
      return true;
    }
    
    if (!(o instanceof Rule)) { // check class
      return false;
    } 

    Rule otherRule = (Rule) o;
    
    // compare mother
    if (mother != otherRule.getMother()){
      return false;
    }
    
    // compare children
    int[] thisChildren = getChildren();
    int[] otherChildren = otherRule.getChildren();
    if (thisChildren == null || otherChildren == null || 
        thisChildren.length != otherChildren.length) {
      return false;
    } 
    
    for (int i = 0; i < thisChildren.length; i++) { // compare individual child
      if (thisChildren[i] != otherChildren[i]){
        return false;
      }
      if(otherRule.isTag(i) != isTag(i)){ // compare tag flags
        return false;
      }
    } 
    
    return true;
  }
  
  public int hashCode() {
    int result = mother;
    for(int child : children){
      result = result<<4 + child;
    }
    return result<<4 + numTags();
  }

  
  /***************** Abstract methods *********************/
  public abstract boolean isTag(int i);
  public abstract int numTags();
  public abstract Rule buildToRule(int dot);
  
  /***************** IO *********************/
  // (X (_ Y) (_ Z)) for TagRule or (X (_ _a) (_ _b) (_ _c)) for TerminalRule 
  public String schemeString(Index<String> tagIndex, Index<String> wordIndex) {
    StringBuffer sb = new StringBuffer();
    sb.append("(" + tagIndex.get(mother) + " ");
    for (int i = 0; i < children.length; i++) {
      sb.append("(_ " + getChildStr(tagIndex, wordIndex, i) + ") ");
    }
    
    if(children.length > 0){
      sb.delete(sb.length()-1, sb.length());
      sb.append(")");
    }
    return sb.toString();
  }
  
  // String read by Mark's IO code
  // X --> Y Z, or X --> _a _b _c
  public String markString(Index<String> tagIndex, Index<String> wordIndex) {
    return lhsString(tagIndex) + " --> " + rhsString(tagIndex, wordIndex);
  }
  
  // String read by Tim's IO code
  // X Y Z, or X _a _b _c
  public String timString(Index<String> tagIndex, Index<String> wordIndex) {
    return lhsString(tagIndex) + " " + rhsString(tagIndex, wordIndex);
  }
  
  // X->[Y Z] for TagRule or X->[_a _b _c] for TerminalRule
  public String toString(Index<String> tagIndex, Index<String> wordIndex){
    return lhsString(tagIndex) + "->[" + rhsString(tagIndex, wordIndex) + "]";
  }
  
  public String toString(){
    StringBuffer sb = new StringBuffer();
    sb.append(mother + "->[");
    
    for (int i = 0; i < children.length; i++) {
      if(isTag(i)){
        sb.append(children[i] + " ");
      } else {
        sb.append("_" + children[i] + " ");
      }
    }
    
    if(children.length>0){
      sb.deleteCharAt(sb.length()-1);
    }
    sb.append(']');
    
    return sb.toString();
  }
  
  // X
  public String lhsString(Index<String> motherIndex){
    return motherIndex.get(mother);
  }
  
  // Y Z or _a _b _c
  protected String rhsString(Index<String> tagIndex, Index<String> wordIndex){
    StringBuffer sb = new StringBuffer();
    for (int i = 0; i < children.length; i++) {
      sb.append(getChildStr(tagIndex, wordIndex, i) + " ");
    }
    
    if(children.length > 0){
      sb.delete(sb.length()-1, sb.length());
    }
    return sb.toString();
  }
  
  public String getChildStr(Index<String> tagIndex, Index<String> wordIndex, int pos){
    if(isTag(pos)){ // tag
      return tagIndex.get(children[pos]);
    } else { // terminal
      return "_" + wordIndex.get(children[pos]);
    }
  }
}
