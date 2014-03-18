package base;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import util.LogProbOperator;
import util.Operator;

import cern.colt.function.DoubleFunction;
import cern.colt.matrix.DoubleMatrix2D;
import cern.colt.matrix.impl.DenseDoubleMatrix2D;
import cern.colt.matrix.impl.RCDoubleMatrix2D;
import cern.colt.matrix.linalg.Algebra;
import edu.stanford.nlp.util.Index;
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
  private Map<Integer, Integer> colIndexMap; // map from indices to real matrix column indices
  // to answer the query given a non-termial Y, what are the non-terminals Z 
  // that have non-zero scores from Z->Y
  // col2rowMap.get(colIndex).get(rowIndex) gives closure score of Z (rowIndex) -> Y (colIndex)
  private Map<Integer, Map<Integer, Double>> col2rowMap; 
  private DoubleMatrix2D closureMatrix;
  private Operator operator;
  private Index<String> tagIndex;
  private String name;
  public ClosureMatrix(DoubleMatrix2D relationMatrix, Operator operator, Index<String> tagIndex, String name) {
    this.operator = operator;
    this.tagIndex = tagIndex;
    this.name = name;
    
    rowIndexMap = new HashMap<Integer, Integer>();
    colIndexMap = new HashMap<Integer, Integer>();
    for (int i = 0; i < relationMatrix.columns(); i++) {
      colIndexMap.put(i, i); // identity, will be non-trivial when changeIndices() is called
    }
    col2rowMap = new HashMap<Integer, Map<Integer,Double>>();
    
    if (verbose >= 1) {
      System.err.println("\n# Building " + name + " closure matrix...");
      Timing.startTime();
    }
    
    computeClosureMatrix(relationMatrix);
    
    if(verbose >= 3){
      System.err.println(this);
    }
 

    if (verbose >= 1) {
      Timing.tick("Done=!");
    }  
  }

  /**
   * if indexMap(i) = j, after changing indices, we could use get(i, i)
   * to refer to the value returned by get(j, j) previously 
   * @param indexMap
   */
  public void changeIndices(Map<Integer, Integer> indexMap){
    Map<Integer, Integer> newRowIndexMap = new HashMap<Integer, Integer>();
    Map<Integer, Integer> newColIndexMap = new HashMap<Integer, Integer>();
    Map<Integer, Map<Integer, Double>> newCol2rowMap = new HashMap<Integer, Map<Integer, Double>>();
    
    Map<Integer, Integer> reverseIndexMap = new HashMap<Integer, Integer>();
    for(int newIndex : indexMap.keySet()){ // indexMap: newIndex -> oldIndex
      int oldIndex = indexMap.get(newIndex);
      
      // row/colIndexMap: oldIndex -> real row/col index
      if(rowIndexMap.containsKey(oldIndex)){
        newRowIndexMap.put(newIndex, rowIndexMap.get(oldIndex));
      }
      if(colIndexMap.containsKey(oldIndex)){
        newColIndexMap.put(newIndex, colIndexMap.get(oldIndex));
      }
      
      reverseIndexMap.put(oldIndex, newIndex);
    }
    rowIndexMap = newRowIndexMap;
    colIndexMap = newColIndexMap;
    
    // update col2rowMap
    for(int oldColId : col2rowMap.keySet()){
      int newColId = reverseIndexMap.get(oldColId);
      if(!newCol2rowMap.containsKey(newColId)){
        newCol2rowMap.put(newColId, new HashMap<Integer, Double>());
      }
      
      Map<Integer, Double> valueMap = col2rowMap.get(oldColId);
      for(int rowId : valueMap.keySet()){
        int newRowId = reverseIndexMap.get(rowId);
        newCol2rowMap.get(newColId).put(newRowId, valueMap.get(rowId));
      }
    }
    col2rowMap = newCol2rowMap;
    
    if(verbose>=2){
      System.err.println("new row map: " + newRowIndexMap);
      System.err.println("new col map: " + newColIndexMap);
      System.err.println("new col2row map: " + newCol2rowMap);
    }
  }
  
  public boolean containsRow(int rowIndex){
    return rowIndexMap.containsKey(rowIndex);
  }
  
  public double get(int rowIndex, int colIndex){
    if(!colIndexMap.containsKey(colIndex)){ // no such column
      return operator.zero();
    }
    
    if(rowIndexMap.containsKey(rowIndex)){
      int compressedRowIndex = rowIndexMap.get(rowIndex);
      int compressedColIndex = colIndexMap.get(colIndex);
      return closureMatrix.get(compressedRowIndex, compressedColIndex);
    } else if(rowIndex == colIndex){
      return operator.one();
    } else {
      return operator.zero();
    }
  }
  
  public Map<Integer, Double> getParentClosures(int colIndex){
    if(col2rowMap.containsKey(colIndex)){
      return col2rowMap.get(colIndex);
    } else {
      return new HashMap<Integer, Double>();
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
      
      // initialize col2rowMap, add P(Y->Y) = 1.0
      col2rowMap.put(i, new HashMap<Integer, Double>());
      col2rowMap.get(i).put(i, operator.one());
    }
    if (verbose >= 1) {
      System.err.println("  num rows=" + numRows + ", num non-zero rows=" + numIndices);
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
    if(verbose >= 3){
    	System.err.print("  inverted submatrix: ");
      System.err.println(subRelationMatrix);
    }
    DoubleMatrix2D invSubMatrix = alg.inverse(subRelationMatrix);
    if (verbose >= 3) {
      System.err.print("  inverted matrix: ");
      System.err.println(invSubMatrix);
      System.err.println("  computing full matrix ... ");
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

        if(value<0 && value >-1E-10){ // too small and close to 0.0, make it 0.0
          System.err.println("! Change value closure matrix (" + rowId + ", " + colId + ") "
              + "from " + value + " to 0.0");
          value = 0.0;
        }
        if(value<0){
          System.err.println("! negative value closure matrix (" + rowId + ", " + colId + ") " + value);
          System.exit(1);
        }
        
        value = operator.getScore(value);
        closureMatrix.set(invSubMatrixRowIndex, colId, value); // Important: here we use invSubMatrixRowIndex instead of rowId
        
        if(value>operator.zero()){
          if(!col2rowMap.containsKey(colId)){
            col2rowMap.put(colId, new HashMap<Integer, Double>());
          }
          
          col2rowMap.get(colId).put(rowId, value); // here we use rowId, so we need to update col2rowMap if changeIndices() is called
        }
      }
      
      
      rowCount++;
      
      if(verbose>=1 && rowCount % 10 == 0){
        System.err.print(" (" + rowCount + ") ");
      }
    } // end for rowId
  }

  public DoubleMatrix2D getClosureMatrix() {
    return closureMatrix;
  }
  
  private static final DoubleFunction takeExp = new DoubleFunction() {
    public double apply(double x) {
      return Math.exp(x);
    }
  };
  
  @Override
  public String toString() {
    StringBuffer sb = new StringBuffer("# Closure matrix " + name + "\n");
    sb.append("  rowIndexMap: " + rowIndexMap.toString() + "\n");
    sb.append("  colIndexMap: " + colIndexMap.toString() + "\n");
    sb.append("  col2rowMap:\n" + sprintCol2rowMap(tagIndex));
    
    if(operator instanceof LogProbOperator){
      sb.append("\n" + closureMatrix.copy().assign(takeExp).toString());
    } else {
      sb.append("\n" + closureMatrix.toString());
    }
    return sb.toString();
  }
 
  public String sprintCol2rowMap(Index<String> tagIndex){
    StringBuffer sb = new StringBuffer();
    for(int colId : col2rowMap.keySet()){
      Map<Integer, Double> valueMap = col2rowMap.get(colId);
      
      sb.append("  \"" + tagIndex.get(colId) + "\" (" + colId + ")\t:");
      for(int rowId : valueMap.keySet()){
        sb.append(" \"" + tagIndex.get(rowId) + "\" (" + rowId + ")=" + operator.getProb(valueMap.get(rowId)));
      }
      sb.append("\n");
    }
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
