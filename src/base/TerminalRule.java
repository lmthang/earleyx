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
public class TerminalRule extends Rule {
  /**
   * @param mother
   * @param children
   */
  public TerminalRule(int mother, int[] children) {
    super(mother, children);
  }

  public TerminalRule(int mother, List<Integer> children) {
    this.mother = mother;
    this.children = new int[children.size()];
    
    for (int i = 0; i < children.size(); i++) {
      this.children[i] = children.get(i);
    }
  }
  
  /**
   * For X -> _y
   * 
   * @param mother
   * @param children
   */
  public TerminalRule(int mother, int child) {
    this.mother = mother;
    children = new int[]{child}; 
  }
  
  /**
   * @param motherStr
   * @param childStrs
   * @param tagIndex
   * @param wordIndex
   */
  public TerminalRule(String motherStr, List<String> childStrs,
      Index<String> tagIndex, Index<String> wordIndex) {
    mother = tagIndex.indexOf(motherStr, true);
    children = new int[childStrs.size()];
    
    for (int i = 0; i < childStrs.size(); i++) {
      children[i] = wordIndex.indexOf(childStrs.get(i), true);
    }
  }
  
  /* (non-Javadoc)
   * @see base.Rule#isTag(int)
   */
  @Override
  public boolean isTag(int i) {
    return false;
  }

  /* (non-Javadoc)
   * @see base.Rule#numTags()
   */
  @Override
  public int numTags() {
    return 0;
  }


  /* (non-Javadoc)
   * @see base.Rule#buildToRule(int)
   */
  @Override
  public Rule buildToRule(int dot) {
    return new TerminalRule(mother, getChildren(dot+1));
  }

}
