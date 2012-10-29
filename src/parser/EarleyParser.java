package parser;

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

import base.BaseLexicon;
import base.Rule;

import util.LogProbOperator;
import util.Operator;
import util.ProbOperator;
import util.RuleFile;
import util.Util;


/**
 * Abstract class for an Earley parser instance
 * with options to use scaling or not, and to use log-prob or not.
 * 
 * @author Minh-Thang Luong, 2012
 */

public abstract class EarleyParser {
  protected boolean isScaling = false;
  protected boolean isLogProb = true;
  protected Operator operator; // either ProbOperator or LogProbOperator
  
  protected Grammar g;
  protected EdgeSpace edgeSpace;
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
  
  /** prefix & syntactic prefix probabilities **/
  // prefixProb[i]: prefix prob of word_0...word_(i-1) sum/log-sum of thisPrefixProb
  protected double[] prefixProb; // numWords+1 
  // synPrefixProb[i]: syntatic prefix prob of word_0...word_(i-1) sum/log-sum of thisSynPrefixProb
  protected double[] synPrefixProb;

  /** per word info **/
  // to accumulate probabilities of all paths leading to a particular words
  protected DoubleList thisPrefixProb;
  protected DoubleList thisSynPrefixProb;
  
  /** scaling info **/
  // * Invariant:
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
  protected double[] scaling; 
  protected double[] scalingMatrix; // used for extended rules, scalingMatrix[left][right] = sum/log-sum scaling[left+1] ... scaling[right]
  
  /** current sentence info **/
  protected int[][] linear; // convert matrix indices [left][right] into linear indices
  protected int numCells; // numCells==((numWords+2)*(numWords+1)/2)
  
  protected List<String> words;
  protected List<Integer> wordIndices;
  protected int numWords = 0;

  
  
  
  protected boolean containsExtendedRule = false;
  public static int verbose = -1;
  protected static DecimalFormat df = new DecimalFormat("0.0000");
  protected static DecimalFormat df1 = new DecimalFormat("0.00");
  
  // constructors with scailing
  public EarleyParser(Treebank treebank, String rootSymbol, boolean isScaling, boolean isLogProb){
    preInit(rootSymbol, isScaling, isLogProb);
    Pair<Collection<Rule>, Collection<IntTaggedWord>> rules_itws = 
      Util.extractRulesWordsFromTreebank(treebank, parserWordIndex, parserTagIndex, parserNonterminalMap);
    rules.addAll(rules_itws.first());
    buildGrammarLex(rules, new ArrayList<Rule>(), rules_itws.second());
    postInit(rootSymbol);
  }
  public EarleyParser(String grammarFile, String rootSymbol, boolean isScaling, boolean isLogProb){
    preInit(rootSymbol, isScaling, isLogProb);
    try{
      init(Util.getBufferedReaderFromFile(grammarFile), rootSymbol);
    } catch(FileNotFoundException e){
      System.err.println("! Problem reading grammar file " + grammarFile);
      e.printStackTrace();
    }
    postInit(rootSymbol);
  }
  public EarleyParser(BufferedReader br, String rootSymbol, boolean isScaling, boolean isLogProb){
    preInit(rootSymbol, isScaling, isLogProb);
    init(br, rootSymbol);
    postInit(rootSymbol);
  }
  
  // add root rule
  private void preInit(String rootSymbol, boolean isScaling, boolean isLogProb){
    this.isScaling = isScaling;
    this.isLogProb = isLogProb;
    if(isLogProb){
      operator = new LogProbOperator();
    } else {
      operator = new ProbOperator();
    }
    
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
    
    // convert to log prob
    if(isLogProb){
      
    }
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
    rootEdge = edgeSpace.indexOf(rootRule.toEdge());
    goalEdge = edgeSpace.indexOfTag(rootRule.getMother());
    assert(goalEdge == edgeSpace.indexOf(rootRule.getMotherEdge()));
    
    edgeSpaceSize = edgeSpace.size();
    
    if(verbose>=2){
      System.err.println("postInit Earley Parser -- nonterminals: " + Util.sprint(parserTagIndex, parserNonterminalMap.keySet()));
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
    g = new Grammar(parserWordIndex, parserTagIndex, parserNonterminalMap, operator);
    g.learnGrammar(rules, extendedRules);
    edgeSpace = g.getEdgeSpace();
    
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
    g = new Grammar(parserWordIndex, parserTagIndex, parserNonterminalMap, operator);
    g.learnGrammar(rules, extendedRules);
    edgeSpace = g.getEdgeSpace();
    
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
        Util.outputSentenceResult(sentenceString, outWriter, surprisalList);

        if(!isScaling){
          synOutWriter.write("# " + id + "\n");
          lexOutWriter.write("# " + id + "\n");
          stringOutWriter.write("# " + id + "\n");
          Util.outputSentenceResult(sentenceString, synOutWriter, synSurprisalList);
          Util.outputSentenceResult(sentenceString, lexOutWriter, lexSurprisalList);
          Util.outputSentenceResult(sentenceString, stringOutWriter, stringProbList);
        }
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
        
    /* add "" -> . ROOT */
    //predictFromEdge(0, 0, rootEdge); // predict from "" -> . ROOT
    addToChart(0, 0, rootEdge, operator.one(), operator.one());
    predictAll(0); // start expanding from ROOT
    addToChart(0, 0, rootEdge, operator.one(), operator.one()); // this is a bit of a hack needed because predictAll(0) wipes out the seeded rootActiveEdge chart entry.
    
    if(verbose>=3){
      dumpChart();
    }
    if (verbose>=2){
      Timing.endTime("Done initializing!");
    }
    
    /* parse word by word */
    double lastProbability = 1.0;
    for(int right=1; right<=numWords; right++){ // span [0, rightEdge] covers words 0, ..., rightEdge-1
      int wordId = right-1;
      parseWord(right);
            
      double prefixProbability = getPrefixProb(right);
      if(!isScaling){
        // string probs
        double stringProbability = stringProbability(right);
        stringProbList.add(stringProbability);

        // surprisals
        double prefixProbabilityRatio = prefixProbability / lastProbability;
        assert prefixProbabilityRatio <= 1.0 + 1e-10;
        surprisalList.add(-Math.log(prefixProbabilityRatio));
        
        // syntatic/lexical surprisals
        double synPrefixProbability = getSynPrefixProb(right);
        synSurprisalList.add(-Math.log(synPrefixProbability/lastProbability));
        lexSurprisalList.add(-Math.log(prefixProbability/synPrefixProbability));

        // print info
        if(verbose>=0){
          System.err.println(Util.sprint(parserWordIndex, wordIndices.subList(0, right)));
          String msg = "Prefix probability: " + prefixProbability + "\n" +
          "String probability: " + stringProbability + "\n" +
          "Surprisal: " + surprisalList.get(wordId) + " = -log(" + 
          prefixProbability + "/" + lastProbability + ")\n";
          System.err.println(msg);
        }
        lastProbability = prefixProbability;
      } else {
        // surprisals
        // note: prefix prob is scaled, and equal to P(w0..w_(right-1))/P(w0..w_(right-2))
        surprisalList.add(-Math.log(prefixProbability));
        
        if(verbose>=0){
          System.err.println(Util.sprint(parserWordIndex, wordIndices.subList(0, right)));
          String msg = "Scaled prefix probability: " + prefixProbability + "\n" +
          "Scaling: " + scaling[right] + "\n" + 
          "Surprisal: " + surprisalList.get(wordId) + " = -log(" + prefixProbability + ")\n";
          System.err.println(msg);
        }
      }
    }

    if(verbose>=4){
      dumpInnerChart();
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
      scaling[right] = operator.inverse(prefixProb[right-1]);
            
      // scaling matrix for extended rules
      if(containsExtendedRule){
        for (int i = 0; i < right; i++) {
          scalingMatrix[linear[i][right]] = operator.multiply(scalingMatrix[linear[i][right-1]], scaling[right]);
        }
      }
    }
    
    /** Handle normal rules **/
    Set<IntTaggedWord> iTWs = lex.tagsForWord(word);
    if (verbose>=1){
      System.err.println("# " + right + "\t" + word + ", numTags=" + iTWs.size());
    }
    for (IntTaggedWord itw : iTWs) { // go through each POS tag the current word could have
      int edge = edgeSpace.indexOfTag(itw.tag());
      double score = lex.score(itw); // log
      if(!isLogProb){
        score = Math.exp(score);
      } else {
        assert(score<=0);
      }
      if(verbose>=1){
        System.err.println("Lexical prob: " + parserTagIndex.get(itw.tag()) + "->[_" 
            + parserWordIndex.get(itw.word()) + "] : " + score);
      }
      
      
      if(isScaling){ // scaling
        score = operator.multiply(score, scaling[right]);
      }
      addToChart(right-1, right, edge, operator.one(), score);
    }

    /** Handle extended rules **/
    for (int i = right-2; i >= 0; --i) {
      // find all rules that rewrite into word_i ... word_(rightEdge-1)
      Map<Integer, Double> valueMap = g.getRuleTrie().findAllMap(wordIndices.subList(i, right));
      if(valueMap != null){
        if (verbose>=3){
          System.err.println("AG full: " + words.subList(i, right) + ": " + Util.sprint(valueMap, parserTagIndex));
        }
        for (int iT : valueMap.keySet()) {
          int edge = edgeSpace.indexOfTag(iT);
          double score = valueMap.get(iT);
          
          if(isScaling){ // scaling
            score = operator.multiply(score, scalingMatrix[linear[i][right]]);
          }
          addToChart(i, right, edge, operator.one(), score);
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
  protected abstract void dumpInnerChart();
  
  /**
   * Returns the total probability of complete parses for the string prefix parsed so far.
   */
  public abstract double stringProbability(int right);
  protected abstract void addToChart(int left, int right, int edge, 
      double forward, double inner);
  public abstract String edgeInfo(int left, int right, int edge);
  protected abstract void dumpChart();
  protected abstract void predictAll(int right);
  protected abstract void predictFromEdge(int left, int right, int edge);
  
  /**
   * Consider all triples [left, middle, right] to see if there're any edge pair
   * middle: left X -> _ . Y _ and right: middle Y -> _ and , we'll then create a new edge
   * right: left X -> _ Y . _  
   * @param right
   */
  protected abstract void completeAll(int right);
  protected abstract void complete(int left, int middle, int right, int passive, double inner);
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
    
    // map matrix indices [left][right] into linear indices
    linear = new int[numWords+1][numWords+1];
    // go in the order of CKY parsing
    numCells=0;
    for(int right=0; right<=numWords; right++){
      for(int left=right; left>=0; left--){
        linear[left][right] = numCells++;
      }
    }
    assert(numCells==((numWords+2)*(numWords+1)/2));
    
    prefixProb = new double[numWords + 1];
    synPrefixProb = new double[numWords + 1];
    
    if (isScaling){
      scaling = new double[numWords + 1];
      if(containsExtendedRule){
        scalingMatrix = new double[numCells];
        Util.init(scalingMatrix, operator.one());
      }
    }
    
    // init
    prefixProb[0] = operator.one();
  }
  
  /**
   * Initialization for every word
   */
  protected void wordInitialize(){
    thisPrefixProb = new DoubleList();
    thisSynPrefixProb = new DoubleList();
  }
  
  protected void storePrefixProb(int right) {
    if(verbose>=2){
      System.err.print("Store prefix prob: ");
      for(double value : thisPrefixProb.toArray()){
        System.err.print(operator.getProb(value) + " ");
      }
      System.err.println();
      
      System.err.print("Store syn prefix prob: ");
      for(double value : thisSynPrefixProb.toArray()){
        System.err.print(operator.getProb(value) + " ");
      }
      System.err.println();
    }
    
    if (thisPrefixProb.size() == 0) { // no path
      prefixProb[right] = operator.zero();
      synPrefixProb[right] = operator.zero();
    } else {
      prefixProb[right] = operator.arraySum(thisPrefixProb.toArray());
      
      synPrefixProb[right] = operator.arraySum(thisSynPrefixProb.toArray());
    }
    if(verbose>=1){
      System.err.println("# Prefix prob [0," + right + "] = " 
          + operator.getProb(prefixProb[right]));
    }
  }
  
  
  protected double getPrefixProb(int right){
    return operator.getProb(prefixProb[right]);
  }
  
  protected double getSynPrefixProb(int right){
    return operator.getProb(synPrefixProb[right]);
  }
  
  protected String edgeInfo(int left, int right, int edge, double forward, double inner){
    return right + ": " + left + " " + 
    g.getEdgeSpace().get(edge).toString(parserTagIndex, parserTagIndex) + " [" + 
    df.format(operator.getProb(forward)) + ", " + 
    df.format(operator.getProb(inner)) + "]";
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
