package utility;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.StringReader;
import java.text.DecimalFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Set;

import cern.colt.matrix.DoubleMatrix2D;

import parser.Completion;
import parser.Prediction;
import parser.Rule;
import parser.EdgeSpace;
import parser.TerminalRule;

import edu.stanford.nlp.ling.TaggedWord;
import edu.stanford.nlp.parser.lexparser.IntTaggedWord;
import edu.stanford.nlp.parser.lexparser.TreebankLangParserParams;
import edu.stanford.nlp.stats.Counter;
import edu.stanford.nlp.stats.Distribution;
import edu.stanford.nlp.stats.TwoDimensionalCounter;
import edu.stanford.nlp.trees.MemoryTreebank;
import edu.stanford.nlp.trees.Tree;
import edu.stanford.nlp.trees.TreeTransformer;
import edu.stanford.nlp.trees.Treebank;
import edu.stanford.nlp.util.Index;
import edu.stanford.nlp.util.Pair;

public class Utility {
  public static DecimalFormat df = new DecimalFormat("0.0");
  public static DecimalFormat df1 = new DecimalFormat("0.0000");
  public static DecimalFormat df3 = new DecimalFormat("000");

  public static BufferedReader getBufferedReaderFromFile(String inFile) throws FileNotFoundException{
    return new BufferedReader(new InputStreamReader(new FileInputStream(inFile)));
  }
  
  public static BufferedReader getBufferedReaderFromString(String str) throws FileNotFoundException{
    return new BufferedReader(new StringReader(str));
  }

  public static int[][] permutationMatrix(int size){
    // C(n, i): # ways of choosing i numbers from n numbers
    // C(n, 0) = C(n, n) = 1
    // C(n, i) = C(n-1, i) + C(n-1, i-1)
    
    int[][] c = new int[size+1][size+1];
    
    // init
    for (int n = 0; n <=size; n++) {
      c[n][0] = 1;
      c[n][n] = 1;
    }
    
    for (int n = 2; n<=size; n++) {
      for (int i = 1; i < n; i++) {
        c[n][i] = c[n-1][i] + c[n-1][i-1];
      }
    }
    
//    for (int i = 0; i < c.length; i++) {
//      for (int j = 0; j <=i; j++) {
//        System.err.print(df3.format(c[i][j]) + " ");
//      }
//      System.err.println();
//    }
    return c;
  }
  public static List<Integer> getNonterminals(Map<Integer, Integer> nonterminalMap){
    List<Map.Entry<Integer, Integer>> list = new LinkedList<Map.Entry<Integer, Integer>>(nonterminalMap.entrySet());

    Collections.sort(list, new Comparator<Map.Entry<Integer, Integer>>() {
      public int compare(Map.Entry<Integer, Integer> m1, Map.Entry<Integer, Integer> m2) {
        return (m1.getValue()).compareTo(m2.getValue());
      }
    });

    List<Integer> result = new LinkedList<Integer>();
    for (Map.Entry<Integer, Integer> entry : list) {
        result.add(entry.getKey());
    }
    
    return result;
  }
  /**
   * returns a collection of scored rules corresponding to all non-terminal productions from a collection of trees.
   */
  public static Collection<Rule> rulesFromTrees(Collection<Tree> trees, 
      Index<String> motherIndex, Index<String> childIndex, Map<Integer, Integer> nonterminalMap) {
    Collection<Rule> rules = new ArrayList<Rule>();
    TwoDimensionalCounter<Integer, List<Integer>> ruleCounts = 
      new TwoDimensionalCounter<Integer, List<Integer>>();
    
    // go through trees
    for(Tree tree:trees){
      for(Tree subTree : tree.subTreeList()){
        if (subTree.isLeaf() || subTree.isPreTerminal()) { // ignore leaf and preterminal nodes
          continue;
        }
     
        // increase count
        int index = motherIndex.indexOf(subTree.value(), true);
        ruleCounts.incrementCount(index, 
            getChildrenFromTree(subTree.children(), childIndex));
        
        // add nonterminals
        if(!nonterminalMap.containsKey(index)){
          nonterminalMap.put(index, nonterminalMap.size());
        }
      }
    }

    for(int mother: ruleCounts.firstKeySet()){ // go through all rules
      // normalize w.r.t to parent node
      Distribution<List<Integer>> normalizedChildren = 
        Distribution.getDistribution(ruleCounts.getCounter(mother));
      for(List<Integer> childList : normalizedChildren.keySet()){
        rules.add(new Rule(mother, childList, normalizedChildren.getCount(childList)));
      }
    }

    return rules;
  }
  

  private static List<Integer> getChildrenFromTree(Tree[] trees, Index<String> childIndex) {
    List<Integer> children = new ArrayList<Integer>(trees.length);
    for (int i = 0; i < trees.length; i++) {
      Tree tree = trees[i];
      children.add(childIndex.indexOf(tree.value(), true));
    }
    return children;
  }
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
    System.err.print("# Loading tree file " + treeFile + " ... ");
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
  public static Pair<Collection<Rule>, Collection<IntTaggedWord>> extractRulesWordsFromTreebank(
      Treebank treebank, Index<String> wordIndex, Index<String> tagIndex,
      Map<Integer, Integer> nonterminalMap) {
    Collection<IntTaggedWord> intTaggedWords = new ArrayList<IntTaggedWord>();
    Collection<Rule> rules = new ArrayList<Rule>();
    
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
    rules.addAll(Utility.rulesFromTrees(trees, tagIndex, tagIndex, nonterminalMap));
    
    return new Pair<Collection<Rule>, Collection<IntTaggedWord>>(rules, intTaggedWords);
  }
  
  /**
   * Load a file into a list of strings, one line per string
   * 
   * @param inFile
   * @return
   * @throws IOException
   */
  public static List<String> loadFile(String inFile) throws IOException{
    BufferedReader br = new BufferedReader(new InputStreamReader(new FileInputStream(inFile)));
    List<String> lines = new ArrayList<String>();
    
    String line = null;
    while((line = br.readLine()) != null)
      lines.add(line);
    
    return lines;
  }
  
  /**
   * Output results for each sentence, each word corresponds to a number
   * 
   * @param sentenceString
   * @param outWriter
   * @param results
   * @throws IOException
   */
  public static void outputSentenceResult(String sentenceString, 
      BufferedWriter outWriter, List<Double> results) throws IOException {
    List<String> sentence = Arrays.asList(sentenceString.split("\\s+"));
    for (int i = 0; i < sentence.size(); i++) {
      outWriter.write(sentence.get(i) + " " + results.get(i) + "\n");
    }
    outWriter.write("#! Done\n");
    outWriter.flush();
  }
  
  public static void init(double[] dl, double value) {
    for (int i = 0; i < dl.length; i++) {
      dl[i] = value;
    }
  }
  
  public static void init(double[][] dl, double value) {
    for (int i = 0; i < dl.length; i++) {
      double[] doubles = dl[i];
      for (int j = 0; j < doubles.length; j++) {
        doubles[j] = value;
      }
    }
  }
  
  /** Print to string methods **/
  // print boolean array
  public static String sprint(boolean[] values){
    StringBuffer sb = new StringBuffer("[");
    
    if(values.length > 0){
      for(boolean value : values){
        sb.append(value + ", ");
      }
    }
    sb.delete(sb.length()-2, sb.length());
    sb.append("]");
    return sb.toString();
  }

  public static String sprint(Index<String> index){
    StringBuffer sb = new StringBuffer("[");
    
    if(index.size() > 0){
      for (int i = 0; i < index.size(); i++) {
        sb.append(index.get(i) + ", ");
      }
    }
    sb.delete(sb.length()-2, sb.length());
    sb.append("]");
    return sb.toString();
  }
  
  public static String sprint(Map<Integer, Double> valueMap, Index<String> tagIndex){
    StringBuffer sb = new StringBuffer("(");
    
    if(valueMap.size() > 0){
      for (int iT : valueMap.keySet()) {
        double score = valueMap.get(iT);
        if (score<=0){
          score = Math.exp(score);
        }
        sb.append(tagIndex.get(iT) + "=" + df1.format(score) + ", ");
      }
      sb.delete(sb.length()-2, sb.length());
    }
    sb.append(")");
    return sb.toString();
  }
  
  // print Prediction[]
  public static String sprint(Prediction[] predictions, EdgeSpace edgeSpace, Index<String> tagIndex){
    StringBuffer sb = new StringBuffer("(");
    for(Prediction prediction : predictions){
      sb.append(prediction.toString(edgeSpace, tagIndex) + ", ");
    }
    if (predictions.length > 0) {
      sb.delete(sb.length()-2, sb.length());
    }
    sb.append(")");
    return sb.toString();
  }

  // print Completion[]
  public static String sprint(Completion[] completions, EdgeSpace edgeSpace, Index<String> tagIndex){
    StringBuffer sb = new StringBuffer("[");
    for(Completion completion : completions){
      sb.append(completion.toString(edgeSpace, tagIndex) + ", ");
    }
    if (completions.length > 0) {
      sb.delete(sb.length()-2, sb.length());
    }
    sb.append("]");
    return sb.toString();
  }
  
  public static String sprint(Index<String> tagIndex, Collection<Integer> indices){
    StringBuffer sb = new StringBuffer("[");
    for(int index : indices){
      //sb.append("(" + index + ", " + tagIndex.get(index) + ") ");
      sb.append(tagIndex.get(index) + ", ");
    }
    sb.delete(sb.length()-2, sb.length());
    sb.append("]");
    return sb.toString();
  }
  
  public static String sprint(Collection<Rule> rules, Index<String> wordIndex, Index<String> tagIndex){
    StringBuffer sb = new StringBuffer("[");
    for(Rule rule : rules){
      if(rule instanceof TerminalRule){
        sb.append(rule.toString(tagIndex, wordIndex) + ", ");
      } else {
        sb.append(rule.toString(tagIndex, tagIndex) + ", ");
      }
    }
    sb.delete(sb.length()-2, sb.length());
    sb.append("]");
    return sb.toString();
  }
  
  public static String schemeSprint(Collection<Rule> rules, Index<String> wordIndex, Index<String> tagIndex){
    StringBuffer sb = new StringBuffer("[");
    for(Rule rule : rules){
      if(rule instanceof TerminalRule){
        sb.append(rule.schemeString(tagIndex, wordIndex) + ", ");
      } else {
        sb.append(rule.schemeString(tagIndex, tagIndex) + ", ");
      }
    }
    sb.delete(sb.length()-2, sb.length());
    sb.append("]");
    return sb.toString();
  }
  
  public static String sprint(Map<Integer, Counter<Integer>> int2intCounter
      , Index<String> keyIndex, Index<String> valueIndex){
    StringBuffer sb = new StringBuffer("{");
    for(int iKey : int2intCounter.keySet()){
      Counter<Integer> counter = int2intCounter.get(iKey);
      sb.append(keyIndex.get(iKey) + "={");
      for(int iValue : counter.keySet()){
        sb.append(valueIndex.get(iValue) + "=" + counter.getCount(iValue) + ", ");
      }
      sb.delete(sb.length()-2, sb.length());
      sb.append("}, ");
    }
    sb.delete(sb.length()-2, sb.length());
    return sb.toString();
  }

  public static String sprintWord2Tags(Map<Integer, Set<IntTaggedWord>> word2tagsMap
      , Index<String> wordIndex, Index<String> tagIndex){
    StringBuffer sb = new StringBuffer("{");
    for(int iW : word2tagsMap.keySet()){
      Set<IntTaggedWord> itwSet = word2tagsMap.get(iW);
      sb.append(wordIndex.get(iW) + "=[");
      for(IntTaggedWord itw : itwSet){
        sb.append(itw.toString(wordIndex, tagIndex) + ", ");
      }
      sb.delete(sb.length()-2, sb.length());
      sb.append("}, ");
    }
    sb.delete(sb.length()-2, sb.length());
    return sb.toString();
  }
  
  public static String sprint(Set<IntTaggedWord> itws, 
      Index<String> wordIndex, Index<String> tagIndex){
    StringBuffer sb = new StringBuffer("[");
    for(IntTaggedWord itw : itws){
      sb.append(itw.toString(wordIndex, tagIndex) + ", ");
    }
    sb.delete(sb.length()-2, sb.length());
    sb.append("]");
    return sb.toString();
  }
  
  public static String sprint(DoubleMatrix2D matrix){
    StringBuffer sb = new StringBuffer("");
    int numRows = matrix.rows();
    int numCols = matrix.columns();
    
    for (int i = 0; i < numRows; i++) {
      for (int j = 0; j < numCols; j++) {
        sb.append(matrix.get(i, j) + " ");
      }
      sb.deleteCharAt(sb.length()-1);
      sb.append("\n");
    }
    sb.deleteCharAt(sb.length()-1);
    
    return sb.toString();
  }  
}
