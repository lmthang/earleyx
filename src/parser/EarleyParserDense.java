package parser;

import java.io.BufferedReader;
import java.util.Map;
import java.util.Map.Entry;

import utility.Utility;

import edu.stanford.nlp.math.ArrayMath;
import edu.stanford.nlp.math.SloppyMath;
import edu.stanford.nlp.trees.Treebank;
import edu.stanford.nlp.util.DoubleList;
import edu.stanford.nlp.util.Pair;
import edu.stanford.nlp.util.Timing;

public class EarleyParserDense extends EarleyParser{
  protected int[][] linear; // convert matrix indices into linear indices
  protected boolean[][] chartEntries; // chartEntries[linear[leftEdge][rightEdge]][categoryNumber]
  protected double[][] forwardProb;   // forwardProb[linear[leftEdge][rightEdge]][categoryNumber]
  protected double[][] innerProb;     // innerProb[linear[leftEdge][rightEdge]][categoryNumber]
  
  public EarleyParserDense(Treebank treebank, String rootSymbol) {
    super(treebank, rootSymbol);
  }
  public EarleyParserDense(String grammarFile, String rootSymbol) {
    super(grammarFile, rootSymbol);
  }
  public EarleyParserDense(BufferedReader br, String rootSymbol) {
    super(br, rootSymbol);
  }
  
  
  protected void sentInitialize(){
    super.sentInitialize();
    if (verbose>=1){
      System.err.println("# EarleyParserDense initializing ... ");
      Timing.startTime();
    }
    
    /* 1-D */
    prefixProb = new double[numWords+1];
    synPrefixProb = new double[numWords+1];
    Utility.initToNegativeInfinity(prefixProb);
    Utility.initToNegativeInfinity(synPrefixProb);
    
    /* 2-D */
    linear = new int[numWords+1][numWords+1];
    // go in the order of CKY parsing
    int numCells=0;
    for(int rightEdge=1; rightEdge<=numWords; rightEdge++){
      for(int leftEdge=rightEdge-1; leftEdge>=0; leftEdge--){
        linear[leftEdge][rightEdge] = numCells++;
      }
    }
    assert(numCells==(numWords*(numWords+1)/2));
    
    chartEntries = new boolean[numCells][edgeSpaceSize];
    forwardProb = new double[numCells][edgeSpaceSize];
    innerProb = new double[numCells][edgeSpaceSize];
    Utility.initToNegativeInfinity(forwardProb);
    Utility.initToNegativeInfinity(innerProb);
    
    // add
    addToChart(0, 0, rootEdge, 0.0, 0.0);
    predictAll(0);
    addToChart(0, 0, rootEdge, 0.0, 0.0); // this is a bit of a hack needed because predictAll(0) wipes out the seeded rootActiveEdge chart entry.
    
    if (verbose>=1){
      Timing.endTime("Done initializing!");
    }
  }
  
  /* used as holding zone for predictions */
  private boolean[] predictedChartEntries;
  private double[] predictedForwardProb;
  private double[] predictedInnerProb;
  
  /**
   * Due to the use of left-corner closures, predicted items themselves never cause further prediction.
   * @param right
   */
  protected void predictAll(int right) {
    // init
    predictedChartEntries = new boolean[edgeSpaceSize];
    predictedForwardProb = new double[edgeSpaceSize];
    predictedInnerProb = new double[edgeSpaceSize];
    Utility.initToNegativeInfinity(predictedForwardProb);
    Utility.initToNegativeInfinity(predictedInnerProb);
    
    for (int left = 0; left <= right; left++) {
      if(verbose>=3){
        System.err.println("# Predict all [" + left + ", " + right + "]: num categories=" + chartCount[left][right]);
      }
      
      if (chartCount[left][right]>0){ // there are active categories
        for (int edge = 0; edge < chartEntries[linear[left][right]].length; edge++) {
          if (chartEntries[linear[left][right]][edge]) { // this state is active
            // predict for right: left X -> _ . Y _
            predictFromEdge(left, right, edge);
          }
        }
      }
    }
    
    // all predictions will have the form right: right Y -> _
    chartEntries[linear[right][right]] = predictedChartEntries;
    forwardProb[linear[right][right]] = predictedForwardProb;
    innerProb[linear[right][right]] = predictedInnerProb;
    
    if (verbose >= 3) {
      dumpChart();
      
      if (right>0){
        System.err.print(right + "\t");
        int count = 0;
        for (int i = 0; i < predictedChartEntries.length; i++) {
          System.err.print(predictedChartEntries[i] + " ");
          if (predictedChartEntries[i]){
            count++;
          }
        }
        System.err.println();
        assert(count == chartCount[right][right]);
      }
    }
  }
  
  // predict for the edge that spans [left, right]
  private void predictFromEdge(int left, int right, int edge) {
    Prediction[] predictions = g.getPredictions(edge);
    if (verbose >= 3) {
      System.err.println("From edge " + edgeInfo(left, right, edge));
    }
    for (int x = 0, n = predictions.length; x < n; x++) { // go through each prediction
      Prediction p = predictions[x];
      
      // spawn new edge
      int newEdge = p.predictedState;
      double newForwardProb = forwardProb[linear[left][right]][edge] + p.forwardProbMultiplier;
      double newInnerProb = p.innerProbMultiplier;
      
      // add to tmp arrays
      if (!predictedChartEntries[edge]){
        predictedChartEntries[edge] = true;
        chartCount[right][right]++; // count for new edge [right, right]
      }
      predictedForwardProb[edge] = SloppyMath.logAdd(predictedForwardProb[edge], newForwardProb);
      predictedInnerProb[edge] = newInnerProb;
      
      if (verbose >= 3) {
        System.err.println("\tto " + edgeInfo(right, right, newEdge, newForwardProb, newInnerProb));
      }
    }
  }
  
  /** Used as holding zones for completions **/
  protected boolean[] theseChartEntries;
  protected DoubleList[] theseForwardProb = new DoubleList[edgeSpaceSize];
  protected DoubleList[] theseInnerProb = new DoubleList[edgeSpaceSize];
  

  @Override
  protected void combineAll(int right) {
    for (int left = right - 1; left >= 0; left--) {
      for (int middle = right - 1; middle >= left; middle--) {
        // check which categories have finished expanding [middle, right]
        if (chartCount[middle][right] == 0){ // there're active edges for the span [middle, right]
          continue;
        }
        
        // init
        theseChartEntries = new boolean[edgeSpaceSize];
        for (int i = 0; i < edgeSpaceSize; i++) {
          theseForwardProb[i] = new DoubleList();
          theseInnerProb[i] = new DoubleList();
        }
        
        // we try to find other completed states that end at middle
        // so that we could generate new states ending at right
        // when middle == right-1, we will attempt to update the prefix probability
        for (int edge = edgeSpaceSize - 1; edge >= 0; edge--) { // Thang: why do we go back ward in edge ??
          if (chartEntries[linear[middle][right]][edge]) { // right: middle Y -> _ .
            double inner = innerProb[linear[middle][right]][edge];
            combine(left, middle, right, edge, inner); // in completion the forward prob of Y -> _ . is ignored
          }
        }
        
        /** Handle extended rules **/
        Map<Integer, Double> valueMap = g.getRuleTrie().findAllPrefixMap(wordIndices.subList(middle, right));
        
        if(valueMap != null){
          for(Entry<Integer, Double> entry : valueMap.entrySet()){
            int edge = entry.getKey();
            double score = entry.getValue();
            if(verbose >= 2){
              System.err.println("# AG rule " + g.getStateSpace().get(edge) + ", RHS=" 
                  + Utility.sprint(WORD_INDEX, wordIndices.subList(middle, right)) + ", score" + score);
            }
            addPrefixProbExtendedRule(left, middle, right, edge, score);
          }
        }
        
        // completions yield edges: right: left X -> _ Y . _
        Pair<boolean[], Integer> pair = booleanUnion(chartEntries[linear[left][right]], theseChartEntries);;
        chartEntries[linear[left][right]] = pair.first;
        chartCount[left][right] = pair.second;
        storeProbs(theseForwardProb, forwardProb, linear[left][right]);
        storeProbs(theseInnerProb, innerProb, linear[left][right]);
      }
      //storeActiveEdgeChartProbs(i);
    }
    
    storePrefixProb(right);
  }
  
  // each agenda item is an int[3] corresponding to a <left,right,edge> triple
  //PriorityQueue agenda = new BinaryHeapPriorityQueue();
  /* end of process-specific dynamic resource class variables */

  /* Combines a passive edge backward.  Note that combine is always BACKWARDS COMBINATION 
   * of a passive edge with earlier actives */
  /**
   * Thang: completion. Given a completed state, passive, from [middle, end]
   * what other new states could be generated
   */
  private void combine(int left, int middle, int right, int passive, double inner) {
    // we already completed the edge, right: middle Y -> _ ., where passive represents for Y
    Completion[] completions = g.getCompletions(passive);
    for (int x = 0, n = completions.length; x < n; x++) { // go through all completions we could finish
      Completion completion = completions[x];
      
      if (chartEntries[linear[left][middle]][completion.activeState]) { // middle: left X -> _ . Y _
        double newForwardProb = forwardProb[linear[left][middle]][completion.activeState] + completion.score + inner;
        double newInnerProb = innerProb[linear[left][middle]][completion.activeState] + completion.score + inner;
        
        // add edge, right: left X -> _ Y . _, to tmp storage
        theseChartEntries[completion.completedState] = true;
        theseForwardProb[completion.completedState].add(newForwardProb);
        theseInnerProb[completion.completedState].add(newInnerProb);
       
        if (verbose >= 3) {
          String newEdge = edgeInfo(left, right, completion.completedState, newForwardProb, newInnerProb);
          String startEdge = edgeInfo(left, middle, completion.activeState, 
              forwardProb[linear[left][middle]][completion.activeState], 
              innerProb[linear[left][middle]][completion.activeState]);
          String endEdge = edgeInfo(middle, right, passive, forwardProb[linear[middle][right]][passive], 
              innerProb[linear[middle][right]][passive]);
          System.err.println("Complete: " + startEdge + " * " + endEdge + "* closure=" + 
              df.format(Math.exp(completion.score)) + " -> "  + newEdge);

          if (completion.completedState == rootEdge) {
            System.err.println("String prob +=" + Math.exp(newInnerProb));
          }
        }

        //also a careful addition to the prefix probabilities -- is this right?
        if (middle == right - 1) {
          //prefixProb[k] = SloppyMath.logAdd(newForwardProb,prefixProb[kv/]);
          thisPrefixProb.add(newForwardProb);
          double synProb = newForwardProb - inner;
          thisSynPrefixProb.add(synProb); // minus the lexical score
          if (verbose >= 2) {
            System.err.println("prefix prob += " + Math.exp(newForwardProb));
          }
        }
      }
    }
  }
  
  
  private void addPrefixProbExtendedRule(int left, int middle, int right, int passive, double inner) {
    assert((right-middle)>=2);
    Completion[] completions = g.getCompletions(passive);
    
    for (int x = 0, n = completions.length; x < n; x++) {
      Completion completion = completions[x];
      
      int activeChild = completion.activeState;
      if (chartEntries[linear[left][middle]][activeChild]) {
        // we are using trie, and there's an extended rule that could be used to update prefix prob
        double prefixScore = forwardProb[linear[left][middle]][activeChild] + 
                             completion.score + inner;
        thisPrefixProb.add(prefixScore);
        thisSynPrefixProb.add(prefixScore); // TODO: compute syntactic scores for AG
        
        
        if (verbose >= 3) {
          String startEdge = edgeInfo(left, middle, activeChild, 
              forwardProb[linear[left][middle]][activeChild], 
              innerProb[linear[left][middle]][activeChild]);
          String endEdge = edgeInfo(middle, right, passive, forwardProb[linear[middle][right]][passive], 
              innerProb[linear[middle][right]][passive]);
          System.err.println("AG Complete: " + startEdge + " * " + endEdge + "* closure=" + 
              df.format(Math.exp(completion.score)));
          System.err.println("prefix prob AG += " + Math.exp(prefixScore));
        }
      }
    }
  }    

  private void storeProbs(DoubleList[] dl, double[][] probs, int index) {
    for (int edge = 0; edge < dl.length; edge++) {
      double[] temps = dl[edge].toArray();
      if (temps.length > 0) {
        probs[index][edge] = SloppyMath.logAdd(ArrayMath.logSum(temps), probs[index][edge]);
      }
    }
  }
  
  /**
   * Add this state into cell [left, right]
   */
  protected void addToChart(int left, int right, int edge,  
      double logForward, double logInner) {
    if (!chartEntries[linear[left][right]][edge]) {
      chartEntries[linear[left][right]][edge] = true;
      forwardProb[linear[left][right]][edge] = logForward;
      innerProb[linear[left][right]][edge] = logInner;
      chartCount[left][right]++; // increase count of categories
    } else {
      forwardProb[linear[left][right]][edge] = SloppyMath.logAdd(logForward, 
          forwardProb[linear[left][right]][edge]);
      innerProb[linear[left][right]][edge] = SloppyMath.logAdd(logInner, 
          innerProb[linear[left][right]][edge]);
    }
    if(verbose >= 2){
      System.err.println("Add edge: " + edgeInfo(left, right, edge));
    }
  }

  private Pair<boolean[], Integer> booleanUnion(boolean[] b1, boolean[] b2) {
    int n = b1.length;
    assert n == b2.length;
    boolean[] result = new boolean[n];
    int count = 0;
    for (int i = 0; i < n; i++) {
      result[i] = b1[i] || b2[i];
      
      if (result[i]){
        count++;
      }
    }
    return new Pair<boolean[], Integer>(result, count);
  }
  
  /**
   * Returns the total probability of complete parses for the string prefix parsed so far.
   */
  public double stringProbability(int rightEdge) {
    int index = linear[0][rightEdge];
    if (chartEntries[index][rootEdge]) {
      assert(Math.abs(innerProb[index][rootEdge] - forwardProb[index][rootEdge]) < 1e-5);
      return innerProb[index][rootEdge];
    } else {
      return Double.NEGATIVE_INFINITY;
    }
  }

  /** Output debug info **/
  public String edgeInfo(int left, int right, int edge){
    return edgeInfo(left, right, edge, forwardProb[linear[left][right]][edge], innerProb[linear[left][right]][edge]);
  }
  
  public String edgeInfo(int left, int right, int edge, double logForward, double logInner){
    return right + ": " + left + " " + 
    g.getStateSpace().get(edge).toString(TAG_INDEX, WORD_INDEX) + " [" + 
    df.format(Math.exp(logForward)) + ", " + 
    df.format(Math.exp(logInner)) + "]";
  }
  
  void dumpChart() {
    System.err.println("## Chart, edge space size = " + edgeSpaceSize);
    for (int left = 0; left <= numWords; left++) {
      for (int right = left; right <= numWords; right++) {
        if(chartCount[left][right]>0){ // there're active states
          System.err.println("[" + left + ", " + right + "]: " 
              + chartCount[left][right] + " (" + chartCount[left][right]*100.0/edgeSpaceSize + "%)\n");
          for (int edge = 0; edge < chartEntries[linear[left][right]].length; edge++) {
            if (chartEntries[linear[left][right]][edge]) {
              System.err.println("\t" + g.getStateSpace().get(edge).toString() 
                  + ") : " + Math.exp(forwardProb[linear[left][right]][edge]) + " " + Math.exp(innerProb[linear[left][right]][edge]));
            }
          }
        }
      }
    }
  }
}
