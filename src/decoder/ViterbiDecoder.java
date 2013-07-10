/**
 * 
 */
package decoder;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

import base.BackTrack;
import base.Edge;
import base.RuleSet;
import parser.EarleyParser;
import edu.stanford.nlp.ling.Label;
import edu.stanford.nlp.ling.Tag;
import edu.stanford.nlp.ling.Word;
import edu.stanford.nlp.trees.LabeledScoredTreeNode;
import edu.stanford.nlp.trees.Tree;

/**
 * @author Minh-Thang Luong, 2012
 *
 */
public class ViterbiDecoder extends Decoder {
  protected Map<Integer, Map<Integer, BackTrack>> backtrackChart;
  protected int goalEdge;
  protected boolean hasFragmentRule = false;
  protected RuleSet ruleSet;
  
  /**
   * @param parser
   */
  public ViterbiDecoder(EarleyParser parser) {
    super(parser);
    commonSetup();
  }

  /**
   * @param parser
   * @param verbose
   */
  public ViterbiDecoder(EarleyParser parser, int verbose) {
    super(parser, verbose);
    commonSetup();
  }

  private void commonSetup(){
    goalEdge = parser.getGoalEdge();
    backtrackChart = parser.getBacktrackChart();
    hasFragmentRule = parser.isHasFragmentRule();
    ruleSet = parser.getRuleSet();
  }

                            
  @Override
  public Tree getBestParse() {
    if(parser.hasParse()){
      return viterbiParse(0, numWords, goalEdge);
    } else {
      System.err.println("! No viterbi parse");
      return null;
    }
  }

  public Tree viterbiParse(int left, int right, int edge){
    if(verbose>=3){
      System.err.println("# Viterbi parse " + parser.edgeInfo(left, right, edge));
    }

    // X -> \alpha . \beta
    Edge edgeObj = edgeSpace.get(edge);
    Label motherLabel = new Tag(parserTagIndex.get(edgeObj.getMother()));
    
    Tree returnTree = null;
    if(edgeObj.getDot()==0 && edgeObj.numChildren()>0){ // X -> . \alpha
      returnTree = new LabeledScoredTreeNode(motherLabel);
    } else if(edgeObj.isTerminalEdge()){ // X -> _w1 ... _wn
      List<Tree> daughterTreesList = new ArrayList<Tree>();
      for (int i = left; i < right; i++) {
        daughterTreesList.add(new LabeledScoredTreeNode(new Word(words.get(i).word())));
      }
      
      returnTree = new LabeledScoredTreeNode(motherLabel, daughterTreesList);
    } else { // dot in the middle,  X -> \alpha Y . \beta
      assert(edge==goalEdge || edgeObj.numChildren()>1);
      // edge is [left, right]: X -> \alpha Y . \beta
      
//      Edge prevEdgeObj = edgeObj.getPrevEdge(); 
//      int prevEdge = edgeSpace.indexOf(prevEdgeObj);
//      BackTrack backtrack = backtrackChart.get(parser.linear(left, right)).get(edge);
//      returnTree = viterbiParse(left, backtrack.middle, prevEdge);
//      if(hasFragmentRule && backtrack.edge == -1) { // Z is in fact a terminal _z  due to fragment rules
//        assert(backtrack.middle == (right-1));
//        returnTree.addChild(new LabeledScoredTreeNode(new Word(words.get(backtrack.middle).word())));
      
      if(hasFragmentRule && !edgeObj.isTagBeforeDot(1) 
          && edgeObj.getChildBeforeDot(1)==wordIndices.get(right-1)) { // Y is in fact a terminal
        int prevRight = right;
        Edge prevEdgeObj = edgeObj; 
        // keep backtrack until no more matching terminals
        do {
          prevRight--;
          prevEdgeObj = prevEdgeObj.getPrevEdge();
        } while(prevRight>0 && prevEdgeObj.getDot()>0 && !prevEdgeObj.isTagBeforeDot(1) 
            && prevEdgeObj.getChildBeforeDot(1)==wordIndices.get(prevRight-1)); // matches terminal _y, prevEdgeObj is [left, prevRight]: X -> \alpha _y . \beta
        
        // after this, prevEdgeObj: [left, prevRight]: X -> \alpha . _y \beta
        returnTree = viterbiParse(left, prevRight, edgeSpace.indexOf(prevEdgeObj));
        for (int i = prevRight; i <= right-1; i++) {
          returnTree.addChild(new LabeledScoredTreeNode(new Word(words.get(i).word())));
        }
      } else { 
        // Viterbi parse: X -> \alpha . Z \beta
        Edge prevEdgeObj = edgeObj.getPrevEdge(); 
        int prevEdge = edgeSpace.indexOf(prevEdgeObj);
        BackTrack backtrack = backtrackChart.get(parser.linear(left, right)).get(edge);
        returnTree = viterbiParse(left, backtrack.middle, prevEdge);  
        
        // Viterbi parse: Y -> v .
        int nextEdge = backtrack.edge;
        Edge nextEdgeObj = edgeSpace.get(nextEdge);
        Tree nextTree = viterbiParse(backtrack.middle, right, nextEdge);
        
        if(prevEdgeObj.getChildAfterDot(0) != nextEdgeObj.getMother()){ // unary chain
          List<Integer> chain = ruleSet.getUnaryChain(prevEdgeObj.getChildAfterDot(0), 
              nextEdgeObj.getMother());
          for (int i = chain.size()-2; i >= 0; i--) {
            Label label = new Tag(parserTagIndex.get(chain.get(i)));
            nextTree = new LabeledScoredTreeNode(label, Arrays.asList(nextTree));
          }
        }
        
        // adjoin trees
        returnTree.addChild(nextTree);
      }
    }
    
    if(verbose>=3){
      System.err.println("[" + left + ", " + right + "] " + returnTree);
    }
    return returnTree;
  }
}
