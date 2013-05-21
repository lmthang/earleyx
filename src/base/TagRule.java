/**
 * 
 */
package base;

/**
 * @author Minh-Thang Luong, 2012
 *
 */
public class TagRule extends Rule {

  /**
   * @param mother
   * @param children
   */
  public TagRule(int mother, int[] children) {
    super(mother, children);
  }

  /* (non-Javadoc)
   * @see base.Rule#isTag(int)
   */
  @Override
  public boolean isTag(int i) {
    return true;
  }

  /* (non-Javadoc)
   * @see base.Rule#numTags()
   */
  @Override
  public int numTags() {
    return children.length;
  }

  /* (non-Javadoc)
   * @see base.Rule#buildToRule(int)
   */
  @Override
  public Rule buildToRule(int dot) {
    return new TagRule(mother, getChildren(dot+1));
  }

}
