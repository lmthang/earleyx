package parser;

import java.text.DecimalFormat;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import edu.stanford.nlp.parser.lexparser.IntTaggedWord;
import edu.stanford.nlp.util.HashIndex;

/**
 * keeps track of edges as integers.  Guarantees that the integers are contiguous.
 *
 * @author Roger Levy
 */
/* Thang v110901: change into generic version */
class StateSpace {
  public static int verbose = 0;
  private Map<Edge, Integer> index = new HashMap<Edge, Integer>();
  private List<Edge> edges = new ArrayList<Edge>();
  
  /** Thang move from Grammar **/
  /* Indexes non-terminal (withou preterminals), index by state id */
  private HashIndex<Integer> nontermIndexer = new HashIndex<Integer>(); // map tagId -> linear Index (non-terminals)

  /* Indexes all non-terminals, including preterminals, index by state id */
  private HashIndex<Integer> nontermPretermIndexer = new HashIndex<Integer>(); // map tagId -> linear Index (non-terminals + pre-terminals)

  private Set<Integer> activeStates = new HashSet<Integer>();
  private Set<Integer> passiveStates = new HashSet<Integer>();
  private static DecimalFormat df = new DecimalFormat("0.0000");
  public Set<Integer> getActiveStates() {
    return activeStates;
  }

  public Set<Integer> getPassiveStates() {
    return passiveStates;
  }

  /** Thang: record passive/active states **/
  private Map<Integer, Set<Integer>> viaStateMap = new HashMap<Integer, Set<Integer>>(); 
  public Map<Integer, Set<Integer>> getViaStateMap() {
    return viaStateMap;
  }

  /** Getters **/
  public HashIndex<Integer> getNontermIndexer(){
    return nontermIndexer;
  }
  
  public HashIndex<Integer> getNontermPretermIndexer(){
    return nontermPretermIndexer;
  }
//  public Map<Integer, Integer> getNontermTagToEdgeMap() {
//    return nontermTagToEdgeMap;
//  }
//  public Map<Integer, Integer> getNontermEdgeToTagMap(){
//    return nontermEdgeToTagMap;
//  }
  /** Thang: end record passive/active states **/
  
  // Thang move from BasicLexicon
  private static Edge dummyEdge = new Edge(null, new ArrayList<IntTaggedWord>());
  public int indexOfTag(IntTaggedWord tagITW) {
    dummyEdge.setMother(tagITW); //new IntTaggedWord(IntTaggedWord.ANY, tag);
    return indexOf(dummyEdge);
  }
  
  
  public int indexOf(Edge o) {
    Integer i = index.get(o);
    if (i == null) {
      return -1;
    } else {
      return i.intValue();
    }
  }

  public Edge get(int state) {
    return edges.get(state);
  }

  int[] via = new int[10];
  int[] to = new int[10];
  int[] mother = new int[10];
  int size = 0;

  public int size() {
    return size;
  }


  public boolean isPassive(int state) {
    return via[state] == -1; // Thang change via(state) -> via[state]
  }

  public int via(int state) {
    return via[state];
  }

  public int to(int state) {
    return to[state];
  }

  public int mother(int state) {
    return mother[state];
  }

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
    mother = ensureSize(mother, size);
  }

  /* returns proper edge number */
  private int storeEdgeInIndex(Edge e) {
    int i = indexOf(e);
    if (i != -1) {
      return i;
    } else {
      i = size;
      incrementSize();
      index.put(e, new Integer(i));
      edges.add(e);
      return i;
    }                               
  }
  
  /*
   * Thang: add edge (represent a rule) and keep track of mother, via, to edges
   */
  public int addEdge(Edge e) {
    // store current state
    int state = storeEdgeInIndex(e);
    
    // get mother state
    Edge motherEdge = e.getMotherEdge();
    int motherState = storeEdgeInIndex(motherEdge);
    
    // create mother state which doesn't have any children, lhs -> []
    mother[state] = motherState; // array access happens before evaluation of the right-hand side!
    via[motherState] = -1;
    to[motherState] = -1;
    passiveStates.add(motherState);
    
    /* Thang move from Grammar */
    //int tagId = e.mother.tag();
    nontermPretermIndexer.add(motherState); // this needs to be outside of if
    
    // check children
    if (e.getDtrs().isEmpty()) { // passive edge, no children
      via[state] = -1;
      to[state] = -1;
      passiveStates.add(state);
    } else { // active edge
      // via edge: first child -> []
      Edge viaEdge = e.getChildEdge(0);
      int viaState = addEdge(viaEdge);
      via[state] = viaState;
      activeStates.add(state);
      
      // to edge: mother -> [second child onwards]
      Edge toEdge = new Edge(e.getMother(), e.getDtrs().subList(1, e.getDtrs().size()));
      int toState = addEdge(toEdge); // recursively add edge
      to[state] = toState;
      
      if (verbose >= 3){
        System.err.println("Add edge " + state + "=" + e + 
            ", mother edge " + motherState + "=" + motherEdge + 
            ", via " + viaState + "=" + viaEdge + ", to " + toState + "=" + toEdge);
      }
      
      nontermIndexer.add(motherState); // this needs to be inside the if
      // update viaStateMap
      if(!viaStateMap.containsKey(viaState)){ // initialize
        viaStateMap.put(viaState, new HashSet<Integer>());
      }
      viaStateMap.get(viaState).add(state);
    }
   
    //System.err.println(e + "\t" + via[state] + "\t" + state);
    return state;
  }

  public int addRule(Rule r) {
    return addEdge(r.toEdge());
  }

  public String toString() {
    StringBuffer sb = new StringBuffer("");
    for (int i = 0; i < size; i++) {
      if (isPassive(i)) {
        sb.append("<passive=" + i + " (" + get(i) + ") >\n");
      } else {
        sb.append("<active=" + i + ", via=" + via[i] + ", to=" + to[i] + ", mother=" + mother[i] + 
            " (" + get(i) + ") >\n");
      }
    }
    return sb.toString();
  }

  
  class Prediction {
    int result;
    double forwardProbMultiplier;
    double innerProbMultiplier;

    public Prediction(int result, double forwardProbMultiplier, double innerProbMultiplier) {
      this.result = result;
      this.forwardProbMultiplier = forwardProbMultiplier;
      this.innerProbMultiplier = innerProbMultiplier;
    }

    public boolean equals(Object o) {
      if (this == o) {
        return true;
      }
      if (!(o instanceof Prediction)) {
        return false;
      }

      final Prediction prediction = (Prediction) o;

      if (forwardProbMultiplier != prediction.forwardProbMultiplier) {
        return false;
      }
      if (innerProbMultiplier != prediction.innerProbMultiplier) {
        return false;
      }
      if (result != prediction.result) {
        return false;
      }

      return true;
    }

    public int hashCode() {
      int result1;
      long temp;
      result1 = result;
      temp = forwardProbMultiplier != +0.0d ? Double.doubleToLongBits(forwardProbMultiplier) : 0l;
      result1 = 29 * result1 + (int) (temp ^ (temp >>> 32));
      temp = innerProbMultiplier != +0.0d ? Double.doubleToLongBits(innerProbMultiplier) : 0l;
      result1 = 29 * result1 + (int) (temp ^ (temp >>> 32));
      return result1;
    }

    public String toString() {
      String str = "(";
      str += StateSpace.this.get(result) + ",";
      str += df.format(Math.exp(forwardProbMultiplier)) + "," + df.format(Math.exp(innerProbMultiplier)) + ")";
      return str;
    }

  }

  /* combine backward with activeChild to get result. */
  class BackwardCombination {
    int activeChild;
    int result;
    double score; // always greater than or equal to zero; derives from unary closure

    /* constructor, equals and hashCode are auto-generated */


    public BackwardCombination(int activeChild, int result, double score) {
      this.result = result;
      this.activeChild = activeChild;
      this.score = score;
    }

    public boolean equals(Object o) {
      if (this == o) {
        return true;
      }
      if (!(o instanceof BackwardCombination)) {
        return false;
      }

      final BackwardCombination backwardCombination = (BackwardCombination) o;

      if (activeChild != backwardCombination.activeChild) {
        return false;
      }
      if (result != backwardCombination.result) {
        return false;
      }

      return true;
    }

    public int hashCode() {
      int result1;
      result1 = activeChild;
      result1 = 29 * result1 + result;
      return result1;
    }

    public String toString() {
      //return activeChild + "<-" + score + "->" + result;
      return "active=" + StateSpace.this.get(activeChild) + ", result=" + StateSpace.this.get(result) + " : " + score;
    }

  }

}
