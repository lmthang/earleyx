package util;

import edu.stanford.nlp.math.ArrayMath;

/**
 * Handle normal probability operators 
 * 
 * @author Minh-Thang Luong, 2012
 *
 */
public class ProbOperator extends Operator {
  public double multiply(double a, double b){
    return a*b;
  }
  
  public double inverse(double a){
    return 1.0/a;
    
  }
  public double arraySum(double[] values){
    return ArrayMath.sum(values);
  }

  public double add(double a, double b) {
    return a+b;
  }

  public double zero() {
    return 0.0;
  }

  public double one() {
    return 1.0;
  }

  public double getProb(double score) {
    // this score is a prob
    return score;
  }

  public double getScore(double prob) {
    return prob;
  }

  public double divide(double a, double b) {
    return a/b;
  }

  @Override
  public double getLogProb(double score) {
    return Math.log(score);
  }
}
