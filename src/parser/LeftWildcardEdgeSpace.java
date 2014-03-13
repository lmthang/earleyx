package parser;

import edu.stanford.nlp.util.Index;
import base.Edge;

public class LeftWildcardEdgeSpace extends EdgeSpace {
  public LeftWildcardEdgeSpace(Index<String> tagIndex, Index<String> wordIndex){
    super(tagIndex, wordIndex);
    if(verbose>=0){
      System.err.println("# LeftWildcard Edge Space");
    }
  }
  
  protected Edge getToEdge(Edge e){
    return new Edge(e.getRule().buildToRule(e.getDot()), 0);
//    return e.getToEdge();
  }
}
