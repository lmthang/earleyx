package test;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;

import base.ProbRule;
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
  
    EdgeSpace edgeSpace = new LeftWildcardEdgeSpace(tagIndex);
    edgeSpace.build(rules);
    
    Grammar g = new Grammar(wordIndex, tagIndex, nonterminalMap, operator);
    g.learnGrammar(rules, extendedRules, edgeSpace);
    
    assertEquals(g.getRuleTrie().toString(wordIndex, tagIndex), "\nd:prefix={A=-0.9}\n e:prefix={A=-0.9}, end={A=-0.9}");
 
  }
  
  public void testBasic1(){
    Collection<ProbRule> rules = new ArrayList<ProbRule>();
    Index<String> tagIndex = new HashIndex<String>();
    Index<String> wordIndex = new HashIndex<String>();
    ProbRule s = new ProbRule("S", Arrays.asList(new String[]{"NP", "VP"}), 1.0, tagIndex, tagIndex);
    ProbRule vp = new ProbRule("VP", Arrays.asList(new String[]{"V", "NP"}), 0.9, tagIndex, tagIndex);
    ProbRule np1 = new ProbRule("NP", Arrays.asList(new String[]{"NP", "PP"}), 0.4, tagIndex, tagIndex);
    ProbRule np2 = new ProbRule("NP", Arrays.asList(new String[]{"DT", "NN"}), 0.2, tagIndex, tagIndex);
    ProbRule np3 = new ProbRule("NP", Arrays.asList(new String[]{"PP"}), 0.1, tagIndex, tagIndex);
    ProbRule pp = new ProbRule("PP", Arrays.asList(new String[]{"P", "NP"}), 0.6, tagIndex, tagIndex);
    ProbRule pp1 = new ProbRule("PP", Arrays.asList(new String[]{"NP"}), 0.4, tagIndex, tagIndex); 
    ProbRule root = new ProbRule("ROOT", Arrays.asList(new String[]{"S"}), 1.0, tagIndex, tagIndex); 

    rules.add(s);
    rules.add(vp);
    rules.add(np1);
    rules.add(np2);
    rules.add(np3);
    rules.add(pp);
    rules.add(pp1);
    rules.add(root);
    
    ProbRule achefNP = new TerminalRule("NP", Arrays.asList(new String[]{"a", "chef"}), 0.15, tagIndex, wordIndex);
    ProbRule thechefNP = new TerminalRule("NP", Arrays.asList(new String[]{"the", "chef"}), 0.1, tagIndex, wordIndex);
    ProbRule asoupNP = new TerminalRule("NP", Arrays.asList(new String[]{"a", "soup"}), 0.05, tagIndex, wordIndex);
    ProbRule cooksoupVP = new TerminalRule("VP", Arrays.asList(new String[]{"cook", "soup"}), 0.1, tagIndex, wordIndex);

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

    EdgeSpace edgeSpace = new LeftWildcardEdgeSpace(tagIndex);
    edgeSpace.build(rules);
    
    Grammar g = new Grammar(wordIndex, tagIndex, nonterminalMap, operator);
    g.learnGrammar(rules, extendedRules, edgeSpace);
    
    assertEquals(g.getRuleTrie().toString(wordIndex, tagIndex), "\na:prefix={NP=-1.6}\n chef:prefix={NP=-1.9}, end={NP=-1.9}\n soup:prefix={NP=-3.0}, end={NP=-3.0}\nthe:prefix={NP=-2.3}\n chef:prefix={NP=-2.3}, end={NP=-2.3}\ncook:prefix={VP=-2.3}\n soup:prefix={VP=-2.3}, end={VP=-2.3}");
 
    assertEquals(Util.sprint(g.getCompletions(tagIndex.indexOf("PP")), edgeSpace, 
        tagIndex, operator), "[(NP -> . PP, NP -> ., 1.0416666666666667), (VP -> . NP, VP -> ., 0.1041666666666667), (NP -> . NP PP, NP -> . PP, 0.1041666666666667), (PP -> . NP, PP -> ., 0.1041666666666667), (S -> . NP VP, S -> . VP, 0.1041666666666667)]");

  }
}
