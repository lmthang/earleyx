package parser;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import recursion.ClosureMatrix;
import utility.Utility;
import edu.stanford.nlp.util.Index;
import edu.stanford.nlp.util.Timing;

/* combine backward with activeChild to get result. */
public class Completion {
  public static int verbose = 0;
  private static final Completion[] NO_COMPLETION = new Completion[0];
  //private static DecimalFormat df = new DecimalFormat("0.0");
  
  int activeEdge; // the state just before completion, stateSpace.to(activeState) gives the completed state
  int completedEdge; // this variable could be removed if memory is an issue
  double score; // always greater than or equal to zero; derives from unary closure

  public Completion(int activeEdge, int completedEdge, double score) {
    this.activeEdge = activeEdge;
    this.completedEdge = completedEdge;
    this.score = score;
  }

  /**
   * Construct completions
   * @param rules
   * @param allNontermCategories
   * @param unaryClosures
   * @return
   */
  public static Completion[][] constructCompletions(ClosureMatrix unaryClosures, 
      EdgeSpace edgeSpace, Index<String> tagIndex){
    
    /* do completion Set[] */
    if(verbose > 0){
      System.err.println("\n## Constructing completions ...");
      Timing.startTime();
    }
    
    Map<Integer, Set<Completion>> tag2completionsMap = new HashMap<Integer, Set<Completion>>(); 
    //Map<Integer, List<Completion>> tag2completionsMap = new HashMap<Integer, List<Completion>>();

    // TODO: move for passiveEdge outside
    for(Integer activeEdge : edgeSpace.getActiveEdges()){ // go through active states, X -> Z \alpha      
      int viaEdge = edgeSpace.via(activeEdge);
      int viaCategoryIndex = edgeSpace.get(viaEdge).getMother(); // Z
      assert(viaEdge>=0);
      assert(edgeSpace.get(viaEdge).numChildren()==0);
     
//      System.err.println(activeEdge + "\t" + edgeSpace.get(activeEdge).toString(tagIndex, tagIndex));
      
      // get passive states
      if(unaryClosures.containsRow(viaCategoryIndex)){ // non-zero rows in closure matrix, there exists some Y that R(Z->Y) is non-zero 
        // go through passive state
        for(Integer passiveEdge : edgeSpace.getPassiveEdges()){ // go through passive states, Y -> []
          int passiveCategoryIndex = edgeSpace.get(passiveEdge).getMother(); 
          
          double unaryClosureScore = Double.NEGATIVE_INFINITY;
          if (passiveCategoryIndex >= 0){
            unaryClosureScore = unaryClosures.get(viaCategoryIndex, passiveCategoryIndex); // R(Z -> Y)
          }
          
          if (unaryClosureScore != Double.NEGATIVE_INFINITY) {
            if (!tag2completionsMap.containsKey(passiveEdge)) {
              tag2completionsMap.put(passiveEdge, new HashSet<Completion>());
              //tag2completionsMap.put(passiveEdge, new LinkedList<Completion>());
            }
            tag2completionsMap.get(passiveEdge).add(new Completion(activeEdge, edgeSpace.to(activeEdge), unaryClosureScore));
//            System.err.println((new Completion(activeEdge, edgeSpace.to(activeEdge), unaryClosureScore)).toString(edgeSpace, tagIndex));
          }
        }
      } else { // for zero row, there is only passive state, which is the via state Z -> []
        int passiveState = viaEdge; 

        if (!tag2completionsMap.containsKey(passiveState)) {
          tag2completionsMap.put(passiveState, new HashSet<Completion>());
          //tag2completionsMap.put(passiveState, new LinkedList<Completion>());
        }
      
        tag2completionsMap.get(passiveState).add(new Completion(activeEdge, edgeSpace.to(activeEdge), 0.0));
      }
    }

    Completion[][] completions = new Completion[edgeSpace.size()][];
    for (int edge = 0; edge < edgeSpace.size(); edge++) {
      if(tag2completionsMap.containsKey(edge)){
        List<Completion> l = new ArrayList<Completion>(tag2completionsMap.get(edge));
        completions[edge] = (Completion[]) l.toArray(NO_COMPLETION);
        
        if(verbose >= 3){
          System.err.println("Edge " + tagIndex.get(edgeSpace.get(edge).getMother())
              + ": completions " + Utility.sprint(completions[edge], edgeSpace, tagIndex));
        }
      } else {
        completions[edge] = NO_COMPLETION;
      }
      
    }
    
    if (verbose >= 1) {
      Timing.tick("Done with completion");
    }
    
    return completions;
  }
  
  /* check to see if any of the completions are invalid*/
  public static boolean checkCompletions(Completion[][] completionsArray
      , EdgeSpace edgeSpace, Index<String> tagIndex) {
    boolean satisfied = true;
    for (int i = 0; i < completionsArray.length; i++) {
      Completion[] completions = completionsArray[i];
      for (int j = 0; j < completions.length; j++) {
        Completion completion = completions[j];
        Edge active = edgeSpace.get(completion.activeEdge);
        Edge result = edgeSpace.get(edgeSpace.to(completion.activeEdge));
        
        // compare mother
        if (active.getMother() != result.getMother()) {
          System.err.println("Error -- mother categories of active edge " + active.toString(tagIndex, tagIndex) 
              + " " + active + " and result " + result.toString(tagIndex, tagIndex) + " " + result + " are not identical");
          satisfied = false;
        }
        
        // compare children: children of active shifted 1 to the right should be equal to those of result
        if (!active.getChildrenAfterDot(1).equals(result.getChildrenAfterDot(0))) {
          System.err.println("Error -- dtrs lists of active edge " + active.toString(tagIndex, tagIndex)  + 
              active + " and result " + result.toString(tagIndex, tagIndex) + result + " are not consistent.");
          satisfied = false;
        }
      }
    }
    return satisfied;
  }
  
  public boolean equals(Object o) {
    if (this == o) {
      return true;
    }
    if (!(o instanceof Completion)) {
      return false;
    }

    final Completion backwardCombination = (Completion) o;

    if (activeEdge != backwardCombination.activeEdge) {
      return false;
    }

    return true;
  }

  public int hashCode() {
    return (int)score<<16 + activeEdge;
  }

  public String toString(EdgeSpace edgeSpace, Index<String> tagIndex) {
    return "(" + edgeSpace.get(activeEdge).toString(tagIndex, tagIndex) 
    + ", " + edgeSpace.get(completedEdge).toString(tagIndex, tagIndex) + ", " + Math.exp(score) + ")"; //df.format()
  }

}

/** Unused code **/
//Completion[][] backwardCombinations = new Completion[tagIndex.size()][];
//for (int iT = 0; iT < backwardCombinations.length; iT++) {
//  int passiveState = stateSpace.indexOfTag(iT);
//  
//Map<Integer, Set<Completion>> backComboMap = new HashMap<Integer, Set<Completion>>(); 
//
//for(Integer activeState : stateSpace.getActiveStates()){ // go through active states, X -> Z \alpha      
//  int viaState = stateSpace.via(activeState);
//  int viaCategoryIndex = stateSpace.get(viaState).getMother(); // Z
//  assert(viaState>=0);
//  assert(stateSpace.get(viaState).numChildren()==0);
//  
////  if(verbose >= 2){
////    System.err.println("Active state " + activeState + ": " + stateSpace.get(activeState)
////        + ", via state " + viaState + ": " + stateSpace.get(viaState));
////  }
//  
//  // get passive states
//  if(unaryClosures.containsRow(viaCategoryIndex)){ // non-zero rows in closure matrix, there exists some Y that R(Z->Y) is non-zero 
//    // go through passive state
//    for(Integer passiveState : stateSpace.getPassiveStates()){ // go through passive states, Y -> []
//      int passiveCategoryIndex = stateSpace.get(passiveState).getMother(); 
//      
//      double unaryClosureScore = Double.NEGATIVE_INFINITY;
//      if (passiveCategoryIndex >= 0){
//        unaryClosureScore = unaryClosures.get(viaCategoryIndex, passiveCategoryIndex); // R(Z -> Y)
//      }
//      
//      if (unaryClosureScore != Double.NEGATIVE_INFINITY) {
//        if (!backComboMap.containsKey(passiveState)) {
//          backComboMap.put(passiveState, new HashSet<Completion>());
//          //backPassiveStateIndexer.add(passiveState);
//        }
//        backComboMap.get(passiveState).add(new Completion(activeState, stateSpace.to(activeState), unaryClosureScore));
//      }
//    }
//  } else { // for zero row, there is only passive state, which is the via state Z -> []
//    int passiveState = viaState; 
//
//    if (!backComboMap.containsKey(passiveState)) {
//      backComboMap.put(passiveState, new HashSet<Completion>());
//      //backPassiveStateIndexer.add(passiveState);
//    }
//  
//    backComboMap.get(passiveState).add(new Completion(
//        activeState, stateSpace.to(activeState), 0.0));
//  }
//}
//
//// BackwardCombination[][] backwardCombinations = new BackwardCombination[numBackPassiveStates][];
//Completion[][] backwardCombinations = new Completion[stateSpace.size()][];
//for (int passiveState = 0; passiveState < stateSpace.size(); passiveState++) {
////for (int i = 0; i < numBackPassiveStates; i++) {
//  //int passiveState = backPassiveStateIndexer.get(i);
//  if(backComboMap.containsKey(passiveState)){
//    Set<Completion> backCombo = backComboMap.get(passiveState);
//    List<Completion> l = new ArrayList<Completion>();
//    l.addAll(backCombo);
//    //backwardCombinations[i] = (BackwardCombination[]) l.toArray(noBackwardCombinations);
//    backwardCombinations[passiveState] = (Completion[]) l.toArray(noBackwardCombinations);
//    
//    if(verbose >= 3){
//      System.err.println("Completions for state " + stateSpace.get(passiveState) + ": " + l);
//    }
//  } else {
//    backwardCombinations[passiveState] = noBackwardCombinations;
//  }
//}