package lexicon;

import java.util.Arrays;
import java.util.Collection;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import edu.stanford.nlp.parser.lexparser.IntTaggedWord;
import edu.stanford.nlp.stats.Counter;
import edu.stanford.nlp.util.Index;

public abstract class BaseLexicon {
  protected Map<Integer, Counter<Integer>> tag2wordsMap;

  protected Map<Integer, Set<IntTaggedWord>> word2tagsMap;
  protected Index<String> wordIndex;
  protected Index<String> tagIndex;
  protected int unkIndex = -1;
  
  /* handle OOV word */
  public static String UNKNOWN_WORD = "UNK"; 
  
  public static Set<String> PRETERMINALS = new HashSet<String>(Arrays.asList(new String[]{"CC", "CD", "DT", "EX", 
      "FW", "IN", "JJ", "JJR", "JJS", "LS", "MD", "NN", "NNS", "NNP", "NNPS", "PDT", "POS", "PRP", "PRP$", "RB", 
      "RBR", "RBS", "RP", "SYM", "TO", "UH", "VB", "VBD", "VBG", "VBN", "VBP", "VBZ", "WDT", "WP", "WP$", "WRB"}));
  
  public BaseLexicon(Index<String> wordIndex, Index<String> tagIndex){
    this.wordIndex = wordIndex;
    this.tagIndex = tagIndex;
    unkIndex = wordIndex.indexOf(UNKNOWN_WORD, true);
  }
  

  /**
   * The score of a word given a tag.  Corresponds to log-probabilities
   */
  public abstract double score(IntTaggedWord itw);

  /**
   * The set of (String) tags allowed for a given word
   */
  public abstract Set<IntTaggedWord> tagsForWord(String word);
  
  /**
   * Train lexicon from intTaggedWords
   * @param intTaggedWords
   */
  public abstract void train(Collection<IntTaggedWord> intTaggedWords);
  
  public Map<Integer, Counter<Integer>> getTag2wordsMap() {
    return tag2wordsMap;
  }

  public Map<Integer, Set<IntTaggedWord>> getWord2tagsMap() {
    return word2tagsMap;
  }

  public void setTag2wordsMap(Map<Integer, Counter<Integer>> tag2wordsMap) {
    this.tag2wordsMap = tag2wordsMap;
  }

  public void setWord2tagsMap(Map<Integer, Set<IntTaggedWord>> word2tagsMap) {
    this.word2tagsMap = word2tagsMap;
  }
}
