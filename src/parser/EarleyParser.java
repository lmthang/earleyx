package parser;

import edu.stanford.nlp.ling.HasWord;
import edu.stanford.nlp.ling.Label;
import edu.stanford.nlp.ling.Tag;
import edu.stanford.nlp.ling.Word;
import edu.stanford.nlp.math.SloppyMath;
import edu.stanford.nlp.parser.Parser;
import edu.stanford.nlp.parser.lexparser.IntTaggedWord;
import edu.stanford.nlp.stats.Counter;
import edu.stanford.nlp.trees.LabeledScoredTreeNode;
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
import java.util.Map.Entry;

import cc.mallet.types.Dirichlet;

import base.BackTrack;
import base.BaseLexicon;
import base.Edge;
import base.ProbRule;
import base.Rule;
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

public abstract class EarleyParser implements Parser { 
  public static final String SURPRISAL_OBJ = "surprisal";
  public static final String STRINGPROB_OBJ = "stringprob";
  public static final String VITERBI_OBJ = "viterbi";
  public static final String SOCIALMARGINAL_OBJ = "socialmarginal";
  
  protected boolean isScaling = true;
  protected boolean isLogProb = false;
  protected int insideOutsideOpt = 0; // 1: EM, 2: VB
  protected int decodeOpt = 0; // 1: Viterbi (Label Tree), 2: Label Recall
  protected boolean isFastComplete = false; 
  protected Operator operator; // either ProbOperator or LogProbOperator
  
  protected Grammar g;
  protected EdgeSpace edgeSpace;
  protected BaseLexicon lex;
  
  protected RuleSet ruleSet;
  protected boolean hasMultiTerminalRule = false;
  
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
  protected Map<Integer, Double> scalingMap; // used for extended rules, scalingMatrix[left][right] = prod scaling[left+1] ... scaling[right]
  
  /** completion info **/
  // completedEdges.get(linear(left, right)): set of completed edges spanning [left, right]
  protected Map<Integer, Set<Integer>> completedEdges;
  // completeInfo.get(right).get(edge): set of left positions of completed edges that span [left, right]
  // edge is X -> \alpha . \beta where \beta is non empty
  protected Map<Integer, Map<Integer, Set<Integer>>> activeEdgeInfo;
  
  /** inside-outside **/

  protected Map<Integer, Double> expectedCounts; // map rule indices (allRules) to expected counts

  /** Decode options **/
  // backtrack info for computing Viterbi parse
  // .get(linear(left, right)).get(edge): back track info
  // edge: X -> \alpha Y . \beta
  // backtrack info is right: middle Y -> v . that leads
  // to X -> \alpha Y . \beta with maximum inner probabilities
  protected Map<Integer, Map<Integer, BackTrack>> backtrackChart;
  
  /** current sentence info **/
  //  protected int[][] linear; // convert matrix indices [left][right] into linear indices
  // protected int numCells; // numCells==((numWords+2)*(numWords+1)/2)
  
  protected List<? extends HasWord> words;
  protected List<Integer> wordIndices; // indices from parserWordIndex
  protected int numWords = 0;

  /** results **/
  private Set<String> objectives;
  protected List<Double> surprisalList;
  protected List<Double> synSurprisalList;
  protected List<Double> lexSurprisalList;
  protected List<Double> stringLogProbList;
  
  public static int verbose = -1;
  protected static DecimalFormat df = new DecimalFormat("0.000000");
  protected static DecimalFormat df1 = new DecimalFormat("0.00");
  protected static DecimalFormat df2 = new DecimalFormat("0.00000000");
  
  public EarleyParser(String grammarFile, int inGrammarType, String rootSymbol, 
      boolean isScaling, 
      boolean isLogProb, int insideOutsideOpt, String objString){
    if(inGrammarType==1){ // grammar file
      construct(Util.getBufferedReaderFromFile(grammarFile), rootSymbol, isScaling, 
          isLogProb, insideOutsideOpt, objString);
    } else if(inGrammarType==2){ // treebank file
      preInit(rootSymbol, isScaling, isLogProb, insideOutsideOpt, objString);
      
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
      boolean isLogProb, int insideOutsideOpt, String objString){
    construct(br, rootSymbol, isScaling, isLogProb, insideOutsideOpt, objString);
  }
  
  private void construct(BufferedReader br, String rootSymbol, boolean isScaling, 
      boolean isLogProb, int insideOutsideOpt, String objString){
    preInit(rootSymbol, isScaling, isLogProb, insideOutsideOpt, objString);
    init(br, rootSymbol);
    postInit(rootSymbol);
  }
  // preInit
  private void preInit(String rootSymbol, boolean isScaling, boolean isLogProb, 
      int insideOutsideOpt, String objString){
    this.isScaling = isScaling;
    this.isLogProb = isLogProb;
    this.insideOutsideOpt = insideOutsideOpt;
    setObjectives(objString); 
    
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
    if(insideOutsideOpt>0){
      expectedCounts = new HashMap<Integer, Double>();
    }
    
    // edgespace
    if(insideOutsideOpt > 0 || decodeOpt > 0){
      edgeSpace = new StandardEdgeSpace(parserTagIndex, parserWordIndex);
    } else {
      // edgespace
      // Earley edges having the same parent and expecting children 
      // (children on the right of the dot) are collapsed into the same edge X -> * . \\alpha.
      // This speeds up parsing time if we only care about inner/forward probs + surprisal values
      edgeSpace = new LeftWildcardEdgeSpace(parserTagIndex, parserWordIndex);
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
    if(verbose>=3){
      System.err.println("# Start edge " + edgeSpace.get(startEdge).toString(parserTagIndex, parserWordIndex));
      System.err.println("# Goal edge " + edgeSpace.get(goalEdge).toString(parserTagIndex, parserWordIndex));
    }
    
    edgeSpaceSize = edgeSpace.size();
    numCategories = parserTagIndex.size();
    
    hasMultiTerminalRule = (ruleSet.getMultiTerminalRules().size()>0);
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
    return parseSentences(sentences, "");
  }
  
  public List<Double> parseSentences(List<String> sentences, String outPrefix){
    List<String> indices = new ArrayList<String>();
    for (int i = 0; i < sentences.size(); i++) {
      indices.add(i + "");
    }
    
    List<Double> sentLogProbs = null;
    try {
      sentLogProbs = parseSentences(sentences, indices, outPrefix);
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
    BufferedWriter surprisalWriter = null;
    BufferedWriter stringprobWriter = null;
    BufferedWriter viterbiWriter = null;
    BufferedWriter socialMarginalWriter = null;
    if(!outPrefix.equals("")) {
    	if (objectives.contains(SURPRISAL_OBJ))
    		surprisalWriter = new BufferedWriter(new FileWriter(outPrefix + "." + SURPRISAL_OBJ));
    	if (objectives.contains(STRINGPROB_OBJ)) 
        	stringprobWriter = new BufferedWriter(new FileWriter(outPrefix + "." + STRINGPROB_OBJ));
    	if (objectives.contains(VITERBI_OBJ)) 
    		viterbiWriter = new BufferedWriter(new FileWriter(outPrefix + "." + VITERBI_OBJ));
    	if (objectives.contains(SOCIALMARGINAL_OBJ)) 
        socialMarginalWriter = new BufferedWriter(new FileWriter(outPrefix + "." + SOCIALMARGINAL_OBJ));
    }
    
    List<Double> sentLogProbs = new ArrayList<Double>();
    for (int i = 0; i < sentences.size(); i++) {
      String sentenceString = sentences.get(i);
      String id = indices.get(i);

      if(verbose>=0){
        System.err.println("\n### Sent " + i + ": id=" + id + ", numWords=" + sentenceString.split("\\s+").length);
      }
      
      // parse sentence
      parseSentence(sentenceString);
      sentLogProbs.add(stringLogProbability(numWords));

      // output
      if(surprisalWriter != null){ // surprisal
        surprisalWriter.write("# " + id + "\n");
        Util.outputSentenceResult(sentenceString, surprisalWriter, surprisalList);
      }
      if(stringprobWriter != null){ // string prob
        stringprobWriter.write("# " + id + "\n");
        Util.outputSentenceResult(sentenceString, stringprobWriter, getStringProbList());
      }
      
      if(viterbiWriter != null){ // viterbi
        Tree viterbiParse = viterbiParse();
        viterbiWriter.write(viterbiParse.toString() + "\n");
      }
      
      if(socialMarginalWriter != null){ // social marginal, need to compute expected counts
        expectedCounts = new HashMap<Integer, Double>();
        computeOutsideProbs();
          
        List<String> treeStrs = socialMarginalDecoding();
        for(String treeStr : treeStrs){
          socialMarginalWriter.write(treeStr + "\n");
        }
      }
      
      
    }
    
    // close
    if(surprisalWriter != null){ // surprisal
      surprisalWriter.close();
    }
    if(stringprobWriter != null){ // stringprob
      stringprobWriter.close();
    }
    if(viterbiWriter != null){ // viterbi
      viterbiWriter.close();
    }
    if(socialMarginalWriter != null){ // social marginal
      socialMarginalWriter.close();
    }
    
    return sentLogProbs;
  }
  
  /**
   * Parse a single sentence
   * @param sentenceString
   * 
   * @throws IOException
   */
  public boolean parseSentence(String sentenceString){
    List<Word> words = new ArrayList<Word>();
    for(String token : Arrays.asList(sentenceString.split("\\s+"))){
      words.add(new Word(token));
    }
    return parse(words);
  }

  public boolean parse(List<? extends HasWord> words) {
    // start
    if(verbose>=0){
      if (words.size()<100){
        System.err.println("## Parsing: " + words);
      }
      Timing.startTime();
    }
    this.words = words;
    
    // init
    sentInit();
    
    /* add "" -> . ROOT */
    addToChart(0, 0, startEdge, operator.one(), operator.one());
    chartPredict(0); // start expanding from ROOT
    addToChart(0, 0, startEdge, operator.one(), operator.one()); // this is a bit of a hack needed because chartPredict(0) wipes out the seeded rootActiveEdge chart entry.
    addInnerScore(0, numWords, startEdge, operator.one()); // set inside score 1.0 for "" -> . ROOT
    addActiveEdgeInfo(0, 0, startEdge);
    
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
      if(verbose>=1){
        sb.append(Util.sprint(parserWordIndex, wordIndices.subList(0, right)) + "\n"
            + "String logprob: " + stringLogProbability + "\n");
      }
      
      // surprisals
      if(objectives.contains(SURPRISAL_OBJ)){
        if(!isScaling){
          double prefixProbabilityRatio = prefixProbability / lastProbability;
          assert prefixProbabilityRatio <= 1.0 + 1e-10;
          surprisalList.add(-Math.log(prefixProbabilityRatio));
          
          // syntatic/lexical surprisals
          double synPrefixProbability = getSynPrefixProb(right);
          synSurprisalList.add(-Math.log(synPrefixProbability/lastProbability));
          lexSurprisalList.add(-Math.log(prefixProbability/synPrefixProbability));
  
          if(verbose>=0){
            sb.append("Prefix probability: " + prefixProbability + "\n" +
            "Surprisal: " + surprisalList.get(wordId) + " = -log(" + 
            prefixProbability + "/" + lastProbability + ")\n");
          }
          lastProbability = prefixProbability;
        } else {
          // note: prefix prob is scaled, and equal to P(w0..w_(right-1))/P(w0..w_(right-2))
          surprisalList.add(-Math.log(prefixProbability));
          
          if(verbose>=0){
            sb.append("Scaled prefix probability: " + prefixProbability + "\n" +
            "Scaling: " + getScaling(right-1, right) + "\n" + 
            "Surprisal: " + surprisalList.get(wordId) + " = -log(" + prefixProbability + ")\n");
          }
        }
      }
      
      if(verbose>=1){
        System.err.println(sb.toString());
      }
      if(verbose>=0 && right%100==0){
        System.err.print(" (" + right + ") ");
      }
    }

    if(verbose>=4){
      dumpChart();
      System.err.println(dumpInnerProb());
      System.err.println(dumpInsideChart());
    }
    
    // inside-outside    
    double rootInnerScore = getInnerScore(0, numWords, goalEdge);
    if(insideOutsideOpt>0 && rootInnerScore>operator.zero()){ 
      computeOutsideProbs();
      
      if(verbose>=4){
        System.err.println(dumpOuterProb());
        System.err.println(dumpOutsideChart());
      }
    }
    
    // end
    if(verbose>=0){
      Timing.tick("NegLogProb=" + -stringLogProbability(numWords) + ". finished parsing sentence. ");
    }
    if (-stringLogProbability(numWords) == Double.NaN){
      System.err.print("! Stop since NegLogProb=NaN");
      System.exit(1);
    }
    return (rootInnerScore>operator.zero());
  }

  public void computeOutsideProbs(){
    double rootInnerScore = getInnerScore(0, numWords, goalEdge);
    assert(rootInnerScore>operator.zero());
    
    System.err.println("# EarleyParser: computeOutsideProbs");
    initOuterProbs();
    computeOutsideProbs(rootInnerScore);
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
  
  public String sprintUnaryChains(){
    return ruleSet.sprintUnaryChains();
  }
  
  public double getScaling(int left, int right){
    if(left==right){
      return operator.one();
    }
    
    assert(right>left);
    int lrIndex = linear(left, right);
    if(!scalingMap.containsKey(lrIndex)){
      double scale = operator.one();
      for (int i = left; i < right; i++) {
        scale = operator.multiply(scale, scalingMap.get(linear(i, i+1)));
      }
      scalingMap.put(lrIndex, scale);
    }
    return scalingMap.get(lrIndex);
  }
  
  public double getScaling(int left, int right, String type){
    if (type.equalsIgnoreCase("outside")){ // outside prob has a scaling factor for [0,left][right, numWords]
      return operator.multiply(getScaling(0, left), getScaling(right, numWords));
    } else {
      return getScaling(left, right);
    }
  }
  /**
   * Read in the next word and build the corresponding chart entries
   * word_(rightEdge-1) corresponds to the span [rightEdge-1, rightEdge]
   * 
   * @param right
   */
  public void parseWord(int right) { // 
    //initializeTemporaryProbLists();
    String word = words.get(right-1).word();
    wordInitialize();
    
    /** Scaling factors **/
    if(isScaling){ // scaling
      scalingMap.put(linear(right-1, right), operator.inverse(prefixProb[right-1]));
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

    /** Handle multi terminal rules **/
    if(hasMultiTerminalRule){
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
    
    if(isFastComplete){
      fastChartComplete(right);
    } else {
      chartComplete(right);
    }
    
    
    if(verbose>=1){
      Timing.tick("# " + word + ", finished chartComplete");
    }
    
    //3. chartPredict all new active edges for further down the road
    if(verbose>=1){
      Timing.startTime();
    }
    chartPredict(right);
    if(verbose>=1){
      Timing.tick("# " + word + ", finished chartPredict");
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
  protected void scanning(int left, int right, int tag, double score){
    if(verbose>=1){
      System.err.println("# Scanning [" + left + ", " + right + "]: "
          + parserTagIndex.get(tag) + "->" + 
          words.subList(left, right) + " : " + operator.getProb(score));
    }
    
    // scaling
    double inner = score;
    if(isScaling){ 
      inner = operator.multiply(score, getScaling(left, right));
    }
    
    int edge = edgeSpace.indexOfTag(tag);
    assert(edgeSpace.get(edge).numChildren()==0);
    
    // add terminal rule
    if(insideOutsideOpt>0){   
      Edge terminalEdge = new Edge(new TerminalRule(tag, wordIndices.subList(left, right)), right-left);
      Rule rule = terminalEdge.getRule();
      if(!ruleSet.contains(rule)){
        ProbRule probRule = new ProbRule(rule, operator.getProb(score));
        ruleSet.add(probRule);
   
        if(verbose>=3){
          System.err.println("Add ProbRule " + probRule.toString(parserTagIndex, parserWordIndex));
        }
      }
      
      if (this instanceof EarleyParserSparse){
        edge = edgeSpace.addEdge(terminalEdge);
      }
    }

    // complete info
    addCompletedEdges(left, right, edge);
    
    addToChart(left, right, edge, operator.zero(), inner);
  }
  
  /********************************/
  /********** PREDICTION **********/
  /********************************/
  /**
   * Predictions at column right
   * 
   * @param right
   */
  protected void chartPredict(int right) {
    // init
    initPredictTmpScores();
    
    boolean flag = false;
    for (int left = 0; left <= right; left++) {
      if (insideChartCount(left, right) == 0){ // no active categories
        continue;
      }
      
      if(verbose>=3){
        System.err.println("\n# Predict all [" + left + "," + right + "]: " + 
            "chart count=" + insideChartCount(left, right));
      }
      
      flag = true;
      for(int edge : listInsideEdges(left, right)){
        // predict for right: left X -> \alpha . Y \beta
        predictFromEdge(left, right, edge);
      }
    }
    
    storePredictTmpScores(right);
    
    if (verbose >= 3 && flag) {
      dumpChart();
    }
  }
  

  protected void predictFromEdge(int left, int right, int edge) {
    Prediction[] predictions = g.getPredictions(edge);
    if (verbose >= 3 && predictions.length>0) {
      System.err.println("From edge " + edgeScoreInfo(left, right, edge));
    }
    
    for (int x = 0, n = predictions.length; x < n; x++) { // go through each prediction
      Prediction p = predictions[x];
      
      // spawn new edge
      int newEdge = p.predictedState;
      double newForwardProb = operator.multiply(getForwardScore(left, right, edge), p.forwardProbMultiplier);
      double newInnerProb = p.innerProbMultiplier;
      
      // add to tmp map
      addPredictTmpForwardScore(newEdge, newForwardProb);
      addPredictTmpInnerScore(newEdge, newInnerProb);
      
      // store activeEdgeInfo: right: right X -> . \alpha
      assert(edgeSpace.get(newEdge).numChildren()>0 && edgeSpace.get(newEdge).getDot()==0);
      addActiveEdgeInfo(right, right, newEdge);
      
      if (verbose >= 3) {
        System.err.println("  to " + edgeScoreInfo(right, right, newEdge, newForwardProb, newInnerProb));
      }
    }
  }
  
  /********************************/
  /********** COMPLETION **********/
  /********************************/
  public void addCompletedEdges(int left, int right, int edge){
    int lrIndex = linear(left, right);
    if(!completedEdges.containsKey(lrIndex)){
      completedEdges.put(lrIndex, new HashSet<Integer>());
    }
    completedEdges.get(lrIndex).add(edge);
    
    if(verbose>=3){
      System.err.println("# Add completed edge " + edgeInfo(left, right, edge));
    }
  }
  
  public void addActiveEdgeInfo(int left, int right, int edge){
    if(!activeEdgeInfo.get(right).containsKey(edge)){
      activeEdgeInfo.get(right).put(edge, new HashSet<Integer>());
    }
    activeEdgeInfo.get(right).get(edge).add(left);
    
    if(verbose>=3){
      System.err.println("# Add active edge info " + edgeInfo(left, right, edge));
    }
  }
  
  /**
   * Consider all triples [left, middle, right] to see if there're any edge pair
   * middle: left X -> _ . Y _ and right: middle Y -> _ and , we'll then create a new edge
   * right: left X -> _ Y . _  
   * @param right
   */
  protected void chartComplete(int right) {
    // Note: for loop orders here matter
    // basically, we start out completing small chunks and gradually form larger chunks to complete
    for (int left = right - 1; left >= 0; left--) {      
      for (int middle = right - 1; middle >= left; middle--) {
        cellComplete(left, middle, right);
      } // end middle
    } // end left
    
    storePrefixProb(right);
    if(verbose>=3){
      dumpChart();
    }
  }
  
  protected void cellComplete(int left, int middle, int right){
    // init
    initCompleteTmpScores();
    
    if(insideChartCount(middle, right)>0){
      // there're active edges for the span [middle, right]
      if(verbose>=3){
        System.err.println("\n# Complete all [" + left + "," + middle + "," + right + "]: insideChartCount[" 
            + middle + "," + right + "]=" + insideChartCount(middle, right));
      }
      
      // tag completions
      for(int edge : listInsideEdges(middle, right)){
        if(edgeSpace.to(edge) == -1){ // no more child after the dot
          // right: middle Y -> _ .
          // in completion the forward prob of Y -> _ . is ignored
          complete(left, middle, right, edge, getInnerScore(middle, right, edge)); 
        }
      }
    }
    
    /** Handle multi-terminal rules **/
    if(hasMultiTerminalRule){
      handleMultiTerminalRules(left, middle, right);
    }
    
    // completions yield edges: right: left X -> \alpha Y . \beta
    storeCompleteTmpScores(left, right);
  }
  
  protected void fastChartComplete(int right){
    for (int middle = right - 1; middle >= 0; middle--) {
      int mrIndex = linear(middle, right);
      
      // list of nextEdge right: middle Y -> v .
      if(completedEdges.containsKey(mrIndex)){
        if(verbose>=3){
          System.err.println("\n# Complete all [" + middle + "," + right + "]: insideChartCount[" 
              + middle + "," + right + "]=" + completedEdges.get(mrIndex).size());
        }
        
        Integer[] copyEdges = completedEdges.get(mrIndex).toArray(new Integer[0]);
        for(int nextEdge : copyEdges){
          fastCellComplete(middle, right, nextEdge); 
        }
      }
      
      // could be made faster
      /** Handle multi-terminal rules **/
      for(int left=middle; left>=0; left--){
        if(hasMultiTerminalRule){
          handleMultiTerminalRules(left, middle, right);
        }
      }
    }
    
    storePrefixProb(right);
    if(verbose>=3){
      dumpChart();
    }
  }
  
  protected void fastCellComplete(int middle, int right, int nextEdge){
    // next edge, right: middle Y -> v .
    int tag = edgeSpace.get(nextEdge).getMother(); // Y
    
    // set of completions X -> \alpha . Z \beta
    Completion[] completions = g.getCompletions(tag);

    if(completions.length>0){ // there exists a completion
      double inner = getInnerScore(middle, right, nextEdge);
      if (verbose>=3){
        System.err.println(completionInfo(middle, right, nextEdge, inner, completions));
      }
 
      for (Completion completion : completions) { // go through all completions we could finish
        int prevEdge = completion.activeEdge; // X -> \alpha . Z \beta

        if(activeEdgeInfo.containsKey(middle) && activeEdgeInfo.get(middle).containsKey(prevEdge)){
          if(verbose>=3){
            System.err.println("Left for [*, " + middle + "] " + 
          edgeSpace.get(prevEdge).toString(parserTagIndex, parserWordIndex) 
                + ":  " + activeEdgeInfo.get(middle).get(prevEdge));
          }
          for(int left : activeEdgeInfo.get(middle).get(prevEdge)){ // middle : left X -> \alpha . Z \beta
            assert(edgeSpace.get(prevEdge).numRemainingChildren()>0);
            /* add/update newEdge right: left X -> \alpha Z . \beta */
            double updateScore = operator.multiply(completion.score, inner);
            double newForwardProb = operator.multiply(
                getForwardScore(left, middle, prevEdge), updateScore);
            double newInnerProb = operator.multiply(
                getInnerScore(left, middle, prevEdge), updateScore);
            int newEdge = completion.completedEdge;

            addInnerScore(left, right, newEdge, newInnerProb);
            addForwardScore(left, right, newEdge, newForwardProb);
            if(this instanceof EarleyParserDense && !containsInsideEdge(left, right, newEdge)){
              ((EarleyParserDense) this).chartCount[linear(left, right)]++;
              ((EarleyParserDense) this).chartEntries[linear(left, right)][newEdge] = true;
            }
            
            // complete info
            Edge newEdgeObj = edgeSpace.get(newEdge);
            if (newEdgeObj.numRemainingChildren()==0){ // completed: X -> \alpha Z .
              assert(newEdge==goalEdge || left<middle); // alpha not empty
              addCompletedEdges(left, right, newEdge);
            } else {
              addActiveEdgeInfo(left, right, newEdge);
            }
            
            // Viterbi: store backtrack info
            if(decodeOpt==1){
              addBacktrack(left, middle, right, nextEdge, newEdge, newInnerProb);
            }


            if (verbose >= 3) {
              System.err.println("  start " + edgeScoreInfo(left, middle, completion.activeEdge) 
                  + " -> new " + edgeScoreInfo(left, right, newEdge, newForwardProb, newInnerProb));

              if (isGoalEdge(newEdge)) {
                System.err.println("# String prob +=" + Math.exp(newInnerProb));
              }
            }
            
            // also a careful addition to the prefix probabilities -- is this right?
            if (middle == right - 1) {
              addPrefixProb(left, middle, right, newForwardProb, inner, completion);  
            }
          } // end for left
        } // end if completeInfo
      } // end for completion
    } // end if completions.length
  }
  
  protected void handleMultiTerminalRules(int left, int middle, int right){
    Map<Integer, Double> valueMap = g.getRuleTrie().findAllPrefixMap(
        wordIndices.subList(middle, right));
    
    if(valueMap != null){
      if(verbose >= 2){
        System.err.println("# AG prefix " + Util.sprint(parserWordIndex, 
            wordIndices.subList(middle, right)) + 
            ": " + Util.sprint(valueMap, parserTagIndex));
      }
      for(Entry<Integer, Double> entry : valueMap.entrySet()){
        int tag = entry.getKey();
        int edge = edgeSpace.indexOfTag(tag);
        double score = entry.getValue();
        addPrefixProbExtendedRule(left, middle, right, edge, score);
      }
    }  
  }
  
  protected void complete(int left, int middle, int right, int nextEdge, double inner) {
    // next edge, right: middle Y -> v .
    int tag = edgeSpace.get(nextEdge).getMother();
    assert(edgeSpace.get(nextEdge).numRemainingChildren()==0);
    
    // set of completions X -> \alpha . Z \beta
    Completion[] completions = g.getCompletions(tag);
    
    if (verbose>=2 && completions.length>0){
      System.err.println(completionInfo(middle, right, nextEdge, inner, completions));
    }
    
    for (Completion completion : completions) { // go through all completions we could finish
      if (containsInsideEdge(left, middle, completion.activeEdge)) { // middle: left X -> \alpha . Z \beta
        double updateScore = operator.multiply(completion.score, inner);
        double newForwardProb = operator.multiply(
            getForwardScore(left, middle, completion.activeEdge), updateScore);
        double newInnerProb = operator.multiply(
            getInnerScore(left, middle, completion.activeEdge), updateScore);
        int newEdge = completion.completedEdge;
        
        // add edge, right: left X -> _ Z . _, to tmp storage
        initCompleteTmpScores(newEdge);
        addCompleteTmpForwardScore(newEdge, newForwardProb);
        addCompleteTmpInnerScore(newEdge, newInnerProb);
    
        // inside-outside info to help outside computation later or marginal decoding later
        if(insideOutsideOpt>0 || decodeOpt==2){
          Edge edgeObj = edgeSpace.get(newEdge);
          
          if(edgeObj.numRemainingChildren()==0){ // complete right: left X -> _ Y .
            addCompletedEdges(left, right, newEdge);
          }
        }
        
        // Viterbi: store backtrack info
        if(decodeOpt==1){
          addBacktrack(left, middle, right, nextEdge, newEdge, newInnerProb);
        }
        
        if (verbose >= 2) {
          System.err.println("  start " + edgeScoreInfo(left, middle, completion.activeEdge) 
              + " -> new " + edgeScoreInfo(left, right, newEdge, newForwardProb, newInnerProb));

          if (isGoalEdge(newEdge)) {
            System.err.println("# String prob +=" + Math.exp(newInnerProb));
          }
        }

        //also a careful addition to the prefix probabilities -- is this right?
        if (middle == right - 1) {
          addPrefixProb(left, middle, right, newForwardProb, inner, completion);
        }
      }
    }
  }
  
  public void addBacktrack(int left, int middle, int right, int nextEdge, int newEdge, double newInnerProb){
    int lrIndex = linear(left, right);
    if(!backtrackChart.containsKey(lrIndex)){
      backtrackChart.put(lrIndex, new HashMap<Integer, BackTrack>());
    }
    Map<Integer, BackTrack> backtrackCell = backtrackChart.get(lrIndex);
    if(!backtrackCell.containsKey(newEdge)){ // no backtrack
      // store Y -> v .
      backtrackCell.put(newEdge, new BackTrack(nextEdge, middle, newInnerProb));
    } else {
      BackTrack backtrack = backtrackCell.get(newEdge);
      if(backtrack.parentInnerScore < newInnerProb){ // update backtrack info
        backtrackCell.put(newEdge, new BackTrack(nextEdge, middle, newInnerProb));
      }
    }
  }
  
  public void addPrefixProb(int left, int middle, int right, double newForwardProb, double inner, Completion completion){
    thisPrefixProb.add(newForwardProb);
    double synProb = operator.divide(newForwardProb, inner);
    thisSynPrefixProb.add(synProb); // minus the lexical score
    if (verbose >= 2) {
      System.err.println("# Prefix prob += " + operator.getProb(newForwardProb) + "=" + 
          operator.getProb(getForwardScore(left, middle, completion.activeEdge)) + "*" + 
          operator.getProb(completion.score) + "*" + operator.getProb(inner) + "\t" + 
          left + "\t" + middle + "\t" + completion.activeEdge);
      System.err.println("# Syn prefix prob += " + operator.getProb(synProb) + "=" + 
          operator.getProb(newForwardProb) + "/" + 
          operator.getProb(inner));
    }
  }
  
  private void computeOutsideProbs(double rootInnerScore){
    if(verbose>=4){
      System.err.println("# Completed edges:");
      for (int left = 0; left <= numWords; left++) {
        for (int right = left; right <= numWords; right++) {
          int lrIndex = linear(left, right);
          
          if(completedEdges.containsKey(linear(left, right))){
            for(int edge : completedEdges.get(lrIndex)){
              System.err.println(edgeInfo(left, right, edge));
            }
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
  
  public boolean hasParse(){
    return (numWords>0 && sentLogProb()>Double.NEGATIVE_INFINITY);
  }
  public Tree viterbiParse(){
    if(hasParse()){
      return viterbiParse(0, numWords, goalEdge);
    } else {
      System.err.println("! No viterbi parse");
      return null;
    }
  }

  public Tree viterbiParse(int left, int right, int edge){
    if(verbose>=3){
      System.err.println("# Viterbi parse " + edgeInfo(left, right, edge));
    }

    // X -> \alpha . \beta
    Edge edgeObj = edgeSpace.get(edge);
    Label motherLabel = new Tag(parserTagIndex.get(edgeObj.getMother()));
    
    Tree returnTree = null;
    if(edgeObj.getDot()==0 && edgeObj.numChildren()>0){ // X -> . \alpha
      returnTree = new LabeledScoredTreeNode(motherLabel);
    } else if(edgeObj.isTerminalEdge()){ // X -> _w1 ... _wn
      List<Tree> daughterTreesList = new ArrayList<Tree>();
      for (int i = left; i < right; i++) {
        daughterTreesList.add(new LabeledScoredTreeNode(new Word(words.get(i).word())));
      }
      
      returnTree = new LabeledScoredTreeNode(motherLabel, daughterTreesList);
    } else { // X -> \alpha Y . \beta
      assert(edge==goalEdge || edgeObj.numChildren()>1);
      BackTrack backtrack = backtrackChart.get(linear(left, right)).get(edge);
      
      // Viterbi parse: X -> \alpha . Z \beta
      Edge prevEdgeObj = edgeObj.getPrevEdge(); 
      int prevEdge = edgeSpace.indexOf(prevEdgeObj);
      //System.err.println(edgeInfo(left, backtrack.middle, prevEdge));
      returnTree = viterbiParse(left, backtrack.middle, prevEdge);
      
      // Viterbi parse: Y -> v .
      int nextEdge = backtrack.edge;
      Edge nextEdgeObj = edgeSpace.get(nextEdge);
      Tree nextTree = viterbiParse(backtrack.middle, right, nextEdge);
      
      if(prevEdgeObj.getChildAfterDot(0) != nextEdgeObj.getMother()){ // unary chain
        List<Integer> chain = ruleSet.getUnaryChain(prevEdgeObj.getChildAfterDot(0), 
            nextEdgeObj.getMother());
        for (int i = chain.size()-2; i >= 0; i--) {
          Label label = new Tag(parserTagIndex.get(chain.get(i)));
          nextTree = new LabeledScoredTreeNode(label, Arrays.asList(nextTree));
        }
      }
      
      // adjoin trees
      returnTree.addChild(nextTree);
    }
    
    if(verbose>=3){
      System.err.println("[" + left + ", " + right + "] " + returnTree);
    }
    return returnTree;
  }
  
  
  protected void outside(int start, int end, int rootEdge, double rootInsideScore, int verbose){
    assert(start<end);

    // configurations.get(linear(left, right)): set of edges to compute outside
    Map<Integer, Set<Integer>> configurations = new HashMap<Integer, Set<Integer>>();
    
    // init
    for(int length=end-start; length>=0; length--){
      for (int left=0; left<=end-length; left++){
        int right = left+length;
        
        configurations.put(linear(left, right), new HashSet<Integer>());
      }
    }
    configurations.get(linear(start, end)).add(rootEdge); // add starting edge
 
    // outside
    for(int length=end-start; length>0; length--){
      if(verbose>=1){
        System.err.println("# Outside length " + length + "");
      }
      for (int left=0; left<=end-length; left++){ // left
        int right = left+length; // right

        // accumulate outside probabilities
        int lrIndex = linear(left, right);
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
              if(containsInsideEdge(left, middle, prevEdge) && parentOutside>operator.zero()
                  && completedEdges.containsKey(linear(middle, right))){
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
    
    int mrIndex = linear(middle, right);
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
          configurations.get(linear(left, middle)).add(prevEdge);
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
    Edge edgeObj = edgeSpace.get(edge);
    if(left==right || edgeObj.isTerminalEdge()){ // predicted edges: X -> . \alpha or tag -> terminals .
      double insideScore = getInnerScore(left, right, edge);
      if(insideScore > operator.zero()){
        double expectedCount = operator.divide(operator.multiply(outsideScore, insideScore), rootInsideScore);
        assert(expectedCount>operator.zero());
        
        if(edgeObj.numChildren()==0){ // tag -> []
          edgeObj = new Edge(new TerminalRule(edgeObj.getMother(), wordIndices.subList(left, right)), right-left);
        }
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
  protected abstract void addPrefixProbExtendedRule(int left, int middle, int right, int edge, double inner);

  /**
   * if there exists an edge spanning [left, right]
   * 
   * @param left
   * @param right
   * @param edge
   * @return
   */
  // inside
  protected abstract boolean containsInsideEdge(int left, int right, int edge);
  protected abstract int insideChartCount(int left, int right);
  protected abstract Set<Integer> listInsideEdges(int left, int right);
  
  // outside
  protected abstract boolean containsOutsideEdge(int left, int right, int edge);
  protected abstract int outsideChartCount(int left, int right);
  protected abstract Set<Integer> listOutsideEdges(int left, int right);
  protected abstract void initOuterProbs();
  
  // tmp predict probabilities
  protected abstract void initPredictTmpScores();
  protected abstract void addPredictTmpForwardScore(int edge, double score);
  protected abstract void addPredictTmpInnerScore(int edge, double score);
  protected abstract void storePredictTmpScores(int right);
 
  // tmp complete probabilities
  protected abstract void initCompleteTmpScores();
  protected abstract void initCompleteTmpScores(int edge);
  protected abstract void addCompleteTmpForwardScore(int edge, double score);
  protected abstract void addCompleteTmpInnerScore(int edge, double score);
  protected abstract void storeCompleteTmpScores(int left, int right);
  
  // forward probabilities
  protected abstract double getForwardScore(int left, int right, int edge);
  protected abstract void addForwardScore(int left, int right, int edge, double score);
  
  
  // inner probabilities
  protected abstract double getInnerScore(int left, int right, int edge);
  protected abstract void addInnerScore(int left, int right, int edge, double score);
  
  // outer probabilities
  protected abstract double getOuterScore(int left, int right, int edge);
  protected abstract void addOuterScore(int left, int right, int edge, double score);
  
  // debug
  public abstract String edgeScoreInfo(int left, int right, int edge);
  
  /*******************/
  /** Other methods **/
  /*******************/
  protected void addScore(Map<Integer, Double> probMap, int key, double score){
    if(probMap == null){
      return;
    }
    
    if(!probMap.containsKey(key)){
      probMap.put(key, score);
    } else {
      probMap.put(key, operator.add(probMap.get(key), score));
    }
  }
  
  // linear index of cell[left][right]
  protected int linear(int left, int right){
    return (right+1)*right/2 + right-left;
  }
  
  /**
   * Initialization for every sentence
   */
  protected void sentInit(){
    if (verbose>=2){
      System.err.println("# EarleyParser initializing ... ");
    }
    
    // words
    if(words == null || words.size()==0){
      System.err.println("! Empty sentence");
      System.exit(1);
    }
    
    numWords = words.size();
    wordIndices = new ArrayList<Integer>();
    for (HasWord word : words) {
      wordIndices.add(parserWordIndex.indexOf(word.word(), true));
    }
    
    // map matrix indices [left][right] into linear indices
    long numCells = (numWords+4)*(numWords+1)/2;
    if(numCells>Integer.MAX_VALUE){
      System.err.println("! Num words = " + numWords + " is too large, causing linear indices to exceed max integer value " + Integer.MAX_VALUE);
      System.exit(1);
    }
    assert(linear(0, numWords+1)==((numWords+4)*(numWords+1)/2));
    
    prefixProb = new double[numWords + 1];
    synPrefixProb = new double[numWords + 1];
    
    if (isScaling){
      scalingMap = new HashMap<Integer, Double>();
    }
    
    // init prefix prob
    prefixProb[0] = operator.one();
    
    // completion info
    completedEdges = new HashMap<Integer, Set<Integer>>();
    activeEdgeInfo = new HashMap<Integer, Map<Integer, Set<Integer>>>();
    for(int i=0; i<=numWords; i++)
      activeEdgeInfo.put(i, new HashMap<Integer, Set<Integer>>());
    
    // result lists
    surprisalList = new ArrayList<Double>();
    synSurprisalList = new ArrayList<Double>();
    lexSurprisalList = new ArrayList<Double>();
    stringLogProbList = new ArrayList<Double>();
    
    
    // Decode
    if(decodeOpt==1){ // Viterbi parse  
      backtrackChart = new HashMap<Integer, Map<Integer,BackTrack>>();
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
      logProb -= operator.getLogProb(getScaling(0, right));
    }
    
    return logProb;
  }
  
  public double sentLogProb(){
    return stringLogProbability(numWords);
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
  
  protected String completionInfo(int middle, int right, 
      int edge, double inner, Completion[] completions){
    return "Completed " + + edge + " " + edgeInfo(middle, right, edge)  
    + ", inside=" + df.format(operator.getProb(inner))  
    + " -> completions: " + Util.sprint(completions, edgeSpace, parserTagIndex, parserWordIndex, operator);
  }
  
  /****************/
  /** Debug info **/
  /****************/
  /**
   * print chart for debugging purpose
   *   isOnlyCategory: only print categories (completed edges) but not partial edges
   * 
   * @param chart
   * @param isOnlyCategory
   */
  protected String dumpChart(String type){
    if(!type.equalsIgnoreCase("outside") && !type.equalsIgnoreCase("inside")){
      System.err.println("! dumpChart: Unknown chart type " + type);
      System.exit(1);
    }
    
    StringBuffer sb = new StringBuffer("# " + type + " chart snapshot\n");
    for(int length=1; length<=numWords; length++){ // length
      for (int left = 0; left <= numWords-length; left++) {
        int right = left+length;

        // scaling
        double scalingFactor = isScaling ? getScaling(left, right, type) : operator.one();
        
        int count = type.equalsIgnoreCase("inside") ? 
            insideChartCount(left, right) : outsideChartCount(left, right);
        if (count==0){
          continue;
        }
        sb.append("[" + left + "," + right + "]: " + count  
            + " (" + df1.format(count*100.0/edgeSpaceSize) + "%)\n");
        
        // print by edge
        Set<Integer> edges = type.equalsIgnoreCase("inside") ?
            listInsideEdges(left, right) : listOutsideEdges(left, right);
        for (int edge : edges) { // edge
          double score = type.equalsIgnoreCase("inside") ?
              getInnerScore(left, right, edge) : getOuterScore(left, right, edge);
          sb.append("  " + edgeSpace.get(edge).toString(parserTagIndex, parserWordIndex) 
              + ": " + df.format(operator.getProb(operator.divide(score, scalingFactor))) + "\n");
        }
      }
    }  
    
    return sb.toString();
  }
  
  public String dumpInnerProb(){
    return dumpChart("Inner");
  }
  
  public String dumpOuterProb(){
    return dumpChart("Outer");
  }
  
  protected Map<Integer, Map<Integer,Double>> computeCatChart(String type){ // type either "inside" or "outside"
    if(!type.equalsIgnoreCase("outside") && !type.equalsIgnoreCase("inside")){
      System.err.println("! computeCatChart: Unknown chart type " + type);
      System.exit(1);
    }
    Map<Integer, Map<Integer,Double>> chart = new HashMap<Integer, Map<Integer,Double>>();
    
    for(int length=1; length<=numWords; length++){ // length
      for (int left = 0; left <= numWords-length; left++) {
        int right = left+length;

        int lrIndex = linear(left, right);
        if ((type.equalsIgnoreCase("inside") && insideChartCount(left, right)==0) ||
            (type.equalsIgnoreCase("outside") && outsideChartCount(left, right)==0))
          continue;
        chart.put(lrIndex, new HashMap<Integer, Double>());
        
        // scaling
        double scalingFactor = isScaling ? getScaling(left, right, type) : operator.one();
        Map<Integer, Double> tagMap = chart.get(lrIndex);
        
        // accumulate score for categories
        Set<Integer> edges = type.equalsIgnoreCase("inside") ? 
            listInsideEdges(left, right) : 
              listOutsideEdges(left, right);
        
        for (int edge : edges) { // edge
          Edge edgeObj = edgeSpace.get(edge);
          if(edgeObj.numRemainingChildren()==0){ // completed edge
            int tag = edgeObj.getMother();
            
            double score = operator.zero();
            if(type.equalsIgnoreCase("outside")){
              score = getOuterScore(left, right, edge);
            } else if(type.equalsIgnoreCase("inside")){
              score = getInnerScore(left, right, edge);
            }
            
            if(score==operator.zero())
              continue;
            
            score = operator.divide(score, scalingFactor);
            if(!tagMap.containsKey(tag)){ // new tag
              tagMap.put(tag, score);
            } else { // old tag
              if(type.equalsIgnoreCase("outside")){
                assert(tagMap.get(tag) == score); // outside probs for paths X -> * . Y are all the same despite the part on the left of the dot
              } else if(type.equalsIgnoreCase("inside")){
                tagMap.put(tag, operator.add(tagMap.get(tag), score));
              }
            }
          }
        }
      }
    }  

    return chart;
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

        int lrIndex = linear(left, right);
        if(!chart.containsKey(lrIndex))
          continue;
        
        Map<Integer, Double> tagMap = chart.get(lrIndex);
        if(tagMap.size()>0){
          sb.append("cell " + left + "-" + right + "\n");
        }
        
        // print by tag            
        for(int tag=0; tag<parserTagIndex.size(); tag++){
          if(!tagMap.containsKey(tag))
            continue;
          
          double score = tagMap.get(tag);
          if(score == operator.zero())
            continue;
          
          sb.append(String.format(" %s: %s\n", parserTagIndex.get(tag),
              operator.getProb(score))); // df2.format(
        }
      }
    }  
    
    return sb.toString();
  }
  
  public String dumpInsideChart() {
    return dumpCatChart(computeCatChart("Inside"), "Inside");
  }
  
  public String dumpOutsideChart() {
    return dumpCatChart(computeCatChart("Outside"), "Outside");
  }
  
  public List<Double> insideOutside(List<String> sentences){
    return insideOutside(sentences, "");
  }
  
  public List<Double> insideOutside(List<String> sentences, String outPrefix){
    double minRuleProb = 1e-50;
    return insideOutside(sentences, outPrefix, minRuleProb);
  }
  
  public List<Double> insideOutside(List<String> sentences, String outPrefix, double minRuleProb){
    return insideOutside(sentences, outPrefix, 0, 0, minRuleProb);
  }
  
  public List<Double> insideOutside(List<String> sentences, String outPrefix, 
      int maxiteration, int intermediate){
    double minRuleProb = 1e-50;
    return insideOutside(sentences, outPrefix, maxiteration, intermediate, minRuleProb);
  }
  
  public List<Double> insideOutside(List<String> sentences, String outPrefix, 
      int maxIteration, int intermediate, double minRuleProb){
    int minIteration = 1;
    double stopTol = 1e-7;
    
    
    System.err.println("## Inside-Outisde stopTol=" + stopTol + ", minRuleProb=" + minRuleProb);
    List<Double> sumNegLogProbList = new ArrayList<Double>();
    int numIterations = 0;
    int prevNumRules = 0;
    double prevSumNegLogProb = Double.POSITIVE_INFINITY;
    while(true){
      numIterations++;
      if(verbose>=3){
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
      
      if(verbose>=-1){
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
      
      // output intermediate IO grammars & parses
      if (intermediate>0 && numIterations % intermediate == 0 && !outPrefix.equals("")){ 
        String outGrammarFile = outPrefix + "." + numIterations + ".iogrammar" ;
        try {
          RuleFile.printRules(outGrammarFile, getAllRules(), getParserWordIndex(), getParserTagIndex());
        } catch (IOException e) {
          System.err.println("! Error outputing intermediate grammar " + outGrammarFile);
          System.exit(1);
        }
        
        parseSentences(sentences, outPrefix + "." + numIterations);
      }
      
      // convergence test
      if(numIterations>=minIteration && numRules==prevNumRules){
        if(maxIteration>0 && numIterations>=maxIteration){ // exceed max iterations
          if(verbose>=3){
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
    
    parseSentences(sentences, outPrefix);
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
      
      if(insideOutsideOpt==2){ // VB
        // add bias
        expectedCounts.put(ruleId, operator.add(expectedCounts.get(ruleId), 
             operator.getScore(ruleSet.getBias(ruleId))));        
      }
      
      if(!tagSums.containsKey(tag)){
        tagSums.put(tag, operator.zero());
      }
      
      tagSums.put(tag, operator.add(tagSums.get(tag), expectedCounts.get(ruleId)));
    }
    
    // normalized probs
    int numRules = 0;
    Map<Integer, Double> vbTagLogSums = null;
    Map<Integer, Double> vbRuleLogProbs = null;
    if(insideOutsideOpt==2){ // for VB we need to renormalize later
      vbTagLogSums = new HashMap<Integer, Double>();
      vbRuleLogProbs = new HashMap<Integer, Double>();
    }
    for (int ruleId = 0; ruleId < ruleSet.size(); ruleId++) {
      if(expectedCounts.containsKey(ruleId)){
        assert(operator.getProb(expectedCounts.get(ruleId))>0);
        int tag = ruleSet.getMother(ruleId);
        
        if(insideOutsideOpt==2){ // VB
          double logProb = Dirichlet.digamma(operator.getProb(expectedCounts.get(ruleId))) 
            - Dirichlet.digamma(operator.getProb(tagSums.get(tag)));
          vbRuleLogProbs.put(ruleId, logProb);
          if(!vbTagLogSums.containsKey(tag)){
            vbTagLogSums.put(tag, Double.NEGATIVE_INFINITY);
          }
          
          vbTagLogSums.put(tag, SloppyMath.logAdd(vbTagLogSums.get(tag),logProb));
        } else { // MLE
          double newProb = operator.getProb(operator.divide(expectedCounts.get(ruleId), 
              tagSums.get(tag)));
        
          if(newProb<minRuleProb){ // filter
            System.err.println("Filter: " + newProb + "\t" + ruleSet.get(ruleId).getRule().toString(parserTagIndex, parserWordIndex));
            newProb = 0.0; 
          } else {
            numRules++;
            if(verbose>=3){
              System.err.println(newProb + "\t" + ruleSet.get(ruleId).getRule().toString(parserTagIndex, parserWordIndex));
            }
          }
          
          ruleSet.setProb(ruleId, newProb);
        } // end insideOutsideOpt
      }
      
      
    }
    
    // VB, renormalize
    if(insideOutsideOpt==2){ 
      for (int ruleId = 0; ruleId < ruleSet.size(); ruleId++) {
        if(expectedCounts.containsKey(ruleId)){
          int tag = ruleSet.getMother(ruleId);
          double newProb = Math.exp(vbRuleLogProbs.get(ruleId)- vbTagLogSums.get(tag));
          ruleSet.setProb(ruleId, newProb);
          
          if(newProb<minRuleProb){ // filter
            System.err.println("Filter: " + newProb + "\t" + ruleSet.get(ruleId).getRule().toString(parserTagIndex, parserWordIndex)
                + "\t" + operator.getProb(expectedCounts.get(ruleId)) + 
                ", " + operator.getProb(tagSums.get(tag)));
            newProb = 0.0; 
          } else {
            numRules++;
            if(verbose>=3){
              System.err.println(newProb + "\t" + ruleSet.get(ruleId).getRule().toString(parserTagIndex, parserWordIndex));
            }
          }
        }
      }
    }
    
    return numRules;
  }
  
  public List<String> socialMarginalDecoding(){
    // mark left and right boundary of a sentence
    // for example if the first sentence has 5 words, then sentLeft = 0, sentRight = 5
    int sentLeft = 0;
    int sentRight = 0;
    
    // .dog kid.eyes mom.eyes # .pig kid.hands # ## and whats that is this a puppy dog
    int doubleHashPos = -1; // if we have seen ## (doubleHashPos>0), that means we have passed through all social cues
    
    if(verbose>0){
      System.err.println("# social marginal decoding: num words = " + numWords);
    }
    
    List<String> results = new ArrayList<String>();
    for (int i = 0; i < numWords; i++) {
      String word = words.get(i).word();
      
      if(word.charAt(0) == '.' || word.equals("##")){ // start a social cue
        if(doubleHashPos>=0){ // this means we have finished processing a sentence
          String result = socialSentMarginalDecoding(sentLeft, sentRight, doubleHashPos);
          results.add(result);
          
          // reset
          sentLeft = sentRight;
          doubleHashPos = -1;
        }
      }

      if(word.equals("##")){ // start processing terminals
        doubleHashPos = i;
      }
      
      sentRight++;
    }
  
    assert(sentLeft<sentRight);

    // marginal decoding a sent
    String result = socialSentMarginalDecoding(sentLeft, sentRight, doubleHashPos);
    results.add(result);
    
    return results;
  }
  
  public Map<Integer, Double> computeMarginalMap(int left, int right){
    // edges with both inside and outside scores over [left, right]
    Set<Integer> edges = listInsideEdges(left, right);
    edges.retainAll(listOutsideEdges(left, right));
    
    Map<Integer, Double> marginalMap = new HashMap<Integer, Double>();
    for (int edge : edges) { // edge
      Edge edgeObj = edgeSpace.get(edge);
      if(edgeObj.numRemainingChildren()==0){ // completed edge
        int tag = edgeObj.getMother();

        // numerator of expected count
        double score = operator.multiply(getOuterScore(left, right, edge), getInnerScore(left, right, edge)); 
        assert(score > operator.zero());
        
        if(!marginalMap.containsKey(tag)){ // new tag
          marginalMap.put(tag, score);
        } else { // old tag
          marginalMap.put(tag, operator.add(marginalMap.get(tag), score));
        }
      }
    }

    return marginalMap;
  }
  
  public int argmax(Map<Integer, Double> marginalMap, String prefixFilter){
    assert(marginalMap.size()>0);
    
    int bestTag = -1;
    double bestScore = operator.zero();
    for(int tag : marginalMap.keySet()){
//      System.err.println(parserTagIndex.get(tag) + "\t" + marginalMap.get(tag));
      assert(parserTagIndex.get(tag).startsWith(prefixFilter) || parserTagIndex.get(tag).equals(""));
      
      if(marginalMap.get(tag) > bestScore){
        bestTag = tag;
        bestScore = marginalMap.get(tag);
      }
    }
    
    return bestTag;
  }
  
  public String socialSentMarginalDecoding(int sentLeft, int sentRight, int doubleHashPos){
    StringBuffer sb = new StringBuffer();
    
    System.err.println(" [" + sentLeft + ", " + sentRight + "]: " + words.subList(sentLeft, sentRight));
    
    // sent tag
    Map<Integer, Double> sentMarginalMap = computeMarginalMap(sentLeft, sentRight);
    
    int sentTag = argmax(sentMarginalMap, "Sentence");
    assert(sentTag>=0);
    sb.append(parserTagIndex.get(sentTag));
    
    System.err.println(parserTagIndex.get(sentTag) + " [" + sentLeft + ", " + sentRight + "]: " + words.subList(sentLeft, sentRight));
    
    for(int tag : sentMarginalMap.keySet()){
      System.err.println(parserTagIndex.get(tag) + "\t" + sentMarginalMap.get(tag));
    }

    // word tags
    for(int i=doubleHashPos+1; i<sentRight; i++){
      String word = words.get(i).word();
      
      Map<Integer, Double> wordMarginalMap = computeMarginalMap(i, i+1);
      int wordTag = argmax(wordMarginalMap, "Word");
      assert(wordTag>=0);
      
      sb.append(" (" + parserTagIndex.get(wordTag) + " " + word + ")");
      
//    if(verbose>=3){
      System.err.println(parserTagIndex.get(wordTag) + " [" + i + ", " + (i+1) + "] " + word);
      
      for(int tag : wordMarginalMap.keySet()){
        System.err.println(parserTagIndex.get(tag) + "\t" + wordMarginalMap.get(tag));
      }
//    }
    }
    
    return sb.toString();
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
////            scalingFactor = scalingMatrix[linear(left, right)];
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

        int count = insideChartCount(left, right);
        if(count>0){ // there're active states
          System.err.println("[" + left + "," + right + "]: " + count  
              + " (" + df1.format(count*100.0/edgeSpaceSize) + "%)");
          for (int edge : listInsideEdges(left, right)) {
            double forwardProb = getForwardScore(left, right, edge);
            double innerProb = getInnerScore(left, right, edge);
            
            if(isScaling){
              forwardProb = operator.divide(forwardProb, getScaling(left, right));
              innerProb = operator.divide(innerProb, getScaling(left, right));
            }
            
            System.err.println("  " + edgeSpace.get(edge).toString(parserTagIndex, parserWordIndex) 
                + ": " + df.format(operator.getProb(forwardProb)) 
                + " " + df.format(operator.getProb(innerProb)));
          }
        }
      }
    }
  }
  
  /**
   * Getters & Setters
   */
  protected void setObjectives(String objString) {
    objectives = new HashSet<String>();
    for(String objective : objString.split(",")){
      objectives.add(objective);
    }
    
    if(objectives.contains("viterbi")){
      decodeOpt = 1;
    } else if(objectives.contains("socialmarginal")){
      decodeOpt = 2;
    }
  }
  
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
    List<ProbRule> allRules = ruleSet.getAllRules();
    if(allRules.get(0).equals(rootRule)==true){ // remove root rule
      return allRules.subList(1, allRules.size());
    } else {
      return allRules;
    }
  }
  public List<Double> getSurprisalList() {
    return surprisalList;
  }
  public List<Double> getSynSurprisalList() {
    return synSurprisalList;
  }
  public List<Double> getLexSurprisalList() {
    return lexSurprisalList;
  }
  public List<Double> getStringLogProbList() {
    return stringLogProbList;
  }
  public List<Double> getStringProbList() {
    List<Double> stringProbList = new ArrayList<Double>();
    for(double logProb : stringLogProbList){
      stringProbList.add(Math.exp(logProb));
    }
    
    return stringProbList;
  }
  public int getDecodeOpt() {
    return decodeOpt;
  }
}

/** Unused code **/
//linear = new int[numWords+1][numWords+1];
//// go in the order of CKY parsing
//numCells=0;
//for(int right=0; right<=numWords; right++){
//  for(int left=right; left>=0; left--){
//    linear(left, right) = numCells++;
//  }
//}
//assert(numCells==((numWords+2)*(numWords+1)/2));

//for (int i = 0; i < numCells; i++) {
//  completedEdges.put(i, new HashSet<Integer>());
//}
//
//if(insideOutsideOpt==2){ // standard way
//  // inside-outside chart
//  insideChart = new HashMap<Integer, Map<Integer,Double>>();
//  
//  for (int i = 0; i < numCells; i++) {
//    insideChart.put(i, new HashMap<Integer, Double>());
//  }
//  outsideChart = null; // will initialize after computing outside probs
//}


// another way of computing expected counts
//protected Map<Integer, Map<Integer, Double>> insideChart; // .get(linear(left, right)).get(tag): inside prob
//protected Map<Integer, Map<Integer, Double>> outsideChart; // .get(linear(left, right)).get(tag): outside prob
//protected void addToInsideChart(int left, int right, int tag, double insideScore) {
//  addScore(insideChart.get(linear(left, right)), tag, insideScore);
//  
//  if(verbose>=3){
//    System.err.println("# Add inside score [" + left + ", " + right + ", " + 
//        parserTagIndex.get(tag) + "] " + operator.getProb(insideScore));
//  }
//  
//}
//
//protected void addToOutsideChart(int left, int right, int tag, double outsideScore) {
//  addScore(outsideChart.get(linear(left, right)), tag, outsideScore);
//  
//  if(verbose>=3){
//    System.err.println("# Add outside score [" + left + ", " + right + ", " + 
//        parserTagIndex.get(tag) + "] " + operator.getProb(outsideScore));
//  }
//  
//}

//// inside-outside
//if(insideOutsideOpt==2){
//  addToInsideChart(left, right, tag, inner);
//}

//if(insideOutsideOpt==2){
//  addToInsideChart(left, right, edgeObj.getMother(), newInnerProb);
//}