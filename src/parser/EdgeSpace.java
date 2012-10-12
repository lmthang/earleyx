package parser;

import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import utility.Utility;

import edu.stanford.nlp.util.HashIndex;
import edu.stanford.nlp.util.Index;

/**
 * keeps track of edges as integers.  Guarantees that the integers are contiguous.
 *
 * @author Roger Levy
 * @author Minh-Thang Luong, 2012: optimize by allowing multiple active edges to 
 * share the same underlying edge structure, but differ in the dot positions.
 */
public class EdgeSpace {
  public static int verbose = 0;
  
  private Index<Edge> activeEdgeIndex; // index active edges
  
  private Map<Integer, Integer> index2stateMap; // map a nonterminal index to the state representing the edge nonterminal -> []

  private Set<Integer> passiveEdges;
  private Set<Integer> activeEdges;
  private Set<Integer> activeIndices; // set of nonterminals, each of which has least one nonterminal left-corner child

  private int size = -0; // number of distinct active edges
  private int[] via; // relate X -> A . B C to B -> []
  private int[] to;  // relate X -> A . B C to X -> A B . C   

  private Index<String> tagIndex; // map tag strings to tag integers
  public EdgeSpace(Index<String> tagIndex){
    this.tagIndex = tagIndex;
    activeEdgeIndex = new HashIndex<Edge>();
    index2stateMap = new HashMap<Integer, Integer>();
    activeIndices = new HashSet<Integer>();
    activeEdges = new HashSet<Integer>();
    passiveEdges = new HashSet<Integer>();
    
    via = new int[1000];
    to = new int[1000];
  }
  
  public void addRules(Collection<Rule> rules){
    if (verbose >= 1){
      System.err.println("\n# Setting up state space and category indices...");
      System.err.println("Normal rules ... ");
    }
    
    int numRules = 0;
    for (Rule r: rules) {
      addEdge(r.toEdge());
      
      if(verbose >= 1){
        if(++numRules % 10000 == 0){
          System.err.print(" (" + numRules + ") ");
        }
      }
    }
    
    if(verbose >= 3){
      System.err.println(Utility.sprint(rules, tagIndex, tagIndex));
    }
    if (verbose >= 1) {
      System.err.println(" Done! Num rules=" + numRules);
      System.err.println("# State space size=" + size 
          + ", num active indices=" + activeIndices.size());
    }
  }
  /**
   * Index of edge tag -> []
   * @param tag
   * @return
   */
  public int indexOfTag(int iT) {
    return index2stateMap.get(iT);
  }
  
  
  public int indexOf(Edge edge) {
    return activeEdgeIndex.indexOf(edge);
  }

  public Edge get(int edgeIndex) {
    return activeEdgeIndex.get(edgeIndex);
  }

  public int size() {
    return size;
  }

  public boolean isPassive(int state) {
    return via[state] == -1;
  }

  public int via(int state) {
    return via[state];
  }

  public int to(int state) {
    return to[state];
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
    via = ensureSize(via, size);
    to = ensureSize(to, size);
  }

  /* returns proper edge number */
  private int storeEdgeInIndex(Edge e) {
    int i = activeEdgeIndex.indexOf(e, true);
    assert(i<=size);
    if (i==size){ // new edge
      incrementSize();
    }
    return i;                               
  }
    
  /*
   * add edge (represent a rule) and keep track of mother, via, to edges
   */
  public int addEdge(Edge e) {
    // store mother -> [] edge if not in the state space
    int motherIndex = e.getMother();
    if(!index2stateMap.containsKey(motherIndex)){ 
      Edge motherEdge = e.getMotherEdge();
      int motherState = storeEdgeInIndex(motherEdge);
      via[motherState] = -1;
      to[motherState] = -1;
      index2stateMap.put(motherIndex, motherState);
      passiveEdges.add(motherState);
    }
    
    // store current state
    int state = storeEdgeInIndex(e);
  
    
    // check children
    if (e.numRemainingChildren() == 0) { // passive edge, no children
      assert(via[state]==-1); assert(to[state]==-1); assert(passiveEdges.contains(state));
    } else { // active edge
      activeIndices.add(motherIndex); // motherIndex has at least one nonterminal left-corner child
      activeEdges.add(state);
      
      // via edge: first child -> []
      Edge viaEdge = e.getViaEdge();
      int viaState = addEdge(viaEdge); 
      via[state] = viaState;
      
      // to edge: mother -> [second child onwards]
      // move dot position, this is where we have some saving
      assert(e.getDot()<e.numChildren());
      int toState = addEdge(new Edge(e.getEdge(), e.getDot()+1)); // recursively add edge
      to[state] = toState;
      
      if (verbose >= 3){
        System.err.println("Add edge " + state + "=" + e.toString(tagIndex, tagIndex) +  
            ", via " + viaState + "=" + get(viaState).toString(tagIndex, tagIndex) + 
            ", to " + toState + "=" + get(toState).toString(tagIndex, tagIndex));
      }
    }
   
    return state;
  }

  public String toString() {
    StringBuffer sb = new StringBuffer("");
    for (int i = 0; i < size; i++) {
      if (isPassive(i)) {
        sb.append("<passive=" + i + " (" + get(i).toString(tagIndex, tagIndex) + ")>\n");
      } else {
        sb.append("<active=" + i + ", via=" + via[i] + ", to=" + to[i] + //", mother=" + mother[i] + 
            " (" + get(i).toString(tagIndex, tagIndex) + ")>\n");
      }
    }
    return sb.toString();
  }
  
  /** Getters **/
  public Set<Integer> getActiveIndices() {
    return activeIndices;
  }
  public Set<Integer> getPassiveEdges() {
    return passiveEdges;
  }
  public Set<Integer> getActiveEdges() {
    return activeEdges;
  }
}

/***** Unused code *****/
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