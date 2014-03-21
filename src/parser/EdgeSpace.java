package parser;

import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import base.Edge;
import base.MotherRule;
import base.ProbRule;

import util.Util;

import edu.stanford.nlp.util.HashIndex;
import edu.stanford.nlp.util.Index;
import edu.stanford.nlp.util.Timing;

/**
 * keeps track of edges as integers.  Guarantees that the integers are contiguous.
 *
 * @author Roger Levy
 * @author Minh-Thang Luong, 2012: optimize by allowing multiple active edges to 
 * share the same underlying edge structure, but differ in the dot positions.
 */
public abstract class EdgeSpace {
  public static int verbose = 0;
  
  protected Index<Edge> edgeIndex; // index edges
  protected Set<Integer> activeEdges; // store edges in which num remaining children (after dot)>0
  
  protected int size = 0; // number of distinct active edges
  protected int[] to;  // relate X -> A . B C to X -> A B . C   

  protected Index<String> tagIndex; // map tag strings to tag integers
  protected Index<String> wordIndex; // map tag strings to tag integers
  protected Map<Integer, Set<Integer>> terminal2fragmentEdges; // map word index of _y to a list of edges X -> . _y Z T, where the first token after the dot is _y
 protected int[] tagEdgeMap; // tagEdgeMap[i]: give an edge number for tag i 

  public EdgeSpace(Index<String> tagIndex, Index<String> wordIndex){
    this.tagIndex = tagIndex;
    this.wordIndex = wordIndex;
    
    edgeIndex = new HashIndex<Edge>();
    activeEdges = new HashSet<Integer>();
    terminal2fragmentEdges = new HashMap<Integer, Set<Integer>>();
    to = new int[1000];
  }
  
  public void build(Collection<ProbRule> rules){
  Timing.startDoing("\n## Setting up edge space");
  	if(verbose>=3) System.err.println("Rules: " + Util.sprint(rules, tagIndex, wordIndex));
    
    int numRules = 0;
    for (ProbRule r: rules) {
      addEdge(r.getEdge());
      
      if(verbose >= 0){
        if(++numRules % 10000 == 0){
          System.err.print(" (" + numRules + ") ");
        }
      }
    }
     
    // add preterminals that we haven't seen
    tagEdgeMap = new int[tagIndex.size()];
    for (int tag = 0; tag < tagIndex.size(); tag++) {
      Edge e = new Edge(new MotherRule(tag), 0);
      if(!edgeIndex.contains(e)){ // add preterminal -> []
        if(verbose>=3) System.err.println("Add preterminal " + tagIndex.get(tag) + " to edge space");
        addEdge(e);
      }
      tagEdgeMap[tag] = indexOf(e);
    }
   Timing.endDoing("Num rules=" + numRules + ", state space size=" + size + ".");
  }
  
  protected abstract Edge getToEdge(Edge e);
  
  /*
   * add edge (represent a rule) and keep track of mother, via, to edges
   */
  public int addEdge(Edge e) {
    if(edgeIndex.contains(e)){
      return indexOf(e);
    }
    
    if (verbose >= 3){
      System.err.println("Adding " + e.toString(tagIndex, wordIndex));
    }
    
    // store current edge
    int state = storeEdgeInIndex(e);
      
    // check children
    if (e.numRemainingChildren() == 0) { // passive edge, no children
      to[state] = -1;
    } else  { //if(e.getRule().isTagRule()){ // tag rule, active edge
      activeEdges.add(state);
      
      // fragment rules
      if (!e.isTagAfterDot(0)){ // X -> _y Z T
        int childIndex = e.getChildAfterDot(0);
        if(!terminal2fragmentEdges.containsKey(childIndex)){
          terminal2fragmentEdges.put(childIndex, new HashSet<Integer>());
        }
        terminal2fragmentEdges.get(childIndex).add(state); // map _y to X -> _y Z T
      }
      
//      while (e.numRemainingChildren()>=2 && !e.isTagAfterDot(0) && !e.isTagAfterDot(1)){ // X -> _y _z T
//        e = getToEdge(e);
//      }
      
      // via edge: first child -> []
      Edge viaEdge = e.getViaEdge(); 
      if(viaEdge != null){
        addEdge(viaEdge);
      }
 
      
      // to edge: mother -> [second child onwards]
      assert(e.getDot()<e.numChildren());
    
      // Note: different classes of EdgeSpace will implement getToEdge differently
      int toState = addEdge(getToEdge(e)); // recursively add edge 
      to[state] = toState; // NOTE: it would be wrong to combine this line with the above line
      
      if (verbose >= 3){
        System.err.println("# Edge added: " + state + "=" + e.toString(tagIndex, wordIndex) +
            //", via edge " + via[state] + "=" + get(via[state]).toString(tagIndex, wordIndex) + 
            ", to edge " + to[state] + "=" + get(to[state]).toString(tagIndex, wordIndex));
      }
    }
   
    return state;
  }
  
  public int indexOf(Edge edge) {
    return edgeIndex.indexOf(edge);
  }

  /**
  * Index of edge tag -> []
  * @param tag
  * @return
  */
  public int indexOfTag(int tag) {
    return tagEdgeMap[tag];
  }
  
  public int size() {
    return size;
  }

  public int to(int edge) {
    return to[edge];
  }


  // extend array size if there's not enough space
  private int[] ensureSize(int[] array, int size) {
    if (array.length == size) {
      int[] newArray = new int[array.length * 2];
      System.arraycopy(array, 0, newArray, 0, array.length);
      return newArray;
    } else {
      return array;
    }
  }

  private void incrementSize() {
    size++;
    to = ensureSize(to, size);
  }

  /* returns proper edge number */
  protected int storeEdgeInIndex(Edge e) {
    int i = edgeIndex.indexOf(e, true);
    assert(i<=size);
    if (i==size){ // new edge
      incrementSize();
    }
    return i;                               
  }
 
  public Set<Integer> getFragmentEdges(int iW){
    return terminal2fragmentEdges.get(iW);
  }
  
  public Map<Integer, Set<Integer>> getTerminal2fragmentEdges() {
    return terminal2fragmentEdges;
  }
  
  public String toString() {
    StringBuffer sb = new StringBuffer("");
    for (int i = 0; i < size; i++) {
      if (get(i).numRemainingChildren()==0) {
        sb.append("<passive=" + i + " (" + get(i).toString(tagIndex, wordIndex) + ")>\n");
      } else {
        sb.append("<active=" + i + ", to=" + to[i] + //", mother=" + mother[i] + 
            " (" + get(i).toString(tagIndex, wordIndex) + ")>\n");
      }
    }
    return sb.toString();
  }
  
  /** Getters **/
  public Edge get(int edge) {
    return edgeIndex.get(edge);
  }
  public Set<Integer> getActiveEdges() {
    return activeEdges;
  }
}

/***** Unused code *****/
//protected int[] via; // relate X -> A . B C to B -> []
//via = new int[1000];
//via[state] = -1;
//via[state] = viaState;
//public boolean isPassive(int edge) {
//  return via[edge] == -1;
//}
//
//public int via(int edge) {
//  return via[edge];
//}
//via = ensureSize(via, size);
//", via " + via[state] + "=" + get(via[state]).toString(tagIndex, tagIndex) + 

//protected Set<Integer> passiveEdges;
//passiveEdges = new HashSet<Integer>();

//public Set<Integer> getPassiveEdges() {
//return passiveEdges;
//}

//if(this instanceof LeftWildcardEdgeSpace){
//  assert(size == (activeEdges.size() + passiveEdges.size()));
//}

//if(this instanceof LeftWildcardEdgeSpace && e.numChildren()==0){
//assert(via[state]==-1); assert(to[state]==-1); assert(passiveEdges.contains(state));
//} else {
//via[state] = -1;
//to[state] = -1;
//}

// store mother -> [] edge if not in the state space
//int motherIndex = e.getMother();
//if(!index2stateMap.containsKey(motherIndex)){ 
//  Edge motherEdge = e.getMotherEdge();
//  int motherState = storeEdgeInIndex(motherEdge);
//  via[motherState] = -1;
//  to[motherState] = -1;
//  index2stateMap.put(motherIndex, motherState);
//  passiveEdges.add(motherState);
//}

//protected Map<Integer, Integer> index2stateMap; // map a nonterminal index to the state representing the edge nonterminal -> []
//index2stateMap = new HashMap<Integer, Integer>();

//protected Set<Integer> activeIndices; // set of nonterminals, each of which has least one nonterminal left-corner child
//activeIndices = new HashSet<Integer>();
//if(verbose >= 3){      
//  System.err.println("Active indices: " + Util.sprint(tagIndex, activeIndices));
//}
//
//+ ", num active indices=" + activeIndices.size()
//  public Set<Integer> getActiveIndices() {
//    return activeIndices;
//  }

//private ActiveEdge dummyEdge = new ActiveEdge(new Edge(-1, new ArrayList<Integer>()), 0);
//dummyEdge.setMother(iT);
//return indexOf(dummyEdge);


//
//size = tagIndex.size();
//via = new int[2*size];
//to = new int[2*size];
//mother = new int[2*size];
//
// add tag edges: tag -> [] to both activeEdgeIndex and passiveStates
//for (int iT = 0; iT < size; iT++) {
//  tag2edgeMap.put(iT, ActiveEdge.createTagEdge(iT));
//  int index = activeEdgeIndex.indexOf(tag2edgeMap.get(iT), true);
//  assert(index == iT);
//  passiveStates.add(index);
//  via[index] = -1;
//  to[index] = -1; 
//}

//

//public Index<String> getTagIndex(){
//  return tagIndex;
//}

// get mother state: mother -> []
//ActiveEdge motherEdge = e.getMotherEdge();
//int motherState = storeEdgeInIndex(motherEdge);
//
//mother[state] = motherState; // array access happens before evaluation of the right-hand side!
//via[motherState] = -1;
//to[motherState] = -1;
//passiveStates.add(motherState);

//private Map<Edge, Integer> index = new HashMap<Edge, Integer>();
//private List<Edge> edges = new ArrayList<Edge>();
//
///** Thang move from Grammar **/
///* Indexes non-terminal (withou preterminals), index by state id */
//private HashIndex<Integer> nontermIndexer = new HashIndex<Integer>(); // map tagId -> linear Index (non-terminals)
//
///* Indexes all non-terminals, including preterminals, index by state id */
//private HashIndex<Integer> nontermPretermIndexer = new HashIndex<Integer>(); // map tagId -> linear Index (non-terminals + pre-terminals)

//public HashIndex<Integer> getNontermIndexer(){
//  return nontermIndexer;
//}
//
//public HashIndex<Integer> getNontermPretermIndexer(){
//  return nontermPretermIndexer;
//}
