package test;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;

import base.ClosureMatrix;
import base.RelationMatrix;
import base.ProbRule;

import cern.colt.matrix.DoubleMatrix2D;

import parser.EdgeSpace;
import parser.LeftWildcardEdgeSpace;
import parser.Prediction;
import util.LogProbOperator;
import util.Operator;
import util.RuleFile;
import util.Util;
import edu.stanford.nlp.parser.lexparser.IntTaggedWord;
import edu.stanford.nlp.stats.Counter;
import edu.stanford.nlp.util.HashIndex;
import edu.stanford.nlp.util.Index;
import junit.framework.TestCase;


public class PredictionTest extends TestCase {
  private Operator operator = new LogProbOperator();
  
  String ruleString = 
    "ROOT->[A] : 1.0\n" + 
    "A->[A B] : 0.1\n" +
    "A->[B C] : 0.2\n" +
    "A->[A1] : 0.3\n" +
    "A->[_d _e] : 0.4\n" +
    "A1->[A2] : 1.0\n" +
    "B->[C] : 0.2\n" +
    "B->[D E] : 0.8\n" +
    "C->[B] : 0.3\n" +
    "C->[D] : 0.7\n" +
    "A2->[_a] : 1.0\n" +
    "D->[_d] : 1.0\n" +
    "E->[_e] : 1.0\n";
  
  public void testBasic(){
    System.err.println(ruleString);
    Index<String> wordIndex = new HashIndex<String>();
    Index<String> tagIndex = new HashIndex<String>();
    
    Collection<ProbRule> rules = new ArrayList<ProbRule>();
    Collection<ProbRule> extendedRules = new ArrayList<ProbRule>();
    
    Map<Integer, Counter<Integer>> tag2wordsMap = new HashMap<Integer, Counter<Integer>>();
    Map<Integer, Set<IntTaggedWord>> word2tagsMap = new HashMap<Integer, Set<IntTaggedWord>>();
    Map<Integer, Integer> nonterminalMap = new HashMap<Integer, Integer>();
    
    
    
    try {
      RuleFile.parseRuleFile(Util.getBufferedReaderFromString(ruleString), 
          rules, extendedRules, tag2wordsMap, word2tagsMap, 
          nonterminalMap, wordIndex, tagIndex);
    } catch (IOException e){
      System.err.println("Error reading rules: " + ruleString);
      e.printStackTrace();
    }

    // statespace
    EdgeSpace edgeSpace = new LeftWildcardEdgeSpace(tagIndex);
    edgeSpace.build(rules);
    
    // closure matrix
    RelationMatrix relationMatrix = new RelationMatrix(tagIndex);
    DoubleMatrix2D pl = relationMatrix.getPL(rules, nonterminalMap);
    ClosureMatrix leftCornerClosures = new ClosureMatrix(pl, operator);
    leftCornerClosures.changeIndices(nonterminalMap);
    
    Prediction[][] predictions = Prediction.constructPredictions(rules, leftCornerClosures, edgeSpace, tagIndex,
        Util.getNonterminals(nonterminalMap), operator);
    StringBuffer sb = new StringBuffer();
    for (int i = 0; i < predictions.length; i++) {
      sb.append(edgeSpace.get(i).toString(tagIndex, tagIndex) + ", " + Util.sprint(predictions[i], edgeSpace, tagIndex, operator) + "\n");
    }
    assertEquals(sb.toString(), "ROOT -> . A, ((A -> . A B,f=0.1111,i=0.1000), (A -> . B C,f=0.2222,i=0.2000), (B -> . D E,f=0.1891,i=0.8000))\nA -> ., ()\nROOT -> ., ()\nA -> . A B, ((A -> . A B,f=0.1111,i=0.1000), (A -> . B C,f=0.2222,i=0.2000), (B -> . D E,f=0.1891,i=0.8000))\nA -> . B, ((B -> . D E,f=0.8511,i=0.8000))\nB -> ., ()\nA -> . B C, ((B -> . D E,f=0.8511,i=0.8000))\nA -> . C, ((B -> . D E,f=0.2553,i=0.8000))\nC -> ., ()\nA -> . A1, ()\nA1 -> ., ()\nA1 -> . A2, ()\nA2 -> ., ()\nB -> . C, ((B -> . D E,f=0.2553,i=0.8000))\nB -> . D E, ()\nD -> ., ()\nB -> . E, ()\nE -> ., ()\nC -> . B, ((B -> . D E,f=0.8511,i=0.8000))\nC -> . D, ()\n");    
  }
}
