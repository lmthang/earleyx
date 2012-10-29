package util;

/**
 * Make it easier to handle in prob or log-prob doamains 
 * 
 * @author Minh-Thang Luong, 2012
 *
 */
public abstract class Operator {
  public abstract double multiply(double a, double b);
  public abstract double divide(double a, double b); // a/b
  public abstract double inverse(double a); // 1/a
  public abstract double add(double a, double b);
  public abstract double arraySum(double[] values);
  public abstract double zero();
  public abstract double one();
  public abstract double getProb(double score); // exp: if LogProbOperator, return same value if ProbOperator
  public abstract double getScore(double prob); // log: if LogProbOperator, return same value if ProbOperator
}
