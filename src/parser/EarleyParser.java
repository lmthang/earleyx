package parser;

import decoder.Decoder;
import decoder.MarginalDecoder;
import decoder.ViterbiDecoder;
import edu.stanford.nlp.ling.HasWord;
import edu.stanford.nlp.ling.Word;
//import edu.stanford.nlp.math.SloppyMath;
import edu.stanford.nlp.parser.Parser;
import edu.stanford.nlp.parser.lexparser.IntTaggedWord;
import edu.stanford.nlp.util.Index;
import edu.stanford.nlp.util.Timing;

import java.io.BufferedWriter;
import java.io.FileWriter;
import java.io.IOException;
import java.text.DecimalFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;

import base.BackTrack;
import base.BaseLexicon;
import base.Edge;
import base.Rule;
import base.ProbRule;
import base.RuleSet;
import base.TerminalRule;
import util.Operator;
import util.Util;


/**
 * Abstract class for an Earley parser instance
 * with options to use scaling or not, and to use log-prob or not.
 * 
 * @author Minh-Thang Luong, 2012
 */

public abstract class EarleyParser implements Parser { 
  public static int verbose = -1;
  protected static DecimalFormat df = new DecimalFormat("0.000000");
  protected static DecimalFormat df1 = new DecimalFormat("0.00");
  protected static DecimalFormat df2 = new DecimalFormat("0.00000000");
  
  /** Things that are constructed once by the EarleyParserGenerator 
   * and reused by multiple instances of the parser **/
  protected Grammar grammar; // // will change if we do rule estimation.
  protected final EdgeSpace edgeSpace;
  protected final BaseLexicon lex;
  protected RuleSet ruleSet; // will change if we do rule estimation.
  protected final Index<String> parserWordIndex;
  protected final Index<String> parserTagIndex;
  // keys are nonterminal indices (indexed by parserTagIndex)
  // values are used to retrieve nonterminals (when necessary) in the order that
  //   we processed them when loading treebank or grammar files (more for debug purpose)
  protected final Map<Integer, Integer> parserNonterminalMap; // doesn't include preterminals. nonterminal + preterminal = parser tag indices
  protected final Operator operator; // either ProbOperator or LogProbOperator
  protected final Set<String> outputMeasures; // output measures (surprisal, stringprob, etc.)
  protected final Set<String> internalMeasures; // internal measures (prefix, entropy, etc.)
  
  /** general info **/
  protected final int startEdge; // "" -> . ROOT
  protected final int goalEdge; // "" -> [] if isLeftWildcard=true; otherwise, "" -> ROOT .
  protected final int edgeSpaceSize;   // edge space
  protected final int numCategories; // nonterminas + preterminals
  protected static boolean isFastComplete = false; // set to false in EarleyParserDense, and true in EarleyParserSparse
  protected final boolean hasMultiTerminalRule;
  protected final boolean hasFragmentRule;
  protected final boolean isSeparateRuleInTrie;
  
  /** inside-outside **/
  protected Map<Integer, Double> expectedCounts; // map rule indices (allRules) to expected counts

  /** decode options **/
  // backtrack info for computing Viterbi parse
  // .get(linear(left, right)).get(edge): back track info
  // edge: X -> \alpha Y . \beta
  // backtrack info is right: middle Y -> v . that leads
  // to X -> \alpha Y . \beta with maximum inner probabilities
  protected Map<Integer, Map<Integer, BackTrack>> backtrackChart;
  protected Decoder decoder;
  
  /** sent data **/
  protected List<? extends HasWord> words;
  protected List<Integer> wordIndices; // indices from parserWordIndex. size: numWords
  protected int numWords = -1;
  protected String sentId = "0";

	/** output info **/
  protected Measures measures; // store values for all measures, initialized for every sentence
//	private double[] wordEntropy; // numWords+1
//  private double[] wordMultiRuleCount; // numWords+1
//  private double[] wordMultiRhsLengthCount; // numWords+1
//  private double[] wordMultiFutureLengthCount; // numWords+1
//  private double[] wordMultiRhsLength; // numWords+1
//  private double[] wordMultiFutureLength; // numWords+1
//  
//  private double[] wordPcfgRuleCount; // numWords+1 
//  private double[] wordPcfgFutureLength; // numWords+1
//  private double[] wordAllFutureLength; // numWords+1
//  private double[] wordPcfgFutureLengthCount; // numWords+1
  
  /** prefix probabilities **/
  // TODO: use double lists and sum when reaching a certain capacity
  // prefixProb[i]: prefix prob/log-prob of word_0...word_(i-1) sum/log-sum of thisPrefixProb
  protected double[] wordPrefixScores; // numWords+1, wordPrefixProbs[i] = wordPcfgPrefixProbs[i] + wordMultiPrefixProbs[i]
//  protected double[] wordPcfgPrefixScores; // numWords+1
//  protected double[] wordMultiPrefixScores; // numWords+1
  
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
  // activeEdgeInfo.get(right).get(edge): set of left positions of completed edges that span [left, right]
  // edge is X -> \alpha . \beta where \beta is non empty
  protected Map<Integer, Map<Integer, Set<Integer>>> activeEdgeInfo;
  
  // fragmentEdgeInfo.get(right).get(left): set of edges X -> \alpha . _y \beta that span [left, right] and _y matches the input terminal at [right, right+1] 
  // edge is X -> \alpha . \beta where \beta is non empty
  protected Map<Integer, Map<Integer, Set<Integer>>> fragmentEdgeInfo;


  /******************/
  /** Constructors **/
  /******************/
//  public EarleyParser(String grammarFile, int inGrammarType, String rootSymbol, 
//      boolean isScaling, boolean isLogProb, String ioOptStr, String decodeOptStr, String measureString){
//    preInit(rootSymbol, isScaling, isLogProb, ioOptStr, decodeOptStr, measureString);
//    
//    MISSING CODE
//    
//    postInit(rootSymbol);
//  }
//  public EarleyParser(BufferedReader br, String rootSymbol, 
//      boolean isScaling, boolean isLogProb,
//      String ioOptStr, String decodeOptStr, String measureString){
//    preInit(rootSymbol, isScaling, isLogProb, ioOptStr, decodeOptStr, measureString);
//    init(br);
//    postInit(rootSymbol);
//  }
   
  public EarleyParser(Grammar grammar, EdgeSpace edgeSpace, BaseLexicon lex, RuleSet ruleSet, 
  		Index<String> parserWordIndex, Index<String> parserTagIndex, Map<Integer, Integer> parserNonterminalMap,
  		Operator operator, Set<String> outputMeasures, Set<String> internalMeasures, boolean isSeparateRuleInTrie) {
  	this.grammar = grammar;
  	this.edgeSpace = edgeSpace;
  	this.lex = lex;
  	this.ruleSet = ruleSet;
  	this.parserWordIndex = parserWordIndex;
  	this.parserTagIndex = parserTagIndex;
  	this.parserNonterminalMap = parserNonterminalMap;
  	this.operator = operator;
  	this.outputMeasures = outputMeasures;
  	this.internalMeasures = internalMeasures;
  	this.isSeparateRuleInTrie = isSeparateRuleInTrie;
  	
  	// root
   	startEdge = edgeSpace.indexOf(EarleyParserOptions.rootRule.getEdge());
   	goalEdge = edgeSpace.to(startEdge);
   	
   	// sizes
   	edgeSpaceSize = edgeSpace.size();
   	numCategories = parserTagIndex.size();

   	// flags 
   	hasMultiTerminalRule = ruleSet.hasMultiTerminalRule();
   	hasFragmentRule = (ruleSet.numFragmentRules()>0);

    // inside-outside
    if(EarleyParserOptions.insideOutsideOpt>0){
      expectedCounts = new HashMap<Integer, Double>();
    }
    
    // note this initialization should be at the very bottom so that all components of the parser has been initialized
    if(EarleyParserOptions.decodeOpt == 1){
      decoder = new ViterbiDecoder(this, verbose);
    } else if(EarleyParserOptions.decodeOpt == 2){
      decoder = new MarginalDecoder(this, verbose);
    }

    // debug info
    if(verbose>=2){
   		System.err.println("# Start edge " + edgeSpace.get(startEdge).toString(parserTagIndex, parserWordIndex));
   		System.err.println("# Goal edge " + edgeSpace.get(goalEdge).toString(parserTagIndex, parserWordIndex));
   	}   	
  }

  
  /**********************/
  /** Abstract methods **/
  /**********************/
  protected abstract void addToChart(int left, int right, int edge, 
      double forward, double inner);
  
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
  public abstract Set<Integer> listInsideEdges(int left, int right);
  
  // outside
  protected abstract boolean containsOutsideEdge(int left, int right, int edge);
  protected abstract int outsideChartCount(int left, int right);
  public abstract Set<Integer> listOutsideEdges(int left, int right);
  protected abstract void initOuterProbs();
  
  // tmp predict probabilities
  protected abstract void chartPredict(int left, int right);
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
  protected abstract boolean isForwardCellEmpty(int left, int right);
  protected abstract double getForwardScore(int left, int right, int edge);
  protected abstract void addForwardScore(int left, int right, int edge, double score);
  
  
  // inner probabilities
  public abstract double getInnerScore(int left, int right, int edge);
  protected abstract void addInnerScore(int left, int right, int edge, double score);
  
  // outer probabilities
  public abstract double getOuterScore(int left, int right, int edge);
  protected abstract void addOuterScore(int left, int right, int edge, double score);
  
  // debug
  public abstract String edgeScoreInfo(int left, int right, int edge);
  
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
    Map<String, BufferedWriter> measureWriterMap = new HashMap<String, BufferedWriter>();
    BufferedWriter decodeWriter = null;
    
    if(!outPrefix.equals("")) {
      for (String measure : outputMeasures) {
        measureWriterMap.put(measure, new BufferedWriter(new 
            FileWriter(outPrefix + "." + measure, true))); // append results
      }
      
    	if (!EarleyParserOptions.decodeOptStr.equals(""))
    	  decodeWriter = new BufferedWriter(new FileWriter(outPrefix + "." + EarleyParserOptions.decodeOptStr));
    }
    
    List<Double> sentLogProbs = new ArrayList<Double>();
    for (int i = 0; i < sentences.size(); i++) {
      String sentenceString = sentences.get(i);
      sentId = indices.get(i);

      if(verbose>=0){
        System.err.println("\n### Sent " + i + ": id=" + sentId + ", numWords=" + sentenceString.split("\\s+").length);
      }
      
      // parse sentence
      parseSentence(sentenceString);
      sentLogProbs.add(stringLogProbability(numWords));

      // output
      for (String measure : measureWriterMap.keySet()) {
        BufferedWriter measureWriter = measureWriterMap.get(measure);
        measureWriter.write("# " + sentId + "\n");
        Util.outputSentenceResult(sentenceString, measureWriter, measures.getSentList(measure));
      }
      
      if(decodeWriter != null){
        if(EarleyParserOptions.decodeOptStr.equalsIgnoreCase(EarleyParserOptions.VITERBI_OPT)){ // viterbi
          decodeWriter.write(decoder.getBestParse().toString() + "\n");
        }
        else if(!EarleyParserOptions.decodeOptStr.equals("")) {// marginal or socialmarginal
          expectedCounts = new HashMap<Integer, Double>();
          computeOutsideProbs();
           
          if (EarleyParserOptions.decodeOptStr.equalsIgnoreCase(EarleyParserOptions.SOCIALMARGINAL_OPT)){
            List<String> treeStrs = ((MarginalDecoder) decoder).socialMarginalDecoding();
            for(String treeStr : treeStrs){
              decodeWriter.write(treeStr + "\n");
            }
          } else {
            decodeWriter.write(decoder.getBestParse().toString() + "\n");
          }
        }
      }
    }
    
    // close
    for (String measure : measureWriterMap.keySet()) {
      BufferedWriter measureWriter = measureWriterMap.get(measure);
      measureWriter.close();
    }
    if(decodeWriter != null){
      decodeWriter.close();
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
//    if(hasFragmentRule && EarleyParserOptions.isScaling){
//      System.err.println("! Currently doesn't support scaling for fragment grammars");
//      return false;
//    }
    
    // start
    if(verbose>=0){
      if (words.size()<100){
        System.err.println("## Parsing sent " + sentId + ": " + words);
      }
     Timing.startTime();
    }
    this.words = words;
    
    // init
    sentInit();
    
    /******************************************************************************/
    /** Step (1): add start edge "" -> . ROOT and perform the initial prediction **/
    /******************************************************************************/
    addToChart(0, 0, startEdge, operator.one(), operator.one());
    if(verbose>=2){
    	dumpChart();
    }
    chartPredict(0); // start expanding from ROOT
    addToChart(0, 0, startEdge, operator.one(), operator.one()); // this is a bit of a hack needed because chartPredict(0) wipes out the seeded rootActiveEdge chart entry.
    addInnerScore(0, numWords, startEdge, operator.one()); // set inside score 1.0 for "" -> . ROOT
    if (isFastComplete){
      addActiveEdgeInfo(0, 0, startEdge);
    }
    if(verbose>=2){
    Timing.endTime("Done initializing!");
      dumpChart();
    }
//    if(internalMeasures.contains(Measures.ENTROPY)){
//      computeInitEntropy();
//    }
    /**********************************/
    /** Step (2): parse word by word **/
    /**********************************/
    for(int right=1; right<=numWords; right++){ // span [0, rightEdge] covers words 0, ..., rightEdge-1
      String word = words.get(right-1).word();
      
      /*********************/
      /** Step (2a): scan **/
      /*********************/
      scanWord(right);
      
      
      /*************************/
      /** Step (2b): complete **/
      /*************************/
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
      
      
      /************************/
      /** Step (2c): predict **/
      /************************/
      // predict all new active edges for further down the road
      if(verbose>=1){
        Timing.startTime(); 
      }
      chartPredict(right);
      if(verbose>=1){
        Timing.tick("# " + word + ", finished chartPredict"); 
      }
      
      
      /*************************************/
      /** Step (2d): output word measures **/
      /*************************************/
      outputWordMeasures(right);
      
      if(verbose>=0 && right%100==0){
        System.err.print(" (" + right + ") ");
      } 
    }

    if(verbose>=4){
      dumpChart();
      System.err.println(dumpInnerProb());
      System.err.println(dumpInsideChart());
    }
    
    /***************************************************/
    /** Step (3): compute outside probs (if required) **/
    /***************************************************/
    // inside-outside    
    double rootInnerScore = getInnerScore(0, numWords, goalEdge);
    if(EarleyParserOptions.insideOutsideOpt>0 && rootInnerScore>operator.zero()){ 
      computeOutsideProbs();
      
      if(verbose>=4){
        System.err.println(dumpOuterProb());
        System.err.println(dumpOutsideChart());
      }
    }
    
    // end
    if(verbose>=0){
     Timing.tick("Finished parsing sentence " + sentId + ". " + words + ". Num words = " + numWords + ", negLogProb=" + -stringLogProbability(numWords) + ".");
    }
    if (-stringLogProbability(numWords) == Double.NaN){
      System.err.print("! NegLogProb=NaN. Sentence " + sentId + ". " + words);
      System.exit(1);
    }
    return (rootInnerScore>operator.zero());
  }

  /**
   * Compute the initial entropy before seeing any input
   */
//  private void computeInitEntropy(){
//    if(verbose>=1){
//      System.err.print("# Compute init entropy ... ");
//    }
//    Set<Integer> edges = listInsideEdges(0, 0); // initial predicted edges
//    for (Integer edge : edges) {
//      double forwardProb = operator.getProb(getForwardScore(0, 0, edge));      
//      wordEntropy[0] -= forwardProb*SloppyMath.log(forwardProb, 2);
//    }
//    addMeasure(Measures.ENTROPY, 0, wordEntropy[0]);
//    if(verbose>=1){
//      System.err.println(" Done! Init entropy = " + wordEntropy[0]);
//    }
//  }
  
  /**
   * Output all measures for the current word
   * 
   * @param right
   */
  private void outputWordMeasures(int right){
    // string probs
    if(outputMeasures.contains(Measures.STRINGPROB)){
      double stringLogProbability = stringLogProbability(right);
      measures.setValue(Measures.STRINGPROB, right, Math.exp(stringLogProbability));
    }
    
    // prefix prob
    double prefixProbability = operator.getProb(wordPrefixScores[right]); //measures.getPrefix(right));
    double scaleFactor = 1; // other measures have been scaled in storeMeasures()
    if(EarleyParserOptions.isScaling){
      scaleFactor = operator.getProb(getScaling(0, right));
    }
    if(outputMeasures.contains(Measures.PREFIX)){
      measures.setValue(Measures.PREFIX, right, prefixProbability/scaleFactor);
    }
//    if(outputMeasures.contains(Measures.MULTI_PREFIX)){
//      measures.setValue(Measures.MULTI_PREFIX, right, operator.getProb(wordMultiPrefixScores[right])/scaleFactor);
//    }
//    if(outputMeasures.contains(Measures.PCFG_PREFIX)){
//      measures.setValue(Measures.PCFG_PREFIX, right, operator.getProb(wordPcfgPrefixScores[right])/scaleFactor);
//    }
    
    // surprisals
    if(outputMeasures.contains(Measures.SURPRISAL)){
      if(!EarleyParserOptions.isScaling){
//        double lastProbability = (right==1) ? 1.0 : operator.getProb(wordPrefixScores[right-1]);
        double lastProbability = operator.getProb(wordPrefixScores[right-1]);
        double prefixProbabilityRatio = prefixProbability / lastProbability;
        if (prefixProbabilityRatio > 1.0 + 1e-10){
          System.err.println("! Error: prefix probability ratio > 1.0: " + prefixProbabilityRatio);
          System.exit(1);
        }
        measures.setValue(Measures.SURPRISAL, right, -Math.log(prefixProbabilityRatio));
        
        // syntatic/lexical surprisals
//        double synPrefixProbability = getSynPrefixProb(right);
//        synSurprisalList.add(-Math.log(synPrefixProbability/lastProbability));
//        lexSurprisalList.add(-Math.log(prefixProbability/synPrefixProbability));
      } else {
        // note: prefix prob is scaled, and equal to P(w0..w_(right-1))/P(w0..w_(right-2))
        measures.setValue(Measures.SURPRISAL, right, -Math.log(prefixProbability));
      }
    }
    
    // entropy reduction
    if(outputMeasures.contains(Measures.ENTROPY_REDUCTION)){
      double lastEntropy = measures.getEntropy(right-1);
      double entropy = measures.getEntropy(right);
      measures.setValue(Measures.ENTROPY_REDUCTION, right, Math.max(0.0, lastEntropy - entropy));
    }    
    
    if(verbose>=1){
      System.err.println(Util.sprint(parserWordIndex, wordIndices.subList(0, right)));
      System.err.println("prefix: " + prefixProbability);
      for(String measure : outputMeasures){
        if(measures.getValue(measure, right)>0){
          System.err.println(measure + ": " + measures.getValue(measure, right));
        }
      }
    }
  } 
  
  /******************************/
  /********** SCANNING **********/
  /******************************/
  
  /**
   * Read in the next word and build the corresponding chart entries
   * word_(rightEdge-1) corresponds to the span [rightEdge-1, rightEdge]
   * 
   * @param right
   */
  public void scanWord(int right) { // 
    //initializeTemporaryProbLists();
    String word = words.get(right-1).word();
    wordInit(right);
    
    /** Scaling factors **/
    if(EarleyParserOptions.isScaling){ // scaling
      scalingMap.put(linear(right-1, right), operator.inverse(wordPrefixScores[right-1])); //measures.getPrefix(right-1)));
    }
    
    /** Handle normal rules **/
    Set<IntTaggedWord> iTWs = lex.tagsForWord(word);
    String status = "";
    int iW = parserWordIndex.indexOf(word, true);
    if(iTWs.size()==0 && ruleSet.hasSmoothRule()){
//      if(!hasFragmentRule || !edgeSpace.terminal2fragmentEdges.containsKey(iW)){ 
        // if there's not a fragment rule X -> . _w, then use all tags
        iTWs = new HashSet<IntTaggedWord>();
        for(int iT : ruleSet.getUnkPreterminals()){
          iTWs.add(new IntTaggedWord(iW, iT));
        }
//      }
      
      status = "unknown";
    }
    
    if(verbose>=1) System.err.println("# " + right + "\t" + word + ", " + status + " numTags=" + iTWs.size());
    for (IntTaggedWord itw : iTWs) { // go through each POS tag the current word could have
      // score
      double score = lex.score(itw); // log
      //Util.error(score>0, "! Lex score should be in log-form, but is " + score + ", rule " + itw);
      
      if(!EarleyParserOptions.isLogProb){
        score = Math.exp(score);
      }
       
      // scan
      scanning(right-1, right, itw.tag(), score);
    }

    
    /** Handle multi terminal rules **/
    if(hasMultiTerminalRule){
      for (int i = right-2; i >= 0; --i) {
        // find all rules that rewrite into word_i ... word_(right-1)
        Map<Integer, Double> valueMap = grammar.getRuleTrie().findAllMap(wordIndices.subList(i, right));
        if(valueMap != null){
        	if(verbose>=1) System.err.println("# Scanning multi-terminal rules: " + words.subList(i, right)	+ ", map " + valueMap);
          
          for (int key : valueMap.keySet()) {
            int iT = -1 ;
            if(isSeparateRuleInTrie){ // key is ruleId
              iT = ruleSet.get(key).getMother();
            } else { // key is tagId
              iT = key;
            }
            
            // score
            double score = valueMap.get(key);
            
            // scanning
            scanning(i, right, iT, score);                      
          }
          
        }
      }
    } // end has multi-terminal rules
    
    /** Handle fragment rules **/
    if(hasFragmentRule){
      fragmentScanning(right);
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
  	if(verbose>=1) System.err.println("# Scanning [" + left + ", " + right + "]: " + parserTagIndex.get(tag) + "->" + words.subList(left, right) + " : " + operator.getProb(score));
    
    // scaling
    double inner = score;
    if(EarleyParserOptions.isScaling){ 
      inner = operator.multiply(score, getScaling(left, right));
    }
    
    int edge = edgeSpace.indexOfTag(tag);
//    Util.error(edge==-1, "! edge = -1. Stop, tag=" + parserTagIndex.get(tag) + ".");
    // Util.error(edgeSpace.get(edge).numChildren()==0, "! edge " + edge + " has no child.");
    assert(edge!=-1 && edgeSpace.get(edge).numChildren()==0);
    
    // add terminal rule
    if(EarleyParserOptions.insideOutsideOpt>0){   
      Edge terminalEdge = new Edge(new TerminalRule(tag, wordIndices.subList(left, right)), right-left);
      Rule rule = terminalEdge.getRule();
      if(!ruleSet.contains(rule)){
        ProbRule probRule = new ProbRule(rule, operator.getProb(score));
        ruleSet.add(probRule);
   
        if(verbose>=3) System.err.println("Add ProbRule " + probRule.toString(parserTagIndex, parserWordIndex));
      }
      
      if (this instanceof EarleyParserSparse){
        edge = edgeSpace.addEdge(terminalEdge);
      }
    }

    // complete info
    if(EarleyParserOptions.insideOutsideOpt>0 || EarleyParserOptions.decodeOpt==2){
      addCompletedEdges(left, right, edge);
    }
    
    addToChart(left, right, edge, operator.zero(), inner); // forward score is zero because we don't care about forward score of a completed edge during completion
  }

  /**
   * Scan (right: left tag -> word_left ... word_(right-1) .)
   * 
   * @param left
   * @param right
   * @param edge
   * @param inner
   */
  protected void fragmentScanning(int right){
    Set<Integer> leftIndices = fragmentEdgeInfo.get(right-1).keySet();
    
    for (Integer left : leftIndices) {
    	if(verbose>=2) System.err.println("# Fragment scan [" + left + ", " + (right-1) + "] ... ");
      Set<Integer> fragmentEdges = fragmentEdgeInfo.get(right-1).get(left);

      for (Integer fragmentEdge : fragmentEdges) {
        fragmentScanning(left, right, fragmentEdge);
      }  
    }
    
    if(verbose>=2) System.err.println("Done fragment scan " + right);
  }
  
  private void fragmentScanning(int left, int right, int fragmentEdge){
    double forwardScore = getForwardScore(left, right-1, fragmentEdge);
    double innerScore = getInnerScore(left, right-1, fragmentEdge);
    
    int edge = fragmentEdge;
    Edge edgeObj = edgeSpace.get(edge);
    if(verbose>=2) System.err.println("# Fragment scanning [" + left + ", " + (right-1) + "]: " + edgeObj.toString(parserTagIndex, parserWordIndex));
    // scaling
    if(EarleyParserOptions.isScaling){ 
      forwardScore = operator.multiply(forwardScore, getScaling(right-1, right));
      innerScore = operator.multiply(innerScore, getScaling(right-1, right));
    }
    addPrefixProb(forwardScore, left, right-1, right, 
        operator.one(), operator.one(), edge, -1, EarleyParserOptions.FG);
    
    // advance to next position [left, nextRight]: X -> \alpha _y . \beta
    edge = edgeSpace.to(edge);
    edgeObj = edgeSpace.get(edge); //nextEdgeObj.getToEdge();
    if(edgeObj.numRemainingChildren()>0 && !edgeObj.isTagAfterDot(0)
        && right<numWords && edgeObj.getChildAfterDot(0) == wordIndices.get(right)){ // look ahead to see if any matching terminal
    	addToChart(left, right, edge, forwardScore, innerScore);
    	addFragmentEdgeInfo(left, right, edge);
    	right++;
    }
	  
	  // add nextEdgeObj [left, nextRight]: X -> \alpha _y . \beta into chart
	  if(verbose>=2) System.err.println("  add to chart: " + edgeInfo(left, right, edge));
	  
	  
	  if (edgeObj.numRemainingChildren()==0){ // completed: X -> \alpha Z .
	    if(EarleyParserOptions.insideOutsideOpt>0 || EarleyParserOptions.decodeOpt==2){
	      addCompletedEdges(left, right, edge);
	    }
	  } else if (isFastComplete){
	    addActiveEdgeInfo(left, right, edge);
	  }
	  addToChart(left, right, edge, forwardScore, innerScore);
  }
  
//  private void fragmentScanning(int left, int right, int fragmentEdge){
//    double forwardScore = getForwardScore(left, right-1, fragmentEdge);
//    double innerScore = getInnerScore(left, right-1, fragmentEdge);
//    
//    int nextRight = right;
//    int edge = fragmentEdge;
//    Edge edgeObj = edgeSpace.get(edge);
//    
//    if(verbose>=2) System.err.println("# Fragment scanning [" + left + ", " + right + "]: " + edgeObj.toString(parserTagIndex, parserWordIndex));
//    
//    while(nextRight<=numWords) {
//      // invariant: edgeObj [left, nextRight-1]: X -> \alpha . _y \beta and _y matches wordIndices.(nextRight-1)
//      
//      // scaling
//      if(EarleyParserOptions.isScaling){ 
//        forwardScore = operator.multiply(forwardScore, getScaling(nextRight-1, nextRight));
//        innerScore = operator.multiply(innerScore, getScaling(nextRight-1, nextRight));
//      }
//      addPrefixProb(forwardScore, left, nextRight-1, nextRight, 
//          operator.one(), operator.one(), edge, -1, EarleyParserOptions.FG);
//      
//      // advance to next position [left, nextRight]: X -> \alpha _y . \beta
//      edge = edgeSpace.to(edge);
//      edgeObj = edgeSpace.get(edge); //nextEdgeObj.getToEdge();
//      if(edgeObj.numRemainingChildren()==0 || edgeObj.isTagAfterDot(0)
//          || nextRight==numWords || edgeObj.getChildAfterDot(0) != wordIndices.get(nextRight)){ // look ahead to see if any matching terminal
//        break;
//      } 
//      
//      // there's a next terminal that matches
//      nextRight++;
//    }  
//    
//    // add nextEdgeObj [left, nextRight]: X -> \alpha _y . \beta into chart
//    if(verbose>=2) System.err.println("  add to chart: " + edgeInfo(left, nextRight, edge));
//    
//    
//    if (edgeObj.numRemainingChildren()==0){ // completed: X -> \alpha Z .
//      if(EarleyParserOptions.insideOutsideOpt>0 || EarleyParserOptions.decodeOpt==2){
//        addCompletedEdges(left, nextRight, edge);
//      }
//    } else if (isFastComplete){
//      addActiveEdgeInfo(left, nextRight, edge);
//    }
//    addToChart(left, nextRight, edge, forwardScore, innerScore);
//    
//  }
  
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
      
      if(verbose>=2) System.err.println("\n# Predict all [" + left + "," + right + "]: " +  "chart count=" + insideChartCount(left, right));
      
      flag = true;
//      chartPredict(left, right);
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
    Prediction[] predictions = grammar.getPredictions(edge);
    if (verbose >= 2 && predictions.length>0) {
      System.err.println("# From edge " + edgeScoreInfo(left, right, edge));
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
      if(isFastComplete){
//        assert(edgeSpace.get(newEdge).numChildren()>0 && edgeSpace.get(newEdge).getDot()==0);
        addActiveEdgeInfo(right, right, newEdge);
      }
      
      // fragment edges
      if(hasFragmentRule && right<numWords){
        Edge nextEdgeObj = edgeSpace.get(newEdge);
//        assert(nextEdgeObj.numChildren()>0 && nextEdgeObj.getDot()==0);
        
        // after dot is a matching terminal
        if(!nextEdgeObj.isTagAfterDot(0) && nextEdgeObj.getChildAfterDot(0) == wordIndices.get(right)){
          addFragmentEdgeInfo(right, right, newEdge);
        }
      }
      
      if(verbose>=3) System.err.println("  to " + edgeScoreInfo(right, right, newEdge, newForwardProb, newInnerProb));
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
    
    if(verbose>=3) System.err.println("# Add completed edge " + edgeInfo(left, right, edge));
  }
  
  public void addActiveEdgeInfo(int left, int right, int edge){
    if(!activeEdgeInfo.get(right).containsKey(edge)){
      activeEdgeInfo.get(right).put(edge, new HashSet<Integer>());
    }
    activeEdgeInfo.get(right).get(edge).add(left);
    
    if(verbose>=3) System.err.println("# Add active edge info " + edgeInfo(left, right, edge));
  }
  
  public void addFragmentEdgeInfo(int left, int right, int edge){
    if(!fragmentEdgeInfo.get(right).containsKey(left)){
      fragmentEdgeInfo.get(right).put(left, new HashSet<Integer>());
    }
    fragmentEdgeInfo.get(right).get(left).add(edge);
    
    if(verbose>=2) System.err.println("# Add fragment edge info " + edgeInfo(left, right, edge));
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
    
    storeMeasures(right);
    if(verbose>=3){
      dumpChart();
    }
  }
  
  protected void cellComplete(int left, int middle, int right){
    // init
    initCompleteTmpScores();
    
    if(insideChartCount(middle, right)>0){
      // there're active edges for the span [middle, right]
    	if(verbose>=2) System.err.println("\n# Complete all [" + left + "," + middle + "," + right + "]: insideChartCount[" + middle + "," + right + "]=" + insideChartCount(middle, right));
      
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
    if(hasMultiTerminalRule && !isForwardCellEmpty(left, middle)){
    	if(verbose>=2) System.err.println("# handle multiterminal rules " + left + ", " + middle + ", " + right);
      Map<Integer, Double> valueMap = grammar.getRuleTrie().findAllPrefixMap(wordIndices.subList(middle, right));
      
      if(valueMap != null){
        if(verbose >= 2){
          agPrefixInfo(middle, right, valueMap);
        }
        
        handleMultiTerminalRules(left, middle, right, valueMap);
      }
    }
    
    /** Handle fragment rules **/
//    if(hasFragmentRule && middle==(right-1)){
//      fragmentComplete(left, right);
//    }
    
    // completions yield edges: right: left X -> \alpha Y . \beta
    storeCompleteTmpScores(left, right);
  }
  
  private void agPrefixInfo(int middle, int right, Map<Integer, Double> valueMap){
    System.err.print("# AG prefix " + "[" + middle + ", " + right + "]: " + 
        Util.sprint(parserWordIndex, wordIndices.subList(middle, right)) + ": ");
    
    if(isSeparateRuleInTrie){
      System.err.println(Util.sprint(valueMap, ruleSet, parserTagIndex, parserWordIndex));
    } else {
      System.err.println(Util.sprint(valueMap, parserTagIndex));
    }
  }
  
  protected Map<Integer, Double> getPrefixMap(List<Integer> indices){
    Map<Integer, Double> valueMap = grammar.getRuleTrie().findAllPrefixMap(indices);
    
    if(valueMap != null){
      if(isSeparateRuleInTrie){
        Map<Integer, Double> newValueMap = new HashMap<Integer, Double>();
        for (Entry<Integer, Double> entry : valueMap.entrySet()) { // pair of rule id and score
          newValueMap.put(ruleSet.getMother(entry.getKey()), entry.getValue());
        }
        valueMap = newValueMap;
      }
    }
    
    return valueMap;
  }
  
  protected void fastChartComplete(int right){
    for (int middle = right - 1; middle >= 0; middle--) {
      int mrIndex = linear(middle, right);
      
      // list of nextEdge right: middle Y -> v .
      if(completedEdges.containsKey(mrIndex)){
      	if(verbose>=3) System.err.println("\n# Complete all [" + middle + "," + right + "]: insideChartCount["  + middle + "," + right + "]=" + completedEdges.get(mrIndex).size());
        
        Integer[] copyEdges = completedEdges.get(mrIndex).toArray(new Integer[0]);
        for(int nextEdge : copyEdges){
          fastCellComplete(middle, right, nextEdge); 
        }
      }
      
      // could be made faster
      /** Handle multi-terminal rules **/
      if(hasMultiTerminalRule){
        Map<Integer, Double> valueMap = grammar.getRuleTrie().findAllPrefixMap(wordIndices.subList(middle, right));
        
        if(valueMap != null){
          if(verbose >= 2){
            agPrefixInfo(middle, right, valueMap);
          }
          for(int left=middle; left>=0; left--){
            if(isForwardCellEmpty(left, middle)){
              continue;
            }
            handleMultiTerminalRules(left, middle, right, valueMap);
          }
        }
      }
    }
    
    storeMeasures(right);
    if(verbose>=3){
      dumpChart();
    }
  }
  
  protected void fastCellComplete(int middle, int right, int nextEdge){
    // next edge, right: middle Y -> v .
    int tag = edgeSpace.get(nextEdge).getMother(); // Y
    
    // set of completions X -> \alpha . Z \beta
    Completion[] completions = grammar.getCompletions(tag);

    if(completions.length>0){ // there exists a completion
      double inner = getInnerScore(middle, right, nextEdge);
      if(verbose>=3) System.err.println(completionInfo(middle, right, nextEdge, inner, completions));
 
      for (Completion completion : completions) { // go through all completions we could finish
        int prevEdge = completion.activeEdge; // X -> \alpha . Z \beta

        if(activeEdgeInfo.containsKey(middle) && activeEdgeInfo.get(middle).containsKey(prevEdge)){
        	if(verbose>=3) System.err.println("Left for [*, " + middle + "] " + edgeSpace.get(prevEdge).toString(parserTagIndex, parserWordIndex) + ":  " + activeEdgeInfo.get(middle).get(prevEdge));
          
          
          for(int left : activeEdgeInfo.get(middle).get(prevEdge)){ // middle : left X -> \alpha . Z \beta
            assert(edgeSpace.get(prevEdge).numRemainingChildren()>0);
            /* add/update newEdge right: left X -> \alpha Z . \beta */
            double updateScore = operator.multiply(completion.score, inner);
            double newForwardScore = operator.multiply(
                getForwardScore(left, middle, prevEdge), updateScore);
            double newInnerScore = operator.multiply(
                getInnerScore(left, middle, prevEdge), updateScore);
            int newEdge = edgeSpace.to(completion.activeEdge);

            addInnerScore(left, right, newEdge, newInnerScore);
            addForwardScore(left, right, newEdge, newForwardScore);
            if(this instanceof EarleyParserDense && !containsInsideEdge(left, right, newEdge)){
              ((EarleyParserDense) this).updateChartCountEntries(left, right, newEdge);
            }
            
            // complete info
            Edge newEdgeObj = edgeSpace.get(newEdge);
            if (newEdgeObj.numRemainingChildren()==0){ // completed: X -> \alpha Z .
              assert(newEdge==goalEdge || left<middle); // alpha not empty
              if(EarleyParserOptions.insideOutsideOpt>0 || EarleyParserOptions.decodeOpt==2){
                addCompletedEdges(left, right, newEdge);
              }
            } else if (isFastComplete){
              addActiveEdgeInfo(left, right, newEdge);
            }
            
            // Viterbi: store backtrack info
            if(EarleyParserOptions.decodeOpt==1){
              addBacktrack(left, middle, right, nextEdge, newEdge, newInnerScore);
            }

            if(hasFragmentRule){ 
              // newEdgeObj: [left, right] X -> \alpha . _y \beta
              if(newEdgeObj.numRemainingChildren()>0 && !newEdgeObj.isTagAfterDot(0) 
                  && right<numWords && newEdgeObj.getChildAfterDot(0)==wordIndices.get(right)){
                addFragmentEdgeInfo(left, right, newEdge);
              }
            }

            if (verbose >= 3) {
              System.err.println("  start " + edgeScoreInfo(left, middle, completion.activeEdge) 
                  + " -> new " + edgeScoreInfo(left, right, newEdge, newForwardScore, newInnerScore));

              if (isGoalEdge(newEdge)) {
                System.err.println("# String prob +=" + Math.exp(newInnerScore));
              }
            }
            
            // also a careful addition to the prefix probabilities -- is this right?
            if (middle == right - 1) {
              addPrefixProb(newForwardScore, left, middle, right, inner, 
                  completion.score, completion.activeEdge, -1, EarleyParserOptions.PCFG);  
            }
          } // end for left
          
          
        } // end if completeInfo
      } // end for completion
    } // end if completions.length
  }
  
  protected void handleMultiTerminalRules(int left, int middle, int right, Map<Integer, Double> valueMap){
    for(Entry<Integer, Double> entry : valueMap.entrySet()){
      int tag = -1; 
      int ruleId = -1;
      if(isSeparateRuleInTrie){
        ruleId = entry.getKey();
        tag = ruleSet.getMother(ruleId);
      } else {
        tag = entry.getKey();
      }
      int edge = edgeSpace.indexOfTag(tag);
      double score = entry.getValue();
      addPrefixMultiRule(left, middle, right, edge, ruleId, score);
    }
  }
  
    
  protected void complete(int left, int middle, int right, int nextEdge, double inner) {
    // next edge, right: middle Y -> v .
    int tag = edgeSpace.get(nextEdge).getMother();
    assert(edgeSpace.get(nextEdge).numRemainingChildren()==0);
    
    // set of completions X -> \alpha . Z \beta
    Completion[] completions = grammar.getCompletions(tag);
    
    if (verbose>=3 && completions.length>0){
      System.err.println(completionInfo(middle, right, nextEdge, inner, completions));
    }
    
    for (Completion completion : completions) { // go through all completions we could finish
      if (containsInsideEdge(left, middle, completion.activeEdge)) { // middle: left X -> \alpha . Z \beta
        double updateScore = operator.multiply(completion.score, inner);
        double newForwardScore = operator.multiply(
            getForwardScore(left, middle, completion.activeEdge), updateScore);
        double newInnerScore = operator.multiply(
            getInnerScore(left, middle, completion.activeEdge), updateScore);
        int newEdge = edgeSpace.to(completion.activeEdge);
        
        // add edge, right: left X -> _ Z . _, to tmp storage
        initCompleteTmpScores(newEdge);
        addCompleteTmpForwardScore(newEdge, newForwardScore);
        addCompleteTmpInnerScore(newEdge, newInnerScore);
    
        // for fragment rules: look ahead to see if there's any terminal matches on the right
        if(hasFragmentRule){ 
          Edge newEdgeObj = edgeSpace.get(newEdge);
          
          // newEdgeObj: [left, right] X -> \alpha . _y \beta
          if(newEdgeObj.numRemainingChildren()>0 && !newEdgeObj.isTagAfterDot(0) 
              && right<numWords && newEdgeObj.getChildAfterDot(0)==wordIndices.get(right)){
            addFragmentEdgeInfo(left, right, newEdge);
          }
        }
        
        // inside-outside info to help outside computation later or marginal decoding later
        if(EarleyParserOptions.insideOutsideOpt>0 || EarleyParserOptions.decodeOpt==2){
          Edge edgeObj = edgeSpace.get(newEdge);
          
          if(edgeObj.numRemainingChildren()==0){ // complete right: left X -> _ Y .
            addCompletedEdges(left, right, newEdge);
          }
        }
        
        // Viterbi: store backtrack info
        if(EarleyParserOptions.decodeOpt==1){
          addBacktrack(left, middle, right, nextEdge, newEdge, newInnerScore);
        }
        
        if (verbose >= 2) {
          System.err.println("# start " + edgeScoreInfo(left, middle, completion.activeEdge) 
              + " -> new " + edgeScoreInfo(left, right, newEdge, newForwardScore, newInnerScore));

          if (isGoalEdge(newEdge)) {
            System.err.println("# String prob +=" + Math.exp(newInnerScore));
          }
        }

        //also a careful addition to the prefix probabilities
        if (middle == right - 1) {
          addPrefixProb(newForwardScore, left, middle, right, inner,
              completion.score, completion.activeEdge, -1, EarleyParserOptions.PCFG);
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
  
  private void addPrefixProb(double prefixScore, int left, int middle, int right,  
      double inner, double completionScore, int prevEdge, int ruleId, int code){
    if(verbose>=2){
      String type = (code==EarleyParserOptions.PCFG) ? "PCFG" : ((code==EarleyParserOptions.AG) ? "AG" : "FG");
      if(code == EarleyParserOptions.AG){ 
        System.err.print("# add " + type + " measures [" + left + ", " + middle + ", " + right + "]");
        if(ruleId>=0) { // it can be -1 in handleMultiTerminalRules
          System.err.println(ruleSet.get(ruleId).getRule().toString(parserTagIndex, parserWordIndex));
        } else {
        	System.err.println();
        }
      } else {
        System.err.println("# add " + type + " measures: " + edgeInfo(left, middle, prevEdge));
      }
    }
    
//    double prefixProb = operator.getProb(prefixScore);
//    if(code==EarleyParserOptions.AG || code==EarleyParserOptions.FG){
//      wordMultiPrefixScores[right] = operator.add(wordMultiPrefixScores[right], prefixScore);
//    } else {
//      wordPcfgPrefixScores[right] = operator.add(wordPcfgPrefixScores[right], prefixScore);
//    }
    
    // prefix
    wordPrefixScores[right] = operator.add(wordPrefixScores[right], prefixScore);
    
    if(verbose>=2) System.err.println("  prefix " + operator.getProb(prefixScore) + " = "  + operator.getProb(getForwardScore(left, middle, prevEdge)) + " * completion " + operator.getProb(completionScore) + " * inner " + operator.getProb(inner));
    
    // entropy
//    if(internalMeasures.contains(Measures.ENTROPY)){ // expected value of log prob
//      double scaleFactor = 1.0;
//      if(EarleyParserOptions.isScaling){
//        scaleFactor = operator.getProb(getScaling(0, right));
//      }
//      wordEntropy[right] += -(prefixProb/scaleFactor)*SloppyMath.log(prefixProb/scaleFactor, 2);
//      
//      if(verbose>=2) System.err.println("  entropy " + -prefixProb*operator.getLogProb(prefixScore));
//    }
    
//    if(isSeparateRuleInTrie){
//      int newEdge = edgeSpace.to(prevEdge);
//      Edge edgeObj = edgeSpace.get(newEdge);
//      int numChildren = edgeObj.numChildren();
//      int numRemainChildren = edgeObj.numRemainingChildren();
//      if(code == EarleyParserOptions.FG){
//        updateMultiProbs(right, prefixProb, numChildren, numRemainChildren);
//        updateMultiCounts(right, 1, numChildren, numRemainChildren);
//      } else if (code == EarleyParserOptions.PCFG) {
//        updatePcfgProbs(right, prefixProb, numChildren, numRemainChildren);
//        updatePcfgCounts(right, 1, numChildren, numRemainChildren);
//      }
//      updateAllProbs(right, prefixProb, numChildren, numRemainChildren);
//    }    
  }

//  private void updateMultiProbs(int right, double prefixProb, int numChildren, int numRemainChildren){
//    // multi rhs length
//    if(internalMeasures.contains(Measures.MULTI_RHS_LENGTH)){
//      wordMultiRhsLength[right] += prefixProb * numChildren;
//    }
//    
//    // multi future length
//    if(internalMeasures.contains(Measures.MULTI_FUTURE_LENGTH)){
//      wordMultiFutureLength[right] += prefixProb * numRemainChildren;
//    }
//    
//    if(verbose>=2){
//      System.err.println("  " + Measures.MULTI_RHS_LENGTH + " " + df.format(prefixProb*numChildren)
//          + " = prob " + df.format(prefixProb) + " * measure " + numChildren);
//      System.err.println("  " + Measures.MULTI_FUTURE_LENGTH + " " + df.format(prefixProb*numRemainChildren)
//          + " = prob " + df.format(prefixProb) + " * measure " + numRemainChildren);
//    }
//  }
//  
//  private void updatePcfgProbs(int right, double prefixProb, int numChildren, int numRemainChildren){
//    // pcfg future length
//    if(internalMeasures.contains(Measures.PCFG_FUTURE_LENGTH)){
//      wordPcfgFutureLength[right] += prefixProb * numRemainChildren;
//    }
//    
//    if(verbose>=2) System.err.println("  " + Measures.PCFG_FUTURE_LENGTH + " " + df.format(prefixProb*numRemainChildren)
//          + " = prob " + df.format(prefixProb) + " * measure " + numRemainChildren);
//  }
//  
//  private void updateAllProbs(int right, double prefixProb, int numChildren, int numRemainChildren){
//    //  future length
//    if(internalMeasures.contains(Measures.ALL_FUTURE_LENGTH)){
//      wordAllFutureLength[right] += prefixProb * numRemainChildren;
//    }
//    
//    if(verbose>=2) System.err.println("  " + Measures.ALL_FUTURE_LENGTH + " " + df.format(prefixProb*numRemainChildren)
//          + " = prob " + df.format(prefixProb) + " * measure " + numRemainChildren);
//  }
//  
//  private void updateMultiCounts(int right, int ruleCount, int numChildren, int numRemainChildren){
//    // multi rule count
//    if(internalMeasures.contains(Measures.MULTI_RULE_COUNT)){
//      wordMultiRuleCount[right] += ruleCount;
//    }
//    
//    // multi rhs length count
//    if(internalMeasures.contains(Measures.MULTI_RHS_LENGTH_COUNT)){
//      wordMultiRhsLengthCount[right] += ruleCount*numChildren;
//    }
//    
//    // multi future length count
//    if(internalMeasures.contains(Measures.MULTI_FUTURE_LENGTH_COUNT)){
//      wordMultiFutureLengthCount[right] += ruleCount*numRemainChildren;
//    }
//    
//    if(verbose>=2){
//      System.err.println("  " + Measures.MULTI_RULE_COUNT + " measure " + ruleCount);
//      System.err.println("  " + Measures.MULTI_RHS_LENGTH_COUNT + " measure " + ruleCount + " * " + numChildren);
//      System.err.println("  " + Measures.MULTI_FUTURE_LENGTH_COUNT + " measure " + ruleCount + " * " + numRemainChildren);
//    }
//  }
//
//  private void updatePcfgCounts(int right, int ruleCount, int numChildren, int numRemainChildren){
//    // pcfg rule count
//    if(internalMeasures.contains(Measures.PCFG_RULE_COUNT)){
//      wordPcfgRuleCount[right] += ruleCount;
//    }
//    // pcfg future length count
//    if(internalMeasures.contains(Measures.PCFG_FUTURE_LENGTH_COUNT)){
//      wordPcfgFutureLengthCount[right] += ruleCount*numRemainChildren;
//    }
//    if(verbose>=2) System.err.println("  " + Measures.PCFG_RULE_COUNT + " measure " + ruleCount);
//  }
  
  protected void addPrefixMultiRule(int left, int middle, int right, int edge, int ruleId, double inner) {    
    int tag = edgeSpace.get(edge).getMother();
    assert(edgeSpace.get(edge).numRemainingChildren()==0);
    
    Completion[] completions = grammar.getCompletions(tag);
    
    if (verbose>=2 && completions.length>0){
      System.err.println(completionInfo(middle, right, edge, inner, completions));
    }
   
    if (EarleyParserOptions.isScaling){
      inner = operator.multiply(inner, getScaling(middle, right));
    }
    
    // the current AG rule: Y -> w_middle ... w_(right-1) .
//    int count = 0;
//    double totalPrefixProb = 0.0;
    for (int x = 0, n = completions.length; x < n; x++) {
      Completion completion = completions[x];
     
      if (containsInsideEdge(left, middle, completion.activeEdge)){
        // we are using trie, and there's an extended rule that could be used to update prefix prob
        double prefixScore = operator.multiply(getForwardScore(left, middle, completion.activeEdge), 
                               operator.multiply(completion.score, inner));
        
        addPrefixProb(prefixScore, left, middle, right, inner, 
            completion.score, completion.activeEdge, ruleId, EarleyParserOptions.AG);
//        count++;
//        totalPrefixProb += operator.getProb(prefixScore);
      }
    }
    
    // update multi rule counts
//    if(isSeparateRuleInTrie){
//	    Rule rule = ruleSet.get(ruleId).getRule();
//	    int numChildren = rule.numChildren();
//	    int numRemainChildren = numChildren - (right-middle);
//	    updateMultiProbs(right, totalPrefixProb, numChildren, numRemainChildren);
//	    updateMultiCounts(right, count, numChildren, numRemainChildren);
//    }
  } 
  
  public boolean hasParse(){
    return (numWords>0 && sentLogProb()>Double.NEGATIVE_INFINITY);
  }
  
  
  /*****************************/
  /********** OUTSIDE **********/
  /*****************************/
  public void computeOutsideProbs(){
    double rootInnerScore = getInnerScore(0, numWords, goalEdge);
    assert(rootInnerScore>operator.zero());
    
    if(verbose>0){
      System.err.println("# EarleyParser: computeOutsideProbs");
    }
    initOuterProbs();
    computeOutsideProbs(rootInnerScore);
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
    Timing.startDoing("# Computing outside probabilities ...");
    }
    

    // assign prob 1.0 to "" -> ROOT .
    addOuterScore(0, numWords, goalEdge, operator.one());
      
    // start recursive outside computation
    outside(0, numWords, goalEdge, rootInnerScore, verbose); //4);
    
    if(verbose>=1){
     Timing.endDoing();
    }
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
    	if(verbose>=1) System.err.println("# Outside length " + length + "");
      
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
          //System.err.println(edge + "\t" + goalEdge + "\t" + edgeObj.toString(parserTagIndex, parserWordIndex));
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
            
            if(verbose>=3) System.err.println("## " + outsideInfo(left, right, edge));
            
            int prevTag = edgeObj.getChild(edgeObj.getDot()-1); // Z
            Edge prevEdgeObj = edgeObj.getPrevEdge(); // X -> \beta . Z \alpha
            int prevEdge = edgeSpace.indexOf(prevEdgeObj);
            if(verbose>=3) System.err.println("  prev edge " + prevEdgeObj.toString(parserTagIndex, parserWordIndex));
            
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
    if(verbose>=3) System.err.println("  left inside [" + left + ", " + middle + "] "  + operator.getProb(leftInside));
    
    int mrIndex = linear(middle, right);
    
    // for fragment rules, cell [middle][right] might be empty
    if(!completedEdges.containsKey(mrIndex)){ // X -> \alpha _y . \beta due to fragment rules 
//    	Util.error(middle != (right-1) || !hasFragmentRule, "! outside: empty completed edges for [middle, right] = [" + middle + ", " + right + "]");
      
      // outside to the left: X -> \alpha . _y \beta
      double rightInside = operator.one();
      if(EarleyParserOptions.isScaling){
        rightInside = getScaling(middle, right);
      }
      double unaryClosureScore = operator.one();
      double leftOutsideScore = operator.multiply(operator.multiply(parentOutside, rightInside), unaryClosureScore);
      if(leftOutsideScore>operator.zero()){
        outsideUpdate(left, middle, prevEdge, leftOutsideScore, rootInsideScore, verbose);
      }
    } else {
      for(int nextEdge : completedEdges.get(mrIndex)){ // Y -> v .
        Edge nextEdgeObj = edgeSpace.get(nextEdge);
        int nextTag = nextEdgeObj.getMother(); // Y
        double unaryClosureScore = grammar.getUnaryClosures().get(prevTag, nextTag);
        
        if(unaryClosureScore > operator.zero()) { // positive R(Z -> Y)
          double rightInside = getInnerScore(middle, right, nextEdge);
          
          if(verbose>=3) System.err.println("    next edge [" + middle + ", " + right + "] " + nextEdgeObj.toString(parserTagIndex, parserWordIndex) + ", right inside " + operator.getProb(rightInside) + ", unary(" + parserTagIndex.get(prevTag) + "->" + parserTagIndex.get(nextTag) + ")=" + operator.getProb(unaryClosureScore));
          
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
    } // end if fragment rules
  }
  
  protected void outsideUpdate(int left, int right, int edge, double outsideScore, double rootInsideScore, int verbose){
    addOuterScore(left, right, edge, outsideScore);
    if(verbose>=3) System.err.println("      after adding " + operator.getProb(outsideScore) +  ", " + outsideInfo(left, right, edge));
    
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
      double prevClosureScore = grammar.getUnaryClosures().get(prevTag, unaryMotherTag); // R(Z=>Z')
      double nextClosureScore = grammar.getUnaryClosures().get(unaryChildTag, nextTag); // R(Y'=>Y)
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
  
  /*****************************/
  /********** SCALING **********/
  /*****************************/
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
  public int linear(int left, int right){
    return (right+1)*right/2 + right-left;
  }
  
  /**
   * Initialization for every sentence
   */
  protected void sentInit(){
  	if(verbose>=2) System.err.println("# EarleyParser initializing ... ");
    
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
    
    measures = new Measures(outputMeasures, numWords); // store various objective values for each word in a sentence
    measures.addMeasures(internalMeasures, numWords);
    
    if (EarleyParserOptions.isScaling){
      scalingMap = new HashMap<Integer, Double>();
    }
    
    // init prefix prob
    //measures.setPrefix(0, operator.one());
//    wordPrefixScores = new double[numWords+1];
//    for (int i = 0; i < wordPrefixScores.length; i++) {
//      wordPrefixScores[i] = operator.zero();
//    }
//    wordPrefixScores[0] = operator.one();
    
    /** init individual word measures **/
//    wordEntropy = new double[numWords+1];
//    wordMultiRuleCount = new double[numWords+1]; 
//    wordMultiRhsLengthCount = new double[numWords+1];
//    wordMultiFutureLengthCount = new double[numWords+1];
//    wordPcfgFutureLengthCount = new double[numWords+1];
//    wordMultiRhsLength = new double[numWords+1];
//    wordMultiFutureLength = new double[numWords+1];
//    wordPcfgRuleCount = new double[numWords+1]; 
//    wordPcfgFutureLength = new double[numWords+1];
//    wordAllFutureLength = new double[numWords+1];
    
    wordPrefixScores = new double[numWords+1];
//    wordPcfgPrefixScores = new double[numWords+1];
//    wordMultiPrefixScores = new double[numWords+1];
    for (int i = 0; i <= numWords; i++) {
//      wordEntropy[i] = 0;
//      wordMultiRuleCount[i] = 0;
//      wordMultiRhsLengthCount[i] = 0;
//      wordMultiFutureLengthCount[i] = 0;
//      wordPcfgFutureLengthCount[i] = 0;
//      wordMultiRhsLength[i] = 0;
//      wordMultiFutureLength[i] = 0;
//      wordPcfgRuleCount[i] = 0;
//      wordPcfgFutureLength[i] = 0;
//      wordAllFutureLength[i] = 0;
      
      wordPrefixScores[i] = operator.zero();
//      wordPcfgPrefixScores[i] = operator.zero();
//      wordMultiPrefixScores[i] = operator.zero();
    }
    wordPrefixScores[0] = operator.one();
    
    // completion info
    if(EarleyParserOptions.insideOutsideOpt>0 || EarleyParserOptions.decodeOpt==2){
      completedEdges = new HashMap<Integer, Set<Integer>>();
    }
    if(isFastComplete){
      activeEdgeInfo = new HashMap<Integer, Map<Integer, Set<Integer>>>();
      for(int i=0; i<=numWords; i++)
        activeEdgeInfo.put(i, new HashMap<Integer, Set<Integer>>());
    }
    
    if(hasFragmentRule){
      fragmentEdgeInfo = new HashMap<Integer, Map<Integer, Set<Integer>>>();
      for(int i=0; i<=numWords; i++)
        fragmentEdgeInfo.put(i, new HashMap<Integer, Set<Integer>>());
    }
    // result lists
//    surprisalList = new ArrayList<Double>();
//    synSurprisalList = new ArrayList<Double>();
//    lexSurprisalList = new ArrayList<Double>();
//    stringLogProbList = new ArrayList<Double>();
    
    
    // Decode
    if(EarleyParserOptions.decodeOpt==1){ // Viterbi parse  
      backtrackChart = new HashMap<Integer, Map<Integer,BackTrack>>();
    }
  }
  
  /**
   * Initialization for every word
   */
  protected void wordInit(int right){
    // clear storage of the previous word
//    wordPrefixLists[right-1] = null;
//    wordMeasures[right-1] = null; 
  }
  
  protected void storeMeasures(int right) {    
    // prefix
    if(internalMeasures.contains(Measures.PREFIX)){
      addMeasure(Measures.PREFIX, right, operator.getProb(wordPrefixScores[right]));
    }
    
//    // multi prefix
//    if(internalMeasures.contains(Measures.MULTI_PREFIX)){
//      addMeasure(Measures.MULTI_PREFIX, right, operator.getProb(wordMultiPrefixScores[right]));
//    }
//    // pcfg prefix
//    if(internalMeasures.contains(Measures.PCFG_PREFIX)){
//      addMeasure(Measures.PCFG_PREFIX, right, operator.getProb(wordPcfgPrefixScores[right]));
//    }
//    
//    // entropy
//    if(internalMeasures.contains(Measures.ENTROPY)){ // expected value of log prob
//      addMeasure(Measures.ENTROPY, right, wordEntropy[right]);
//    }
//    
//    double scaleFactor = 1; // for prefix probs, we will scale in outputWordMeasures. entropy has been scaled in addPrefixProb
//    if(EarleyParserOptions.isScaling){
//      scaleFactor = operator.getProb(getScaling(0, right));
//    }
//    
//    // multi rhs length
//    if(internalMeasures.contains(Measures.MULTI_RHS_LENGTH)){
//      addMeasure(Measures.MULTI_RHS_LENGTH, right, wordMultiRhsLength[right]/scaleFactor);
//    }
//    
//    // multi future length
//    if(internalMeasures.contains(Measures.MULTI_FUTURE_LENGTH)){
//      addMeasure(Measures.MULTI_FUTURE_LENGTH, right, wordMultiFutureLength[right]/scaleFactor);
//    }
//    
//    // pcfg future length
//    if(internalMeasures.contains(Measures.PCFG_FUTURE_LENGTH)){
//      addMeasure(Measures.PCFG_FUTURE_LENGTH, right, wordPcfgFutureLength[right]/scaleFactor);
//    }
//    
//    // all future length
//    if(internalMeasures.contains(Measures.ALL_FUTURE_LENGTH)){
//      addMeasure(Measures.ALL_FUTURE_LENGTH, right, wordAllFutureLength[right]/scaleFactor);
//    }
//    
//    // multi rule count
//    if(internalMeasures.contains(Measures.MULTI_RULE_COUNT)){
//      double count = wordMultiRuleCount[right];
//      addMeasure(Measures.MULTI_RULE_COUNT, right, count);
//    
//      // multi rhs length count
//      if(internalMeasures.contains(Measures.MULTI_RHS_LENGTH_COUNT)){
//        if(count>0){
//          wordMultiRhsLengthCount[right] /= count;
//        }
//        addMeasure(Measures.MULTI_RHS_LENGTH_COUNT, right, wordMultiRhsLengthCount[right]);
//      }
//      
//      // multi future length count
//      if(internalMeasures.contains(Measures.MULTI_FUTURE_LENGTH_COUNT)){
//        if(count>0){
//          wordMultiFutureLengthCount[right] /= count;
//        }
//        addMeasure(Measures.MULTI_FUTURE_LENGTH_COUNT, right, wordMultiFutureLengthCount[right]);
//      }
//    }
//    
//    // pcfg rule count
//    if(internalMeasures.contains(Measures.PCFG_RULE_COUNT)){
//      double count = wordPcfgRuleCount[right];
//      addMeasure(Measures.PCFG_RULE_COUNT, right, count);
//      
//      // pcfg future length count
//      if(internalMeasures.contains(Measures.PCFG_FUTURE_LENGTH_COUNT)){
//        if(count>0){
//          wordPcfgFutureLengthCount[right] /= count;
//        }
//        addMeasure(Measures.PCFG_FUTURE_LENGTH_COUNT, right, wordPcfgFutureLengthCount[right]);
//      }
//    }
  }

  private void addMeasure(String measure, int right, double score){
    measures.setValue(measure, right, score);
    if(verbose>=1 && score>0){
      System.err.println("# sum " + measure + " [0," + right + "] " + score);
    }
  }
  /**
   * Returns log of the total probability of complete parses for the string prefix parsed so far.
   */
  public double stringLogProbability(int right) {
    double logProb = operator.getLogProb(getInnerScore(0, right, goalEdge));
  
    if(EarleyParserOptions.isScaling){
      logProb -= operator.getLogProb(getScaling(0, right));
    }
    
    return logProb;
  }
  
  public double sentLogProb(){
    return stringLogProbability(numWords);
  }
  
  protected boolean isGoalEdge(int edge){
    Edge edgeObj = edgeSpace.get(edge);
    return edgeObj.numRemainingChildren()==0 && edgeObj.getMother() == EarleyParserOptions.origSymbolIndex;
  }
  
  public void updateGrammar(){
    /* learn grammar */
  	if(verbose>=1) System.err.println("\n### Update grammar ... ");
    grammar = new Grammar(parserWordIndex, parserTagIndex, parserNonterminalMap, operator);
    grammar.learnGrammar(ruleSet, edgeSpace, isSeparateRuleInTrie);
  }

  
  /****************/
  /** Debug info **/
  /****************/
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
  
  public String edgeInfo(int left, int right, int edge){
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
    return "# Completed " + edge + " " + edgeInfo(middle, right, edge)  
    + ", inside=" + df.format(operator.getProb(inner))  
    + ", completions: " + Util.sprint(completions, edgeSpace, parserTagIndex, parserWordIndex, operator);
  }
  
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
        double scalingFactor = EarleyParserOptions.isScaling ? getScaling(left, right, type) : operator.one();
        
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
//    Util.error(!type.equalsIgnoreCase("outside") && !type.equalsIgnoreCase("inside"), 
//    		"! computeCatChart: Unknown chart type " + type);
  	
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
        double scalingFactor = EarleyParserOptions.isScaling ? getScaling(left, right, type) : operator.one();
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
    
  /** debug methods **/
  protected void dumpChart() {
    System.err.println("# Chart snapshot, edge space size = " + edgeSpaceSize);
    for(int length=0; length<=numWords; length++){ // length
      for (int left = 0; left <= numWords-length; left++) {
        int right = left+length;

        int count = insideChartCount(left, right);
        if(count>0){ // there're active states
          System.err.println("[" + left + "," + right + "]: " + count  
              + " (" + df1.format(count*100.0/edgeSpaceSize) + "%)");
          for (int edge : listInsideEdges(left, right)) {
            double forwardProb = getForwardScore(left, right, edge);
            double innerProb = getInnerScore(left, right, edge);
            
            if(EarleyParserOptions.isScaling){
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
  
  protected void dumpCompletedEdges() {
    System.err.println("# Completed edges, edge space size = " + edgeSpaceSize);
    for(int length=1; length<=numWords; length++){ // length
      for (int left = 0; left <= numWords-length; left++) {
        int right = left+length;

        if(completedEdges.containsKey(linear(left, right))){ // there're active states
          System.err.println("[" + left + "," + right + "]: " + 
              Util.sprint(completedEdges.get(linear(left, right)), edgeSpace
              , parserTagIndex, parserWordIndex));
        }
      }
    }
  }
  
  /**
   * Getters & Setters
   */  
  public int getNumWords() {
    return numWords;
  }
  public int getGoalEdge() {
    return goalEdge;
  }
  public Map<Integer, Double> getExpectedCounts() {
    return expectedCounts;
  }
  public void setExpectedCounts(Map<Integer, Double> expectedCounts) {
    this.expectedCounts = expectedCounts;
  }
  public Map<Integer, Map<Integer, BackTrack>> getBacktrackChart() {
    return backtrackChart;
  }
  public List<? extends HasWord> getWords() {
    return words;
  }
//  public int getInsideOutsideOpt() {
//    return insideOutsideOpt;
//  }
  public Operator getOperator() {
    return operator;
  }
  public EdgeSpace getEdgeSpace() {
    return edgeSpace;
  }
  public BaseLexicon getLex() {
    return lex;
  }
  public RuleSet getRuleSet() {
    return ruleSet;
  }
  public BaseLexicon getLexicon(){
    return lex;
  }
  public Grammar getGrammar(){
    return grammar;
  }
  public Index<String> getParserWordIndex() {
    return parserWordIndex;
  }
  public Index<String> getParserTagIndex() {
    return parserTagIndex;
  }
//  public ProbRule getRootRule() {
//    return rootRule;
//  }
  
  public List<ProbRule> getAllRules(){
    List<ProbRule> allRules = ruleSet.getAllRules();
    if(allRules.get(0).equals(EarleyParserOptions.rootRule)==true){ // remove root rule
      return allRules.subList(1, allRules.size());
    } else {
      return allRules;
    }
  }
  
  public List<Double> getMeasureList(String measure) {
    return measures.getSentList(measure);
  }
  
  public int getDecodeOpt() {
    return EarleyParserOptions.decodeOpt;
  }
  
  public boolean isHasFragmentRule() {
    return hasFragmentRule;
  }
  
  public List<Integer> getWordIndices() {
    return wordIndices;
  }
  
  public Measures getMeasures() {
		return measures;
	}
  
  public void setSentId(String sentId) {
		this.sentId = sentId;
	}
}

/** Unused code **/


// prefix: either in prob or log-prob domain
//double score = operator.zero();
//if(wordPrefixLists[right].size()==0){
//System.err.println("! storeMeasures: no paths go through word " + right + ", " + words.get(right-1).word());
//System.exit(1);
//}
//
//if(wordPrefixLists[right].size()>0){
//score = operator.arraySum(wordPrefixLists[right].toArray());
//}
//wordPrefixScores[right] = operator.add(wordPrefixScores[right], score);
//if(verbose>=1 && score>operator.zero()){
//System.err.print("# sum prefix [0," + right + "] " + operator.getProb(score));
//
//if(verbose>=2){
//  System.err.println(", list = " + Util.sprintProb(wordPrefixLists[right].toArray(), operator));
//} else{
//  System.err.println();
//}
//}

//public void labelRecalParsing(){
//for (int length = 2; length <= numWords; length++) {
//  for (int left = 0; left <= (numWords-length); left++) {
//    int right = left + length;
//    
//    // scaling
//    double scalingFactor = operator.one();
//    if (EarleyParserOptions.isScaling){
//      if (isOutsideProb){ // outside prob has a scaling factor for [0,left][right, numWords]
//        scalingFactor = operator.multiply(scalingMatrix[linear[0][left]], scalingMatrix[linear[right][numWords]]);
//      } else {
//        scalingFactor = scalingMatrix[linear(left, right)];
//      }
//    }
//
//    
//    double maxG = Double.NEGATIVE_INFINITY;
//    for (int tag = 0; tag < parserTagIndex.size(); tag++) {
//      
//    }
//    
//  }
//}
//}

//linear = new int[numWords+1][numWords+1];
// go in the order of CKY parsing
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

// inside-outside
//if(insideOutsideOpt==2){
//  addToInsideChart(left, right, tag, inner);
//}

//if(insideOutsideOpt==2){
//  addToInsideChart(left, right, edgeObj.getMother(), newInnerProb);
//}

//protected void fragmentComplete(int left, int right){
//  String word = words.get(right-1).word();
//  Set<Integer> fragmentEdges = edgeSpace.getFragmentEdges(parserWordIndex.indexOf(word));
//  int middle = right-1;
//  
//  if(fragmentEdges != null){ // there are fragment edges that start with the current word
//    Set<Integer> predictedEdges = listInsideEdges(left, right-1);
//    
//    if(predictedEdges.size()>0){ // non-empty cell
//      System.err.println("# [" + left + ", " + (right-1) + "]: " + Util.sprint(predictedEdges, edgeSpace, parserTagIndex, parserWordIndex));
//      System.err.println("Fragment: " + Util.sprint(fragmentEdges, edgeSpace, parserTagIndex, parserWordIndex));   
//      Set<Integer> intersectEdges = Util.intersection(predictedEdges, fragmentEdges);
//     
//      // Thang July 2013: potential BUG (maybe very minor). 
//      // For fragment edges in which the dot position is 0, we might need to consider 
//      // unary recursive rewriting rules similar to how we handle multi-terminal rules in addPrefixMultiRule()
//      if(intersectEdges.size()>0){
//        System.err.println("# " + word + "\t[" + left + ", " + (right-1) + 
//            "] " + ": " + Util.sprint(intersectEdges, edgeSpace, parserTagIndex, parserWordIndex));
//
//        int rhsLengthCount = 0;
//        int futureLengthCount = 0;
//        for (Integer edge : intersectEdges) {
//          fragmentComplete(left, right, edge);
//          
//          int newEdge = edgeSpace.to(edge);
//          Edge edgeObj = edgeSpace.get(newEdge);
//          rhsLengthCount += edgeObj.numChildren();
//          futureLengthCount += edgeObj.numRemainingChildren();
//        }
//      }
//    }
//  }
//}
//
//protected void fragmentComplete(int left, int right, int prevEdge){
//  // prevEdge: left: right-1 X -> . _y Z T
//  int middle = right-1;
//  double newForwardProb = getForwardScore(left, middle, prevEdge);
//  double newInnerProb = getInnerScore(left, middle, prevEdge);
//  
//  if (EarleyParserOptions.isScaling){
//    if(verbose>=2){
//      System.err.println("fragment complete scaling " + operator.multiply(newForwardProb, getScaling(middle, right)) 
//          + " = " + newForwardProb 
//          + ", scaled = " + getScaling(middle, right));
//    }
//    newForwardProb = operator.multiply(newForwardProb, getScaling(middle, right));
//    newInnerProb = operator.multiply(newInnerProb, getScaling(middle, right));
//  }
//  int newEdge = edgeSpace.to(prevEdge);
//  
//  // add edge, right: left X -> _ Z . _, to tmp storage
//  initCompleteTmpScores(newEdge);
//  addCompleteTmpForwardScore(newEdge, newForwardProb);
//  addCompleteTmpInnerScore(newEdge, newInnerProb);
//
//  // inside-outside info to help outside computation later or marginal decoding later
//  if(insideOutsideOpt>0 || decodeOpt==2){
//    Edge edgeObj = edgeSpace.get(newEdge);
//    
//    if(edgeObj.numRemainingChildren()==0){ // complete right: left X -> _ Y .
//      addCompletedEdges(left, right, newEdge);
//    }
//  }
//  
//  // Viterbi: store backtrack info
//  if(decodeOpt==1){
//    int nextEdge = -1;
//    addBacktrack(left, middle, right, nextEdge, newEdge, newInnerProb);
//  }
//  
//  if (verbose >= 3) {
//    System.err.println("  fragment start " + edgeScoreInfo(left, middle, prevEdge) 
//        + " -> new " + edgeScoreInfo(left, right, newEdge, newForwardProb, newInnerProb));
//
//    if (isGoalEdge(newEdge)) {
//      System.err.println("# fragment string prob +=" + Math.exp(newInnerProb));
//    }
//  }
//  
//  addPrefixProb(newForwardProb, left, right-1, right, 
//      operator.one(), operator.one(), prevEdge, -1, FG);  
//}

///** per word info **/
// to accumulate probabilities of all paths leading to a particular words
//protected Measures[] wordMeasures; // numWords+1, for each word, for each measure, store a list of values and sum up at the end  
//protected DoubleList[] wordPrefixLists; // numWords+1

///** current sentence info **/
//  protected int[][] linear; // convert matrix indices [left][right] into linear indices
// protected int numCells; // numCells==((numWords+2)*(numWords+1)/2)
