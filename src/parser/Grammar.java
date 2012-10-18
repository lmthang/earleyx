package parser;

import cern.colt.matrix.DoubleMatrix2D;
import edu.stanford.nlp.util.Index;
import edu.stanford.nlp.util.Pair;
import edu.stanford.nlp.util.Timing;

import java.util.Collection;
import java.util.List;
import java.util.Map;

import recursion.ClosureMatrix;
import recursion.RelationMatrix;
import utility.TrieSurprisal;
import utility.Utility;

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
  private Completion[][] completionsArray; // completions[i] is the set of Completion instances for the state i 
  private Prediction[][] predictionsArray; // predictions[i] is the set of Prediction instances for the state i
  private EdgeSpace edgeSpace; // consist of all the rules

  private Index<String> wordIndex;
  private Index<String> tagIndex;
  private Map<Integer, Integer> nonterminalMap;

  private ClosureMatrix leftCornerClosures;
  private ClosureMatrix unaryClosures;
  public Grammar(Index<String> wordIndex, Index<String> tagIndex, Map<Integer, Integer> nonterminals){
    this.wordIndex = wordIndex;
    this.tagIndex = tagIndex;
    this.nonterminalMap = nonterminals;
    
    edgeSpace = new EdgeSpace(tagIndex);
    ruleTrie = new TrieSurprisal();
  }
    
  /**
   * learns all the grammar stuff.  Note that rootRule must be a unary contained in rules.
   */
  public void learnGrammar(Collection<Rule> rules, Collection<Rule> extendedRules) { // , Collection<Rule> extendedRules, , Rule rootRule    
    /* set up state space. */
    edgeSpace.build(rules);
    
    /*** Compute reflective and transitive left-corner and unit-production matrices ***/
    RelationMatrix relationMatrix = new RelationMatrix(tagIndex);
    
    /* do left-corner closures matrix */
    DoubleMatrix2D pl = relationMatrix.getPL(rules, nonterminalMap);
    leftCornerClosures = new ClosureMatrix(pl);
    leftCornerClosures.changeIndices(nonterminalMap);
    
    /* do unary closure matrix */
    DoubleMatrix2D pu = relationMatrix.getPU(rules); //, nontermPretermIndexer);
    unaryClosures = new ClosureMatrix(pu);
    
    /*** Extended rules ***/
    /* !!! Important: this needs to be added after closure matrix construction
     *  and before predictions and combinations */
    processExtendedRules(extendedRules);
    
    /*** construct predictions ***/
    predictionsArray = Prediction.constructPredictions(rules, leftCornerClosures, edgeSpace, tagIndex, 
        Utility.getNonterminals(nonterminalMap)); 
    assert Prediction.checkPredictions(predictionsArray, edgeSpace);

    /*** construct completion Set[] ***/
    // here state space does implies new states added from extended rules
    // we purposely use the old nontermPretermIndexer
    completionsArray = Completion.constructCompletions(unaryClosures, edgeSpace, tagIndex);
    
    assert Completion.checkCompletions(completionsArray, edgeSpace, tagIndex);
  }

  private void processExtendedRules(Collection<Rule> extendedRules){
    if (verbose >= 1) {
      System.err.println("\n# Processing extended rules ...");
      Timing.startTime();
    }
    
    int numExtendedRules = 0;
    for (Rule extendedRule : extendedRules) {
      List<Integer> children = extendedRule.getChildren();
      int motherState = edgeSpace.indexOfTag(extendedRule.getMother()); 
      assert(motherState == edgeSpace.indexOf(extendedRule.getMotherEdge()));
      assert(motherState >= 0);
      ruleTrie.append(children, new Pair<Integer, Double>(extendedRule.getMother(), Math.log(extendedRule.score))); // add log value here 
      
      if (verbose >= 4) {
        System.err.println("Add to trie: " + extendedRule.toString(tagIndex, wordIndex));
      }
      if (verbose >= 1) {
        if(++numExtendedRules % 10000 == 0){
          System.err.print(" (" + numExtendedRules + ") ");
        }
      }
    }
    
    if (verbose >= 4) {
      System.err.println(Utility.sprint(extendedRules, wordIndex, tagIndex));
      System.err.println(ruleTrie.toString(wordIndex, tagIndex));
    }
    if (verbose >= 1) {
      Timing.endTime("Done! Num extended rules=" + numExtendedRules);
    }
  }
  

  /** Getters **/  
  public TrieSurprisal getRuleTrie() {
    return ruleTrie;
  }
  public Completion[] getCompletions(int pos) {
    return completionsArray[pos];
  }

  public Prediction[] getPredictions(int pos) {
    return predictionsArray[pos];
  }

  public EdgeSpace getEdgeSpace() {
    return edgeSpace;
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
////assert (viaState == -1 && !nontermIndexer.contains(viaState));
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
//// rule A -> b c, map terminal index of "b c" into a list of state indices, on of which is A -> []
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


