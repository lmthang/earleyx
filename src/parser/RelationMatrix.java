package parser;

import java.util.Collection;

import cern.colt.matrix.DoubleMatrix2D;
import cern.colt.matrix.impl.RCDoubleMatrix2D;
import edu.stanford.nlp.util.Index;
import edu.stanford.nlp.util.Timing;

/**
 * Construct left-corner and unit-production relation matrices
 * 
 * @author lmthang
 *
 */
public class RelationMatrix {
  public static int verbose = 0;
  
  private StateSpace stateSpace;
  
  public RelationMatrix(StateSpace stateSpace){
    this.stateSpace = stateSpace;
  }
  
  private String getCategoryToString(Index<Integer> categories){
    StringBuffer sb = new StringBuffer();
    for (int i = 0; i < categories.size(); i++) {
      int state = categories.get(i);
      sb.append(i + ":" + stateSpace.get(state) + ", ");
    }
    sb.delete(sb.length()-2, sb.length());
    
    return sb.toString();
  }
  
  /**
   * Construct left-corner relation matrix
   * @param rules
   * @param categories
   * @return pl (sparse matrix)
   */
  public DoubleMatrix2D getPL(Collection<Rule> rules, Index<Integer> categories){
    int numRows = categories.size();
    DoubleMatrix2D pl = new RCDoubleMatrix2D(numRows, numRows);
    
    if(verbose >= 1){
      System.err.println("\n# Construct left-corner relation matrix");
      Timing.startTime();
      System.err.println("Categories: " + getCategoryToString(categories));
    }
    
    int numRules = 0;
    for (Rule r:rules) {
      int firstDtrIndex = categories.indexOf(stateSpace.indexOf(r.getChildEdge(0)));
      if (firstDtrIndex >= 0) { // if the first child is a non-terminal
        numRules++;
        int motherIndex = categories.indexOf(stateSpace.indexOf(r.getMotherEdge()));
        pl.set(motherIndex, firstDtrIndex, pl.get(motherIndex, firstDtrIndex) + r.score); // note of + sign here
        
        if(verbose >= 1){
          if(numRules % 10000 == 0){
            System.err.print(" (" + numRules + ") ");
          }
        }
        if(verbose >= 3){
          System.err.println("Rule: " + r + ", update score for mother=" + 
              r.getMotherEdge() + ", child=" + r.getChildEdge(0));
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
  public DoubleMatrix2D getPU(Collection<Rule> rules, Index<Integer> allNontermCategories){
    int numRows = allNontermCategories.size();
    DoubleMatrix2D pu = new RCDoubleMatrix2D(numRows, numRows);
    int numUnaryRules = 0;
    
    if(verbose >= 1){
      System.err.println("\n# Construct unit-production relation matrix");
      Timing.startTime();
      System.err.println("Categories: " + getCategoryToString(allNontermCategories));
    }
    
    for (Rule r:rules) {
      if (r.isUnary()) {
        numUnaryRules++;
        int motherIndex = allNontermCategories.indexOf(stateSpace.indexOf(r.getMotherEdge()));
        int onlyDtrIndex = allNontermCategories.indexOf(stateSpace.indexOf(r.getChildEdge(0)));
        pu.set(motherIndex, onlyDtrIndex, pu.get(motherIndex, onlyDtrIndex) + r.score); // note of + sign here
        
        if(verbose >= 1){
          if(numUnaryRules % 100 == 0){
            System.err.print(" (" + numUnaryRules + ") ");
          }
        }
        if(verbose >= 3){
          System.err.println("Rule: " + r);
          System.err.println(", update score for mother=" + 
              r.getMotherEdge() + ", child=" + r.getChildEdge(0));
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
