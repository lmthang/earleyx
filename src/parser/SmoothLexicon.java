package parser;

import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Set;

import edu.stanford.nlp.parser.lexparser.IntTaggedWord;
import edu.stanford.nlp.stats.ClassicCounter;
import edu.stanford.nlp.stats.Counter;
import edu.stanford.nlp.stats.Counters;
import edu.stanford.nlp.util.Index;

public class SmoothLexicon extends BaseLexicon {
  public SmoothLexicon(Index<String> wordIndex, Index<String> tagIndex){
    super(wordIndex, tagIndex);
  }
  
  public Set<IntTaggedWord> tagsForWord(String word) {
    int iW = wordIndex.indexOf(word, true);
    Set<Integer> tagIndices = null;
    if(!word2tagsMap.containsKey(iW)){ // unknown word
      word = getSignature(word);
      iW = wordIndex.indexOf(word, true);
      
      if(!word2tagsMap.containsKey(iW)){ // unknown signature
        word = UNKNOWN_WORD;
        iW = wordIndex.indexOf(word, true);
      }
    }
    assert(word2tagsMap.containsKey(iW));
    tagIndices = word2tagsMap.get(iW);
    
    // build int tagged words
    Set<IntTaggedWord> itws = new HashSet<IntTaggedWord>();
    for(int iT : tagIndices){ // go through all tags
      itws.add(new IntTaggedWord(iW, iT));
    }
    return itws;
  }

  public double score(IntTaggedWord itw) {
    if(!tag2wordsMap.containsKey(itw.tag())){ // no such tag
      return Double.NEGATIVE_INFINITY;
    }
    int iW = itw.word();
    String word = wordIndex.get(iW);
    
    if(!word2tagsMap.containsKey(iW)){ // unknown word
      word = getSignature(word);
      iW = wordIndex.indexOf(word, true);
      
      if(!word2tagsMap.containsKey(iW)){ // unknown signature
        word = UNKNOWN_WORD;
        iW = unkIndex;
      }
    }
     
//    System.err.println("score: " + word + "\t" + itw + "\t" + itw.wordString(wordIndex) +
//        "\t" + tag2wordsMap.get(itw.tag()).getCount(iW));
    
    return tag2wordsMap.get(itw.tag()).getCount(iW);
  }

  public void train(Collection<IntTaggedWord> intTaggedWords) {
    // initialize
    tag2wordsMap = new HashMap<Integer, Counter<Integer>>(); 
    word2tagsMap = new HashMap<Integer, Set<Integer>>();
    
    
    // scan data, collect counts
    for(IntTaggedWord itw : intTaggedWords){
      int tag = itw.tag();
      int word = itw.word();
      
      // tag2wordsMap
      if(!tag2wordsMap.containsKey(tag)){
        tag2wordsMap.put(tag, new ClassicCounter<Integer>());
      }
      tag2wordsMap.get(tag).incrementCount(word);
      
      // word2tagsMap
      if(!word2tagsMap.containsKey(word)){
        word2tagsMap.put(word, new HashSet<Integer>());
      }
      word2tagsMap.get(word).add(tag);
    }
    
    // smoothing
    word2tagsMap.put(unkIndex, new HashSet<Integer>());
    for(Integer iT : tag2wordsMap.keySet()){ // tag
      Counter<Integer> wordCounter = tag2wordsMap.get(iT);
      
      // find singleton words to build each signature counter per tag
      ClassicCounter<Integer> signatureCounter = new ClassicCounter<Integer>();
      for(Integer iW : wordCounter.keySet()){ // word
        if(wordCounter.getCount(iW) == 1) { // singleton
          String wordSignature = getSignature(wordIndex.get(iW));
          int signatureIndex = wordIndex.indexOf(wordSignature, true);
          signatureCounter.incrementCount(signatureIndex);
          
          // update word2tags map
          if(!word2tagsMap.containsKey(signatureIndex)){
            word2tagsMap.put(signatureIndex, new HashSet<Integer>());
          }
          word2tagsMap.get(signatureIndex).add(iT);
        }
      }
      wordCounter.addAll(signatureCounter);
      
      // add unk, for a totally new word at test time, whose signature we haven't seen
      wordCounter.incrementCount(unkIndex);
      word2tagsMap.get(unkIndex).add(iT); // add all tags to unk
      
      // normalize
      Counters.normalize(wordCounter);
      
      // convert to log space
      Counters.logInPlace(wordCounter);
    }
  }

  /**
   * Signature for a specific word (copied from BaseUnknownWordModel)
   * 
   * @param word The word
   * @return A "signature" (which represents an equivalence class of Strings), e.g., a suffix of the string
   */
  public String getSignature(String word) {
    boolean useFirst = false; //= true;
    boolean useEnd = true; 
    boolean useFirstCap = true;
    int endLength = 2;
  
    StringBuilder subStr = new StringBuilder("UNK-");
    int n = word.length(); // Thang fix, remove -1;
    char first = word.charAt(0);
    if (useFirstCap) {
      if (Character.isUpperCase(first) || Character.isTitleCase(first)) {
        subStr.append('C');
      } else {
        subStr.append('c');
      }
    }
    if (useFirst) {
      subStr.append(first);
    }
    if (useEnd) {
      subStr.append(word.substring(n - endLength > 0 ? n - endLength : 0, n));
    }
    return subStr.toString();
  }
}
