/**
 * 
 */
package test;

import java.io.IOException;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;

import parser.EdgeSpace;
import parser.LeftWildcardEdgeSpace;
import parser.StandardEdgeSpace;
import util.RuleFile;
import util.Util;
import base.ProbRule;
import base.RuleSet;
import edu.stanford.nlp.parser.lexparser.IntTaggedWord;
import edu.stanford.nlp.stats.Counter;
import edu.stanford.nlp.util.HashIndex;
import edu.stanford.nlp.util.Index;
import junit.framework.TestCase;

/**
 * @author Minh-Thang Luong, 2012
 *
 */
public class EdgeSpaceTest extends TestCase {
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
  
  String fragmentRuleString = 
    "ROOT->[A] : 1.0\n" + 
    "A->[A B] : 0.05\n" +
    "A->[A _b] : 0.025\n" +
    "A->[_a B] : 0.025\n" +
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
    Index<String> wordIndex = new HashIndex<String>();
    Index<String> tagIndex = new HashIndex<String>();
    
    RuleSet ruleSet = new RuleSet(tagIndex, wordIndex);
    
    Map<Integer, Counter<Integer>> tag2wordsMap = new HashMap<Integer, Counter<Integer>>();
    Map<Integer, Set<IntTaggedWord>> word2tagsMap = new HashMap<Integer, Set<IntTaggedWord>>();
    Map<Integer, Integer> nonterminalMap = new HashMap<Integer, Integer>();
    
    
    
    try {
      RuleFile.parseRuleFile(Util.getBufferedReaderFromString(ruleString), 
          ruleSet, tag2wordsMap, word2tagsMap, 
          nonterminalMap, wordIndex, tagIndex, false);
    } catch (IOException e){
      System.err.println("Error reading rules: " + ruleString);
      e.printStackTrace();
    }
    Collection<ProbRule> tagRules = ruleSet.getOtherRules();
    
    // statespace
    EdgeSpace edgeSpace = new StandardEdgeSpace(tagIndex, wordIndex);
    edgeSpace.build(tagRules);
    
    assertEquals(edgeSpace.toString(), "<active=0, to=2 (ROOT -> . A)>\n<passive=1 (A -> .)>\n<passive=2 (ROOT -> A .)>\n<active=3, to=4 (A -> . A B)>\n<active=4, to=6 (A -> A . B)>\n<passive=5 (B -> .)>\n<passive=6 (A -> A B .)>\n<active=7, to=8 (A -> . B C)>\n<active=8, to=10 (A -> B . C)>\n<passive=9 (C -> .)>\n<passive=10 (A -> B C .)>\n<active=11, to=13 (A -> . A1)>\n<passive=12 (A1 -> .)>\n<passive=13 (A -> A1 .)>\n<active=14, to=15 (A -> . _d _e)>\n<active=15, to=16 (A -> _d . _e)>\n<passive=16 (A -> _d _e .)>\n<active=17, to=19 (A1 -> . A2)>\n<passive=18 (A2 -> .)>\n<passive=19 (A1 -> A2 .)>\n<active=20, to=21 (B -> . C)>\n<passive=21 (B -> C .)>\n<active=22, to=24 (B -> . D E)>\n<passive=23 (D -> .)>\n<active=24, to=26 (B -> D . E)>\n<passive=25 (E -> .)>\n<passive=26 (B -> D E .)>\n<active=27, to=28 (C -> . B)>\n<passive=28 (C -> B .)>\n<active=29, to=30 (C -> . D)>\n<passive=30 (C -> D .)>\n<passive=31 (ROOT -> .)>\n");
  }
  
  public void testLeftWildcardBasic(){
    Index<String> wordIndex = new HashIndex<String>();
    Index<String> tagIndex = new HashIndex<String>();
    
    RuleSet ruleSet = new RuleSet(tagIndex, wordIndex);
    
    Map<Integer, Counter<Integer>> tag2wordsMap = new HashMap<Integer, Counter<Integer>>();
    Map<Integer, Set<IntTaggedWord>> word2tagsMap = new HashMap<Integer, Set<IntTaggedWord>>();
    Map<Integer, Integer> nonterminalMap = new HashMap<Integer, Integer>();
    
    
    
    try {
      RuleFile.parseRuleFile(Util.getBufferedReaderFromString(ruleString), 
          ruleSet, tag2wordsMap, word2tagsMap, 
          nonterminalMap, wordIndex, tagIndex, false);
    } catch (IOException e){
      System.err.println("Error reading rules: " + ruleString);
      e.printStackTrace();
    }
    Collection<ProbRule> tagRules = ruleSet.getOtherRules();
    
    // statespace
    EdgeSpace edgeSpace = new LeftWildcardEdgeSpace(tagIndex, wordIndex);
    edgeSpace.build(tagRules);
    
    assertEquals(edgeSpace.toString(), "<active=0, to=2 (ROOT -> . A)>\n<passive=1 (A -> .)>\n<passive=2 (ROOT -> .)>\n<active=3, to=4 (A -> . A B)>\n<active=4, to=1 (A -> . B)>\n<passive=5 (B -> .)>\n<active=6, to=7 (A -> . B C)>\n<active=7, to=1 (A -> . C)>\n<passive=8 (C -> .)>\n<active=9, to=1 (A -> . A1)>\n<passive=10 (A1 -> .)>\n<active=11, to=12 (A -> . _d _e)>\n<active=12, to=1 (A -> . _e)>\n<active=13, to=10 (A1 -> . A2)>\n<passive=14 (A2 -> .)>\n<active=15, to=5 (B -> . C)>\n<active=16, to=18 (B -> . D E)>\n<passive=17 (D -> .)>\n<active=18, to=5 (B -> . E)>\n<passive=19 (E -> .)>\n<active=20, to=8 (C -> . B)>\n<active=21, to=8 (C -> . D)>\n");
  }
  
  public void testFragmentBasic(){
    Index<String> wordIndex = new HashIndex<String>();
    Index<String> tagIndex = new HashIndex<String>();
    
    RuleSet ruleSet = new RuleSet(tagIndex, wordIndex);
    
    Map<Integer, Counter<Integer>> tag2wordsMap = new HashMap<Integer, Counter<Integer>>();
    Map<Integer, Set<IntTaggedWord>> word2tagsMap = new HashMap<Integer, Set<IntTaggedWord>>();
    Map<Integer, Integer> nonterminalMap = new HashMap<Integer, Integer>();
    
    
    
    try {
      RuleFile.parseRuleFile(Util.getBufferedReaderFromString(fragmentRuleString), 
          ruleSet, tag2wordsMap, word2tagsMap, 
          nonterminalMap, wordIndex, tagIndex, false);
    } catch (IOException e){
      System.err.println("Error reading rules: " + fragmentRuleString);
      e.printStackTrace();
    }
    Collection<ProbRule> tagRules = ruleSet.getOtherRules();
    
    // statespace
    EdgeSpace edgeSpace = new StandardEdgeSpace(tagIndex, wordIndex);
    edgeSpace.build(tagRules);
    
    assertEquals(edgeSpace.toString(), "<active=0, to=2 (ROOT -> . A)>\n<passive=1 (A -> .)>\n<passive=2 (ROOT -> A .)>\n<active=3, to=4 (A -> . A B)>\n<active=4, to=6 (A -> A . B)>\n<passive=5 (B -> .)>\n<passive=6 (A -> A B .)>\n<active=7, to=8 (A -> . A _b)>\n<active=8, to=9 (A -> A . _b)>\n<passive=9 (A -> A _b .)>\n<active=10, to=11 (A -> . _a B)>\n<active=11, to=12 (A -> _a . B)>\n<passive=12 (A -> _a B .)>\n<active=13, to=14 (A -> . B C)>\n<active=14, to=16 (A -> B . C)>\n<passive=15 (C -> .)>\n<passive=16 (A -> B C .)>\n<active=17, to=19 (A -> . A1)>\n<passive=18 (A1 -> .)>\n<passive=19 (A -> A1 .)>\n<active=20, to=21 (A -> . _d _e)>\n<active=21, to=22 (A -> _d . _e)>\n<passive=22 (A -> _d _e .)>\n<active=23, to=25 (A1 -> . A2)>\n<passive=24 (A2 -> .)>\n<passive=25 (A1 -> A2 .)>\n<active=26, to=27 (B -> . C)>\n<passive=27 (B -> C .)>\n<active=28, to=30 (B -> . D E)>\n<passive=29 (D -> .)>\n<active=30, to=32 (B -> D . E)>\n<passive=31 (E -> .)>\n<passive=32 (B -> D E .)>\n<active=33, to=34 (C -> . B)>\n<passive=34 (C -> B .)>\n<active=35, to=36 (C -> . D)>\n<passive=36 (C -> D .)>\n<passive=37 (ROOT -> .)>\n");
    
    Map<Integer, Set<Integer>> terminal2fragmentEdges = edgeSpace.getTerminal2fragmentEdges();
    StringBuffer sb = new StringBuffer();
    for (int terminalIndex : terminal2fragmentEdges.keySet()) {
      sb.append(wordIndex.get(terminalIndex) + ":");
      for (int edge : terminal2fragmentEdges.get(terminalIndex)){
        sb.append(" " + edgeSpace.get(edge).toString(tagIndex, wordIndex));
      }
      sb.append("\n");
    }
    assertEquals(sb.toString(), "b: A -> A . _b\na: A -> . _a B\nd: A -> . _d _e\ne: A -> _d . _e\n");
  }
  
  public void testWildcardFragmentBasic(){
    Index<String> wordIndex = new HashIndex<String>();
    Index<String> tagIndex = new HashIndex<String>();
    
    RuleSet ruleSet = new RuleSet(tagIndex, wordIndex);
    
    Map<Integer, Counter<Integer>> tag2wordsMap = new HashMap<Integer, Counter<Integer>>();
    Map<Integer, Set<IntTaggedWord>> word2tagsMap = new HashMap<Integer, Set<IntTaggedWord>>();
    Map<Integer, Integer> nonterminalMap = new HashMap<Integer, Integer>();
    
    
    
    try {
      RuleFile.parseRuleFile(Util.getBufferedReaderFromString(fragmentRuleString), 
          ruleSet, tag2wordsMap, word2tagsMap, 
          nonterminalMap, wordIndex, tagIndex, false);
    } catch (IOException e){
      System.err.println("Error reading rules: " + fragmentRuleString);
      e.printStackTrace();
    }
    Collection<ProbRule> tagRules = ruleSet.getOtherRules();
    
    // statespace
    EdgeSpace edgeSpace = new LeftWildcardEdgeSpace(tagIndex, wordIndex);
    edgeSpace.build(tagRules);
    
    assertEquals(edgeSpace.toString(), "<active=0, to=2 (ROOT -> . A)>\n<passive=1 (A -> .)>\n<passive=2 (ROOT -> .)>\n<active=3, to=4 (A -> . A B)>\n<active=4, to=1 (A -> . B)>\n<passive=5 (B -> .)>\n<active=6, to=7 (A -> . A _b)>\n<active=7, to=1 (A -> . _b)>\n<active=8, to=4 (A -> . _a B)>\n<active=9, to=10 (A -> . B C)>\n<active=10, to=1 (A -> . C)>\n<passive=11 (C -> .)>\n<active=12, to=1 (A -> . A1)>\n<passive=13 (A1 -> .)>\n<active=14, to=15 (A -> . _d _e)>\n<active=15, to=1 (A -> . _e)>\n<active=16, to=13 (A1 -> . A2)>\n<passive=17 (A2 -> .)>\n<active=18, to=5 (B -> . C)>\n<active=19, to=21 (B -> . D E)>\n<passive=20 (D -> .)>\n<active=21, to=5 (B -> . E)>\n<passive=22 (E -> .)>\n<active=23, to=11 (C -> . B)>\n<active=24, to=11 (C -> . D)>\n");
    
    Map<Integer, Set<Integer>> terminal2fragmentEdges = edgeSpace.getTerminal2fragmentEdges();
    StringBuffer sb = new StringBuffer();
    for (int terminalIndex : terminal2fragmentEdges.keySet()) {
      sb.append(wordIndex.get(terminalIndex) + ":");
      for (int edge : terminal2fragmentEdges.get(terminalIndex)){
        sb.append(" " + edgeSpace.get(edge).toString(tagIndex, wordIndex));
      }
      sb.append("\n");
    }
    assertEquals(sb.toString(), "b: A -> . _b\na: A -> . _a B\nd: A -> . _d _e\ne: A -> . _e\n");
  }
}
