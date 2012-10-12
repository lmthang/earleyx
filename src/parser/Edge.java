package parser;

import java.util.ArrayList;
import java.util.List;

import edu.stanford.nlp.util.Index;

/**
 * Represent an active edge used in Earley algorithms, e.g X -> a b . c
 * IMPORTANT: When comparing active edges, we ignore the part before the dot, i.e. X -> a b . c is equal to X -> d e . c 
 * This results in the aggregating effect in computing probabilities for rules.
 * 
 * @author Minh-Thang Luong, 2012
 */
public class Edge {
  protected BaseEdge edge; // rule being expanded
  protected int dot; // number of children found so far, right = left + dot
  
  
  public Edge(BaseEdge edge, int dot) {
    super();
    this.edge = edge;
    this.dot = dot;
    assert(dot<=edge.numChildren());
  }

  public void setMother(int mother){
    edge.setMother(mother);
  }
  
  /* Getters */
  public BaseEdge getEdge() {
    return edge;
  }
  public int getDot() {
    return dot;
  }
  public int getMother(){
    return edge.getMother();
  }
  public int numChildren(){
    return edge.numChildren();
  }
  public int numRemainingChildren(){
    return edge.numChildren()-dot;
  }
  public List<Integer> getChildrenAfterDot(int pos){
    return edge.getChildren(dot+pos);
  }
  public int getChildAfterDot(int pos){
    return edge.getChild(dot+pos);
  }
  
  /** 
  * create mother edge which doesn't have any children, mother -> []
  * @return
  */
  public Edge getMotherEdge(){
    return new Edge(new BaseEdge(edge.getMother(), new ArrayList<Integer>()), 0);
  }

  /** 
  * via edge: first child -> []
  * @return
  */
  public Edge getViaEdge(){ // first child after the dot
    return new Edge(new BaseEdge(edge.getChildren().get(dot), new ArrayList<Integer>()), 0);
  }

  public boolean equals(Object o) {
    if (this == o){ // compare pointer
      return true;
    }
    
    if (!(o instanceof Edge)) { // check class
      return false;
    } 

    Edge otherActiveEdge = (Edge) o;
    
//    // compare dot position
//    if (this.dot != otherActiveEdge.getDot()){
//      return false;
//    }
    
    // compare children
    return edge.equals(otherActiveEdge.getEdge(), dot, otherActiveEdge.getDot());
  }

  public int hashCode() {
    return edge.hashCode(dot); //  << 8 + dot
  }
  
  // create a tag edge: tag -> []
  public static Edge createTagEdge(int tag){
    return new Edge(new BaseEdge(tag, new ArrayList<Integer>()), 0);
  }
  
  public String toString(Index<String> motherIndex, Index<String> childIndex){
    StringBuffer sb = new StringBuffer();
    sb.append(edge.lhsString(motherIndex) + " -> ");
    List<Integer> children = edge.getChildren();
    for (int i = 0; i < dot; i++) {
      sb.append(childIndex.get(children.get(i)) + " ");
    }
    sb.append(".");
    for (int i = dot; i < children.size(); i++) {
      sb.append(" " + childIndex.get(children.get(i)));
    }
    return sb.toString();
  }
}

/*** Unused code ***/

//
///** 
//* to edge: mother -> [second child onwards]
//* @return
//*/
//public ActiveEdge getToEdge(){
//return new ActiveEdge(new Edge(edge.getMother(), edge.getChildren(dot+1)), 0);
//}

//protected int left; // location of the left edge
//protected int right; // location of the right edge
//, int left, int right
//this.left = left;
//this.right = right;
//public int getLeft() {
//  return left;
//}
//public int getRight() {
//  return right;
//}
//assert(left<=right);