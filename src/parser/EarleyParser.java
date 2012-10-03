package parser;

import edu.stanford.nlp.ling.Label;
import edu.stanford.nlp.ling.TaggedWord;
import edu.stanford.nlp.math.SloppyMath;
import edu.stanford.nlp.parser.lexparser.IntTaggedWord;
import edu.stanford.nlp.stats.Counter;
import edu.stanford.nlp.stats.Counters;
import edu.stanford.nlp.trees.Tree;
import edu.stanford.nlp.trees.Treebank;
import edu.stanford.nlp.util.HashIndex;
import edu.stanford.nlp.util.Index;
import edu.stanford.nlp.util.Pair;
import edu.stanford.nlp.util.Timing;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.FileWriter;
import java.io.IOException;
import java.io.Reader;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.Map.Entry;

/**
 * Trains grammar and lexicon models for Earley parsers
 * 
 * @author lmthang
 */

public class EarleyParser {
  protected Grammar g;
  protected BaseLexicon lex;
  protected Collection<Rule> rules;

  public static final Index<String> WORD_INDEX = new HashIndex<String>();
  public static final Index<String> TAG_INDEX = new HashIndex<String>();
  
  protected int[][] matrix2linear; // convert matrix indices into linear indices
  protected boolean[][] chartEntries; // chartEntries[matrix2linear[leftEdge][rightEdge]][categoryNumber]
  protected double[][] forwardProb;   // forwardProb[matrix2linear[leftEdge][rightEdge]][categoryNumber]
  protected double[][] innerProb;     // innerProb[matrix2linear[leftEdge][rightEdge]][categoryNumber]
  protected double[] prefixProb;
  protected double[] synPrefixProb;

  public EarleyParser(Grammar g, BaseLexicon lex, Collection<Rule> rules) {
    this.g = g;
    this.lex = lex;
    this.rules = rules;
  }

  public EarleyParser(Treebank treebank, String rootSymbol){
    Rule rootRule = new Rule("", Arrays.asList(new String[]{rootSymbol}), 
        1.0, TAG_INDEX, TAG_INDEX);
    Pair<Collection<Rule>, Collection<IntTaggedWord>> rules_itws = 
      Utility.extractRulesWordsFromTreebank(treebank, WORD_INDEX, TAG_INDEX);
    rules = rules_itws.first();
    buildGrammarLex(rules, rules_itws.second(), rootRule);
  }
  
  public EarleyParser(String grammarFile, String rootSymbol){
    Rule rootRule = new Rule("", Arrays.asList(new String[]{rootSymbol}), 
        1.0, TAG_INDEX, TAG_INDEX);
    rules = new ArrayList<Rule>();
    
    Map<IntTaggedWord, Counter<IntTaggedWord>> wordCounterTagMap = new HashMap<IntTaggedWord, Counter<IntTaggedWord>>();
    Map<IntTaggedWord, Set<IntTaggedWord>> tagsForWord = new HashMap<IntTaggedWord, Set<IntTaggedWord>>();
    Set<IntTaggedWord> preterminalSet = new HashSet<IntTaggedWord>();
    Map<Label, Counter<String>> tagHash = new HashMap<Label, Counter<String>>();
    Set<String> seenEnd = new HashSet<String>();
    Collection<Rule> extendedRules = new ArrayList<Rule>();
    
    try {
      RuleFile.parseRuleFile(grammarFile, rules, extendedRules, wordCounterTagMap, 
          tagsForWord, preterminalSet, tagHash, seenEnd);
    } catch (IOException e) {
      System.err.println("! Problem reading grammar file " + grammarFile);
      e.printStackTrace();
    }
    
    // log
    for(IntTaggedWord iT : wordCounterTagMap.keySet()){
      Counter<IntTaggedWord> counter = wordCounterTagMap.get(iT);
      Counters.logInPlace(counter);
    }
    for(Label label : tagHash.keySet()){
      Counter<String> counter = tagHash.get(label);
      Counters.logInPlace(counter);
    }
    
    getGeneratorLexDistribution(rules, extendedRules, wordCounterTagMap, tagsForWord, 
        preterminalSet, tagHash, 
        seenEnd, rootRule);
  }
  
  /**
   * Construct parsers from rules and int tagged words
   * 
   * @param originalRules
   * @param intTaggedWords
   * @param rootRule
   * @return
   */
  private void buildGrammarLex(Collection<Rule> originalRules, 
      Collection<IntTaggedWord> intTaggedWords, Rule rootRule) {
      
    Collection<Rule> rules = new ArrayList<Rule>();
    rules.addAll(originalRules);
    rules.add(rootRule);
    
    /* learn grammar */
    System.err.print("# Learning grammar ... ");
    g = new Grammar();
    g.learnGrammar(rules, new ArrayList<Rule>(), rootRule);
    System.err.println("Done!");
    
    /* learn lexicon */
    System.err.print("# Learning lexicon ... ");
    lex = new SmoothLexicon(WORD_INDEX, TAG_INDEX);
    lex.train(intTaggedWords);
    System.err.println("Done!");
  }
  
  public void getGeneratorLexDistribution(
      Collection<Rule> originalRules,
      Collection<Rule> extendedRules,
      Map<IntTaggedWord, Counter<IntTaggedWord>> wordCounterTagHash, 
      Map<IntTaggedWord, Set<IntTaggedWord>> tagsForWord, 
      Set<IntTaggedWord> preterminalSet,
      Map<Label, Counter<String>> tagHash,
      Set<String> seenEnd, Rule rootRule
      ) {
    Collection<Rule> rules = new ArrayList<Rule>();
    rules.addAll(originalRules);
    rules.add(rootRule);
  
    /* learn grammar */
    System.err.print("# Learning grammar ... ");
    g = new Grammar();
    g.learnGrammar(rules, extendedRules, rootRule);
    System.err.println("Done!");
    
    /* learn lexicon */
    lex = new SmoothLexicon(WORD_INDEX, TAG_INDEX);
//    lex.setWordCounterTagMap(wordCounterTagHash);
//    lex.setTagsForWord(tagsForWord);
//    lex.setPreterminals(preterminalSet);
//    lex.setTagHash(tagHash);
//    lex.setSeenEnd(seenEnd);
  }
  
  /**
   * Parse a list of pre-tokenized sentences, one per line, from a Reader.
   */
  public void parseSentences(List<String> sentences, List<String> indices, String outPrefix) throws IOException {
    assert(sentences.size() == indices.size());
    BufferedWriter outWriter = new BufferedWriter(new FileWriter(outPrefix + ".srprsl"));
    BufferedWriter synOutWriter = new BufferedWriter(new FileWriter(outPrefix + ".SynSp"));
    BufferedWriter lexOutWriter = new BufferedWriter(new FileWriter(outPrefix + ".LexSp"));
    BufferedWriter stringOutWriter = new BufferedWriter(new FileWriter(outPrefix + ".string"));
    
    for (int i = 0; i < sentences.size(); i++) {
      String sentenceString = sentences.get(i);
      String id = indices.get(i);
      
      System.err.println("### Sent " + i + ": id=" + id + ", "+ sentenceString);
      
      // start
      Timing.startTime();     
      List<List<Double>> resultLists = parseSentence(sentenceString);
      assert(resultLists.size() == 4);
      List<Double> surprisalList = resultLists.get(0);
      List<Double> synSurprisalList = resultLists.get(1);
      List<Double> lexSurprisalList = resultLists.get(2);
      List<Double> stringProbList = resultLists.get(3);
      
      // end
      Timing.tick("finished parsing sentence. ");
      if(outWriter != null){ // output
        outWriter.write("# " + id + "\n");
        synOutWriter.write("# " + id + "\n");
        lexOutWriter.write("# " + id + "\n");
        stringOutWriter.write("# " + id + "\n");
        Utility.outputSentenceResult(sentenceString, outWriter, surprisalList);
        Utility.outputSentenceResult(sentenceString, synOutWriter, synSurprisalList);
        Utility.outputSentenceResult(sentenceString, lexOutWriter, lexSurprisalList);
        Utility.outputSentenceResult(sentenceString, stringOutWriter, stringProbList);
      }
    }
  }

  public void initialize(int numWords){
    /* 1-D */
    prefixProb = new double[numWords+1];
    synPrefixProb = new double[numWords+1];
    Utility.initToNegativeInfinity(prefixProb);
    Utility.initToNegativeInfinity(synPrefixProb);
    
    /* 2-D */
    matrix2linear = new int[numWords+1][numWords+1];
    // go in the order of CKY parsing
    int numCells=0;
    for(int rightEdge=1; rightEdge<=numWords; rightEdge++){
      for(int leftEdge=rightEdge-1; leftEdge>=0; leftEdge--){
        matrix2linear[leftEdge][rightEdge] = numCells++;
      }
    }
    assert(numCells==(numWords*(numWords+1)/2));
    
    chartEntries = new boolean[numCells][g.stateSpace.size()];
    forwardProb = new double[numCells][g.stateSpace.size()];
    innerProb = new double[numCells][g.stateSpace.size()];
    Utility.initToNegativeInfinity(forwardProb);
    Utility.initToNegativeInfinity(innerProb);
  }
  
  /**
   * Returns the total probability of complete parses for the string prefix parsed so far.
   */
  public double stringProbability(int rightEdge) {
    int index = matrix2linear[0][rightEdge];
    if (chartEntries[index][g.goalEdge]) {
      assert(Math.abs(innerProb[index][g.goalEdge] - forwardProb[index][g.goalEdge]) < 1e-5);
      return innerProb[index][g.goalEdge];
    } else {
      return Double.NEGATIVE_INFINITY;
    }
  }

  /**
   * Parse a single sentence
   * @param sentenceString
   * @return various values: surprisal, syntactic, lexical, and string probability.
   * 
   * @throws IOException
   */
  public List<List<Double>> parseSentence(String sentenceString){
    List<String> sentence = Arrays.asList(sentenceString.split("\\s+"));
    
    List<Double> surprisalList = new ArrayList<Double>();
    List<Double> synSurprisalList = new ArrayList<Double>();
    List<Double> lexSurprisalList = new ArrayList<Double>();
    List<Double> stringProbList = new ArrayList<Double>();
    
    // init
    int numWords = sentence.size(); 
    initialize(numWords);
    
    double lastProbability = 1.0;
    for(int rightEdge=1; rightEdge<=numWords; rightEdge++){ // span [0, rightEdge] covers words 0, ..., rightEdge-1
      int wordId = rightEdge-1;
      parseNextWord(sentence.get(wordId), rightEdge);
      
      double prefixProbability = Math.exp(prefixProb[wordId]);
      double synPrefixProbability = Math.exp(synPrefixProb[wordId]);
      double stringProbability = Math.exp(stringProbability(rightEdge));
      double prefixProbabilityRatio = prefixProbability / lastProbability;
      assert prefixProbabilityRatio <= 1.0;
     
      
      surprisalList.add(-Math.log(prefixProbabilityRatio));
      synSurprisalList.add(-Math.log(synPrefixProbability/lastProbability));
      lexSurprisalList.add(-Math.log(prefixProbability/synPrefixProbability));
      stringProbList.add(stringProbability);
      lastProbability = prefixProbability;
      
      // print info
      String msg = "Prefix probability: " + prefixProbability + "\n" +
      "Syntactic Prefix probability: " + synPrefixProbability + "\n" +
      "String probability: " + stringProbability + "\n" +
      "Prefix probability ratio for word " + wordId + " " + sentence.get(wordId) + ": " + prefixProbabilityRatio + "\n" + 
      "Surprisal: " + surprisalList.get(wordId) +"\n" + 
      "synSurprisal: " + synSurprisalList.get(wordId) +"\n" +
      "lexSurprisal: " + lexSurprisalList.get(wordId);
      System.err.println(msg);
    }
    
    // compile result lists
    List<List<Double>> resultLists = new ArrayList<List<Double>>();
    resultLists.add(surprisalList);
    resultLists.add(synSurprisalList);
    resultLists.add(lexSurprisalList);
    resultLists.add(stringProbList);
    return resultLists;
  }
  
  /**
   * call this method to read in the next word from the input and build the corresponding chart entries
   */
  public void parseNextWord(String word, int currentWordId) {
    //initializeTemporaryProbLists();
    
    STOP HERE
    
    /** Handle normal rules **/
    Set<IntTaggedWord> iTWs = lex.tagsForWord(word);
    System.err.println("# " + currentWordId + "\t" + word + ", numTags=" + iTWs.size());
    
    int tag = -1; double innerScore = 0; double forwardScore = 0;
    for (IntTaggedWord itw : iTWs) {
      tag = g.stateSpace.indexOfTag(itw.toTagITW()); //intTags[i];
      innerScore = lex.score(itw);
      addEdge(currentWordId - 1, currentWordId, itw., forwardScore, innerScore); // forward probability is irrelevant for tags
    }

    /** Handle extended rules **/
    if(Grammar.useTrie){
      List<IntTaggedWord> suffixList = new LinkedList<IntTaggedWord>();
      suffixList.add(sentenceITWs.get(currentWordId-1)); // add current word 
      
//      if(PrefixProbabilityParserOld.verbose >= 2){
//        System.err.println("\n## parseNextWords trie rules for: " + suffixList);
//      }
      for (int i = currentWordId-2; i >= 0; --i) {
        suffixList.add(0, sentenceITWs.get(i)); // contains word_i ... word_(currentWordId-1) 
        
        Map<Integer, Double> statePairs = g.getRuleTrie().findAllMap(suffixList);
        if(statePairs != null){
          for (Entry<Integer, Double> pair : statePairs.entrySet()) {
            int edge = pair.getKey();
            double score = pair.getValue();
//            if(PrefixProbabilityParserOld.verbose >= 2){
//              System.err.println("# " + g.stateSpace.get(edge) + "\t" + score);
//            }
//            
            addEdge(i, currentWordId, edge, forwardScore, score);
          }
        }
      }
    }
    
    //storePassiveEdgeChartProbs(currentWord-1);
    //2a. initialize intermediate data structures
    //2. combine
    /* emptyAgenda(); */
    
    Timing.startTime();
    combineAll();
    Timing.tick("# " + word + ", finished combineAll");
    
    //3. predictAll all new active edges for further down the road
    Timing.startTime();
    predictAll(currentWordId);
    //storeActiveEdgeChartProbs(currentWord);
    Timing.tick("# " + word + ", finished predictAll");
    
    processedWords.add(word);
    
    //System.exit(1);
  }

  protected void addEdge(int left, int right, int newEdge, double forward, double inner) {    
    if (!chartEntries[left][right][newEdge]) {
      //int[] item = new int[] { left,right,newEdge };
      //agenda.add(item,getPriority(item));
      chartEntries[left][right][newEdge] = true;
      forwardProb[left][right][newEdge] = forward;
      innerProb[left][right][newEdge] = inner;
    } else {
      forwardProb[left][right][newEdge] = SloppyMath.logAdd(forward, forwardProb[left][right][newEdge]);
      innerProb[left][right][newEdge] = SloppyMath.logAdd(inner, innerProb[left][right][newEdge]);
    }
    if(verbose >= 2){
      System.err.println("Scan: " + getEdgeInfo(left, right, newEdge, forwardProb[left][right][newEdge], 
          innerProb[left][right][newEdge]));
    }
    //theseForwardProb[left][newEdge].add(forward);
    //theseInnerProb[left][newEdge].add(inner);
  }

  /**
   * getters
   */
  public BaseLexicon getLexicon(){
    return lex;
  }
  public Grammar getGrammar(){
    return g;
  }
  public Collection<Rule> getRules() {
    return rules;
  }
}
