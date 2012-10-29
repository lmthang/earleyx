package base;

import java.util.Collection;
import java.util.Map;


import util.Util;

import cern.colt.matrix.DoubleMatrix2D;
import cern.colt.matrix.impl.RCDoubleMatrix2D;
import edu.stanford.nlp.util.Index;
import edu.stanford.nlp.util.Timing;

/**
 * Construct left-corner and unit-production relation matrices
 * 
 * @author Minh-Thang Luong, 2012
 *
 */
public class RelationMatrix {
  public static int verbose = 0;
  private Index<String> tagIndex;
  
  public RelationMatrix(Index<String> tagIndex){
    this.tagIndex = tagIndex;
  }
  
  /**
   * Construct left-corner relation matrix
   * @param rules -- scores should be >= 0
   * @param categories
   * @return pl (sparse matrix)
   */
  public DoubleMatrix2D getPL(Collection<Rule> rules, Map<Integer, Integer> nonterminalMap) {
    int numRows = nonterminalMap.size(); //tagIndex.size(); // categories.size();
    DoubleMatrix2D pl = new RCDoubleMatrix2D(numRows, numRows);
    
    if(verbose >= 1){
      System.err.println("\n# Construct left-corner relation matrix " + numRows + " x " + numRows);
      Timing.startTime();
      if(verbose>=2){
        System.err.println(Util.sprint(tagIndex, Util.getNonterminals(nonterminalMap)));
      }
    }
    
    int numRules = 0;
    for (Rule r:rules) {
      int firstChild = r.getChild(0); //categories.indexOf(stateSpace.indexOf(r.getChildEdge(0)));
      if (nonterminalMap.containsKey(firstChild)) { // if the first child is a non-terminal
        assert(nonterminalMap.containsKey(r.getMother()));
        assert(r.getScore()>=0);
        
        numRules++;
        int mother = r.getMother(); //categories.indexOf(stateSpace.indexOf(r.getMotherEdge()));
        
        // change indices
        int newFirstChild = nonterminalMap.get(firstChild);
        int newMother = nonterminalMap.get(mother);
        //pl.set(mother, firstChild, pl.get(mother, firstChild) + r.getScore()); // note of + sign here
        pl.set(newMother, newFirstChild, pl.get(newMother, newFirstChild) + r.getScore()); // note of + sign here
        
        if(verbose >= 1){
          if(numRules % 10000 == 0){
            System.err.print(" (" + numRules + ") ");
          }
        }
        if(verbose >= 3){
          System.err.println("Rule: " + r.toString(tagIndex, tagIndex) + ", score " + 
              tagIndex.get(mother) + " -> " + tagIndex.get(firstChild) 
              //+ " " + pl.get(mother, firstChild));
              + " " + pl.get(newMother, newFirstChild));
        }
      }
    }
    
    if (verbose >= 1) {
      Timing.endTime("Done! Num rules processed =" + numRules);
    }
    if(verbose >= 3){
      System.err.println(pl + "\n");
    }
    
    return pl;
  }
  
  /**
   * Construct unit-production relation matrix
   * @param rules
   * @param allNontermCategories
   * @return pu (sparse matrix)
   */
  public DoubleMatrix2D getPU(Collection<Rule> rules){ //, Index<Integer> allNontermCategories){
    int numRows = tagIndex.size(); //allNontermCategories.size();
    DoubleMatrix2D pu = new RCDoubleMatrix2D(numRows, numRows);
    int numUnaryRules = 0;
    
    if(verbose >= 1){
      System.err.println("\n# Construct unit-production relation matrix " + numRows + " x " + numRows);
      Timing.startTime();
      if(verbose>=2){
        System.err.println(Util.sprint(tagIndex));
      }
    }
    
    for (Rule r:rules) {
      if (r.isUnary()) {
        numUnaryRules++;
        assert(r.getScore()>=0);
        int mother = r.getMother(); //allNontermCategories.indexOf(stateSpace.indexOf(r.getMotherEdge()));
        int onlyChild = r.getChild(0); //allNontermCategories.indexOf(stateSpace.indexOf(r.getChildEdge(0)));
        pu.set(mother, onlyChild, pu.get(mother, onlyChild) + r.getScore()); // note of + sign here
        
        if(verbose >= 1){
          if(numUnaryRules % 100 == 0){
            System.err.print(" (" + numUnaryRules + ") ");
          }
        }
        if(verbose >= 3){
          System.err.println("Rule: " + r.toString(tagIndex, tagIndex) + ", score " + 
              tagIndex.get(mother) + " -> " + tagIndex.get(onlyChild) 
              + " " + pu.get(mother, onlyChild));
        }
      }
    }
    
    if (verbose >= 1) {
      Timing.endTime("Done! Num unary rules=" + numUnaryRules);
    }
    if(verbose >= 3){
      System.err.println(pu + "\n");
    }
    
    return pu;
  }
}

/** Unused code **/
//private String getCategoryToString(Index<Integer> categories){
//  StringBuffer sb = new StringBuffer();
//  for (int i = 0; i < categories.size(); i++) {
//    int state = categories.get(i);
//    sb.append(i + ":" + stateSpace.get(state) + ", ");
//  }
//  sb.delete(sb.length()-2, sb.length());
//  
//  return sb.toString();
//}