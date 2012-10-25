/**
 * 
 */
package parser;

import edu.stanford.nlp.math.ArrayMath;
import edu.stanford.nlp.math.SloppyMath;

/**
 * Handle log-prob operators
 * 
 * @author Minh-Thang Luong, 2012
 *
 */
public class LogProbOperator extends Operator {
  public double multiply(double a, double b){
    return a+b;
  }
  public double inverse(double a){
    return -a;
  }
  public double arraySum(double[] values){
    return ArrayMath.logSum(values);
  }
  public double add(double a, double b) {
    return SloppyMath.logAdd(a, b);
  }
  
  public double zero() {
    return Double.NEGATIVE_INFINITY;
  }

  public double one() {
    return 0.0;
  }
  
  public double getProb(double score) {
    // this score is a log-prob
    return Math.exp(score);
  }
  
  public double getScore(double prob) {
    return Math.log(prob);
  }
  @Override
  public double divide(double a, double b) {
    return a-b;
  }

}
