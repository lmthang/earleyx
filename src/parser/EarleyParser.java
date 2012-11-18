package parser;

import edu.stanford.nlp.parser.lexparser.IntTaggedWord;
import edu.stanford.nlp.stats.Counter;
import edu.stanford.nlp.util.DoubleList;
import edu.stanford.nlp.util.HashIndex;
import edu.stanford.nlp.util.Index;
import edu.stanford.nlp.util.Timing;

import java.io.BufferedReader;
import java.io.BufferedWriter;
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

import base.BaseLexicon;
import base.Edge;
import base.ProbRule;
import base.RuleSet;
import base.TagRule;
import base.TerminalRule;

import util.LogProbOperator;
import util.Operator;
import util.ProbOperator;
import util.RuleFile;
import util.TreeBankFile;
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
  protected int insideOutsideOpt = 0; // 1: Earley way, 2: traditional way
  
  protected Operator operator; // either ProbOperator or LogProbOperator
  
  protected Grammar g;
  protected EdgeSpace edgeSpace;
  protected BaseLexicon lex;
  
  protected RuleSet ruleSet;
  protected List<ProbRule> tagRules;
  protected Collection<ProbRule> extendedRules;
  //protected Map<Integer, Map<Integer, Double>> unaryEdgeMap; // unaryEdgeMap.get(childTag Y).get(edge): score of unary edge X -> Y
  protected List<ProbRule> unaryRules;
  
  // root
  protected static final String ORIG_SYMBOL = "";
  protected int origSymbolIndex; // indexed by TAG_INDEX
  protected String rootSymbol; // default: ROOT, could be changed
  protected int rootSymbolIndex;
  protected ProbRule rootRule; // "" -> ROOT
  protected int startEdge; // "" -> . ROOT
  protected int goalEdge; // "" -> [] if isLeftWildcard=true; otherwise, "" -> ROOT .
  
  protected int edgeSpaceSize;   // edge space
  protected int numCategories; // nonterminas + preterminals
  
  /** convert strings into integers **/
  protected Index<String> parserWordIndex;
  protected Index<String> parserTagIndex;
  // keys are nonterminal indices (indexed by parserTagIndex)
  // values are used to retrieve nonterminals (when necessary) in the order that
  //   we processed them when loading treebank or grammar files (more for debug purpose)
  protected Map<Integer, Integer> parserNonterminalMap; // nonterminal + preterminal = parser tag indices
  
  
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
  
  /** inside-outside **/
  // completedEdges.get(linear[left][right]): set of completed edges
  protected Map<Integer, Set<Integer>> completedEdges;
  protected Map<Integer, Double> expectedCounts; // map rule indices (allRules) to expected counts
  
  // another way of computing expected counts
  protected Map<Integer, Map<Integer, Double>> insideChart; // .get(linear[left][right]).get(tag): inside prob
  protected Map<Integer, Map<Integer, Double>> outsideChart; // .get(linear[left][right]).get(tag): outside prob
  
  /** current sentence info **/
  protected int[][] linear; // convert matrix indices [left][right] into linear indices
  protected int numCells; // numCells==((numWords+2)*(numWords+1)/2)
  
  protected List<String> words;
  protected List<Integer> wordIndices;
  protected int numWords = 0;

  protected boolean containsExtendedRule = false;
  public static int verbose = -1;
  protected static DecimalFormat df = new DecimalFormat("0.000000");
  protected static DecimalFormat df1 = new DecimalFormat("0.00");
  protected static DecimalFormat df2 = new DecimalFormat("0.00000000");
  
  public EarleyParser(String grammarFile, int inGrammarType, String rootSymbol, 
      boolean isScaling, 
      boolean isLogProb, int insideOutsideOpt){
    if(inGrammarType==1){ // grammar file
      construct(Util.getBufferedReaderFromFile(grammarFile), rootSymbol, isScaling, 
          isLogProb, insideOutsideOpt);
    } else if(inGrammarType==2){ // treebank file
      preInit(rootSymbol, isScaling, isLogProb, insideOutsideOpt);
      Collection<IntTaggedWord> intTaggedWords = new ArrayList<IntTaggedWord>();
      TreeBankFile.processTreebank(grammarFile, tagRules, intTaggedWords, parserTagIndex, 
          parserWordIndex, parserNonterminalMap);
      
      buildEdgeSpace();
      buildGrammar();
      buildLex(intTaggedWords);
      
      // build all rules
      if(!tagRules.get(0).equals(rootRule) || !ruleSet.get(0).equals(rootRule)){
        System.err.println("! No rootRule either in tagRules or in ruleSet");
        System.exit(1);
      }
      ruleSet.addAll(tagRules.subList(1, tagRules.size()));
      
      Map<Integer, Counter<Integer>> tag2wordsMap = lex.getTag2wordsMap();
      for(int iT : tag2wordsMap.keySet()){
        Counter<Integer> counter = tag2wordsMap.get(iT);
        for(int iW : counter.keySet()){
          ruleSet.add(new ProbRule(new TerminalRule(iT, Arrays.asList(iW)), 
              Math.exp(counter.getCount(iW))));
        }
      }
      
      postInit(rootSymbol);
    } else {
      System.err.println("! Invalid grammarType " + inGrammarType);
      System.exit(1);
    }
  }
  public EarleyParser(BufferedReader br, String rootSymbol, boolean isScaling, 
      boolean isLogProb, int insideOutsideOpt){
    construct(br, rootSymbol, isScaling, isLogProb, insideOutsideOpt);
  }
  
  private void construct(BufferedReader br, String rootSymbol, boolean isScaling, 
      boolean isLogProb, int insideOutsideOpt){
    preInit(rootSymbol, isScaling, isLogProb, insideOutsideOpt);
    init(br, rootSymbol);
    postInit(rootSymbol);
  }
  // preInit
  private void preInit(String rootSymbol, boolean isScaling, boolean isLogProb, 
      int insideOutsideOpt){
    this.isScaling = isScaling;
    this.isLogProb = isLogProb;
    this.insideOutsideOpt = insideOutsideOpt;
    
    if(isLogProb){
      operator = new LogProbOperator();
    } else {
      operator = new ProbOperator();
    }
    
    // index
    parserWordIndex = new HashIndex<String>();
    parserTagIndex = new HashIndex<String>();
    parserNonterminalMap = new HashMap<Integer, Integer>();
    
    // root symbol
    this.rootSymbol = rootSymbol;
    rootRule = new ProbRule(new TagRule(ORIG_SYMBOL, Arrays.asList(rootSymbol), parserTagIndex), 1.0);
    origSymbolIndex = parserTagIndex.indexOf(ORIG_SYMBOL);
    rootSymbolIndex = parserTagIndex.indexOf(rootSymbol);
    parserNonterminalMap.put(origSymbolIndex, parserNonterminalMap.size());
    assert(parserNonterminalMap.get(origSymbolIndex) == 0);
    
    // rules
    ruleSet = new RuleSet(parserTagIndex, parserWordIndex);
    ruleSet.add(rootRule);
    
    tagRules = new ArrayList<ProbRule>();
    tagRules.add(rootRule);
    extendedRules = new ArrayList<ProbRule>();
    
    // inside-outside
    if(insideOutsideOpt == 0){
      // Earley edges having the same parent and expecting children 
      // (children on the right of the dot) are collapsed into the same edge X -> * . \\alpha.
      // This speeds up parsing time if we only care about inner/forward probs + surprisal values
      edgeSpace = new LeftWildcardEdgeSpace(parserTagIndex, parserWordIndex);
    } else {
      edgeSpace = new StandardEdgeSpace(parserTagIndex, parserWordIndex);
      expectedCounts = new HashMap<Integer, Double>();
    }
  }
  
  // init
  private void init(BufferedReader br, String rootSymbol){    
    Map<Integer, Counter<Integer>> tag2wordsMap = new HashMap<Integer, Counter<Integer>>();
    Map<Integer, Set<IntTaggedWord>> word2tagsMap = new HashMap<Integer, Set<IntTaggedWord>>();
        
    try {
      RuleFile.parseRuleFile(br, 
          ruleSet, tagRules, extendedRules, tag2wordsMap, word2tagsMap, parserNonterminalMap, parserWordIndex, parserTagIndex);
    } catch (IOException e) {
      System.err.println("! Problem initializing Earley parser");
      e.printStackTrace();
    }
    
    // convert to log prob
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
    
    buildEdgeSpace();
    buildGrammar();
    buildLex(tag2wordsMap, word2tagsMap);
    if(extendedRules.size()>0){
      containsExtendedRule = true;
      if(verbose>=0){
        System.err.println("# Num extended rules = " + extendedRules.size());
      }
    }
  }
  
  // post init
  private void postInit(String rootSymbol){
    // root
    startEdge = edgeSpace.indexOf(rootRule.getEdge());
    goalEdge = edgeSpace.to(startEdge);
    
    edgeSpaceSize = edgeSpace.size();
    numCategories = parserTagIndex.size();
    
    // build unary rules
    int numUnaryRules = 0;
    unaryRules = new ArrayList<ProbRule>();
    for(ProbRule rule : tagRules){
      if(rule.getChildren().size()==1){ // unary
        unaryRules.add(rule);
        numUnaryRules++;
      }
    }
    
    if(verbose>=1){
      System.err.println("# postInit Earley Parser -- num unary rules = " + numUnaryRules + 
          " num nonterminals " + parserNonterminalMap.size());
      if(verbose>=2){
        System.err.println(Util.sprint(parserTagIndex, parserNonterminalMap.keySet()));
      }
    }
    
    
  }
  
  private void buildEdgeSpace(){
    /* set up edge space. */
    if (verbose>=1){
      System.err.print("\n### Building edgespace ... ");
    }
    
    edgeSpace.build(tagRules);    
  }
  
  private void buildGrammar(){
    /* learn grammar */
    if (verbose>=1){
      System.err.println("\n### Learning grammar ... ");
    }
    g = new Grammar(parserWordIndex, parserTagIndex, parserNonterminalMap, operator);
    g.learnGrammar(tagRules, extendedRules, edgeSpace);
  }
  
  /**
   * Construct grammar and lexicon from rules and int tagged words
   * 
   * @param originalRules
   * @param intTaggedWords
   * @param rootRule
   * @return
   */
  private void buildLex(Collection<IntTaggedWord> intTaggedWords){
    /* learn lexicon */
    if (verbose>=1){
      System.err.println("\n### Learning lexicon ... ");
    }
    lex = new SmoothLexicon(parserWordIndex, parserTagIndex);
    lex.train(intTaggedWords);
  }
  
  public void buildLex(Map<Integer, Counter<Integer>> tag2wordsMap,
      Map<Integer, Set<IntTaggedWord>> word2tagsMap) {
    /* create lexicon */
    if (verbose>=1){
      System.err.println("\n### Learning lexicon ... ");
    }
    lex = new SmoothLexicon(parserWordIndex, parserTagIndex);
    lex.setTag2wordsMap(tag2wordsMap);
    lex.setWord2tagsMap(word2tagsMap);
  }
  
  public void parseSentences(List<String> sentences){
    List<String> indices = new ArrayList<String>();
    for (int i = 0; i < sentences.size(); i++) {
      indices.add(i + "");
    }
    try {
      parseSentences(sentences, indices, "");
    } catch (IOException e) {
      e.printStackTrace();
    }
  }
  
  /**
   * Parse a list of pre-tokenized sentences, one per line, from a Reader.
   */
  public void parseSentences(List<String> sentences, List<String> indices, String outPrefix) throws IOException {
    assert(sentences.size() == indices.size());
    BufferedWriter outWriter = outPrefix.equals("") ? null : new BufferedWriter(new FileWriter(outPrefix + ".srprsl"));
    BufferedWriter synOutWriter = outPrefix.equals("") ? null : new BufferedWriter(new FileWriter(outPrefix + ".SynSp"));
    BufferedWriter lexOutWriter = outPrefix.equals("") ? null : new BufferedWriter(new FileWriter(outPrefix + ".LexSp"));
    BufferedWriter stringOutWriter = outPrefix.equals("") ? null : new BufferedWriter(new FileWriter(outPrefix + ".string"));
    
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
    addToChart(0, 0, startEdge, operator.one(), operator.one());
    predictAll(0); // start expanding from ROOT
    addToChart(0, 0, startEdge, operator.one(), operator.one()); // this is a bit of a hack needed because predictAll(0) wipes out the seeded rootActiveEdge chart entry.
    addInnerScore(0, numWords, startEdge, operator.one()); // set inside score 1.0 for "" -> . ROOT
    
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
      dumpChart();
      System.err.println(dumpInnerProb());
      System.err.println(dumpInsideChart());
    }
    
    if(insideOutsideOpt>0){
      double rootInsideScore = getInnerScore(0, numWords, goalEdge);
      if(verbose>=0){
        System.err.println("Root prob: " + operator.getProb(rootInsideScore));
      }
      computeOutsideProbs(rootInsideScore);
      
      if(verbose>=4){
        System.err.println(dumpOuterProb());
        System.err.println(dumpOutsideChart());
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

  // print expected rule count to string
  public String sprintExpectedCounts(){
    StringBuffer sb = new StringBuffer("# Expected counts\n");

    for (int ruleId = 1; ruleId < ruleSet.size(); ruleId++) {
      ProbRule rule = ruleSet.get(ruleId);
      double expectedCount = 0.0;
      if(expectedCounts.containsKey(ruleId)){
        expectedCount = operator.getProb(expectedCounts.get(ruleId));
        sb.append(df.format(expectedCount) + " " + rule.getRule().toString(parserTagIndex, parserWordIndex) +"\n");
      }
    }
    
    return sb.toString();
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
    
    /** Scaling factors **/
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
      // score
      double score = lex.score(itw); // log
      if(!isLogProb){
        score = Math.exp(score);
      } else {
        assert(score<=0);
      }
      
      // scaling
      if(isScaling){ 
        score = operator.multiply(score, scaling[right]);
      }
      
      // scan
      scanning(right-1, right, itw.tag(), score);
    }

    /** Handle extended rules **/
    for (int i = right-2; i >= 0; --i) {
      // find all rules that rewrite into word_i ... word_(right-1)
      Map<Integer, Double> valueMap = g.getRuleTrie().findAllMap(wordIndices.subList(i, right));
      if(valueMap != null){
        if (verbose>=3){
          System.err.println("AG full: " + words.subList(i, right) + ": " + Util.sprint(valueMap, parserTagIndex));
        }
        for (int iT : valueMap.keySet()) {
          // score
          double score = valueMap.get(iT);
          if(isScaling){ // scaling
            score = operator.multiply(score, scalingMatrix[linear[i][right]]);
          }
          
          // scanning
          scanning(i, right, iT, score);                      
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

  /**
   * Scan (right: left tag -> word_left ... word_(right-1) .)
   * 
   * @param left
   * @param right
   * @param edge
   * @param inner
   */
  protected void scanning(int left, int right, int tag, double inner){
    if(verbose>=1){
      System.err.println("# Scanning [" + left + ", " + right + "]: "
          + parserTagIndex.get(tag) + "->" + 
          words.subList(left, right) + " : " + operator.getProb(inner));
    }
    
    int edge = edgeSpace.indexOfTag(tag);
    
    if(insideOutsideOpt>0){
      if(this instanceof EarleyParserDense){
        System.err.println("! currently inside outside option only works with EarleyParserSparse");
        System.exit(1);
      }
      
      Edge terminalEdge = new Edge(new TerminalRule(tag, wordIndices.subList(left, right)), right-left);
      edge = edgeSpace.addEdge(terminalEdge);
      
      // Note for EarleyParseDense: the edge value will be larger than statesSpaceSize
      completedEdges.get(linear[left][right]).add(edge);
     
      if(verbose>=3){
        System.err.println("# Add completed edge for outside computation [" + left + ", " + right + "] " 
            + edgeSpace.get(edge).toString(parserTagIndex, parserWordIndex));
      }
      
      // add terminal rule
      ruleSet.add(new ProbRule(terminalEdge.getRule(), operator.getProb(inner)));
      
//      Rule rule = terminalEdge.getRule();
//      if(!ruleSet.contains(rule)){
//        System.err.println("Rule " + (new ProbRule(rule, operator.getProb(inner))).toString(parserTagIndex, parserWordIndex));
//      }
      
      // inside-outside
      if(insideOutsideOpt==2){
        addToInsideChart(left, right, tag, inner);
      }
    } else {
      assert(edgeSpace.get(edge).numChildren()==0);
    }
    
    
    addToChart(left, right, edge, operator.zero(), inner);
    
    
  }
   
  /**
   * Consider all triples [left, middle, right] to see if there're any edge pair
   * middle: left X -> _ . Y _ and right: middle Y -> _ and , we'll then create a new edge
   * right: left X -> _ Y . _  
   * @param right
   */
  protected void completeAll(int right) {
    // Note: for loop orders here matter
    // basically, we start out completing small chunks and gradually form larger chunks to complete
    for (int left = right - 1; left >= 0; left--) {      
      for (int middle = right - 1; middle >= left; middle--) {
        completeAll(left, middle, right);
      } // end middle
    } // end left
    
    storePrefixProb(right);
    if(verbose>=3){
      dumpChart();
    }
  }
  
  protected void complete(int left, int middle, int right, int edge, double inner) {
    int tag = edgeSpace.get(edge).getMother();
    assert(edgeSpace.get(edge).numRemainingChildren()==0);
    
    // we already completed the edge, right: middle Y -> _ ., where passive represents for Y
    Completion[] completions = g.getCompletions(tag);
    
    if (verbose>=3 && completions.length>0){
      System.err.println(completionInfo(left, middle, right, edge, inner, completions));
    }
    
    for (int x = 0, n = completions.length; x < n; x++) { // go through all completions we could finish
      Completion completion = completions[x];
      
      if (containsEdge(left, middle, completion.activeEdge)) { // middle: left X -> _ . Y _
        double updateScore = operator.multiply(completion.score, inner);
        double newForwardProb = operator.multiply(
            getForwardScore(left, middle, completion.activeEdge), updateScore);
        double newInnerProb = operator.multiply(
            getInnerScore(left, middle, completion.activeEdge), updateScore);
        
        
        // add edge, right: left X -> _ Y . _, to tmp storage
        initTmpScores(completion.completedEdge);
        addTmpForwardScore(completion.completedEdge, newForwardProb);
        addTmpInnerScore(completion.completedEdge, newInnerProb);
    
        // info to help outside computation later
        if(insideOutsideOpt>0){
          Edge edgeObj = edgeSpace.get(completion.completedEdge);
          
          if(edgeObj.numRemainingChildren()==0){ // complete right: left X -> _ Y .
            completedEdges.get(linear[left][right]).add(completion.completedEdge);
      
            if(insideOutsideOpt==2){
              addToInsideChart(left, right, edgeObj.getMother(), newInnerProb);
            }
            
            if(verbose>=3){
              System.err.println("# Add completed edge for outside computation " + edgeInfo(left, right, completion.completedEdge));
            }
          }
        }
        
        if (verbose >= 3) {
          System.err.println("  start " + edgeScoreInfo(left, middle, completion.activeEdge) 
              + " -> new " + edgeScoreInfo(left, right, completion.completedEdge, newForwardProb, newInnerProb));

          if (isGoalEdge(completion.completedEdge)) {
            System.err.println("# String prob +=" + Math.exp(newInnerProb));
          }
        }

        //also a careful addition to the prefix probabilities -- is this right?
        if (middle == right - 1) {
          thisPrefixProb.add(newForwardProb);
          double synProb = operator.divide(newForwardProb, inner);
          thisSynPrefixProb.add(synProb); // minus the lexical score
          if (verbose >= 2) {
            System.err.println("# Prefix prob += " + operator.getProb(newForwardProb) + "=" + 
                operator.getProb(getForwardScore(left, middle, completion.activeEdge)) + "*" + 
                operator.getProb(completion.score) + "*" + operator.getProb(inner) + "\t" + left + "\t" + middle + "\t" + completion.activeEdge);
            System.err.println("# Syn prefix prob += " + operator.getProb(synProb) + "=" + 
                operator.getProb(newForwardProb) + "/" + 
                operator.getProb(inner));
          }
        }
      }
    }
  }
  
  public void computeOutsideProbs(double rootInsideScore){
    if(verbose>=4){
      System.err.println("# Completed edges:");
      for (int left = 0; left <= numWords; left++) {
        for (int right = left; right <= numWords; right++) {
          int lrIndex = linear[left][right];
          
          for(int edge : completedEdges.get(lrIndex)){
            System.err.println(edgeInfo(left, right, edge));
          }
        }
      }
    }
    
    if(verbose>=1){
      System.err.println("# Computing outside probabilities ...");
      Timing.startTime();
    }
  
    // assign prob 1.0 to "" -> ROOT .
    addOuterScore(0, numWords, goalEdge, operator.one());
      
    // start recursive outside computation
    outside(0, numWords, goalEdge, rootInsideScore, verbose); //4);
  
    if (insideOutsideOpt==1){ // standard way
      computeOutsideChart();
//      computeExpectedCounts(rootInsideScore);
    }
    
    if(verbose>=1){
      Timing.endTime("# Done computing outside probabilities !");
    }
  }
  
//  protected void computeExpectedCounts(double rootInsideScore){
//    for (int ruleId = 1; ruleId < allRules.size(); ruleId++) {
//      ProbRule probRule = allRules.get(ruleId);
//      double expectedCount = 0.0;
//      if(expectedCounts.containsKey(ruleId)){
//        expectedCount = operator.getProb(expectedCounts.get(ruleId)); 
//      }
//    }
//  }
  
  protected void outside(int start, int end, int rootEdge, double rootInsideScore, int verbose){
    assert(start<end);

    // configurations.get(linear[left][right]): set of edges to compute outside
    Map<Integer, Set<Integer>> configurations = new HashMap<Integer, Set<Integer>>();
    
    // init
    for(int length=end-start; length>=0; length--){
      for (int left=0; left<=end-length; left++){
        int right = left+length;
        
        configurations.put(linear[left][right], new HashSet<Integer>());
      }
    }
    configurations.get(linear[start][end]).add(rootEdge); // add starting edge
 
    // outside
    for(int length=end-start; length>0; length--){
      if(verbose>=0){
        System.err.println("# Outside length " + length + "");
      }
      for (int left=0; left<=end-length; left++){ // left
        int right = left+length; // right

        // accumulate outside probabilities
        int lrIndex = linear[left][right];
        Set<Integer> edges = configurations.get(lrIndex);
        
        /** process edges X -> Z . \alpha **/
        /** new edges spanning [left, right] will be added **/
        Integer[] copyEdges = edges.toArray(new Integer[0]);
        List<Integer> removeEdges = new ArrayList<Integer>();
        for(int edge : copyEdges){ // use copyEdges cause outside(left, middle, right, ...) will update edges
          Edge edgeObj = edgeSpace.get(edge);
          assert((edge == goalEdge || edgeObj.numChildren()>1) && edgeObj.getDot()>0);
          if(edgeObj.getDot() > 1){
            continue;
          }
          
          // X -> Z . \alpha
          double parentOutside = getOuterScore(left, right, edge);
          int prevTag = edgeObj.getChild(edgeObj.getDot()-1); // Z
          Edge prevEdgeObj = new Edge(edgeObj.getRule(), edgeObj.getDot()-1); // X -> . Z \alpha
          assert(prevEdgeObj.getDot()==0);
          int prevEdge = edgeSpace.indexOf(prevEdgeObj);
          
          // [left, left, right]
          outside(left, left, right, edge, prevTag, prevEdge, prevEdgeObj, 
              parentOutside, rootInsideScore, configurations);
          
          removeEdges.add(edge);
        }
        edges.removeAll(removeEdges);
        
        /** process edges X -> \beta Z . \alpha where \beta is not empty **/
        /** no new edge will be added to edges **/
        copyEdges = edges.toArray(new Integer[0]);
        
        for(int edge : copyEdges){
          double parentOutside = getOuterScore(left, right, edge);
          Edge edgeObj = edgeSpace.get(edge);
          
          if(edgeObj.getDot()>0){// X -> \beta Z . \alpha // && !edgeObj.isTerminalEdge()){ 
            assert(edge == goalEdge || edgeObj.numChildren()>1);
            
            if(verbose>=3){
              System.err.println("## " + outsideInfo(left, right, edge));
            }
            
            int prevTag = edgeObj.getChild(edgeObj.getDot()-1); // Z
            Edge prevEdgeObj = new Edge(edgeObj.getRule(), edgeObj.getDot()-1); // X -> \beta . Z \alpha
            int prevEdge = edgeSpace.indexOf(prevEdgeObj);
            if(verbose>=4){
              System.err.println("  prev edge " + prevEdgeObj.toString(parserTagIndex, parserWordIndex));
            }
            
            // recursively split into strictly smaller chunks
            for(int middle=right-1; middle>left; middle--){ // middle
              if(containsEdge(left, middle, prevEdge)){
                outside(left, middle, right, edge, prevTag, prevEdge, prevEdgeObj, parentOutside, rootInsideScore, configurations);
              }
            } // end for middle
          }
        } // end for edge
        
        assert((new HashSet<Integer>(Arrays.asList(copyEdges))).equals(edges)); // no modification to edges
      } // start
    } // length  
  }
  
  protected void outside(int left, int middle, int right, 
      int edge, int prevTag, int prevEdge, Edge prevEdgeObj,
      double parentOutside, double rootInsideScore, Map<Integer, Set<Integer>> configurations){
    assert(middle!=left || prevEdgeObj.getDot()==0); // if middle==left, prev edge should be X -> . Z \alpha
    
    double leftInside = getInnerScore(left, middle, prevEdge);
    if(verbose>=4){
      System.err.println("  left inside [" + left + ", " + middle + "] " 
          + operator.getProb(leftInside));
    }
    
    int mrIndex = linear[middle][right];
    for(int nextEdge : completedEdges.get(mrIndex)){ // Y -> v .
      Edge nextEdgeObj = edgeSpace.get(nextEdge);
      int nextTag = nextEdgeObj.getMother(); // Y
      double unaryClosureScore = g.getUnaryClosures().get(prevTag, nextTag);
      
      if(unaryClosureScore > operator.zero()) { // positive R(Z -> Y)
        double rightInside = getInnerScore(middle, right, nextEdge);
        
        if(verbose>=4) {
          System.err.println("    next edge [" + middle + ", " + right + "] " + 
              nextEdgeObj.toString(parserTagIndex, parserWordIndex) + 
              ", right inside " + operator.getProb(rightInside) + 
              ", unary(" + parserTagIndex.get(prevTag) + "->" 
              + parserTagIndex.get(nextTag) + ")=" + operator.getProb(unaryClosureScore));
        }
        
        // left outside = parent outside * right inside
        // Note: we multiply unaryClosure score here even though Stolcke's paper suggests that we should not
        // but adding this closure score results in scores match with Mark's code
        outsideUpdate(left, middle, prevEdge, 
            operator.multiply(operator.multiply(parentOutside, rightInside), unaryClosureScore), 
            rootInsideScore, verbose);
        
        // right outside = parent outside * left inside * unary score
        outsideUpdate(middle, right, nextEdge, 
            operator.multiply(operator.multiply(parentOutside, leftInside), unaryClosureScore), 
            rootInsideScore, verbose);
        
        // to backtrack we might want to check if completedEdge has any children
        // if it has no, that means it was constructed directly from terminals
        
        // recursive call
        if(middle>left){ // add middle: left X -> \beta . Z \alpha only if left < middle
          assert(!prevEdgeObj.isTerminalEdge());
          configurations.get(linear[left][middle]).add(prevEdge);
        }
        
        assert(middle != left || nextEdge != edge);
        if(!nextEdgeObj.isTerminalEdge()){ // add right: middle Y -> v . if Y -> v. is not a terminal edge
          configurations.get(mrIndex).add(nextEdge);
        }
        
        // update unary counts
        updateUnaryCount(prevTag, nextTag, parentOutside, leftInside, rightInside, rootInsideScore, verbose);
      }
    } // end nextEdge
  }
  protected void outsideUpdate(int left, int right, int edge, double outsideScore, double rootInsideScore, int verbose){
    addOuterScore(left, right, edge, outsideScore);
    if(verbose>=3){
      System.err.println("      after adding " + operator.getProb(outsideScore) + 
          ", " + outsideInfo(left, right, edge));
    }
    
    // add expected counts
    if(insideOutsideOpt==1){
      Edge edgeObj = edgeSpace.get(edge);
      if(left==right || edgeObj.isTerminalEdge()){ // predicted edges: X -> . \alpha or tag -> terminals .
      //if(edgeObj.numRemainingChildren()==0){
        double insideScore = getInnerScore(left, right, edge);
        double expectedCount = operator.divide(operator.multiply(outsideScore, insideScore), rootInsideScore);
        
        addScore(expectedCounts, ruleSet.indexOf(edgeObj.getRule()), expectedCount);
        if(verbose>=3){
          System.err.format("count %s += %e = %e * %e / %e => %e\n", 
              edgeObj.getRule().markString(parserTagIndex, parserWordIndex), 
              operator.getProb(expectedCount), operator.getProb(outsideScore), 
              operator.getProb(insideScore), operator.getProb(rootInsideScore),
              expectedCounts.get(ruleSet.indexOf(edgeObj.getRule())));
        }
      }
    }
  }
  
  /** update expected counts for unary rules **/
  protected void updateUnaryCount(int prevTag, int nextTag, 
      double parentOutside, double leftInside, double rightInside, double rootInsideScore, int verbose){
    // prevTag: Z
    // nextTag: Y
    for(ProbRule probRule : unaryRules){ // unary rule Z' -> Y'
      int unaryMotherTag = probRule.getMother(); // Z'
      int unaryChildTag = probRule.getChild(0); // Y'
      double prevClosureScore = g.getUnaryClosures().get(prevTag, unaryMotherTag); // R(Z=>Z')
      double nextClosureScore = g.getUnaryClosures().get(unaryChildTag, nextTag); // R(Y'=>Y)
      if(prevClosureScore > operator.zero() && nextClosureScore > operator.zero()) { // positive closure scores
        // outside Z ->  Z'. 
        double unaryOutside = operator.multiply(operator.multiply(parentOutside, leftInside), prevClosureScore);
        
        // inside Y' -> Y .
        double unaryInside = operator.multiply(rightInside, nextClosureScore);
        
        double expectedCount = operator.divide(operator.multiply(operator.multiply(unaryOutside, unaryInside), 
            operator.getScore(probRule.getProb())), rootInsideScore);
        
        addScore(expectedCounts, ruleSet.indexOf(probRule.getRule()), expectedCount);
        if(verbose>=3){
          System.err.format("unary count %s += %e = %e * %e * %e / %e\n", 
              probRule.getRule().markString(parserTagIndex, parserWordIndex), 
              operator.getProb(expectedCount), operator.getProb(unaryOutside), 
              operator.getProb(unaryInside), probRule.getProb(), operator.getProb(rootInsideScore));
        }
      }
    }
  }
  
    /**********************/
  /** Abstract methods **/
  /**********************/
  protected abstract void addToChart(int left, int right, int edge, 
      double forward, double inner);
  protected abstract void predictAll(int right);
  protected abstract void predictFromEdge(int left, int right, int edge);
  
  protected abstract void completeAll(int left, int middle, int right);
  protected abstract void addPrefixProbExtendedRule(int left, int middle, int right, int edge, double inner);

  /**
   * if there exists an edge spanning [left, right]
   * 
   * @param left
   * @param right
   * @param edge
   * @return
   */
  protected abstract boolean containsEdge(int left, int right, int edge);
  
  // tmp probabilities
  protected abstract void initTmpScores(int edge);
  protected abstract void addTmpForwardScore(int edge, double score);
  protected abstract void addTmpInnerScore(int edge, double score);
  
  // forward probabilities
  protected abstract double getForwardScore(int left, int right, int edge);
  protected abstract void addForwardScore(int left, int right, int edge, double score);
  
  
  // inner probabilities
  protected abstract double getInnerScore(int left, int right, int edge);
  protected abstract void addInnerScore(int left, int right, int edge, double score);
  
  // outer probabilities
  protected abstract double getOuterScore(int left, int right, int edge);
  protected abstract void addOuterScore(int left, int right, int edge, double score);
  protected abstract Map<Integer, Map<Integer,Double>> computeOutsideChart();
  
  // inside-outside
//  protected abstract void addInsideChart(int left, int right, int tag, double score);
//  protected abstract void addOutsideChart(int left, int right, int tag, double score);
  protected abstract void standardOutside(int start, int end, int rootEdge, double rootInsideScore);
  
  // debug
  public abstract String edgeScoreInfo(int left, int right, int edge);
  protected abstract void dumpChart(); // print both inner and forward probs
  public abstract String dumpInnerProb();
  public abstract String dumpOuterProb();
  public abstract String dumpInsideChart();
  public abstract String dumpOutsideChart();
  
  /*******************/
  /** Other methods **/
  /*******************/
  protected void addToInsideChart(int left, int right, int tag, double insideScore) {
    addScore(insideChart.get(linear[left][right]), tag, insideScore);
    
    if(verbose>=3){
      System.err.println("# Add inside score [" + left + ", " + right + ", " + 
          parserTagIndex.get(tag) + "] " + operator.getProb(insideScore));
    }
    
  }

  protected void addToOutsideChart(int left, int right, int tag, double outsideScore) {
    addScore(outsideChart.get(linear[left][right]), tag, outsideScore);
    
    if(verbose>=3){
      System.err.println("# Add outside score [" + left + ", " + right + ", " + 
          parserTagIndex.get(tag) + "] " + operator.getProb(outsideScore));
    }
    
  }
  
  /**
   * print chart for debugging purpose
   *   isOnlyCategory: only print categories (completed edges) but not partial edges
   * 
   * @param chart
   * @param isOnlyCategory
   */
  protected String dumpCatChart(Map<Integer, Map<Integer, Double>> chart, String name){
    StringBuffer sb = new StringBuffer("# " + name + " chart snapshot\n");
    for(int length=1; length<=numWords; length++){ // length
      for (int left = 0; left <= numWords-length; left++) {
        int right = left+length;

        // scaling
        double scalingFactor = 0;
        if(isScaling){
          for(int i=left+1; i<=right; i++){
            scalingFactor += scaling[i];
          }
        }
        
        int lrIndex = linear[left][right];
        Map<Integer, Double> tagMap = chart.get(lrIndex);
        if(tagMap.size()>0){
          sb.append("cell " + left + "-" + right + "\n");
        }
        
        // print by tag            
        for(Map.Entry<Integer, Double> entry : tagMap.entrySet()){
          int tag = entry.getKey();
          sb.append(" " + parserTagIndex.get(tag) 
              + ": " + df2.format(operator.getProb(tagMap.get(tag))) + "\n");
        }
      }
    }  
    
    return sb.toString();
  }
  
  protected void addScore(Map<Integer, Double> probMap, int key, double score){
    if(!probMap.containsKey(key)){
      probMap.put(key, score);
    } else {
      probMap.put(key, operator.add(probMap.get(key), score));
    }
  }
  
  
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
    
    // init prefix prob
    prefixProb[0] = operator.one();
    
    // inside outside
    if(insideOutsideOpt>0){
      completedEdges = new HashMap<Integer, Set<Integer>>();
      
      for (int i = 0; i < numCells; i++) {
        completedEdges.put(i, new HashSet<Integer>());
      }
      
      if(insideOutsideOpt==2){ // standard way
        // inside-outside chart
        insideChart = new HashMap<Integer, Map<Integer,Double>>();
        
        for (int i = 0; i < numCells; i++) {
          insideChart.put(i, new HashMap<Integer, Double>());
        }
        outsideChart = null; // will initialize after computing outside probs
      }
    } 
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

  /**
   * Returns the total probability of complete parses for the string prefix parsed so far.
   */
  public double stringProbability(int right) {    
    return operator.getProb(getInnerScore(0, right, goalEdge));
  }
  
  protected boolean isGoalEdge(int edge){
    Edge edgeObj = edgeSpace.get(edge);
    return edgeObj.numRemainingChildren()==0 && edgeObj.getMother() == origSymbolIndex;
  }
  
  protected String edgeInfo(int left, int right, int edge){
    return "(" + right + ": " + left + " " + 
    edgeSpace.get(edge).toString(parserTagIndex, parserWordIndex) + ")";
  }
  protected String edgeScoreInfo(int left, int right, int edge, double forward, double inner){
    return edgeInfo(left, right, edge) + " [" + 
    df.format(operator.getProb(forward)) + ", " + 
    df.format(operator.getProb(inner)) + "]";
  }
  
  protected String outsideInfo(int left, int right, int edge){
    return "outside (" + edgeInfo(left, right, edge) + ", " + 
        operator.getProb(getOuterScore(left, right, edge)) + ")";
  }
  
  protected String completionInfo(int left, int middle, int right, 
      int edge, double inner, Completion[] completions){
    return "Completed " + edgeInfo(middle, right, edge)  
    + ", inside=" + df.format(operator.getProb(inner))  
    + " -> completions: " + Util.sprint(completions, edgeSpace, parserTagIndex, parserWordIndex, operator);
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
  public Collection<ProbRule> getRules() {
    return tagRules;
  }
  public Index<String> getParserWordIndex() {
    return parserWordIndex;
  }
  public Index<String> getParserTagIndex() {
    return parserTagIndex;
  }
  public ProbRule getRootRule() {
    return rootRule;
  }
  
  public void insideOutside(List<String> sentences){
    verbose = -1;
    int numIterations = 5;
    for (int i = 0; i < numIterations; i++) {
      System.err.println("# Iteration " + i);
      parseSentences(sentences);
      System.err.println(sprintExpectedCounts());
      
      
      // update rule probs
      Map<Integer, List<Integer>> tag2ruleIndices = ruleSet.getTag2ruleIndices();
      for(int tag : tag2ruleIndices.keySet()){
        List<Integer> ruleIndices = tag2ruleIndices.get(tag);
        double sum = 0.0;
        for(int ruleId : ruleIndices){
          sum += expectedCounts.get(ruleId);
        }
        
        if(sum>0.0){
          for(int ruleId : ruleIndices){
            if(ruleSet.getProb(ruleId)>0.0){
              ruleSet.setProb(ruleId, ruleSet.getProb(ruleId)/sum);
              System.err.println(ruleSet.get(ruleId).toString(parserTagIndex, parserWordIndex));
            }
          }
        } else {
          System.err.println("# Tag " + parserTagIndex.get(tag) + " has no positive rules");
        }
      }
      // reinit model params
      
      expectedCounts = new HashMap<Integer, Double>();
    }
    
    
  }
}

/** Unused code **/
//if(unaryEdgeMap.containsKey(nextTag)){
//  for(int unaryEdge : unaryEdgeMap.get(nextTag).keySet()) { // Y'--> Y, go through Y' for which the unary rule Y' -> Y exists
//    Edge unaryEdgeObj = edgeSpace.get(unaryEdge);
//    int unaryMotherTag = unaryEdgeObj.getMother(); // Y'
//    double unaryClosureScore = g.getUnaryClosures().get(prevTag, unaryMotherTag);
//    if(unaryClosureScore > operator.zero()) { // positive R(Z -> Y')
//      // outside Y' -> Y . 
//      double unaryOutside = operator.multiply(operator.multiply(parentOutside, leftInside), unaryClosureScore);
//      
//      // outside Y' -> . Y
//      unaryOutside = operator.multiply(unaryOutside, rightInside);
//      
//      double unaryInside =  unaryEdgeMap.get(nextTag).get(unaryEdge); // prob(Y' ->Y) or inside Y' -> . Y
//      double expectedCount = operator.divide(operator.multiply(unaryOutside, unaryInside), rootInsideScore);
//      
//      addScore(expectedCounts, ruleMap.get(unaryEdgeObj.getRule()), expectedCount);
//      if(verbose>=-1){
//        System.err.format("unary count %s += %e = %e * %e / %e\n", 
//            unaryEdgeObj.getRule().markString(parserTagIndex, parserWordIndex), 
//            operator.getProb(expectedCount), operator.getProb(unaryOutside),
//      }
//    }
//  }
//}

//build unaryEdgeMap
//unaryEdgeMap = new HashMap<Integer, Map<Integer,Double>>();
//int numUnaryRules = 0;
//for(ProbRule rule : tagRules){
//  if(rule.getChildren().size()==1){ // unary
//    int tag = rule.getChild(0); // rule.getMother();
//    if(!unaryEdgeMap.containsKey(tag)){
//      unaryEdgeMap.put(tag, new HashMap<Integer, Double>());
//    }
//    int edge = edgeSpace.indexOf(rule.getEdge());
//    assert(!unaryEdgeMap.get(tag).containsKey(edge));
//    unaryEdgeMap.get(tag).put(edge, operator.getScore(rule.getProb()));
//    numUnaryRules++;
//  }
//}

//if (insideOutsideOpt==1){// Earley way
//  
//} else if (insideOutsideOpt==2) { // standard ways
//  standardOutside(0, numWords, goalEdge, rootInsideScore);
//} else {
//  System.err.println("! Unknown inside-outside option " + insideOutsideOpt);
//  System.exit(1);
//}

//protected abstract void addToIOChart(int left, int right, int tag,  double inner);
//if(isLeftWildcard) { // goal edge: "" -> []
//goalEdge = edgeSpace.indexOfTag(rootRule.getMother());
//} else { // 
//goalEdge = edgeSpace.indexOf(new Edge(rootRule.getRule(), 1));
//}

//if(isLeftWildcard){
//addToChart(right-1, right, edge, operator.one(), score);
//} else {
//addToIOChart(right-1, right, itw.tag(), score);
//}

//if(isLeftWildcard){
//  int edge = edgeSpace.indexOfTag(iT);
//  addToChart(i, right, edge, operator.one(), score);            
//} else {
//  addToIOChart(i, right, iT, score);
//}


//protected void terminalComplete(int middle, int right, int iT, double inner){
//for (int left = middle; left >= 0; left--) {
//  int edge = edgeSpace.indexOfTag(iT);
//  // bug: we haven't initialized theseChart... structures
//  complete(left, middle, right, edge, inner); // in completion the forward prob of Y -> _ . is ignored
//}
//}

//// scanning
//if (isNewWay) {
////  terminalComplete(right-1, right, itw.tag(), score);
//  
//  terminalCompletions.get(linear[right-1][right]).put(itw.tag(), score);
//  if(verbose>=2){
//    edgeInfo(right-1, right, edge, operator.one(), score);
//  }
//} else {
//  addToChart(right-1, right, edge, operator.one(), score);
//}

//
//if (isNewWay) {
////  terminalComplete(i, right, iT, score);
//  terminalCompletions.get(linear[i][right]).put(iT, score);
//  if(verbose>=2){
//    edgeInfo(i, right, edge, operator.one(), score);
//  }
//} else {

//}

//if(isLeftWildcard){
//} else {
//Edge edgeObj = new Edge(new Rule(itw.tag(), Arrays.asList(itw.word())), 1); // X -> _w .    
//edge = edgeSpace.addEdge(edgeObj);
//}      

//if(isLeftWildcard){
//} else {
//Edge edgeObj = new Edge(new Rule(iT, wordIndices.subList(i, right)), 1); // X -> _w1 ... _wk .    
//edge = edgeSpace.addEdge(edgeObj);
//}      

//"Syntactic Prefix probability: " + synPrefixProbability + "\n" +
//"Prefix probability ratio for word " + wordId + " " + words.get(wordId) + ": " + prefixProbabilityRatio + "\n" +
//+ 
//"synSurprisal: " + synSurprisalList.get(wordId) +"\n" +
//"lexSurprisal: " + lexSurprisalList.get(wordId)
//
