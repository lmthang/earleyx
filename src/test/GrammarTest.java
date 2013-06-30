package test;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;

import base.FragmentRule;
import base.ProbRule;
import base.RuleSet;
import base.TerminalRule;

import parser.EdgeSpace;
import parser.Grammar;
import parser.LeftWildcardEdgeSpace;


import util.LogProbOperator;
import util.Operator;
import util.RuleFile;
import util.Util;
import edu.stanford.nlp.parser.lexparser.IntTaggedWord;
import edu.stanford.nlp.stats.Counter;
import edu.stanford.nlp.util.HashIndex;
import edu.stanford.nlp.util.Index;
import junit.framework.TestCase;


public class GrammarTest extends TestCase{
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
    
    EdgeSpace edgeSpace = new LeftWildcardEdgeSpace(tagIndex, wordIndex);
    edgeSpace.build(tagRules);
    
    Grammar g = new Grammar(wordIndex, tagIndex, nonterminalMap, operator);
    boolean isSeparateRuleInTrie = false;
    g.learnGrammar(ruleSet, edgeSpace, isSeparateRuleInTrie);
    assertEquals(g.getRuleTrie().toString(wordIndex, tagIndex), "\nd:prefix={A=-0.9}\n e:prefix={A=-0.9}, end={A=-0.9}");
 
    g = new Grammar(wordIndex, tagIndex, nonterminalMap, operator);
    isSeparateRuleInTrie = true;
    g.learnGrammar(ruleSet, edgeSpace, isSeparateRuleInTrie);
    assertEquals(g.getRuleTrie().toString(ruleSet, wordIndex, tagIndex), "\nd:prefix={A->[_d _e] : 0.400000=-0.9}\n e:prefix={A->[_d _e] : 0.400000=-0.9}, end={A->[_d _e] : 0.400000=-0.9}");
  }
  
  public void testBasic1(){
    Collection<ProbRule> rules = new ArrayList<ProbRule>();
    Index<String> tagIndex = new HashIndex<String>();
    Index<String> wordIndex = new HashIndex<String>();
    ProbRule s = new ProbRule(new FragmentRule("S", Arrays.asList(new String[]{"NP", "VP"}), tagIndex, wordIndex, true), 1.0);
    ProbRule vp = new ProbRule(new FragmentRule("VP", Arrays.asList(new String[]{"V", "NP"}), tagIndex, wordIndex, true), 0.9);
    ProbRule np1 = new ProbRule(new FragmentRule("NP", Arrays.asList(new String[]{"NP", "PP"}), tagIndex, wordIndex, true), 0.4);
    ProbRule np2 = new ProbRule(new FragmentRule("NP", Arrays.asList(new String[]{"DT", "NN"}), tagIndex, wordIndex, true), 0.2);
    ProbRule np3 = new ProbRule(new FragmentRule("NP", Arrays.asList(new String[]{"PP"}), tagIndex, wordIndex, true), 0.1);
    ProbRule pp = new ProbRule(new FragmentRule("PP", Arrays.asList(new String[]{"P", "NP"}), tagIndex, wordIndex, true), 0.6);
    ProbRule pp1 = new ProbRule(new FragmentRule("PP", Arrays.asList(new String[]{"NP"}), tagIndex, wordIndex, true), 0.4); 
    ProbRule root = new ProbRule(new FragmentRule("ROOT", Arrays.asList(new String[]{"S"}), tagIndex, wordIndex, true), 1.0); 

    rules.add(s);
    rules.add(vp);
    rules.add(np1);
    rules.add(np2);
    rules.add(np3);
    rules.add(pp);
    rules.add(pp1);
    rules.add(root);
    
    ProbRule achefNP = new ProbRule(new TerminalRule("NP", Arrays.asList(new String[]{"a", "chef"}), tagIndex, wordIndex), 0.15);
    ProbRule thechefNP = new ProbRule(new TerminalRule("NP", Arrays.asList(new String[]{"the", "chef"}), tagIndex, wordIndex), 0.1);
    ProbRule asoupNP = new ProbRule(new TerminalRule("NP", Arrays.asList(new String[]{"a", "soup"}), tagIndex, wordIndex), 0.05);
    ProbRule cooksoupVP = new ProbRule(new TerminalRule("VP", Arrays.asList(new String[]{"cook", "soup"}), tagIndex, wordIndex), 0.1);

    Collection<ProbRule> extendedRules = new ArrayList<ProbRule>();
    extendedRules.add(achefNP);
    extendedRules.add(thechefNP);
    extendedRules.add(asoupNP);
    extendedRules.add(cooksoupVP);
    Map<Integer, Integer> nonterminalMap = new HashMap<Integer, Integer>();
    for (int i = 0; i < tagIndex.size(); i++) {
      String tag = tagIndex.get(i);
      if(!tag.equals("a") && !tag.equals("the") && !tag.equals("chef") && !tag.equals("cook") && !tag.equals("soup")){
        if(!nonterminalMap.containsKey(i)){
          nonterminalMap.put(i, nonterminalMap.size());
        }
      }
    }

    rules.addAll(extendedRules);
    EdgeSpace edgeSpace = new LeftWildcardEdgeSpace(tagIndex, wordIndex);
    edgeSpace.build(rules);
    
    Grammar g = new Grammar(wordIndex, tagIndex, nonterminalMap, operator);
    RuleSet ruleSet = new RuleSet(tagIndex, wordIndex);
    ruleSet.addAll(rules);
    boolean isSeparateRuleInTrie = false;
    g.learnGrammar(ruleSet, edgeSpace, isSeparateRuleInTrie);
    assertEquals(g.getRuleTrie().toString(wordIndex, tagIndex), "\na:prefix={NP=-1.6}\n chef:prefix={NP=-1.9}, end={NP=-1.9}\n soup:prefix={NP=-3.0}, end={NP=-3.0}\nthe:prefix={NP=-2.3}\n chef:prefix={NP=-2.3}, end={NP=-2.3}\ncook:prefix={VP=-2.3}\n soup:prefix={VP=-2.3}, end={VP=-2.3}");
    assertEquals(Util.sprint(g.getCompletions(tagIndex.indexOf("PP")), edgeSpace, 
        tagIndex, wordIndex, operator), "[(NP -> . PP, 1.0416666666666667), (VP -> . NP, 0.1041666666666667), (NP -> . NP PP, 0.1041666666666667), (PP -> . NP, 0.1041666666666667), (S -> . NP VP, 0.1041666666666667)]");

    g = new Grammar(wordIndex, tagIndex, nonterminalMap, operator);
    isSeparateRuleInTrie = true;
    g.learnGrammar(ruleSet, edgeSpace, isSeparateRuleInTrie);
    assertEquals(g.getRuleTrie().toString(ruleSet, wordIndex, tagIndex), "\na:prefix={NP->[_a _chef] : 0.150000=-1.9, NP->[_a _soup] : 0.0500000=-3.0}\n chef:prefix={NP->[_a _chef] : 0.150000=-1.9}, end={NP->[_a _chef] : 0.150000=-1.9}\n soup:prefix={NP->[_a _soup] : 0.0500000=-3.0}, end={NP->[_a _soup] : 0.0500000=-3.0}\nthe:prefix={NP->[_the _chef] : 0.100000=-2.3}\n chef:prefix={NP->[_the _chef] : 0.100000=-2.3}, end={NP->[_the _chef] : 0.100000=-2.3}\ncook:prefix={VP->[_cook _soup] : 0.100000=-2.3}\n soup:prefix={VP->[_cook _soup] : 0.100000=-2.3}, end={VP->[_cook _soup] : 0.100000=-2.3}");
    assertEquals(Util.sprint(g.getCompletions(tagIndex.indexOf("PP")), edgeSpace, 
        tagIndex, wordIndex, operator), "[(NP -> . PP, 1.0416666666666667), (VP -> . NP, 0.1041666666666667), (NP -> . NP PP, 0.1041666666666667), (PP -> . NP, 0.1041666666666667), (S -> . NP VP, 0.1041666666666667)]");

  }
}
