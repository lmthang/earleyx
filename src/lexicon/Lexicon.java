package lexicon;

import java.util.Arrays;
import java.util.Collection;
import java.util.HashSet;
import java.util.Set;

import edu.stanford.nlp.parser.lexparser.IntTaggedWord;

/**
 * An interface for lexica
 *
 * @author Minh-Thang Luong
 */
interface Lexicon {
  //contains 2 maps
  // * lex.scoreDistributions: conditional probability of a terminal give a non-terminal tag
  // * lex.tagsForWord: given a non-terminal, returns a set of tags which go with that non-terminals
  
  //public Set tagsForWord(String word);
  //public int[] intTagsForWord(IntTaggedWord wordITW);
  //public int[] intTagsForWord(String word);

 
  /* handle OOV word */
  public static String UNKNOWN_WORD = "UNK"; 
  
  //public static IntTaggedWord UNKNOWN_WORD_ITW = new IntTaggedWord(UNKNOWN_WORD, IntTaggedWord.ANY);
  //public static IntTaggedWord UNKNOWN_TAG_ITW = new IntTaggedWord(IntTaggedWord.ANY, "$" + UNKNOWN_WORD);
  public static Set<String> PRETERMINALS = new HashSet<String>(Arrays.asList(new String[]{"CC", "CD", "DT", "EX", 
      "FW", "IN", "JJ", "JJR", "JJS", "LS", "MD", "NN", "NNS", "NNP", "NNPS", "PDT", "POS", "PRP", "PRP$", "RB", 
      "RBR", "RBS", "RP", "SYM", "TO", "UH", "VB", "VBD", "VBG", "VBN", "VBP", "VBZ", "WDT", "WP", "WP$", "WRB"}));
  
  /**
   * The score of a word given a tag.  Corresponds to log-probabilities
   */
  public double score(IntTaggedWord itw);
  
  /**
   * The set of (String) tags allowed for a given word
   */
  public Set<IntTaggedWord> tagsForWord(String word);
  
  /**
   * Train lexicon from intTaggedWords
   * @param intTaggedWords
   */
  public void train(Collection<IntTaggedWord> intTaggedWords);
  
}
