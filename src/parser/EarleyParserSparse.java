/**
 * 
 */
package parser;

import java.io.BufferedReader;
import java.util.HashMap;
import java.util.Map;
import java.util.Map.Entry;

import util.Util;

import edu.stanford.nlp.trees.Treebank;
import edu.stanford.nlp.util.DoubleList;
import edu.stanford.nlp.util.Timing;

/**
 * @author Minh-Thang Luong, 2012
 *
 */
public class EarleyParserSparse extends EarleyParser {
  protected Map<Integer, Map<Integer, Double>> forwardProb;   // forwardProb.get(linear[left][right]).get(edge)
  protected Map<Integer, Map<Integer, Double>> innerProb;     // innerProb.get(linear[left][right]).get(edge)

  
  public EarleyParserSparse(BufferedReader br, String rootSymbol,
      boolean isScaling, boolean isLogProb, boolean isLeftWildcard) {
    super(br, rootSymbol, isScaling, isLogProb, isLeftWildcard);
  }

  public EarleyParserSparse(String grammarFile, String rootSymbol,
      boolean isScaling, boolean isLogProb, boolean isLeftWildcard) {
    super(grammarFile, rootSymbol, isScaling, isLogProb, isLeftWildcard);
  }

  public EarleyParserSparse(Treebank treebank, String rootSymbol,
      boolean isScaling, boolean isLogProb, boolean isLeftWildcard) {
    super(treebank, rootSymbol, isScaling, isLogProb, isLeftWildcard);
  }

  protected void sentInit(){
    super.sentInit();
    
    if (verbose>=2){
      System.err.println("# EarleyParserSparse initializing ... ");
      Timing.startTime();
    }
  
    forwardProb = new HashMap<Integer, Map<Integer,Double>>();
    innerProb = new HashMap<Integer, Map<Integer,Double>>();
  }
  
  /* (non-Javadoc)
   * @see parser.EarleyParser#addToChart(int, int, int, double, double)
   */
  @Override
  protected void addToChart(int left, int right, int edge, double logForward,
      double logInner) {
    int lrIndex = linear[left][right]; // left right index
    
    if(!forwardProb.containsKey(lrIndex)){
      forwardProb.put(lrIndex, new HashMap<Integer, Double>());
      innerProb.put(lrIndex, new HashMap<Integer, Double>());
    }
    
    assert(!forwardProb.get(lrIndex).containsKey(edge));
    forwardProb.get(lrIndex).put(edge, logForward);
    innerProb.get(lrIndex).put(edge, logInner);
    
    if(verbose >= 2){
      System.err.println("# Add edge " + edgeInfo(left, right, edge));
    }
  }

  /* used as holding zone for predictions */
  private Map<Integer, Double> predictedForwardProb;
  private Map<Integer, Double> predictedInnerProb;
  
  /* (non-Javadoc)
   * @see parser.EarleyParser#predictAll(int)
   */
  @Override
  protected void predictAll(int right) {
    // init
    predictedForwardProb = new HashMap<Integer, Double>();
    predictedInnerProb = new HashMap<Integer, Double>();
    
    boolean flag = false;
    for (int left = 0; left <= right; left++) {
      int lrIndex = linear[left][right]; // left right index
      if (!forwardProb.containsKey(lrIndex)){ // no active categories
        continue;
      }
      
      if(verbose>=3){
        System.err.println("\n# Predict all [" + left + "," + right + "]: " + 
            "chart count=" + forwardProb.get(lrIndex).size());
      }
      
      flag = true;
      for(int edge : forwardProb.get(lrIndex).keySet()){
        // predict for right: left X -> _ . Y _
        predictFromEdge(left, right, edge);
      }
    }
    
    // replace old entries with recently predicted entries
    // all predictions will have the form right: right Y -> _
    int rrIndex = linear[right][right]; // right right index
    forwardProb.put(rrIndex, predictedForwardProb);
    innerProb.put(rrIndex, predictedInnerProb);
    if (verbose >= 3 && flag) {
      dumpChart();
    }
  }
  
  protected void predictFromEdge(int left, int right, int edge) {
    Prediction[] predictions = g.getPredictions(edge);
    if (verbose >= 3 && predictions.length>0) {
      System.err.println("From edge " + edgeInfo(left, right, edge));
    }
    
    for (int x = 0, n = predictions.length; x < n; x++) { // go through each prediction
      Prediction p = predictions[x];
      
      // spawn new edge
      int newEdge = p.predictedState;
      double newForwardProb = operator.multiply(forwardProb.get(linear[left][right]).get(edge), p.forwardProbMultiplier);
      double newInnerProb = p.innerProbMultiplier;
      
      // add to tmp map
      if (!predictedForwardProb.containsKey(newEdge)){ // new edge not in map
        predictedForwardProb.put(newEdge, operator.zero());
      }
      predictedForwardProb.put(newEdge, operator.add(predictedForwardProb.get(newEdge), newForwardProb));
      predictedInnerProb.put(newEdge, newInnerProb);
      
      if (verbose >= 3) {
        System.err.println("  to " + edgeInfo(right, right, newEdge, newForwardProb, newInnerProb));
      }
    }
  }

  /** Used as holding zones for completions **/
  protected Map<Integer, DoubleList> theseForwardProb;
  protected Map<Integer, DoubleList> theseInnerProb;
  
  protected void completeAll(int left, int middle, int right){
    int mrIndex = linear[middle][right]; // middle right index
    if(!forwardProb.containsKey(mrIndex)){
      forwardProb.put(mrIndex, new HashMap<Integer, Double>());
      innerProb.put(mrIndex, new HashMap<Integer, Double>());
    }
    
    // there're active edges for the span [middle, right]
    if(verbose>=3){
      System.err.println("\n# Complete all [" + left + "," + middle + "," + right + "]: chartCount[" 
          + middle + "," + right + "]=" + forwardProb.get(mrIndex).size());
    }
    
    // init
    theseForwardProb = new HashMap<Integer, DoubleList>();
    theseInnerProb = new HashMap<Integer, DoubleList>();
      
    // tag completions
    tagComplete(left, middle, right);
    
    /** Handle extended rules **/
    Map<Integer, Double> valueMap = g.getRuleTrie().findAllPrefixMap(wordIndices.subList(middle, right));
    
    if(valueMap != null){
      if(verbose >= 2){
        System.err.println("# AG prefix " + Util.sprint(parserWordIndex, wordIndices.subList(middle, right)) + 
            ": " + Util.sprint(valueMap, parserTagIndex));
      }
      for(Entry<Integer, Double> entry : valueMap.entrySet()){
        int tag = entry.getKey();
        double score = entry.getValue();
        addPrefixProbExtendedRule(left, middle, right, tag, score);
      }
      
    }

    // completions yield edges: right: left X -> _ Y . _
    int lrIndex = linear[left][right];
    if(!forwardProb.containsKey(lrIndex)){
      forwardProb.put(lrIndex, new HashMap<Integer, Double>());
      innerProb.put(lrIndex, new HashMap<Integer, Double>());
    }
    storeProbs(theseForwardProb, forwardProb.get(lrIndex));
    storeProbs(theseInnerProb, innerProb.get(lrIndex));
  }
  
  protected void complete(int left, int middle, int right, int tag, double inner) {
    // we already completed the edge, right: middle Y -> _ ., where passive represents for Y
    Completion[] completions = g.getCompletions1(tag);
    
    if (verbose>=3 && completions.length>0){
      System.err.println(completionInfo(left, middle, right, tag, inner, completions));
    }
    
    int lmIndex = linear[left][middle]; // left middle index
    assert(forwardProb.containsKey(lmIndex));
    Map<Integer, Double> forwardMap = forwardProb.get(lmIndex);
    Map<Integer, Double> innerMap = innerProb.get(lmIndex);
    
    for (int x = 0, n = completions.length; x < n; x++) { // go through all completions we could finish
      Completion completion = completions[x];
      
      if (forwardMap.containsKey(completion.activeEdge)) { // middle: left X -> _ . Y _
        complete(left, middle, right, completion, forwardMap, innerMap, inner);
      }
    }
  }

  protected void complete(int left, int middle, int right, Completion completion, Map<Integer, Double> forwardMap, Map<Integer, Double> innerMap, double inner){
    double updateScore = operator.multiply(completion.score, inner);
    double newForwardProb = operator.multiply(forwardMap.get(completion.activeEdge), updateScore);
    double newInnerProb = operator.multiply(innerMap.get(completion.activeEdge), updateScore);
    
    
    // add edge, right: left X -> _ Y . _, to tmp storage
    if(!theseForwardProb.containsKey(completion.completedEdge)){
      theseForwardProb.put(completion.completedEdge, new DoubleList());
      theseInnerProb.put(completion.completedEdge, new DoubleList());
    }
    theseForwardProb.get(completion.completedEdge).add(newForwardProb);
    theseInnerProb.get(completion.completedEdge).add(newInnerProb);
        
    // store inside probs
    storeInsideProb(left, right, completion.completedEdge, newInnerProb);
    
    if (verbose >= 3) {
      System.err.println("  start " + edgeInfo(left, middle, completion.activeEdge) 
          + " -> new " + edgeInfo(left, right, completion.completedEdge, newForwardProb, newInnerProb));

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
            operator.getProb(forwardMap.get(completion.activeEdge)) + "*" + 
            operator.getProb(completion.score) + "*" + operator.getProb(inner) + "\t" + left + "\t" + middle + "\t" + completion.activeEdge);
        System.err.println("# Syn prefix prob += " + operator.getProb(synProb) + "=" + 
            operator.getProb(newForwardProb) + "/" + 
            operator.getProb(inner));
      }
    }
  }
  
  protected void addPrefixProbExtendedRule(int left, int middle, int right, int tag, double inner) {    
//    int passive = edgeSpace.indexOfTag(tag);
    Completion[] completions = g.getCompletions1(tag);
    
    if (verbose>=3 && completions.length>0){
      System.err.println(completionInfo(left, middle, right, tag, inner, completions));
    }
   

    if (isScaling){
      assert(containsExtendedRule);
      inner = operator.multiply(inner, scalingMatrix[linear[middle][right]]);
    }
    
    int lmIndex = linear[left][middle]; // left middle index
    assert(forwardProb.containsKey(lmIndex));
    Map<Integer, Double> forwardMap = forwardProb.get(lmIndex);
    
    // the current AG rule: Y -> w_middle ... w_(right-1) .
    for (int x = 0, n = completions.length; x < n; x++) {
      Completion completion = completions[x];
     
      if (forwardMap.containsKey(completion.activeEdge)){
        // we are using trie, and there's an extended rule that could be used to update prefix prob
        double prefixScore = operator.multiply(forwardMap.get(completion.activeEdge), 
                               operator.multiply(completion.score, inner));
        
        thisPrefixProb.add(prefixScore);
        thisSynPrefixProb.add(prefixScore); // TODO: compute syntactic scores for AG
        
        if (verbose >= 3) {
          System.err.println("  start " + edgeInfo(left, middle, completion.activeEdge));
        }
        
        if (verbose >= 2) {
          System.err.println("  # prefix prob AG += " + operator.getProb(prefixScore) + "(" +  
              operator.getProb(forwardProb.get(lmIndex).get(completion.activeEdge)) + "*" +
              operator.getProb(completion.score) + "*" + operator.getProb(inner)  + ")");
        }
      }
    }
  }    

  protected void storeProbs(Map<Integer, DoubleList> dl, Map<Integer, Double> probs) {
    
    for (int edge : dl.keySet()) {
      double[] temps = dl.get(edge).toArray();
      if (temps.length > 0) {
        double currentValue = (probs.containsKey(edge)) ? probs.get(edge) : operator.zero();
        probs.put(edge, operator.add(operator.arraySum(temps), currentValue));
      }
    }
  }

  @Override
  public String edgeInfo(int left, int right, int edge) {
    int index = linear[left][right];
    if(forwardProb.containsKey(index) && forwardProb.get(index).containsKey(edge)){
      return edgeInfo(left, right, edge, forwardProb.get(index).get(edge), innerProb.get(index).get(edge));
    } else {
      return edgeInfo(left, right, edge, operator.zero(), operator.zero());
    }    
  }

  
  /* (non-Javadoc)
   * @see parser.EarleyParser#dumpInnerChart()
   */
  @Override
  protected void dumpInsideChart() {
    // TODO Auto-generated method stub

  }

  @Override
  protected void dumpChart() {
    System.err.println("# Chart snapshot, edge space size = " + edgeSpaceSize);
    for (int left = 0; left <= numWords; left++) {
      for (int right = left; right <= numWords; right++) {
        int lrIndex = linear[left][right];
        
        int count = forwardProb.containsKey(lrIndex) ? forwardProb.get(lrIndex).size() : 0;
        if(count>0){ // there're active states
          assert(forwardProb.get(lrIndex).size()>0);
          
          System.err.println("[" + left + "," + right + "]: " + count  
              + " (" + df1.format(count*100.0/edgeSpaceSize) + "%)");
          for (int edge : forwardProb.get(lrIndex).keySet()) {
            System.err.println("  " + edgeSpace.get(edge).toString(parserTagIndex, parserTagIndex) 
                + ": " + df.format(operator.getProb(forwardProb.get(lrIndex).get(edge))) 
                + " " + df.format(operator.getProb(innerProb.get(lrIndex).get(edge))));
          }
        }
      }
    }
  }

}

/** Unused code **/
//for(int edge : forwardProb.get(mrIndex).keySet()){
//  // right: middle Y -> _ .
//  double inner = innerProb.get(mrIndex).get(edge);
//  complete(left, middle, right, edge, inner); // in completion the forward prob of Y -> _ . is ignored
//}

///* (non-Javadoc)
// * @see parser.EarleyParser#stringProbability(int)
// */
//@Override
//public double stringProbability(int right) {
//  int index = linear[0][right];
//  double prefixProb = operator.zero();
//  if (forwardProb.containsKey(index) && forwardProb.get(index).containsKey(goalEdge)) {
//    prefixProb = innerProb.get(index).get(goalEdge);
//    assert(Math.abs(prefixProb - forwardProb.get(index).get(goalEdge)) < 1e-5);
//    
//  }
//  
//  return operator.getProb(prefixProb);
//}