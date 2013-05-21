/**
 * 
 */
package base;

import java.util.List;

import edu.stanford.nlp.util.Index;

/**
 * Represents fragment rule such as ADJP -> RBR _competitive
 * @author Minh-Thang Luong, 2012
 *
 */
public class Rule {
  protected int mother;
  protected int[] children;
  private boolean[] tagFlags; // mark if a rhs token is a tag or not. For a rule ADJP -> RBR _competitive, the bit set value is 1 0
  public boolean[] getTagFlags() {
    return tagFlags;
  }

  private int numTags;
  
  /**
   * @param motherStr
   * @param childStrs
   * @param tagIndex
   * @param wordIndex
   */
  public Rule(String motherStr, List<String> childStrs,
      Index<String> tagIndex, Index<String> wordIndex, boolean[] tagFlags) {
    mother = tagIndex.indexOf(motherStr, true);
    children = new int[childStrs.size()];
    this.tagFlags = tagFlags;
    
    numTags = 0;
    for (int i = 0; i < childStrs.size(); i++) {
      if(tagFlags[i]){ // tag
        children[i] = tagIndex.indexOf(childStrs.get(i), true);
        numTags++;
      } else { // terminal
        children[i] = wordIndex.indexOf(childStrs.get(i), true);
      }
    }
  }

  /**
   * @param motherStr
   * @param childStrs
   * @param tagIndex
   * @param wordIndex
   */
  public Rule(String motherStr, List<String> childStrs,
      Index<String> tagIndex, Index<String> wordIndex, boolean isAllTag) {
    mother = tagIndex.indexOf(motherStr, true);
    children = new int[childStrs.size()];
    tagFlags = new boolean[childStrs.size()]; //BitSet(childStrs.size());
    
    for (int i = 0; i < childStrs.size(); i++) {
      tagFlags[i] = isAllTag;
      
      if (isAllTag){ // all tags
        children[i] = tagIndex.indexOf(childStrs.get(i), true);
      } else {
        children[i] = wordIndex.indexOf(childStrs.get(i), true);
      }
    }
    if (isAllTag){ // all tags
      numTags = childStrs.size();
    } else { // all terminals
      numTags = 0;
    }
  }
  
  /**
   * 
   * @param mother
   * @param children
   * @param tagFlags
   */
  public Rule(int mother, int[] children, boolean[] tagFlags) {
    this.mother = mother;
    this.children = children; //new int[children.length];
    this.tagFlags = tagFlags;
    
    numTags = 0;
    for (int i = 0; i < children.length; i++) {
      if(tagFlags[i]){
        numTags++;
      }
    }
  }
  
  /**
   * 
   * @param mother
   * @param children
   * @param tagFlags
   * @param numTags
   */
  public Rule(int mother, int[] children, boolean[] tagFlags, int numTags) {
    this.mother = mother;
    this.children = children;
    this.tagFlags = tagFlags;
    this.numTags = numTags;
  }
  
  /**
   * For normal rules (either X -> Y Z T or X -> _y _z _t)
   * 
   * @param mother
   * @param children
   */
  public Rule(int mother, int[] children, boolean isAllTag) {
    this.mother = mother;
    this.children = children;
    tagFlags = new boolean[children.length];
    for (int i = 0; i < children.length; i++) {
      tagFlags[i] = isAllTag;
    }
    
    if(isAllTag){
      numTags = children.length;
    } else {
      numTags = 0;
    }
  }
  
  public Rule(int mother, List<Integer> children, boolean isAllTag) {
    this.mother = mother;
    this.children = new int[children.size()];
    tagFlags = new boolean[children.size()]; //BitSet(childStrs.size());
    for (int i = 0; i < children.size(); i++) {
      this.children[i] = children.get(i);
      tagFlags[i] = isAllTag;
    }
    
    if(isAllTag){
      numTags = children.size();
    } else {
      numTags = 0;
    }
  }
  
  
  /**
   * For unary rules X - Y or X -> _y
   * 
   * @param mother
   * @param children
   */
  public Rule(int mother, int child, boolean isTag) {
    this.mother = mother;
    children = new int[]{child}; 
    tagFlags = new boolean[]{isTag}; //BitSet(1);
    if(isTag){
      numTags = 1;
    }
  }
  
  public static Rule buildLhsOnlyRule(int motherId){
    return new Rule(motherId, new int[0], new boolean[0]);
  }
  
  public static Rule buildToRule(Rule rule, int dot){
    return new Rule(rule.getMother(), rule.getChildren(dot+1), rule.getTagFlagsAfterDot(dot+1));
  }
  
  /**
   * @return true if X -> Y Z T
   */
//  public boolean isTagRule(){
//    return numTags == children.length;
//  }
  
  /**
   * @return true if X -> _y _z _t
   */
  public boolean isTerminalRule(){
    return numTags == 0;
  }
  
  
  public boolean isTag(int childIndex){
    return tagFlags[childIndex];
  }
  
  public boolean isUnary() {
    return children.length == 1 && tagFlags[0];
  }
  
  public int numTags(){
    return numTags;
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
  
  public int numChildren(){
    return children.length;
  }

  
  public boolean[] getTagFlagsAfterDot(int dot) {
    boolean[] newFlags = new boolean[children.length-dot];
    for (int i = 0; i < newFlags.length; i++) {
      newFlags[i] = tagFlags[i+dot];
    }
    return newFlags;
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
    
    boolean[] otherFlags = otherRule.getTagFlags();
    for (int i = 0; i < thisChildren.length; i++) { // compare individual child
      if (thisChildren[i] != otherChildren[i]){
        return false;
      }
      if(otherFlags[i] != tagFlags[i]){ // compare tag flags
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
    return result<<4 + numTags;
  }

  
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
      if(tagFlags[i]){
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
    if(tagFlags[pos]){ // tag
      return tagIndex.get(children[pos]);
    } else { // terminal
      return "_" + wordIndex.get(children[pos]);
    }
  }
}
