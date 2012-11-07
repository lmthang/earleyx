package test;


import java.io.FileNotFoundException;
import java.util.List;

import junit.framework.TestCase;
import org.junit.Before;

import base.ClosureMatrix;
import base.RelationMatrix;

import parser.Completion;
import parser.EarleyParser;
import parser.EarleyParserDense;
import parser.EarleyParserSparse;
import parser.EdgeSpace;
import parser.Grammar;
import parser.Prediction;
import util.Util;

public class EarleyParserTest extends TestCase {
  private EarleyParser parser;
  private String rootSymbol = "ROOT";
  private int parserOpt = 0; // 0: dense, 1: sparse, 2: sparse IO
  private boolean isScaling = false; // true; //
  private boolean isLogProb = true; 
  boolean isComputeOutside = true; // false; //  
  
  
  String basicGrammarString = "ROOT->[A B] : 0.9\n" + 
  "ROOT->[_a _b] : 0.1\n" +
  "A->[_a] : 1.0\n" +
  "B->[_b] : 1.0\n";
  
  String basicUnaryGrammarString = "ROOT->[X] : 1.0\n" +
  "X->[A B] : 0.9\n" + 
  "X->[_a _b] : 0.1\n" +
  "A->[_a] : 1.0\n" +
  "B->[_b] : 1.0\n";
  
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
  
  String wsj500RuleFile = "grammars/WSJ.500/WSJ.500.AG-PCFG.extendedRules";
  String markGrammarFile = "grammars/testengger.grammar";
  
  @Before
  public void setUp(){    
    // set output verbose modes
    RelationMatrix.verbose = 0;
    ClosureMatrix.verbose = 0;
    EdgeSpace.verbose = 0;
    Grammar.verbose = 0;
    Prediction.verbose = 0;
    Completion.verbose = 0;
    EarleyParser.verbose = 0;
  }
  
  private void initParserFromFile(String ruleFile){
    if(parserOpt==0){
      parser = new EarleyParserDense(ruleFile, rootSymbol, isScaling, 
          isLogProb, isComputeOutside);
    } else if(parserOpt==1){
      parser = new EarleyParserSparse(ruleFile, rootSymbol, isScaling, 
          isLogProb, isComputeOutside);
    } else {
      assert(false);
    }
  }
  
  private void initParserFromString(String grammarString){
    try {
      if(parserOpt==0){
        parser= new EarleyParserDense(Util.getBufferedReaderFromString(grammarString), 
            rootSymbol, isScaling, isLogProb, isComputeOutside);
      } else if(parserOpt==1){    
        parser= new EarleyParserSparse(Util.getBufferedReaderFromString(grammarString), 
            rootSymbol, isScaling, isLogProb, isComputeOutside);
      }
    } catch (FileNotFoundException e) {
      e.printStackTrace();
    }
  }
  
  public void testBasic(){
    initParserFromString(basicGrammarString);
    
    String inputSentence = "a b";
    System.err.println("\n### Run test parsing with string \"" + inputSentence + "\"");
    List<List<Double>> resultLists = parser.parseSentence(inputSentence);
    assertEquals(resultLists.size(), 4);
    List<Double> surprisalList = resultLists.get(0);
    List<Double> stringProbList = resultLists.get(3);
    
    assertEquals(surprisalList.size(), 2);
    assertEquals(0.0, surprisalList.get(0), 1e-5);
    assertEquals(0.0, surprisalList.get(1), 1e-5);
    
    if(!isScaling){
      assertEquals(stringProbList.size(), 2);
      assertEquals(0.0, stringProbList.get(0), 1e-5);
      assertEquals(1.0, stringProbList.get(1), 1e-5);
    }
  }

  public void testIO(){
    rootSymbol = "S";
    initParserFromFile(markGrammarFile);
    
    int numSentences = 1;
    String[] inputSentences = new String[numSentences];
    inputSentences[0] = "the dog bites a cat";
    
    String inputSentence = inputSentences[0];
    System.err.println("\n### Run test parsing with string \"" + inputSentence + "\"");
    List<List<Double>> resultLists = parser.parseSentence(inputSentence);
    assertEquals(resultLists.size(), 4);
    List<Double> surprisalList = resultLists.get(0);
    List<Double> stringProbList = resultLists.get(3);
    
    assertEquals(surprisalList.size(), 5);
    System.err.println(surprisalList);
    assertEquals(1.9459104490553583, surprisalList.get(0), 1e-5);
    assertEquals(1.9459104490553583, surprisalList.get(1), 1e-5);
    assertEquals(1.9459104490553583, surprisalList.get(2), 1e-5);
    assertEquals(2.1690540003695684, surprisalList.get(3), 1e-5);
    assertEquals(1.9459104490553583, surprisalList.get(4), 1e-5);

    if(!isScaling){
      assertEquals(stringProbList.size(), 5);
      System.err.println(stringProbList);
      assertEquals(0.0, stringProbList.get(0), 1e-5);
      assertEquals(0.0, stringProbList.get(1), 1e-5);
      assertEquals(5.83089854227563E-4, stringProbList.get(2), 1e-5);
      assertEquals(0.0, stringProbList.get(3), 1e-5);
      assertEquals(2.3799571607089895E-5, stringProbList.get(4), 1e-5);
    }
    
    assertEquals(parser.dumpInsideChart(), "# Inside chart snapshot\ncell 0-1\n Det: 0.14285710\n N: 0.14285710\n V: 0.14285710\ncell 1-2\n Det: 0.14285710\n N: 0.14285710\n V: 0.14285710\ncell 2-3\n Det: 0.14285710\n N: 0.14285710\n V: 0.14285710\ncell 3-4\n Det: 0.14285710\n N: 0.14285710\n V: 0.14285710\ncell 4-5\n Det: 0.14285710\n N: 0.14285710\n V: 0.14285710\ncell 0-2\n NP: 0.02040815\ncell 2-4\n NP: 0.02040815\ncell 3-5\n NP: 0.02040815\ncell 0-3\n : 0.00058309\n S: 0.00058309\ncell 2-5\n VP: 0.00116618\ncell 0-5\n : 0.00002380\n S: 0.00002380\n");
    if(isComputeOutside){
      assertEquals(parser.dumpOutsideChart(), "# Outside chart snapshot\ncell 0-1\n Det: 0.00008330\n N: 0.00008330\ncell 1-2\n Det: 0.00008330\n N: 0.00008330\ncell 2-3\n Det: 0.00004165\n N: 0.00004165\n V: 0.00008330\ncell 3-4\n Det: 0.00008330\n N: 0.00008330\ncell 4-5\n Det: 0.00004165\n N: 0.00004165\n V: 0.00008330\ncell 0-2\n NP: 0.00116618\ncell 2-4\n NP: 0.00058309\ncell 3-5\n NP: 0.00058309\ncell 2-5\n VP: 0.02040815\ncell 0-5\n : 1.00000000\n S: 1.00000000\n");
    }
  }

  public void testBasicUnary(){
    initParserFromString(basicUnaryGrammarString);
    
    String inputSentence = "a b";
    System.err.println("\n### Run test parsing with string \"" + inputSentence + "\"");
    List<List<Double>> resultLists = parser.parseSentence(inputSentence);
    assertEquals(resultLists.size(), 4);
    List<Double> surprisalList = resultLists.get(0);
    List<Double> stringProbList = resultLists.get(3);
    
    
    for (int i = 0; i < surprisalList.size(); i++) {
      System.err.println(i + "\t" + surprisalList.get(i));
    }
    
    assertEquals(surprisalList.size(), 2);
    assertEquals(0.0, surprisalList.get(0), 1e-5);
    assertEquals(0.0, surprisalList.get(1), 1e-5);
    
    if(!isScaling){
      assertEquals(stringProbList.size(), 2);
      assertEquals(0.0, stringProbList.get(0), 1e-5);
      assertEquals(1.0, stringProbList.get(1), 1e-5);
    }
  }
  
  public void testLeftInfiniteGrammar(){
    double p = 0.1;
    double q = 1-p;
    String infiniteGrammarString = 
    "ROOT->[X] : " + p + "\n" +
    "ROOT->[ROOT X] : " + q + "\n" +
    "X->[_x] : 1.0\n";
    initParserFromString(infiniteGrammarString);
    
    int numSymbols = 100;
    StringBuffer sb = new StringBuffer("x");
    for (int i = 0; i < (numSymbols-1); i++) {
      sb.append(" x");
    }
    String inputSentence = sb.toString();
    
    System.err.println("\n### Run test parsing with string \"" + inputSentence + "\"");
    List<List<Double>> resultLists = parser.parseSentence(inputSentence);
    assertEquals(resultLists.size(), 4);
    List<Double> surprisalList = resultLists.get(0);
    List<Double> stringProbList = resultLists.get(3);
    
    assert(surprisalList.size() == numSymbols);
    
    // string x: string prob = p, prefix prob = 1.0, surprisal = -log(1)=0
    System.err.println(0 + "\tsurprisal " + surprisalList.get(0));
    assertEquals(0, surprisalList.get(0), 1e-10);
    if(!isScaling){
      assert(stringProbList.size()==numSymbols);
      System.err.println(0 + "\tstring prob " + stringProbList.get(0));
      assertEquals(p, stringProbList.get(0), 1e-10);
    }
    for (int i = 1; i < numSymbols; i++) {
      // strings of (i+1) x: string prob = p*q^i, prefix prob = q^i, surprisal = -log(q)
      System.err.println(i + "\tsurprisal " + surprisalList.get(i));
      assertEquals(-Math.log(q), surprisalList.get(i), 1e-10);
      if(!isScaling){
        System.err.println(i + "\tstring prob " + stringProbList.get(i));
        assertEquals(p*Math.pow(q, i), stringProbList.get(i), 1e-10);
      }
    }
  }
  
  public void testCatalanGrammar(){
    double p = 0.1;
    double q = 1-p;
    String catalanGrammarString = 
    "ROOT->[X] : " + p + "\n" +
    "ROOT->[ROOT ROOT] : " + q + "\n" +
    "X->[_x] : 1.0\n";
    initParserFromString(catalanGrammarString);
    
    int numSymbols = 100;
    StringBuffer sb = new StringBuffer("x");
    for (int i = 0; i < (numSymbols-1); i++) {
      sb.append(" x");
    }
    String inputSentence = sb.toString();
    
    System.err.println("\n### Run test parsing with string \"" + inputSentence + "\"");
    List<List<Double>> resultLists = parser.parseSentence(inputSentence);
    assertEquals(resultLists.size(), 4);
    List<Double> surprisalList = resultLists.get(0);
    List<Double> stringProbList = resultLists.get(3);
    
    assert(surprisalList.size() == numSymbols);
    
    // string x: string prob[1] = p, prefix prob[1] = 1.0, surprisal = -log(1)=0
//    System.err.println(0 + "\tsurprisal " + surprisalList.get(0));
    assertEquals(0, surprisalList.get(0), 1e-10);
    if(!isScaling){
      assert(stringProbList.size()==numSymbols);
//      System.err.println(0 + "\tstring prob " + stringProbList.get(0));
      assertEquals(p, stringProbList.get(0), 1e-10);
    }
    
    /// TO THINK: by right if p<q, the total prob string < 1, should prefix prob[1] < 1.0 ?
    //double totalStringProb = Math.min(1, p/q); // see "The Linguist's Guide to Statistics", section 4.6
    // ANSWER: Stolcke's approach only handles left-corner recursion and will be incorrect for the Catalan grammar
    int[][] c = Util.permutationMatrix(2*numSymbols);
    
    double prevPrefixProb = 1.0;
    double prevStringProb = p;
    double totalStringProb = p;
    for (int i = 1; i < numSymbols; i++) {
      System.err.println(i + "\tsurprisal " + surprisalList.get(i));
      
      // strings of (i+1) x: string prob[i+1] = (c[2i][i]/(i+1))*p^(i+1)*q^i
      // prefix prob[i+1] = prefix prob[i]-string prob[i]
      double currentStringProb = (c[2*i][i]/(i+1.0))*Math.pow(p,i+1)*Math.pow(q, i);
      if(!isScaling){
        System.err.println(i + "\tstring prob " + stringProbList.get(i));
        assertEquals(currentStringProb, stringProbList.get(i), 1e-10);
      }
      
      
      double currentPrefixProb = prevPrefixProb - prevStringProb;
      assertEquals(-Math.log(currentPrefixProb/prevPrefixProb), surprisalList.get(i), 1e-10);
      
      // update
      prevPrefixProb = currentPrefixProb;
      prevStringProb = currentStringProb;
      
      totalStringProb += currentStringProb;
      System.err.println(totalStringProb);
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
    List<Double> synSurprisalList = resultLists.get(1);
    List<Double> lexSurprisalList = resultLists.get(2);
    List<Double> stringProbList = resultLists.get(3);
    
    
    for (int i = 0; i < surprisalList.size(); i++) {
      System.err.println(i + "\t" + surprisalList.get(i));
    }
    
    assertEquals(surprisalList.size(), 2);
    assertEquals(0.7985076959756138, surprisalList.get(0), 1e-5);
    assertEquals(0.10536051541566838, surprisalList.get(1), 1e-5);
    
    if(!isScaling){
      assertEquals(synSurprisalList.size(), 2);
      assertEquals(0.6931471805599453, synSurprisalList.get(0), 1e-5);
      assertEquals(0.0, synSurprisalList.get(1), 1e-5);
      
      assertEquals(lexSurprisalList.size(), 2);
      assertEquals(0.10536051565782628, lexSurprisalList.get(0), 1e-5);
      assertEquals(0.10536051565782628, lexSurprisalList.get(1), 1e-5);
      
      assertEquals(stringProbList.toString(), "[0.0, 0.405]");
    }
  }
  
  public void testParsing2(){
    initParserFromString(grammarString);
    
    String inputSentence = "a";
    System.err.println("\n### Run test parsing with string \"" + inputSentence + "\"");
    List<List<Double>> resultLists = parser.parseSentence(inputSentence);
    assertEquals(resultLists.size(), 4);
    List<Double> surprisalList = resultLists.get(0);
    
    for (int i = 0; i < surprisalList.size(); i++) {
      System.err.println(i + "\t" + surprisalList.get(i));
    }
    
    assertEquals(surprisalList.size(), 1);
    assertEquals(2.3025851249694824, surprisalList.get(0), 1e-5);
    
    if(!isScaling){
      List<Double> synSurprisalList = resultLists.get(1);
      List<Double> lexSurprisalList = resultLists.get(2);
      List<Double> stringProbList = resultLists.get(3);
      
      assertEquals(synSurprisalList.size(), 1);
      assertEquals(1.1102230246251565E-16, synSurprisalList.get(0), 1e-5);
      
      assertEquals(lexSurprisalList.toString(), "[2.3025850929940455]");
      assertEquals(stringProbList.toString(), "[0.0]");
    }
  }
  
  public void testParsing3(){
    initParserFromString(grammarString);
    
    String inputSentence = "d e";
    System.err.println("\n### Run test parsing with string \"" + inputSentence + "\"");
    List<List<Double>> resultLists = parser.parseSentence(inputSentence);
    assertEquals(resultLists.size(), 4);
    List<Double> surprisalList = resultLists.get(0);
    
    for (int i = 0; i < surprisalList.size(); i++) {
      System.err.println(i + "\t" + surprisalList.get(i));
    }
  
    assertEquals(surprisalList.size(), 2);
    assertEquals(0.9162907283333066, surprisalList.get(0), 1e-5);
    assertEquals(2.3025851249694824, surprisalList.get(1), 1e-5);
    
    if(!isScaling){
      List<Double> synSurprisalList = resultLists.get(1);
      List<Double> lexSurprisalList = resultLists.get(2);
      List<Double> stringProbList = resultLists.get(3);
  
      assertEquals(synSurprisalList.size(), 2);
      assertEquals(0.6931471805599453, synSurprisalList.get(0), 1e-5);
      assertEquals(1.1102230246251565E-16, synSurprisalList.get(1), 1e-5);
      
      assertEquals(lexSurprisalList.toString(), "[0.2231435513142097, 2.3025850929940455]");
      assertEquals(stringProbList.toString(), "[0.0, 0.04000000000000001]");
    }
  }
  
  public void testRecursiveGrammar(){
    initParserFromString(recursiveGrammarString);
    
    String inputSentence = "d d b c";
    System.err.println("\n### Run test parsing with string \"" + inputSentence + "\"");
    List<List<Double>> resultLists = parser.parseSentence(inputSentence);
    assertEquals(resultLists.size(), 4);
    List<Double> surprisalList = resultLists.get(0);
    
    for (int i = 0; i < surprisalList.size(); i++) {
      System.err.println(i + "\t" + surprisalList.get(i));
    }
  
    assertEquals(surprisalList.size(), 4);
    assertEquals(0.8649974339457559, surprisalList.get(0), 1e-5);
    assertEquals(3.167582526939802, surprisalList.get(1), 1e-5);
    assertEquals(0.17185025338581103, surprisalList.get(2), 1e-5);
    assertEquals(2.052896986215565, surprisalList.get(3), 1e-5);
    
    if(!isScaling){
      List<Double> synSurprisalList = resultLists.get(1);
      List<Double> lexSurprisalList = resultLists.get(2);
      List<Double> stringProbList = resultLists.get(3);
      assertEquals(synSurprisalList.toString(), "[0.6418538861723948, 2.9444389791664407, -0.05129329438755048, 1.9475364707998972]");
      
      assertEquals(lexSurprisalList.size(), 4);
      assertEquals(0.2231435513142097, lexSurprisalList.get(0), 1e-5);
      assertEquals(0.2231435513142097, lexSurprisalList.get(1), 1e-5);
      assertEquals(0.2231435513142097, lexSurprisalList.get(2), 1e-5);
      assertEquals(0.10536051565782628, lexSurprisalList.get(3), 1e-5);

      assertEquals(stringProbList.size(), 4);
      assertEquals(0.0, stringProbList.get(0), 1e-5);
      assertEquals(0.0, stringProbList.get(1), 1e-5);
      assertEquals(0.012800000000000008, stringProbList.get(2), 1e-5);
      assertEquals(0.0017280000000000019, stringProbList.get(3), 1e-5);
    }
  }
  
  
  public void testWSJ500(){
    initParserFromFile(wsj500RuleFile);
    
    int numSentences = 4;
    String[] inputSentences = new String[numSentences];
    inputSentences[0] = "Are tourists enticed by these attractions threatening their very existence ?";
    inputSentences[1] = "The two young sea-lions took not the slightest interest in our arrival .";
    inputSentences[2] = "A little further on were the blue-footed boobies , birds with brilliant china-blue feet , again unique .";
    inputSentences[3] = "It was late afternoon on one of the last days of the year , and we had come ashore to scramble round the rough dark lava rocks of Punta Espinosa on the island .";
    
    String inputSentence = inputSentences[1];
    System.err.println("\n### Run test parsing with string \"" + inputSentence + "\"");
    List<List<Double>> resultLists = parser.parseSentence(inputSentence);
    assertEquals(resultLists.size(), 4);
    List<Double> surprisalList = resultLists.get(0);
    List<Double> stringProbList = resultLists.get(3);
    
    // Note: scores here are slightly different from those of previous version
    // that is because RoarkBaseLexicon.score returns float instead of double
    assertEquals(surprisalList.size(), 13);
    assertEquals(2.5902917249093926, surprisalList.get(0), 1e-5);
    assertEquals(11.17802383709047, surprisalList.get(1), 1e-5);
    assertEquals(6.679507135955136, surprisalList.get(2), 1e-5);
    assertEquals(4.948450710073175, surprisalList.get(3), 1e-5);
    assertEquals(9.385103677293866, surprisalList.get(4), 1e-5);
    assertEquals(6.44860564335427, surprisalList.get(5), 1e-5);
    assertEquals(4.250959913607808, surprisalList.get(6), 1e-5);
    assertEquals(2.033052140740189, surprisalList.get(7), 1e-5);
    assertEquals(7.950249255267927, surprisalList.get(8), 1e-5);
    assertEquals(3.9584749706452556, surprisalList.get(9), 1e-5);
    assertEquals(7.72672337348633, surprisalList.get(10), 1e-5);
    assertEquals(1.2492400898444487, surprisalList.get(11), 1e-5);
    assertEquals(2.072968468479857, surprisalList.get(12), 1e-5);

    if(!isScaling){
      assertEquals(stringProbList.size(), 13);
      assertEquals(2.7631257999498153E-6, stringProbList.get(0), 1e-5);
      assertEquals(3.7643574066525755E-8, stringProbList.get(1), 1e-5);
      assertEquals(1.7159626394143225E-12, stringProbList.get(2), 1e-5);
      assertEquals(1.8778083802959357E-12, stringProbList.get(3), 1e-5);
      assertEquals(5.8136325173038904E-18, stringProbList.get(4), 1e-5);
      assertEquals(1.1484971623511003E-20, stringProbList.get(5), 1e-5);
      assertEquals(9.315915216122732E-23, stringProbList.get(6), 1e-5);
      assertEquals(1.718918071281025E-22, stringProbList.get(7), 1e-5);
      assertEquals(8.212552526820724E-26, stringProbList.get(8), 1e-5);
      assertEquals(1.0082110788122197E-29, stringProbList.get(9), 1e-5);
      assertEquals(0.0, stringProbList.get(10), 1e-5);
      assertEquals(2.4430738209264177E-31, stringProbList.get(11), 1e-5);
      assertEquals(2.267542490039142E-31, stringProbList.get(12), 1e-5);
    }
  }
  
  public void testExtendedRule(){
    initParserFromString(extendedGrammarString);
    
    String inputSentence = "b c";
    System.err.println("\n### Run test parsing with string \"" + inputSentence + "\"");
    
    List<List<Double>> resultLists = parser.parseSentence(inputSentence);
    assertEquals(resultLists.size(), 4);
    List<Double> surprisalList = resultLists.get(0);
    
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
    List<Double> stringProbList = resultLists.get(3);
    
    for (int i = 0; i < surprisalList.size(); i++) {
      System.err.println(i + "\t" + surprisalList.get(i));
    }
    
    assertEquals(surprisalList.size(), 2);
    assertEquals(0.0, surprisalList.get(0), 1e-5);
    assertEquals(-Math.log(1-x*y), surprisalList.get(1), 1e-5);
    
    if(!isScaling){
      assertEquals(stringProbList.size(), 2);
      assertEquals(x*y, stringProbList.get(0), 1e-5);
      assertEquals(1-x*y, stringProbList.get(1), 1e-5);
    }
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
    List<Double> stringProbList = resultLists.get(3);
    
    for (int i = 0; i < surprisalList.size(); i++) {
      System.err.println(i + "\t" + surprisalList.get(i));
    }
    
    assertEquals(surprisalList.size(), 3);
    assertEquals(0.0, surprisalList.get(0), 1e-5);
    assertEquals(-Math.log(x2 + x4 + x5 + x1*(y2 + y4 + y5)), surprisalList.get(1), 1e-5);
    assertEquals(-Math.log((x4 + x1*y4)/(x2 + x4 + x5 + x1*(y2 + y4 + y5))), surprisalList.get(2), 1e-5);
    
    if(!isScaling){
      assertEquals(stringProbList.size(), 3);
      assertEquals(x1*y1, stringProbList.get(0), 1e-5);
      assertEquals(x1*y2 + x2, stringProbList.get(1), 1e-5);
      assertEquals(x4 + x1*y4, stringProbList.get(2), 1e-5);
    }
  }
  
  public void testComplexAG1(){
    double x1 = 0.1;
    double x2 = 1-x1;
    double y1 = 0.2;
    double y2 = 1-y1;
    double z1 = 0.3;
    double z2 = 1-z1;
    
    String complexAGGrammarString = 
      "ROOT->[S] : " + x1 + "\n" +
      "ROOT->[_a _b _c] : " + x2 + "\n" +
      "S->[A S1] : " + y1 + "\n" +
      "S->[_a _b _c] : " + y2 + "\n" +
      "S1->[B C] : " + z1 + "\n" +
      "S1->[_b _c] : " + z2 + "\n" +
      "A->[_a] : " + 1.0 + "\n" +
      "B->[_b] : " + 1.0 + "\n" + 
      "C->[_c] : " + 1.0;
    
    initParserFromString(complexAGGrammarString);
    
    String inputSentence = "a b c";
    System.err.println("\n### Run test parsing with string \"" + inputSentence + "\"");
    List<List<Double>> resultLists = parser.parseSentence(inputSentence);
    assertEquals(resultLists.size(), 4);
    List<Double> surprisalList = resultLists.get(0);
    List<Double> stringProbList = resultLists.get(3);
    
    for (int i = 0; i < surprisalList.size(); i++) {
      System.err.println(i + "\t" + surprisalList.get(i));
    }
    
    assertEquals(surprisalList.size(), 3);
    assertEquals(0.0, surprisalList.get(0), 1e-5);
    assertEquals(0.0, surprisalList.get(1), 1e-5);
    assertEquals(0.0, surprisalList.get(2), 1e-5);
    
    if(!isScaling){
      assertEquals(stringProbList.size(), 3);
      assertEquals(0.0, stringProbList.get(0), 1e-5);
      assertEquals(0.0, stringProbList.get(1), 1e-5);
      assertEquals(1.0, stringProbList.get(2), 1e-5);
    }
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
      "A->[B C] : " + z2 + "\n" +
      "A->[_b _c] : " + z3 + "\n" +
      "A->[A1] : " + z4 + "\n" +
      "B->[B1] : " + 1.0 + "\n" +
      "C->[C1] : " + 1.0 + "\n" +
      "A1->[_a] : " + 1.0 + "\n" +
      "B1->[_b] : " + 1.0 + "\n" + 
      "C1->[_c] : " + 1.0;
    
    initParserFromString(simpleExtendedGrammarString);
    
    String inputSentence = "a b c";
    System.err.println("\n### Run test parsing with string \"" + inputSentence + "\"");
    List<List<Double>> resultLists = parser.parseSentence(inputSentence);
    assertEquals(resultLists.size(), 4);
    List<Double> surprisalList = resultLists.get(0);
    List<Double> stringProbList = resultLists.get(3);
    
    // test left-corner closure
    // a = zeros(6,6); a(1,2)=1.0; a(2,3)=0.01; a(3,3)=0.89;a(3,4)=0.11;a(4,3)=0.21;a(4,5)=0.22;
    // (eye(6)-a)^(-1)
    if(isLogProb){
      assertEquals(Util.sprint(
        parser.getGrammar().getLeftCornerClosures().getClosureMatrix()), "0.0 0.0 -2.1621729392773004 -4.3694478524670215 -5.883575585096797 -Infinity\n-Infinity 0.0 -2.1621729392773004 -4.3694478524670215 -5.883575585096797 -Infinity\n-Infinity -Infinity 2.442997246710791 0.23572233352106994 -1.278405399108706 -Infinity\n-Infinity -Infinity 0.8823494984461224 0.23572233352106994 -1.2784053991087057 -Infinity");
    } else {
      assertEquals(Util.sprint(
          parser.getGrammar().getLeftCornerClosures().getClosureMatrix()), "1.0 1.0 0.11507479861910243 0.012658227848101267 0.002784810126582277 0.0\n0.0 1.0 0.11507479861910243 0.012658227848101267 0.002784810126582277 0.0\n0.0 0.0 11.507479861910243 1.2658227848101267 0.2784810126582277 0.0\n0.0 0.0 2.416570771001151 1.2658227848101267 0.27848101265822783 0.0");
    }
    

    for (int i = 0; i < surprisalList.size(); i++) {
      System.err.println(i + "\tsurprisal=" + surprisalList.get(i));
      if(!isScaling){
        assert(stringProbList.get(i)<=1.0);
      }
    }
    
    assertEquals(surprisalList.size(), 3);
    assertEquals(0.005712487765391473, surprisalList.get(0), 1e-5);
    assertEquals(0.0020843742359873776, surprisalList.get(1), 1e-5);
    assertEquals(0.0, surprisalList.get(2), 1e-5);
    
    if(!isScaling){
      assertEquals(stringProbList.size(), 3);
      assertEquals(3.8284368922100536E-4, stringProbList.get(0), 1e-5);
      assertEquals(0.0, stringProbList.get(1), 1e-5);
      assertEquals(0.9901606659305786, stringProbList.get(2), 1e-5);
    }
  }
}
