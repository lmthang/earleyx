package parser;

import edu.stanford.nlp.math.ArrayMath;
import edu.stanford.nlp.parser.lexparser.IntTaggedWord;
import edu.stanford.nlp.stats.Counter;
import edu.stanford.nlp.trees.Treebank;
import edu.stanford.nlp.util.DoubleList;
import edu.stanford.nlp.util.HashIndex;
import edu.stanford.nlp.util.Index;
import edu.stanford.nlp.util.Pair;
import edu.stanford.nlp.util.Timing;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.FileNotFoundException;
import java.io.FileWriter;
import java.io.IOException;
import java.text.DecimalFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import utility.Utility;

import lexicon.BaseLexicon;
import lexicon.SmoothLexicon;

/**
 * Trains grammar and lexicon models for Earley parsers
 * 
 * @author lmthang
 */

public abstract class EarleyParser {
  protected Grammar g;
  protected BaseLexicon lex;
  protected Collection<Rule> rules;
  protected Collection<Rule> extendedRules;
  
  // root
  //protected String rootSymbol;
  //protected int rootEdge;
  protected Rule rootRule; // "" -> ROOT
  protected int rootEdge; // ROOT -> []
  protected int goalEdge; // "" -> []
  
  // edge space
  protected int edgeSpaceSize;
  
  public static Index<String> WORD_INDEX = new HashIndex<String>();
  public static Index<String> TAG_INDEX = new HashIndex<String>();
  public static Set<Integer> NONTERMINALS = new HashSet<Integer>();
  
  /** prefix & syntactic prefix probabilities: all in log forms **/
  // to accumulate probabilities of all paths leading to a particular words
  protected DoubleList thisPrefixProb;
  // prefixProb[i]: prefix prob of word_0...word_(i-1) log-sum of thisPrefixProb
  protected double[] prefixProb; 
  // to accumulate probabilities of all paths leading to a particular words, but don't commit to lexical rewriting
  protected DoubleList thisSynPrefixProb;
  // synPrefixProb[i]: syntatic prefix prob of word_0...word_(i-1) log-sum of thisSynPrefixProb
  protected double[] synPrefixProb;

  /** current sentence info **/
  protected List<String> words;
  protected List<Integer> wordIndices;
  protected int[][] chartCount; // chartCount[left][right]: how many categories at the cell [left, right]
  protected int numWords = 0;
  
  public static int verbose = 0;
  protected static DecimalFormat df = new DecimalFormat("0.0000");

  public EarleyParser(Treebank treebank, String rootSymbol){
    preInit(rootSymbol);
    Pair<Collection<Rule>, Collection<IntTaggedWord>> rules_itws = 
      Utility.extractRulesWordsFromTreebank(treebank, WORD_INDEX, TAG_INDEX, NONTERMINALS);
    rules.addAll(rules_itws.first());
    buildGrammarLex(rules, new ArrayList<Rule>(), rules_itws.second());
    postInit(rootSymbol);
  }
  
  
  public EarleyParser(String grammarFile, String rootSymbol){
    preInit(rootSymbol);
    try{
      init(Utility.getBufferedReaderFromFile(grammarFile), rootSymbol);
    } catch(FileNotFoundException e){
      System.err.println("! Problem reading grammar file " + grammarFile);
      e.printStackTrace();
    }
    postInit(rootSymbol);
  }
  
  public EarleyParser(BufferedReader br, String rootSymbol){
    preInit(rootSymbol);
    init(br, rootSymbol);
    postInit(rootSymbol);
  }
  
  // add root rule
  private void preInit(String rootSymbol){
    rootRule = new Rule("", Arrays.asList(rootSymbol), 1.0, TAG_INDEX, TAG_INDEX);
    rules = new ArrayList<Rule>();
    rules.add(rootRule);
  }
  
  private void init(BufferedReader br, String rootSymbol){
    extendedRules = new ArrayList<Rule>();
    
    Map<Integer, Counter<Integer>> tag2wordsMap = new HashMap<Integer, Counter<Integer>>();
    Map<Integer, Set<IntTaggedWord>> word2tagsMap = new HashMap<Integer, Set<IntTaggedWord>>();
        
    try {
      RuleFile.parseRuleFile(br, 
          rules, extendedRules, tag2wordsMap, word2tagsMap, NONTERMINALS, WORD_INDEX, TAG_INDEX);
    } catch (IOException e) {
      System.err.println("! Problem initializing Earley parser");
      e.printStackTrace();
    }
    if(verbose>=2){
      System.err.println("Earley Parser -- nonterminals: " + Utility.sprint(TAG_INDEX, NONTERMINALS));
    }
    
    // // convert to log prob
    for(int iT : tag2wordsMap.keySet()){
      Counter<Integer> counter = tag2wordsMap.get(iT);
      for (int iW : counter.keySet()) {
        double prob = counter.getCount(iW);
        if(prob<0 || prob>1){ // make sure this is a proper prob
          System.err.println("! prob of " + TAG_INDEX.get(iT) + "->" + WORD_INDEX.get(iW) + " " + prob 
              + " not in [0, 1]");
          System.exit(1);
        }
        counter.setCount(iW, Math.log(prob));
      }
      // Counters.logInPlace(counter);
    }
    
    buildGrammarLex(rules, extendedRules, tag2wordsMap, word2tagsMap);
  }
  
  private void postInit(String rootSymbol){
    // root
    rootEdge = g.getStateSpace().indexOf(rootRule.toEdge());
    goalEdge = g.getStateSpace().indexOfTag(rootRule.getMother());
    assert(goalEdge == g.getStateSpace().indexOf(rootRule.getMotherEdge()));
    
    edgeSpaceSize = g.getStateSpace().size();
    
    if(verbose>=2){
      System.err.println("Earley Parser -- nonterminals: " + Utility.sprint(TAG_INDEX, NONTERMINALS));
    }
  }
  
  /**
   * Construct grammar and lexicon from rules and int tagged words
   * 
   * @param originalRules
   * @param intTaggedWords
   * @param rootRule
   * @return
   */
  private void buildGrammarLex(Collection<Rule> rules, Collection<Rule> extendedRules, Collection<IntTaggedWord> intTaggedWords){//, Rule rootRule) {
    /* learn grammar */
    if (verbose>=1){
      System.err.print("# Learning grammar ... ");
    }
    g = new Grammar(WORD_INDEX, TAG_INDEX, NONTERMINALS);
    g.learnGrammar(rules, extendedRules);
    System.err.println("Done!");
    
    /* learn lexicon */
    System.err.print("# Learning lexicon ... ");
    lex = new SmoothLexicon(WORD_INDEX, TAG_INDEX);
    lex.train(intTaggedWords);
    
    if (verbose>=1){
      System.err.println("Done!");
    }
  }
  
  public void buildGrammarLex(
      Collection<Rule> rules,
      Collection<Rule> extendedRules,
      Map<Integer, Counter<Integer>> tag2wordsMap,
      Map<Integer, Set<IntTaggedWord>> word2tagsMap) {
//    Collection<Rule> rules = new ArrayList<Rule>();
//    rules.addAll(originalRules);
//    rules.add(rootRule);
  
    /* learn grammar */
    if (verbose>=1){
      System.err.print("# Learning grammar ... ");
    }
    
    g = new Grammar(WORD_INDEX, TAG_INDEX, NONTERMINALS);
    g.learnGrammar(rules, extendedRules);
    
    if (verbose>=1){
      System.err.println("Done!");
    }
    
    /* create lexicon */
    lex = new SmoothLexicon(WORD_INDEX, TAG_INDEX);
    lex.setTag2wordsMap(tag2wordsMap);
    lex.setWord2tagsMap(word2tagsMap);
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

  /**
   * Parse a single sentence
   * @param sentenceString
   * @return various values: surprisal, syntactic, lexical, and string probability.
   * 
   * @throws IOException
   */
  public List<List<Double>> parseSentence(String sentenceString){
    words = Arrays.asList(sentenceString.split("\\s+"));
    numWords = words.size();
    wordIndices = new ArrayList<Integer>();
    for (String word : words) {
      wordIndices.add(WORD_INDEX.indexOf(word, true));
    }
    
    List<Double> surprisalList = new ArrayList<Double>();
    List<Double> synSurprisalList = new ArrayList<Double>();
    List<Double> lexSurprisalList = new ArrayList<Double>();
    List<Double> stringProbList = new ArrayList<Double>();
    
    // init
    sentInitialize();
    
    
    double lastProbability = 1.0;
    for(int rightEdge=1; rightEdge<=numWords; rightEdge++){ // span [0, rightEdge] covers words 0, ..., rightEdge-1
      int wordId = rightEdge-1;
      parseNextWord(rightEdge);
      
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
      "Prefix probability ratio for word " + wordId + " " + words.get(wordId) + ": " + prefixProbabilityRatio + "\n" + 
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
   * Read in the next word and build the corresponding chart entries
   * word_(rightEdge-1) corresponds to the span [rightEdge-1, rightEdge]
   * 
   * @param right
   */
  public void parseNextWord(int right) { // 
    //initializeTemporaryProbLists();
    String word = words.get(right-1);
    wordInitialize();
    
    /** Handle normal rules **/
    Set<IntTaggedWord> iTWs = lex.tagsForWord(word);
    if (verbose>=2){
      System.err.println("# " + right + "\t" + word + ", numTags=" + iTWs.size());
    }
    for (IntTaggedWord itw : iTWs) { // go through each POS tag the current word could have
      int edge = g.getStateSpace().indexOfTag(itw.tag());
      double score = lex.score(itw);
      assert(score<0);
      addToChart(right-1, right, edge, 0, score);
    }

    /** Handle extended rules **/
    for (int i = right-2; i >= 0; --i) {
      // find all rules that rewrite into word_i ... word_(rightEdge-1)
      Map<Integer, Double> valueMap = g.getRuleTrie().findAllMap(wordIndices.subList(i, right));
      if(valueMap != null){
        for (int iT : valueMap.keySet()) {
          int edge = g.getStateSpace().indexOfTag(iT);
          double score = valueMap.get(iT);
          addToChart(i, right, edge, 0, score);
        }
        if (verbose>=3){
          System.err.println(words.subList(i, right) + ": " + Utility.sprint(valueMap, TAG_INDEX));
        }
      }
    }
    
    
    //storePassiveEdgeChartProbs(currentWord-1);
    //2a. initialize intermediate data structures
    //2. combine
    /* emptyAgenda(); */
    
    Timing.startTime();
    combineAll(right);
    Timing.tick("# " + word + ", finished combineAll");
    
    //3. predictAll all new active edges for further down the road
    Timing.startTime();
    predictAll(right);
    //storeActiveEdgeChartProbs(currentWord);
    Timing.tick("# " + word + ", finished predictAll");
  }

  /**********************/
  /** Abstract methods **/
  /**********************/
  /**
   * Returns the total probability of complete parses for the string prefix parsed so far.
   */
  public abstract double stringProbability(int right);
  protected abstract void addToChart(int state, int left, int right, 
      double logForward, double logInner);
  protected abstract void predictAll(int right);
  protected abstract void combineAll(int right);
  
  /*******************/
  /** Other methods **/
  /*******************/
  
  /**
   * Initialization for every sentence
   */
  protected void sentInitialize(){
    if (verbose>=1){
      System.err.println("# EarleyParser initializing ... ");
    }
    chartCount = new int[numWords+1][numWords+1];
    prefixProb = new double[numWords + 1];
    synPrefixProb = new double[numWords + 1];
    Utility.initToNegativeInfinity(prefixProb);
    Utility.initToNegativeInfinity(synPrefixProb);
  }
  
  /**
   * Initialization for every word
   */
  protected void wordInitialize(){
    thisPrefixProb = new DoubleList();
    thisSynPrefixProb = new DoubleList();
  }
  
  protected void storePrefixProb(int right) {
    if (thisPrefixProb.size() == 0) { // no path
      prefixProb[right] = Double.NEGATIVE_INFINITY;
      synPrefixProb[right] = Double.NEGATIVE_INFINITY;
    } else {
      prefixProb[right] = ArrayMath.logSum(thisPrefixProb.toArray());
      synPrefixProb[right] = ArrayMath.logSum(thisSynPrefixProb.toArray());
    }
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

/** Unused code **/
