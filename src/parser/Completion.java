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
  private static final Completion[] noBackwardCombinations = new Completion[0];

  int activeState; // the state just before completion, stateSpace.to(activeState) gives the completed state
  int completedState;
  double score; // always greater than or equal to zero; derives from unary closure

  public Completion(int activeState, int result, double score) {
    this.activeState = activeState;
    this.completedState = result;
    this.score = score;
  }

  /**
   * Construct backward combinations
   * @param rules
   * @param allNontermCategories
   * @param unaryClosures
   * @return
   */
  public static Completion[][] constructCompletions(ClosureMatrix unaryClosures, 
      EdgeSpace stateSpace, Index<String> tagIndex){
    
    /* do backward combination Set[] */
    if(verbose > 0){
      System.err.println("\n# Backward combination ...");
      Timing.startTime();
    }
    
    Map<Integer, Set<Completion>> tag2completionsMap = new HashMap<Integer, Set<Completion>>(); 

    for(Integer activeState : stateSpace.getActiveEdges()){ // go through active states, X -> Z \alpha      
      int viaState = stateSpace.via(activeState);
      int viaCategoryIndex = stateSpace.get(viaState).getMother(); // Z
      assert(viaState>=0);
      assert(stateSpace.get(viaState).numChildren()==0);
      
//      if(verbose >= 2){
//        System.err.println("Active state " + activeState + ": " + stateSpace.get(activeState)
//            + ", via state " + viaState + ": " + stateSpace.get(viaState));
//      }
      
      // get passive states
      if(unaryClosures.containsRow(viaCategoryIndex)){ // non-zero rows in closure matrix, there exists some Y that R(Z->Y) is non-zero 
        // go through passive state
        for(Integer passiveState : stateSpace.getPassiveEdges()){ // go through passive states, Y -> []
          int passiveCategoryIndex = stateSpace.get(passiveState).getMother(); 
          
          double unaryClosureScore = Double.NEGATIVE_INFINITY;
          if (passiveCategoryIndex >= 0){
            unaryClosureScore = unaryClosures.get(viaCategoryIndex, passiveCategoryIndex); // R(Z -> Y)
          }
          
          if (unaryClosureScore != Double.NEGATIVE_INFINITY) {
            if (!tag2completionsMap.containsKey(passiveState)) {
              tag2completionsMap.put(passiveState, new HashSet<Completion>());
              //backPassiveStateIndexer.add(passiveState);
            }
            tag2completionsMap.get(passiveState).add(new Completion(activeState, stateSpace.to(activeState), unaryClosureScore));
          }
        }
      } else { // for zero row, there is only passive state, which is the via state Z -> []
        int passiveState = viaState; 

        if (!tag2completionsMap.containsKey(passiveState)) {
          tag2completionsMap.put(passiveState, new HashSet<Completion>());
          //backPassiveStateIndexer.add(passiveState);
        }
      
        tag2completionsMap.get(passiveState).add(new Completion(activeState, stateSpace.to(activeState), 0.0));
      }
    }
    

    Completion[][] backwardCombinations = new Completion[stateSpace.size()][];
    for (int passiveState = 0; passiveState < stateSpace.size(); passiveState++) {
      if(tag2completionsMap.containsKey(passiveState)){
        List<Completion> l = new ArrayList<Completion>(tag2completionsMap.get(passiveState));
        backwardCombinations[passiveState] = (Completion[]) l.toArray(noBackwardCombinations);
        if(verbose >= 3){
          System.err.println(stateSpace.get(passiveState).toString(tagIndex, tagIndex)
              + "\t" + Utility.sprint(backwardCombinations[passiveState], stateSpace, tagIndex));
        }
      } else {
        backwardCombinations[passiveState] = noBackwardCombinations;
      }
      
    }
    
    if (verbose >= 1) {
      Timing.tick("Done with backward combination");
    }
    
    return backwardCombinations;
  }
  
  public boolean equals(Object o) {
    if (this == o) {
      return true;
    }
    if (!(o instanceof Completion)) {
      return false;
    }

    final Completion backwardCombination = (Completion) o;

    if (activeState != backwardCombination.activeState) {
      return false;
    }

    return true;
  }

  public int hashCode() {
    return (int)score<<16 + activeState;
  }

  public String toString(EdgeSpace stateSpace, Index<String> tagIndex) {
    //return activeChild + "<-" + score + "->" + result;
    return "(" + stateSpace.get(activeState).toString(tagIndex, tagIndex) 
    + ", " + stateSpace.get(completedState).toString(tagIndex, tagIndex) + ", " + score + ")";
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