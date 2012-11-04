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

import parser.Completion;
import parser.EdgeSpace;
import parser.LeftWildcardEdgeSpace;
import util.LogProbOperator;
import util.Operator;
import util.RuleFile;
import util.Util;
import cern.colt.matrix.DoubleMatrix2D;
import edu.stanford.nlp.parser.lexparser.IntTaggedWord;
import edu.stanford.nlp.stats.Counter;
import edu.stanford.nlp.util.HashIndex;
import edu.stanford.nlp.util.Index;
import junit.framework.TestCase;


public class CompletionTest extends TestCase{
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
    EdgeSpace.verbose = 0;
    ClosureMatrix.verbose = 0;
    Completion.verbose = 0;
    
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
    EdgeSpace stateSpace = new LeftWildcardEdgeSpace(tagIndex);
    stateSpace.build(rules);
    
    // closure matrix
    RelationMatrix relationMatrix = new RelationMatrix(tagIndex);
    DoubleMatrix2D pu = relationMatrix.getPU(rules);
    ClosureMatrix unaryClosures = new ClosureMatrix(pu, operator);
    
    Map<Integer, Completion[]>  tag2completionsMap = Completion.constructCompletions(
        unaryClosures, stateSpace, tagIndex, operator);
    StringBuffer sb = new StringBuffer();
    for(int iT : tag2completionsMap.keySet()){
      Completion[] completions = tag2completionsMap.get(iT);
      sb.append(tagIndex.get(iT) + ", " + 
          Util.sprint(completions, stateSpace, tagIndex, operator) + "\n");
    }
    assertEquals(sb.toString(), "ROOT, []\nA, [(A -> . A B, A -> . B, 1.0), (ROOT -> . A, ROOT -> ., 1.0)]\nB, [(A -> . B C, A -> . C, 1.0638297872340425), (A -> . B, A -> ., 1.0638297872340425), (C -> . B, C -> ., 1.0638297872340425), (A -> . C, A -> ., 0.3191489361702127), (B -> . C, B -> ., 0.3191489361702127)]\nC, [(B -> . C, B -> ., 1.0638297872340425), (A -> . C, A -> ., 1.0638297872340425), (A -> . B, A -> ., 0.21276595744680848), (A -> . B C, A -> . C, 0.21276595744680848), (C -> . B, C -> ., 0.21276595744680848)]\nA1, [(A -> . A1, A -> ., 1.0), (A -> . A B, A -> . B, 0.29999999999999993), (ROOT -> . A, ROOT -> ., 0.29999999999999993)]\nA2, [(A1 -> . A2, A1 -> ., 1.0), (A -> . A1, A -> ., 1.0), (A -> . A B, A -> . B, 0.29999999999999993), (ROOT -> . A, ROOT -> ., 0.29999999999999993)]\nD, [(B -> . D E, B -> . E, 1.0), (B -> . C, B -> ., 0.7446808510638298), (A -> . C, A -> ., 0.7446808510638298), (C -> . D, C -> ., 1.0), (A -> . B, A -> ., 0.14893617021276595), (A -> . B C, A -> . C, 0.14893617021276595), (C -> . B, C -> ., 0.14893617021276595)]\nE, [(B -> . E, B -> ., 1.0)]\n");    
  }
}
