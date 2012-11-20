/**
 * 
 */
package base;

/**
 * we will store for every right: left X -> \alpha Y . \beta
 * a backtrack information (completed edge) right: middle Y -> v .
 * that gives the maximum inner prob for right: left X -> \alpha Y . \beta
 * 
 * @author Minh-Thang Luong, 2012
 *
 */
public class BackTrack {
  public int edge; // Y -> v .
  public int middle;
  public double parentInnerScore; // inner score for right: left X -> \alpha Y . \beta
  
  public BackTrack(int edge, int middle, double parentInnerScore) {
    super();
    this.edge = edge;
    this.middle = middle;
    this.parentInnerScore = parentInnerScore;
  }
}
