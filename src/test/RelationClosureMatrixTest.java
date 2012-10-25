package test;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;

import cern.colt.matrix.DoubleMatrix2D;

import parser.LogProbOperator;
import parser.Operator;
import parser.Rule;
import parser.RuleFile;
import recursion.ClosureMatrix;
import recursion.RelationMatrix;
import utility.Utility;
import edu.stanford.nlp.parser.lexparser.IntTaggedWord;
import edu.stanford.nlp.stats.Counter;
import edu.stanford.nlp.util.HashIndex;
import edu.stanford.nlp.util.Index;
import junit.framework.TestCase;


public class RelationClosureMatrixTest extends TestCase{
  private Operator operator = new LogProbOperator();
  
  String ruleString = 
  "ROOT->[A] : 1.0\n" + 
  "A->[A B] : 0.1\n" +
  "A->[B C] : 0.1\n" +
  "A->[_d _e] : 0.8\n" +
  "B->[C] : 0.2\n" +
  "B->[D E] : 0.8\n" +
  "C->[B] : 0.3\n" +
  "C->[D] : 0.7\n" +
  "D->[_d] : 1.0\n" +
  "E->[_e] : 1.0\n";
  
  public void testBasic(){
    Index<String> wordIndex = new HashIndex<String>();
    Index<String> tagIndex = new HashIndex<String>();
    
    Collection<Rule> rules = new ArrayList<Rule>();
    Collection<Rule> extendedRules = new ArrayList<Rule>();
    
    Map<Integer, Counter<Integer>> tag2wordsMap = new HashMap<Integer, Counter<Integer>>();
    Map<Integer, Set<IntTaggedWord>> word2tagsMap = new HashMap<Integer, Set<IntTaggedWord>>();
    Map<Integer, Integer> nonterminalMap = new HashMap<Integer, Integer>();
    
    
    
    try {
      RuleFile.parseRuleFile(Utility.getBufferedReaderFromString(ruleString), 
          rules, extendedRules, tag2wordsMap, word2tagsMap, 
          nonterminalMap, wordIndex, tagIndex);
    } catch (IOException e){
      System.err.println("Error reading rules: " + ruleString);
      e.printStackTrace();
    }
    
    // create relation matrix
    RelationMatrix relationMatrix = new RelationMatrix(tagIndex);
    RelationMatrix.verbose = 3;
    System.err.println(ruleString);
    assertEquals(tagIndex.toString(), "[0=ROOT,1=A,2=B,3=C,4=D,5=E]");
    
    /* do left-corner closures matrix */
    DoubleMatrix2D pl = relationMatrix.getPL(rules, nonterminalMap);
    assertEquals(Utility.sprint(pl), "0.0 1.0 0.0 0.0\n0.0 0.1 0.1 0.0\n0.0 0.0 0.0 0.2\n0.0 0.0 0.3 0.0");
    ClosureMatrix leftCornerClosures = new ClosureMatrix(pl, operator);
    leftCornerClosures.changeIndices(nonterminalMap);
    assertEquals(Utility.sprint(leftCornerClosures.getClosureMatrix()), "0.0 0.10536051565782635 -2.135349173618132 -3.744787086052232\n-Infinity 0.10536051565782635 -2.135349173618132 -3.744787086052232\n-Infinity -Infinity 0.06187540371808745 -1.547562508716013\n-Infinity -Infinity -1.1420974006078486 0.06187540371808745");
    
    // Matlab code
    // a = [0 1 0 0 0 0; 0 0.1 0.1 0 0 0; 0 0 0 0.2 0 0; 0 0 0.3 0 0 0; 0 0 0 0 0 0; 0 0 0 0 0 0];
    // log((eye(6)-a)^(-1))
    
    /* do unary closure matrix */
    DoubleMatrix2D pu = relationMatrix.getPU(rules); //, nontermPretermIndexer);
    assertEquals(Utility.sprint(pu), "0.0 1.0 0.0 0.0 0.0 0.0\n0.0 0.0 0.0 0.0 0.0 0.0\n0.0 0.0 0.0 0.2 0.0 0.0\n0.0 0.0 0.3 0.0 0.7 0.0\n0.0 0.0 0.0 0.0 0.0 0.0\n0.0 0.0 0.0 0.0 0.0 0.0");
    ClosureMatrix unaryClosures = new ClosureMatrix(pu, operator);
    assertEquals(Utility.sprint(unaryClosures.getClosureMatrix()), "0.0 0.0 -Infinity -Infinity -Infinity -Infinity\n-Infinity -Infinity 0.06187540371808745 -1.547562508716013 -1.9042374526547454 -Infinity\n-Infinity -Infinity -1.1420974006078486 0.06187540371808745 -0.294799540220645 -Infinity");
    System.err.println(Utility.sprint(unaryClosures.getClosureMatrix()));
    // Matlab code
    // a = [0 1 0 0 0 0; 0 0 0 0 0 0; 0 0 0 0.2 0 0; 0 0 0.3 0 0.7 0; 0 0 0 0 0 0; 0 0 0 0 0 0];
    // log((eye(6)-a)^(-1))
    int aIndex = tagIndex.indexOf("A");
    for (int i = 0; i < tagIndex.size(); i++) {
      double aScore = unaryClosures.get(aIndex, i);
      if (i==aIndex){
        assertEquals(aScore, 0.0);
      } else {
        assertEquals(aScore, Double.NEGATIVE_INFINITY);
      }
    }
  }
}
