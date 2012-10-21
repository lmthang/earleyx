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
  protected static final String ORIG_SYMBOL = "";
  protected int origSymbolIndex; // indexed by TAG_INDEX
  protected Rule rootRule; // "" -> ROOT
  protected int rootEdge; // "" -> . ROOT
  protected int goalEdge; // "" -> []
  
  protected int edgeSpaceSize;   // edge space
  
  /** convert strings into integers **/
  protected Index<String> parserWordIndex;
  protected Index<String> parserTagIndex;
  // keys are nonterminal indices (indexed by parserTagIndex)
  // values are used to retrieve nonterminals (when necessary) in the order that
  //   we processed them when loading treebank or grammar files (more for debug purpose)
  protected Map<Integer, Integer> parserNonterminalMap; 
  
  /** prefix & syntactic prefix probabilities: all in log forms **/
  // prefixProb[i]: prefix prob of word_0...word_(i-1) log-sum of thisPrefixProb
  protected double[] prefixProb; // numWords+1 
  // synPrefixProb[i]: syntatic prefix prob of word_0...word_(i-1) log-sum of thisSynPrefixProb
  protected double[] synPrefixProb;

  /** per word info **/
  // to accumulate probabilities of all paths leading to a particular words
  protected DoubleList thisPrefixProb;
  protected DoubleList thisAGPrefixProb; // for debug purpose: accumulate prefix prob arising from AG rules
  // to accumulate probabilities of all paths leading to a particular words, but don't commit to lexical rewriting
  protected DoubleList thisSynPrefixProb;
  
  /** scaling info **/
  // * Invariants (here we assume things in normal probs not log prob):
  // Let P(w0...w_(i-1) be the non-scaled prefix prob
  // prefixProb[i]: be the scaled prefix prob for w0...w_(i-1)
  // prefixProb[0] = 1
  // prefixProb[1] = P(w0)
  //
  // scaling[1] = 1
  // scaling[2] = 1/P(w0)
  // scaling[i] = P(w0..w_(i-3)) / P(w0..w_(i-2))
  // prefixProb[i] = scaling[1]*...scaling[i]*P(w0..w_(i-1)) = P(w0..w_(i-1)) / P(w0..w_(i-2))
  // Thus, scaling[i] = 1/prefixProb[i-1]
  // surprisal[i] now = -log(prefixProb[i])
  protected boolean isScaling = false;
  protected double[] scaling; // log 
  protected double[][] scalingMatrix; // used for extended rules, scalingMatrix[left][right] = log sum scaling[left+1] ... scaling[right]
  
  /** current sentence info **/
  protected List<String> words;
  protected List<Integer> wordIndices;
  protected int[][] chartCount; // chartCount[left][right]: how many categories at the cell [left, right]
  protected int numWords = 0;
  
  protected boolean containsExtendedRule = false;
  public static int verbose = -1;
  protected static DecimalFormat df = new DecimalFormat("0.0000");
  protected static DecimalFormat df1 = new DecimalFormat("0.00");

  public EarleyParser(Treebank treebank, String rootSymbol){
    preInit(rootSymbol);
    Pair<Collection<Rule>, Collection<IntTaggedWord>> rules_itws = 
      Utility.extractRulesWordsFromTreebank(treebank, parserWordIndex, parserTagIndex, parserNonterminalMap);
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
  
  // constructors with scailing
  public EarleyParser(Treebank treebank, String rootSymbol, boolean isScaling){
    this(treebank, rootSymbol);
    this.isScaling = isScaling;
  }
  public EarleyParser(String grammarFile, String rootSymbol, boolean isScaling){
    this(grammarFile, rootSymbol);
    this.isScaling = isScaling;
  }
  public EarleyParser(BufferedReader br, String rootSymbol, boolean isScaling){
    this(br, rootSymbol);
    this.isScaling = isScaling;
  }
  
  // add root rule
  private void preInit(String rootSymbol){
    parserWordIndex = new HashIndex<String>();
    parserTagIndex = new HashIndex<String>();
    parserNonterminalMap = new HashMap<Integer, Integer>();
    
    rootRule = new Rule(ORIG_SYMBOL, Arrays.asList(rootSymbol), 1.0, parserTagIndex, parserTagIndex);
    origSymbolIndex = parserTagIndex.indexOf(ORIG_SYMBOL);
    parserNonterminalMap.put(origSymbolIndex, parserNonterminalMap.size());
    assert(parserNonterminalMap.get(origSymbolIndex) == 0);
    
    rules = new ArrayList<Rule>();
    rules.add(rootRule);
  }
  
  private void init(BufferedReader br, String rootSymbol){
    extendedRules = new ArrayList<Rule>();
    
    Map<Integer, Counter<Integer>> tag2wordsMap = new HashMap<Integer, Counter<Integer>>();
    Map<Integer, Set<IntTaggedWord>> word2tagsMap = new HashMap<Integer, Set<IntTaggedWord>>();
        
    try {
      RuleFile.parseRuleFile(br, 
          rules, extendedRules, tag2wordsMap, word2tagsMap, parserNonterminalMap, parserWordIndex, parserTagIndex);
    } catch (IOException e) {
      System.err.println("! Problem initializing Earley parser");
      e.printStackTrace();
    }
    
    // // convert to log prob
    for(int iT : tag2wordsMap.keySet()){
      Counter<Integer> counter = tag2wordsMap.get(iT);
      for (int iW : counter.keySet()) {
        double prob = counter.getCount(iW);
        if(prob<0 || prob>1){ // make sure this is a proper prob
          System.err.println("! prob of " + parserTagIndex.get(iT) + "->" + parserWordIndex.get(iW) + " " + prob 
              + " not in [0, 1]");
          System.exit(1);
        }
        counter.setCount(iW, Math.log(prob));
      }
      // Counters.logInPlace(counter);
    }
    
    
    buildGrammarLex(rules, extendedRules, tag2wordsMap, word2tagsMap);
    if(extendedRules.size()>0){
      containsExtendedRule = true;
      if(verbose>=0){
        System.err.println("# Num extended rules = " + extendedRules.size());
      }
    }
  }
  
  private void postInit(String rootSymbol){
    // root
    rootEdge = g.getEdgeSpace().indexOf(rootRule.toEdge());
    goalEdge = g.getEdgeSpace().indexOfTag(rootRule.getMother());
    assert(goalEdge == g.getEdgeSpace().indexOf(rootRule.getMotherEdge()));
    
    edgeSpaceSize = g.getEdgeSpace().size();
    
    if(verbose>=2){
      System.err.println("postInit Earley Parser -- nonterminals: " + Utility.sprint(parserTagIndex, parserNonterminalMap.keySet()));
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
      System.err.println("\n### Learning grammar ... ");
    }
    g = new Grammar(parserWordIndex, parserTagIndex, parserNonterminalMap);
    g.learnGrammar(rules, extendedRules);
    
    /* learn lexicon */
    if (verbose>=1){
      System.err.println("\n### Learning lexicon ... ");
    }
    lex = new SmoothLexicon(parserWordIndex, parserTagIndex);
    lex.train(intTaggedWords);
  }
  
  public void buildGrammarLex(
      Collection<Rule> rules,
      Collection<Rule> extendedRules,
      Map<Integer, Counter<Integer>> tag2wordsMap,
      Map<Integer, Set<IntTaggedWord>> word2tagsMap) {

    /* learn grammar */
    if (verbose>=1){
      System.err.print("\n### Learning grammar ... ");
    }    
    g = new Grammar(parserWordIndex, parserTagIndex, parserNonterminalMap);
    g.learnGrammar(rules, extendedRules);
    
    /* create lexicon */
    if (verbose>=1){
      System.err.println("\n### Learning lexicon ... ");
    }
    lex = new SmoothLexicon(parserWordIndex, parserTagIndex);
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
      wordIndices.add(parserWordIndex.indexOf(word, true));
    }
    
    List<Double> surprisalList = new ArrayList<Double>();
    List<Double> synSurprisalList = new ArrayList<Double>();
    List<Double> lexSurprisalList = new ArrayList<Double>();
    List<Double> stringProbList = new ArrayList<Double>();
    
    // init
    sentInit();
        
    double lastProbability = 1.0;
    for(int right=1; right<=numWords; right++){ // span [0, rightEdge] covers words 0, ..., rightEdge-1
      int wordId = right-1;
      parseWord(right);
            
      double prefixProbability = Math.exp(prefixProb[right]);
      if(!isScaling){
        // string probs
        double stringProbability = Math.exp(stringProbability(right));
        stringProbList.add(stringProbability);

        // surprisals
        double prefixProbabilityRatio = prefixProbability / lastProbability;
        assert prefixProbabilityRatio <= 1.0 + 1e-10;
        surprisalList.add(-Math.log(prefixProbabilityRatio));
        
        // syntatic/lexical surprisals
        double synPrefixProbability = Math.exp(synPrefixProb[right]);
        synSurprisalList.add(-Math.log(synPrefixProbability/lastProbability));
        lexSurprisalList.add(-Math.log(prefixProbability/synPrefixProbability));

        // print info
        if(verbose>=0){
          System.err.println(Utility.sprint(parserWordIndex, wordIndices.subList(0, right)));
          String msg = "Prefix probability: " + prefixProbability + "\n" +
          "String probability: " + stringProbability + "\n" +
          "Surprisal: " + surprisalList.get(wordId) + " = -log(" + 
          prefixProbability + "/" + lastProbability + ")\n";
          System.err.println(msg);
        }
        lastProbability = prefixProbability;
      } else {
        // surprisals
        surprisalList.add(-prefixProb[right]); // note: prefix prob is scaled, and equal to log P(w0..w_(right-1))/P(w0..w_(right-2))
        if(verbose>=0){
          System.err.println(Utility.sprint(parserWordIndex, wordIndices.subList(0, right)));
          String msg = "Scaled prefix probability: " + prefixProbability + "\n" +
          "Scaling: " + scaling[right] + "\n" + 
          "Surprisal: " + surprisalList.get(wordId) + " = -" + prefixProb[right] + "\n";
          System.err.println(msg);
        }
      }
      
            
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
  public void parseWord(int right) { // 
    //initializeTemporaryProbLists();
    String word = words.get(right-1);
    wordInitialize();
    
    /** Scailing factors **/
    if(isScaling){ // scaling
      scaling[right] = -prefixProb[right-1];
      if(verbose>=1){
        System.err.println("# Scaling " + right + ": " + Math.exp(scaling[right]) + ", \t prev prefix prob = " + Math.exp(prefixProb[right-1]));
      }
      
      // scaling matrix for extended rules
      if(containsExtendedRule){
        for (int i = 0; i < right; i++) {
          scalingMatrix[i][right] = scalingMatrix[i][right-1] + scaling[right];
        }
      }
    }
    
    /** Handle normal rules **/
    Set<IntTaggedWord> iTWs = lex.tagsForWord(word);
    if (verbose>=1){
      System.err.println("# " + right + "\t" + word + ", numTags=" + iTWs.size());
    }
    for (IntTaggedWord itw : iTWs) { // go through each POS tag the current word could have
      int edge = g.getEdgeSpace().indexOfTag(itw.tag());
      double score = lex.score(itw);
      if(verbose>=1){
        System.err.println("Lexical prob: " + parserTagIndex.get(itw.tag()) + "->[_" 
            + parserWordIndex.get(itw.word()) + "] : " + Math.exp(score));
        //System.err.println(itw.toString(parserWordIndex, parserTagIndex) + "\t" + score);
      }
      assert(score<=0);
      
      if(isScaling){ // scaling
        score += scaling[right];
      }
      addToChart(right-1, right, edge, 0, score);
    }

    /** Handle extended rules **/
    for (int i = right-2; i >= 0; --i) {
      // find all rules that rewrite into word_i ... word_(rightEdge-1)
      Map<Integer, Double> valueMap = g.getRuleTrie().findAllMap(wordIndices.subList(i, right));
      if(valueMap != null){
        if (verbose>=3){
          System.err.println("AG full: " + words.subList(i, right) + ": " + Utility.sprint(valueMap, parserTagIndex));
        }
        for (int iT : valueMap.keySet()) {
          int edge = g.getEdgeSpace().indexOfTag(iT);
          double score = valueMap.get(iT);
          
          if(isScaling){ // scaling
            score += scalingMatrix[i][right];
          }
          addToChart(i, right, edge, 0, score);
        }
        
      }
    }
    
    
    //2. completion
    if(verbose>=1){
      Timing.startTime();
    }
    completeAll(right);
    if(verbose>=1){
      Timing.tick("# " + word + ", finished completeAll");
    }
    
    //3. predictAll all new active edges for further down the road
    if(verbose>=1){
      Timing.startTime();
    }
    predictAll(right);
    if(verbose>=1){
      Timing.tick("# " + word + ", finished predictAll");
    }
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
  
  /**
   * Consider all triples [left, middle, right] to see if there're any edge pair
   * middle: left X -> _ . Y _ and right: middle Y -> _ and , we'll then create a new edge
   * right: left X -> _ Y . _  
   * @param right
   */
  protected abstract void completeAll(int right);
  
  /*******************/
  /** Other methods **/
  /*******************/
  
  /**
   * Initialization for every sentence
   */
  protected void sentInit(){
    if (verbose>=2){
      System.err.println("# EarleyParser initializing ... ");
    }
    chartCount = new int[numWords+1][numWords+1];
    prefixProb = new double[numWords + 1];
    synPrefixProb = new double[numWords + 1];
    
    if (isScaling){
      scaling = new double[numWords + 1];
      if(containsExtendedRule){
        scalingMatrix = new double[numWords + 1][numWords+1];
      }
    }
    
    // init
    prefixProb[0] = 0.0; // log(1)
  }
  
  /**
   * Initialization for every word
   */
  protected void wordInitialize(){
    thisPrefixProb = new DoubleList();
    thisAGPrefixProb = new DoubleList();
    thisSynPrefixProb = new DoubleList();
  }
  
  protected void storePrefixProb(int right) {
    if(verbose>=2){
      System.err.print("Store prefix prob: ");
      for(double value : thisPrefixProb.toArray()){
        System.err.print(Math.exp(value) + " ");
      }
      System.err.println();
    }
    
    if (thisPrefixProb.size() == 0) { // no path
      prefixProb[right] = Double.NEGATIVE_INFINITY;
      synPrefixProb[right] = Double.NEGATIVE_INFINITY;
    } else {
      prefixProb[right] = ArrayMath.logSum(thisPrefixProb.toArray());
      
      synPrefixProb[right] = ArrayMath.logSum(thisSynPrefixProb.toArray());
    }
    if(verbose>=1){
      double agPrefixProb = Double.NEGATIVE_INFINITY;
      if (thisAGPrefixProb.size() > 0) { // AG paths
        agPrefixProb = ArrayMath.logSum(thisAGPrefixProb.toArray());
      }
      System.err.println("# Prefix prob [0," + right + "] = " 
          + Math.exp(prefixProb[right]) 
          +  ": ag prefix prob=" + Math.exp(agPrefixProb)
          + " + normal prefix prob=" + (Math.exp(prefixProb[right])-Math.exp(agPrefixProb)));
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
  public Index<String> getParserWordIndex() {
    return parserWordIndex;
  }
  public Index<String> getParserTagIndex() {
    return parserTagIndex;
  }
  public Rule getRootRule() {
    return rootRule;
  }
}

/** Unused code **/
//"Syntactic Prefix probability: " + synPrefixProbability + "\n" +
//"Prefix probability ratio for word " + wordId + " " + words.get(wordId) + ": " + prefixProbabilityRatio + "\n" +
//+ 
//"synSurprisal: " + synSurprisalList.get(wordId) +"\n" +
//"lexSurprisal: " + lexSurprisalList.get(wordId)
//
