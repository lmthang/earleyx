package lexicon;

import java.util.Collection;
import java.util.Map;
import java.util.Set;


import sun.reflect.generics.reflectiveObjects.NotImplementedException;

import edu.stanford.nlp.parser.lexparser.IntTaggedWord;
import edu.stanford.nlp.stats.Counter;
import edu.stanford.nlp.util.Index;

public class BaseLexicon implements Lexicon {
  protected Map<Integer, Counter<Integer>> tag2wordsMap;

  protected Map<Integer, Set<IntTaggedWord>> word2tagsMap;
  protected Index<String> wordIndex;
  protected Index<String> tagIndex;
  protected int unkIndex = -1;
  
  public BaseLexicon(Index<String> wordIndex, Index<String> tagIndex){
    this.wordIndex = wordIndex;
    this.tagIndex = tagIndex;
    unkIndex = wordIndex.indexOf(UNKNOWN_WORD, true);
  }
  
  public double score(IntTaggedWord itw) {
    // TODO Auto-generated method stub
    return 0;
  }

  public Set<IntTaggedWord> tagsForWord(String word) {
    // TODO Auto-generated method stub
    return null;
  }

  public void train(Collection<IntTaggedWord> intTaggedWords) {
    // TODO Auto-generated method stub
    throw new NotImplementedException();
  }
  
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
