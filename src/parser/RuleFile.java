package parser;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.StringReader;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.Map.Entry;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import edu.stanford.nlp.ling.Label;
import edu.stanford.nlp.parser.lexparser.EnglishUnknownWordModel;
import edu.stanford.nlp.parser.lexparser.IntTaggedWord;
import edu.stanford.nlp.parser.lexparser.Options.LexOptions;
import edu.stanford.nlp.parser.lexparser.Train;
import edu.stanford.nlp.stats.ClassicCounter;
import edu.stanford.nlp.stats.Counter;
import edu.stanford.nlp.stats.Counters;
import edu.stanford.nlp.trees.DiskTreebank;
import edu.stanford.nlp.trees.Treebank;
import edu.stanford.nlp.util.Index;

public class RuleFile {
  private static Pattern p = Pattern.compile("(.+?)->\\[(.+?)\\] : ([0-9.\\+\\-Ee]+)");
  private static String UNK = "UNK";
  public static String PSEUDOTAG_PREFIX = "PSEUDO";
  public static boolean isPseudoTag = false;
  
  public static void parseRuleFile(StringReader sr, Collection<Rule> rules,
      Collection<Rule> extendedRules,
      Map<IntTaggedWord, Counter<IntTaggedWord>> wordCounterTagMap, boolean isPseudoTag) throws IOException{
    Map<IntTaggedWord, Set<IntTaggedWord>> tagsForWord = new HashMap<IntTaggedWord, Set<IntTaggedWord>>();
    Set<IntTaggedWord> preterminalSet = new HashSet<IntTaggedWord>();
    Map<Label, Counter<String>> tagHash = new HashMap<Label, Counter<String>>();
    Set<String> seenEnd = new HashSet<String>();
    parseRuleFile(sr, rules, extendedRules, wordCounterTagMap, tagsForWord, preterminalSet, tagHash, seenEnd);
  }
  
  public static void parseRuleFile(StringReader sr, Collection<Rule> rules, 
      Collection<Rule> extendedRules,
      Map<IntTaggedWord, Counter<IntTaggedWord>> wordCounterTagMap,
      Map<IntTaggedWord, Set<IntTaggedWord>> tagsForWord,
      Set<IntTaggedWord> preterminalSet,
      Map<Label, Counter<String>> tagHash,
      Set<String> seenEnd) throws IOException{
    BufferedReader br = new BufferedReader(sr);
    
    System.err.println("Reading from string ... ");
    parseRuleFile(br, rules, extendedRules, wordCounterTagMap, tagsForWord, preterminalSet, tagHash, seenEnd);
  }
  
  public static void parseRuleFile(String grammarFile, Collection<Rule> rules, 
      Collection<Rule> extendedRules,
      Map<IntTaggedWord, Counter<IntTaggedWord>> wordCounterTagMap,
      Map<IntTaggedWord, Set<IntTaggedWord>> tagsForWord,
      Set<IntTaggedWord> preterminalSet,
      Map<Label, Counter<String>> tagHash,
      Set<String> seenEnd) throws IOException{
    System.err.print("# Reading rule file " + grammarFile + " ...");
    BufferedReader br = new BufferedReader(new InputStreamReader(new FileInputStream(grammarFile)));
    
    parseRuleFile(br, rules, extendedRules, wordCounterTagMap, tagsForWord, preterminalSet, tagHash, seenEnd);
  }
  
  private static void parseRuleFile(BufferedReader br, Collection<Rule> rules, 
      Collection<Rule> extendedRules, // e.g. adaptor grammar rules, i.e. approximating pcfg with sequence of terminals on the rhs
      // check RoarkBaseLexicon for the meaning of these variables
      Map<IntTaggedWord, Counter<IntTaggedWord>> wordCounterTagMap,
      Map<IntTaggedWord, Set<IntTaggedWord>> tagsForWord,
      Set<IntTaggedWord> preterminalSet,
      Map<Label, Counter<String>> tagHash,
      Set<String> seenEnd) throws IOException{
    String inputLine;
    
    Counter<IntTaggedWord> pseudoNodeCounter = new ClassicCounter<IntTaggedWord>();
    
    int count = 0;
    Set<String> unarySet = new HashSet<String>();
    while ((inputLine = br.readLine()) != null){
      count++;

      inputLine = inputLine.replaceAll("(^\\s+|\\s+$)", ""); // remove leading and trailing spaces
      Matcher m = p.matcher(inputLine);
      
      if(m.matches()){
        // sanity check
        if(m.groupCount() != 3){
          System.err.println("! Num of matched groups != 3 for line \"" + inputLine + "\"");
          System.exit(1);
        }
        
        // retrieve info
        String tag = m.group(1);
        String rhs = m.group(2);
        double prob = Double.parseDouble(m.group(3));
        if(prob < 0){
          System.err.println("value < 0: " + inputLine);
          System.exit(1);
        }
        
        //System.err.println(tag + "\t" + iT);
        String[] children = rhs.split(" ");
        int numChilds = children.length;
        
        // create a rule node or a tagged word
        if (numChilds == 1 && children[0].startsWith("_")){ // terminal symbol, update distribution
          String word = children[0].substring(1);
          addWord(word, tag, prob, wordCounterTagMap, tagsForWord, preterminalSet, tagHash, seenEnd);
        } else { // rule
          // check for extended (e.g. ag) rules
          boolean isExtendedRule = true;
          
          // get words
          List<String> words = new ArrayList<String>();
          for (int i=0; i<numChilds; ++i){
            String child = children[i];
            if(!child.startsWith("_")){
              isExtendedRule = false;
              break;
            } else {
              words.add(child.substring(1));
            }
          }
          
          if (isExtendedRule){ // process extended rule X -> _a _b _c
            if(isPseudoTag) { // create pseudo tag
              processExtendedRule(tag, words, prob, pseudoNodeCounter, extendedRules, tagsForWord);
            } else { // treat like normal rule
              //Rule extendedRule = new Rule(tag, words, prob);
              Rule extendedRule = new Rule(tag, words, prob, false);
              extendedRules.add(extendedRule);
            }
          } else {
            Rule rule = new Rule(tag, Arrays.asList(children), prob);
            if(children.length == 1){
              String unaryPair = tag + "->" + children[0];
              String reverseUnaryPair = children[0] + "->" + tag;
              if (unarySet.contains(reverseUnaryPair)) {
                System.err.println("# Recursive unary rule: " + unaryPair);
              }
              unarySet.add(unaryPair);
            }
            rules.add(rule);
          }
        }
      } else {
        System.err.println("! Fail to match line \"" + inputLine + "\"");
        System.exit(1);
      }
      
      if(count % 10000 == 0){
        System.err.print(" (" + count + ") ");
      }
    }

    System.err.println(" Done! Total lines = " + count);
//    System.err.println(wordCounterTagMap.keySet());
//    System.err.println(tagHash.keySet());
//    System.exit(1);
  }

  private static void processExtendedRule(String tag, List<String> words, double prob
      , Counter<IntTaggedWord> pseudoNodeCounter, Collection<Rule> extendedRules
      , Map<IntTaggedWord, Set<IntTaggedWord>> tagsForWord){
    IntTaggedWord motherITW = IntTaggedWord.createTagITW(tag);
    pseudoNodeCounter.incrementCount(motherITW);
    int currentCount = (int) pseudoNodeCounter.getCount(motherITW);
    
    int childCount = 0;
    List<IntTaggedWord> pseudoITWs = new ArrayList<IntTaggedWord>();
    for(String word : words){
      // pseudonodes: |X1-1|a, |X1-2|b, |X1-3|c
      
      String pseudoTag = PSEUDOTAG_PREFIX + "|" + tag + currentCount + "-" + ++childCount + "|" + word;
      IntTaggedWord pseudoITW = IntTaggedWord.createTagITW(pseudoTag);
      pseudoITWs.add(pseudoITW);
      
      // add lexicon rule: |X1-1|a -> a : 1.0
      // addWord(word, pseudoTag, 1.0, wordCounterTagMap, tagsForWord, tagHash, seenEnd);
      
      /* Note: here we don't update wordCounterTagMap, since in BasicLexicon.score, pseudo tag will be handled */
      // update list of tags per terminal
      IntTaggedWord iW = IntTaggedWord.createWordITW(word);
      if (!tagsForWord.containsKey(iW)) {
        tagsForWord.put(iW, new HashSet<IntTaggedWord>());
      }
      tagsForWord.get(iW).add(new IntTaggedWord(word, pseudoTag)); // NOTE: it is important to have the tag here due to BaseLexicon.score() method's requirement      
    }
    
    // add new rule: X -> |X1-1|a |X1-2|b |X1-3|c
    Rule extendedRule = new Rule(motherITW, pseudoITWs, prob);
    extendedRules.add(extendedRule);
  }
  
  private static void addWord(String word, String tag, double prob, 
      Map<IntTaggedWord, Counter<IntTaggedWord>> wordCounterTagMap,
      Map<IntTaggedWord, Set<IntTaggedWord>> tagsForWord,
      Set<IntTaggedWord> preterminalSet,
      Map<Label, Counter<String>> tagHash,
      Set<String> seenEnd){
    IntTaggedWord iW = IntTaggedWord.createWordITW(word);
    IntTaggedWord iT = IntTaggedWord.createTagITW(tag);
    IntTaggedWord iTW = new IntTaggedWord(word, tag);
    
    if(word.startsWith(UNK)){ // store tagHash
      // add seenEnd
      if(!word.equals(UNK)){
        seenEnd.add(word);
      }
      
      // initialize counter
      Label tagLabel = iT.tagLabel();
      if(!tagHash.containsKey(tagLabel)){
        tagHash.put(tagLabel, new ClassicCounter<String>());
      }
      Counter<String> unknownWordCounter = tagHash.get(tagLabel);
    
      // sanity check
      if(unknownWordCounter.containsKey(word)){
        System.err.println("! Error duplicate key: " + word + ", for tag=" + tagLabel);
        System.exit(1);
      }
      
      // set prob
      unknownWordCounter.setCount(word, prob);
      
      // set tags for UNK
      if(!preterminalSet.contains(iT)){
        preterminalSet.add(iT);
      }
      
    } else { // store wordCounterTagMap
      // initialize counter
      if(!wordCounterTagMap.containsKey(iT)){
        wordCounterTagMap.put(iT, new ClassicCounter<IntTaggedWord>());
      }
      Counter<IntTaggedWord> wordCounter = wordCounterTagMap.get(iT);
      
      // sanity check
      assert(!wordCounter.containsKey(iW));
      
      // set prob
      wordCounter.setCount(iW, prob);
    }
   
    // update list of tags per terminal
    if (!tagsForWord.containsKey(iW)) {
      tagsForWord.put(iW, new HashSet<IntTaggedWord>());
    }
    tagsForWord.get(iW).add(iTW); // NOTE: it is important to have the tag here due to BaseLexicon.score() method's requirement
  }
  
  public static void processRuleFile(String grammarFile) throws IOException{
    BufferedReader br = new BufferedReader(new InputStreamReader(new FileInputStream(grammarFile)));
    String inputLine;
    
    System.err.print("# Reading rule file ...");
    int count = 0;
    while ((inputLine = br.readLine()) != null){
      count++;
      inputLine = inputLine.replaceAll("(^\\s+|\\s+$)", ""); // remove leading and trailing spaces
      Matcher m = p.matcher(inputLine);
      if(m.matches()){ // check pattern
        // sanity check
        if(m.groupCount() != 3){
          System.err.println("! Num of matched groups != 3 for line \"" + inputLine + "\"");
          System.exit(1);
        }
        
        // retrieve info
        String tag = m.group(1);
        String rhs = m.group(2);
        double prob = Double.parseDouble(m.group(3));
        
        System.err.println(tag + "\t" + rhs + "\t" + prob);
        break;
      } else {
        System.err.println("! Fail to match line \"" + inputLine + "\"");
        System.exit(1);
      }
      
      if(count % 10000 == 0){
        System.err.print(" (" + count + ") ");
      }
    }
    System.err.println(" Done! Total lines = " + count);    
  }
  
  /**
   * Thang v110901: output rules to file
   * @throws IOException 
   **/
  public static void printRules(String ruleFile, Collection<Rule> rules
      , Map<Integer, Counter<Integer>> origTag2wordsMap, Index<String> wordIndex, Index<String> tagIndex, boolean isExp) throws IOException{
    System.err.println("# Output rules to file " + (new File(ruleFile)).getAbsolutePath());
    BufferedWriter bw = new BufferedWriter(new FileWriter(ruleFile));
    
    Map<Integer, Counter<Integer>> tag2wordsMap;
    if (isExp){ // exp
      tag2wordsMap = new HashMap<Integer, Counter<Integer>>();
      for(Integer iT : tag2wordsMap.keySet()){
        Counter<Integer> counter = Counters.getCopy(tag2wordsMap.get(iT));
        Counters.expInPlace(counter);
        
        tag2wordsMap.put(iT, counter);
        System.err.println(counter + "\t" + tag2wordsMap.get(iT));
      }
    } else {
      tag2wordsMap = origTag2wordsMap;
    }
    
    // rules: non-terminal->[terminal] : prob
    for(Integer iT : tag2wordsMap.keySet()) { //Entry<Integer, Counter<Integer>> mapEntry : tag2wordsMap.entrySet()){
      String prefix = tagIndex.get(iT) + "->[_";
      Counter<Integer> counter = tag2wordsMap.get(iT);
      
      for(Integer iW : counter.keySet()){
        double prob = counter.getCount(iW);
        if(isExp && prob < 0){
          System.err.println("Prob < 0: " + prefix + "\t" + wordIndex.get(iW) + "\t" + prob);
          System.exit(1);
        }
        bw.write(prefix + wordIndex.get(iW) + "] : " + prob + "\n");
      }
    }
    
    // rules: non-terminal -> non-terminals
    for(Rule rule : rules){
      bw.write(rule + "\n");
    }
    
    bw.close();
  }
  
  /**
   * Thang v110901: output rules to file
   * @throws IOException 
   **/
  public static void printRulesSchemeFormat(String prefixFile, Collection<Rule> rules
      , Collection<Rule> extendedRules
      , Map<IntTaggedWord, Counter<IntTaggedWord>> wordCounterTagMap) throws IOException{
    String ruleFile = prefixFile + ".forms.txt";
    String countFile = prefixFile + ".counts.txt";
    System.err.println("# Output rules to files: " + (new File(ruleFile)).getAbsolutePath()
        + "\t" + (new File(countFile)).getAbsolutePath());
    BufferedWriter bw = new BufferedWriter(new FileWriter(ruleFile));
    BufferedWriter bwCount = new BufferedWriter(new FileWriter(countFile));
    
    // rules: non-terminal->[terminal] : prob
    for(Entry<IntTaggedWord, Counter<IntTaggedWord>> mapEntry : wordCounterTagMap.entrySet()){
      String tag = mapEntry.getKey().tagString();
      
      Counter<IntTaggedWord> counter = mapEntry.getValue();
      for(IntTaggedWord wordITW : counter.keySet()){
        String word = wordITW.wordString();
        if (word.startsWith("UNK")){
          int count = (int) counter.getCount(wordITW);
          System.err.println("(" + tag + " _" + word + ")\t" + count);
          bw.write("(" + tag + " _" + word + ")\n");
          bwCount.write(count + "\n");
        }
      }
    }
    
    // print rules
//    rules.addAll(extendedRules);
//    for(Rule rule : rules){
//      bw.write(rule.schemeString() + "\n");
//      bwCount.write((int) rule.score + "\n"); 
//    }
    
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
  public void testRuleFile(){
    String grammarFile = "../data/WSJ.50/WSJ.50.base-rule-counts.txt";
    String treebankFile = "../data/MRG/WSJ-processed.MRG.50";
    
    try {
      processRuleFile(grammarFile);
    } catch (IOException e) {
      e.printStackTrace();
    }
    
    LexOptions op = new LexOptions();
    op.useUnknownWordSignatures = 1;
    //op.uwModel = "edu.stanford.nlp.parser.prefixparser.ExtendedBaseUnknownWordModel";
    RoarkBaseLexicon lex = new RoarkBaseLexicon(op);
    int unknownLevel = 0; // all unknown words are represented by UNK
    lex.getUnknownWordModel().setUnknownLevel(unknownLevel);
    
    Treebank tb = new DiskTreebank();
    tb.loadPath(treebankFile, null); //new NumberRangesFileFilter(args[1], true));
    Train.fractionBeforeUnseenCounting = 0;
    //lex.train(tb);
    
    List<IntTaggedWord> taggedWords = new ArrayList<IntTaggedWord>();
    ExtendedBaseUnknownWordModel.processTreeCollection(tb, taggedWords);
    lex.trainWithIntTaggedWords(taggedWords);
    
    BasicLexicon.testBaseLexicon(lex);
    //System.err.println(lex);
    lex.printLexStats();

  }

  public static Map<IntTaggedWord, Counter<IntTaggedWord>> smoothWordCounterTagMap(
      Map<IntTaggedWord, Counter<IntTaggedWord>> wordCounterTagMap){
    LexOptions op = new LexOptions();
    op.useUnknownWordSignatures = 2;
    Train.fractionBeforeUnseenCounting = 0;
    EnglishUnknownWordModel uwModel = new EnglishUnknownWordModel(op, null);
    
    // add signatures for singletons
    Map<IntTaggedWord, Counter<IntTaggedWord>> newWordCounterTagMap = new HashMap<IntTaggedWord, Counter<IntTaggedWord>>();
    for(IntTaggedWord iT : wordCounterTagMap.keySet()){
      Counter<IntTaggedWord> wordCounter = wordCounterTagMap.get(iT);

      Counter<IntTaggedWord> newWordCounter = new ClassicCounter<IntTaggedWord>();
      for(IntTaggedWord iW : wordCounter.keySet()){
        
        int count = (int) wordCounter.getCount(iW);
        newWordCounter.setCount(iW, count); // add back counter
        
        String wordStr = iW.wordString();
        String[] tokens = wordStr.split("\\s+");
        if(count == 1 && tokens.length == 1) { // singleton & single terminal rule
          String signature = uwModel.getSignature(iW.wordString(), -1);
          System.err.println(iT.tagString() + " _" + signature + " " + iW.wordString());
          newWordCounter.incrementCount(IntTaggedWord.createWordITW(signature));
        }
      }
      newWordCounter.incrementCount(RoarkUnknownWordModel.unknownITW);
      
      newWordCounterTagMap.put(iT, newWordCounter);
    }
    
    return newWordCounterTagMap;
  }
  
  public static void main(String[] args) {
    String ruleFile = null;
    String outRuleFile = null;
    if (args.length == 2){
      ruleFile = args[0];
      outRuleFile = args[1];
    } else {
      System.err.println("# Run program with two arguments [inFile] [outFile]");
      System.exit(1);
    }
    //ruleFile = "../grammars/WSJ.5/WSJ.5.lexicon-rule-counts.deescaped.txt";
    //outRuleFile = ruleFile + ".out";
    
    // extract rules and taggedWords from grammar file
    Map<IntTaggedWord, Counter<IntTaggedWord>> wordCounterTagMap = new HashMap<IntTaggedWord, Counter<IntTaggedWord>>();
    Map<IntTaggedWord, Set<IntTaggedWord>> tagsForWord = new HashMap<IntTaggedWord, Set<IntTaggedWord>>();
    Set<IntTaggedWord> preterminalSet = new HashSet<IntTaggedWord>();
    Map<Label, Counter<String>> tagHash = new HashMap<Label, Counter<String>>();
    Set<String> seenEnd = new HashSet<String>();
    Collection<Rule> rules = new ArrayList<Rule>();
    Collection<Rule> extendedRules = new ArrayList<Rule>();
    
    /* Input */
    try {
      RuleFile.isPseudoTag = false;
      RuleFile.parseRuleFile(ruleFile, rules, extendedRules, wordCounterTagMap, 
          tagsForWord, preterminalSet, tagHash, seenEnd); // we don't care much about extended rules, just treat them as rules
      //rules.addAll(extendedRules);
    } catch (IOException e){
      System.err.println("Can't read rule file: " + ruleFile);
      e.printStackTrace();
    }
    
    /* Smooth */
    Map<IntTaggedWord, Counter<IntTaggedWord>> newWordCounterTagMap = RuleFile.smoothWordCounterTagMap(wordCounterTagMap);
    
    /* Output */
    try {
      //RuleFile.printRules(outRuleFile, rules, newWordCounterTagMap);
      RuleFile.printRulesSchemeFormat(outRuleFile, rules, extendedRules, newWordCounterTagMap);
    } catch (IOException e){
      System.err.println("Can't write to: " + outRuleFile);
      e.printStackTrace();
    }
    
    
  }
}
