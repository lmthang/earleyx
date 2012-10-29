package base;

import java.util.List;

import edu.stanford.nlp.util.Index;

public class TerminalRule extends Rule {

  public TerminalRule(BaseEdge edge, double score) {
    super(edge, score);
    // TODO Auto-generated constructor stub
  }

  public TerminalRule(int mother, List<Integer> children, double score) {
    super(mother, children, score);
    // TODO Auto-generated constructor stub
  }

  public TerminalRule(String motherStr, List<String> childStrs, double score,
      Index<String> motherIndex, Index<String> childIndex) {
    super(motherStr, childStrs, score, motherIndex, childIndex);
    // TODO Auto-generated constructor stub
  }

  // X->[_a _b _c] : 0.1
  public String toString(Index<String> motherIndex, Index<String> childIndex) {
    return edge.lhsString(motherIndex) + "->[" + edge.rhsString(childIndex, true) + "] : " + score;
  }
  
  public String schemeString(Index<String> motherIndex, Index<String> childIndex) {
    return edge.schemeString(motherIndex, childIndex, true);
  }
}
