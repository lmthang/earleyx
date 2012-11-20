package base;

import java.util.List;

import edu.stanford.nlp.util.Index;

public class TerminalRule extends Rule {
  public TerminalRule(int mother, List<Integer> children) {
    super(mother, children);
  }

  public TerminalRule(String motherStr, List<String> childStrs,
      Index<String> tagIndex, Index<String> wordIndex) {
    super(motherStr, childStrs, tagIndex, wordIndex);
  }
  
  public boolean equals(Object o) {
    if (this == o){ // compare pointer
      return true;
    }
    
    if (!(o instanceof TerminalRule)) { // check class
      return false;
    } 

    return equals((Rule) o);
  }
  
  public int hashCode() {
    return super.hashCode()<<4 + 1;
  }
}

////X->[_a _b _c] : 0.1
//public String toString(Index<String> motherIndex, Index<String> childIndex) {
//  return rule.lhsString(motherIndex) + "->[" + rule.rhsString(childIndex, true) + "] : " + score;
//}
//
//public String schemeString(Index<String> motherIndex, Index<String> childIndex) {
//  return rule.schemeString(motherIndex, childIndex, true);
//}