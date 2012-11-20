package base;

import java.util.List;

import edu.stanford.nlp.util.Index;

/**
 * Represent a rule involving only tags (no terminals on the RHS), e.g X -> [Y Z]
 * 
 * @author Minh-Thang Luong, 2012
 *
 */
public class TagRule extends Rule {  
  public TagRule(int mother, List<Integer> children) {
    super(mother, children);
  }

  public TagRule(String motherStr, List<String> childStrs, Index<String> tagIndex) {
    super(motherStr, childStrs, tagIndex, tagIndex);
  }
  
  public boolean equals(Object o) {
    if (this == o){ // compare pointer
      return true;
    }
    
    if (!(o instanceof TagRule)) { // check class
      return false;
    } 

    return equals((Rule) o);
  }
  
  public int hashCode() {
    return super.hashCode()<<4 + 2;
  }
}
