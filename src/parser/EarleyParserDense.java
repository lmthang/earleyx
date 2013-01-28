package parser;

import java.io.BufferedReader;
import java.util.HashSet;
import java.util.Set;

import util.Util;

import edu.stanford.nlp.util.DoubleList;
import edu.stanford.nlp.util.Pair;
import edu.stanford.nlp.util.Timing;

public class EarleyParserDense extends EarleyParser{
  protected boolean[][] chartEntries; // chartEntries[linear(left, right)][edge]
  protected double[][] forwardProb;
  protected double[][] innerProb;
  protected int[] chartCount; // chartCount[linear(left, right)]: how many edges at the cell [left, right]
  
  // outside
  protected boolean[][] outsideChartEntries;
  protected int[] outsideChartCount;
  protected double[][] outerProb;

  public EarleyParserDense(BufferedReader br, String rootSymbol,
      boolean isScaling, boolean isLogProb, int insideOutsideOpt,
      String objString) {
    super(br, rootSymbol, isScaling, isLogProb, insideOutsideOpt, objString);
    isFastComplete = false;
    // TODO Auto-generated constructor stub
  }

  public EarleyParserDense(String grammarFile, int inGrammarType,
      String rootSymbol, boolean isScaling, boolean isLogProb,
      int insideOutsideOpt, String objString) {
    super(grammarFile, inGrammarType, rootSymbol, isScaling, isLogProb,
        insideOutsideOpt, objString);
    isFastComplete = false;
    // TODO Auto-generated constructor stub
  }

  protected void sentInit(){
    super.sentInit();
    
    if (verbose>=2){
      System.err.println("# EarleyParserDense initializing ... ");
      Timing.startTime();
    }
  
    int numCells = linear(0, numWords+1);
    chartEntries = new boolean[numCells][edgeSpaceSize];
    forwardProb = new double[numCells][edgeSpaceSize];
    innerProb = new double[numCells][edgeSpaceSize];
    chartCount = new int[numCells];
    Util.init(forwardProb, operator.zero());
    Util.init(innerProb, operator.zero());
  }

  
    
  @Override
  protected void initOuterProbs() {
    int numCells = linear(0, numWords+1);
    outerProb = new double[numCells][edgeSpaceSize];
    outsideChartCount = new int[numCells];
    outsideChartEntries = new boolean[numCells][edgeSpaceSize];
    Util.init(outerProb, operator.zero());    
  }



  // to avoid concurrently modify insideChart while performing completions
  protected boolean[] tempIOEntries;
  protected DoubleList[] tempInsideProbs = new DoubleList[numCategories];
  
  
      
  protected void addPrefixProbExtendedRule(int left, int middle, int right, int edge, double inner) {    
    int tag = edgeSpace.get(edge).getMother();
    assert(edgeSpace.get(edge).numRemainingChildren()==0);
    
    Completion[] completions = g.getCompletions(tag);
    
    if (verbose>=3 && completions.length>0){
      System.err.println(completionInfo(middle, right, edge, inner, completions));
    }
   
    if (isScaling){
      inner = operator.multiply(inner, getScaling(middle, right));
    }
    
    int lmIndex = linear(left, middle); // left middle index
    
    // the current AG rule: Y -> w_middle ... w_(right-1) .
    for (int x = 0, n = completions.length; x < n; x++) {
      Completion completion = completions[x];
     
      if (chartEntries[lmIndex][completion.activeEdge]){
        // we are using trie, and there's an extended rule that could be used to update prefix prob
        double prefixScore = operator.multiply(forwardProb[lmIndex][completion.activeEdge], 
                               operator.multiply(completion.score, inner));
        
        thisPrefixProb.add(prefixScore);
        thisSynPrefixProb.add(prefixScore); // TODO: compute syntactic scores for AG
        
        if (verbose >= 3) {
          System.err.println("  start " + edgeScoreInfo(left, middle, completion.activeEdge));
        }
        
        if (verbose >= 2) {
          System.err.println("  # prefix prob AG += " + operator.getProb(prefixScore) + "(" +  
              operator.getProb(forwardProb[lmIndex][completion.activeEdge]) + "*" +
              operator.getProb(completion.score) + "*" + operator.getProb(inner)  + ")");
        }
      }
    }
  } 
  
  private void storeProbs(DoubleList[] dl, double[][] probs, int index) {
    for (int edge = 0; edge < dl.length; edge++) {
      double[] temps = dl[edge].toArray();
      if (temps.length > 0) {
        probs[index][edge] = operator.add(operator.arraySum(temps), probs[index][edge]);
      }
    }
  }

  
  /**
  * Add this state into cell [left, right]
  */
  protected void addToChart(int left, int right, int edge,  
     double forward, double inner) {
    int lrIndex = linear(left, right); // left right index
    assert(chartEntries[lrIndex][edge] == false);
   
    chartEntries[lrIndex][edge] = true;
    forwardProb[lrIndex][edge] = forward;
    innerProb[lrIndex][edge] = inner;
    
    chartCount[lrIndex]++; // increase count of categories
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

  
  @Override
  protected boolean containsInsideEdge(int left, int right, int edge) {
    return chartEntries[linear(left, right)][edge];
  }

  @Override
  protected int insideChartCount(int left, int right) {
    return chartCount[linear(left, right)];
  }

  
  @Override
  protected Set<Integer> listInsideEdges(int left, int right) {
    Set<Integer> edges = new HashSet<Integer>();
    for (int edge = 0; edge < edgeSpaceSize; edge++) {
      if(containsInsideEdge(left, right, edge)){
        edges.add(edge);
      }
    }
    return edges;
  }

  
  @Override
  protected boolean containsOutsideEdge(int left, int right, int edge) {
    return outsideChartEntries[linear(left, right)][edge];
  }

  @Override
  protected int outsideChartCount(int left, int right) {
    return outsideChartCount[linear(left, right)];
  }

  @Override
  protected Set<Integer> listOutsideEdges(int left, int right) {
    Set<Integer> edges = new HashSet<Integer>();
    for (int edge = 0; edge < edgeSpaceSize; edge++) {
      if(containsOutsideEdge(left, right, edge)){
        edges.add(edge);
      }
    }
    return edges;
  }


  /****************************/
  /** Temporary prob methods **/
  /***************************/
  /** Used as holding zone for predictions **/
  private boolean[] predictedChartEntries;
  private double[] predictedForwardProb;
  private double[] predictedInnerProb;
  private int predictedChartCount;
  
  @Override
  protected void initPredictTmpScores() {
    predictedChartEntries = new boolean[edgeSpaceSize];
    predictedForwardProb = new double[edgeSpaceSize];
    predictedInnerProb = new double[edgeSpaceSize];
    predictedChartCount = 0;
    Util.init(predictedForwardProb, operator.zero());
    Util.init(predictedInnerProb, operator.zero()); 
  }

  @Override
  protected void addPredictTmpForwardScore(int edge, double score) {
    if (!predictedChartEntries[edge]){
      predictedChartEntries[edge] = true;
      predictedChartCount++; // count for new edge [right, right]
    }
    predictedForwardProb[edge] = operator.add(predictedForwardProb[edge], score);
  }

  @Override
  protected void addPredictTmpInnerScore(int edge, double score) {
    predictedInnerProb[edge] = score;
  }

  @Override
  protected void storePredictTmpScores(int right) {
    // replace old entries with recently predicted entries
    // all predictions will have the form right: right Y -> _
    int rrIndex = linear(right, right); // right right index
    chartEntries[rrIndex] = predictedChartEntries;
    forwardProb[rrIndex] = predictedForwardProb;
    innerProb[rrIndex] = predictedInnerProb;
    chartCount[rrIndex] = predictedChartCount;
  }
  
  /** Used as holding zones for completions **/
  protected boolean[] theseChartEntries;
  protected DoubleList[] theseForwardProb = new DoubleList[edgeSpaceSize];
  protected DoubleList[] theseInnerProb = new DoubleList[edgeSpaceSize];
  
  @Override
  protected void initCompleteTmpScores() {
    theseChartEntries = new boolean[edgeSpaceSize];
    for (int i = 0; i < edgeSpaceSize; i++) {
      theseForwardProb[i] = new DoubleList();
      theseInnerProb[i] = new DoubleList();
    }
  }

  @Override
  protected void storeCompleteTmpScores(int left, int right) {
    int lrIndex = linear(left, right);
    Pair<boolean[], Integer> pair = booleanUnion(chartEntries[lrIndex], theseChartEntries);;
    chartEntries[lrIndex] = pair.first;
    chartCount[lrIndex] = pair.second;
    storeProbs(theseForwardProb, forwardProb, lrIndex);
    storeProbs(theseInnerProb, innerProb, lrIndex);
  }

  @Override
  protected void initCompleteTmpScores(int edge) {
    theseChartEntries[edge] = true;
  }
  
  @Override
  protected void addCompleteTmpForwardScore(int edge, double score) {
    theseForwardProb[edge].add(score);
  }

  @Override
  protected void addCompleteTmpInnerScore(int edge, double score) {
    theseInnerProb[edge].add(score);
  }
  
  /****************************/
  /** Forward probabilities **/
  /***************************/
  protected double getForwardScore(int left, int right, int edge){
    return forwardProb[linear(left, right)][edge];
  }
  
  protected void addForwardScore(int left, int right, int edge, double score){
    forwardProb[linear(left, right)][edge] = 
      operator.add(forwardProb[linear(left, right)][edge], score);
  }
  
  /*************************/
  /** Inner probabilities **/
  /*************************/
  protected double getInnerScore(int left, int right, int edge){
    return innerProb[linear(left, right)][edge];
  }
  
  protected void addInnerScore(int left, int right, int edge, double score){
    innerProb[linear(left, right)][edge] = 
      operator.add(innerProb[linear(left, right)][edge], score);
  }
  
  /*************************/
  /** Outer probabilities **/
  /*************************/
  protected double getOuterScore(int left, int right, int edge){
    return outerProb[linear(left, right)][edge];
  }

  @Override
  protected void addOuterScore(int left, int right, int edge, double score) {
    outerProb[linear(left, right)][edge] = 
      operator.add(outerProb[linear(left, right)][edge], score);
    
    if (!outsideChartEntries[linear(left, right)][edge]){
      outsideChartEntries[linear(left, right)][edge] = true;
      outsideChartCount[linear(left, right)]++;
    }
  }

  /****************/
  /** Debug info **/
  /****************/
  public String edgeScoreInfo(int left, int right, int edge){
    return edgeScoreInfo(left, right, edge, forwardProb[linear(left, right)][edge], innerProb[linear(left, right)][edge]);
  }
}

///** Unused code **/

//protected String dumpChart(double[][] chart, String type) {
//  if(!type.equalsIgnoreCase("outside") && !type.equalsIgnoreCase("inside")){
//    System.err.println("! computeChart: Unknown chart type " + type);
//    System.exit(1);
//  }
//  
//  StringBuffer sb = new StringBuffer("# " + type + " chart snapshot\n");
//  
//  for(int length=1; length<=numWords; length++){ // length
//    for (int left = 0; left <= numWords-length; left++) {
//      int right = left+length;
//      
//      // scaling
//      double scalingFactor = operator.one();
//      if (isScaling){
//        if (type.equalsIgnoreCase("outside")){ // outside prob has a scaling factor for [0,left][right, numWords]
//          scalingFactor = operator.multiply(getScaling(0, left), getScaling(right, numWords));
//        } else {
//          scalingFactor = getScaling(left, right);
//        }
//      }
//
//      int lrIndex = linear(left, right);
//      if(chartCount[lrIndex]>0){ // there're active states
//        int count = chartCount[lrIndex];
//        sb.append("[" + left + "," + right + "]: " + count  
//            + " (" + df1.format(count*100.0/edgeSpaceSize) + "%)\n");
//        
//        for (int edge = 0; edge < chartEntries[lrIndex].length; edge++) { // edge
//          if(chartEntries[lrIndex][edge]){
//            sb.append("  " + edgeSpace.get(edge).toString(parserTagIndex, parserWordIndex) 
//                + ": " + df.format(operator.getProb(operator.divide(chart[lrIndex][edge], scalingFactor))) + "\n");
//          }
//        }
//      }
//    }
//  }
//  
//  return sb.toString();
//}
//
//public String dumpInnerProb(){
//  return dumpChart(innerProb, "Inner");
//}
//
//public String dumpOuterProb(){
//  return dumpChart(outerProb, "Outer");
//}
//public String dumpInsideChart() {
//  return dumpChart(innerProb, true, false, "Inside");
////  return dumpCatChart(computeChart("Inside"), "Inside");
//}

//@Override
///** TODO: compute expected counts **/
//protected void outside(int start, int end, int rootEdge, double rootInsideScore) {
//  assert(start<end);
//
//  // configurations.get(linear(left, right)): set of edges to compute outside
//  Map<Integer, Set<Integer>> configurations = new HashMap<Integer, Set<Integer>>();
//  
//  // init
//  for(int length=end-start; length>=0; length--){
//    for (int left=0; left<=end-length; left++){
//      int right = left+length;
//      
//      configurations.put(linear(left, right), new HashSet<Integer>());
//    }
//  }
//  configurations.get(linear(start, end)).add(rootEdge); // add starting edge
//  
//  // outside
//  for(int length=end-start; length>=0; length--){
//    for (int left=0; left<=end-length; left++){
//      int right = left+length;
//      
//      Set<Integer> edges = configurations.get(linear(left, right));
//      while(edges.size()>0){ // while there're still edges to consider
//        Integer[] copyEdges = edges.toArray(new Integer[0]);
//        for(int edge : copyEdges){
//          double parentOutside = outerProb[linear(left, right)][edge];
//          Edge edgeObj = edgeSpace.get(edge);
//          
//          if(edgeObj.getDot()>0){ // X -> _ Z . \alpha
//            if(verbose>=3){
//              System.err.println("## " + outsideInfo(left, right, edge));
//            }
//            
//            int prevTag = edgeObj.getChild(edgeObj.getDot()-1); // Z
//            Edge prevEdgeObj = new Edge(edgeObj.getRule(), edgeObj.getDot()-1); // X -> _ . Z \alpha
//            int prevEdge = edgeSpace.indexOf(prevEdgeObj);
//            if(verbose>=4){
//              System.err.println("  prev edge " + prevEdgeObj.toString(parserTagIndex, parserWordIndex));
//            }
//            
//            for(int middle=right-1; middle>=0; middle--){ // middle
//              if(chartEntries[linear(left, middle)][prevEdge]){
//                double leftInside = innerProb[linear(left, middle)][prevEdge];
//                if(verbose>=4){
//                  System.err.println("  left inside [" + left + ", " + middle + "] " + operator.getProb(leftInside));
//                }
//                
//                for(int nextEdge : completedEdges.get(linear(middle, right))){ // Y -> v .
//                  Edge nextEdgeObj = edgeSpace.get(nextEdge);
//                  int nextTag = nextEdgeObj.getMother();
//                  double unaryScore = g.getUnaryClosures().get(prevTag, nextTag);
//                  
//                  if(unaryScore > operator.zero()) { // positive R(Z -> Y)
//                    double rightInside = innerProb[linear(middle, right)][nextEdge];
//                    
//                    if(verbose>=4) {
//                      System.err.println("    next edge [" + middle + ", " + right + "] " + 
//                          nextEdgeObj.toString(parserTagIndex, parserTagIndex) + 
//                          ", right inside " + operator.getProb(rightInside) + 
//                          ", unary(" + parserTagIndex.get(prevTag) + "->" 
//                          + parserTagIndex.get(nextTag) + ")=" + operator.getProb(unaryScore));
//                    }
//                    
//                    // left outside = parent outside * right inside
//                    addOuterScore(left, middle, prevEdge, 
//                        operator.multiply(parentOutside, rightInside), rootInsideScore);
//                    
//                    // right outside = parent outside * left inside * unary score
//                    addOuterScore(middle, right, nextEdge, 
//                        operator.multiply(operator.multiply(parentOutside, leftInside), unaryScore), rootInsideScore);
//                    
//                    // to backtrack we might want to check if completedEdge has any children
//                    // if it has no, that means it was constructed directly from terminals
//                    
//                    // recursive call
//                    if(middle>left){
//                      assert(middle != right || prevEdge != edge);
//                      configurations.get(linear(left, middle)).add(prevEdge);
//                    }
//                    if(right>middle){ //  && nextEdgeObj.numChildren()>0 
//                      assert(middle != left || nextEdge != edge);
//                      configurations.get(linear(middle, right)).add(nextEdge);
//                    }
//                  }
//                } // end nextEdge
//              }
//            } // end for middle
//          }
//          
//          edges.remove(edge);
//        } // end for edge
//      } // end while
//    } // start
//  } // length
//  
//}

// inside-outside
//protected boolean[][] ioEntries; // ioEntries[linear(left, right)][edge]
//protected double[][] insideChart;
//protected double[][] outsideChart;
//protected int[] ioCount; // ioCount[linear(left, right)]: how many categories at the cell [left, right]

//// io init
//ioEntries = new boolean[numCells][numCategories];
//insideChart = new double[numCells][numCategories];
//outsideChart = new double[numCells][numCategories];
//ioCount = new int[numCells];
//Util.init(insideChart, operator.zero());
//Util.init(outsideChart, operator.zero());

//// each agenda item is an int[3] corresponding to a <left,right,edge> triple
////PriorityQueue agenda = new BinaryHeapPriorityQueue();
///* end of process-specific dynamic resource class variables */
//
///* Combines a passive edge backward.  Note that combine is always BACKWARDS COMBINATION 
// * of a passive edge with earlier actives */
///**
// * Thang: completion. Given a completed state, passive, from [middle, end]
// * what other new states could be generated
// */
//protected void complete(int left, int middle, int right, int edge, double inner) {
//  int tag = edgeSpace.get(edge).getMother();
//  assert(edgeSpace.get(edge).numRemainingChildren()==0);
//  
//  // we try to find other completed states that end at middle
//  // so that we could generate new states ending at right
//  // when middle == right-1, we will attempt to update the prefix probability
//  
//  // we already completed the edge, right: middle Y -> _ ., where passive represents for Y
//  Completion[] completions = g.getCompletions(tag);
//  
//  if (verbose>=3 && completions.length>0){
//    System.err.println(completionInfo(left, middle, right, edge, inner, completions));
//  }
//  
//  int lmIndex = linear(left, middle); // left middle index
//  for (int x = 0, n = completions.length; x < n; x++) { // go through all completions we could finish
//    Completion completion = completions[x];
//    
//    if (chartEntries[lmIndex][completion.activeEdge]) { // middle: left X -> _ . Y _
//      double updateScore = operator.multiply(completion.score, inner);
//      double newForwardProb = operator.multiply(forwardProb[lmIndex][completion.activeEdge], updateScore);
//      double newInnerProb = operator.multiply(innerProb[lmIndex][completion.activeEdge], updateScore);
//      
//      // add edge, right: left X -> _ Y . _, to tmp storage
//      theseChartEntries[completion.completedEdge] = true;
//      theseForwardProb[completion.completedEdge].add(newForwardProb);
//      theseInnerProb[completion.completedEdge].add(newInnerProb);
//      
//      // info to help outside computation later
//      if(insideOutsideOpt>0){
//        Edge edgeObj = edgeSpace.get(completion.completedEdge);
//        
//        if(edgeObj.numRemainingChildren()==0){ // complete right: left X -> _ Y .
//          completedEdges.get(linear(left, right)).add(completion.completedEdge);
//          
//          if(verbose>=3){
//            System.err.println("# Add completed edge for outside computation " + edgeInfo(left, right, completion.completedEdge));
//          }
//        }
//      }
//      
//      if (verbose >= 3) {
//        System.err.println("  start edge " + completion.activeEdge + ", "+ edgeScoreInfo(left, middle, completion.activeEdge) 
//            + " -> new edge " + completion.completedEdge + ", " + edgeScoreInfo(left, right, completion.completedEdge, newForwardProb, newInnerProb));
//
//        if (isGoalEdge(completion.completedEdge)) {
//          System.err.println("# String prob +=" + Math.exp(newInnerProb));
//        }
//      }
//
//      //also a careful addition to the prefix probabilities -- is this right?
//      if (middle == right - 1) {
//        thisPrefixProb.add(newForwardProb);
//        double synProb = operator.divide(newForwardProb, inner);
//        thisSynPrefixProb.add(synProb); // minus the lexical score
//        if (verbose >= 2) {
//          System.err.println("# Prefix prob += " + operator.getProb(newForwardProb) + "=" + 
//              operator.getProb(forwardProb[lmIndex][completion.activeEdge]) + "*" + 
//              operator.getProb(completion.score) + "*" + operator.getProb(inner) + "\t" + left + "\t" + middle + "\t" + completion.activeEdge);
//          System.err.println("# Syn prefix prob += " + operator.getProb(synProb) + "=" + 
//              operator.getProb(newForwardProb) + "/" + 
//              operator.getProb(inner));
//        }
//      }
//    }
//  }
//}

/**
 * Add this state into IO cell [left, right]
 */
//protected void addToIOChart(int left, int right, int tag,  double inner) {
//  int lrIndex = linear(left, right); // left right index
//  assert(ioEntries[lrIndex][tag] == false);
//  
//  ioEntries[lrIndex][tag] = true;
//  insideChart[lrIndex][tag] = inner;
//  ioCount[lrIndex]++; // increase count of categories
//}
//protected void dumpInsideChart() {
//  System.err.println("# Inside chart");
//  
//  for(int length=1; length<=numWords; length++){ // length
//    for (int left = 0; left <= numWords-length; left++) {
//      int right = left+length;
//
//      double scalingFactor = 0;
//      if(isScaling){
//        for(int i=left+1; i<=right; i++){
//          scalingFactor += scaling[i];
//        }
//      }
//      
//      int lrIndex = linear(left, right);
//      if(chartCount[lrIndex]>0){
//        System.err.println("cell " + left + "-" + right);
//        for(int edge=0; edge<edgeSpaceSize; edge++){
//          if(chartEntries[lrIndex][edge] && edgeSpace.get(edge).numRemainingChildren()==0){
//            System.err.println(" " + parserTagIndex.get(edgeSpace.get(edge).getMother()) 
//                + ": " + operator.getProb(innerProb[lrIndex][edge]-scalingFactor));
//          }
//        }
//      }
//    }
//  }
//}

///**
// * Due to the use of left-corner closures, predicted items themselves never cause further prediction.
// * @param right
// */
//protected void chartPredict(int right) {
//  // init
//  predictedChartEntries = new boolean[edgeSpaceSize];
//  predictedForwardProb = new double[edgeSpaceSize];
//  predictedInnerProb = new double[edgeSpaceSize];
//  predictedChartCount = 0;
//  Util.init(predictedForwardProb, operator.zero());
//  Util.init(predictedInnerProb, operator.zero());
//  
//  boolean flag = false;
//  for (int left = 0; left <= right; left++) {
//    int lrIndex = linear(left, right); // left right index
//    if (chartCount[lrIndex]==0){ // no active categories
//      continue;
//    }
//    if(verbose>=3){
//      System.err.println("\n# Predict all [" + left + "," + right + "]: chart count=" + chartCount[linear(left, right)]);
//    }
//    
//    flag = true;
//    for (int edge = 0; edge < chartEntries[lrIndex].length; edge++) { // category
//      if (chartEntries[lrIndex][edge]) { // this state is active
//        // predict for right: left X -> _ . Y _
//        predictFromEdge(left, right, edge);
//      }
//    }
//  }
//  
//  // replace old entries with recently predicted entries
//  // all predictions will have the form right: right Y -> _
//  int rrIndex = linear(right, right); // right right index
//  chartEntries[rrIndex] = predictedChartEntries;
//  forwardProb[rrIndex] = predictedForwardProb;
//  innerProb[rrIndex] = predictedInnerProb;
//  chartCount[rrIndex] = predictedChartCount;
//  if (verbose >= 3 && flag) {
//    dumpChart();
//  }
//}
//
//// predict for the edge that spans [left, right]
//protected void predictFromEdge(int left, int right, int edge) {
//  Prediction[] predictions = g.getPredictions(edge);
//  if (verbose >= 3 && predictions.length>0) {
//    System.err.println("From edge " + edgeScoreInfo(left, right, edge));
//  }
//  for (int x = 0, n = predictions.length; x < n; x++) { // go through each prediction
//    Prediction p = predictions[x];
//    
//    // spawn new edge
//    int newEdge = p.predictedState;
//    double newForwardProb = operator.multiply(forwardProb[linear(left, right)][edge], p.forwardProbMultiplier);
//    double newInnerProb = p.innerProbMultiplier;
//    
//    // add to tmp arrays
//    if (!predictedChartEntries[newEdge]){
//      predictedChartEntries[newEdge] = true;
//      predictedChartCount++; // count for new edge [right, right]
//    }
//    predictedForwardProb[newEdge] = operator.add(predictedForwardProb[newEdge], newForwardProb);
//    predictedInnerProb[newEdge] = newInnerProb;
//    
//    if (verbose >= 3) {
//      System.err.println("  to " + edgeScoreInfo(right, right, newEdge, newForwardProb, newInnerProb));
//    }
//  }
//}

//@Override
//protected void cellComplete(int left, int middle, int right) {
//  int mrIndex = linear(middle, right); // middle right index
//  if(verbose>=3){
//    System.err.println("\n# Complete all [" + left + "," + middle + "," + right + "]: chartCount[" 
//        + middle + "," + right + "]=" + chartCount[mrIndex]);
//  }
//  
//  // init
//  theseChartEntries = new boolean[edgeSpaceSize];
//  for (int i = 0; i < edgeSpaceSize; i++) {
//    theseForwardProb[i] = new DoubleList();
//    theseInnerProb[i] = new DoubleList();
//  }
//  
//  // tag completions
//  if (chartCount[mrIndex]>0){ // there're active edges for the span [middle, right]
//    // check which categories have finished expanding [middle, right]
//    for (int edge = edgeSpaceSize - 1; edge >= 0; edge--) { // TODO: we could be faster here by going through only passive edges, Thang: why do we go back ward in edge ??
//    //for(int tag=0; tag<parserTagIndex.size(); tag++){
//      //int edge = edgeSpace.indexOfTag(tag);
//      if (chartEntries[mrIndex][edge] && edgeSpace.to(edge)==-1) { // right: middle Y -> _ .
//        complete(left, middle, right, edge, innerProb[mrIndex][edge]); // in completion the forward prob of Y -> _ . is ignored
//      }
//    }
//  } 
//  
//  
//  /** Handle multi-terminal rules **/
//  if(hasMultiTerminalRule){
//    handleMultiTerminalRules(left, middle, right);
//  }
//  
//  // completions yield edges: right: left X -> _ Y . _
//  int lrIndex = linear(left, right);
//  Pair<boolean[], Integer> pair = booleanUnion(chartEntries[lrIndex], theseChartEntries);;
//  chartEntries[lrIndex] = pair.first;
//  chartCount[lrIndex] = pair.second;
//  storeProbs(theseForwardProb, forwardProb, lrIndex);
//  storeProbs(theseInnerProb, innerProb, lrIndex);
//}