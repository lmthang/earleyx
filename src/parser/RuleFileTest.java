package parser;


import java.io.IOException;
import java.io.StringReader;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import junit.framework.TestCase;

import org.junit.Before;

import edu.stanford.nlp.ling.Label;
import edu.stanford.nlp.parser.lexparser.IntTaggedWord;
import edu.stanford.nlp.stats.Counter;

public class RuleFileTest extends TestCase{

  String ruleString = "ROOT->[A] : 1.0\n" + 
  "A->[_b _c] : 0.1\n" +
  "A->[_d _b] : 0.1\n" +
  "A->[B C] : 0.4\n" +
  "A->[D B] : 0.4\n" +
  "B->[_b] : 0.9\n" +
  "B->[_UNK] : 0.1\n" +
  "C->[_c] : 0.9\n" +
  "C->[_UNK] : 0.1\n" +
  "D->[_d] : 0.8\n" +
  "D->[_UNK] : 0.1\n" +
  "D->[_UNK-1] : 0.1\n";
  
  String ruleStringNoSmooth = "ROOT->[A] : 5\n" + 
  "A->[_b _c] : 1\n" +
  "A->[_d _b] : 1\n" +
  "A->[B C] : 4\n" +
  "A->[D B] : 4\n" +
  "B->[_b] : 9\n" +
  "C->[_C] : 1\n" +
  "D->[_d] : 1\n";
  
  @Before
  public void setUp() throws Exception {

  }
  
  public void testInput(){  
    Collection<Rule> rules = new ArrayList<Rule>();
    Map<IntTaggedWord, Counter<IntTaggedWord>> wordCounterTagMap = new HashMap<IntTaggedWord, Counter<IntTaggedWord>>();
    Map<IntTaggedWord, Set<IntTaggedWord>> tagsForWord = new HashMap<IntTaggedWord, Set<IntTaggedWord>>();
    Set<IntTaggedWord> preterminalSet = new HashSet<IntTaggedWord>();
    Map<Label, Counter<String>> tagHash = new HashMap<Label, Counter<String>>();
    Set<String> seenEnd = new HashSet<String>();
    Collection<Rule> extendedRules = new ArrayList<Rule>();
    
    StringReader sr = new StringReader(ruleString);

 
    try {
      RuleFile.isPseudoTag = false;
      RuleFile.parseRuleFile(sr, rules, extendedRules, wordCounterTagMap, tagsForWord, preterminalSet, tagHash, seenEnd);
    } catch (IOException e){
      System.err.println("Error reading rules: " + ruleString);
      e.printStackTrace();
    }
    
    assertEquals(preterminalSet.toString(), "[.*./D, .*./C, .*./B]");
    assertEquals(rules.toString(), "[ROOT->[A] : 1.0, A->[B C] : 0.4, A->[D B] : 0.4]");
    assertEquals(extendedRules.toString(), "[A->[b c] : 0.1, A->[d b] : 0.1]");
    assertEquals(wordCounterTagMap.toString(), "{.*./D={d/.*.=0.8}, .*./C={c/.*.=0.9}, .*./B={b/.*.=0.9}}");
    assertEquals(tagsForWord.toString(), "{UNK-1/.*.=[UNK-1/D], UNK/.*.=[UNK/C, UNK/B, UNK/D], d/.*.=[d/D], c/.*.=[c/C], b/.*.=[b/B]}");
    assertEquals(tagHash.toString(), "{D={UNK=0.1, UNK-1=0.1}, B={UNK=0.1}, C={UNK=0.1}}");
    assertEquals(seenEnd.toString(), "[UNK-1]");
    
    sr.close();
  }

  public void testInputIsPseudoTag(){  
    StringReader sr = new StringReader(ruleString);

    Collection<Rule> rules = new ArrayList<Rule>();
    Map<IntTaggedWord, Counter<IntTaggedWord>> wordCounterTagMap = new HashMap<IntTaggedWord, Counter<IntTaggedWord>>();
    Map<IntTaggedWord, Set<IntTaggedWord>> tagsForWord = new HashMap<IntTaggedWord, Set<IntTaggedWord>>();
    Set<IntTaggedWord> preterminalSet = new HashSet<IntTaggedWord>();
    Map<Label, Counter<String>> tagHash = new HashMap<Label, Counter<String>>();
    Set<String> seenEnd = new HashSet<String>();
    Collection<Rule> extendedRules = new ArrayList<Rule>();
    
    try {
      RuleFile.isPseudoTag = true;
      RuleFile.parseRuleFile(sr, rules, extendedRules, wordCounterTagMap, tagsForWord, preterminalSet, tagHash, seenEnd);
    } catch (IOException e){
      System.err.println("Error reading rules: " + ruleString);
      e.printStackTrace();
    }
    
    assertEquals(preterminalSet.toString(), "[.*./D, .*./C, .*./B]");
    assertEquals(rules.toString(), "[ROOT->[A] : 1.0, A->[B C] : 0.4, A->[D B] : 0.4]");
    assertEquals(extendedRules.toString(), "[A->[PSEUDO|A1-1|b PSEUDO|A1-2|c] : 0.1, A->[PSEUDO|A2-1|d PSEUDO|A2-2|b] : 0.1]");
    assertEquals(wordCounterTagMap.toString(), "{.*./D={d/.*.=0.8}, .*./C={c/.*.=0.9}, .*./B={b/.*.=0.9}}");
    assertEquals(tagsForWord.toString(), "{UNK-1/.*.=[UNK-1/D], UNK/.*.=[UNK/C, UNK/B, UNK/D], d/.*.=[d/PSEUDO|A2-1|d, d/D], c/.*.=[c/C, c/PSEUDO|A1-2|c], b/.*.=[b/B, b/PSEUDO|A1-1|b, b/PSEUDO|A2-2|b]}");
    assertEquals(tagHash.toString(), "{D={UNK=0.1, UNK-1=0.1}, B={UNK=0.1}, C={UNK=0.1}}");
    assertEquals(seenEnd.toString(), "[UNK-1]");  
    
    sr.close();
  }
  
  public void testRuleSmoothing(){
    StringReader sr = new StringReader(ruleStringNoSmooth);
    
    Collection<Rule> rules = new ArrayList<Rule>();
    Map<IntTaggedWord, Counter<IntTaggedWord>> wordCounterTagMap = new HashMap<IntTaggedWord, Counter<IntTaggedWord>>();
    Map<IntTaggedWord, Set<IntTaggedWord>> tagsForWord = new HashMap<IntTaggedWord, Set<IntTaggedWord>>();
    Set<IntTaggedWord> preterminalSet = new HashSet<IntTaggedWord>();
    Map<Label, Counter<String>> tagHash = new HashMap<Label, Counter<String>>();
    Set<String> seenEnd = new HashSet<String>();
    Collection<Rule> extendedRules = new ArrayList<Rule>();
    
    /* Input */
    try {
      RuleFile.isPseudoTag = false;
      RuleFile.parseRuleFile(sr, rules, extendedRules, wordCounterTagMap, 
          tagsForWord, preterminalSet, tagHash, seenEnd); // we don't care much about extended rules, just treat them as rules
      //rules.addAll(extendedRules);
    } catch (IOException e){
      System.err.println("Can't read rule string: " + ruleString);
      e.printStackTrace();
    }
    
    /* Smooth */
    
    Map<IntTaggedWord, Counter<IntTaggedWord>> newWordCounterTagMap = RuleFile.smoothWordCounterTagMap(wordCounterTagMap);
    assertEquals(rules.toString(), "[ROOT->[A] : 5.0, A->[B C] : 4.0, A->[D B] : 4.0]");
    //System.err.println(newWordCounterTagMap);
    assertEquals(newWordCounterTagMap.toString(), "{.*./D={UNK-LC/.*.=1.0, UNK/.*.=1.0, d/.*.=1.0}, .*./C={UNK-ALLC/.*.=1.0, C/.*.=1.0, UNK/.*.=1.0}, .*./B={UNK/.*.=1.0, b/.*.=9.0}}");
    StringBuffer sb = new StringBuffer();
    
    rules.addAll(extendedRules);
    for (Rule rule : rules) {
      sb.append(rule.schemeString() + ", ");
    }
    
    sb.delete(sb.length()-2, sb.length());
    System.err.println("rules: " + sb.toString());
    assertEquals(sb.toString(), "(ROOT (X A)), (A (X B) (X C)), (A (X D) (X B)), (A (X _b) (X _c)), (A (X _d) (X _b))");  
    sr.close();
  }
}
