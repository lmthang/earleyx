package test;

import java.io.IOException;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;

import parser.SmoothLexicon;

import base.ProbRule;
import base.RuleSet;

import junit.framework.TestCase;


import util.RuleFile;
import util.Util;

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
  
  String fragmentRuleString = "ROOT->[A] : 1.0\n" + 
  "A->[_b _c] : 0.1\n" +
  "A->[B _c] : 0.1\n" +
  "A->[_d _b] : 0.1\n" +
  "A->[_d B] : 0.1\n" +
  "A->[B C] : 0.3\n" +
  "A->[D B] : 0.3\n" +
  "B->[A] : 0.1\n" +
  "B->[_b] : 0.8\n" +
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

  String biasRuleString = "S->[NP VP] : 1.000000e+00\nNP->[Det N] : 5.000000e-01\nNP->[N Det] : 5.000000e-01\nVP->[V] : 2.000000e-01\nVP->[V NP] : 2.000000e-01\nVP->[NP V] : 2.000000e-01\nVP->[V NP NP] : 2.000000e-01\nVP->[NP NP V] : 2.000000e-01\n1.000000e-02 Det->[_the] : 1.428571e-01\nN->[_the] : 1.428571e-01\nV->[_the] : 1.428571e-01\n1.000000e-02 Det->[_a] : 1.428571e-01\nN->[_a] : 1.428571e-01\nV->[_a] : 1.428571e-01\n1.000000e-02 Det->[_dog] : 1.428571e-01\nN->[_dog] : 1.428571e-01\nV->[_dog] : 1.428571e-01\n1.000000e-02 Det->[_cat] : 1.428571e-01\nN->[_cat] : 1.428571e-01\nV->[_cat] : 1.428571e-01\n1.000000e-02 Det->[_bone] : 1.428571e-01\nN->[_bone] : 1.428571e-01\nV->[_bone] : 1.428571e-01\n1.000000e-02 Det->[_bites] : 1.428571e-01\nN->[_bites] : 1.428571e-01\nV->[_bites] : 1.428571e-01\n1.000000e-02 Det->[_gives] : 1.428571e-01\nN->[_gives] : 1.428571e-01\nV->[_gives] : 1.428571e-01\n";
  
  public void testInput(){  
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
    Collection<ProbRule> tagRules = ruleSet.getTagRules();
    Collection<ProbRule> multiTerminalRules = ruleSet.getMultiTerminalRules();
    
    assertEquals(tagIndex.toString(), "[0=ROOT,1=A,2=B,3=C,4=D]");
    assertEquals(wordIndex.toString(), "[0=b,1=c,2=d,3=UNK,4=UNK-1]");
    
    assertEquals(Util.sprint(tagRules, tagIndex, wordIndex), "ROOT->[A] : 1.00000\nA->[B C] : 0.400000\nA->[D B] : 0.400000\n");
    assertEquals(Util.sprint(multiTerminalRules, wordIndex, tagIndex), "A->[_b _c] : 0.100000\nA->[_d _b] : 0.100000\n");
    
    assertEquals(Util.sprint(tagIndex, nonterminalMap.keySet()), "[ROOT, A]");
    assertEquals(Util.sprint(tag2wordsMap, tagIndex, wordIndex), "{B={b=0.9, UNK=0.1}, C={c=0.9, UNK=0.1}, D={d=0.8, UNK=0.1, UNK-1=0.1}");
    assertEquals(Util.sprintWord2Tags(word2tagsMap, wordIndex, tagIndex), "{b=[b/B}, c=[c/C}, d=[d/D}, UNK=[UNK/C, UNK/B, UNK/D}, UNK-1=[UNK-1/D}");
  }
  
  public void testFragmentInput(){  
    Index<String> tagIndex = new HashIndex<String>();
    Index<String> wordIndex = new HashIndex<String>();
    
    RuleSet ruleSet = new RuleSet(tagIndex, wordIndex);
    
    Map<Integer, Counter<Integer>> tag2wordsMap = new HashMap<Integer, Counter<Integer>>();
    Map<Integer, Set<IntTaggedWord>> word2tagsMap = new HashMap<Integer, Set<IntTaggedWord>>();
    Map<Integer, Integer> nonterminalMap = new HashMap<Integer, Integer>();
    
    
    
    try {
      RuleFile.parseRuleFile(Util.getBufferedReaderFromString(fragmentRuleString), 
          ruleSet, tag2wordsMap, word2tagsMap, 
          nonterminalMap, wordIndex, tagIndex, false);
    } catch (IOException e){
      System.err.println("Error reading rules: " + ruleString);
      e.printStackTrace();
    }
    assertEquals(ruleSet.toString(tagIndex, wordIndex), "\n# Ruleset\nROOT->[A] : 1.00000\nA->[_b _c] : 0.100000\nA->[B _c] : 0.100000\nA->[_d _b] : 0.100000\nA->[_d B] : 0.100000\nA->[B C] : 0.300000\nA->[D B] : 0.300000\nB->[A] : 0.100000\nB->[_b] : 0.800000\nB->[_UNK] : 0.100000\nC->[_c] : 0.900000\nC->[_UNK] : 0.100000\nD->[_d] : 0.800000\nD->[_UNK] : 0.100000\nD->[_UNK-1] : 0.100000\n");
    
    assertEquals(2, ruleSet.numFragmentRules());
  }
  
  public void testBiasInput(){  
    Index<String> wordIndex = new HashIndex<String>();
    Index<String> tagIndex = new HashIndex<String>();
    
    RuleSet ruleSet = new RuleSet(tagIndex, wordIndex);
    
    Map<Integer, Counter<Integer>> tag2wordsMap = new HashMap<Integer, Counter<Integer>>();
    Map<Integer, Set<IntTaggedWord>> word2tagsMap = new HashMap<Integer, Set<IntTaggedWord>>();
    Map<Integer, Integer> nonterminalMap = new HashMap<Integer, Integer>();
    
    
    
    try {
      RuleFile.parseRuleFile(Util.getBufferedReaderFromString(biasRuleString), 
          ruleSet, tag2wordsMap, word2tagsMap, 
          nonterminalMap, wordIndex, tagIndex, false);
    } catch (IOException e){
      System.err.println("Error reading rules: " + ruleString);
      e.printStackTrace();
    }
    
    assertEquals(ruleSet.toString(tagIndex, wordIndex), "\n# Ruleset\nS->[NP VP] : 1.00000\nNP->[Det N] : 0.500000\nNP->[N Det] : 0.500000\nVP->[V] : 0.200000\nVP->[V NP] : 0.200000\nVP->[NP V] : 0.200000\nVP->[V NP NP] : 0.200000\nVP->[NP NP V] : 0.200000\n0.0100000 Det->[_the] : 0.142857\nN->[_the] : 0.142857\nV->[_the] : 0.142857\n0.0100000 Det->[_a] : 0.142857\nN->[_a] : 0.142857\nV->[_a] : 0.142857\n0.0100000 Det->[_dog] : 0.142857\nN->[_dog] : 0.142857\nV->[_dog] : 0.142857\n0.0100000 Det->[_cat] : 0.142857\nN->[_cat] : 0.142857\nV->[_cat] : 0.142857\n0.0100000 Det->[_bone] : 0.142857\nN->[_bone] : 0.142857\nV->[_bone] : 0.142857\n0.0100000 Det->[_bites] : 0.142857\nN->[_bites] : 0.142857\nV->[_bites] : 0.142857\n0.0100000 Det->[_gives] : 0.142857\nN->[_gives] : 0.142857\nV->[_gives] : 0.142857\n");
  }
  
  public void testRuleSmoothing(){
    Map<Integer, Counter<Integer>> tag2wordsMap = new HashMap<Integer, Counter<Integer>>();
    Map<Integer, Set<IntTaggedWord>> word2tagsMap = new HashMap<Integer, Set<IntTaggedWord>>();
    Map<Integer, Integer> nonterminalMap = new HashMap<Integer, Integer>();
    Index<String> wordIndex = new HashIndex<String>();
    Index<String> tagIndex = new HashIndex<String>();
    
    RuleSet ruleSet = new RuleSet(tagIndex, wordIndex);
    /* Input */
    try {
      RuleFile.parseRuleFile(Util.getBufferedReaderFromString(ruleStringNoSmooth), 
          ruleSet, tag2wordsMap, 
          word2tagsMap, nonterminalMap, wordIndex, tagIndex, false); // we don't care much about extended rules, just treat them as rules
      //rules.addAll(extendedRules);
    } catch (IOException e){
      System.err.println("Can't read rule string: " + ruleString);
      e.printStackTrace();
    }
    Collection<ProbRule> tagRules = ruleSet.getTagRules();
    Collection<ProbRule> multiTerminalRules = ruleSet.getMultiTerminalRules();
    
    assertEquals(Util.sprint(tagRules, tagIndex, wordIndex), "ROOT->[A] : 5.00000\nA->[B C] : 4.00000\nA->[D B] : 4.00000\n");
    
    /* Smooth */
    assertEquals(Util.sprint(tag2wordsMap, tagIndex, wordIndex), "{B={b=9.0, b1=1.0, b2=1.0, b3=2.0}, C={C=1.0}, D={d=1.0}");
    SmoothLexicon.smooth(tag2wordsMap, wordIndex, tagIndex, word2tagsMap, true); //Map<IntTaggedWord, Counter<IntTaggedWord>> newWordCounterTagMap = RuleFile.smoothWordCounterTagMap(tag2wordsMap);
    assertEquals(Util.sprint(tag2wordsMap, tagIndex, wordIndex), "{B={b=9.0, b1=1.0, b2=1.0, b3=2.0, UNK=1.0, UNK-LC-DIG=2.0}, C={C=1.0, UNK=1.0, UNK-ALLC=1.0}, D={d=1.0, UNK=1.0, UNK-LC=1.0}");
    
    assertEquals(Util.sprintWord2Tags(word2tagsMap, wordIndex, tagIndex), "{b=[b/B}, d=[d/D}, b1=[b1/B}, b2=[b2/B}, b3=[b3/B}, C=[C/C}, UNK=[UNK/D, UNK/C, UNK/B}, UNK-LC-DIG=[UNK-LC-DIG/B}, UNK-ALLC=[UNK-ALLC/C}, UNK-LC=[UNK-LC/D}");
    
    // test scheme print
    tagRules.addAll(multiTerminalRules);
    assertEquals(Util.schemeSprint(tagRules, wordIndex, tagIndex), "[(ROOT (_ A)), (A (_ B) (_ C)), (A (_ D) (_ B)), (A (_ _b) (_ _c)), (A (_ _d) (_ _b))]");
  }
}
