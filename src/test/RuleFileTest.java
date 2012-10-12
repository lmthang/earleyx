package test;


import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import junit.framework.TestCase;
import lexicon.SmoothLexicon;

import parser.Rule;
import parser.RuleFile;

import utility.Utility;

import edu.stanford.nlp.parser.lexparser.IntTaggedWord;
import edu.stanford.nlp.stats.Counter;
import edu.stanford.nlp.util.HashIndex;
import edu.stanford.nlp.util.Index;

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
  "B->[_b1] : 1\n" +
  "B->[_b2] : 1\n" +
  "B->[_b3] : 2\n" +
  "C->[_C] : 1\n" +
  "D->[_d] : 1\n";

  public void testInput(){  
    Index<String> wordIndex = new HashIndex<String>();
    Index<String> tagIndex = new HashIndex<String>();
    
    Collection<Rule> rules = new ArrayList<Rule>();
    Collection<Rule> extendedRules = new ArrayList<Rule>();
    
    Map<Integer, Counter<Integer>> tag2wordsMap = new HashMap<Integer, Counter<Integer>>();
    Map<Integer, Set<IntTaggedWord>> word2tagsMap = new HashMap<Integer, Set<IntTaggedWord>>();
    Set<Integer> nonterminals = new HashSet<Integer>();
    
    
    
    try {
      RuleFile.parseRuleFile(Utility.getBufferedReaderFromString(ruleString), 
          rules, extendedRules, tag2wordsMap, word2tagsMap, 
          nonterminals, wordIndex, tagIndex);
    } catch (IOException e){
      System.err.println("Error reading rules: " + ruleString);
      e.printStackTrace();
    }
    
    assertEquals(tagIndex.toString(), "[0=ROOT,1=A,2=B,3=C,4=D]");
    assertEquals(wordIndex.toString(), "[0=b,1=c,2=d,3=UNK,4=UNK-1]");
    
    assertEquals(Utility.sprint(rules, wordIndex, tagIndex), "[ROOT->[A] : 1.0, A->[B C] : 0.4, A->[D B] : 0.4]");
    assertEquals(Utility.sprint(extendedRules, wordIndex, tagIndex), "[A->[_b _c] : 0.1, A->[_d _b] : 0.1]");
    
    assertEquals(Utility.sprint(tagIndex, nonterminals), "(0, ROOT) (1, A)");
    assertEquals(Utility.sprint(tag2wordsMap, tagIndex, wordIndex), "{B={b=0.9, UNK=0.1}, C={c=0.9, UNK=0.1}, D={d=0.8, UNK=0.1, UNK-1=0.1}");
    assertEquals(Utility.sprintWord2Tags(word2tagsMap, wordIndex, tagIndex), "{b=[b/B}, c=[c/C}, d=[d/D}, UNK=[UNK/C, UNK/B, UNK/D}, UNK-1=[UNK-1/D}");
  }
  
  public void testRuleSmoothing(){
    Map<Integer, Counter<Integer>> tag2wordsMap = new HashMap<Integer, Counter<Integer>>();
    Map<Integer, Set<IntTaggedWord>> word2tagsMap = new HashMap<Integer, Set<IntTaggedWord>>();
    Set<Integer> nonterminals = new HashSet<Integer>();
    Index<String> wordIndex = new HashIndex<String>();
    Index<String> tagIndex = new HashIndex<String>();
    Collection<Rule> rules = new ArrayList<Rule>();
    Collection<Rule> extendedRules = new ArrayList<Rule>();
    
    /* Input */
    try {
      RuleFile.parseRuleFile(Utility.getBufferedReaderFromString(ruleStringNoSmooth), rules, extendedRules, tag2wordsMap, 
          word2tagsMap, nonterminals, wordIndex, tagIndex); // we don't care much about extended rules, just treat them as rules
      //rules.addAll(extendedRules);
    } catch (IOException e){
      System.err.println("Can't read rule string: " + ruleString);
      e.printStackTrace();
    }
    System.err.println(ruleStringNoSmooth);
    
    assertEquals(Utility.sprint(rules, wordIndex, tagIndex), "[ROOT->[A] : 5.0, A->[B C] : 4.0, A->[D B] : 4.0]");
    
    /* Smooth */
    assertEquals(Utility.sprint(tag2wordsMap, tagIndex, wordIndex), "{B={b=9.0, b1=1.0, b2=1.0, b3=2.0}, C={C=1.0}, D={d=1.0}");
    SmoothLexicon.smooth(tag2wordsMap, wordIndex, word2tagsMap, true); //Map<IntTaggedWord, Counter<IntTaggedWord>> newWordCounterTagMap = RuleFile.smoothWordCounterTagMap(tag2wordsMap);
    assertEquals(Utility.sprint(tag2wordsMap, tagIndex, wordIndex), "{B={b=9.0, b1=1.0, b2=1.0, b3=2.0, UNKNOWN=1.0, UNK-cb1=1.0, UNK-cb2=1.0}, C={C=1.0, UNKNOWN=1.0, UNK-CC=1.0}, D={d=1.0, UNKNOWN=1.0, UNK-cd=1.0}");
    
    assertEquals(Utility.sprintWord2Tags(word2tagsMap, wordIndex, tagIndex), "{b=[b/B}, d=[d/D}, b1=[b1/B}, b2=[b2/B}, b3=[b3/B}, C=[C/C}, UNKNOWN=[UNKNOWN/D, UNKNOWN/C, UNKNOWN/B}, UNK-cb1=[UNK-cb1/B}, UNK-cb2=[UNK-cb2/B}, UNK-CC=[UNK-CC/C}, UNK-cd=[UNK-cd/D}");
    
    
    
    // test scheme print
    rules.addAll(extendedRules);
    assertEquals(Utility.schemeSprint(rules, wordIndex, tagIndex), "[(ROOT (_ A)), (A (_ B) (_ C)), (A (_ D) (_ B)), (A (_ _b) (_ _c)), (A (_ _d) (_ _b))]");
  }
}
