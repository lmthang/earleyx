package parser;

import java.text.DecimalFormat;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Set;

import recursion.ClosureMatrix;
import utility.Utility;

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
  private static final Prediction[] NO_PREDICTION = new Prediction[0];
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
  public static Prediction[][] constructPredictions(Collection<Rule> rules,
      ClosureMatrix leftCornerClosures, 
      EdgeSpace stateSpace, Index<String> tagIndex){
    // indexed by non-terminal index, predictions for Z
    Prediction[][] predictionsVia = new Prediction[tagIndex.size()][]; 
    
    if(verbose > 0){
      System.err.println("\n## Constructing predictions ...");
      Timing.startTime();
    }
    
    int viaStateCount = 0; // via state with prediction
    int totalPredictions = 0;
    
    
    /** Construct predictions via states**/
    Set<Integer> activeIndices = stateSpace.getActiveIndices(); // those that has at least 1 non-terminal left-corner child 
    for (int viaCategoryIndex : activeIndices) { // Z 
      //System.err.println(viaCategoryIndex + "\t" + stateSpace.get(viaCategoryIndex).numChildren() + "\t" + stateSpace.get(stateSpace.indexOfTag(viaCategoryIndex)).getMother());
      assert(stateSpace.get(stateSpace.indexOfTag(viaCategoryIndex)).getMother()==viaCategoryIndex);
      if(verbose >= 2){
        System.err.println("# via: " + tagIndex.get(viaCategoryIndex));
      }
      
      /** Make prediction **/
      List<Prediction> thesePredictions = new ArrayList<Prediction>();
      for (Rule r:rules) { // go through each rule, Y -> . \beta, TODO: speed up here, keep track of indices with positive left-closure scores
        if (r.isUnary()) {
          continue;
        }
        assert(r.getScore()>=0 && r.getScore()<=1);
        double rewriteScore = Math.log(r.score);
        
        int predictedCategoryMotherIndex = r.getMother(); //categories.indexOf(stateSpace.indexOf(r.getMotherEdge()));
        int predictedState = stateSpace.indexOf(r.toEdge());
        double leftCornerClosureScore = leftCornerClosures.get(viaCategoryIndex, predictedCategoryMotherIndex); // P_L (Z -> Y)
        
        if (leftCornerClosureScore != Double.NEGATIVE_INFINITY) {
          assert rewriteScore <= 0.0;
          
          Prediction p = new Prediction(predictedState, rewriteScore + leftCornerClosureScore, rewriteScore); // note scores are in log space
          thesePredictions.add(p);
          if (verbose>=2){
            System.err.println("Predict: " + p.toString(stateSpace, tagIndex) 
                + ", left-corner=" + df.format(Math.exp(leftCornerClosureScore))
                + ", rewrite=" + df.format(Math.exp(rewriteScore)));
          }
        }
      }
      
      if (thesePredictions.size() == 0) {
        if(verbose>=2){
          System.err.println("! Nonterminal " + tagIndex.get(viaCategoryIndex) + " has no prediction.");
        }
        predictionsVia[viaCategoryIndex] = NO_PREDICTION;
      } else {
        predictionsVia[viaCategoryIndex] = (Prediction[]) thesePredictions.toArray(NO_PREDICTION);
        viaStateCount++;
        totalPredictions += predictionsVia[viaCategoryIndex].length;
      }
    }
   
    /** construct complete predictions **/
    Prediction[][] predictions = new Prediction[stateSpace.size()][];
    for (int predictorState = 0; predictorState < stateSpace.size(); predictorState++) {
      int viaState = stateSpace.via(predictorState);
      
      if (viaState == -1){ // tag -> []
        predictions[predictorState] = NO_PREDICTION;
      } else {
        int viaCategoryIndex = stateSpace.get(viaState).getMother();
        assert(stateSpace.get(viaState).numChildren()==0);
        assert(stateSpace.get(predictorState).getChildAfterDot(0) == viaCategoryIndex);
        
        if (activeIndices.contains(viaCategoryIndex)){
          predictions[predictorState] = predictionsVia[viaCategoryIndex];
          
          if(verbose>=3){
            System.err.println(predictorState + "\t" + stateSpace.get(predictorState).toString(tagIndex, tagIndex)
                + "\t" + Utility.sprint(predictions[predictorState], stateSpace, tagIndex));
          }
        } else {
          predictions[predictorState] = NO_PREDICTION;
        }
      }
      
      
    }
    
    if(verbose >= 1){
      Timing.tick("Done! Total predictions=" + totalPredictions 
          + ", num nonterminals with predictions=" + viaStateCount); 
    }
    
    return predictions;
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

  public String toString(EdgeSpace stateSpace, Index<String> tagIndex) {
    return "(" + stateSpace.get(predictedState).toString(tagIndex, tagIndex) + ",f=" + df.format(Math.exp(forwardProbMultiplier)) + ",i=" 
    + df.format(Math.exp(innerProbMultiplier)) + ")";
  }

}
