package base;

import edu.stanford.nlp.util.Index;

public class BiasProbRule extends ProbRule {
  private double bias;
  
  public double getBias() {
    return bias;
  }

  public BiasProbRule(Rule rule, double prob, double bias) {
    super(rule, prob);
    this.bias = bias;
  }
  
  // 1 X->[a b c] : 0.1
  public String toString(Index<String> tagIndex, Index<String> wordIndex) {
    return String.format("%e %s : %e", bias, rule.toString(tagIndex, wordIndex), prob);
  }
  
  // format read by Mark's code
  public String markString(Index<String> tagIndex, Index<String> wordIndex) {
    return bias + " " + prob + " " + rule.markString(tagIndex, wordIndex);
  }
}
