package util;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;

import parser.SmoothLexicon;
import base.BaseLexicon;
import base.ProbRule;
import base.RuleSet;
import base.TerminalRule;
import edu.stanford.nlp.ling.TaggedWord;
import edu.stanford.nlp.parser.lexparser.IntTaggedWord;
import edu.stanford.nlp.parser.lexparser.TreebankLangParserParams;
import edu.stanford.nlp.stats.Counter;
import edu.stanford.nlp.trees.MemoryTreebank;
import edu.stanford.nlp.trees.Tree;
import edu.stanford.nlp.trees.TreeTransformer;
import edu.stanford.nlp.trees.Treebank;
import edu.stanford.nlp.util.HashIndex;
import edu.stanford.nlp.util.Index;
import edu.stanford.nlp.util.StringUtils;

public class TreeBankFile {
  public static String transformerClassName = null;
  public static String treebankPackClassName = "edu.stanford.nlp.parser.lexparser.EnglishTreebankParserParams";
  
  /**
   * transform trees into a form that could be processed by the system
   * @param treeFile
   * @param transformerClassName
   * @param treebankPackClassName
   * @param pennWriter
   * @return
   */
  public static MemoryTreebank transformTrees(String treeFile, String transformerClassName, String treebankPackClassName){
    /* transformer */
    TreeTransformer transformer = null;
    if(transformerClassName != null){
      try {
        transformer = (TreeTransformer) Class.forName(transformerClassName).newInstance();
      } catch (InstantiationException e) {
        e.printStackTrace();
      } catch (IllegalAccessException e) {
        e.printStackTrace();
      } catch (ClassNotFoundException e) {
        e.printStackTrace();
      }
    } else { // default transformer
      transformer = new TreeTransformer() {
        public Tree transformTree(Tree t) {
          return t;
        }
      };
    }
    
    /* initialize trees & transformedTrees */
    TreebankLangParserParams tlpp = null;
    try {
      tlpp = (TreebankLangParserParams) Class.forName(treebankPackClassName).newInstance();
    } catch (InstantiationException e) {
      e.printStackTrace();
    } catch (IllegalAccessException e) {
      e.printStackTrace();
    } catch (ClassNotFoundException e) {
      e.printStackTrace();
    }
    Treebank trees = tlpp.memoryTreebank();
    MemoryTreebank transformedTrees = tlpp.memoryTreebank();
    
    
    /* load tree file */
    System.err.print("# Loading tree file " + (new File(treeFile)).getAbsolutePath() + " ... ");
    trees.loadPath(treeFile);
    System.err.println("Done!");

    /* transform trees */
    for (Tree t : trees) {
      Tree newTree = transformer.transformTree(t);
      transformedTrees.add(newTree); //transformTree(t, tf));
    }
    
    return transformedTrees;
  }
  
  /**
   * extract rules and words from trees
   */
  public static void extractRulesWordsFromTreebank(
      Treebank treebank, RuleSet ruleSet, Collection<IntTaggedWord> intTaggedWords, 
      Index<String> wordIndex, Index<String> tagIndex,
      Map<Integer, Integer> nonterminalMap) {
    Collection<Tree> trees = new ArrayList<Tree>();
    for (Iterator<Tree> i = treebank.iterator(); i.hasNext();) {
      Tree t = i.next();
      
      // int tagged words
      for(TaggedWord tw : t.taggedYield()){
        intTaggedWords.add(new IntTaggedWord(tw.word(), tw.tag(), wordIndex, tagIndex));
      }
      trees.add(t); 
    }
    
    // build rules
    ruleSet.addAll(Util.tagRulesFromTrees(trees, tagIndex, tagIndex, nonterminalMap));
  }
  
  public static void processTreebank(String treeFile, RuleSet ruleSet,
      Collection<IntTaggedWord> intTaggedWords, 
      Index<String> tagIndex, Index<String> wordIndex, 
      Map<Integer, Integer> nonterminalMap){
    // load treebank
    MemoryTreebank treebank =  transformTrees(treeFile, transformerClassName, treebankPackClassName);
    
    // process
    extractRulesWordsFromTreebank(treebank, ruleSet, intTaggedWords, wordIndex, tagIndex, nonterminalMap);
  }
  
  public static void printHelp(String[] args, String message){
    System.err.println("! " + message);
    System.err.println("TreeBankFile -in inFile -out outFile"); // -opt option]");
    
    // compulsory
    System.err.println("\tCompulsory:");
    System.err.println("\t\t in \t\t input grammar");
    System.err.println("\t\t out \t\t output file");
//    System.err.println("\t\t opt \t\t 1 -- smooth output to format read by Tim's program, " + 
//        "2 -- output to format read by Mark's IO code");
    System.err.println();
    System.exit(1);
  }
  
  public static void main(String[] args){
    if(args.length==0){
      printHelp(args, "No argument");
    }
    System.err.println("TreeBankFile invoked with arguments " + Arrays.asList(args));
    
    /* Define flags */
    Map<String, Integer> flags = new HashMap<String, Integer>();
    // compulsory
    flags.put("-in", new Integer(1)); // input filename
    flags.put("-out", new Integer(1)); // output filename
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
//    int option = -1;
//    if (argsMap.keySet().contains("-opt")) {
//      option = Integer.parseInt(argsMap.get("-opt")[0]);
//    } else {
//      printHelp(args, "No output file, -opt option");
//    }
    
    
    System.err.println("# Input file = " + ruleFile);
    System.err.println("# Output file = " + outRuleFile);
//    System.err.println("# Option = " + option);
    
    // extract rules and taggedWords from treebank file
    Map<Integer, Integer> nonterminalMap = new HashMap<Integer, Integer>();
    Index<String> wordIndex = new HashIndex<String>();
    Index<String> tagIndex = new HashIndex<String>();
    RuleSet ruleSet = new RuleSet(tagIndex, wordIndex);
    
    Collection<IntTaggedWord> intTaggedWords = new ArrayList<IntTaggedWord>();
    
    processTreebank(ruleFile, ruleSet, intTaggedWords, tagIndex, wordIndex, nonterminalMap);
    
    BaseLexicon lex = new SmoothLexicon(wordIndex, tagIndex);
    lex.train(intTaggedWords);
    
    Map<Integer, Counter<Integer>> tag2wordsMap = lex.getTag2wordsMap();
    for(int iT : tag2wordsMap.keySet()){
      Counter<Integer> counter = tag2wordsMap.get(iT);
      for(int iW : counter.keySet()){
        ruleSet.add(new ProbRule(new TerminalRule(iT, Arrays.asList(iW)), 
            Math.exp(counter.getCount(iW))));
      }
    }
    
    try {
      BufferedWriter bw = new BufferedWriter(new FileWriter(outRuleFile));
      for(ProbRule rule : ruleSet.getAllRules()){
        bw.write(rule.toString(tagIndex, wordIndex) + "\n");
      }
      bw.close();
    } catch (IOException e){
      System.err.println("Can't write to: " + outRuleFile);
      e.printStackTrace();
    }
  }
}
