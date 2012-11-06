/**
 * 
 */
package parser;

import java.io.BufferedReader;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;

import edu.stanford.nlp.trees.Treebank;
import edu.stanford.nlp.util.Timing;

/**
 * @author Minh-Thang Luong, 2012
 *
 */
public class EarleyParserSparseIO extends EarleyParserSparse {
  protected Map<Integer, Map<Integer, Double>> outterProb;     // outterProb.get(linear[leftEdge][rightEdge]).get(categoryNumber)
  
  // backtracks.get(linear[leftEdge][rightEdge]).get(completedState).get(fromState): list of middle positions j
  // Specifically, if completed state is i: k X -> _ . alpha
  // fromState is j: X -> _ . Y alpha, we have Y spans over [j k]
  // where j is a position between [k, i]
  protected Map<Integer, Map<Integer, Map<Integer, Set<Integer>>>> backtracks;  
  
  public EarleyParserSparseIO(BufferedReader br, String rootSymbol,
      boolean isScaling, boolean isLogProb, boolean isLeftWildcard) {
    super(br, rootSymbol, isScaling, isLogProb, isLeftWildcard);
  }

  public EarleyParserSparseIO(String grammarFile, String rootSymbol,
      boolean isScaling, boolean isLogProb, boolean isLeftWildcard) {
    super(grammarFile, rootSymbol, isScaling, isLogProb, isLeftWildcard);
  }

  public EarleyParserSparseIO(Treebank treebank, String rootSymbol,
      boolean isScaling, boolean isLogProb, boolean isLeftWildcard) {
    super(treebank, rootSymbol, isScaling, isLogProb, isLeftWildcard);
  }

  protected void sentInit(){
    super.sentInit();
    
    if (verbose>=2){
      System.err.println("# EarleyParserSparseIO initializing ... ");
      Timing.startTime();
    }
  
    outterProb = new HashMap<Integer, Map<Integer,Double>>();
    backtracks = new HashMap<Integer, Map<Integer,Map<Integer,Set<Integer>>>>();
  }
}

//private Map<Integer, Map<Integer, Set<Integer>>> theseBacktrack;
//
//@Override
//protected void completeAll(int right) {
//  for (int left = right - 1; left >= 0; left--) {
//    // backtrack 
//    theseBacktrack = new HashMap<Integer, Map<Integer,Set<Integer>>>();
//    
//    for (int middle = right - 1; middle >= left; middle--) {
//      completeAll(left, middle, right);
//    } // end middle
//    
//    // backtrack
//    int lrIndex = linear[left][right];
//    assert(!backtracks.containsKey(lrIndex));
//    backtracks.put(lrIndex, theseBacktrack);
//  } // end left
//  
//  storePrefixProb(right);
//  if(verbose>=3){
//    dumpChart();
//  }
//}
//
//protected void complete(int left, int middle, int right, int tag, double inner) {
//  // we already completed the edge, right: middle Y -> _ ., where passive represents for Y
//  Completion[] completions = g.getCompletions1(tag);
//  
//  if (verbose>=3 && completions.length>0){
//    System.err.println("Complete [middle=" + middle + ", right=" + right + "] " + parserTagIndex.get(tag) 
//        + ", inside prob = " + inner  
//        + ", completions: " + Util.sprint(completions, edgeSpace, parserTagIndex, operator));
//  }
//  
//  int lmIndex = linear[left][middle]; // left middle index
//  assert(forwardProb.containsKey(lmIndex));
//  Map<Integer, Double> forwardMap = forwardProb.get(lmIndex);
//  Map<Integer, Double> innerMap = innerProb.get(lmIndex);
//  
//  for (int x = 0, n = completions.length; x < n; x++) { // go through all completions we could finish
//    Completion completion = completions[x];
//    
//    if (forwardMap.containsKey(completion.activeEdge)) { // middle: left X -> _ . Y _
//      // backtrack
//      if(!theseBacktrack.containsKey(completion.completedEdge)){
//        theseBacktrack.put(completion.completedEdge, new HashMap<Integer, Set<Integer>>());
//      }
//      
//      complete(left, middle, right, completion, forwardMap, innerMap, inner);
//      
//      // backtrack
//      if(!theseBacktrack.get(completion.completedEdge).containsKey(completion.activeEdge)){
//        theseBacktrack.get(completion.completedEdge).put(completion.activeEdge, new HashSet<Integer>());
//      }
//      theseBacktrack.get(completion.completedEdge).get(completion.activeEdge).add(middle);
//    }
//  }
//}