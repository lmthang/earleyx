/**
 * 
 */
package base;

/**
 * @author Minh-Thang Luong, 2012
 *
 */
public class MotherRule extends Rule {

  /**
   * 
   */
  public MotherRule(int mother) {
    super(mother, new int[0]);
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
   * @see base.Rule#buildMotherRule()
   */
  @Override
  public Rule buildMotherRule() {
    return null;
  }

  /* (non-Javadoc)
   * @see base.Rule#buildViaRule(int)
   */
  @Override
  public Rule buildViaRule(int dot) {
    return null;
  }

  /* (non-Javadoc)
   * @see base.Rule#buildToRule(int)
   */
  @Override
  public Rule buildToRule(int dot) {
    return null;
  }

}
