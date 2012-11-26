/**
 * 
 */
package parser;

import java.io.BufferedReader;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;

import base.Edge;

import edu.stanford.nlp.util.DoubleList;
import edu.stanford.nlp.util.Timing;

/**
 * @author Minh-Thang Luong, 2012
 *
 */
public class EarleyParserSparse extends EarleyParser {
  protected Map<Integer, Map<Integer, Double>> forwardProb;   // forwardProb.get(linear(left, right)).get(edge)
  protected Map<Integer, Map<Integer, Double>> innerProb;     // innerProb.get(linear(left, right)).get(edge)

  /* for inside-outside computation, currently works when isLeftWildcard=false */
  protected Map<Integer, Map<Integer, Double>> outerProb;     // innerProb.get(linear(left, right)).get(edge)

  public EarleyParserSparse(BufferedReader br, String rootSymbol,
      boolean isScaling, boolean isLogProb, int insideOutsideOpt,
      String objString) {
    super(br, rootSymbol, isScaling, isLogProb, insideOutsideOpt, objString);
    // TODO Auto-generated constructor stub
  }

  public EarleyParserSparse(String grammarFile, int inGrammarType,
      String rootSymbol, boolean isScaling, boolean isLogProb,
      int insideOutsideOpt, String objString) {
    super(grammarFile, inGrammarType, rootSymbol, isScaling, isLogProb,
        insideOutsideOpt, objString);
    // TODO Auto-generated constructor stub
  }

  protected void sentInit(){
    super.sentInit();
    
    if (verbose>=2){
      System.err.println("# EarleyParserSparse initializing ... ");
      Timing.startTime();
    }
  
    forwardProb = new HashMap<Integer, Map<Integer,Double>>();
    innerProb = new HashMap<Integer, Map<Integer,Double>>();

    // compute outside
    if(insideOutsideOpt>0){
      outerProb = new HashMap<Integer, Map<Integer,Double>>();
    }
  }
  
  /* (non-Javadoc)
   * @see parser.EarleyParser#addToChart(int, int, int, double, double)
   */
  @Override
  protected void addToChart(int left, int right, int edge, double logForward,
      double logInner) {
    int lrIndex = linear(left, right); // left right index
    
    if(!forwardProb.containsKey(lrIndex)){
      forwardProb.put(lrIndex, new HashMap<Integer, Double>());
      innerProb.put(lrIndex, new HashMap<Integer, Double>());
    }
    assert(!forwardProb.get(lrIndex).containsKey(edge));
    forwardProb.get(lrIndex).put(edge, logForward);
    innerProb.get(lrIndex).put(edge, logInner);
  }
  
  
  @Override
  protected void initCompleteTmpScores() {
    theseForwardProb = new HashMap<Integer, DoubleList>();
    theseInnerProb = new HashMap<Integer, DoubleList>();
  }

  
  @Override
  protected void storeCompleteTmpScores(int left, int right) {
    // completions yield edges: right: left X -> _ Y . _
    int lrIndex = linear(left, right);
    if(!forwardProb.containsKey(lrIndex)){
      forwardProb.put(lrIndex, new HashMap<Integer, Double>());
      innerProb.put(lrIndex, new HashMap<Integer, Double>());
    }
    storeProbs(theseForwardProb, forwardProb.get(lrIndex));
    storeProbs(theseInnerProb, innerProb.get(lrIndex));
  }
  
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
    int index = linear(left, right);
    if(forwardProb.containsKey(index) && forwardProb.get(index).containsKey(edge)){
      return edgeScoreInfo(left, right, edge, forwardProb.get(index).get(edge), innerProb.get(index).get(edge));
    } else {
      return edgeScoreInfo(left, right, edge, operator.zero(), operator.zero());
    }    
  }
  
  @Override
  protected boolean containsEdge(int left, int right, int edge) {
    return forwardProb.containsKey(linear(left, right)) && forwardProb.get(linear(left, right)).containsKey(edge);
  }
  
  @Override
  protected int chartCount(int left, int right) {
    if(forwardProb.containsKey(linear(left, right))){
      return forwardProb.get(linear(left, right)).size();
    } else {
      return 0;
    }
  }
  
  @Override
  protected Set<Integer> listEdges(int left, int right) {
    if(forwardProb.containsKey(linear(left, right))){
      return forwardProb.get(linear(left, right)).keySet();
    } else {
      return null;
    }
  }

  /****************************/
  /** Temporary prob methods **/
  /***************************/
  
  /** Used as holding zone for predictions **/
  private Map<Integer, Double> predictedForwardProb;
  private Map<Integer, Double> predictedInnerProb;
  
  @Override
  protected void initPredictTmpScores() {
    predictedForwardProb = new HashMap<Integer, Double>();
    predictedInnerProb = new HashMap<Integer, Double>(); 
  }

  @Override
  protected void addPredictTmpForwardScore(int edge, double score) {
    addScore(predictedForwardProb, edge, score);
  }

  @Override
  protected void addPredictTmpInnerScore(int edge, double score) {
    predictedInnerProb.put(edge, score);
  }

  
  @Override
  protected void storePredictTmpScores(int right) {
    // replace old entries with recently predicted entries
    // all predictions will have the form right: right Y -> _
    int rrIndex = linear(right, right); // right right index
    forwardProb.put(rrIndex, predictedForwardProb);
    innerProb.put(rrIndex, predictedInnerProb);
  }
  
  /** Used as holding zones for completions **/
  protected Map<Integer, DoubleList> theseForwardProb;
  protected Map<Integer, DoubleList> theseInnerProb;
  
  @Override
  protected void initCompleteTmpScores(int edge) {
    if(!theseForwardProb.containsKey(edge)){
      theseForwardProb.put(edge, new DoubleList());
      theseInnerProb.put(edge, new DoubleList());
    }
  }

  @Override
  protected void addCompleteTmpForwardScore(int edge, double score) {
    theseForwardProb.get(edge).add(score);
  }

  @Override
  protected void addCompleteTmpInnerScore(int edge, double score) {
    theseInnerProb.get(edge).add(score);
  }

  /****************************/
  /** Forward probabilities **/
  /***************************/
  protected double getForwardScore(int left, int right, int edge){
    int lrIndex = linear(left, right);
    
    if(!forwardProb.containsKey(lrIndex)){
      return operator.zero();
    }
    
    if(!forwardProb.get(lrIndex).containsKey(edge)){
      return operator.zero();
    } else {
      return forwardProb.get(lrIndex).get(edge);
    }
  }
  
  protected void addForwardScore(int left, int right, int edge, double score){
    int lrIndex = linear(left, right);
    
    if(!forwardProb.containsKey(lrIndex)){
      forwardProb.put(lrIndex, new HashMap<Integer, Double>());
    }
    
    addScore(forwardProb.get(lrIndex), edge, score);
  }
  
  
  /*************************/
  /** Inner probabilities **/
  /*************************/
  protected double getInnerScore(int left, int right, int edge){
    int lrIndex = linear(left, right);
    if(!innerProb.containsKey(lrIndex)){
      return operator.zero();
    }
    
    if(!innerProb.get(lrIndex).containsKey(edge)){
      return operator.zero();
    } else {
      return innerProb.get(lrIndex).get(edge);
    }
  }
  
  protected void addInnerScore(int left, int right, int edge, double score){
    int lrIndex = linear(left, right);
    
    if(!innerProb.containsKey(lrIndex)){
      innerProb.put(lrIndex, new HashMap<Integer, Double>());
    }
    
    addScore(innerProb.get(lrIndex), edge, score);
  }
  
  /*************************/
  /** Outside computation **/
  /*************************/
  protected double getOuterScore(int left, int right, int edge){
    int lrIndex = linear(left, right);
    
    if(!outerProb.containsKey(lrIndex)){
      return operator.zero();
    }
    
    if(!outerProb.get(lrIndex).containsKey(edge)){
      return operator.zero();
    } else {
      return outerProb.get(lrIndex).get(edge);
    }
  }
  
  protected void addOuterScore(int left, int right, int edge, double score){
    int lrIndex = linear(left, right);
    
    if(!outerProb.containsKey(lrIndex)){
      outerProb.put(lrIndex, new HashMap<Integer, Double>());
    }
    
    addScore(outerProb.get(lrIndex), edge, score);
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
  protected String dumpChart(Map<Integer, Map<Integer, Double>> chart, boolean isOnlyCategory, boolean isOutsideProb, String name){
    StringBuffer sb = new StringBuffer("# " + name + " chart snapshot\n");
    for(int length=1; length<=numWords; length++){ // length
      for (int left = 0; left <= numWords-length; left++) {
        int right = left+length;

        // scaling
        double scalingFactor = operator.one();
        if (isScaling){
          if (isOutsideProb){ // outside prob has a scaling factor for [0,left][right, numWords]
            scalingFactor = operator.multiply(getScaling(0, left), getScaling(right, numWords));
          } else {
            scalingFactor = getScaling(left, right);
          }
        }
        
        int lrIndex = linear(left, right);
        if(!chart.containsKey(lrIndex)){
          continue;
        }
        Map<Integer, Double> valueMap = chart.get(lrIndex);
        if(valueMap.size()>0){ // there're active states
          if(isOnlyCategory){ // print by tags (completed edges)
            Map<Integer, Double> tagMap = new TreeMap<Integer, Double>(); // sort keys
            
            // accumulate score for categories
            for (int edge : valueMap.keySet()) { // edge
              Edge edgeObj = edgeSpace.get(edge);
              if(edgeObj.numRemainingChildren()==0){ // completed edge
                int tag = edgeObj.getMother();
                
                double score = operator.divide(valueMap.get(edge), scalingFactor);
                if(!tagMap.containsKey(tag)){ // new tag
                  tagMap.put(tag, score);
                } else { // old tag
                  if(isOutsideProb){ // for outside probs, we don't accumulate scores for categories
                    assert(tagMap.get(tag) == score); // outside probs for paths X -> * . Y are all the same despite the part on the left of the dot  
                  } else {
                    tagMap.put(tag, operator.add(tagMap.get(tag), score));
                  }
                }
              }
            }
            
            if(tagMap.size()>0){
              sb.append("cell " + left + "-" + right + "\n");
            }
            
            // print by tag            
            for(Map.Entry<Integer, Double> entry : tagMap.entrySet()){
              int tag = entry.getKey();
              sb.append(" " + parserTagIndex.get(tag) 
                  + ": " + df2.format(operator.getProb(tagMap.get(tag))) + "\n");
            }
          } else { // print by edge
            int count = valueMap.size();
            sb.append("[" + left + "," + right + "]: " + count  
                + " (" + df1.format(count*100.0/edgeSpaceSize) + "%)\n");
            
            for (int edge : valueMap.keySet()) { // edge
              sb.append("  " + edgeSpace.get(edge).toString(parserTagIndex, parserWordIndex) 
                  + ": " + df.format(operator.getProb(operator.divide(valueMap.get(edge), scalingFactor))) + "\n");
            }
          }
        }
      }
    }  
    
    return sb.toString();
  }
  
  public String dumpInnerProb(){
    return dumpChart(innerProb, false, false, "Inner");
  }
  
  public String dumpOuterProb(){
    return dumpChart(outerProb, false, true, "Outer");
  }
  
  @Override
  public String dumpInsideChart() {
    return dumpChart(innerProb, true, false, "Inside");
  }
  
  @Override
  public String dumpOutsideChart() {
    return dumpChart(outerProb, true, true, "Outside");
//    return dumpCatChart(computeOutsideChart(), "Outside");
  }
}

///** Unused code **/
//// insideChart.get(linear(left, right)).get(tagIndex) 
//protected Map<Integer, Map<Integer, Double>> insideChart;
//protected Map<Integer, Map<Integer, Double>> outsideChart;
//


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
//      int lrIndex = linear(left, right);
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
//      int lrIndex = linear(left, right);
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

//protected Map<Integer, DoubleList> tempInsideProbs; // to avoid concurrently modify insideChart while performing completions