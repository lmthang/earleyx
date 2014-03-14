package util;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import base.BiasProbRule;
import base.FragmentRule;
import base.ProbRule;
import base.Rule;
import base.RuleSet;
import base.TagRule;
import base.TerminalRule;

import edu.stanford.nlp.parser.lexparser.IntTaggedWord;
import edu.stanford.nlp.stats.ClassicCounter;
import edu.stanford.nlp.stats.Counter;
import edu.stanford.nlp.util.HashIndex;
import edu.stanford.nlp.util.Index;
import edu.stanford.nlp.util.StringUtils;

public class RuleFile {
  public static int verbose = 0;
  private static Pattern p = Pattern.compile("(.+?)->\\[(.+?)\\] : ([0-9\\.\\+\\-Ee]+)");
  private static Pattern biasP = Pattern.compile("([0-9\\.\\+\\-Ee]+) (.+?)->\\[(.+?)\\] : ([0-9\\.\\+\\-Ee]+)");
  //private static String UNK = "UNK";

  
  public static void printHelp(String[] args, String message){
    System.err.println("! " + message);
    System.err.println("RuleFile -in inFile -out outFile -opt outputOption"); // [-smooth]
    
    // compulsory
    System.err.println("\tCompulsory:");
    System.err.println("\t\t in \t\t input grammar");
    System.err.println("\t\t out \t\t output file");
//    System.err.println("\t\t smooth \t\t smooth grammar");
    System.err.println("\t\t opt \t\t output option: 0 -- format read by Earleyx (default), 1 -- format read by Tim's program, " + 
        "2 -- to format read by Mark's IO code");
    System.err.println();
    System.exit(1);
  }
  
  public static void parseRuleFile(BufferedReader br, RuleSet ruleSet, 
      Map<Integer, Counter<Integer>> tag2wordsMap,
      Map<Integer, Set<IntTaggedWord>> word2tagsMap,
      Map<Integer, Integer> nonterminalMap,
      Index<String> wordIndex,
      Index<String> tagIndex,
      boolean isBias // true if file contains bias
      ) throws IOException{
    String inputLine;
    
    if (verbose>=1){
      System.err.print("# Parsing rule data ...");
    }
    
    int count = 0;
    Matcher m = null;
    Matcher biasM = null;
    boolean isMatched = false;
    boolean isBiasMatched = false;
    while ((inputLine = br.readLine()) != null){
      count++;

      inputLine = inputLine.replaceAll("(^\\s+|\\s+$)", ""); // remove leading and trailing spaces
      m = p.matcher(inputLine);
      isMatched = m.matches();
      
      if(isBias){
        biasM = biasP.matcher(inputLine);
        isBiasMatched = biasM.matches();
      }
      if(!isMatched && (!isBias || !isBiasMatched)){
        System.err.println("! Fail to match line \"" + inputLine + "\"");
        System.exit(1);
      }
      
      double bias = 0.0;
      String tag = null;
      String rhs = null;
      double prob = 0.0;
      
      if(isBiasMatched){ // has bias counts for grammar rule induction
        // sanity check
        if(biasM.groupCount() != 4){
          System.err.println("! Num of matched groups != 4 for line \"" + inputLine + "\"");
          System.exit(1);
        }
        
        // retrieve info
        bias = Double.parseDouble(biasM.group(1));
        tag = biasM.group(2);
        rhs = biasM.group(3);
        prob = Double.parseDouble(biasM.group(4));
      } else if(isMatched){
        if(isBias){ // no explicit bias, set to 1.0
          bias = 1.0;
        }
        
        // sanity check
        if(m.groupCount() != 3){
          System.err.println("! Num of matched groups != 3 for line \"" + inputLine + "\"");
          System.exit(1);
        }
        
        // retrieve info
        tag = m.group(1);
        rhs = m.group(2);
        prob = Double.parseDouble(m.group(3));
      } 

      int iT = tagIndex.indexOf(tag, true);
      
      if(prob < 0){
        System.err.println("value < 0: " + inputLine);
        System.exit(1);
      }
      
      if(prob>1 && prob<1.000001){
    	System.err.println("! Change rule prob to 1.0: " + inputLine);
    	prob = 1.0;
      }
      String[] children = rhs.split(" ");
      int numChildren = children.length;
      
      // create a rule node or a tagged word
      ProbRule probRule = null;
      if (numChildren == 1 && children[0].startsWith("_")){ // X -> _y, terminal symbol, update distribution
        int iW = wordIndex.indexOf(children[0].substring(1), true);
        addWord(iW, iT, prob, tag2wordsMap, word2tagsMap);
        
        if(bias == 0.0){
          probRule = new ProbRule(new TerminalRule(iT, iW), prob);
        } else {
          probRule = new BiasProbRule(new TerminalRule(iT, iW), prob, bias);
        }
      } else { // rule
        if(!nonterminalMap.containsKey(iT)){
          nonterminalMap.put(iT, nonterminalMap.size());
        }
        
        // child indices
        int[] childIndices = new int[numChildren];
        boolean[] tagFlags = new boolean[numChildren];
        int numTags = 0;
        for (int i=0; i<numChildren; ++i){
          String child = children[i];
          if(!child.startsWith("_")){ // tag 
            childIndices[i] = tagIndex.indexOf(child, true); // tag index
            tagFlags[i] = true;
            numTags++;
          } else { // terminal
            childIndices[i] = wordIndex.indexOf(child.substring(1), true); // word index
            tagFlags[i] = false;
          }
        }
        
        Rule rule = null;
        if (numTags>0){
          rule = new FragmentRule(iT, childIndices, tagFlags, numTags);
        } else if (numTags == numChildren){
          rule = new TagRule(iT, childIndices);
        } else {
          rule = new TerminalRule(iT, childIndices);
        }
        
        if(bias == 0.0){
          probRule = new ProbRule(rule, prob);
        } else { // bias rule
          probRule = new BiasProbRule(rule, prob, bias);
        }
      }
      
      ruleSet.add(probRule);
      
      if (verbose>=1){
        if(count % 10000 == 0){
          System.err.print(" (" + count + ") ");
        }
      }
    }
    
    if (verbose>=1){
      System.err.println(" Done! Num rules = " + count + ", num fragment rules = " 
          + ruleSet.numFragmentRules() + " (num multiple terminal rules = " + ruleSet.numMultipleTerminalRules());
    }
    if (verbose>=4){
      System.err.println("# ruleSet " + ruleSet.size() + "\n" + Util.sprint(ruleSet.getAllRules(), tagIndex, wordIndex));
      System.err.println(Util.sprint(tag2wordsMap, tagIndex, wordIndex));
    }
    
//    System.err.println(ruleSet.getUnkPreterminals());
//    System.err.println(Util.sprint(ruleSet.getUnkPreterminals(), tagIndex));
    br.close();
  }
  
  private static void addWord(int iW, int iT, double prob, 
      Map<Integer, Counter<Integer>> tag2wordsMap,
      Map<Integer, Set<IntTaggedWord>> word2tagsMap
      // Set<Integer> nonterminals
      //Map<Label, Counter<String>> tagHash,
      //Set<String> seenEnd
      ){
    
    // initialize counter
    if(!tag2wordsMap.containsKey(iT)){
      tag2wordsMap.put(iT, new ClassicCounter<Integer>());
    }
    Counter<Integer> wordCounter = tag2wordsMap.get(iT);
    
    // sanity check
    assert(!wordCounter.containsKey(iW));
    
    // set prob
    wordCounter.setCount(iW, prob);
    
   
    // update list of tags per terminal
    if (!word2tagsMap.containsKey(iW)) {
      word2tagsMap.put(iW, new HashSet<IntTaggedWord>());
    }
    word2tagsMap.get(iW).add(new IntTaggedWord(iW, iT)); // NOTE: it is important to have the tag here due to BaseLexicon.score() method's requirement
  }
  
  /**
   * Thang v110901: output rules to file
   * @throws IOException 
   **/
  public static void printRules(String ruleFile, Collection<ProbRule> rules
      , Index<String> wordIndex, Index<String> tagIndex) throws IOException{
    System.err.println("# Output rules to file " + (new File(ruleFile)).getAbsolutePath());
    BufferedWriter bw = new BufferedWriter(new FileWriter(ruleFile));
    
    for(ProbRule rule : rules){
      if(rule.getProb()>0.0){
        bw.write(rule.toString(tagIndex, wordIndex) + "\n");
      }
    }
    
    bw.close();
  }
  
  /**
   * Thang v110901: output rules to file
   * @throws IOException 
   **/
  public static void printUnkLexiconSchemeFormat(String prefixFile
      , Map<Integer, Counter<Integer>> tag2wordsMap 
      , Index<String> wordIndex, Index<String> tagIndex) throws IOException{
    String ruleFile = prefixFile + ".forms.txt";
    String countFile = prefixFile + ".counts.txt";
    System.err.println("# Output rules to files: " + (new File(ruleFile)).getAbsolutePath()
        + "\t" + (new File(countFile)).getAbsolutePath());
    BufferedWriter bw = new BufferedWriter(new FileWriter(ruleFile));
    BufferedWriter bwCount = new BufferedWriter(new FileWriter(countFile));
    
    // rules: non-terminal->[terminal] : prob
    for(int iT : tag2wordsMap.keySet()){
      String tag = tagIndex.get(iT);
      Counter<Integer> counter = tag2wordsMap.get(iT);
      for(Integer iW : counter.keySet()){
        String word = wordIndex.get(iW);
        if (word.startsWith("UNK")){
          int count = (int) counter.getCount(iW);
          bw.write("(" + tag + " _" + word + ")\n");
          bwCount.write(count + "\n");
        }
      }
    }
    
    bw.close();
    bwCount.close();
  }
  
  public static String schemeString(String mother, List<String> children) {
    StringBuffer sb = new StringBuffer();
    sb.append("(" + mother + " ");
    for (String dtr : children){
      sb.append("(X " + dtr + ") ");
    }
    
    if(children.size() > 0){
      sb.delete(sb.length()-1, sb.length());
      sb.append(")");
    }
    
    
    //sb.append("\t" + ((int) score));
    return sb.toString();
  }
  
  public static void main(String[] args) {
    if(args.length==0){
      printHelp(args, "No argument");
    }
    System.err.println("RuleFile invoked with arguments " + Arrays.asList(args));
    
    /* Define flags */
    Map<String, Integer> flags = new HashMap<String, Integer>();
    // compulsory
    flags.put("-in", new Integer(1)); // input filename
    flags.put("-out", new Integer(1)); // output filename
//    flags.put("-smooth", new Integer(0)); // smooth
    flags.put("-opt", new Integer(1)); // option
    
    Map<String, String[]> argsMap = StringUtils.argsToMap(args, flags);
    args = argsMap.get(null);
    
    /* input file */
    String ruleFile = null;
    if (argsMap.keySet().contains("-in")) {
      ruleFile = argsMap.get("-in")[0];
    } else {
      printHelp(args, "No input file, -in option");
    }
    
    /* output file */
    String outRuleFile = null;
    if (argsMap.keySet().contains("-out")) {
      outRuleFile = argsMap.get("-out")[0];
    } else {
      printHelp(args, "No output file, -out option");
    }
    
    /* option */
    int option = 0;
    if (argsMap.keySet().contains("-opt")) {
      option = Integer.parseInt(argsMap.get("-opt")[0]);
    } else {
      printHelp(args, "No output file, -opt option");
    }
    
    
    System.err.println("# Input file = " + ruleFile);
    System.err.println("# Output file = " + outRuleFile);
    System.err.println("# Option = " + option);
    
    // extract rules and taggedWords from grammar file
    Map<Integer, Counter<Integer>> tag2wordsMap = new HashMap<Integer, Counter<Integer>>();
    Map<Integer, Set<IntTaggedWord>> word2tagsMap = new HashMap<Integer, Set<IntTaggedWord>>();
    Map<Integer, Integer> nonterminalMap = new HashMap<Integer, Integer>();
    Index<String> wordIndex = new HashIndex<String>();
    Index<String> tagIndex = new HashIndex<String>();
    RuleSet ruleSet = new RuleSet(tagIndex, wordIndex);
    
    /* Input */
    try {
      RuleFile.parseRuleFile(Util.getBufferedReaderFromFile(ruleFile), ruleSet, tag2wordsMap, 
          word2tagsMap, nonterminalMap, wordIndex, tagIndex, false); //, tagHash, seenEnd); // we don't care much about extended rules, just treat them as rules
      //rules.addAll(extendedRules);
    } catch (IOException e){
      System.err.println("Can't read rule file: " + ruleFile);
      e.printStackTrace();
    }
    
    try {
      BufferedWriter bw = new BufferedWriter(new FileWriter(outRuleFile));
      for(ProbRule rule : ruleSet.getAllRules()){
        if (option==3){ // Output in format that could be read by Tim's code
          bw.write(rule.timString(tagIndex, wordIndex) + "\n");
        } else if (option==2){ // Output in format that could be read by Mark's IO code
          bw.write("0.0 " + rule.markString(tagIndex, wordIndex) + "\n");
        } else { // Earleyx format
          bw.write(rule.toString(tagIndex, wordIndex) + "\n");
        }
      }
      bw.close();
    } catch (IOException e){
      System.err.println("Can't write to: " + outRuleFile);
      e.printStackTrace();
    }
    
//    if(smooth==1){
//      /* Smooth */
//      SmoothLexicon.smooth(tag2wordsMap, wordIndex, tagIndex, word2tagsMap);
//      
//      /* Output */
//      try {
//        //RuleFile.printRules(outRuleFile, rules, newWordCounterTagMap);
//        RuleFile.printUnkLexiconSchemeFormat(outRuleFile, tag2wordsMap, wordIndex, tagIndex);
//      } catch (IOException e){
//        System.err.println("Can't write to: " + outRuleFile);
//        e.printStackTrace();
//      }
//    } else {   
//    }
    
    
  }
}

/** Unused code **/
//public static void printRules(String ruleFile, Collection<ProbRule> rules
//    , Map<Integer, Counter<Integer>> origTag2wordsMap, Index<String> wordIndex, Index<String> tagIndex, boolean isExp) throws IOException{
//  System.err.println("# Output rules to file " + (new File(ruleFile)).getAbsolutePath());
//  BufferedWriter bw = new BufferedWriter(new FileWriter(ruleFile));
//  
//  Map<Integer, Counter<Integer>> tag2wordsMap;
//  if (isExp){ // exp
//    tag2wordsMap = new HashMap<Integer, Counter<Integer>>();
//    for(Integer iT : origTag2wordsMap.keySet()){
//      Counter<Integer> counter = Counters.getCopy(origTag2wordsMap.get(iT));
//      Counters.expInPlace(counter);
//      
//      tag2wordsMap.put(iT, counter);
//    }
//  } else {
//    tag2wordsMap = origTag2wordsMap;
//  }
//  
//  // rules: non-terminal->[terminal] : prob
//  for(Integer iT : tag2wordsMap.keySet()) { //Entry<Integer, Counter<Integer>> mapEntry : tag2wordsMap.entrySet()){
//    String prefix = tagIndex.get(iT) + "->[_";
//    Counter<Integer> counter = tag2wordsMap.get(iT);
//    
//    for(Integer iW : counter.keySet()){
//      double prob = counter.getCount(iW);
//      if(isExp && prob < 0){
//        System.err.println("Prob < 0: " + prefix + "\t" + wordIndex.get(iW) + "\t" + prob);
//        System.exit(1);
//      }
//      bw.write(prefix + wordIndex.get(iW) + "] : " + prob + "\n");
//    }
//  }
//  
//  // rules: non-terminal -> non-terminals
//  bw.write(Util.sprint(rules, wordIndex, tagIndex));
////  for(Rule rule : rules){
////    bw.write(rule + "\n");
////  }
//  
//  bw.close();
//}

//public static void processRuleFile(String grammarFile) throws IOException{
//  BufferedReader br = new BufferedReader(new InputStreamReader(new FileInputStream(grammarFile)));
//  String inputLine;
//  
//  System.err.print("# Reading rule file ...");
//  int count = 0;
//  while ((inputLine = br.readLine()) != null){
//    count++;
//    inputLine = inputLine.replaceAll("(^\\s+|\\s+$)", ""); // remove leading and trailing spaces
//    Matcher m = p.matcher(inputLine);
//    if(m.matches()){ // check pattern
//      // sanity check
//      if(m.groupCount() != 3){
//        System.err.println("! Num of matched groups != 3 for line \"" + inputLine + "\"");
//        System.exit(1);
//      }
//      
//      // retrieve info
//      String tag = m.group(1);
//      String rhs = m.group(2);
//      double prob = Double.parseDouble(m.group(3));
//      
//      System.err.println(tag + "\t" + rhs + "\t" + prob);
//      break;
//    } else {
//      System.err.println("! Fail to match line \"" + inputLine + "\"");
//      System.exit(1);
//    }
//    
//    if(count % 10000 == 0){
//      System.err.print(" (" + count + ") ");
//    }
//  }
//  System.err.println(" Done! Total lines = " + count);    
//}
//public static void parseRuleFile(BufferedReader br, Collection<Rule> rules,
//    Collection<Rule> extendedRules,
//    Map<Integer, Counter<Integer>> tag2wordsMap) throws IOException{
//
//  Map<Integer, Set<IntTaggedWord>> word2tagsMap = new HashMap<Integer, Set<IntTaggedWord>>();
//  Set<Integer> nonterminals = new HashSet<Integer>();
//  Index<String> wordIndex = new HashIndex<String>();
//  Index<String> tagIndex = new HashIndex<String>();
//  parseRuleFile(br, rules, extendedRules, tag2wordsMap, word2tagsMap, nonterminals, wordIndex, tagIndex); //tagHash, seenEnd);
//}
///** Read from string **/
//public static void parseRuleFile(StringReader sr, Collection<Rule> rules, 
//    Collection<Rule> extendedRules,
//    Map<Integer, Counter<Integer>> tag2wordsMap,
//    Map<Integer, Set<IntTaggedWord>> word2tagsMap,
//    Set<Integer> nonterminals,
//    Index<String> wordIndex,
//    Index<String> tagIndex
//    ) throws IOException{
//  BufferedReader br = new BufferedReader(sr);
//  
//  System.err.println("Reading from string ... ");
//  parseRuleFile(br, rules, extendedRules, tag2wordsMap, word2tagsMap, nonterminals, wordIndex, tagIndex);
//}

//public static void parseRuleFile(String grammarFile, Collection<Rule> rules, 
//    Collection<Rule> extendedRules,
//    Map<Integer, Counter<Integer>> tag2wordsMap,
//    Map<Integer, Set<IntTaggedWord>> word2tagsMap,
//    Set<Integer> nonterminals,
//    Index<String> wordIndex,
//    Index<String> tagIndex
//    //Map<Label, Counter<String>> tagHash,
//    //Set<String> seenEnd
//    ) throws IOException{
//  System.err.print("# Reading rule file " + grammarFile + " ...");
//  BufferedReader br = new BufferedReader(new InputStreamReader(new FileInputStream(grammarFile)));
//  
//  parseRuleFile(br, rules, extendedRules, tag2wordsMap, word2tagsMap, nonterminals, wordIndex, tagIndex); //, tagHash, seenEnd);
//}

//Set<String> unarySet = new HashSet<String>();
//if(children.length == 1){ // unary
//  String unaryPair = tag + "->" + children[0];
//  String reverseUnaryPair = children[0] + "->" + tag;
//  if (unarySet.contains(reverseUnaryPair)) {
//    System.err.println("# Recursive unary rule: " + unaryPair);
//  }
//  unarySet.add(unaryPair);
//}

//if(word.startsWith(UNK)){ // store tagHash
//  // add seenEnd
//  if(!word.equals(UNK)){
//    seenEnd.add(word);
//  }
//  
//  // initialize counter
//  Label tagLabel = iT.tagLabel();
//  if(!tagHash.containsKey(tagLabel)){
//    tagHash.put(tagLabel, new ClassicCounter<String>());
//  }
//  Counter<String> unknownWordCounter = tagHash.get(tagLabel);
//
//  // sanity check
//  if(unknownWordCounter.containsKey(word)){
//    System.err.println("! Error duplicate key: " + word + ", for tag=" + tagLabel);
//    System.exit(1);
//  }
//  
//  // set prob
//  unknownWordCounter.setCount(word, prob);
//  
//  // set tags for UNK
//  if(!nonterminals.contains(iT)){
//    nonterminals.add(iT);
//  }
//  
//} else { // store tag2wordsMap
//  // initialize counter
//  if(!tag2wordsMap.containsKey(iT)){
//    tag2wordsMap.put(iT, new ClassicCounter<Integer>());
//  }
//  Counter<Integer> wordCounter = tag2wordsMap.get(iT);
//  
//  // sanity check
//  assert(!wordCounter.containsKey(iW));
//  
//  // set prob
//  wordCounter.setCount(iW, prob);
//}
