package parser;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.Set;

import base.Edge;
import base.ProbRule;
import base.Rule;

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
  public EdgeSpace(Index<String> tagIndex){
    this.tagIndex = tagIndex;
    edgeIndex = new HashIndex<Edge>();
    activeEdges = new HashSet<Integer>();
    
    to = new int[1000];
  }
  
  public void build(Collection<ProbRule> rules){
    if (verbose >= 1){
      System.err.println("\n## Setting up edge space ...");
    }
    if(verbose >= 3){
      System.err.println("Rules: " + Util.sprint(rules, tagIndex, tagIndex));
    }
    
    int numRules = 0;
    for (ProbRule r: rules) {
      addEdge(r.getEdge());
      
      if(verbose >= 1){
        if(++numRules % 10000 == 0){
          System.err.print(" (" + numRules + ") ");
        }
      }
    }
    
    
    
    // add preterminals that we haven't seen
    for (int iT = 0; iT < tagIndex.size(); iT++) {
      if(indexOfTag(iT) == -1){ // add preterminal -> []
        if(verbose>=3){
          System.err.println("Add preterminal " + tagIndex.get(iT) + " to edge space");
        }
        addEdge(Edge.createTagEdge(iT));
      }
    }
     
    if (verbose >= 1) {
      Timing.tick("Done! Num rules=" + numRules + ", state space size=" + size);
    }
  }
  
  protected abstract Edge getToEdge(Edge e);
  
  /**
  * Index of edge tag -> []
  * @param tag
  * @return
  */
  private Edge dummyEdge = new Edge(new Rule(0, new ArrayList<Integer>()), 0);
  public int indexOfTag(int iT) {
   dummyEdge.setMother(iT);
   return edgeIndex.indexOf(dummyEdge);
  }
  
  /*
   * add edge (represent a rule) and keep track of mother, via, to edges
   */
  public int addEdge(Edge e) {
    if(edgeIndex.contains(e)){
      return indexOf(e);
    }
    
    // store current edge
    int state = storeEdgeInIndex(e);
        
    // check children
    if (e.numRemainingChildren() == 0) { // passive edge, no children
      to[state] = -1;
    } else { // active edge
//      activeIndices.add(motherIndex); // motherIndex has at least one nonterminal left-corner child
      activeEdges.add(state);
      
      // via edge: first child -> []
      addEdge(e.getViaEdge());
//      int viaState = addEdge(e.getViaEdge()); 
      
      // to edge: mother -> [second child onwards]
      assert(e.getDot()<e.numChildren());
    
      // Note: different classes of EdgeSpace will implement getToEdge differently
      int toState = addEdge(getToEdge(e)); // recursively add edge 
      to[state] = toState; // NOTE: it would be wrong to combine this line with the above line
      
      // move dot position, this is where we have some saving
      //int toState = addEdge(new Edge(e.getRule(), e.getDot()+1)); // recursively add edge
      
      if (verbose >= 3){
        System.err.println("Add edge " + state + "=" + e.toString(tagIndex, tagIndex) +  
            ", to " + to[state] + "=" + get(to[state]).toString(tagIndex, tagIndex));
      }
    }
   
    return state;
  }
  
  public int indexOf(Edge edge) {
    return edgeIndex.indexOf(edge);
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
  
  public String toString() {
    StringBuffer sb = new StringBuffer("");
    for (int i = 0; i < size; i++) {
      if (get(i).numRemainingChildren()==0) {
        sb.append("<passive=" + i + " (" + get(i).toString(tagIndex, tagIndex) + ")>\n");
      } else {
        sb.append("<active=" + i + ", to=" + to[i] + //", mother=" + mother[i] + 
            " (" + get(i).toString(tagIndex, tagIndex) + ")>\n");
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

//// store mother -> [] edge if not in the state space
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
////mother = new int[2*size];
//
//// add tag edges: tag -> [] to both activeEdgeIndex and passiveStates
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

//// get mother state: mother -> []
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