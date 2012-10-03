package parser;

import cern.colt.matrix.DoubleMatrix2D;
import edu.stanford.nlp.parser.lexparser.IntTaggedWord;
import edu.stanford.nlp.util.HashIndex;
import edu.stanford.nlp.util.Index;
import edu.stanford.nlp.util.Indexes;
import edu.stanford.nlp.util.Pair;
import edu.stanford.nlp.util.Timing;

import java.text.DecimalFormat;
import java.util.Collection;

/**
 * A grammar to supply to construct a {@link PrefixProbabilityParser}.
 *
 * @author Roger Levy
 */
class Grammar {

  //private static final Set<StateSpace.BackwardCombination> emptySet = new HashSet();
  private static final StateSpace.Prediction[] NO_PREDICTION = new StateSpace.Prediction[0];
  private static final StateSpace.BackwardCombination[] noBackwardCombinations = new StateSpace.BackwardCombination[0];

  public static int verbose = 0;

  int rootActiveEdge;
  int goalEdge;

  //GeneralizedCounter lexicon = new GeneralizedCounter(2);
  //Map lexicon; //lexicon will be string -> double[] (array of prob for
  //each preterm category)
  //Set seenWords;

  // backwardCombinations[i] is the set of BackwardCombination instances for the passive category i
  public StateSpace.BackwardCombination[][] backwardCombinations; 
  public StateSpace.Prediction[][] predictions; // predictions[i] is the set of Prediction instances for active category i
  //private Index<Integer> backPassiveStateIndexer = new HashIndex<Integer>();
  
  // predictions[i] is the set of Prediction instances for state predictorStateIndex.get(i)
  //private StateSpace.Prediction[][] predictions;   // (edge # x forwardProb x innerProb)
  // private StateSpace.Prediction[][] predictionsVia;
  StateSpace stateSpace; // consist of all the rules

  // keep track of rules of type X -> a b c, where "a b c" is a key, sequence of string,
  // while a pair of X, state id, and rule score is a value associated to the key
  private TrieSurprisal ruleTrie;
  public static boolean useTrie = true;
  private static DecimalFormat df = new DecimalFormat("0.0000");
  
  public TrieSurprisal getRuleTrie() {
    return ruleTrie;
  }

  public Grammar(){
    stateSpace = new StateSpace();
    ruleTrie = new TrieSurprisal();
  }
    
  /**
   * learns all the grammar stuff.  Note that rootRule must be a unary contained in rules.
   */
  public void learnGrammar(Collection<Rule> rules, 
      Collection<Rule> extendedRules, Rule rootRule) {
    /* set up state space. */
    if (verbose >= 1){
      System.err.println("\n# Setting up state space and category indices...");
      System.err.println("Normal rules ...");
    }
    
    int numRules = 0;
    for (Rule r: rules) {
      stateSpace.addRule(r);
      
      if(verbose >= 1){
        if(++numRules % 10000 == 0){
          System.err.print(" (" + numRules + ") ");
        }
      }
      if(verbose >= 3){
        System.err.println(r);
      }
    }
    
    if (verbose >= 1) {
      System.err.println("Done! Num normal rules=" + numRules);
      System.err.println(" Done! Num rules=" + numRules + "\n" + 
          "# State space size=" + stateSpace.size() + ", passive=" + stateSpace.getPassiveStates().size() +
          ", active=" + stateSpace.getActiveStates().size());
    }
    /* done setting up state space, category indices */

    /*** Compute reflective and transitive left-corner and unit-production matrices ***/
    RelationMatrix relationMatrix = new RelationMatrix(stateSpace);
    HashIndex<Integer> nontermIndexer = stateSpace.getNontermIndexer().unmodifiableView(); // deepCopy().
    HashIndex<Integer> nontermPretermIndexer = stateSpace.getNontermPretermIndexer().unmodifiableView(); // deepCopy().
    
    /* do left-corner closures matrix */
    DoubleMatrix2D pl = relationMatrix.getPL(rules, nontermIndexer);
    ClosureMatrix leftCornerClosures = new ClosureMatrix(pl);

    /* do unary closure matrix */
    DoubleMatrix2D pu = relationMatrix.getPU(rules, nontermPretermIndexer);
    ClosureMatrix unaryClosures = new ClosureMatrix(pu);
    
    /*** Extended rules ***/
    /* !!! Important: this needs to be added after closure matrix construction
     *  and before predictions and combinations */
    /* Here, we make an assumption that extended rules if of the form X -> A B ... C
     * where X is a non-terminal, A, B, C are preterminals. If it doesn't satisfy, please check again!
     */
    processExtendedRules(extendedRules);
    Collection<Rule> combinedRules = new ArrayList<Rule>(rules);
    if(!useTrie){
      combinedRules.addAll(extendedRules);
      assert nontermIndexer.equals(stateSpace.getNontermIndexer().unmodifiableView());  // assert nontermIndexer remains the same, nontermPretermIndexer changes
    }
    
    
    /*** construct predictions ***/
    // here it's important to use combined rules
    predictions = constructPredictions(combinedRules, nontermIndexer, leftCornerClosures, stateSpace); 
    assert checkPredictionInnerProbDependsOnlyOnPredictedState(predictions);

    /*** construct backward combination Set[] ***/
    // here state space does implies new states added from extended rules
    // we purposely use the old nontermPretermIndexer
    backwardCombinations = constructBackwardCombinations(nontermPretermIndexer, unaryClosures, stateSpace);
    
    //if(verbose) System.err.println(stateSpace.toString());
    assert checkBackwardCombinations();

    /* finally set up root active edge */
    rootActiveEdge = stateSpace.indexOf(rootRule.toEdge());
    goalEdge = stateSpace.indexOf(rootRule.getMotherEdge());
  }

  private void processExtendedRules(Collection<Rule> extendedRules){
    if (verbose >= 1) {
      System.err.println("\n# Processing extended rules ...");
      Timing.startTime();
    }
    
    int numExtendedRules = 0;
    for (Rule extendedRule : extendedRules) {
      if(!useTrie){
        stateSpace.addRule(extendedRule);
      } else {
        List<IntTaggedWord> children = extendedRule.getDtrs();
        int motherState = stateSpace.indexOf(extendedRule.getMotherEdge());
        assert(motherState >= 0);
        ruleTrie.append(children, new Pair<Integer, Double>(motherState, Math.log(extendedRule.score))); // add log value here 
      }
      
      if (verbose >= 1) {
        if(++numExtendedRules % 10000 == 0){
          System.err.print(" (" + numExtendedRules + ") ");
        }
      }
      
      if (verbose >= 3) {
        System.err.println(extendedRule);
      }
    }
    
    if (verbose >= 1) {
      Timing.endTime("Done! Num extended rules=" + numExtendedRules);
    }
    
    if (verbose >= 3){
      System.err.println(ruleTrie);
      System.err.println(ruleTrie.getPrefixValueMap());
      System.err.println(ruleTrie.getValueMap());
    }
  }
  
  /**
   * Construct predictions 
   * 
   * @param rules
   * @param categories
   * @param leftCornerClosures
   * @return
   */
  private StateSpace.Prediction[][] constructPredictions(Collection<Rule> rules,
      Index<Integer> categories,
      ClosureMatrix leftCornerClosures, StateSpace stateSpace){
    /* do predictions: X -> . Z \alpha  predicts Y -> .\beta */
    
    // indexed by non-terminal index, predictions for Z
    StateSpace.Prediction[][] predictionsVia = new StateSpace.Prediction[categories.size()][]; 
    
    if(verbose > 0){
      System.err.print("\n# Constructing predictions ...");
      Timing.startTime();
    }
    
    int viaStateCount = 0; // via state with prediction
    int totalPredictions = 0;
    
    System.err.println("\nsize=" + categories.size() + ", " + categories);
    /** Construct predictions via states**/
    for (int viaCategoryIndex = 0; viaCategoryIndex < categories.size(); viaCategoryIndex++) { // Z     
      /** Make prediction **/
      int viaState = categories.get(viaCategoryIndex);
      if(verbose >= 2){
        System.err.println("# via: " + stateSpace.get(viaState));
      }
      List<StateSpace.Prediction> thesePredictions = new ArrayList<StateSpace.Prediction>();
      for (Rule r:rules) { // go through each rule, Y -> . \beta, TODO: speed up here, keep track of indices with positive left-closure scores
        if (r.isUnary()) {
          continue;
        }
        double rewriteScore = Math.log(r.score);
        int predictedCategoryMotherIndex = categories.indexOf(stateSpace.indexOf(r.getMotherEdge()));
        int predictedState = stateSpace.indexOf(r.toEdge());
        double leftCornerClosureScore = leftCornerClosures.get(viaCategoryIndex, predictedCategoryMotherIndex); // P_L (Z -> Y)
        
        if (leftCornerClosureScore != Double.NEGATIVE_INFINITY) {
          assert rewriteScore <= 0.00000001;
          assert rewriteScore + leftCornerClosureScore <= 0.0000001;
          StateSpace.Prediction p = stateSpace.new Prediction(predictedState, rewriteScore + leftCornerClosureScore, rewriteScore); // note scores are in log space
          thesePredictions.add(p); //predictionIndex.get(predictionIndex.indexOf(p)));
          if (verbose>=2){
            System.err.println("Predict: " + p + ", rewrite=" + df.format(Math.exp(rewriteScore)) 
                + ", left-corner=" + df.format(Math.exp(leftCornerClosureScore)));
          }
        }
      }
      
      if (thesePredictions.size() == 0) {
        //System.err.println("! No prediction for tag " + stateSpace.get(categories.get(viaCategoryIndex)));
        predictionsVia[viaCategoryIndex] = NO_PREDICTION;
      } else {
        predictionsVia[viaCategoryIndex] = (StateSpace.Prediction[]) thesePredictions.toArray(NO_PREDICTION);
        viaStateCount++;
        totalPredictions += predictionsVia[viaCategoryIndex].length;
      }
    }
   
    predictions = new StateSpace.Prediction[stateSpace.size()][];
    
    for (int predictorState = 0; predictorState < stateSpace.size(); predictorState++) {
      int viaState = stateSpace.via(predictorState);
      if (viaState == -1 || !categories.contains(viaState)) {
        predictions[predictorState] = NO_PREDICTION;
      } else {
        int viaCategoryIndex = categories.indexOf(viaState);
        predictions[predictorState] = predictionsVia[viaCategoryIndex];
      }
    }
    
    if(verbose >= 1){
      Timing.tick("Done! Total predictions=" + totalPredictions); 
    }
    
    return predictions;
  }
  
  /**
   * Construct backward combinations
   * @param rules
   * @param allNontermCategories
   * @param unaryClosures
   * @return
   */
  private StateSpace.BackwardCombination[][] constructBackwardCombinations(
      Index<Integer> allNontermCategories, ClosureMatrix unaryClosures, StateSpace stateSpace){
    
    /* do backward combination Set[] */
    if(verbose > 0){
      System.err.println("\n# Backward combination ...");
      Timing.startTime();
    }
    
    Map<Integer, Set<StateSpace.BackwardCombination>> backComboMap = new HashMap<Integer, Set<StateSpace.BackwardCombination>>(); 

    for(Integer activeState : stateSpace.getActiveStates()){ // go through active states, X -> Z \alpha      
      int viaState = stateSpace.via(activeState);
      int viaCategoryIndex = allNontermCategories.indexOf(viaState); // Z
      
//      if(verbose >= 2){
//        System.err.println("Active state " + activeState + ": " + stateSpace.get(activeState)
//            + ", via state " + viaState + ": " + stateSpace.get(viaState));
//      }
      
      // get passive states
      if(viaCategoryIndex >= 0 && unaryClosures.containsRow(viaCategoryIndex)){ // non-zero rows in closure matrix
        // go through passive state
        for(Integer passiveState : stateSpace.getPassiveStates()){ // go through passive states, Y ->
          int passiveCategoryIndex = allNontermCategories.indexOf(passiveState); // Y
          
          double unaryClosureScore = Double.NEGATIVE_INFINITY;
          if (passiveCategoryIndex >= 0){
            unaryClosureScore = unaryClosures.get(viaCategoryIndex, passiveCategoryIndex); // R(Z -> Y)
          } // else: Y is the new preterminal added from extended rules
          
          if (unaryClosureScore != Double.NEGATIVE_INFINITY) {
            if (!backComboMap.containsKey(passiveState)) {
              backComboMap.put(passiveState, new HashSet<StateSpace.BackwardCombination>());
              //backPassiveStateIndexer.add(passiveState);
            }
            backComboMap.get(passiveState).add(stateSpace.new BackwardCombination(activeState, stateSpace.to(activeState), unaryClosureScore));
          }
        }
      } else { // for zero row, there is only passive state, which is the via state Z -> []
        int passiveState = viaState; //allNontermCategories.get(viaCategoryIndex);

        if (!backComboMap.containsKey(passiveState)) {
          backComboMap.put(passiveState, new HashSet<StateSpace.BackwardCombination>());
          //backPassiveStateIndexer.add(passiveState);
        }
      
        backComboMap.get(passiveState).add(stateSpace.new BackwardCombination(
            activeState, stateSpace.to(activeState), 0.0));
      }
    }
    
    // StateSpace.BackwardCombination[][] backwardCombinations = new StateSpace.BackwardCombination[numBackPassiveStates][];
    StateSpace.BackwardCombination[][] backwardCombinations = new StateSpace.BackwardCombination[stateSpace.size()][];
    for (int passiveState = 0; passiveState < stateSpace.size(); passiveState++) {
    //for (int i = 0; i < numBackPassiveStates; i++) {
      //int passiveState = backPassiveStateIndexer.get(i);
      if(backComboMap.containsKey(passiveState)){
        Set<StateSpace.BackwardCombination> backCombo = backComboMap.get(passiveState);
        List<StateSpace.BackwardCombination> l = new ArrayList<StateSpace.BackwardCombination>();
        l.addAll(backCombo);
        //backwardCombinations[i] = (StateSpace.BackwardCombination[]) l.toArray(noBackwardCombinations);
        backwardCombinations[passiveState] = (StateSpace.BackwardCombination[]) l.toArray(noBackwardCombinations);
        
        if(verbose >= 3){
          System.err.println("Completions for state " + stateSpace.get(passiveState) + ": " + l);
        }
      } else {
        backwardCombinations[passiveState] = noBackwardCombinations;
      }
    }
    
    if (verbose >= 1) {
      Timing.tick("Done with backward combination");
    }
    
    return backwardCombinations;
  }
  
  /* safety check via assertion */
  private boolean checkPredictionInnerProbDependsOnlyOnPredictedState(StateSpace.Prediction[][] predictions) {
    boolean result = true;
    double[] predictedStateInnerProbs = new double[stateSpace.size()];
    boolean[] existingPredictedStates = new boolean[stateSpace.size()];
    for (int i = 0; i < predictions.length; i++) {
      StateSpace.Prediction[] prediction = predictions[i];
      for (int j = 0; j < prediction.length; j++) {
        StateSpace.Prediction p = prediction[j];
        if (existingPredictedStates[p.result]) {
          if (Math.abs(predictedStateInnerProbs[p.result] - p.innerProbMultiplier) > 0.00001) {
            System.err.println("Error -- predicted-state " + stateSpace.get(p.result) + "has inconsistent inner probability estimate of " + p.innerProbMultiplier);
            result = false;
          }
        } else {
          existingPredictedStates[p.result] = true;
          predictedStateInnerProbs[p.result] = p.innerProbMultiplier;
        }
      }
    }
    return result;
  }

  /* check to see if any of the backward combinations are invalid*/
  private boolean checkBackwardCombinations() {
    System.err.println("Checking validity of backward combinations...");
    boolean satisfied = true;
    for (int i = 0; i < backwardCombinations.length; i++) {
      StateSpace.BackwardCombination[] backwardCombination = backwardCombinations[i];
      for (int j = 0; j < backwardCombination.length; j++) {
        StateSpace.BackwardCombination combination = backwardCombination[j];
        Edge active = stateSpace.get(combination.activeChild);
        Edge result = stateSpace.get(combination.result);
        if (!active.getMother().equals(result.getMother())) {
          System.err.println("Error -- mother categories of active edge " + combination.activeChild + " " + active + " and result " + combination.result + " " + result + " are not identical");
          satisfied = false;
        }
        if (!active.getDtrs().subList(1, active.getDtrs().size()).equals(result.getDtrs())) {
          System.err.println("Error -- dtrs lists of active edge " + combination.activeChild + active + " and result " + combination.result + result + " are not consistent.");
          satisfied = false;
        }
      }
    }
    if (satisfied) {
      System.err.println("All backward combinations are valid.");
    }
    return satisfied;
  }

  /** Getters **/
//  public StateSpace.Prediction[][] getPredictions(){
//    return predictions;
//  }
  
  public static void main(String[] args) {
    Collection<Rule> rules = new ArrayList<Rule>();

    Rule s = new Rule("S", Arrays.asList(new String[]{"NP", "VP"}), 1.0);
    Rule vp = new Rule("VP", Arrays.asList(new String[]{"V", "NP"}), 1.0);
    Rule np1 = new Rule("NP", Arrays.asList(new String[]{"NP", "PP"}), 0.4);
    Rule np2 = new Rule("NP", Arrays.asList(new String[]{"DT", "NN"}), 0.5);
    Rule np3 = new Rule("NP", Arrays.asList(new String[]{"PP"}), 0.1);
    Rule pp = new Rule("PP", Arrays.asList(new String[]{"P", "NP"}), 0.6);
    Rule pp1 = new Rule("PP", Arrays.asList(new String[]{"NP"}), 0.4); // Thang add
    Rule root = new Rule("ROOT", Arrays.asList(new String[]{"S"}), 1.0);

    rules.add(s);
    rules.add(vp);
    rules.add(np1);
    rules.add(np2);
    rules.add(np3);
    rules.add(pp);
    rules.add(pp1); // Thang add
    rules.add(root);

//    Set<IntTaggedWord> preterminals = new HashSet<IntTaggedWord>();
//    Set<IntTaggedWord> terminals = new HashSet<IntTaggedWord>();
//    preterminals.add(IntTaggedWord.createTagITW("V"));
//    preterminals.add(IntTaggedWord.createTagITW("DT"));
//    preterminals.add(IntTaggedWord.createTagITW("NN"));
    Grammar g = new Grammar();
    g.learnGrammar(rules, new ArrayList<Rule>(), root);

    // see the results!

    Edge r = new Edge("PP", new ArrayList<String>());
    System.out.println(g.backwardCombinations[g.stateSpace.indexOf(r)]);
  }
  
//public StateSpace.Prediction[] getPrediction(int predictorState){
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

//public StateSpace.BackwardCombination[] getBackwardCombination(int passiveState){
//if(backPassiveStateIndexer.contains(passiveState)){
//  int passiveStateIndex = backPassiveStateIndexer.indexOf(passiveState);
//  return backwardCombinations[passiveStateIndex];
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

}

