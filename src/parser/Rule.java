package parser;

import edu.stanford.nlp.parser.lexparser.IntTaggedWord;
import edu.stanford.nlp.stats.Distribution;
import edu.stanford.nlp.stats.TwoDimensionalCounter;
import edu.stanford.nlp.trees.Tree;
import edu.stanford.nlp.util.Index;

import java.util.*;

/**
 * Representation of CFG rules. Memory saving is achieved by using integers.
 *
 * @author Minh-Thang Luong
 */

class Rule {
  private int mother;
  private int[] children;
  private boolean isRHSTag = true; // indicate if the RHS are tags or words
  
  double score;
  
  public Rule(String mother, List<String> children, double score, boolean isRHSTag, Index<String> tagIndex, Index<String> wordIndex) {
    this.mother = tagIndex.indexOf(mother, true);
    this.children = new int[children.size()];
    this.score = score;
    this.isRHSTag = isRHSTag;
    for (String child : children) {
      if(isRHSTag){ // tag
        this.children.add(new IntTaggedWord(IntTaggedWord.ANY, child));
      } else { // word
        this.children.add(new IntTaggedWord(child, IntTaggedWord.ANY));
      }
    }
  }
  
  public Rule(IntTaggedWord mother, List<IntTaggedWord> dtrs, double score){
    this.mother = mother;
    this.children = dtrs;
    this.score = score;
  }
  
  public IntTaggedWord getMother(){
    return mother;
  }
  
  public List<IntTaggedWord> getDtrs(){
    return children;
  }
  
  /**
   * Get the reverse view of the children
   * 
   * @return
   */
  public List<IntTaggedWord> getReverseChildren(){
    ListIterator<IntTaggedWord> iterator = children.listIterator(children.size());
    List<IntTaggedWord> reverseChildren = new ArrayList<IntTaggedWord>();
    while(iterator.hasPrevious()){
      reverseChildren.add(iterator.previous());
    }
    return reverseChildren;
  }
  
  public Edge getMotherEdge(){
    return new Edge(mother, new ArrayList<IntTaggedWord>());
  }
  
  public Edge getChildEdge(int i){
    return new Edge(children.get(i), new ArrayList<IntTaggedWord>());
  }
  
  
  /*
  public Edge[] toEdges() {
    Edge[] edges = new Edge[dtrs.size()];
    for (int i = 0; i < dtrs.size(); i++) {
      List thisDtrs = dtrs.subList(i,dtrs.size());
      edges[i] = new Edge(mother,thisDtrs);
    }
    return edges;
  }
  */

  public Edge toEdge() {
    return new Edge(mother, children);
  }

  public boolean isUnary() {
    return children.size() == 1;
  }

  public String schemeString() {
    StringBuffer sb = new StringBuffer();
    sb.append("(" + mother.tagString() + " ");
    for (IntTaggedWord dtr : children){
      if(isRHSTag){
        sb.append("(X " + dtr.tagString() + ") ");
      } else {
        sb.append("(X _" + dtr.wordString() + ") ");
      }
    }
    
    if(children.size() > 0){
      sb.delete(sb.length()-1, sb.length());
      sb.append(")");
    }
    
    
    //sb.append("\t" + ((int) score));
    return sb.toString();
  }
  
  public String rhsString(){
    StringBuffer sb = new StringBuffer();
    for (IntTaggedWord dtr : children){
      if(isRHSTag) {
        sb.append(dtr.tagString() + " ");
      } else {
        sb.append(dtr.wordString() + " ");
      }
    }
    if(children.size() > 0){
      sb.delete(sb.length()-1, sb.length());
    }
    
    return sb.toString();
  }
  
  public String toString() {
    StringBuffer sb = new StringBuffer();
    sb.append(mother.tagString() + "->[");
    sb.append(rhsString());
    sb.append("] : " + score);
    return sb.toString();
  }

  /**
   * returns a collection of scored rules corresponding to all non-terminal productions from a collection of trees.
   */
  public static Collection<Rule> rulesFromTrees(Collection<Tree> trees) {
    Collection<Rule> rules = new ArrayList<Rule>();
    //GeneralizedCounter ruleCounts = new GeneralizedCounter(2);
    TwoDimensionalCounter<IntTaggedWord, List<IntTaggedWord>> ruleCounts = new TwoDimensionalCounter<IntTaggedWord, List<IntTaggedWord>>();
    
    // go through trees
    for(Tree tree:trees){
      for(Tree subTree : tree.subTreeList()){
        if (subTree.isLeaf() || subTree.isPreTerminal()) { // ignore leaf and preterminal nodes
          continue;
        }
     
        // increase count
        ruleCounts.incrementCount(new IntTaggedWord(IntTaggedWord.ANY, subTree.value()), dtrCatsList(subTree.children())); 
      }
    }

    for(IntTaggedWord mother: ruleCounts.firstKeySet()){ // go through all rules
      // normalize w.r.t to parent node
      Distribution<List<IntTaggedWord>> normalizedChildren = Distribution.getDistribution(ruleCounts.getCounter(mother));
      for(List<IntTaggedWord> childList : normalizedChildren.keySet()){
        rules.add(new Rule(mother, childList, normalizedChildren.getCount(childList)));
      }
    }

    return rules;
  }

  private static List<IntTaggedWord> dtrCatsList(Tree[] dtrs) {
    List<IntTaggedWord> l = new ArrayList<IntTaggedWord>(dtrs.length);
    for (int i = 0; i < dtrs.length; i++) {
      Tree dtr = dtrs[i];
      l.add(new IntTaggedWord(IntTaggedWord.ANY, dtr.value()));
    }
    return l;
  }

}

/*** Unused code ***/
//public Rule(String mother, List<String> children, double score) {
//  this(mother, children, score, true);
//}