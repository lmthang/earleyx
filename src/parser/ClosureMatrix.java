package parser;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import cern.colt.function.DoubleFunction;
import cern.colt.matrix.DoubleMatrix2D;
import cern.colt.matrix.impl.DenseDoubleMatrix2D;
import cern.colt.matrix.impl.RCDoubleMatrix2D;
import cern.colt.matrix.linalg.Algebra;
import edu.stanford.nlp.util.Timing;

/**
 * Compute reflective, transitive matrices for both left-corner and unit-production relations
 * 
 * Wrap around DoubleMatrix2D to handle large closure matrix
 * without storing all elements
 * 
 * Row indices are compressed since only a small subset of rows are non-zero in the closure matrix
 * 
 * @author lmthang
 *
 */
public class ClosureMatrix {
  public static int verbose = 0;

  /* for matrix operation */
  private static Algebra alg = new Algebra();
  private Map<Integer, Integer> rowIndexMap; // keep track of non-zero rows in pu matrix, non-zero row index -> linear id
  private DoubleMatrix2D closureMatrix;
  
  public ClosureMatrix(DoubleMatrix2D relationMatrix) {
    rowIndexMap = new HashMap<Integer, Integer>();
    
    if (verbose >= 1) {
      System.err.println("\n# Building closure matrix...");
      Timing.startTime();
    }
    
    computeClosureMatrix(relationMatrix);
    
    if (verbose >= 1) {
      Timing.tick("Done with closure matrix.");
    }   
    
    if(verbose >= 3){
      System.err.println("# closure matrix\n" + this);
    }
 
  }

  public Set<Integer> getNonZeroRowIndices(){
    return rowIndexMap.keySet();
  }
  
  public boolean containsRow(int rowIndex){
    return rowIndexMap.containsKey(rowIndex);
  }
  
  public double get(int rowIndex, int colIndex){
    if(rowIndexMap.containsKey(rowIndex)){
      int compressedRowIndex = rowIndexMap.get(rowIndex);
      return closureMatrix.get(compressedRowIndex, colIndex);
    } else if(rowIndex == colIndex){ // return log(1) since we add I
      return 0;
    } else { // return log(0)
      return Double.NEGATIVE_INFINITY;
    }
  }
  
  /**
   * Compute closure matrix
   * @param relationMatrix (contains +score)
   * @param rowIndexMap map from non-zero indices to its new index, for later reconstruction
   * @return closureMatrix (sparse matrix)
   */
  private void computeClosureMatrix(DoubleMatrix2D relationMatrix){
    // add identity closure matrix
    int numRows = relationMatrix.rows();    
    
    // find indices where rowSum > 0
    List<Integer> nonzeroIndices = new ArrayList<Integer>();
    int numIndices = 0;
    for (int i = 0; i < numRows; i++) {
      double total = relationMatrix.viewRow(i).zSum();

      if(total != 0){ // since relation matrix contains +score
        nonzeroIndices.add(i);
        rowIndexMap.put(i, numIndices);
        numIndices++;
      }
    }
    if (verbose >= 1) {
      System.err.println("Num rows=" + numRows + ", num non-zero rows=" + numIndices);
    }
    
    // construct submatrix of indices with non-zero rows
    DoubleMatrix2D subRelationMatrix = new DenseDoubleMatrix2D(numIndices, numIndices); // this is a dense matrix
    for (int i = 0; i < numIndices; i++) {
      int origRowIndex = nonzeroIndices.get(i);
      for (int j = 0; j < numIndices; j++) {
        int origColIndex = nonzeroIndices.get(j);
        subRelationMatrix.set(i, j, -relationMatrix.get(origRowIndex, origColIndex)); // - P'
      }
      
      subRelationMatrix.set(i, i, 1.0 + subRelationMatrix.get(i, i)); // + I', note: subRelationMatrix.get
    }
    
    // inverse submatrix
    if (verbose >= 1) {
      System.err.print("Inverting submatrix ... ");
    }
    if(verbose >= 3){
      System.err.println(subRelationMatrix);
    }
    DoubleMatrix2D invSubMatrix = alg.inverse(subRelationMatrix);
    if (verbose >= 1) {
      System.err.print("Done!\nComputing closure matrix ... ");
    }
    
    /** compute closure matrix **/
    closureMatrix = new RCDoubleMatrix2D(numIndices, numRows); // sparse matrix, compress row indices
    
    // Compute zeroAugInvMatrix * relationMatrix    
    int rowCount = 0;
    for (Integer rowId : rowIndexMap.keySet()) { // only need to compute for non-zero row
      int invSubMatrixRowIndex = rowIndexMap.get(rowId);
      
      // compute value for the closureMatrix(rowId, colId) = sum_z zeroAugInvMatrix(rowId, z)*relationMatrix(z, colId)
      for (int colId = 0; colId < numRows; colId++) {
        double value = 0.0;
        
        for(Integer z : rowIndexMap.keySet()){ // only consider non-zero value of zeroAugInvMatrix(rowId, z)
          int invSubMatrixColIndex = rowIndexMap.get(z);
          value += invSubMatrix.get(invSubMatrixRowIndex, invSubMatrixColIndex)*relationMatrix.get(z, colId);
        }

        if(colId == rowId){ // add I
          value += 1.0;
        }
        
        double logValue = Math.log(value);
        closureMatrix.set(invSubMatrixRowIndex, colId, logValue); // Important: here we use invSubMatrixRowIndex instead of rowId
      }
      
      
      rowCount++;
      
      if(rowCount % 10 == 0){
        System.err.print(" (" + rowCount + ") ");
      }
    } // end for rowId

    System.err.println("Done! Num non-zero rows=" + rowCount);
    
  }
   
  private static final DoubleFunction takeExp = new DoubleFunction() {
    public double apply(double x) {
      return Math.exp(x);
    }
  };
  
  @Override
  public String toString() {
    StringBuffer sb = new StringBuffer(rowIndexMap.toString());
    //sb.append("\n" + closureMatrix.toString());
    sb.append("\n" + closureMatrix.copy().assign(takeExp).toString());
    return sb.toString();
  }
 
  /**** Unused code ****/
  
//private static final DoubleFunction takeLog = new DoubleFunction() {
//public double apply(double x) {
//  return Math.log(x);
//}
//};
//
///**
// * Compute closure matrix (old method)
// * @param relationMatrix (contains +score)
// * @return
// */
//private void computeClosureMatrixOld(DoubleMatrix2D relationMatrix){
//  // add identity closure matrix
//  int numRows = relationMatrix.rows();
//  for (int i = 0; i < numRows; i++) {
//    indexMap.put(i, i); // trivial index map
//    
//    for (int j = 0; j < numRows; j++) {
//      relationMatrix.set(i, j, -relationMatrix.get(i, j)); // -P
//    }
//    
//    relationMatrix.set(i, i, 1.0 + relationMatrix.get(i, i)); // + I
//  }
//  
//  closureMatrix = alg.inverse(relationMatrix);
//
//  // set log
//  closureMatrix.assign(takeLog);
//}

}
