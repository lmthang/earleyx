package parser;

import java.util.ArrayList;
import java.util.List;

import edu.stanford.nlp.parser.lexparser.IntTaggedWord;

/* an active/passive edge in a chart*/

/* Thang v110901 move this class Edge definition into a separate class, and make it generic */
class Edge {
  private IntTaggedWord mother;
  public void setMother(IntTaggedWord mother) {
    this.mother = mother;
  }

  private List<IntTaggedWord> dtrs;
  
  public IntTaggedWord getMother(){
    return mother;
  }
  
  public List<IntTaggedWord> getDtrs(){
    return dtrs;
  }
  
  public Edge getMotherEdge(){
    return new Edge(mother, new ArrayList<IntTaggedWord>());
  }
  
  public Edge getChildEdge(int i){
    return new Edge(dtrs.get(i), new ArrayList<IntTaggedWord>());
  }
  
  public Edge(String mother, List<String> children) {
    this.mother = IntTaggedWord.createTagITW(mother);
    this.dtrs = new ArrayList<IntTaggedWord>();
    for (String child : children) {
      this.dtrs.add(IntTaggedWord.createTagITW(child));
    }
  }

  public Edge(IntTaggedWord mother, List<IntTaggedWord> dtrs){
    this.mother = mother;
    this.dtrs = dtrs;
  }
  
  public boolean equals(Object o) {
    if (this == o) {
      return true;
    }
    if (!(o instanceof Edge)) {
      return false;
    }

    final Edge rogerEdge = (Edge) o;

    if (dtrs != null ? !dtrs.equals(rogerEdge.dtrs) : rogerEdge.dtrs != null) {
      return false;
    }
    if (mother != null ? !mother.equals(rogerEdge.mother) : rogerEdge.mother != null) {
      return false;
    }

    return true;
  }

  public int hashCode() {
    int result;
    result = (mother != null ? mother.hashCode() : 0);
    result = 29 * result + (dtrs != null ? dtrs.hashCode() : 0);
    return result;
  }

  public String toString() {
    StringBuffer sb = new StringBuffer();
    sb.append(mother.tagString() + "->[");
    for (IntTaggedWord dtr : dtrs){
      sb.append(dtr.tagString() + " ");
    }
    if(dtrs.size() > 0){
      sb.deleteCharAt(sb.length()-1);
    }
    sb.append("]");
    return sb.toString();
  }

}