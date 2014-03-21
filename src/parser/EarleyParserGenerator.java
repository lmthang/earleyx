/**
 * 
 */
package parser;

import java.io.BufferedReader;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import util.LogProbOperator;
import util.Operator;
import util.ProbOperator;
import util.RuleFile;
import util.TreeBankFile;
import util.Util;
import base.BaseLexicon;
import base.BiasProbRule;
import base.FragmentRule;
import base.ProbRule;
import base.RuleSet;
import base.TerminalRule;
import edu.stanford.nlp.parser.lexparser.IntTaggedWord;
import edu.stanford.nlp.stats.Counter;
import edu.stanford.nlp.util.HashIndex;
import edu.stanford.nlp.util.Index;

/**
 * Factory class to generate multiple instances of the Earley Parser.
 * Grammar, lexicon, edge space, and rule set are constructed once.
 * (ruleSet, parserTagIndex, parserWordIndex, parserNonterminalMap, edgeSpace, grammar, lex)
 * 
 * @author lmthang
 *
 */

public class EarleyParserGenerator {
	public static int verbose = -1;
	
	private Grammar grammar;
  private EdgeSpace edgeSpace;
  private BaseLexicon lex;
  private RuleSet ruleSet;
  private Index<String> parserWordIndex;
  private Index<String> parserTagIndex;
  // keys are nonterminal indices (indexed by parserTagIndex)
  // values are used to retrieve nonterminals (when necessary) in the order that
  //   we processed them when loading treebank or grammar files (more for debug purpose)
  private Map<Integer, Integer> parserNonterminalMap; // doesn't include preterminals. nonterminal + preterminal = parser tag indices
  private Operator operator; // either ProbOperator or LogProbOperator
  private Set<String> outputMeasures; // output measures (surprisal, stringprob, etc.)
	private Set<String> internalMeasures; // internal measures (prefix, entropy, etc.)
  private boolean isSeparateRuleInTrie = false; // IMPORTANT: this one, by default should be false.
  
  public EarleyParserGenerator(String grammarFile, int inGrammarType, String rootSymbol, 
      boolean isScaling, boolean isLogProb, String ioOptStr, String decodeOptStr, String objString){
    preInit(rootSymbol, isScaling, isLogProb, ioOptStr, decodeOptStr, objString);
    
  	if(inGrammarType==1){ // grammar file
      init(Util.getBufferedReaderFromFile(grammarFile));
    } else if(inGrammarType==2){ // treebank file
      Collection<IntTaggedWord> intTaggedWords = new ArrayList<IntTaggedWord>();
      TreeBankFile.processTreebank(grammarFile, ruleSet, intTaggedWords, parserTagIndex, 
          parserWordIndex, parserNonterminalMap);
      
      // build edgeSpace
      edgeSpace.build(ruleSet.getTagRules());
      // build grammar
      grammar = new Grammar(parserWordIndex, parserTagIndex, parserNonterminalMap, operator);
      grammar.learnGrammar(ruleSet, edgeSpace, isSeparateRuleInTrie);
      // build lexicon
      lex = new SmoothLexicon(parserWordIndex, parserTagIndex);
      lex.train(intTaggedWords);
      
      // add terminal rules      
      Map<Integer, Counter<Integer>> tag2wordsMap = lex.getTag2wordsMap();
      for(int iT : tag2wordsMap.keySet()){
        Counter<Integer> counter = tag2wordsMap.get(iT);
        for(int iW : counter.keySet()){
          ruleSet.add(new ProbRule(new TerminalRule(iT, iW), 
              Math.exp(counter.getCount(iW))));
        }
      }
    } else {
      System.err.println("! Invalid grammarType " + inGrammarType);
      System.exit(1);
    }
  	
  	if(verbose>=2) System.err.println("# Num nonterminals " + parserNonterminalMap.size());
  	if(verbose>=2) System.err.println(Util.sprint(parserTagIndex, parserNonterminalMap.keySet()));
  }
  
  public EarleyParserGenerator(BufferedReader br, String rootSymbol, 
      boolean isScaling, boolean isLogProb,
      String ioOptStr, String decodeOptStr, String measureString){
    preInit(rootSymbol, isScaling, isLogProb, ioOptStr, decodeOptStr, measureString);
    init(br);
  }
  
  public EarleyParser getParserDense(){
  	return new EarleyParserDense(grammar, edgeSpace, lex, ruleSet, parserWordIndex, parserTagIndex, 
  			parserNonterminalMap, operator, outputMeasures, internalMeasures, isSeparateRuleInTrie); 
  }
  
  public EarleyParser getParserSparse(){
  	return new EarleyParserSparse(grammar, edgeSpace, lex, ruleSet, parserWordIndex, parserTagIndex, 
  			parserNonterminalMap, operator, outputMeasures, internalMeasures, isSeparateRuleInTrie); 
  }
  
  // preInit
  private void preInit(String rootSymbol, boolean isScaling, boolean isLogProb,
      String ioOptStr, String decodeOptStr, String measureString){
  	EarleyParserOptions.rootSymbol = rootSymbol;
  	EarleyParserOptions.isScaling = isScaling;
    EarleyParserOptions.isLogProb = isLogProb;
    EarleyParserOptions.decodeOptStr = decodeOptStr;
    
    // induction option
    if(ioOptStr.equalsIgnoreCase("em")){
    	EarleyParserOptions.insideOutsideOpt = 1;
    } else if(ioOptStr.equalsIgnoreCase("vb")){
    	EarleyParserOptions.insideOutsideOpt = 2;
    }
   
    // decode option
    if(EarleyParserOptions.decodeOptStr.equalsIgnoreCase(EarleyParserOptions.VITERBI_OPT)){
    	EarleyParserOptions.decodeOpt = 1;
    } else if(EarleyParserOptions.decodeOptStr.equalsIgnoreCase(EarleyParserOptions.MARGINAL_OPT) 
        || EarleyParserOptions.decodeOptStr.equalsIgnoreCase(EarleyParserOptions.SOCIALMARGINAL_OPT)){
    	EarleyParserOptions.decodeOpt = 2;
    }
    
    // output measures
    outputMeasures = new HashSet<String>();
    for(String measure : measureString.split("\\s*,\\s*")){
    	outputMeasures.add(measure);
    }    
    if (verbose>=0){
    	System.err.println("# output measures: " + outputMeasures);
    }

    // internal measures
    internalMeasures = new HashSet<String>();
    for(String measure : outputMeasures){
      if(measure.equals(Measures.STRINGPROB) || measure.equals(Measures.SURPRISAL)
          || measure.equals(Measures.PREFIX)){
        continue;
      }
      if(measure.equals(Measures.ENTROPY) || measure.equals(Measures.ENTROPY_REDUCTION)){
      	internalMeasures.add(Measures.ENTROPY);
      } else {
      	internalMeasures.add(measure);
      }
    }

    if(internalMeasures.size()>0){
    	isSeparateRuleInTrie = true;
    	if (verbose>=0){
	      System.err.println("# internal measures: " + internalMeasures);
	      System.err.println("# set isSeparateRuleInTrie to true");
    	}
    }
//    if(internalMeasures.contains(Measures.ENTROPY) && isScaling){
//      System.err.println("Can't use -scale option when \"entropy\" is part of the output measures");
//      System.exit(1);
//    }
    
    if(EarleyParserOptions.isLogProb){
    	operator = new LogProbOperator();
    } else {
    	operator = new ProbOperator();
    }
    
    // index
    parserWordIndex = new HashIndex<String>();
    parserTagIndex = new HashIndex<String>();
    parserNonterminalMap = new HashMap<Integer, Integer>();
    
    // root symbol
    EarleyParserOptions.origSymbolIndex = parserTagIndex.indexOf(EarleyParserOptions.ORIG_SYMBOL, true);
    EarleyParserOptions.rootSymbolIndex = parserTagIndex.indexOf(rootSymbol, true);
    parserNonterminalMap.put(EarleyParserOptions.origSymbolIndex, parserNonterminalMap.size());
    assert(parserNonterminalMap.get(EarleyParserOptions.origSymbolIndex) == 0);
    
    if(EarleyParserOptions.insideOutsideOpt==2){
    	EarleyParserOptions.rootRule = new BiasProbRule(new FragmentRule(EarleyParserOptions.origSymbolIndex, 
    			EarleyParserOptions.rootSymbolIndex, true), 1.0, 1.0);
    } else {
    	EarleyParserOptions.rootRule = new ProbRule(new FragmentRule(EarleyParserOptions.origSymbolIndex, 
    			EarleyParserOptions.rootSymbolIndex, true), 1.0);
    }
    
    // rules
    ruleSet = new RuleSet(parserTagIndex, parserWordIndex);
    ruleSet.add(EarleyParserOptions.rootRule);
    
    // edgespace
    if(EarleyParserOptions.insideOutsideOpt > 0 || EarleyParserOptions.decodeOpt > 0 
    		|| outputMeasures.contains(Measures.ENTROPY)){
      edgeSpace = new StandardEdgeSpace(parserTagIndex, parserWordIndex);
    } else {
      // edgespace
      // Earley edges having the same parent and expecting children 
      // (children on the right of the dot) are collapsed into the same edge X -> * . \\alpha.
      // This speeds up parsing time if we only care about inner/forward probs + surprisal values
      edgeSpace = new LeftWildcardEdgeSpace(parserTagIndex, parserWordIndex);
    }
  }
  
  //init
  private void init(BufferedReader br){    
   Map<Integer, Counter<Integer>> tag2wordsMap = new HashMap<Integer, Counter<Integer>>();
   Map<Integer, Set<IntTaggedWord>> word2tagsMap = new HashMap<Integer, Set<IntTaggedWord>>();
       
   try {
     if (EarleyParserOptions.insideOutsideOpt==2){
       RuleFile.parseRuleFile(br, 
           ruleSet, tag2wordsMap, word2tagsMap, parserNonterminalMap, parserWordIndex, parserTagIndex, true);
     } else {
       RuleFile.parseRuleFile(br, 
           ruleSet, tag2wordsMap, word2tagsMap, parserNonterminalMap, parserWordIndex, parserTagIndex, false);
     }
   } catch (IOException e) {
     System.err.println("! Problem initializing Earley parser");
     e.printStackTrace();
   }
//   System.err.println(ruleSet.toString(parserTagIndex, parserWordIndex));
   
   // convert to log prob
   for(int iT : tag2wordsMap.keySet()){
     Counter<Integer> counter = tag2wordsMap.get(iT);
     for (int iW : counter.keySet()) {
       double prob = counter.getCount(iW);
       if(prob<0 || prob>1.000001){ // make sure this is a proper prob
         System.err.println("! prob of " + parserTagIndex.get(iT) + "->" + parserWordIndex.get(iW) + " " + prob 
             + " not in [0, 1]");
         System.exit(1);
       }
       counter.setCount(iW, Math.log(prob));
     }
     // Counters.logInPlace(counter);
   }

   // build edgeSpace
   edgeSpace.build(ruleSet.getTagRules());   
   // build grammar
   grammar = new Grammar(parserWordIndex, parserTagIndex, parserNonterminalMap, operator);
   grammar.learnGrammar(ruleSet, edgeSpace, isSeparateRuleInTrie);
   
   buildLex(tag2wordsMap, word2tagsMap);
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

  public Set<String> getOutputMeasures() {
		return outputMeasures;
	} 
  
}
