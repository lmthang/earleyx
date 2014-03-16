package parser;

import base.ProbRule;

/**
 * Right now, we assume that all Earley Parser instances will share the same settings.
 * 
 * @author lmthang
 *
 */
public class EarleyParserOptions {
	public static final String VITERBI_OPT = "viterbi";
  public static final String MARGINAL_OPT = "marginal";
  public static final String SOCIALMARGINAL_OPT = "socialmarginal";
  public static final int PCFG = 0;
  public static final int AG = 1;
  public static final int FG = 2;
  
  /** flags **/
  public static boolean isScaling = true;
  public static boolean isLogProb = false;
  public static boolean isFastFG = true;  
  
  /** general info **/
  public static final String ORIG_SYMBOL = "";
  public static int origSymbolIndex; // indexed by TAG_INDEX
  public static String rootSymbol; // default: ROOT, could be changed
  public static int rootSymbolIndex;
  public static ProbRule rootRule; // "" -> ROOT
  

  /** inside-outside **/
  public static int insideOutsideOpt = 0; // 1: EM, 2: VB
  
  /** decode options **/
  public static String decodeOptStr = "";
  public static int decodeOpt = 0; // 1: Viterbi (Label Tree), 2: Marginal Decoding (Label Recall), 3: Social Marginal Decoding
}
