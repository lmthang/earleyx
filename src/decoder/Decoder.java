/**
 * 
 */
package decoder;

import java.util.List;

import edu.stanford.nlp.ling.HasWord;
import edu.stanford.nlp.trees.Tree;
import edu.stanford.nlp.util.Index;
import parser.EarleyParser;
import parser.EdgeSpace;
import util.Operator;

/**
 * @author Minh-Thang Luong, 2012
 *
 */
public abstract class Decoder {
  protected EarleyParser parser;
  protected EdgeSpace edgeSpace;
  protected Operator operator;
  protected Index<String> parserTagIndex;
  protected Index<String> parserWordIndex;
  protected int verbose;
  
  protected List<? extends HasWord> words; // words to decode
  protected List<Integer> wordIndices; // indices from parserWordIndex. size: numWords
  protected int numWords;
  
  public Decoder(EarleyParser parser) {
    this.parser = parser;
    edgeSpace = parser.getEdgeSpace();
    operator = parser.getOperator();
    parserTagIndex = parser.getParserTagIndex();
    parserWordIndex = parser.getParserWordIndex();
    words = parser.getWords();
    wordIndices = parser.getWordIndices();
    numWords = parser.getNumWords();
    
    this.verbose = EarleyParser.verbose;
  }
  
  public Decoder(EarleyParser parser, int verbose) {
    this(parser);
    this.verbose = verbose;
  }
  
  /**
   * Input string that we will decode over is retrieved by calling parser.getWords();
   * @return
   */
  public abstract Tree getBestParse();
}
