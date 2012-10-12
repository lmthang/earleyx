package test;

import java.io.FileNotFoundException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import junit.framework.TestCase;
import org.junit.Before;

import parser.Completion;
import parser.EarleyParser;
import parser.EarleyParserDense;
import parser.Grammar;
import parser.Prediction;
import parser.Rule;
import parser.EdgeSpace;
import recursion.ClosureMatrix;
import recursion.RelationMatrix;
import utility.Utility;

import edu.stanford.nlp.parser.lexparser.IntTaggedWord;
import edu.stanford.nlp.stats.Counter;
import edu.stanford.nlp.util.HashIndex;
import edu.stanford.nlp.util.Index;

public class EarleyParserTest extends TestCase {
  private EarleyParser parser;
//  private String prefixModel = "parser.EarleyParserDense";
  String grammarString = "ROOT->[A] : 1.0\n" + 
  "A->[B C] : 0.5\n" +
  "A->[D B] : 0.5\n" +
  "B->[_b] : 0.9\n" +
  "B->[_UNK] : 0.1\n" +
  "C->[_c] : 0.9\n" +
  "C->[_UNK] : 0.1\n" +
  "D->[_d] : 0.8\n" +
  "D->[_UNK] : 0.1\n" +
  "D->[_UNK-1] : 0.1\n";
  
  String recursiveGrammarString = "ROOT->[A] : 1.0\n" + 
  "A->[B C] : 0.5\n" +
  "A->[D B] : 0.5\n" +
  "B->[A] : 0.1\n" +
  "B->[_b] : 0.8\n" +
  "B->[_UNK] : 0.1\n" +
  "C->[_c] : 0.9\n" +
  "C->[_UNK] : 0.1\n" +
  "D->[_d] : 0.8\n" +
  "D->[_UNK] : 0.1\n" +
  "D->[_UNK-1] : 0.1\n";

  String extendedGrammarString = "ROOT->[A] : 1.0\n" + 
  "A->[_b _e] : 0.1\n" +
  "A->[_b _c] : 0.1\n" +
  "A->[_d _c] : 0.1\n" +
  "A->[B C] : 0.6\n" +
  "A->[D C] : 0.1\n" +
  "B->[A] : 0.1\n" +
  "B->[_b] : 0.8\n" +
  "B->[_UNK] : 0.1\n" +
  "C->[_c] : 0.9\n" +
  "C->[_UNK] : 0.1\n" + 
  "D->[_d] : 0.9\n" +
  "D->[_UNK] : 0.1\n";
  
  String grammarStringWithPseudoNodes = "ROOT->[A] : 1.0\n" + 
  "A->[|A1-1|b |A1-2|e] : 0.1\n" +
  "A->[|A2-1|b |A2-2|c] : 0.1\n" +
  "A->[|A3-1|d |A3-2|c] : 0.1\n" +
  "A->[B C] : 0.6\n" +
  "A->[D C] : 0.1\n" +
  "|A1-1|b->[_b] : 1.0\n" +
  "|A1-2|e->[_e] : 1.0\n" +
  "|A2-1|b->[_b] : 1.0\n" +
  "|A2-2|c->[_c] : 1.0\n" +
  "|A3-1|d->[_d] : 1.0\n" +
  "|A3-2|c->[_c] : 1.0\n" +
  "B->[A] : 0.1\n" +
  "B->[_b] : 0.8\n" +
  "B->[_UNK] : 0.1\n" +
  "C->[_c] : 0.9\n" +
  "C->[_UNK] : 0.1\n" + 
  "D->[_d] : 0.9\n" +
  "D->[_UNK] : 0.1\n";
  
  String wsj500RuleFile = "../grammars/WSJ.500/WSJ.500.AG-PCFG.extendedRules";
  String wsj5000RuleFile = "../grammars/WSJ.5000/WSJ.5000.AG-PCFG.rules"; // this is the latest extended rules
  
  Index<String> wordIndex = new HashIndex<String>();
  Index<String> tagIndex = new HashIndex<String>();
  
  Collection<Rule> rules = new ArrayList<Rule>();
  Collection<Rule> extendedRules = new ArrayList<Rule>();
  
  Map<Integer, Counter<Integer>> tag2wordsMap = new HashMap<Integer, Counter<Integer>>();
  Map<Integer, Set<IntTaggedWord>> word2tagsMap = new HashMap<Integer, Set<IntTaggedWord>>();
  Set<Integer> nonterminals = new HashSet<Integer>();
  
  
  @Before
  public void setUp(){    
    // set output verbose modes
    //StateSpace.verbose = 2;
    //Grammar.verbose = 2;
    //RelationMatrix.verbose = 2;
    //ClosureMatrix.verbose = 2;
    Prediction.verbose = 3;
    Completion.verbose = 3;
    EarleyParser.verbose = 3;
  }
  
  private void initParserFromFile(String ruleFile){
    parser = new EarleyParserDense(ruleFile, "ROOT");
  }
  
  private void initParserFromString(String grammarString){
    try {
      parser= new EarleyParserDense(Utility.getBufferedReaderFromString(grammarString), "ROOT");
    } catch (FileNotFoundException e) {
      e.printStackTrace();
    }
  }
  
  public void testParsing1(){
    System.err.println(grammarString);
    initParserFromString(grammarString);
    
    String inputSentence = "b c";
    System.err.println("\n### Run test parsing with string \"" + inputSentence + "\"");
    List<List<Double>> resultLists = parser.parseSentence(inputSentence);
    assertEquals(resultLists.size(), 4);
    List<Double> surprisalList = resultLists.get(0);
    //List<Double> synSurprisalList = resultLists.get(1);
    //List<Double> lexSurprisalList = resultLists.get(2);
    //List<Double> stringProbList = resultLists.get(3);
    
    
    for (int i = 0; i < surprisalList.size(); i++) {
      System.err.println(i + "\t" + surprisalList.get(i));
    }
    
    assertEquals(surprisalList.size(), 2);
    assertEquals(0.7985076959756138, surprisalList.get(0), 1e-5);
    assertEquals(0.10536051541566838, surprisalList.get(1), 1e-5);
  }
  
  public void testParsing2(){
    initParserFromString(grammarString);
    
    String inputSentence = "a";
    System.err.println("\n### Run test parsing with string \"" + inputSentence + "\"");
    List<List<Double>> resultLists = parser.parseSentence(inputSentence);
    assertEquals(resultLists.size(), 4);
    List<Double> surprisalList = resultLists.get(0);
    List<Double> synSurprisalList = resultLists.get(1);
    List<Double> lexSurprisalList = resultLists.get(2);
    List<Double> stringProbList = resultLists.get(3);
    
    for (int i = 0; i < surprisalList.size(); i++) {
      System.err.println(i + "\t" + surprisalList.get(i));
    }
    
    assertEquals(surprisalList.size(), 1);
    assertEquals(2.3025851249694824, surprisalList.get(0), 1e-5);
  }
  
  public void testParsing3(){
    initParserFromString(grammarString);
    
    String inputSentence = "d e";
    System.err.println("\n### Run test parsing with string \"" + inputSentence + "\"");
    List<List<Double>> resultLists = parser.parseSentence(inputSentence);
    assertEquals(resultLists.size(), 4);
    List<Double> surprisalList = resultLists.get(0);
    List<Double> synSurprisalList = resultLists.get(1);
    List<Double> lexSurprisalList = resultLists.get(2);
    List<Double> stringProbList = resultLists.get(3);
    
    for (int i = 0; i < surprisalList.size(); i++) {
      System.err.println(i + "\t" + surprisalList.get(i));
    }
  
    assertEquals(surprisalList.size(), 2);
    assertEquals(0.9162907283333066, surprisalList.get(0), 1e-5);
    assertEquals(2.3025851249694824, surprisalList.get(1), 1e-5);
  }
  
  public void testRecursiveGrammar(){
    initParserFromString(recursiveGrammarString);
    
    String inputSentence = "d d b c";
    System.err.println("\n### Run test parsing with string \"" + inputSentence + "\"");
    List<List<Double>> resultLists = parser.parseSentence(inputSentence);
    assertEquals(resultLists.size(), 4);
    List<Double> surprisalList = resultLists.get(0);
    List<Double> synSurprisalList = resultLists.get(1);
    List<Double> lexSurprisalList = resultLists.get(2);
    List<Double> stringProbList = resultLists.get(3);
    
    for (int i = 0; i < surprisalList.size(); i++) {
      System.err.println(i + "\t" + surprisalList.get(i));
    }
  
    assertEquals(surprisalList.size(), 4);
    assertEquals(0.8649974339457559, surprisalList.get(0), 1e-5);
    assertEquals(3.167582526939802, surprisalList.get(1), 1e-5);
    assertEquals(0.17185025338581103, surprisalList.get(2), 1e-5);
    assertEquals(2.052896986215565, surprisalList.get(3), 1e-5);
  }
  
  public void testGrammarWithPseudoNodes() {
    initParserFromString(grammarStringWithPseudoNodes);
    
    String inputSentence = "b c";
    System.err.println("\n### Run test parsing with string \"" + inputSentence + "\"");
    List<List<Double>> resultLists = parser.parseSentence(inputSentence);
    assertEquals(resultLists.size(), 4);
    List<Double> surprisalList = resultLists.get(0);
    List<Double> synSurprisalList = resultLists.get(1);
    List<Double> lexSurprisalList = resultLists.get(2);
    List<Double> stringProbList = resultLists.get(3);
    
    for (int i = 0; i < surprisalList.size(); i++) {
      System.err.println(i + "\t" + surprisalList.get(i));
    }
    
    assertEquals(surprisalList.size(), 2);
    assertEquals(0.3237870745944744, surprisalList.get(0), 1e-5);
    assertEquals(0.24544930825601569, surprisalList.get(1), 1e-5);
  }
  
  public void testWSJ500(){
    EdgeSpace.verbose = 1;
    Grammar.verbose = 1;
    RelationMatrix.verbose = 1;
    ClosureMatrix.verbose = 1;
    EarleyParser.verbose = 1;
    
    initParserFromFile(wsj500RuleFile);
    
    int numSentences = 4;
    String[] inputSentences = new String[numSentences];
    inputSentences[0] = "Are tourists enticed by these attractions threatening their very existence ?";
    inputSentences[1] = "The two young sea-lions took not the slightest interest in our arrival .";
    inputSentences[2] = "A little further on were the blue-footed boobies , birds with brilliant china-blue feet , again unique .";
    inputSentences[3] = "It was late afternoon on one of the last days of the year , and we had come ashore to scramble round the rough dark lava rocks of Punta Espinosa on the island .";
    
//    for (int i = 2; i < numSentences; i++) {
//      String inputSentence = inputSentences[i];
//      System.err.println("\n### Run test parsing with string \"" + inputSentence + "\"");
//      double[] surprisals = parser.parseSentence(inputSentence);
//      for (double d : surprisals) {
//        System.err.println(d);
//      }
//    }
    
    String inputSentence = inputSentences[1];
    System.err.println("\n### Run test parsing with string \"" + inputSentence + "\"");
    List<List<Double>> resultLists = parser.parseSentence(inputSentence);
    assertEquals(resultLists.size(), 4);
    List<Double> surprisalList = resultLists.get(0);
    List<Double> synSurprisalList = resultLists.get(1);
    List<Double> lexSurprisalList = resultLists.get(2);
    List<Double> stringProbList = resultLists.get(3);
    
    for (int i = 0; i < surprisalList.size(); i++) {
      System.err.println(i + "\t" + surprisalList.get(i));
    }
    System.err.println(synSurprisalList);
    System.err.println(lexSurprisalList);
    assertEquals(surprisalList.size(), 13);
    assertEquals(2.5902917249093926, surprisalList.get(0), 1e-5);
    assertEquals(11.17802383709047, surprisalList.get(1), 1e-5);
    assertEquals(6.679507135955136, surprisalList.get(2), 1e-5);
    assertEquals(5.0073376384631345, surprisalList.get(3), 1e-5);
    assertEquals(9.380751667699116, surprisalList.get(4), 1e-5);
    assertEquals(6.460154578833205, surprisalList.get(5), 1e-5);
    assertEquals(4.3742137680538775, surprisalList.get(6), 1e-5);
    assertEquals(2.1105612970193235, surprisalList.get(7), 1e-5);
    assertEquals(7.902509908353828, surprisalList.get(8), 1e-5);
    assertEquals(3.841929098333189, surprisalList.get(9), 1e-5);
    assertEquals(7.72602036377566, surprisalList.get(10), 1e-5);
    assertEquals(1.2489743964356563, surprisalList.get(11), 1e-5);
    assertEquals(2.0214240754987856, surprisalList.get(12), 1e-5);
    
    assertEquals(synSurprisalList.size(), 13);
    assertEquals(2.3602916439035084, synSurprisalList.get(0), 1e-5);
    assertEquals(7.357910128168035, synSurprisalList.get(1), 1e-5);
    assertEquals(1.5620497546547085, synSurprisalList.get(2), 1e-5);
    assertEquals(0.0022030055514754587, synSurprisalList.get(3), 1e-5);
    assertEquals(4.266575485102322, synSurprisalList.get(4), 1e-5);
    assertEquals(3.3698137403538055, synSurprisalList.get(5), 1e-5);
    assertEquals(3.907705022942366, synSurprisalList.get(6), 1e-5);
    assertEquals(0.6713614106221825, synSurprisalList.get(7), 1e-5);
    assertEquals(1.798583726560615, synSurprisalList.get(8), 1e-5);
    assertEquals(2.4213217230452457, synSurprisalList.get(9), 1e-5);
    assertEquals(5.142054047896671, synSurprisalList.get(10), 1e-5);
    assertEquals(0.16264021799989337, synSurprisalList.get(11), 1e-5);
    assertEquals(1.845825275316443, synSurprisalList.get(12), 1e-5);
    
    assertEquals(lexSurprisalList.size(), 13);
    assertEquals(0.2300000810058728, lexSurprisalList.get(0), 1e-5);
    assertEquals(3.8201137089224453, lexSurprisalList.get(1), 1e-5);
    assertEquals(5.117457381300428, lexSurprisalList.get(2), 1e-5);
    assertEquals(5.005134632911659, lexSurprisalList.get(3), 1e-5);
    assertEquals(5.11417618259658, lexSurprisalList.get(4), 1e-5);
    assertEquals(3.090340838479669, lexSurprisalList.get(5), 1e-5);
    assertEquals(0.46650874511149715, lexSurprisalList.get(6), 1e-5);
    assertEquals(1.4391998863970983, lexSurprisalList.get(7), 1e-5);
    assertEquals(6.103926181793213, lexSurprisalList.get(8), 1e-5);
    assertEquals(1.4206073752878936, lexSurprisalList.get(9), 1e-5);
    assertEquals(2.583966315879323, lexSurprisalList.get(10), 1e-5);
    assertEquals(1.0863341784354787, lexSurprisalList.get(11), 1e-5);
    assertEquals(0.1755988001823425, lexSurprisalList.get(12), 1e-5);
  }
  
  public void testExtendedRule(){
    System.err.println(extendedGrammarString);
    initParserFromString(extendedGrammarString);
    
    String inputSentence = "b c";
    System.err.println("\n### Run test parsing with string \"" + inputSentence + "\"");
    
    List<List<Double>> resultLists = parser.parseSentence(inputSentence);
    assertEquals(resultLists.size(), 4);
    List<Double> surprisalList = resultLists.get(0);
    List<Double> synSurprisalList = resultLists.get(1);
    List<Double> lexSurprisalList = resultLists.get(2);
    List<Double> stringProbList = resultLists.get(3);
    
    for (int i = 0; i < surprisalList.size(); i++) {
      System.err.println(i + "\t" + surprisalList.get(i));
    }
    
    assertEquals(surprisalList.size(), 2);
    assertEquals(0.3237870745944744, surprisalList.get(0), 1e-5);
    assertEquals(0.24544930825601569, surprisalList.get(1), 1e-5);
  }

  public void testSimpleExtendedRule(){
    double x = 0.7;
    
    String simpleExtendedGrammarString = "ROOT->[A] : 1.0\n" + 
    "A->[_b _c] : " + x + "\n" + 
    "A->[D] : " + (1-x) + "\n";
    
    initParserFromString(simpleExtendedGrammarString);
    
    String inputSentence = "b c";
    System.err.println("\n### Run test parsing with string \"" + inputSentence + "\"");
    List<List<Double>> resultLists = parser.parseSentence(inputSentence);
    assertEquals(resultLists.size(), 4);
    List<Double> surprisalList = resultLists.get(0);
    List<Double> synSurprisalList = resultLists.get(1);
    List<Double> lexSurprisalList = resultLists.get(2);
    List<Double> stringProbList = resultLists.get(3);
    
    for (int i = 0; i < surprisalList.size(); i++) {
      System.err.println(i + "\t" + surprisalList.get(i));
    }
    
    assertEquals(surprisalList.size(), 2);
    assertEquals(-Math.log(x), surprisalList.get(0), 1e-5);
    assertEquals(0.0, surprisalList.get(1), 1e-5); // -ln(1)
  }
  
  public void testSimpleExtendedRule1(){
    double x = 0.7;
    double y = 0.6;
    String simpleExtendedGrammarString = "ROOT->[A] : 1.0\n" + 
    "A->[B] : " + x + "\n" + 
    "A->[_b _c] : " + (1-x) + "\n" + 
    "B->[B1] : " + y + "\n" + 
    "B->[_b _c] : " + (1-y) + "\n" +
    "B1->[_b] : " + 1.0 + "\n";
    
    initParserFromString(simpleExtendedGrammarString);
    
    String inputSentence = "b c";
    System.err.println("\n### Run test parsing with string \"" + inputSentence + "\"");
    List<List<Double>> resultLists = parser.parseSentence(inputSentence);
    assertEquals(resultLists.size(), 4);
    List<Double> surprisalList = resultLists.get(0);
    List<Double> synSurprisalList = resultLists.get(1);
    List<Double> lexSurprisalList = resultLists.get(2);
    List<Double> stringProbList = resultLists.get(3);
    
    for (int i = 0; i < surprisalList.size(); i++) {
      System.err.println(i + "\t" + surprisalList.get(i));
    }
    
    assertEquals(surprisalList.size(), 2);
    assertEquals(0.0, surprisalList.get(0), 1e-5);
    assertEquals(-Math.log(1-x*y), surprisalList.get(1), 1e-5);
    
    assertEquals(stringProbList.size(), 2);
    assertEquals(x*y, stringProbList.get(0), 1e-5);
    assertEquals(1-x*y, stringProbList.get(1), 1e-5);
  }
  
  public void testComplexAG(){
    double x1 = 0.1;
    double x2 = 0.2;
    double x3 = 0.3;
    double x4 = 0.3;
    double x5 = 1 - x1 - x2 - x3 - x4;
    double y1 = 0.1;
    double y2 = 0.2;
    double y3 = 0.3;
    double y4 = 0.3;
    double y5 = 1 - y1 - y2 - y3 - y4;
    
    String simpleExtendedGrammarString = "ROOT->[A] : 1.0\n" + 
    "A->[B] : " + x1 + "\n" +
    "A->[_b _c] : " + x2 + "\n" +
    "A->[_b _e] : " + x3 + "\n" +
    "A->[_b _c _d] : " + x4 + "\n" +
    "A->[_b _c _f] : " + x5 + "\n" +
    "B->[B1] : " + y1 + "\n" +
    "B->[_b _c] : " + y2 + "\n" +
    "B->[_b _e] : " + y3 + "\n" +
    "B->[_b _c _d] : " + y4 + "\n" +
    "B->[_b _c _f] : " + y5 + "\n" +
    "B1->[_b] : " + 1.0;
    
    initParserFromString(simpleExtendedGrammarString);
    
    String inputSentence = "b c d";
    System.err.println("\n### Run test parsing with string \"" + inputSentence + "\"");
    List<List<Double>> resultLists = parser.parseSentence(inputSentence);
    assertEquals(resultLists.size(), 4);
    List<Double> surprisalList = resultLists.get(0);
    List<Double> synSurprisalList = resultLists.get(1);
    List<Double> lexSurprisalList = resultLists.get(2);
    List<Double> stringProbList = resultLists.get(3);
    
    for (int i = 0; i < surprisalList.size(); i++) {
      System.err.println(i + "\t" + surprisalList.get(i));
    }
    
    assertEquals(surprisalList.size(), 3);
    assertEquals(0.0, surprisalList.get(0), 1e-5);
    assertEquals(-Math.log(x2 + x4 + x5 + x1*(y2 + y4 + y5)), surprisalList.get(1), 1e-5);
    assertEquals(-Math.log((x4 + x1*y4)/(x2 + x4 + x5 + x1*(y2 + y4 + y5))), surprisalList.get(2), 1e-5);
    
    assertEquals(stringProbList.size(), 3);
    assertEquals(x1*y1, stringProbList.get(0), 1e-5);
    assertEquals(x1*y2 + x2, stringProbList.get(1), 1e-5);
    assertEquals(x4 + x1*y4, stringProbList.get(2), 1e-5);
  }
  
  public void testComplexAG1(){
    double x1 = 0.1;
    double x2 = 1-x1;
    double y1 = 0.2;
    double y2 = 1-y1;
    double z1 = 0.3;
    double z2 = 1-z1;
    
    String simpleExtendedGrammarString = 
      "ROOT->[S] : " + x1 + "\n" +
      "ROOT->[_a _b _c] : " + x2 + "\n" +
      "S->[A S1] : " + y1 + "\n" +
      "S->[_a _b _c] : " + y2 + "\n" +
      "S1->[B C] : " + z1 + "\n" +
      "S1->[_b _c] : " + z2 + "\n" +
      "A->[_a] : " + 1.0 + "\n" +
      "B->[_b] : " + 1.0 + "\n" + 
      "C->[_c] : " + 1.0;
    
    initParserFromString(simpleExtendedGrammarString);
    
    String inputSentence = "a b c";
    System.err.println("\n### Run test parsing with string \"" + inputSentence + "\"");
    List<List<Double>> resultLists = parser.parseSentence(inputSentence);
    assertEquals(resultLists.size(), 4);
    List<Double> surprisalList = resultLists.get(0);
    List<Double> synSurprisalList = resultLists.get(1);
    List<Double> lexSurprisalList = resultLists.get(2);
    List<Double> stringProbList = resultLists.get(3);
    
    for (int i = 0; i < surprisalList.size(); i++) {
      System.err.println(i + "\t" + surprisalList.get(i));
    }
    
    assertEquals(surprisalList.size(), 3);
    assertEquals(0.0, surprisalList.get(0), 1e-5);
    assertEquals(0.0, surprisalList.get(1), 1e-5);
    assertEquals(0.0, surprisalList.get(2), 1e-5);
    
    assertEquals(stringProbList.size(), 3);
    assertEquals(0.0, stringProbList.get(0), 1e-5);
    assertEquals(0.0, stringProbList.get(1), 1e-5);
    assertEquals(1.0, stringProbList.get(2), 1e-5);
  }
  
  // @TODO: TRY THIS TEST!!!
  public void testComplexAGClosure(){
    double x1 = 0.01;
    double x2 = 1-x1;
    double y1 = 0.11;
    double y2 = 1-y1;
    
    double z1 = 0.21;
    double z2 = 0.22;
    double z3 = 0.23;
    double z4 = 1 - z1 - z2 - z3;
    
    String simpleExtendedGrammarString = 
      "ROOT->[S] : " + x1 + "\n" +
      "ROOT->[_a _b _c] : " + x2 + "\n" +
      "S->[A] : " + y1 + "\n" +
      "S->[S A] : " + y2 + "\n" +
      "A->[S] : " + z1 + "\n" +
      "A->[A1] : " + z2 + "\n" +
      "A->[_b _c] : " + z3 + "\n" +
      "A->[B C] : " + z4 + "\n" +
      "A1->[_a] : " + 1.0 + "\n" +
      "B->[_b] : " + 1.0 + "\n" + 
      "C->[_c] : " + 1.0;
    System.err.println(simpleExtendedGrammarString);
    
    Grammar.verbose = 3;
    RelationMatrix.verbose = 3;
    ClosureMatrix.verbose = 3;
    initParserFromString(simpleExtendedGrammarString);
    System.err.println(parser.getGrammar().getStateSpace());
    
    String inputSentence = "a b c";
    System.err.println("\n### Run test parsing with string \"" + inputSentence + "\"");
    List<List<Double>> resultLists = parser.parseSentence(inputSentence);
    assertEquals(resultLists.size(), 4);
    List<Double> surprisalList = resultLists.get(0);
    List<Double> synSurprisalList = resultLists.get(1);
    List<Double> lexSurprisalList = resultLists.get(2);
    List<Double> stringProbList = resultLists.get(3);
    
    for (int i = 0; i < surprisalList.size(); i++) {
      System.err.println(i + "\t" + surprisalList.get(i));
    }
    
    assertEquals(surprisalList.size(), 3);
    
  }
}
