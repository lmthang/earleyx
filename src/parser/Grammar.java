package parser;

import base.ClosureMatrix;
import base.ProbRule;
import base.RelationMatrix;
import base.RuleSet;
import cern.colt.matrix.DoubleMatrix2D;
import edu.stanford.nlp.util.Index;
import edu.stanford.nlp.util.Pair;
import edu.stanford.nlp.util.Timing;

import java.util.Collection;
import java.util.List;
import java.util.Map;

import util.LogProbOperator;
import util.Operator;
import util.TrieSurprisal;
import util.Util;

/**
 * A grammar to supply to construct an {@link EarleyParser}, which prebuilds:
 *   all active edges and hash them into integers, handled by StateSpace
 *   all prediction instances for each state i, handled by Prediction
 *   all completion instances for each state i, handled by Completion
 *   a rule trie that keep track of "extended" rules which rewrite non-terminals directly into a sequence of terminals.
 *    
 * @author Roger Levy
 * @author Minh-Thang Luong, 2012
 */
public class Grammar {
  public static int verbose = 0;

  // keep track of rules of type X -> a b c, where "a b c" is a key, sequence of string,
  // while a pair of X, state id, and rule score is a value associated to the key
  private TrieSurprisal ruleTrie;
  
  private Completion[][] completionsArray; // completions[i] is the set of Completion instances for the tag i
//  private Map<Integer, Completion[]> tag2completionsMap;
  private Prediction[][] predictionsArray; // predictions[i] is the set of Prediction instances for the state i

  private Index<String> wordIndex;
  private Index<String> tagIndex;
  private Map<Integer, Integer> nonterminalMap;

  private ClosureMatrix leftCornerClosures;
  private ClosureMatrix unaryClosures;
  private Operator operator;
  public Grammar(Index<String> wordIndex, Index<String> tagIndex, Map<Integer, Integer> nonterminals, Operator operator){
    this.wordIndex = wordIndex;
    this.tagIndex = tagIndex;
    this.nonterminalMap = nonterminals;
    this.operator = operator;
    
//    System.err.println("Grammar nonterminals: " + Util.sprint(nonterminalMap.keySet(), tagIndex));
    if(operator instanceof LogProbOperator){
      ruleTrie = new TrieSurprisal(true); // log prob
    } else {
      ruleTrie = new TrieSurprisal(false);
    }
      
  }
    
  /**
   * learns all the grammar stuff.  Note that rootRule must be a unary contained in rules.
   */
  public void learnGrammar(RuleSet ruleSet, EdgeSpace edgeSpace, boolean isSeparateRuleInTrie) {   
  	if(verbose>=0) System.err.println("\n### Learning grammar ... ");
  	
    /*** Compute reflective and transitive left-corner and unit-production matrices ***/
    RelationMatrix relationMatrix = new RelationMatrix(tagIndex);
    
    /* do left-corner closures matrix */
    DoubleMatrix2D pl = relationMatrix.getPL(ruleSet.getTagRules(), nonterminalMap);
    leftCornerClosures = new ClosureMatrix(pl, operator, tagIndex, "left-corner");
    leftCornerClosures.changeIndices(nonterminalMap);
    
    /* do unary closure matrix */
    DoubleMatrix2D pu = relationMatrix.getPU(ruleSet.getTagRules()); //, nontermPretermIndexer);
    unaryClosures = new ClosureMatrix(pu, operator, tagIndex, "unary");
    
    /*** Extended rules ***/
    /* !!! Important: this needs to be added after closure matrix construction
     *  and before predictions and combinations */
    processMultiTerminalRules(ruleSet.getMultiTerminalRules(), ruleSet, edgeSpace, isSeparateRuleInTrie);
    
    /*** construct predictions ***/
    predictionsArray = Prediction.constructPredictions(ruleSet.getTagRules(), leftCornerClosures, 
        edgeSpace, tagIndex, wordIndex, 
        Util.getNonterminals(nonterminalMap), operator); 
    assert Prediction.checkPredictions(predictionsArray, edgeSpace);

    /*** construct completion Set[] ***/
    // here state space does implies new states added from extended rules
    // we purposely use the old nontermPretermIndexer
//    completionsArray = Completion.constructCompletions(unaryClosures, edgeSpace, tagIndex);
    completionsArray = Completion.constructCompletions(unaryClosures, edgeSpace, 
        tagIndex, wordIndex, operator); // tag2completionsMap
    
    if(verbose>=0){
      Timing.tick("! Done building grammar."); 
    }
  }

  private void processMultiTerminalRules(Collection<ProbRule> extendedRules, RuleSet ruleSet, 
      EdgeSpace edgeSpace, boolean isSeparateRuleInTrie){
  Timing.startDoing("\n# Processing extended rules ...");
    
    int numExtendedRules = 0;
    for (ProbRule extendedRule : extendedRules) {
      List<Integer> children = Util.toList(extendedRule.getChildren());
      if (isSeparateRuleInTrie){ // use rule id instead of mother tag id
        ruleTrie.append(children, new Pair<Integer, Double>(ruleSet.indexOf(extendedRule.getRule()), 
            operator.getScore(extendedRule.getProb())));
      } else {
        ruleTrie.append(children, new Pair<Integer, Double>(extendedRule.getMother(), 
            operator.getScore(extendedRule.getProb())));
      }
      
      if(verbose>=2) System.err.println("Add to trie: " + extendedRule.toString(tagIndex, wordIndex));
      if (verbose>=0) {
        if(++numExtendedRules % 10000 == 0){
          System.err.print(" (" + numExtendedRules + ") ");
        }
      }
    }
    
    if(verbose>=2) System.err.println(Util.sprint(extendedRules, tagIndex, wordIndex));
    //if (!isSeparateRuleInTrie) if(verbose>=1) System.err.println(ruleTrie.toString(wordIndex, tagIndex));
   Timing.endDoing("Num extended rules=" + numExtendedRules + ", tag index size =" + tagIndex.size()	+ ", word index size = " + wordIndex.size());
  }
  

  /** Getters **/  
  public TrieSurprisal getRuleTrie() {
    return ruleTrie;
  }

  public Completion[] getCompletions(int tag) {
//    if(tag2completionsMap.containsKey(tag)){
//      return tag2completionsMap.get(tag);
//    } else {
//      return Completion.NO_COMPLETION;
//    }
  	if(tag>=completionsArray.length){
  		return Completion.NO_COMPLETION;
  	} else {
  		return completionsArray[tag];
  	}
  }

  public Prediction[] getPredictions(int pos) {
    if(pos>=predictionsArray.length){
      return Prediction.NO_PREDICTION;
    } else {
      return predictionsArray[pos];
    }
  }
  
  public ClosureMatrix getLeftCornerClosures() {
    return leftCornerClosures;
  }

  public ClosureMatrix getUnaryClosures() {
    return unaryClosures;
  }

}

/** Unused code **/

//private static final Set<BackwardCombination> emptySet = new HashSet();



//private Index<Integer> backPassiveStateIndexer = new HashIndex<Integer>();

// predictions[i] is the set of Prediction instances for state predictorStateIndex.get(i)
//private Prediction[][] predictions;   // (edge # x forwardProb x innerProb)
// private Prediction[][] predictionsVia;

//private int rootActiveEdge;
//private int goalEdge;

//GeneralizedCounter lexicon = new GeneralizedCounter(2);
//Map lexicon; //lexicon will be string -> double[] (array of prob for
//each preterm category)
//Set seenWords;

//public Prediction[][] getPredictions(){
//return predictions;
//}

/* Here, we make an assumption that extended rules if of the form X -> A B ... C
 * where X is a non-terminal, A, B, C are preterminals. If it doesn't satisfy, please check again!
 */

//if (!active.getMother().equals(result.getMother())) {
//  System.err.println("Error -- mother categories of active edge " + combination.activeChild + " " + active + " and result " + combination.result + " " + result + " are not identical");
//  satisfied = false;
//}
//if (!active.getDtrs().subList(1, active.getDtrs().size()).equals(result.getDtrs())) {
//  System.err.println("Error -- dtrs lists of active edge " + combination.activeChild + active + " and result " + combination.result + result + " are not consistent.");
//  satisfied = false;
//}

///*** Extended rules ***/
///* !!! Important: this needs to be added after closure matrix construction
// *  and before predictions and combinations */
///* Here, we make an assumption that extended rules if of the form X -> A B ... C
// * where X is a non-terminal, A, B, C are preterminals. If it doesn't satisfy, please check again!
// */
//processExtendedRules(extendedRules);
//Collection<Rule> combinedRules = new ArrayList<Rule>(rules);
//if(!useTrie){
//  combinedRules.addAll(extendedRules);
//}

//public Prediction[] getPrediction(int predictorState){
//int viaState = stateSpace.via[predictorState];
//
//assert (viaState == -1 && !nontermIndexer.contains(viaState));
//
//if(nontermIndexer.contains(viaState)){
//  int viaCategoryIndex = nontermIndexer.indexOf(viaState);
//  return predictionsVia[viaCategoryIndex];
//} else {
//  return NO_PREDICTION;
//}
//}

//public BackwardCombination[] getBackwardCombination(int passiveState){
//if(backPassiveStateIndexer.contains(passiveState)){
//  int passiveStateIndex = backPassiveStateIndexer.indexOf(passiveState);
//  return completions[passiveStateIndex];
//} else {
//  return noBackwardCombinations;
//}
//}

//public boolean containsExtendedRule = false;
//private Index<String> extendedTerminalIndexer = new HashIndex<String>(); // index the rhs terminal sequence of extended rules
//
// rule A -> b c, map terminal index of "b c" into a list of state indices, on of which is A -> []
//private Map<Integer, List<Pair<Integer, Double>>> extendedTerminalMap = 
//new HashMap<Integer, List<Pair<Integer,Double>>>();
//
//public List<Pair<Integer, Double>> getMotherStates(String terminals){
//int terminalIndex = extendedTerminalIndexer.indexOf(terminals);
//return extendedTerminalMap.get(terminalIndex);
//}
//
//public void processExtendedRule(Rule rule, int id){
//String rhsString = rule.rhsString();
//int rhsIndex = extendedTerminalIndexer.indexOf(rhsString, true); 
//int motherState = stateSpace.indexOfTag(rule.getMother());
//double logProb = Math.log(rule.score);
//System.err.println(rule + "\t" + stateSpace.get(motherState));
//
//for(IntTaggedWord child : rule.getDtrs()){
//  System.err.println(child);
//}
//if(!extendedTerminalMap.containsKey(rhsIndex)){
//  extendedTerminalMap.put(rhsIndex, new ArrayList<Pair<Integer, Double>>());
//}
//extendedTerminalMap.get(rhsIndex).add(new Pair<Integer, Double>(motherState, logProb));
//
//}


