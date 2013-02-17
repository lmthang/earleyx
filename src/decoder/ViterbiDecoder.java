/**
 * 
 */
package decoder;

import parser.EarleyParser;
import edu.stanford.nlp.trees.Tree;

/**
 * @author Minh-Thang Luong, 2012
 *
 */
public class ViterbiDecoder extends Decoder {

  /**
   * @param parser
   */
  public ViterbiDecoder(EarleyParser parser) {
    super(parser);
    // TODO Auto-generated constructor stub
  }

  /**
   * @param parser
   * @param verbose
   */
  public ViterbiDecoder(EarleyParser parser, int verbose) {
    super(parser, verbose);
    // TODO Auto-generated constructor stub
  }

  @Override
  public Tree getBestParse() {
    // TODO Auto-generated method stub
    return null;
  }

}
