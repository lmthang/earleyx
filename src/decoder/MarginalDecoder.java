/**
 * 
 */
package decoder;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import edu.stanford.nlp.ling.Tag;
import edu.stanford.nlp.ling.Word;
import edu.stanford.nlp.trees.LabeledScoredTreeNode;
import edu.stanford.nlp.trees.Tree;

import parser.EarleyParser;

import base.Edge;

/**
 * @author Minh-Thang Luong, 2012
 *
 */
public class MarginalDecoder extends Decoder {
  public MarginalDecoder(EarleyParser parser, int verbose) {
    super(parser, verbose);
    // TODO Auto-generated constructor stub
  }

  public MarginalDecoder(EarleyParser parser) {
    super(parser);
    // TODO Auto-generated constructor stub
  }

  public Map<Integer, Double> computeMarginalMap(int left, int right){
    // edges with both inside and outside scores over [left, right]
    Set<Integer> edges = parser.listInsideEdges(left, right);
    edges.retainAll(parser.listOutsideEdges(left, right));
    
    Map<Integer, Double> marginalMap = new HashMap<Integer, Double>();
    for (int edge : edges) { // edge
      Edge edgeObj = edgeSpace.get(edge);
      if(edgeObj.numRemainingChildren()==0){ // completed edge
        int tag = edgeObj.getMother();

        // numerator of expected count
        double score = operator.multiply(parser.getOuterScore(left, right, edge), 
            parser.getInnerScore(left, right, edge)); 
        assert(score > operator.zero());
        
        if(!marginalMap.containsKey(tag)){ // new tag
          marginalMap.put(tag, score);
        } else { // old tag
          marginalMap.put(tag, operator.add(marginalMap.get(tag), score));
        }
      }
    }

    return marginalMap;
  }
  
  public int argmax(Map<Integer, Double> marginalMap, String prefixFilter){
    assert(marginalMap.size()>0);
    
    int bestTag = -1;
    double bestScore = operator.zero();
    for(int tag : marginalMap.keySet()){
//      System.err.println(parserTagIndex.get(tag) + "\t" + marginalMap.get(tag));
      assert(parserTagIndex.get(tag).startsWith(prefixFilter) || parserTagIndex.get(tag).equals(""));
      
      if(marginalMap.get(tag) > bestScore){
        bestTag = tag;
        bestScore = marginalMap.get(tag);
      }
    }
    
    return bestTag;
  }
  
  public List<String> socialMarginalDecoding(){
    this.words = parser.getWords();
    numWords = words.size();
    
    // mark left and right boundary of a sentence
    // for example if the first sentence has 5 words, then sentLeft = 0, sentRight = 5
    int sentLeft = 0;
    int sentRight = 0;
    
    // .dog kid.eyes mom.eyes # .pig kid.hands # ## and whats that is this a puppy dog
    int doubleHashPos = -1; // if we have seen ## (doubleHashPos>0), that means we have passed through all social cues
    
    if(verbose>0){
      System.err.println("# social marginal decoding: num words = " + numWords);
    }
    
    List<String> results = new ArrayList<String>();
    for (int i = 0; i < numWords; i++) {
      String word = words.get(i).word();
      
      if(word.charAt(0) == '.' || word.equals("##")){ // start a social cue
        if(doubleHashPos>=0){ // this means we have finished processing a sentence
          String result = socialSentMarginalDecoding(sentLeft, sentRight, doubleHashPos);
          results.add(result);
          
          // reset
          sentLeft = sentRight;
          doubleHashPos = -1;
        }
      }

      if(word.equals("##")){ // start processing terminals
        doubleHashPos = i;
      }
      
      sentRight++;
    }
  
    assert(sentLeft<sentRight);

    // marginal decoding a sent
    String result = socialSentMarginalDecoding(sentLeft, sentRight, doubleHashPos);
    results.add(result);
    
    return results;
  }
  
  public String socialSentMarginalDecoding(int sentLeft, int sentRight, int doubleHashPos){
    StringBuffer sb = new StringBuffer();
    
    System.err.println(" [" + sentLeft + ", " + sentRight + "]: " + words.subList(sentLeft, sentRight));
    
    // sent tag
    Map<Integer, Double> sentMarginalMap = computeMarginalMap(sentLeft, sentRight);
    int sentTag = argmax(sentMarginalMap, "Sentence");
    assert(sentTag>=0);
    sb.append(parserTagIndex.get(sentTag));
    
    System.err.println(parserTagIndex.get(sentTag) + " [" + sentLeft + ", " + sentRight + "]: " + words.subList(sentLeft, sentRight));
    
    for(int tag : sentMarginalMap.keySet()){
      System.err.println(parserTagIndex.get(tag) + "\t" + sentMarginalMap.get(tag));
    }

    Tree bestParse = getBestParse(doubleHashPos+1, sentRight);
    sb.append(" " + bestParse.toString());
    // word tags
//    for(int i=doubleHashPos+1; i<sentRight; i++){
//      String word = words.get(i).word();
//      
//      Map<Integer, Double> wordMarginalMap = computeMarginalMap(i, i+1);
//      int wordTag = argmax(wordMarginalMap, "Word");
//      assert(wordTag>=0);
//      
//      sb.append(" (" + parserTagIndex.get(wordTag) + " " + word + ")");
//      
//      if(verbose>=0){
//        System.err.println(parserTagIndex.get(wordTag) + " [" + i + ", " + (i+1) + "] " + word);
//        
//        for(int tag : wordMarginalMap.keySet()){
//          System.err.println(parserTagIndex.get(tag) + "\t" + wordMarginalMap.get(tag));
//        }
//      }
//    }
    
    return sb.toString();
  }

  @Override
  public Tree getBestParse() {
    this.words = parser.getWords();
    numWords = words.size();
    return getBestParse(0, numWords);
  }

  private Tree getBestParse(int startIndex, int endIndex){
    assert(endIndex>startIndex);
    int numSpanWords = endIndex - startIndex;
    int[][] cellTags = new int[numSpanWords+1][numSpanWords+1];
    double[][] cellScores = new double[numSpanWords+1][numSpanWords+1];
    Tree[][] cellTrees = new Tree[numSpanWords+1][numSpanWords+1];
    
    // init span 1
    for (int left = startIndex; left < endIndex; left++) {
      int right = left+1;
      Map<Integer, Double> marginalMap = computeMarginalMap(left, right);
      int bestTag = argmax(marginalMap, "");
      double bestScore = marginalMap.get(bestTag);
      List<Tree> daughterTreesList = new ArrayList<Tree>();
      daughterTreesList.add(new LabeledScoredTreeNode(new Word(words.get(left).word())));
      Tree bestParse = new LabeledScoredTreeNode(new Tag(parserTagIndex.get(bestTag)), daughterTreesList);
      
      cellTags[left-startIndex][right-startIndex] = bestTag;
      cellScores[left-startIndex][right-startIndex] = bestScore;
      cellTrees[left-startIndex][right-startIndex] = bestParse; 
      if(verbose>=3){
        System.err.println(left + "\t" + right + "\t" + parserTagIndex.get(bestTag)
            + "\t" + bestScore + "\t" + bestParse);
      }
    }
    
    for (int length = 2; length <= numSpanWords; length++) { // length
      for (int left = startIndex; left <= endIndex-length; left++) {
        int right = left + length;
        
        Map<Integer, Double> marginalMap = computeMarginalMap(left, right);
        if (marginalMap.size()==0){ // no tag covers this span
          continue;
        }
        int bestTag = argmax(marginalMap, "");
        double bestScore = marginalMap.get(bestTag);
        
        // find middle position
        double bestSplitScore = operator.zero();
        int bestSplit = -1;
        for (int middle = left+1; middle < right; middle++) {
          if(cellTrees[left-startIndex][middle-startIndex]==null 
              || cellTrees[middle-startIndex][right-startIndex]==null){
            continue;
          }
          
          double score = operator.add(cellScores[left-startIndex][middle-startIndex], 
              cellScores[middle-startIndex][right-startIndex]);
          if (score > bestScore){
            bestScore = score;
            bestSplit = middle;
          }
        }
        bestScore = operator.add(bestScore, bestSplitScore);
        
        // construct best parse
        List<Tree> daughterTreesList = new ArrayList<Tree>();
        daughterTreesList.add(cellTrees[left-startIndex][bestSplit-startIndex]);
        daughterTreesList.add(cellTrees[bestSplit-startIndex][right-startIndex]);
        Tree bestParse = new LabeledScoredTreeNode(new Tag(parserTagIndex.get(bestTag)), 
            daughterTreesList);
        
        // assign
        cellTags[left-startIndex][right-startIndex] = bestTag;
        cellScores[left-startIndex][right-startIndex] = bestScore;
        cellTrees[left-startIndex][right-startIndex] = bestParse; 
        if(verbose>=3){
          System.err.println(left + "\t" + right + "\t" + parserTagIndex.get(bestTag)
              + "\t" + bestScore + "\t" + bestParse);
        }
      }
    }
    
    assert(cellTrees[0][endIndex-startIndex]!=null);
    return cellTrees[0][endIndex-startIndex];

  }
}
