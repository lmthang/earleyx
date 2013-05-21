/**
 * 
 */
package base;

import java.util.List;

import edu.stanford.nlp.util.Index;

/**
 * @author Minh-Thang Luong, 2012
 *
 */
public class FragmentRule extends Rule {
  private boolean[] tagFlags; // mark if a rhs token is a tag or not. For a rule ADJP -> RBR _competitive, the bit set value is 1 0
  private int numTags;

  /**
   * @param motherStr
   * @param childStrs
   * @param tagIndex
   * @param wordIndex
   */
  public FragmentRule(String motherStr, List<String> childStrs,
      Index<String> tagIndex, Index<String> wordIndex, boolean[] tagFlags) {
    mother = tagIndex.indexOf(motherStr, true);
    children = new int[childStrs.size()];
    this.tagFlags = tagFlags;
    
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
  public FragmentRule(String motherStr, List<String> childStrs,
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
  public FragmentRule(int mother, int[] children, boolean[] tagFlags) {
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
  public FragmentRule(int mother, int[] children, boolean[] tagFlags, int numTags) {
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
  public FragmentRule(int mother, int[] children, boolean isAllTag) {
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
  
  public FragmentRule(int mother, List<Integer> children, boolean isAllTag) {
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
  public FragmentRule(int mother, int child, boolean isTag) {
    this.mother = mother;
    children = new int[]{child}; 
    tagFlags = new boolean[]{isTag}; //BitSet(1);
    if(isTag){
      numTags = 1;
    }
  }
  
  @Override
  public Rule buildToRule(int dot){
    return new FragmentRule(mother, getChildren(dot+1), getTagFlagsAfterDot(dot+1));
  }
  
  @Override
  public boolean isTag(int i) {
    return tagFlags[i];
  }
  
  public int numTags(){
    return numTags;
  }
  
  /** Setters and getters **/
  public boolean[] getTagFlags() {
    return tagFlags;
  }
  
  public int hashCode() {
    int result = mother;
    for(int child : children){
      result = result<<4 + child;
    }
    return result<<4 + numTags;
  }
  
}
