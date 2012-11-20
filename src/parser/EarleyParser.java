package parser;

import edu.stanford.nlp.ling.Label;
import edu.stanford.nlp.ling.Tag;
import edu.stanford.nlp.ling.Word;
import edu.stanford.nlp.parser.lexparser.IntTaggedWord;
import edu.stanford.nlp.stats.Counter;
import edu.stanford.nlp.trees.LabeledScoredTreeNode;
import edu.stanford.nlp.trees.SimpleTree;
import edu.stanford.nlp.trees.Tree;
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

import base.BackTrack;
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
  protected int decodeOpt = 0; // 1: Viterbi (Label Tree), 2: Label Recall

  protected Operator operator; // either ProbOperator or LogProbOperator
  
  protected Grammar g;
  protected EdgeSpace edgeSpace;
  protected BaseLexicon lex;
  
  protected RuleSet ruleSet;
  
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
  //protected double[] scaling; 
  protected double[] scalingMatrix; // used for extended rules, scalingMatrix[left][right] = prod scaling[left+1] ... scaling[right]
  
  /** inside-outside **/
  // completedEdges.get(linear[left][right]): set of completed edges
  protected Map<Integer, Set<Integer>> completedEdges;
  protected Map<Integer, Double> expectedCounts; // map rule indices (allRules) to expected counts
  
  // another way of computing expected counts
  protected Map<Integer, Map<Integer, Double>> insideChart; // .get(linear[left][right]).get(tag): inside prob
  protected Map<Integer, Map<Integer, Double>> outsideChart; // .get(linear[left][right]).get(tag): outside prob

  /** Decode options **/
  // backtrack info for computing Viterbi parse
  // .get(linear[left][right]).get(edge): back track info
  // edge: X -> \alpha Y . \beta
  // backtrack info is right: middle Y -> v . that leads
  // to X -> \alpha Y . \beta with maximum inner probabilities
  protected Map<Integer, Map<Integer, BackTrack>> backtrackChart;
  
  // .get(linear[left][right]).get(tag)
  // expected counts for tag X spaning over [left, right]
  protected Map<Integer, Map<Integer, Double>> sentExpectedCount;
  
  /** current sentence info **/
  protected int[][] linear; // convert matrix indices [left][right] into linear indices
  protected int numCells; // numCells==((numWords+2)*(numWords+1)/2)
  
  protected List<String> words;
  protected List<Integer> wordIndices;
  protected int numWords = 0;

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
      TreeBankFile.processTreebank(grammarFile, ruleSet, intTaggedWords, parserTagIndex, 
          parserWordIndex, parserNonterminalMap);
      
      buildEdgeSpace();
      buildGrammar();
      buildLex(intTaggedWords);
      
      // add terminal rules      
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
          ruleSet, tag2wordsMap, word2tagsMap, parserNonterminalMap, parserWordIndex, parserTagIndex);
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
  }
  
  // post init
  private void postInit(String rootSymbol){
    // root
    startEdge = edgeSpace.indexOf(rootRule.getEdge());
    goalEdge = edgeSpace.to(startEdge);
    
    edgeSpaceSize = edgeSpace.size();
    numCategories = parserTagIndex.size();
    
    if(verbose>=1){
      System.err.println("# postInit Earley Parser -- num nonterminals " + parserNonterminalMap.size());
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
    
    edgeSpace.build(ruleSet.getTagRules());    
  }
  
  private void buildGrammar(){
    /* learn grammar */
    if (verbose>=1){
      System.err.println("\n### Learning grammar ... ");
    }
    g = new Grammar(parserWordIndex, parserTagIndex, parserNonterminalMap, operator);
    g.learnGrammar(ruleSet, edgeSpace);
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
  
  public List<Double> parseSentences(List<String> sentences){
    List<String> indices = new ArrayList<String>();
    for (int i = 0; i < sentences.size(); i++) {
      indices.add(i + "");
    }
    
    List<Double> sentLogProbs = null;
    try {
      sentLogProbs = parseSentences(sentences, indices, "");
    } catch (IOException e) {
      e.printStackTrace();
    }
    
    return sentLogProbs;
  }
  
  /**
   * Parse a list of pre-tokenized sentences, one per line, from a Reader.
   */
  public List<Double> parseSentences(List<String> sentences, List<String> indices, 
      String outPrefix) throws IOException {
    assert(sentences.size() == indices.size());
    BufferedWriter outWriter = outPrefix.equals("") ? null : new BufferedWriter(new FileWriter(outPrefix + ".srprsl"));
    BufferedWriter synOutWriter = outPrefix.equals("") ? null : new BufferedWriter(new FileWriter(outPrefix + ".SynSp"));
    BufferedWriter lexOutWriter = outPrefix.equals("") ? null : new BufferedWriter(new FileWriter(outPrefix + ".LexSp"));
    BufferedWriter stringOutWriter = outPrefix.equals("") ? null : new BufferedWriter(new FileWriter(outPrefix + ".string"));
    
    List<Double> sentLogProbs = new ArrayList<Double>();
    for (int i = 0; i < sentences.size(); i++) {
      String sentenceString = sentences.get(i);
      String id = indices.get(i);
      
      if(verbose>=0){
        System.err.println("\n### Sent " + i + ": id=" + id + ", "+ sentenceString);
      
        // start
        Timing.startTime();
      }
      
      List<List<Double>> resultLists = parseSentence(sentenceString);
      assert(resultLists.size() == 4);
      List<Double> surprisalList = resultLists.get(0);
      List<Double> synSurprisalList = resultLists.get(1);
      List<Double> lexSurprisalList = resultLists.get(2);
      List<Double> stringLogProbList = resultLists.get(3);
      List<Double> stringProbList = new ArrayList<Double>();
      for(double logProb : stringLogProbList){
        stringProbList.add(Math.exp(logProb));
      }
      sentLogProbs.add(stringLogProbability(numWords));
      
      // end
      if(verbose>=0){
        Timing.tick("NegLogProb=" + stringLogProbability(numWords) + ". finished parsing sentence. ");
      }
      
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
    
    return sentLogProbs;
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
    List<Double> stringLogProbList = new ArrayList<Double>();
    
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

      // string probs
      double stringLogProbability = stringLogProbability(right);
      stringLogProbList.add(stringLogProbability);
      
      StringBuffer sb = new StringBuffer(); 
      if(verbose>=0){
        sb.append(Util.sprint(parserWordIndex, wordIndices.subList(0, right)) + "\n"
            + "String logprob: " + stringLogProbability + "\n");
      }
      
      if(!isScaling){
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
          sb.append("Prefix probability: " + prefixProbability + "\n" +
          "Surprisal: " + surprisalList.get(wordId) + " = -log(" + 
          prefixProbability + "/" + lastProbability + ")\n");
        }
        lastProbability = prefixProbability;
      } else {
        // surprisals
        // note: prefix prob is scaled, and equal to P(w0..w_(right-1))/P(w0..w_(right-2))
        surprisalList.add(-Math.log(prefixProbability));
        
        if(verbose>=0){
          sb.append("Scaled prefix probability: " + prefixProbability + "\n" +
          "Scaling: " + scalingMatrix[linear[right-1][right]] + "\n" + 
          "Surprisal: " + surprisalList.get(wordId) + " = -log(" + prefixProbability + ")\n");
        }
      }
      
      if(verbose>=0){
        System.err.println(sb.toString());
      }
    }

    if(verbose>=4){
      dumpChart();
      System.err.println(dumpInnerProb());
      System.err.println(dumpInsideChart());
    }
    
    if(insideOutsideOpt>0){
      double rootInnerScore = getInnerScore(0, numWords, goalEdge);
      if(verbose>=0){
        System.err.println("Root prob: " + operator.getProb(rootInnerScore));
      }
      computeOutsideProbs(rootInnerScore);
      
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
    resultLists.add(stringLogProbList);
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
      scalingMatrix[linear[right-1][right]] = operator.inverse(prefixProb[right-1]);
            
//      System.err.println("# Scaling [" + (right-1) + "][" + right + "] " + 
//          operator.getProb(scalingMatrix[linear[right-1][right]]));
      // scaling matrix for extended rules
      for (int i = 0; i < (right-1); i++) {
        scalingMatrix[linear[i][right]] = operator.multiply(scalingMatrix[linear[i][right-1]], 
            scalingMatrix[linear[right-1][right]]);
//        System.err.println("# Scaling [" + (i) + "][" + right + "] " + 
//            operator.getProb(scalingMatrix[linear[i][right]]));
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
      assert(score<=0);
      if(!isLogProb){
        score = Math.exp(score);
      }
       
      // scan
      scanning(right-1, right, itw.tag(), score);
    }

    /** Handle extended rules **/
    if(ruleSet.hasMultiTerminalRule()){
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
            
            // scanning
            scanning(i, right, iT, score);                      
          }
          
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
    
    // scaling
    if(isScaling){ 
      inner = operator.multiply(inner, scalingMatrix[linear[left][right]]);
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
        int completedEdge = completion.completedEdge;
        
        // add edge, right: left X -> _ Y . _, to tmp storage
        initTmpScores(completedEdge);
        addTmpForwardScore(completedEdge, newForwardProb);
        addTmpInnerScore(completedEdge, newInnerProb);
    
        // inside-outside info to help outside computation later
        if(insideOutsideOpt>0){
          Edge edgeObj = edgeSpace.get(completedEdge);
          
          if(edgeObj.numRemainingChildren()==0){ // complete right: left X -> _ Y .
            completedEdges.get(linear[left][right]).add(completedEdge);
      
            if(insideOutsideOpt==2){
              addToInsideChart(left, right, edgeObj.getMother(), newInnerProb);
            }
            
            if(verbose>=3){
              System.err.println("# Add completed edge for outside computation " + edgeInfo(left, right, completedEdge));
            }
          }
        }
        
        // Viterbi: store backtrack info
        if(decodeOpt==1){
          Map<Integer, BackTrack> backtrackCell = backtrackChart.get(linear[left][right]);
          if(!backtrackCell.containsKey(completedEdge)){ // no backtrack
            // store Y -> v .
            backtrackCell.put(completedEdge, new BackTrack(edge, middle, newInnerProb));
          } else {
            BackTrack backtrack = backtrackCell.get(completedEdge);
            if(backtrack.parentInnerScore < newInnerProb){ // update backtrack info
              backtrackCell.put(completedEdge, new BackTrack(edge, middle, newInnerProb));
            }
          }
        }
        
        if (verbose >= 3) {
          System.err.println("  start " + edgeScoreInfo(left, middle, completion.activeEdge) 
              + " -> new " + edgeScoreInfo(left, right, completedEdge, newForwardProb, newInnerProb));

          if (isGoalEdge(completedEdge)) {
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
  
  public void computeOutsideProbs(double rootInnerScore){
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
    outside(0, numWords, goalEdge, rootInnerScore, verbose); //4);
  
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
  
  public Tree viterbiParse(){
    return viterbiParse(0, numWords, goalEdge);
  }

  public Tree viterbiParse(int left, int right, int edge){
    if(verbose>=0){
      System.err.println("# Viterbi parse " + edgeInfo(left, right, edge));
    }

    // X -> \alpha . \beta
    Edge edgeObj = edgeSpace.get(edge);
    Label motherLabel = new Tag(parserTagIndex.get(edgeObj.getMother()));
    
    Tree returnTree = null;
    if(edgeObj.getDot()==0){
      returnTree = new LabeledScoredTreeNode(motherLabel);
    } else if(edgeObj.isTerminalEdge()){ // X -> _w1 ... _wn
      List<Tree> daughterTreesList = new ArrayList<Tree>();
      for (int i = left; i < right; i++) {
        daughterTreesList.add(new LabeledScoredTreeNode(new Word(words.get(i))));
      }
      
      returnTree = new SimpleTree(motherLabel, daughterTreesList);
    } else { // X -> \alpha Y . \beta
      assert(edge==goalEdge || edgeObj.numChildren()>1);
      BackTrack backtrack = backtrackChart.get(linear[left][right]).get(edge);
      
      // Viterbi parse: X -> \alpha . Y \beta
      Edge prevEdgeObj = edgeObj.getPrevEdge(); 
      int prevEdge = edgeSpace.indexOf(prevEdgeObj);
      returnTree = viterbiParse(left, backtrack.middle, prevEdge);
      
      // Viterbi parse: Y -> v .
      int nextEdge = backtrack.edge;
      Tree nextTree = viterbiParse(backtrack.middle, right, nextEdge);
      
      // adjoin trees
      returnTree.addChild(nextTree);
    }
    
    return returnTree;
  }
  
  
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
          Edge prevEdgeObj = edgeObj.getPrevEdge(); // X -> . Z \alpha
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
            Edge prevEdgeObj = edgeObj.getPrevEdge(); // X -> \beta . Z \alpha
            int prevEdge = edgeSpace.indexOf(prevEdgeObj);
            if(verbose>=4){
              System.err.println("  prev edge " + prevEdgeObj.toString(parserTagIndex, parserWordIndex));
            }
            
            // recursively split into strictly smaller chunks
            for(int middle=right-1; middle>left; middle--){ // middle
              if(containsEdge(left, middle, prevEdge) && parentOutside>operator.zero()){
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
        double leftOutsideScore = operator.multiply(operator.multiply(parentOutside, rightInside), unaryClosureScore);
        if(leftOutsideScore>operator.zero()){
          outsideUpdate(left, middle, prevEdge, leftOutsideScore, rootInsideScore, verbose);
        }
        
        // right outside = parent outside * left inside * unary score
        double rightOutsideScore = operator.multiply(operator.multiply(parentOutside, leftInside), unaryClosureScore);
        if(rightOutsideScore>operator.zero()){
          outsideUpdate(middle, right, nextEdge, rightOutsideScore, rootInsideScore, verbose);
        }
        
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
        if(leftInside>operator.zero() && rightInside>operator.zero()){
          updateUnaryCount(prevTag, nextTag, parentOutside, leftInside, rightInside, rootInsideScore, verbose);
        }
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
        if(insideScore > operator.zero()){
          double expectedCount = operator.divide(operator.multiply(outsideScore, insideScore), rootInsideScore);
          assert(expectedCount>operator.zero());
          
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
  }
  
  /** update expected counts for unary rules **/
  protected void updateUnaryCount(int prevTag, int nextTag, 
      double parentOutside, double leftInside, double rightInside, double rootInsideScore, int verbose){
    // prevTag: Z
    // nextTag: Y
    for(ProbRule probRule : ruleSet.getUnaryRules()){ // unary rule Z' -> Y'
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
        assert(expectedCount>operator.zero());
        
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
  protected abstract int chartCount(int left, int right);
  protected abstract Set<Integer> listEdges(int left, int right);
  
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
        double scalingFactor = operator.one();
        if (isScaling){
          scalingFactor = scalingMatrix[linear[left][right]];
        }
        
        int lrIndex = linear[left][right];
        Map<Integer, Double> tagMap = chart.get(lrIndex);
        if(tagMap.size()>0){
          sb.append("cell " + left + "-" + right + "\n");
        }
        
        // print by tag            
        for(Map.Entry<Integer, Double> entry : tagMap.entrySet()){
          int tag = entry.getKey();
          
          double score = operator.divide(tagMap.get(tag), scalingFactor);
          sb.append(" " + parserTagIndex.get(tag) 
              + ": " + df2.format(operator.getProb(score)) + "\n");
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
      scalingMatrix = new double[numCells];
      Util.init(scalingMatrix, operator.one());
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
    
    // Decode
    if(decodeOpt==1){ // Viterbi parse  
      backtrackChart = new HashMap<Integer, Map<Integer,BackTrack>>();
      for (int i = 0; i < numCells; i++) {
        backtrackChart.put(i, new HashMap<Integer, BackTrack>());
      }
    } else if(decodeOpt==2){ // Label Recall parse
      sentExpectedCount = new HashMap<Integer, Map<Integer,Double>>();
      for (int i = 0; i < numCells; i++) {
        sentExpectedCount.put(i, new HashMap<Integer, Double>());
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
   * Returns log of the total probability of complete parses for the string prefix parsed so far.
   */
  public double stringLogProbability(int right) {
    double logProb = operator.getLogProb(getInnerScore(0, right, goalEdge));
    if(isScaling){
      logProb -= operator.getLogProb(scalingMatrix[linear[0][right]]);
    }
    
    return logProb;
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
  
  public Index<String> getParserWordIndex() {
    return parserWordIndex;
  }
  public Index<String> getParserTagIndex() {
    return parserTagIndex;
  }
  public ProbRule getRootRule() {
    return rootRule;
  }
  public List<ProbRule> getAllRules(){
    return ruleSet.getAllRules();
  }
  
  public void setDecodeOpt(int decodeOpt) {
    this.decodeOpt = decodeOpt;
  }
  
  public List<Double> insideOutside(List<String> sentences){
    int minIteration = 1;
    int maxIteration = 0; // 0: run until convergence
    double stopTol = 1e-7;
    double minRuleProb = 1e-20;
    
    List<Double> sumNegLogProbList = new ArrayList<Double>();
    int numIterations = 0;
    int prevNumRules = 0;
    double prevSumNegLogProb = Double.POSITIVE_INFINITY;
    while(true){
      numIterations++;
      if(verbose>=0){
        System.err.println(ruleSet.toString(parserTagIndex, parserWordIndex));
      }
      
      // sumLogProbs
      List<Double> sentLogProbs = parseSentences(sentences);
      double sumNegLogProb = 0.0;
      for (Double sentLogProb : sentLogProbs) {
        sumNegLogProb -= sentLogProb;
      }
      sumNegLogProbList.add(sumNegLogProb);
      
      // update rule probs
      int numRules = updateRuleset(minRuleProb);

      if(verbose>=0){
        System.err.println("# iteration " + numIterations + ", numRules=" + numRules 
            + ", sumNegLogProb = " + sumNegLogProb);
      }


      /** update model params **/
      buildGrammar();
      Map<Integer, Counter<Integer>> tag2wordsMap = lex.getTag2wordsMap();
      
      // reset lex
      for(int tag : tag2wordsMap.keySet()){ // tag
        Counter<Integer> counter = tag2wordsMap.get(tag);
        
        for(int word : counter.keySet()){
          counter.setCount(word, Double.NEGATIVE_INFINITY); // zero
        }
      }
      
      // update lex
      for(ProbRule probRule : ruleSet.getTerminalRules()){
        tag2wordsMap.get(probRule.getMother()).setCount(probRule.getChild(0), Math.log(probRule.getProb()));
      }
      
      // convergence test
      if(numIterations>=minIteration && numRules==prevNumRules){
        if(maxIteration>0 && numIterations>=maxIteration){ // exceed max iterations
          if(verbose>=0){
            System.err.println("# Exceed number of iterations " + maxIteration + ", stop");
          }
          
          break;
        } else if(sumNegLogProb==0){
          if(verbose>=0){
            System.err.println("# Reach minimum sumNegLogProb = 0.0, stop");
          }
          break;
        } else {
          double relativeChange = (prevSumNegLogProb-sumNegLogProb)/Math.abs(sumNegLogProb);
          if (relativeChange<stopTol){ // change is too small
            if(verbose>=0){
              System.err.println("# Relative change " + relativeChange + " < " + stopTol + ", stop");
            }
            break;
          }
        }
      }
      
      // reset
      expectedCounts = new HashMap<Integer, Double>();
      prevNumRules = numRules;
      prevSumNegLogProb = sumNegLogProb;
    }
    
    return sumNegLogProbList;
  }
  
  public int updateRuleset(double minRuleProb){ 
    if(verbose>=3){
      System.err.println("\n# Update rule probs");
    }
    
    // compute sums per tag
    Map<Integer, Double> tagSums = new HashMap<Integer, Double>();
    for (int ruleId : expectedCounts.keySet()) {
      int tag = ruleSet.getMother(ruleId);
      
      
      if(!tagSums.containsKey(tag)){
        tagSums.put(tag, operator.zero());
      }
      
      tagSums.put(tag, operator.add(tagSums.get(tag), expectedCounts.get(ruleId)));
    }
    
    // normalized probs
    int numRules = 0;
    for (int ruleId = 0; ruleId < ruleSet.size(); ruleId++) {
      double newProb = 0.0;
      
      if(expectedCounts.containsKey(ruleId)){
        assert(operator.getProb(expectedCounts.get(ruleId))>0);
        int tag = ruleSet.getMother(ruleId);
        newProb = operator.getProb(operator.divide(expectedCounts.get(ruleId), tagSums.get(tag)));        

        if(newProb<minRuleProb){ // filter
          newProb = 0.0; 
        } else {
          numRules++;
          if(verbose>=3){
            System.err.println(newProb + "\t" + ruleSet.get(ruleId).getRule().toString(parserTagIndex, parserWordIndex));
          }
        }
      }
      
      ruleSet.setProb(ruleId, newProb);
    }
    
    return numRules;
  }
  
//  public void labelRecalParsing(){
//    for (int length = 2; length <= numWords; length++) {
//      for (int left = 0; left <= (numWords-length); left++) {
//        int right = left + length;
//        
//        // scaling
//        double scalingFactor = operator.one();
////        if (isScaling){
////          if (isOutsideProb){ // outside prob has a scaling factor for [0,left][right, numWords]
////            scalingFactor = operator.multiply(scalingMatrix[linear[0][left]], scalingMatrix[linear[right][numWords]]);
////          } else {
////            scalingFactor = scalingMatrix[linear[left][right]];
////          }
////        }
//
//        
//        double maxG = Double.NEGATIVE_INFINITY;
//        for (int tag = 0; tag < parserTagIndex.size(); tag++) {
//          
//        }
//        
//      }
//    }
//  }
  
  /** debug methods **/
  protected void dumpChart() {
    System.err.println("# Chart snapshot, edge space size = " + edgeSpaceSize);
    for(int length=1; length<=numWords; length++){ // length
      for (int left = 0; left <= numWords-length; left++) {
        int right = left+length;

        int count = chartCount(left, right);
        if(count>0){ // there're active states
          System.err.println("[" + left + "," + right + "]: " + count  
              + " (" + df1.format(count*100.0/edgeSpaceSize) + "%)");
          for (int edge : listEdges(left, right)) {
            double forwardProb = getForwardScore(left, right, edge);
            double innerProb = getInnerScore(left, right, edge);
            
            if(isScaling){
              forwardProb = operator.divide(forwardProb, scalingMatrix[linear[0][right]]);
              innerProb = operator.divide(innerProb, scalingMatrix[linear[left][right]]);
            }
            
            System.err.println("  " + edgeSpace.get(edge).toString(parserTagIndex, parserWordIndex) 
                + ": " + df.format(operator.getProb(forwardProb)) 
                + " " + df.format(operator.getProb(innerProb)));
          }
        }
      }
    }
  }
}

/** Unused code **/
//Map<Integer, List<Integer>> tag2ruleIndices = ruleSet.getTag2ruleIndices();
//
//for(int tag : tag2ruleIndices.keySet()){ // go through each tag
//  List<Integer> ruleIndices = tag2ruleIndices.get(tag);
//  
//  // compute sum
//  double sum = operator.zero();
//  for(int ruleId : ruleIndices){
//    if(expectedCounts.containsKey(ruleId)){
//      sum = operator.add(sum, expectedCounts.get(ruleId));
//    }
//  }
//  
//  // normalize
//  for(int ruleId : ruleIndices){
//    double newProb = operator.zero();
//    if(expectedCounts.containsKey(ruleId)){
//      assert(operator.getProb(expectedCounts.get(ruleId))>0);
//      newProb = operator.getProb(operator.divide(expectedCounts.get(ruleId), sum));
//      
//      if(verbose>=0){
//        System.err.println(ruleSet.get(ruleId).toString(parserTagIndex, parserWordIndex));
//      }
//    }
//    ruleSet.setProb(ruleId, newProb);
//  }
//}

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
