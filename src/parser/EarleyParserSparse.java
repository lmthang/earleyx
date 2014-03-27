/**
 * 
 */
package parser;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import util.DoubleList;
import util.Operator;
import base.BaseLexicon;
import base.RuleSet;
import edu.stanford.nlp.util.Index;
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

  
  public EarleyParserSparse(Grammar grammar, EdgeSpace edgeSpace,
			BaseLexicon lex, RuleSet ruleSet, Index<String> parserWordIndex,
			Index<String> parserTagIndex, Map<Integer, Integer> parserNonterminalMap,
			Operator operator, Set<String> outputMeasures,
			Set<String> internalMeasures, boolean isSeparateRuleInTrie) {
		super(grammar, edgeSpace, lex, ruleSet, parserWordIndex, parserTagIndex,
				parserNonterminalMap, operator, outputMeasures, internalMeasures,
				isSeparateRuleInTrie);
		isFastComplete = true;
		if(verbose>=0){
			System.err.println("# EarleyParserSparse");
		}
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

  @Override
  protected void initOuterProbs() {
    outerProb = new HashMap<Integer, Map<Integer,Double>>();
  }

  @Override
  protected void chartPredict(int left, int right){
    Set<Integer> edges = forwardProb.containsKey(linear(left, right)) ? 
    		forwardProb.get(linear(left, right)).keySet() : new HashSet<Integer>();    
  	
  	for (int edge : edges) {
      // predict for right: left X -> \alpha . Y \beta
      predictFromEdge(left, right, edge);
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
  protected boolean containsInsideEdge(int left, int right, int edge) {
    return forwardProb.containsKey(linear(left, right)) && forwardProb.get(linear(left, right)).containsKey(edge);
  }

  @Override
  protected int insideChartCount(int left, int right) {
    if(forwardProb.containsKey(linear(left, right))){
      return forwardProb.get(linear(left, right)).size();
    } else {
      return 0;
    }
  }
  
  @Override
  public Set<Integer> listInsideEdges(int left, int right) {
    if(forwardProb.containsKey(linear(left, right))){
      return forwardProb.get(linear(left, right)).keySet();
    } else {
      return new HashSet<Integer>();
    }
  }
  
  @Override
  protected boolean containsOutsideEdge(int left, int right, int edge) {
    return outerProb.containsKey(linear(left, right)) && outerProb.get(linear(left, right)).containsKey(edge);
  }
  
  
  @Override
  protected int outsideChartCount(int left, int right) {
    if(outerProb.containsKey(linear(left, right))){
      return outerProb.get(linear(left, right)).size();
    } else {
      return 0;
    }
  }

  @Override
  public Set<Integer> listOutsideEdges(int left, int right) {
    if(outerProb.containsKey(linear(left, right))){
      return outerProb.get(linear(left, right)).keySet();
    } else {
      return new HashSet<Integer>();
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
  @Override
  protected boolean isForwardCellEmpty(int left, int right) {
    return !forwardProb.containsKey(linear(left, right));
  }
  
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
  public double getInnerScore(int left, int right, int edge){
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
  public double getOuterScore(int left, int right, int edge){
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
}

///** Unused code **/

//@Override
//public String dumpInsideChart() {
//return dumpChart(innerProb, true, false, "Inside");
//}

//@Override
//public String dumpOutsideChart() {
//return dumpChart(outerProb, true, true, "Outside");
////return dumpCatChart(computeOutsideChart(), "Outside");
//}

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