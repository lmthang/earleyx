/**
 * 
 */
package parser;

import base.Edge;
import edu.stanford.nlp.util.Index;

/**
 * @author Minh-Thang Luong, 2012
 *
 */
public class StandardEdgeSpace extends EdgeSpace {

  /**
   * @param tagIndex
   */
  public StandardEdgeSpace(Index<String> tagIndex, Index<String> wordIndex) {
    super(tagIndex, wordIndex);
  }

  @Override
  protected Edge getToEdge(Edge e) {
    return new Edge(e.getRule(), e.getDot()+1);
  }

}
