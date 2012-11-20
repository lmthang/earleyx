package test;


import java.io.FileNotFoundException;
import java.util.ArrayList;
import java.util.List;

import junit.framework.TestCase;
import org.junit.Before;

import edu.stanford.nlp.trees.Tree;

import base.ClosureMatrix;
import base.RelationMatrix;

import parser.Completion;
import parser.EarleyParser;
import parser.EarleyParserDense;
import parser.EarleyParserSparse;
import parser.EdgeSpace;
import parser.Grammar;
import parser.Prediction;
import util.RuleFile;
import util.Util;

public class EarleyParserTest extends TestCase {
  private EarleyParser parser;
  private String rootSymbol = "ROOT";
  private int parserOpt = 1; // 0: dense, 1: sparse, 2: sparse IO
  private boolean isScaling = true; // 
  private boolean isLogProb = true; 
  private int insideOutsideOpt = 1; // false; //          
  private int decodeOpt = 1; // 1: Viterbi
  
  @Before
  public void setUp(){    
    // set output verbose modes
    RelationMatrix.verbose = 0;
    ClosureMatrix.verbose = 0;
    EdgeSpace.verbose = 0;
    Grammar.verbose = 0;
    Prediction.verbose = 0;
    Completion.verbose = 0;
    RuleFile.verbose = 0;
    EarleyParser.verbose = 0;
  }
  
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
  
  String wsj500AG = "grammars/WSJ.500/WSJ.500.AG-PCFG.extendedRules";
  String wsj500 = "grammars/wsj500unk.grammar";
  String wsj5 = "grammars/wsj5unk.grammar";
  String markGrammarFile = "grammars/testengger.grammar";
  
  private void initParserFromFile(String ruleFile){
    int inGrammarType = 1; // read from grammar
    if(parserOpt==0){
      parser = new EarleyParserDense(ruleFile, inGrammarType, rootSymbol, isScaling, 
          isLogProb, insideOutsideOpt);
    } else if(parserOpt==1){
      parser = new EarleyParserSparse(ruleFile, inGrammarType, rootSymbol, isScaling, 
          isLogProb, insideOutsideOpt);
    } else {
      assert(false);
    }
    
    parser.setDecodeOpt(decodeOpt);
  }
  
  private void initParserFromString(String grammarString){
    try {
      if(parserOpt==0){
        parser= new EarleyParserDense(Util.getBufferedReaderFromString(grammarString), 
            rootSymbol, isScaling, isLogProb, insideOutsideOpt);
      } else if(parserOpt==1){    
        parser= new EarleyParserSparse(Util.getBufferedReaderFromString(grammarString), 
            rootSymbol, isScaling, isLogProb, insideOutsideOpt);
      }
    } catch (FileNotFoundException e) {
      e.printStackTrace();
    }
    
    parser.setDecodeOpt(decodeOpt);
  }
  
  public void testBasic(){
    initParserFromString(basicGrammarString);
    
    String inputSentence = "a b";
    System.err.println("\n### Run test parsing with string \"" + inputSentence + "\"");
    List<List<Double>> resultLists = parser.parseSentence(inputSentence);
    assertEquals(resultLists.size(), 4);
    List<Double> surprisalList = resultLists.get(0);
    List<Double> stringLogProbList = resultLists.get(3);
    List<Double> stringProbList = new ArrayList<Double>();
    for(double logProb : stringLogProbList){
      stringProbList.add(Math.exp(logProb));
    }
    
    assertEquals(surprisalList.size(), 2);
    assertEquals(0.0, surprisalList.get(0), 1e-5);
    assertEquals(0.0, surprisalList.get(1), 1e-5);
    
    
    assertEquals(stringProbList.size(), 2);
    assertEquals(0.0, stringProbList.get(0), 1e-5);
    assertEquals(1.0, stringProbList.get(1), 1e-5);
  }
  
  public void testExtendedGrammarIO(){
    initParserFromString(extendedGrammarString);
    
    List<String> inputSentences = new ArrayList<String>();
    inputSentences.add("b c");
    inputSentences.add("d c");
    
    if(insideOutsideOpt>0){
      List<Double> sumNegLogProbList = parser.insideOutside(inputSentences);
      assertEquals(sumNegLogProbList.toString(), "[2.340370037356804, 1.3862943611198908, 1.3862943611198908]");
    }
  }

  public void testMarkGrammar(){
    rootSymbol = "S";
    initParserFromFile(markGrammarFile);
    
    String inputSentence = "the dog bites a cat";
    System.err.println("\n### Run test parsing with string \"" + inputSentence + "\"");
    List<List<Double>> resultLists = parser.parseSentence(inputSentence);
    
    List<Double> surprisalList = resultLists.get(0);
    List<Double> stringLogProbList = resultLists.get(3);
    List<Double> stringProbList = new ArrayList<Double>();
    for(double logProb : stringLogProbList){
      stringProbList.add(Math.exp(logProb));
    }
        
    assertEquals(surprisalList.size(), 5);
    assertEquals(1.9459104490553583, surprisalList.get(0), 1e-5);
    assertEquals(1.9459104490553583, surprisalList.get(1), 1e-5);
    assertEquals(1.9459104490553583, surprisalList.get(2), 1e-5);
    assertEquals(2.1690540003695684, surprisalList.get(3), 1e-5);
    assertEquals(1.9459104490553583, surprisalList.get(4), 1e-5);

    assertEquals(stringProbList.size(), 5);
    assertEquals(0.0, stringProbList.get(0), 1e-5);
    assertEquals(0.0, stringProbList.get(1), 1e-5);
    assertEquals(5.83089854227563E-4, stringProbList.get(2), 1e-5);
    assertEquals(0.0, stringProbList.get(3), 1e-5);
    assertEquals(2.3799571607089895E-5, stringProbList.get(4), 1e-5);
    
    assertEquals(parser.dumpInsideChart(), "# Inside chart snapshot\ncell 0-1\n Det: 0.14285710\n N: 0.14285710\n V: 0.14285710\ncell 1-2\n Det: 0.14285710\n N: 0.14285710\n V: 0.14285710\ncell 2-3\n Det: 0.14285710\n N: 0.14285710\n V: 0.14285710\ncell 3-4\n Det: 0.14285710\n N: 0.14285710\n V: 0.14285710\ncell 4-5\n Det: 0.14285710\n N: 0.14285710\n V: 0.14285710\ncell 0-2\n NP: 0.02040815\ncell 2-4\n NP: 0.02040815\ncell 3-5\n NP: 0.02040815\ncell 0-3\n : 0.00058309\n S: 0.00058309\ncell 2-5\n VP: 0.00116618\ncell 0-5\n : 0.00002380\n S: 0.00002380\n");
    
    if(decodeOpt==1){
      Tree tree = parser.viterbiParse();
      assertEquals(tree.toString(), "( (S (NP ( the) ( dog)) (VP ( bites) (NP ( a) ( cat)))))");
    }
    
    if(insideOutsideOpt>0){
      assertEquals(parser.dumpOutsideChart(), "# Outside chart snapshot\ncell 0-1\n Det: 0.00008330\n N: 0.00008330\ncell 1-2\n Det: 0.00008330\n N: 0.00008330\ncell 2-3\n Det: 0.00004165\n N: 0.00004165\n V: 0.00008330\ncell 3-4\n Det: 0.00008330\n N: 0.00008330\ncell 4-5\n Det: 0.00004165\n N: 0.00004165\n V: 0.00008330\ncell 0-2\n NP: 0.00116618\ncell 2-4\n NP: 0.00058309\ncell 3-5\n NP: 0.00058309\ncell 2-5\n VP: 0.02040815\ncell 0-5\n : 1.00000000\n S: 1.00000000\n");
      
      List<String> inputSentences = new ArrayList<String>();
      inputSentences.add("the cat bites a dog");
      inputSentences.add("a cat gives the dog a bone");
      inputSentences.add("the dog gives a cat the bone");
      inputSentences.add("a dog bites a bone");
      inputSentences.add("the dog bites");
      parser.parseSentences(inputSentences);
      assertEquals(parser.sprintExpectedCounts(), "# Expected counts\n6.000000 S->[NP VP]\n6.500000 NP->[Det N]\n6.500000 NP->[N Det]\n1.000000 VP->[V]\n1.500000 VP->[V NP]\n1.500000 VP->[NP V]\n1.000000 VP->[V NP NP]\n1.000000 VP->[NP NP V]\n3.000000 Det->[_the]\n3.000000 N->[_the]\n3.500000 Det->[_a]\n3.500000 N->[_a]\n2.750000 Det->[_dog]\n2.750000 N->[_dog]\n0.500000 V->[_dog]\n1.750000 Det->[_cat]\n1.750000 N->[_cat]\n0.500000 V->[_cat]\n0.750000 Det->[_bone]\n0.750000 N->[_bone]\n1.500000 V->[_bone]\n0.750000 Det->[_bites]\n0.750000 N->[_bites]\n2.500000 V->[_bites]\n0.500000 Det->[_gives]\n0.500000 N->[_gives]\n1.000000 V->[_gives]\n");
    }    
  }
  
  public void testMarkGrammarIO(){
    rootSymbol = "S";
    initParserFromFile(markGrammarFile);
    
    List<String> inputSentences = new ArrayList<String>();
    inputSentences.add("the dog bites a cat");
    inputSentences.add("the cat bites a dog");
    inputSentences.add("a cat gives the dog a bone");
    inputSentences.add("the dog gives a cat the bone");
    inputSentences.add("a dog bites a bone");
    inputSentences.add("the dog bites");
    
    if(insideOutsideOpt>0){
      List<Double> sumNegLogProbList = parser.insideOutside(inputSentences);
      assertEquals(parser.sprintExpectedCounts(), "# Expected counts\n6.000000 S->[NP VP]\n6.500000 NP->[Det N]\n6.500000 NP->[N Det]\n1.000000 VP->[V]\n3.000000 VP->[V NP]\n2.000000 VP->[V NP NP]\n3.000000 Det->[_the]\n3.000000 N->[_the]\n3.500000 Det->[_a]\n3.500000 N->[_a]\n3.000000 Det->[_dog]\n3.000000 N->[_dog]\n2.000000 Det->[_cat]\n2.000000 N->[_cat]\n1.500000 Det->[_bone]\n1.500000 N->[_bone]\n4.000000 V->[_bites]\n2.000000 V->[_gives]\n");
      
      assertEquals(sumNegLogProbList.size()==9, true);
      assertEquals(68.46002594157635, sumNegLogProbList.get(0), 1e-5);
      assertEquals(58.5105558430655, sumNegLogProbList.get(1), 1e-5);
      assertEquals(55.22092447712423, sumNegLogProbList.get(2), 1e-5);
      assertEquals(53.7010153144059, sumNegLogProbList.get(3), 1e-5);
      assertEquals(52.26369575985821, sumNegLogProbList.get(4), 1e-5);
      assertEquals(50.75935400505649, sumNegLogProbList.get(5), 1e-5);
      assertEquals(50.63455865171508, sumNegLogProbList.get(6), 1e-5);
      assertEquals(50.63452160196378, sumNegLogProbList.get(7), 1e-5);
      assertEquals(50.634521601963776, sumNegLogProbList.get(8), 1e-5);
    }
  }

  public void testBasicUnary(){
    initParserFromString(basicUnaryGrammarString);
    
    String inputSentence = "a b";
    System.err.println("\n### Run test parsing with string \"" + inputSentence + "\"");
    List<List<Double>> resultLists = parser.parseSentence(inputSentence);
    assertEquals(resultLists.size(), 4);
    List<Double> surprisalList = resultLists.get(0);
    List<Double> stringLogProbList = resultLists.get(3);
    List<Double> stringProbList = new ArrayList<Double>();
    for(double logProb : stringLogProbList){
      stringProbList.add(Math.exp(logProb));
    }
    
    
    for (int i = 0; i < surprisalList.size(); i++) {
      System.err.println(i + "\t" + surprisalList.get(i));
    }
    
    assertEquals(surprisalList.size(), 2);
    assertEquals(0.0, surprisalList.get(0), 1e-5);
    assertEquals(0.0, surprisalList.get(1), 1e-5);
    
    assertEquals(stringProbList.size(), 2);
    assertEquals(0.0, stringProbList.get(0), 1e-5);
    assertEquals(1.0, stringProbList.get(1), 1e-5);
  }
  
  public String getLeftInfiniteGrammar(double p){
    double q = 1-p;
    return "ROOT->[X] : " + p + "\n" +
    "ROOT->[ROOT X] : " + q + "\n" +
    "X->[_x] : 1.0\n";
  }
  
  public void testLeftInfiniteGrammar(){
    double p = 0.1;
    double q = 1-p;
    initParserFromString(getLeftInfiniteGrammar(p));
    
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
    List<Double> stringLogProbList = resultLists.get(3);
    List<Double> stringProbList = new ArrayList<Double>();
    for(double logProb : stringLogProbList){
      stringProbList.add(Math.exp(logProb));
    }
    
    assert(surprisalList.size() == numSymbols);
    
    // string x: string prob = p, prefix prob = 1.0, surprisal = -log(1)=0
    assertEquals(0, surprisalList.get(0), 1e-10);
    
    
    assert(stringProbList.size()==numSymbols);
    assertEquals(p, stringProbList.get(0), 1e-10);
    
    for (int i = 1; i < numSymbols; i++) {
      // strings of (i+1) x: string prob = p*q^i, prefix prob = q^i, surprisal = -log(q)
      assertEquals(-Math.log(q), surprisalList.get(i), 1e-10);
      if(!isScaling){
        assertEquals(p*Math.pow(q, i), stringProbList.get(i), 1e-10);
      }
    }
  }
  
  public void testLeftInfiniteIO(){
    double p=0.1;
    initParserFromString(getLeftInfiniteGrammar(p));
    
    List<String> inputSentences = new ArrayList<String>();
    inputSentences.add("x x x x x x x x x x");
    inputSentences.add("x x x x x");

    String inputSentence = inputSentences.get(0);
    System.err.println("\n### Run test parsing with string \"" + inputSentence + "\"");
    parser.parseSentence(inputSentence);

//    System.err.println(parser.dumpInsideChart());
//    System.err.println(parser.dumpOutsideChart());
    assertEquals(parser.dumpInsideChart(), "# Inside chart snapshot\ncell 0-1\n : 0.10000000\n X: 1.00000000\ncell 1-2\n X: 1.00000000\ncell 2-3\n X: 1.00000000\ncell 3-4\n X: 1.00000000\ncell 4-5\n X: 1.00000000\ncell 5-6\n X: 1.00000000\ncell 6-7\n X: 1.00000000\ncell 7-8\n X: 1.00000000\ncell 8-9\n X: 1.00000000\ncell 9-10\n X: 1.00000000\ncell 0-2\n : 0.09000000\n ROOT: 0.09000000\ncell 0-3\n : 0.08100000\n ROOT: 0.08100000\ncell 0-4\n : 0.07290000\n ROOT: 0.07290000\ncell 0-5\n : 0.06561000\n ROOT: 0.06561000\ncell 0-6\n : 0.05904900\n ROOT: 0.05904900\ncell 0-7\n : 0.05314410\n ROOT: 0.05314410\ncell 0-8\n : 0.04782969\n ROOT: 0.04782969\ncell 0-9\n : 0.04304672\n ROOT: 0.04304672\ncell 0-10\n : 0.03874205\n ROOT: 0.03874205\n");
    if(insideOutsideOpt>0){
      assertEquals(parser.dumpOutsideChart(), "# Outside chart snapshot\ncell 0-1\n X: 0.03874205\ncell 1-2\n X: 0.03874205\ncell 2-3\n X: 0.03874205\ncell 3-4\n X: 0.03874205\ncell 4-5\n X: 0.03874205\ncell 5-6\n X: 0.03874205\ncell 6-7\n X: 0.03874205\ncell 7-8\n X: 0.03874205\ncell 8-9\n X: 0.03874205\ncell 9-10\n X: 0.03874205\ncell 0-2\n ROOT: 0.43046721\ncell 0-3\n ROOT: 0.47829690\ncell 0-4\n ROOT: 0.53144100\ncell 0-5\n ROOT: 0.59049000\ncell 0-6\n ROOT: 0.65610000\ncell 0-7\n ROOT: 0.72900000\ncell 0-8\n ROOT: 0.81000000\ncell 0-9\n ROOT: 0.90000000\ncell 0-10\n : 1.00000000\n ROOT: 1.00000000\n");
    
      parser.parseSentences(inputSentences.subList(1, inputSentences.size()));
//      System.err.println(parser.sprintExpectedCounts());
      assertEquals(parser.sprintExpectedCounts(), "# Expected counts\n2.000000 ROOT->[X]\n13.000000 ROOT->[ROOT X]\n15.000000 X->[_x]\n");
    }
  }
  
  public String getCatalanGrammar(double p){
    double q = 1-p;
    return "ROOT->[X] : " + p + "\n" +
      "ROOT->[ROOT ROOT] : " + q + "\n" +
      "X->[_x] : 1.0\n";
  }
  
  public void testCatalanGrammar(){
    double p=0.1;
    double q = 1-p;
    initParserFromString(getCatalanGrammar(p));
    
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
    List<Double> stringLogProbList = resultLists.get(3);
    List<Double> stringProbList = new ArrayList<Double>();
    for(double logProb : stringLogProbList){
      stringProbList.add(Math.exp(logProb));
    }
    
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
      // strings of (i+1) x: string prob[i+1] = (c[2i][i]/(i+1))*p^(i+1)*q^i
      // prefix prob[i+1] = prefix prob[i]-string prob[i]
      double currentStringProb = (c[2*i][i]/(i+1.0))*Math.pow(p,i+1)*Math.pow(q, i);
      assertEquals(currentStringProb, stringProbList.get(i), 1e-10);
      
      
      
      double currentPrefixProb = prevPrefixProb - prevStringProb;
      assertEquals(-Math.log(currentPrefixProb/prevPrefixProb), surprisalList.get(i), 1e-10);
      
      // update
      prevPrefixProb = currentPrefixProb;
      prevStringProb = currentStringProb;
      
      totalStringProb += currentStringProb;
      System.err.println(totalStringProb);
    }
  }
  
  public void testCatalanIO(){
    double p=0.1;
    initParserFromString(getCatalanGrammar(p));
    
    List<String> inputSentences = new ArrayList<String>();
    inputSentences.add("x x x x x x x x x x");
    inputSentences.add("x x x x x");

    String inputSentence = inputSentences.get(0);
    System.err.println("\n### Run test parsing with string \"" + inputSentence + "\"");
    parser.parseSentence(inputSentence);

    assertEquals(parser.dumpInsideChart(), "# Inside chart snapshot\ncell 0-1\n : 0.10000000\n X: 1.00000000\ncell 1-2\n X: 1.00000000\ncell 2-3\n X: 1.00000000\ncell 3-4\n X: 1.00000000\ncell 4-5\n X: 1.00000000\ncell 5-6\n X: 1.00000000\ncell 6-7\n X: 1.00000000\ncell 7-8\n X: 1.00000000\ncell 8-9\n X: 1.00000000\ncell 9-10\n X: 1.00000000\ncell 0-2\n : 0.00900000\n ROOT: 0.00900000\ncell 1-3\n ROOT: 0.00900000\ncell 2-4\n ROOT: 0.00900000\ncell 3-5\n ROOT: 0.00900000\ncell 4-6\n ROOT: 0.00900000\ncell 5-7\n ROOT: 0.00900000\ncell 6-8\n ROOT: 0.00900000\ncell 7-9\n ROOT: 0.00900000\ncell 8-10\n ROOT: 0.00900000\ncell 0-3\n : 0.00162000\n ROOT: 0.00162000\ncell 1-4\n ROOT: 0.00162000\ncell 2-5\n ROOT: 0.00162000\ncell 3-6\n ROOT: 0.00162000\ncell 4-7\n ROOT: 0.00162000\ncell 5-8\n ROOT: 0.00162000\ncell 6-9\n ROOT: 0.00162000\ncell 7-10\n ROOT: 0.00162000\ncell 0-4\n : 0.00036450\n ROOT: 0.00036450\ncell 1-5\n ROOT: 0.00036450\ncell 2-6\n ROOT: 0.00036450\ncell 3-7\n ROOT: 0.00036450\ncell 4-8\n ROOT: 0.00036450\ncell 5-9\n ROOT: 0.00036450\ncell 6-10\n ROOT: 0.00036450\ncell 0-5\n : 0.00009185\n ROOT: 0.00009185\ncell 1-6\n ROOT: 0.00009185\ncell 2-7\n ROOT: 0.00009185\ncell 3-8\n ROOT: 0.00009185\ncell 4-9\n ROOT: 0.00009185\ncell 5-10\n ROOT: 0.00009185\ncell 0-6\n : 0.00002480\n ROOT: 0.00002480\ncell 1-7\n ROOT: 0.00002480\ncell 2-8\n ROOT: 0.00002480\ncell 3-9\n ROOT: 0.00002480\ncell 4-10\n ROOT: 0.00002480\ncell 0-7\n : 0.00000702\n ROOT: 0.00000702\ncell 1-8\n ROOT: 0.00000702\ncell 2-9\n ROOT: 0.00000702\ncell 3-10\n ROOT: 0.00000702\ncell 0-8\n : 0.00000205\n ROOT: 0.00000205\ncell 1-9\n ROOT: 0.00000205\ncell 2-10\n ROOT: 0.00000205\ncell 0-9\n : 0.00000062\n ROOT: 0.00000062\ncell 1-10\n ROOT: 0.00000062\ncell 0-10\n : 0.00000019\n ROOT: 0.00000019\n");
    if(insideOutsideOpt>0){
      assertEquals(parser.dumpOutsideChart(), "# Outside chart snapshot\ncell 0-1\n X: 0.00000019\ncell 1-2\n X: 0.00000019\ncell 2-3\n X: 0.00000019\ncell 3-4\n X: 0.00000019\ncell 4-5\n X: 0.00000019\ncell 5-6\n X: 0.00000019\ncell 6-7\n X: 0.00000019\ncell 7-8\n X: 0.00000019\ncell 8-9\n X: 0.00000019\ncell 9-10\n X: 0.00000019\ncell 0-2\n ROOT: 0.00000616\ncell 1-3\n ROOT: 0.00000616\ncell 2-4\n ROOT: 0.00000616\ncell 3-5\n ROOT: 0.00000616\ncell 4-6\n ROOT: 0.00000616\ncell 5-7\n ROOT: 0.00000616\ncell 6-8\n ROOT: 0.00000616\ncell 7-9\n ROOT: 0.00000616\ncell 8-10\n ROOT: 0.00000616\ncell 0-3\n ROOT: 0.00002052\ncell 1-4\n ROOT: 0.00002052\ncell 2-5\n ROOT: 0.00002052\ncell 3-6\n ROOT: 0.00002052\ncell 4-7\n ROOT: 0.00002052\ncell 5-8\n ROOT: 0.00002052\ncell 6-9\n ROOT: 0.00002052\ncell 7-10\n ROOT: 0.00002052\ncell 0-4\n ROOT: 0.00007015\ncell 1-5\n ROOT: 0.00007015\ncell 2-6\n ROOT: 0.00007015\ncell 3-7\n ROOT: 0.00007015\ncell 4-8\n ROOT: 0.00007015\ncell 5-9\n ROOT: 0.00007015\ncell 6-10\n ROOT: 0.00007015\ncell 0-5\n ROOT: 0.00024801\ncell 1-6\n ROOT: 0.00024801\ncell 2-7\n ROOT: 0.00024801\ncell 3-8\n ROOT: 0.00024801\ncell 4-9\n ROOT: 0.00024801\ncell 5-10\n ROOT: 0.00024801\ncell 0-6\n ROOT: 0.00091854\ncell 1-7\n ROOT: 0.00091854\ncell 2-8\n ROOT: 0.00091854\ncell 3-9\n ROOT: 0.00091854\ncell 4-10\n ROOT: 0.00091854\ncell 0-7\n ROOT: 0.00364500\ncell 1-8\n ROOT: 0.00364500\ncell 2-9\n ROOT: 0.00364500\ncell 3-10\n ROOT: 0.00364500\ncell 0-8\n ROOT: 0.01620000\ncell 1-9\n ROOT: 0.01620000\ncell 2-10\n ROOT: 0.01620000\ncell 0-9\n ROOT: 0.09000000\ncell 1-10\n ROOT: 0.09000000\ncell 0-10\n : 1.00000000\n ROOT: 1.00000000\n");
    
      parser.parseSentences(inputSentences.subList(1, inputSentences.size()));
      assertEquals(parser.sprintExpectedCounts(), "# Expected counts\n15.000000 ROOT->[X]\n13.000000 ROOT->[ROOT ROOT]\n15.000000 X->[_x]\n");
    }
  }
  
  public void testParsing1(){
    initParserFromString(grammarString);
    
    String inputSentence = "b c";
    System.err.println("\n### Run test parsing with string \"" + inputSentence + "\"");
    List<List<Double>> resultLists = parser.parseSentence(inputSentence);
    assertEquals(resultLists.size(), 4);
    List<Double> surprisalList = resultLists.get(0);
    List<Double> synSurprisalList = resultLists.get(1);
    List<Double> lexSurprisalList = resultLists.get(2);
    List<Double> stringLogProbList = resultLists.get(3);
    List<Double> stringProbList = new ArrayList<Double>();
    for(double logProb : stringLogProbList){
      stringProbList.add(Math.exp(logProb));
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
    }
    
    assertEquals(stringProbList.toString(), "[0.0, 0.405]");
    
  }
  
  public void testParsing2(){
    initParserFromString(grammarString);
    
    String inputSentence = "a";
    System.err.println("\n### Run test parsing with string \"" + inputSentence + "\"");
    List<List<Double>> resultLists = parser.parseSentence(inputSentence);
    assertEquals(resultLists.size(), 4);
    List<Double> surprisalList = resultLists.get(0);
    
    assertEquals(surprisalList.size(), 1);
    assertEquals(2.3025851249694824, surprisalList.get(0), 1e-5);
    
    List<Double> synSurprisalList = resultLists.get(1);
    List<Double> lexSurprisalList = resultLists.get(2);
    List<Double> stringLogProbList = resultLists.get(3);
    List<Double> stringProbList = new ArrayList<Double>();
    for(double logProb : stringLogProbList){
      stringProbList.add(Math.exp(logProb));
    }
    
    if(!isScaling){
      assertEquals(synSurprisalList.size(), 1);
      assertEquals(1.1102230246251565E-16, synSurprisalList.get(0), 1e-5);
      assertEquals(lexSurprisalList.toString(), "[2.3025850929940455]");
    }
    
    assertEquals(stringProbList.toString(), "[0.0]");
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
    
    
    List<Double> synSurprisalList = resultLists.get(1);
    List<Double> lexSurprisalList = resultLists.get(2);
    List<Double> stringLogProbList = resultLists.get(3);
    List<Double> stringProbList = new ArrayList<Double>();
    for(double logProb : stringLogProbList){
      stringProbList.add(Math.exp(logProb));
    }

    if(!isScaling){
      assertEquals(synSurprisalList.size(), 2);
      assertEquals(0.6931471805599453, synSurprisalList.get(0), 1e-5);
      assertEquals(1.1102230246251565E-16, synSurprisalList.get(1), 1e-5);
      assertEquals(lexSurprisalList.toString(), "[0.2231435513142097, 2.3025850929940455]");
    }
    
    assertEquals(stringProbList.toString(), "[0.0, 0.04000000000000001]");
    
  }
  
  public void testRecursiveGrammarIO(){
    initParserFromString(recursiveGrammarString);
    
    String inputSentence = "d d b c";
    System.err.println("\n### Run test parsing with string \"" + inputSentence + "\"");
    List<List<Double>> resultLists = parser.parseSentence(inputSentence);
    assertEquals(resultLists.size(), 4);
    List<Double> surprisalList = resultLists.get(0);
  
    assertEquals(surprisalList.size(), 4);
    assertEquals(0.8649974339457559, surprisalList.get(0), 1e-5);
    assertEquals(3.167582526939802, surprisalList.get(1), 1e-5);
    assertEquals(0.17185025338581103, surprisalList.get(2), 1e-5);
    assertEquals(2.052896986215565, surprisalList.get(3), 1e-5);
    
    List<Double> synSurprisalList = resultLists.get(1);
    List<Double> lexSurprisalList = resultLists.get(2);
    List<Double> stringLogProbList = resultLists.get(3);
    List<Double> stringProbList = new ArrayList<Double>();
    for(double logProb : stringLogProbList){
      stringProbList.add(Math.exp(logProb));
    }

    if(!isScaling){
      assertEquals(synSurprisalList.toString(), "[0.6418538861723948, 2.9444389791664407, -0.05129329438755048, 1.9475364707998972]");
      assertEquals(lexSurprisalList.size(), 4);
      assertEquals(0.2231435513142097, lexSurprisalList.get(0), 1e-5);
      assertEquals(0.2231435513142097, lexSurprisalList.get(1), 1e-5);
      assertEquals(0.2231435513142097, lexSurprisalList.get(2), 1e-5);
      assertEquals(0.10536051565782628, lexSurprisalList.get(3), 1e-5);
    }
    
    assertEquals(stringProbList.size(), 4);
    assertEquals(0.0, stringProbList.get(0), 1e-5);
    assertEquals(0.0, stringProbList.get(1), 1e-5);
    assertEquals(0.012800000000000008, stringProbList.get(2), 1e-5);
    assertEquals(0.0017280000000000019, stringProbList.get(3), 1e-5);
    
    assertEquals(parser.dumpInsideChart(), "# Inside chart snapshot\ncell 0-1\n D: 0.80000000\ncell 1-2\n D: 0.80000000\ncell 2-3\n B: 0.80000000\ncell 3-4\n C: 0.90000000\ncell 1-3\n A: 0.32000000\ncell 2-4\n A: 0.36000000\ncell 0-3\n : 0.01280000\n A: 0.01280000\ncell 1-4\n A: 0.02880000\ncell 0-4\n : 0.00172800\n A: 0.00172800\n");
    if(insideOutsideOpt>0){
      assertEquals(parser.dumpOutsideChart(), "# Outside chart snapshot\ncell 0-1\n D: 0.00216000\ncell 1-2\n D: 0.00216000\ncell 2-3\n B: 0.00216000\ncell 3-4\n C: 0.00192000\ncell 1-3\n A: 0.00360000\ncell 2-4\n A: 0.00160000\ncell 0-3\n A: 0.04500000\ncell 1-4\n A: 0.04000000\ncell 0-4\n : 1.00000000\n A: 1.00000000\n");
      assertEquals(parser.sprintExpectedCounts(), "# Expected counts\n1.000000 ROOT->[A]\n1.000000 A->[B C]\n2.000000 A->[D B]\n2.000000 B->[A]\n1.000000 B->[_b]\n1.000000 C->[_c]\n2.000000 D->[_d]\n");
    }
  }
  
  
  public void testWSJ500AG(){
    initParserFromFile(wsj500AG);
    
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
    List<Double> stringLogProbList = resultLists.get(3);
    List<Double> stringProbList = new ArrayList<Double>();
    for(double logProb : stringLogProbList){
      stringProbList.add(Math.exp(logProb));
    }

    
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
  
  public void testWSJ500IO(){
    initParserFromFile(wsj500);
    
    String inputSentence = "The two young sea-lions took not the slightest interest in our arrival .";
    System.err.println("\n### Run test parsing with string \"" + inputSentence + "\"");
    List<List<Double>> resultLists = parser.parseSentence(inputSentence);
    assertEquals(resultLists.size(), 4);
    List<Double> surprisalList = resultLists.get(0);
    List<Double> stringLogProbList = resultLists.get(3);
    List<Double> stringProbList = new ArrayList<Double>();
    for(double logProb : stringLogProbList){
      stringProbList.add(Math.exp(logProb));
    }

    
    // Note: scores here are slightly different from those of previous version
    // that is because RoarkBaseLexicon.score returns float instead of double
    assertEquals(surprisalList.size(), 13);
    assertEquals(4.297369612568129, surprisalList.get(0), 1e-5);
    assertEquals(7.295271761862825, surprisalList.get(1), 1e-5);
    assertEquals(7.235382153987473, surprisalList.get(2), 1e-5);
    assertEquals(6.579374855858557, surprisalList.get(3), 1e-5);
    assertEquals(8.550845844480936, surprisalList.get(4), 1e-5);
    assertEquals(5.652534988855393, surprisalList.get(5), 1e-5);
    assertEquals(4.375939791924388, surprisalList.get(6), 1e-5);
    assertEquals(4.054906889124851, surprisalList.get(7), 1e-5);
    assertEquals(7.567168879152675, surprisalList.get(8), 1e-5);
    assertEquals(3.7381719867359706, surprisalList.get(9), 1e-5);
    assertEquals(8.101975429529261, surprisalList.get(10), 1e-5);
    assertEquals(4.346770625309475, surprisalList.get(11), 1e-5);
    assertEquals(3.413711206475597, surprisalList.get(12), 1e-5);

    assertEquals(stringProbList.size(), 13);
    assertEquals(2.4538477180526925E-5, stringProbList.get(0), 1e-5);
    assertEquals(1.6970900624754254E-7, stringProbList.get(1), 1e-5);
    assertEquals(2.8755568614102028E-11, stringProbList.get(2), 1e-5);
    assertEquals(3.658265478750189E-13, stringProbList.get(3), 1e-5);
    assertEquals(6.186894611508017E-17, stringProbList.get(4), 1e-5);
    assertEquals(1.1894000273453555E-19, stringProbList.get(5), 1e-5);
    assertEquals(1.184450111083609E-21, stringProbList.get(6), 1e-5);
    assertEquals(1.8687442504202126E-22, stringProbList.get(7), 1e-5);
    assertEquals(1.289941394650639E-25, stringProbList.get(8), 1e-5);
    assertEquals(2.316801041738345E-29, stringProbList.get(9), 1e-5);
    assertEquals(0.0, stringProbList.get(10), 1e-5);
    assertEquals(3.405533303307524E-33, stringProbList.get(11), 1e-5);
    assertEquals(1.8818812758090438E-33, stringProbList.get(12), 1e-5);
    
    if(insideOutsideOpt>0){
      assertEquals(parser.sprintExpectedCounts(), "# Expected counts\n0.000047 ROOT->[ADJP]\n0.000290 ROOT->[SINV]\n0.000488 ROOT->[X]\n0.998773 ROOT->[S]\n0.000000 ROOT->[SQ]\n0.000096 ROOT->[NP]\n0.000302 ROOT->[FRAG]\n0.000004 ROOT->[SBARQ]\n0.000000 WHPP->[IN WHNP]\n0.000488 X->[NP PP .]\n0.001412 NX->[NN NN]\n0.003223 NX->[NN]\n0.000073 NX->[VBG NN]\n0.000030 NX->[NNP NN]\n0.000111 NX->[NNP]\n0.000335 NX->[NNS]\n0.000000 S->[NP , ADVP , VP .]\n0.000000 S->[S VP]\n0.001259 S->[NP ADVP VP .]\n0.000000 S->[S : S : S .]\n0.000000 S->[ADVP , NP VP .]\n0.000000 S->[PP ADVP NP VP]\n0.000023 S->[VP .]\n0.000000 S->[VP ,]\n0.000000 S->[S , S]\n0.000015 S->[NP ADVP VP]\n0.000000 S->[NP , NP VP .]\n0.001566 S->[NP ADJP]\n0.000205 S->[NP ADVP]\n0.000031 S->[NP VP S]\n0.000390 S->[NP NP]\n0.012594 S->[NP VP]\n0.002583 S->[NP PP]\n0.000000 S->[SBAR PRN NP VP .]\n0.003361 S->[NP ADJP VP .]\n0.000008 S->[NP NP VP]\n0.968799 S->[NP VP .]\n0.001547 S->[NP]\n0.002271 S->[VP]\n0.000026 S->[VP PP]\n0.000126 S->[ADJP]\n0.000000 S->[NP VP :]\n0.004123 S->[ADVP NP VP .]\n0.000000 S->[ADVP , NP VP]\n0.000015 S->[S CC S .]\n0.000039 S->[ADVP VP]\n0.000004 S->[: VP]\n0.000000 S->[`` NP '' VP]\n0.000434 S->[S : S .]\n0.000000 S->[NP VP ,]\n0.000000 S->[PP VP]\n0.000000 S->[CC ADVP NP ADVP VP .]\n0.000000 S->[SBAR , NP ADVP VP .]\n0.000001 S->[S : S]\n0.000000 S->[-LRB- VP -RRB-]\n0.000000 S->[SBAR , NP VP]\n0.000597 S->[ADVP NP VP]\n0.000000 S->[NP VBZ ADVP ADVP VP]\n0.000000 S->[CC NP VP .]\n0.000005 S->[PP NP VP .]\n0.000002 S->[`` VP]\n0.001152 S->[NP NP VP .]\n0.000121 S->[S NP VP .]\n0.000000 S->[CC NP VP]\n0.000000 S->[S , NP PP VP .]\n0.000167 S->[: VP .]\n0.000000 S->[CC NP ADVP VP]\n0.000000 S->[`` NP VP .]\n0.000000 S->[S , NP VP .]\n0.000085 S->[S VP .]\n0.000000 S->[S NP VP]\n0.000004 S->[NP `` VP]\n0.000011 S->[NP PP VP .]\n0.000001 S->[NP : VP]\n0.019368 S->[NP VP PP .]\n0.000000 S->[NP PP VP]\n0.000001 S->[S CC S]\n0.000000 S->[SBAR , NP VP .]\n0.000024 NP->[DT NNP]\n0.000000 NP->[`` S '']\n0.000046 NP->[NNP]\n0.000415 NP->[PRP]\n0.000011 NP->[DT VBG]\n0.000000 NP->[NP CC NP POS]\n0.000021 NP->[DT RB]\n0.000325 NP->[DT NNS]\n0.000017 NP->[NNPS]\n0.002377 NP->[DT]\n0.000001 NP->[NP CC NP .]\n0.008986 NP->[NN]\n0.000581 NP->[CD]\n0.022386 NP->[DT CD]\n0.000108 NP->[JJ]\n0.000064 NP->[DT NNPS]\n0.000133 NP->[DT JJ]\n0.031463 NP->[DT NN]\n0.000000 NP->[DT ADJP QP]\n0.000002 NP->[NP : NP .]\n0.000003 NP->[VBZ]\n0.000132 NP->[NNS]\n0.000093 NP->[DT JJR]\n0.000005 NP->[VBG]\n0.007624 NP->[RB]\n0.000070 NP->[PDT]\n0.000090 NP->[JJS]\n0.000700 NP->[DT JJS]\n0.000390 NP->[EX]\n0.000026 NP->[RBR]\n0.000036 NP->[JJR]\n0.000068 NP->[S]\n0.000025 NP->[ADJP]\n0.058329 NP->[DT JJS NN]\n0.001257 NP->[QP]\n0.000030 NP->[DT ADJP NNS]\n0.000001 NP->[ADVP NNP NN]\n0.012646 NP->[DT ADJP NN]\n0.000001 NP->[DT NNS S]\n0.000011 NP->[JJR NN]\n0.000005 NP->[NP PRP$ NNS]\n0.000656 NP->[JJ NN]\n0.000028 NP->[JJ NNP]\n0.001610 NP->[JJ NNS]\n0.000000 NP->[JJ NNS SBAR]\n0.000260 NP->[NN NN]\n0.000022 NP->[NP NNP NN]\n0.000588 NP->[DT NP]\n0.000164 NP->[DT ADJP]\n0.010578 NP->[ADVP DT NN]\n0.000000 NP->[ADJP VBN NN]\n0.000000 NP->[NP `` NP '']\n0.001460 NP->[DT NX]\n0.000011 NP->[NP : NP]\n0.000000 NP->[NP : S]\n0.000000 NP->[NP : PP]\n0.000000 NP->[NN S]\n0.000001 NP->[DT ADJP JJ NN]\n0.000000 NP->[NP : SBARQ]\n0.004610 NP->[DT VBG NN]\n0.000007 NP->[NP NNS]\n0.000002 NP->[DT ADJP NN NN]\n0.000042 NP->[NP , NP]\n0.000001 NP->[NP , VP]\n0.000000 NP->[NP , SBAR]\n0.000000 NP->[NP , PP]\n0.002961 NP->[NP ADJP]\n0.000001 NP->[NNS NN]\n0.003616 NP->[NP NP]\n0.000016 NP->[NP S]\n0.000363 NP->[NP SBAR]\n0.001289 NP->[NP VP]\n0.000000 NP->[NP QP]\n0.442874 NP->[NP PP]\n0.000000 NP->[NP , ADVP]\n0.000000 NP->[NP PRN]\n0.000308 NP->[NP NX]\n0.000003 NP->[JJ NX]\n0.000029 NP->[CD NN]\n0.000203 NP->[DT NNS NN]\n0.346680 NP->[RB DT JJ NN]\n0.000000 NP->[NP , NP ,]\n0.000101 NP->[NP CC NP]\n0.000052 NP->[QP NN]\n0.000000 NP->[NP , VP ,]\n0.008702 NP->[RB DT]\n0.000000 NP->[`` S]\n0.003775 NP->[NP NN NN]\n0.000077 NP->[DT NN S]\n0.000020 NP->[DT NN SBAR]\n0.000000 NP->[NP , SBAR ,]\n0.000000 NP->[`` ADJP]\n0.000001 NP->[NP NN S]\n0.000003 NP->[NP JJ ADJP NN]\n0.018218 NP->[QP JJ NNS]\n0.000000 NP->[NP NP , ADVP]\n0.000000 NP->[NP : NP : NP .]\n0.186913 NP->[DT JJ NN]\n0.002243 NP->[DT CD NN]\n0.006928 NP->[NP JJ NN]\n0.000000 NP->[NNS S]\n0.005771 NP->[NP JJ NNS]\n0.000001 NP->[NP NNP]\n0.000000 NP->[NP PP , PP]\n0.000000 NP->[NP PP , SBAR]\n0.000000 NP->[NP PP , VP]\n0.041631 NP->[NP NN]\n0.000000 NP->[NP PP POS]\n0.000000 NP->[NP PP '']\n0.000024 NP->[NP CD NN]\n0.000446 NP->[CD JJ NNS]\n0.000019 NP->[NP VP PP]\n0.000001 NP->[JJ NN SBAR .]\n0.000001 NP->[NP VP :]\n0.000000 NP->[NP , NP , SBAR]\n0.000306 NP->[NP NP PP]\n0.003378 NP->[DT NNP NN]\n0.000011 NP->[ADVP QP]\n0.000004 NP->[VBG NN]\n0.000001 NP->[QP NX]\n0.000000 NP->[NP NNS S]\n0.286342 NP->[DT NN NN]\n0.000243 NP->[ADJP JJ NN]\n0.000000 NP->[NP CC ADVP NP]\n0.000005 NP->[PDT NP]\n0.000000 NP->[DT ADJP , ADJP NN]\n0.000000 NP->[DT ADJP VBN NN]\n0.017499 NP->[DT JJR NN]\n0.000030 NP->[ADJP NN]\n0.000062 NP->[ADJP NNS]\n0.000000 NP->[NP : NP : NP]\n0.000000 NP->[NP , NP CC NP]\n0.014341 NP->[RB DT NN]\n0.000000 NP->[NP '' PP]\n0.000013 NP->[NP '' NX]\n0.050577 NP->[PRP$ NNS]\n0.842617 NP->[PRP$ NN]\n0.005377 NP->[RB DT ADJP]\n0.103520 NP->[PRP$ JJ]\n0.000000 NP->[JJ NN SBAR]\n0.000015 NP->[VBN NN]\n0.000000 NP->[NP PP PP PP]\n0.000302 NP->[NP ADJP NNS]\n0.000000 NP->[NP PRN PP]\n0.000000 NP->[NP PRN SBAR]\n0.000000 NP->[NP PRN :]\n0.628504 NP->[DT CD JJ NNS]\n0.328455 NP->[DT CD JJ NN]\n0.000391 NP->[NP ADJP NN]\n0.005518 NP->[DT VBN NN]\n0.000000 NP->[NP PP ADVP]\n0.003400 NP->[PRP$ NX]\n0.000000 NP->[NP PP SBAR]\n0.000000 NP->[NP PP VP]\n0.000039 NP->[NP PP PP]\n0.000001 NP->[NNP NN]\n0.000004 NP->[NP PP NP]\n0.000000 NP->[NP PP S]\n0.019795 QP->[DT CD]\n0.000000 PP->[TO NP NP]\n0.000006 PP->[TO NP PP]\n0.000000 PP->[PP CC PP]\n0.000013 PP->[VBN NP]\n0.000000 PP->[IN PP]\n0.978595 PP->[IN NP]\n0.000188 PP->[IN S]\n0.000009 PP->[IN SBAR]\n0.000008 PP->[VBG NP]\n0.016505 PP->[NP IN NP]\n0.000000 PP->[PP CC ADJP NP]\n0.000001 PP->[IN ADJP]\n0.000000 PP->[PP PP NP]\n0.002140 PP->[ADVP IN NP]\n0.000000 PP->[IN ADVP]\n0.000000 PP->[IN NP '' PP]\n0.000263 PP->[JJ NP]\n0.000000 PP->[NP RB PP]\n0.000000 PP->[JJ IN NP]\n0.000057 PP->[IN]\n0.000000 PP->[IN NP CC NP]\n0.000000 PP->[VBG PP]\n0.000001 PP->[ADVP IN S]\n0.000156 PP->[RB PP]\n0.000484 PP->[RB]\n0.000137 PP->[TO NP]\n0.000014 PP->[PP PP]\n0.000000 PP->[JJ TO NP]\n0.000156 PP->[FW NP]\n0.000011 PP->[JJ IN S]\n0.000000 PP->[PP ADVP]\n0.000000 SBAR->[WHPP S]\n0.000000 SBAR->[WHNP S ,]\n0.005407 SBAR->[S]\n0.000123 SBAR->[WHNP S]\n0.000025 SBAR->[ADVP IN S]\n0.004388 SBAR->[RB S]\n0.000085 SBAR->[DT S]\n0.000322 SBAR->[IN S]\n0.000029 SBAR->[NP IN S]\n0.000044 SBAR->[WHADVP S]\n0.000011 SBAR->[SINV]\n0.000000 SBAR->[SBAR CC SBAR]\n0.000000 SBAR->[PP IN S]\n0.000000 VP->[VBG NP SBAR]\n0.000063 VP->[VBG NP PP]\n0.000000 VP->[VBG NP NP]\n0.000000 VP->[ADVP VBG]\n0.000000 VP->[VBG NP VP]\n0.000000 VP->[VBZ ADJP S]\n0.000000 VP->[VP CC VP , SBAR]\n0.000000 VP->[VBG NP S]\n0.000001 VP->[VBG NP ADVP]\n0.000000 VP->[VBZ ADJP ADVP]\n0.000803 VP->[VB NP]\n0.000015 VP->[VB NP ADVP]\n0.000055 VP->[VB S]\n0.000000 VP->[VB NP ADJP]\n0.000003 VP->[ADVP VBD]\n0.000002 VP->[VB PP]\n0.000000 VP->[VB NP PRT PP]\n0.000005 VP->[VB SBAR]\n0.000292 VP->[VB VP]\n0.000126 VP->[VBD NP PP PP]\n0.001366 VP->[VB NP PP]\n0.000000 VP->[VB NP SBAR]\n0.000000 VP->[VB NP VP]\n0.000000 VP->[VB NP NP]\n0.000000 VP->[VB NP S]\n0.000000 VP->[VBD NP PP S]\n0.000000 VP->[NNP]\n0.000079 VP->[VBD]\n0.000000 VP->[VBG PP]\n0.000001 VP->[VBG ADJP]\n0.000014 VP->[VBZ]\n0.000000 VP->[VBG ADVP]\n0.000004 VP->[VBG]\n0.002014 VP->[TO VP]\n0.000025 VP->[ADVP VB NP PP]\n0.000027 VP->[VB]\n0.000027 VP->[VBN]\n0.000000 VP->[VBZ NP , SBAR]\n0.000002 VP->[MD]\n0.000013 VP->[VBP]\n0.000000 VP->[VBZ NP , S]\n0.000005 VP->[VBG SBAR]\n0.000173 VP->[ADJP]\n0.000010 VP->[VBG VP]\n0.000000 VP->[VBP PP NP]\n0.000033 VP->[VBG NP]\n0.000045 VP->[VBG S]\n0.000000 VP->[VBG NP , ADVP]\n0.000004 VP->[VP CC VP PP]\n0.000000 VP->[ADVP VBZ NP]\n0.000000 VP->[VP CC VP NP]\n0.000000 VP->[VB NP ADVP ADVP]\n0.000261 VP->[ADVP VBD NP PP]\n0.000000 VP->[VBG PP PP]\n0.078066 VP->[VBD NP]\n0.000000 VP->[VBD NP , PP]\n0.000000 VP->[VBD NP , SBAR]\n0.000458 VP->[VBD VP]\n0.006939 VP->[VBD S]\n0.000162 VP->[VBD S NP PP]\n0.000000 VP->[VB PP ADVP]\n0.002485 VP->[VBD ADVP]\n0.000000 VP->[VBD S NP SBAR]\n0.000001 VP->[ADVP VBN]\n0.000000 VP->[VB PP SBAR]\n0.002598 VP->[VBD PP]\n0.008775 VP->[VBD SBAR]\n0.000000 VP->[VB S , S]\n0.000000 VP->[VB PP PP]\n0.001854 VP->[VBD ADJP]\n0.000000 VP->[VBD NP ,]\n0.042466 VP->[VBD ADJP NP PP]\n0.104009 VP->[VBD PRT NP PP]\n0.000000 VP->[VBD NP PP PP PP]\n0.000000 VP->[ADVP VBG NP]\n0.000001 VP->[VBD S S]\n0.000000 VP->[MD ADVP ADVP VP]\n0.000179 VP->[VBD S PP]\n0.000004 VP->[PP PP]\n0.000000 VP->[VBZ PP PP SBAR]\n0.000000 VP->[VP , VP , VP]\n0.296122 VP->[VBD NP PP]\n0.000598 VP->[VBD NP SBAR]\n0.000000 VP->[VBD S : SBAR]\n0.006962 VP->[VBD NP NP]\n0.000000 VP->[VBP VP , SBAR]\n0.001482 VP->[VBD NP S]\n0.002406 VP->[VBD NP ADVP]\n0.000000 VP->[VB ADVP]\n0.000042 VP->[VBZ NP PP]\n0.000000 VP->[VBZ NP S]\n0.000080 VP->[VB ADJP]\n0.000012 VP->[NN NP]\n0.000000 VP->[VB ADJP S]\n0.011931 VP->[NN PP]\n0.000000 VP->[VB ADJP SBAR]\n0.000000 VP->[VB ADJP PP]\n0.000000 VP->[VBN NP PP PP]\n0.000000 VP->[NN NP PP PP]\n0.000011 VP->[VBZ S PP]\n0.000000 VP->[VBP ADVP SBAR]\n0.000000 VP->[VBP ADVP VP]\n0.000000 VP->[VBP ADVP S]\n0.000000 VP->[PP VBD VP]\n0.000000 VP->[VBP ADVP ADJP]\n0.000013 VP->[VBD PP SBAR]\n0.000004 VP->[VBD PP PP]\n0.000000 VP->[NNP NP PRT]\n0.000000 VP->[VBN ADVP PP]\n0.000000 VP->[VBD NP NP , SBAR]\n0.000000 VP->[VBN ADVP VP]\n0.000000 VP->[ADVP VP CC VP]\n0.000000 VP->[VBN NP , S]\n0.000000 VP->[VBN NP , SBAR]\n0.000000 VP->[VBP NP S , PP]\n0.000002 VP->[VP : NP]\n0.000025 VP->[VBD NP ADVP PP]\n0.000000 VP->[VB NP NP PP]\n0.000000 VP->[VBN ADVP NP PRN]\n0.000102 VP->[VBD SBAR PP]\n0.000450 VP->[VBD PP NP]\n0.000001 VP->[ADVP VBG NP PP]\n0.000000 VP->[VBD RB VP]\n0.000000 VP->[VBN S SBAR]\n0.004694 VP->[VBD RB PP]\n0.000032 VP->[VBD RB ADJP]\n0.000002 VP->[VBN ADJP]\n0.000000 VP->[VBN ADVP]\n0.000000 VP->[VB NP PP S]\n0.000001 VP->[VBN PP]\n0.000000 VP->[MD ADVP VP]\n0.000000 VP->[VP , VP CC VP]\n0.000038 VP->[VBN NP]\n0.000000 VP->[VB NP PP SBAR]\n0.000043 VP->[VBN S]\n0.000005 VP->[VBN SBAR]\n0.000095 VP->[VBN VP]\n0.000000 VP->[VB NP PP NP]\n0.000000 VP->[ADVP VB PP]\n0.000003 VP->[ADVP VB NP]\n0.000029 VP->[JJ PP]\n0.000000 VP->[VBG ADVP S]\n0.000000 VP->[VBG ADVP PP]\n0.000000 VP->[VB ADVP S]\n0.000001 VP->[VB ADVP NP]\n0.000000 VP->[VB ADVP VP]\n0.000000 VP->[VP CC ADVP VP]\n0.000000 VP->[VBP NP : S]\n0.000000 VP->[VBD S PP SBAR]\n0.000381 VP->[VBP NP PP]\n0.000000 VP->[VBN ADVP SBAR , S]\n0.000002 VP->[POS NP]\n0.000007 VP->[ADVP VBN S]\n0.000352 VP->[VBD PRT PP]\n0.000000 VP->[ADVP VBN PP]\n0.012303 VP->[VBD PRT NP]\n0.000000 VP->[VBZ NP PP S]\n0.002244 VP->[MD VP]\n0.000000 VP->[VB NP S , SBAR]\n0.000000 VP->[VBP NP SBAR]\n0.000000 VP->[VBN PP SBAR]\n0.000000 VP->[VBN PP PP]\n0.000073 VP->[VBD ADJP SBAR]\n0.000048 VP->[VBD ADJP PP]\n0.000000 VP->[VB ADVP NP PP]\n0.000000 VP->[VBN PP NP]\n0.000000 VP->[VBN PP S]\n0.000000 VP->[VBN ADJP SBAR]\n0.000000 VP->[VB NP S S]\n0.000001 VP->[VBD PRT ADVP SBAR]\n0.000000 VP->[VBD PRT ADVP PP]\n0.000033 VP->[VBZ NP]\n0.000033 VP->[VBZ S]\n0.000000 VP->[VBZ ADVP NP]\n0.000000 VP->[VBZ ADVP VP]\n0.363529 VP->[VBD ADVP NP]\n0.000000 VP->[VBZ PP]\n0.000105 VP->[VBN NP PP]\n0.000007 VP->[VBZ SBAR]\n0.000000 VP->[VBN NP SBAR]\n0.000000 VP->[VBZ ADVP S]\n0.000527 VP->[VBZ VP]\n0.062578 VP->[VBD ADVP PP]\n0.000005 VP->[VBZ ADJP]\n0.000431 VP->[VBD ADVP VP]\n0.000000 VP->[VBZ ADVP ADJP]\n0.000014 VP->[VBP SBAR]\n0.000001 VP->[VBP PP]\n0.000309 VP->[VBP NP]\n0.000000 VP->[VB S SBAR]\n0.000423 VP->[VBP VP]\n0.000053 VP->[VBP S]\n0.000021 VP->[VP CC VP]\n0.000000 VP->[VBP ADVP]\n0.000001 VP->[VBN NP ADVP]\n0.000092 VP->[VBP ADJP]\n0.000000 VP->[NNS SBAR]\n0.116666 PRT->[RB]\n0.000000 PRT->[IN]\n0.000000 PRT->[RP]\n0.000000 PRT->[NNP]\n0.000000 ADJP->[RB VBD]\n0.000000 ADJP->[ADVP JJ SBAR]\n0.000025 ADJP->[NP JJR]\n0.000000 ADJP->[JJR PP]\n0.000158 ADJP->[NP JJ SBAR]\n0.016611 ADJP->[JJ]\n0.000076 ADJP->[VBG]\n0.042978 ADJP->[RB]\n0.000054 ADJP->[JJ '' S]\n0.000947 ADJP->[VBN]\n0.000000 ADJP->[ADJP PRN]\n0.000001 ADJP->[VBN PP]\n0.002679 ADJP->[JJR]\n0.001959 ADJP->[CD NN]\n0.000006 ADJP->[VBN S]\n0.000027 ADJP->[ADJP PP]\n0.000014 ADJP->[JJ PP]\n0.000000 ADJP->[ADJP CC ADJP]\n0.000248 ADJP->[QP]\n0.000976 ADJP->[JJ S]\n0.000591 ADJP->[JJ NP]\n0.000641 ADJP->[NN PP]\n0.003726 ADJP->[ADVP NN PP]\n0.000009 ADJP->[QP NN]\n0.000002 ADJP->[ADVP VBN]\n0.000578 ADJP->[NP JJ]\n0.000000 PRN->[-LRB- S -RRB-]\n0.000000 PRN->[: NP :]\n0.000000 PRN->[: S :]\n0.000000 PRN->[, S]\n0.000000 PRN->[, S ,]\n0.000000 PRN->[-LRB- NP -RRB-]\n0.000116 SINV->[ADJP VP NP .]\n0.000045 SINV->[S , VP NP .]\n0.000129 SINV->[S VP NP .]\n0.000000 SINV->[`` S , VP NP .]\n0.000000 SINV->[VP VP NP , PP .]\n0.000001 SINV->[ADVP VP NP .]\n0.000010 SINV->[VBD RB NP VP]\n0.000044 WHADVP->[WRB]\n0.000170 ADVP->[ADVP SBAR]\n0.000088 ADVP->[ADVP PP]\n0.000034 ADVP->[IN]\n0.000272 ADVP->[RBR]\n0.000038 ADVP->[NP RB]\n0.000000 ADVP->[IN PP]\n0.000946 ADVP->[JJ]\n0.058961 ADVP->[RB NP]\n0.002623 ADVP->[IN NP]\n0.000114 ADVP->[RB SBAR]\n0.000106 ADVP->[ADVP JJR]\n0.390880 ADVP->[RB]\n0.000015 ADVP->[JJ RB S]\n0.000254 ADVP->[NP IN]\n0.000335 ADVP->[RBS]\n0.000024 ADVP->[RBR NP]\n0.000001 ADVP->[NNP]\n0.000002 WHNP->[WRB]\n0.000009 WHNP->[IN]\n0.000055 WHNP->[WDT]\n0.000057 WHNP->[WP]\n0.000004 WHNP->[NP PP]\n0.000000 FRAG->[NP : NP : NP .]\n0.000006 FRAG->[NP]\n0.000296 FRAG->[NP .]\n0.000001 FRAG->[NP : NP .]\n0.000000 SQ->[MD VP]\n0.000000 SQ->[VBZ NP S]\n0.000000 SQ->[VP]\n0.000004 SQ->[VBD RB NP VP]\n0.000000 SBARQ->[SBAR , SBARQ .]\n0.000004 SBARQ->[WHNP SQ .]\n0.000000 SBARQ->[WHNP SQ]\n1.000000 VBD->[_took]\n1.000000 DT->[_the]\n1.000000 DT->[_The]\n1.000000 NN->[_interest]\n1.000000 IN->[_in]\n1.000000 JJ->[_young]\n1.000000 CD->[_two]\n1.000000 .->[_.]\n1.000000 RB->[_not]\n0.000000 RP->[_in]\n1.000000 PRP$->[_our]\n0.000435 VBP->[_sea-lions]\n0.000404 PRP->[_sea-lions]\n0.001983 TO->[_sea-lions]\n0.000069 VBG->[_sea-lions]\n0.000133 RBS->[_sea-lions]\n0.000068 PDT->[_sea-lions]\n0.000004 .->[_sea-lions]\n0.000278 VB->[_sea-lions]\n0.000020 JJ->[_sea-lions]\n0.000510 VBD->[_sea-lions]\n0.000000 POS->[_sea-lions]\n0.000074 NNP->[_sea-lions]\n0.000088 JJS->[_sea-lions]\n0.000030 WRB->[_sea-lions]\n0.000156 VBN->[_sea-lions]\n0.000362 RB->[_sea-lions]\n0.000597 :->[_sea-lions]\n0.002211 MD->[_sea-lions]\n0.000032 CD->[_sea-lions]\n0.000036 WDT->[_sea-lions]\n0.000038 WP->[_sea-lions]\n0.000065 IN->[_sea-lions]\n0.000054 ''->[_sea-lions]\n0.000134 RBR->[_sea-lions]\n0.655082 NNS->[_sea-lions]\n0.000584 VBZ->[_sea-lions]\n0.000016 NNPS->[_sea-lions]\n0.000000 FW->[_sea-lions]\n0.000003 ``->[_sea-lions]\n0.000160 JJR->[_sea-lions]\n0.000033 CC->[_sea-lions]\n0.000046 ,->[_sea-lions]\n0.000000 -LRB-->[_sea-lions]\n0.000379 EX->[_sea-lions]\n0.335902 NN->[_sea-lions]\n0.000014 DT->[_sea-lions]\n0.000011 PRP->[_slightest]\n0.000851 VBP->[_slightest]\n0.000174 TO->[_slightest]\n0.004881 VBG->[_slightest]\n0.000007 PDT->[_slightest]\n0.000202 RBS->[_slightest]\n0.000001 .->[_slightest]\n0.000000 -RRB-->[_slightest]\n0.002394 VB->[_slightest]\n0.544928 JJ->[_slightest]\n0.000172 VBD->[_slightest]\n0.000002 POS->[_slightest]\n0.003457 NNP->[_slightest]\n0.000015 WRB->[_slightest]\n0.059031 JJS->[_slightest]\n0.006672 VBN->[_slightest]\n0.001803 RB->[_slightest]\n0.000027 :->[_slightest]\n0.000119 PRP$->[_slightest]\n0.004392 CD->[_slightest]\n0.000035 MD->[_slightest]\n0.000018 WDT->[_slightest]\n0.000739 IN->[_slightest]\n0.000019 WP->[_slightest]\n0.000013 ''->[_slightest]\n0.000189 RBR->[_slightest]\n0.000532 NNS->[_slightest]\n0.000091 VBZ->[_slightest]\n0.000000 RP->[_slightest]\n0.000064 NNPS->[_slightest]\n0.000156 FW->[_slightest]\n0.000003 ``->[_slightest]\n0.020288 JJR->[_slightest]\n0.000109 CC->[_slightest]\n0.000043 ,->[_slightest]\n0.000011 EX->[_slightest]\n0.348430 NN->[_slightest]\n0.000122 DT->[_slightest]\n0.845454 NN->[_arrival]\n0.050914 NNS->[_arrival]\n0.000111 NNP->[_arrival]\n0.103520 JJ->[_arrival]\n");
    }
  }
  
  public void testWSJ5IO(){
    initParserFromFile(wsj5);
    
    String inputSentence = "The two young sea-lions .";
    System.err.println("\n### Run test parsing with string \"" + inputSentence + "\"");
    List<List<Double>> resultLists = parser.parseSentence(inputSentence);
    assertEquals(resultLists.size(), 4);
    List<Double> surprisalList = resultLists.get(0);
    List<Double> stringLogProbList = resultLists.get(3);
    List<Double> stringProbList = new ArrayList<Double>();
    for(double logProb : stringLogProbList){
      stringProbList.add(Math.exp(logProb));
    }
    
    assertEquals(surprisalList.size(), 5);
    assertEquals(3.3767245251434295, surprisalList.get(0), 1e-5);
    assertEquals(2.450539006283793, surprisalList.get(1), 1e-5);
    assertEquals(2.5505675598325848, surprisalList.get(2), 1e-5);
    assertEquals(2.292861303099343, surprisalList.get(3), 1e-5);
    assertEquals(4.894604763694462, surprisalList.get(4), 1e-5);
    
    assertEquals(stringProbList.size(), 5);
    assertEquals(0.0, stringProbList.get(0), 1e-5);
    assertEquals(0.0, stringProbList.get(1), 1e-5);
    assertEquals(2.5315814789499013E-6, stringProbList.get(2), 1e-5);
    assertEquals(5.442039250199556E-7, stringProbList.get(3), 1e-5);
    assertEquals(1.2514394214109883E-7, stringProbList.get(4), 1e-5);
    
    assertEquals(parser.dumpInsideChart(), "# Inside chart snapshot\ncell 0-1\n DT: 0.12500000\ncell 1-2\n ,: 0.07692308\n .: 0.16666667\n CC: 0.11111111\n VBD: 0.07142857\n VBZ: 0.20000000\n ``: 0.66666667\n TO: 0.33333333\n VB: 0.33333333\n NN: 0.03030303\n DT: 0.06250000\n JJ: 0.09090909\n NNPS: 0.16666667\n NNP: 0.02777778\n NNS: 0.11111111\n PRP: 0.40000000\n CD: 0.09090909\n POS: 0.33333333\n VBG: 0.33333333\n IN: 0.11764706\n $: 0.33333333\n RB: 0.33333333\n '': 0.66666667\ncell 2-3\n ,: 0.07692308\n .: 0.16666667\n CC: 0.11111111\n VBD: 0.07142857\n VBZ: 0.20000000\n ``: 0.66666667\n TO: 0.33333333\n VB: 0.33333333\n NN: 0.03030303\n DT: 0.06250000\n JJ: 0.09090909\n NNPS: 0.16666667\n NNP: 0.02777778\n NNS: 0.11111111\n PRP: 0.20000000\n CD: 0.09090909\n POS: 0.33333333\n VBG: 0.33333333\n IN: 0.05882353\n $: 0.33333333\n RB: 0.33333333\n '': 0.66666667\ncell 3-4\n ,: 0.07692308\n .: 0.16666667\n CC: 0.11111111\n VBD: 0.07142857\n VBZ: 0.20000000\n ``: 0.66666667\n TO: 0.33333333\n VB: 0.33333333\n NN: 0.03030303\n DT: 0.06250000\n JJ: 0.09090909\n NNPS: 0.16666667\n NNP: 0.02777778\n NNS: 0.11111111\n PRP: 0.20000000\n CD: 0.09090909\n POS: 0.33333333\n VBG: 0.33333333\n IN: 0.05882353\n $: 0.33333333\n RB: 0.33333333\n '': 0.66666667\ncell 4-5\n .: 0.83333333\ncell 0-2\n NP: 0.00053163\ncell 2-4\n VP: 0.00119865\n PP: 0.00099655\ncell 0-3\n : 0.00000253\n S: 0.00000316\n NP: 0.00002531\ncell 1-4\n QP: 0.00275482\ncell 0-4\n : 0.00000054\n S: 0.00000068\n NP: 0.00000627\ncell 0-5\n : 0.00000013\n S: 0.00000016\n");
    if(insideOutsideOpt>0){
      assertEquals(parser.dumpOutsideChart(), "# Outside chart snapshot\ncell 0-1\n DT: 0.00000100\ncell 1-2\n NN: 0.00000297\n VBG: 0.00000011\ncell 2-3\n VBD: 0.00000012\n VBZ: 0.00000010\n TO: 0.00000007\n VB: 0.00000010\n NN: 0.00000127\n NNS: 0.00000001\ncell 3-4\n VBD: 0.00000090\n NN: 0.00000006\n NNPS: 0.00000006\n NNP: 0.00000038\n NNS: 0.00000006\n PRP: 0.00000013\n CD: 0.00000006\ncell 4-5\n .: 0.00000015\ncell 0-2\n NP: 0.00016325\ncell 2-4\n VP: 0.00007088\ncell 0-3\n NP: 0.00158730\ncell 0-5\n : 1.00000000\n S: 0.80000000\n");
    
      assertEquals(parser.sprintExpectedCounts(), "# Expected counts\n1.000000 ROOT->[S]\n0.008028 S->[VP]\n1.000000 S->[NP VP .]\n0.004014 VP->[VBD SBAR]\n0.159932 VP->[VBZ NP]\n0.517803 VP->[VBD]\n0.057119 VP->[VBD NP]\n0.187309 VP->[TO VP]\n0.266554 VP->[VB NP]\n0.004014 VP->[VBD S]\n0.013144 NP->[NP NNS]\n0.025542 NP->[DT NN NN]\n0.083467 NP->[NNP]\n0.200322 NP->[PRP]\n0.001408 NP->[NP VP]\n0.083467 NP->[NNPS]\n0.015176 NP->[NN]\n0.045528 NP->[CD]\n0.693495 NP->[DT NN]\n0.055645 NP->[NNS]\n0.280963 NP->[DT VBG NN]\n0.004014 SBAR->[S]\n1.000000 DT->[_The]\n1.000000 .->[_.]\n0.280963 VBG->[_two]\n0.719037 NN->[_two]\n0.013144 NNS->[_young]\n0.266554 VB->[_young]\n0.159932 VBZ->[_young]\n0.066555 VBD->[_young]\n0.187309 TO->[_young]\n0.306505 NN->[_young]\n0.045528 CD->[_sea-lions]\n0.055645 NNS->[_sea-lions]\n0.083467 NNPS->[_sea-lions]\n0.200322 PRP->[_sea-lions]\n0.516395 VBD->[_sea-lions]\n0.083467 NNP->[_sea-lions]\n0.015176 NN->[_sea-lions]\n");
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
    List<Double> stringLogProbList = resultLists.get(3);
    List<Double> stringProbList = new ArrayList<Double>();
    for(double logProb : stringLogProbList){
      stringProbList.add(Math.exp(logProb));
    }
    
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
    List<Double> stringLogProbList = resultLists.get(3);
    List<Double> stringProbList = new ArrayList<Double>();
    for(double logProb : stringLogProbList){
      stringProbList.add(Math.exp(logProb));
    }

    
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
    List<Double> stringLogProbList = resultLists.get(3);
    List<Double> stringProbList = new ArrayList<Double>();
    for(double logProb : stringLogProbList){
      stringProbList.add(Math.exp(logProb));
    }
    
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
    List<Double> stringLogProbList = resultLists.get(3);
    List<Double> stringProbList = new ArrayList<Double>();
    for(double logProb : stringLogProbList){
      stringProbList.add(Math.exp(logProb));
    }
    
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
    
    assertEquals(stringProbList.size(), 3);
    assertEquals(3.8284368922100536E-4, stringProbList.get(0), 1e-5);
    assertEquals(0.0, stringProbList.get(1), 1e-5);
    assertEquals(0.9901606659305786, stringProbList.get(2), 1e-5);
  }
}
