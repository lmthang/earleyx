/**
 * 
 */
package parser;

import java.io.BufferedReader;
import java.util.HashMap;
import java.util.Map;
import java.util.Map.Entry;

import base.Edge;

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

  // insideChart.get(linear[left][right]).get(tagIndex) 
  protected Map<Integer, Map<Integer, Double>> insideChart;
  protected Map<Integer, Map<Integer, Double>> outsideChart;
  
  /* for inside-outside computation, currently works when isLeftWildcard=false */
  protected Map<Integer, Map<Integer, Double>> outerProb;     // innerProb.get(linear[left][right]).get(edge)
  
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
   
    for (int i = 0; i < numCells; i++) {
      forwardProb.put(i, new HashMap<Integer, Double>());
      innerProb.put(i, new HashMap<Integer, Double>());
    }
    
    // compute outside
    if(isComputeOutside){
      outerProb = new HashMap<Integer, Map<Integer,Double>>();
      
      for (int i = 0; i < numCells; i++) {
        outerProb.put(i, new HashMap<Integer, Double>());
      }
    }
  }
  
  /* (non-Javadoc)
   * @see parser.EarleyParser#addToChart(int, int, int, double, double)
   */
  @Override
  protected void addToChart(int left, int right, int edge, double logForward,
      double logInner) {
    int lrIndex = linear[left][right]; // left right index
    
    assert(!forwardProb.get(lrIndex).containsKey(edge));
    forwardProb.get(lrIndex).put(edge, logForward);
    innerProb.get(lrIndex).put(edge, logInner);
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
      System.err.println("From edge " + edgeScoreInfo(left, right, edge));
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
        System.err.println("  to " + edgeScoreInfo(right, right, newEdge, newForwardProb, newInnerProb));
      }
    }
  }

  /** Used as holding zones for completions **/
  protected Map<Integer, DoubleList> theseForwardProb;
  protected Map<Integer, DoubleList> theseInnerProb;
  protected Map<Integer, DoubleList> tempInsideProbs; // to avoid concurrently modify insideChart while performing completions
  
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
    for(int edge : forwardProb.get(mrIndex).keySet()){
      if(edgeSpace.to(edge) == -1){ // no more child after the dot
        // right: middle Y -> _ .
        // in completion the forward prob of Y -> _ . is ignored
        complete(left, middle, right, edgeSpace.get(edge).getMother(), innerProb.get(mrIndex).get(edge)); 
      }
    }
    
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
    
    if(verbose>=4){
      dumpInsideChart();
    }
  }
  
  protected void complete(int left, int middle, int right, int tag, double inner) {
    // we already completed the edge, right: middle Y -> _ ., where passive represents for Y
    Completion[] completions = g.getCompletions(tag);
    
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
    
        
        // info to help outside computation later
        if(isComputeOutside){
          Edge edgeObj = edgeSpace.get(completion.completedEdge);
          
          if(edgeObj.numRemainingChildren()==0){ // complete right: left X -> _ Y .
            completedEdges.get(linear[left][right]).add(completion.completedEdge);
            
            if(verbose>=3){
              System.err.println("# Add completed edge for outside computation " + edgeInfo(left, right, completion.completedEdge));
            }
          }
        }
        
        if (verbose >= 3) {
          System.err.println("  start " + edgeScoreInfo(left, middle, completion.activeEdge) 
              + " -> new " + edgeScoreInfo(left, right, completion.completedEdge, newForwardProb, newInnerProb));

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
    }
  }
  
  protected void addPrefixProbExtendedRule(int left, int middle, int right, int tag, double inner) {    
//    int passive = edgeSpace.indexOfTag(tag);
    Completion[] completions = g.getCompletions(tag);
    
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
          System.err.println("  start " + edgeScoreInfo(left, middle, completion.activeEdge));
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
  public String edgeScoreInfo(int left, int right, int edge) {
    int index = linear[left][right];
    if(forwardProb.containsKey(index) && forwardProb.get(index).containsKey(edge)){
      return edgeScoreInfo(left, right, edge, forwardProb.get(index).get(edge), innerProb.get(index).get(edge));
    } else {
      return edgeScoreInfo(left, right, edge, operator.zero(), operator.zero());
    }    
  }

  
  
  protected void outside(int left, int right, int edge){
    assert(left<right);
    double parentOutside = outerProb.get(linear[left][right]).get(edge);
    Edge edgeObj = edgeSpace.get(edge);
    
    if(edgeObj.getDot()>0){ // X -> _ Z . \alpha
      if(verbose>=3){
        System.err.println("## " + outsideInfo(left, right, edge));
      }
      
      int prevTag = edgeObj.getChild(edgeObj.getDot()-1); // Z
      Edge prevEdgeObj = new Edge(edgeObj.getRule(), edgeObj.getDot()-1); // X -> _ . Z \alpha
      int prevEdge = edgeSpace.indexOf(prevEdgeObj);
      if(verbose>=4){
        System.err.println("  prev edge " + prevEdgeObj.toString(parserTagIndex, parserTagIndex));
      }
      
      for(int middle=right-1; middle>=0; middle--){ // middle
        if(innerProb.get(linear[left][middle]).containsKey(prevEdge)){
          double leftInside = innerProb.get(linear[left][middle]).get(prevEdge);
          if(verbose>=4){
            System.err.println("  left inside [" + left + ", " + middle + "] " + operator.getProb(leftInside));
          }
          
          for(int nextEdge : completedEdges.get(linear[middle][right])){ // Y -> v .
            Edge nextEdgeObj = edgeSpace.get(nextEdge);
            int nextTag = nextEdgeObj.getMother();
            double unaryScore = g.getUnaryClosures().get(prevTag, nextTag);
            
            if(unaryScore > operator.zero()) { // positive R(Z -> Y)
              double rightInside = innerProb.get(linear[middle][right]).get(nextEdge);
              
              if(verbose>=4) {
                System.err.println("    next edge [" + middle + ", " + right + "] " + 
                    nextEdgeObj.toString(parserTagIndex, parserTagIndex) + 
                    ", right inside " + operator.getProb(rightInside) + 
                    ", unary(" + parserTagIndex.get(prevTag) + "->" 
                    + parserTagIndex.get(nextTag) + ")=" + operator.getProb(unaryScore));
              }
              
              // left outside = parent outside * right inside
              addOutsideScore(left, middle, prevEdge, operator.multiply(parentOutside, rightInside));
              
              // right outside = parent outside * left inside * unary score
              addOutsideScore(middle, right, nextEdge, operator.multiply(operator.multiply(parentOutside, leftInside), unaryScore));
              
              // to backtrack we might want to check if completedEdge has any children
              // if it has no, that means it was constructed directly from terminals
              
              // recursive call
              if(middle>left){
                outside(left, middle, prevEdge);
              }
              if(right>middle){ //  && nextEdgeObj.numChildren()>0 
                outside(middle, right, nextEdge);
              }
            }
          }
        }
      }
    }
  }
  
  protected void addInsideScore(int left, int right, int edge, double score){
    addScore(innerProb.get(linear[left][right]), edge, score);
  }
  
  protected double getInsideScore(int left, int right, int edge){
    if(!innerProb.get(linear[left][right]).containsKey(edge)){
      return operator.zero();
    } else {
      return innerProb.get(linear[left][right]).get(edge);
    }
  }
  
  protected void addOutsideScore(int left, int right, int edge, double score){
    addScore(outerProb.get(linear[left][right]), edge, score);
        
    if(verbose>=3){
      System.err.println("      after adding " + operator.getProb(score) + ", " + outsideInfo(left, right, edge));
    }
  }
  
  protected void addScore(Map<Integer, Double> probMap, int edge, double score){
    if(!probMap.containsKey(edge)){
      probMap.put(edge, score);
    } else {
      probMap.put(edge, operator.add(probMap.get(edge), score));
    }
  }
  
  protected String outsideInfo(int left, int right, int edge){
    return "outside ([" + left + ", " + right + "], " 
        + edgeSpace.get(edge).toString(parserTagIndex, parserTagIndex) + ", " + 
        operator.getProb(outerProb.get(linear[left][right]).get(edge)) + ")";
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
  protected void dumpChart(Map<Integer, Map<Integer, Double>> chart, boolean isOnlyCategory){
    System.err.println("# Chart snapshot, onlyCategory=" + isOnlyCategory);
    for(int length=1; length<=numWords; length++){ // length
      for (int left = 0; left <= numWords-length; left++) {
        int right = left+length;

        // scaling
        double scalingFactor = 0;
        if(isScaling){
          for(int i=left+1; i<=right; i++){
            scalingFactor += scaling[i];
          }
        }
        
        int lrIndex = linear[left][right];
        Map<Integer, Double> insideMap = chart.get(lrIndex);
        if(insideMap.size()>0){ // there're active states
          if(isOnlyCategory){
            System.err.println("cell " + left + "-" + right);
          } else {
            int count = insideMap.size();
            System.err.println("[" + left + "," + right + "]: " + count  
                + " (" + df1.format(count*100.0/edgeSpaceSize) + "%)");  
          }
          
          for (int edge : insideMap.keySet()) { // edge
            Edge edgeObj = edgeSpace.get(edge);
          
            if(isOnlyCategory){
              if(edgeObj.numRemainingChildren()==0){ // completed edge
                System.err.println(" " + parserTagIndex.get(edgeObj.getMother()) 
                   + ": " + operator.getProb(insideMap.get(edge)-scalingFactor));
              }
            } else {
              System.err.println("  " + edgeSpace.get(edge).toString(parserTagIndex, parserTagIndex) 
                  + ": " + df.format(operator.getProb(insideMap.get(edge))));
            }
          }
        }
      }
    }    
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
  
  public void dumpInnerProb(){
    dumpChart(innerProb, false);
  }
  
  public void dumpOuterProb(){
    dumpChart(outerProb, false);
  }
  
  @Override
  public void dumpInsideChart() {
    dumpChart(innerProb, true);
  }
  
  @Override
  public void dumpOutsideChart() {
    dumpChart(outerProb, true);
  }
  
  
}

///** Unused code **/
//@Override
//protected void addToIOChart(int left, int right, int tag, double inner) {
//  int lrIndex = linear[left][right]; // left right index
//  
//  assert(!insideChart.get(lrIndex).containsKey(tag));
//  insideChart.get(lrIndex).put(tag, inner);
//  
//  if(isComputeOutside){
//    int edge = edgeSpace.indexOfTag(tag);
//    completedEdges.get(linear[left][right]).add(edge);
//    
//    if(verbose>=3){
//      System.err.println("# Add completed edge for outside computation [" + left + ", " + right + "] " 
//          + edgeSpace.get(edge).toString(parserTagIndex, parserTagIndex));
//    }
//  }
//}

///* (non-Javadoc)
// * @see parser.EarleyParser#dumpInnerChart()
// */
//@Override
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
//      int lrIndex = linear[left][right];
//      Map<Integer, Double> insideMap = innerProb.get(lrIndex);
//      if(insideMap.size()>0){ // there're active states
//        System.err.println("cell " + left + "-" + right);
//        for (int edge : insideMap.keySet()) {
//          Edge edgeObj = edgeSpace.get(edge);
//          if(edgeObj.numRemainingChildren()==0){
//            System.err.println(" " + parserTagIndex.get(edgeObj.getMother()) 
//               + ": " + operator.getProb(insideMap.get(edge)-scalingFactor));
//          }
//        }
//      }
//    }
//  }
//
//}

//protected void dumpChart(Map<Integer, Map<Integer, Double>> chart) {
//  
//  for (int left = 0; left <= numWords; left++) {
//    for (int right = left; right <= numWords; right++) {
//      int lrIndex = linear[left][right];
//      
//      int count = chart.containsKey(lrIndex) ? chart.get(lrIndex).size() : 0;
//      if(count>0){ // there're active states
//        assert(chart.get(lrIndex).size()>0);
//        
//        
//        for (int edge : chart.get(lrIndex).keySet()) {
//          System.err.println("  " + edgeSpace.get(edge).toString(parserTagIndex, parserTagIndex) 
//              + ": " + df.format(operator.getProb(chart.get(lrIndex).get(edge))));
//        }
//      }
//    }
//  }
//}
//

///**
// * Returns the total probability of complete parses for the string prefix parsed so far.
// */
//public double stringProbability(int right) {
//  int index = linear[0][right];
//  double stringProb = operator.zero();
//  
//  if (forwardProb.containsKey(index) && forwardProb.get(index).containsKey(goalEdge)) {
//    stringProb = innerProb.get(index).get(goalEdge);
//  }
//  
//  return operator.getProb(stringProb);
//}

//if(isLeftWildcard){
//  
//} else {
//  if(insideChart.get(index).containsKey(origSymbolIndex)){
//    stringProb = insideChart.get(index).get(origSymbolIndex);
//  }
//}

//if(isLeftWildcard){
//  
//} else {
//  Map<Integer, Double> insideMap = insideChart.get(lrIndex);
//  if(insideMap.size()>0){ // there're active states
//    System.err.println("cell " + left + "-" + right);
//    for (int tag : insideMap.keySet()) {
//      System.err.println(" " + parserTagIndex.get(tag) 
//          + ": " + operator.getProb(insideMap.get(tag)-scalingFactor));
//    }
//  }
//}

//// store inside probs
//if(!isLeftWildcard){
//  Edge edgeObj = edgeSpace.get(completion.completedEdge);
//  if(edgeObj.numRemainingChildren()==0){ // complete right: left X -> _ Y .
//    int mother = edgeObj.getMother();
//    if(!tempInsideProbs.containsKey(mother)){
//      tempInsideProbs.put(mother, new DoubleList());
//    }
//    tempInsideProbs.get(mother).add(newInnerProb);
//    
//    
//  }
//}

//if(isLeftWildcard){
//  
//} else {
//  tempInsideProbs = new HashMap<Integer, DoubleList>();
//  
//  // go through categories that have finished expanding [middle, right]
//  Map<Integer, Double> insideCell = insideChart.get(mrIndex); 
//  for(int tag : insideCell.keySet()){
//    complete(left, middle, right, tag, insideCell.get(tag));
//  }
//}
//
//if(!isLeftWildcard){
//  storeProbs(tempInsideProbs, insideChart.get(lrIndex));
//}

//if(!isLeftWildcard){
//  insideChart = new HashMap<Integer, Map<Integer,Double>>();
//  outsideChart = new HashMap<Integer, Map<Integer,Double>>();
//  
//  for (int i = 0; i < numCells; i++) {
//    insideChart.put(i, new HashMap<Integer, Double>());
//    outsideChart.put(i, new HashMap<Integer, Double>());
//  }
//  
//  
//}

//int lrIndex = linear[left][right];
//for(int edge : completedEdges.get(lrIndex)){
//  
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