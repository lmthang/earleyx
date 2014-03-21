package parser;

import java.text.DecimalFormat;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import base.ClosureMatrix;
import base.Edge;
import base.ProbRule;

import util.Operator;
import util.Util;

import edu.stanford.nlp.util.Index;
import edu.stanford.nlp.util.Timing;

/**
 * Store the predicted state that the algorithm will enter after making a prediction.
 * Multipliers to update forward and inner probabilities are stored as well.
 *  
 * @author Minh-Thang Luong, 2012: based initially on Roger Levy's code
 *
 */
public class Prediction {
  public static final Prediction[] NO_PREDICTION = new Prediction[0];
  private static DecimalFormat df = new DecimalFormat("0.0000");
  public static int verbose = 0;
  
  int predictedState;
  double forwardProbMultiplier;
  double innerProbMultiplier;

  public Prediction(int predictedState, double forwardProbMultiplier, double innerProbMultiplier) {
    this.predictedState = predictedState;
    this.forwardProbMultiplier = forwardProbMultiplier;
    this.innerProbMultiplier = innerProbMultiplier;
  }

  /**
   * Construct predictions: X -> . Z \alpha  predicts Y -> .\beta 
   * if there exists a non-zero left-corner rule R_left(Z -> Y)
   * 
   * @param rules
   * @param categories
   * @param leftCornerClosures
   * @return a 2-dimensional array, say predictions, 
   * in which predictions[stateIndex] list all possible predictions we could make
   * for the left child of the active edge associated with stateIndex
   */
  public static Prediction[][] constructPredictions(Collection<ProbRule> rules,
      ClosureMatrix leftCornerClosures, 
      EdgeSpace stateSpace, Index<String> tagIndex, Index<String> wordIndex, 
      List<Integer> nonterminals, Operator operator){
  	if(verbose >= 0){
  	Timing.startDoing("\n## Constructing predictions");
  	}

    // Note: we used list for nonterminals instead of set, to ensure a fixed order for debug purpose
    // indexed by non-terminal index, predictions for Z 
    Map<Integer, List<Prediction>> predictionsViaList = new HashMap<Integer, List<Prediction>>(); 
    for (int viaCategoryIndex : nonterminals) { // Z
      predictionsViaList.put(viaCategoryIndex, new ArrayList<Prediction>());
    }
    
    int viaStateCount = 0; // via state with prediction
    int totalPredictions = 0;
    
    /** Construct predictions via states**/
    int count = 0;
    for (ProbRule r:rules) { // go through each rule, Y -> . \beta, TODO: speed up here, keep track of indices with positive left-closure scores
      count++;
      if(count % 10000 == 0){
        System.err.print(" (" + count + ") ");
      }
      
      if (r.isUnary()) {
        continue;
      }
      
      if(r.getProb()<0 || r.getProb()>1){
        System.err.println("! Invalid rule prob: " + r.toString(tagIndex, wordIndex));
      }
      double rewriteScore = operator.getScore(r.getProb());
      
      int predictedCategoryMotherIndex = r.getMother();
      int predictedState = stateSpace.indexOf(r.getEdge());
      
      Map<Integer, Double> closureMap = leftCornerClosures.getParentClosures(predictedCategoryMotherIndex); // get a list of Z with non-zero closure scores Z -> Y
      for(int viaCategoryIndex : closureMap.keySet()){
        double leftCornerClosureScore = closureMap.get(viaCategoryIndex); //leftCornerClosures.get(viaCategoryIndex, predictedCategoryMotherIndex); // P_L (Z -> Y)
      
        if (leftCornerClosureScore != operator.zero()) {
          Prediction p = new Prediction(predictedState, operator.multiply(rewriteScore, leftCornerClosureScore), rewriteScore);
          predictionsViaList.get(viaCategoryIndex).add(p); //thesePredictions.add(p);
      
//          if (verbose>=2){
//            System.err.println("Predict: " + p.toString(stateSpace, tagIndex, wordIndex, operator) 
//                + ", left-corner=" + df.format(operator.getProb(leftCornerClosureScore))
//                + ", rewrite=" + df.format(operator.getProb(rewriteScore)));
//          }
        }
      }
    }

    Prediction[][] predictionsVia = new Prediction[tagIndex.size()][];
    for (int viaCategoryIndex : nonterminals) { // Z
      if (predictionsViaList.get(viaCategoryIndex).size() == 0) {
        predictionsVia[viaCategoryIndex] = NO_PREDICTION;
      } else {
        predictionsVia[viaCategoryIndex] = (Prediction[]) predictionsViaList.get(viaCategoryIndex).toArray(NO_PREDICTION);
        viaStateCount++;
        totalPredictions += predictionsVia[viaCategoryIndex].length;
        
        if(verbose>=2) System.err.println("  via: " + tagIndex.get(viaCategoryIndex) + ", num predictions " + predictionsVia[viaCategoryIndex].length);
        if(verbose >= 3){
        	for (int i = 0; i < predictionsVia[viaCategoryIndex].length; i++) {
        		Prediction p = predictionsVia[viaCategoryIndex][i];
        		System.err.println("  predict: " + p.toString(stateSpace, tagIndex, wordIndex, operator) 
                //+ ", left-corner=" + df.format(operator.getProb(leftCornerClosureScore))
                + ", rewrite=" + df.format(operator.getProb(p.innerProbMultiplier)));
  				}
        }
      }      
    }
   
    /** construct complete predictions **/
    if(verbose>=1) System.err.println("# Constructing complete predictions ...");
    
    Prediction[][] predictions = new Prediction[stateSpace.size()][];
    for (int predictorState = 0; predictorState < stateSpace.size(); predictorState++) {
      Edge edgeObj = stateSpace.get(predictorState);
      if (edgeObj.numRemainingChildren()==0 || !edgeObj.isTagAfterDot(0)){ // tag -> []
        predictions[predictorState] = NO_PREDICTION;
      } else {
        int viaCategoryIndex = edgeObj.getChildAfterDot(0);
        
        if (nonterminals.contains(viaCategoryIndex)){
          predictions[predictorState] = predictionsVia[viaCategoryIndex];
         
          if(verbose>=3) System.err.println("Edge " + predictorState + ", "  + stateSpace.get(predictorState).toString(tagIndex, wordIndex) + ": predictions " + Util.sprint(predictions[predictorState], stateSpace, tagIndex, wordIndex, operator));
        } else {
          predictions[predictorState] = NO_PREDICTION;
        }
      }
      
      if(verbose>=1 && predictorState%10000==0){
        System.err.print(" (" + predictorState + ") ");
      }
    }
    
    if(verbose >= 0){
    Timing.endDoing("Done! Total predictions=" + totalPredictions  + ", num nonterminals with predictions=" + viaStateCount);   
    }
    
    return predictions;
  }
  
  /* safety check via assertion */
  public static boolean checkPredictions(Prediction[][] predictionsArray, EdgeSpace edgeSpace) {
    boolean satisfied = true;
    double[] predictedStateInnerProbs = new double[edgeSpace.size()];
    boolean[] existingPredictedStates = new boolean[edgeSpace.size()];
    for (int i = 0; i < predictionsArray.length; i++) {
      Prediction[] predictions = predictionsArray[i];
      
      for (int j = 0; j < predictions.length; j++) {
        Prediction prediction = predictions[j];
        if (existingPredictedStates[prediction.predictedState]) {
          if (Math.abs(predictedStateInnerProbs[prediction.predictedState] - prediction.innerProbMultiplier) > 0.00001) {
            System.err.println("Error -- predicted-state " + edgeSpace.get(prediction.predictedState) + "has inconsistent inner probability estimate of " + prediction.innerProbMultiplier);
            satisfied = false;
          }
        } else {
          existingPredictedStates[prediction.predictedState] = true;
          predictedStateInnerProbs[prediction.predictedState] = prediction.innerProbMultiplier;
        }
      }
    }
    return satisfied;
  }
  
  public boolean equals(Object o) {
    if (this == o) {
      return true;
    }
    if (!(o instanceof Prediction)) {
      return false;
    }

    final Prediction prediction = (Prediction) o;

    if (forwardProbMultiplier != prediction.forwardProbMultiplier) {
      return false;
    }
    if (innerProbMultiplier != prediction.innerProbMultiplier) {
      return false;
    }
    if (predictedState != prediction.predictedState) {
      return false;
    }

    return true;
  }

  public int hashCode() { // Thang: to check this mysterious number 29?
    long temp = forwardProbMultiplier != +0.0d ? Double.doubleToLongBits(forwardProbMultiplier) : 0l;
    int result1 = 29 * predictedState + (int) (temp ^ (temp >>> 32));
    temp = innerProbMultiplier != +0.0d ? Double.doubleToLongBits(innerProbMultiplier) : 0l;
    result1 = 29 * result1 + (int) (temp ^ (temp >>> 32));
    return result1;
  }

  public String toString(EdgeSpace stateSpace, Index<String> tagIndex, 
      Index<String> wordIndex, Operator operator) {
    return "(" + stateSpace.get(predictedState).toString(tagIndex, wordIndex) 
    + ",f=" + df.format(operator.getProb(forwardProbMultiplier)) + ",i=" 
    + df.format(operator.getProb(innerProbMultiplier)) + ")";
  }

}
//public static Prediction[][] constructPredictions(Collection<ProbRule> rules,
//    ClosureMatrix leftCornerClosures, 
//    EdgeSpace stateSpace, Index<String> tagIndex, Index<String> wordIndex, 
//    List<Integer> nonterminals, Operator operator){
//  // Note: we used list for nonterminals instead of set, to ensure a fixed order for debug purpose
//  
//  // indexed by non-terminal index, predictions for Z
//  Prediction[][] predictionsVia = new Prediction[tagIndex.size()][]; 
//  
//  if(verbose > 0){
//    System.err.println("\n## Constructing predictions ...");
//   Timing.startTime();
//  }
//  
//  int viaStateCount = 0; // via state with prediction
//  int totalPredictions = 0;
//  
//  
//  System.err.println(stateSpace.toString());
//  
//  /** Construct predictions via states**/
//  for (int viaCategoryIndex : nonterminals) { // Z
//    if(verbose >= 1){
//      System.err.print("# via: " + tagIndex.get(viaCategoryIndex) + " ...");
//    }
//    
//    /** Make prediction **/
//    List<Prediction> thesePredictions = new ArrayList<Prediction>();
//    
//    for (ProbRule r:rules) { // go through each rule, Y -> . \beta, TODO: speed up here, keep track of indices with positive left-closure scores
//      if (r.isUnary()) {
//        continue;
//      }
//      
//      if(r.getProb()<0 || r.getProb()>1){
//        System.err.println("! Invalid rule prob: " + r.toString(tagIndex, wordIndex));
//      }
//      double rewriteScore = operator.getScore(r.getProb());
//      
//      int predictedCategoryMotherIndex = r.getMother();
//      int predictedState = stateSpace.indexOf(r.getEdge());
//      double leftCornerClosureScore = leftCornerClosures.get(viaCategoryIndex, predictedCategoryMotherIndex); // P_L (Z -> Y)
//      
//      if (leftCornerClosureScore != operator.zero()) {
//        Prediction p = new Prediction(predictedState, operator.multiply(rewriteScore, leftCornerClosureScore), rewriteScore);
//        thesePredictions.add(p);
//    
//        if(verbose>=1 && thesePredictions.size() % 1000 == 0){
//          System.err.print(" (" + thesePredictions.size() + ") ");
//        }
//        if (verbose>=2){
//          System.err.println("Predict: " + p.toString(stateSpace, tagIndex, wordIndex, operator) 
//              + ", left-corner=" + df.format(operator.getProb(leftCornerClosureScore))
//              + ", rewrite=" + df.format(operator.getProb(rewriteScore)));
//        }
//      }
//    }
//    if(verbose>=1){
//      System.err.println("Done! Num predictions = " + thesePredictions.size());
//    }
//    
//    if (thesePredictions.size() == 0) {
//      predictionsVia[viaCategoryIndex] = NO_PREDICTION;
//    } else {
//      predictionsVia[viaCategoryIndex] = (Prediction[]) thesePredictions.toArray(NO_PREDICTION);
//      viaStateCount++;
//      totalPredictions += predictionsVia[viaCategoryIndex].length;
//    }
//  }
// 
//  /** construct complete predictions **/
//  if(verbose>=1){
//    System.err.print("# Constructing complete predictions ...");
//  }
//  Prediction[][] predictions = new Prediction[stateSpace.size()][];
//  for (int predictorState = 0; predictorState < stateSpace.size(); predictorState++) {
//    Edge edgeObj = stateSpace.get(predictorState);
//    if (edgeObj.numRemainingChildren()==0 || !edgeObj.isTagAfterDot(0)){ // tag -> []
//      predictions[predictorState] = NO_PREDICTION;
//    } else {
//      int viaCategoryIndex = edgeObj.getChildAfterDot(0);
//      
//      if (nonterminals.contains(viaCategoryIndex)){
//        predictions[predictorState] = predictionsVia[viaCategoryIndex];
//        
//        if(verbose>=2){
//          System.err.println("Edge " + predictorState + ", " 
//              + stateSpace.get(predictorState).toString(tagIndex, wordIndex)
//              + ": predictions " + Util.sprint(predictions[predictorState], 
//                  stateSpace, tagIndex, wordIndex, operator));
//        }
//      } else {
//        predictions[predictorState] = NO_PREDICTION;
//      }
//    }
//    
//    if(verbose>=1 && predictorState%10000==0){
//      System.err.print(" (" + predictorState + ") ");
//    }
//  }
//  
//  if(verbose >= 1){
//   Timing.tick("Done! Total predictions=" + totalPredictions 
//        + ", num nonterminals with predictions=" + viaStateCount); 
//  }
//  
//  return predictions;
//}
