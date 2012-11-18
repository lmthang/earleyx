package parser;

import edu.stanford.nlp.util.Index;
import base.Edge;

public class LeftWildcardEdgeSpace extends EdgeSpace {
  public LeftWildcardEdgeSpace(Index<String> tagIndex, Index<String> wordIndex){
    super(tagIndex, wordIndex);
  }
  
  protected Edge getToEdge(Edge e){
    return e.getToEdge();
  }
}
