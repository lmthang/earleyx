package parser;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Iterator;
import java.util.List;

import edu.stanford.nlp.ling.TaggedWord;
import edu.stanford.nlp.parser.lexparser.IntTaggedWord;
import edu.stanford.nlp.parser.lexparser.TreebankLangParserParams;
import edu.stanford.nlp.stats.Distribution;
import edu.stanford.nlp.stats.TwoDimensionalCounter;
import edu.stanford.nlp.trees.MemoryTreebank;
import edu.stanford.nlp.trees.Tree;
import edu.stanford.nlp.trees.TreeTransformer;
import edu.stanford.nlp.trees.Treebank;
import edu.stanford.nlp.util.Index;
import edu.stanford.nlp.util.Pair;

public class Utility {
  /**
   * returns a collection of scored rules corresponding to all non-terminal productions from a collection of trees.
   */
  public static Collection<Rule> rulesFromTrees(Collection<Tree> trees, 
      Index<String> motherIndex, Index<String> childIndex) {
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
        ruleCounts.incrementCount(motherIndex.indexOf(subTree.value(), true), 
            getChildrenFromTree(subTree.children(), childIndex)); 
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
      Treebank treebank, Index<String> wordIndex, Index<String> tagIndex) {
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
    rules.addAll(Utility.rulesFromTrees(trees, tagIndex, tagIndex));
    
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
  
  public static void initToNegativeInfinity(double[] dl) {
    for (int i = 0; i < dl.length; i++) {
      dl[i] = Double.NEGATIVE_INFINITY;
    }
  }
  
  public static void initToNegativeInfinity(double[][] dl) {
    for (int i = 0; i < dl.length; i++) {
      double[] doubles = dl[i];
      for (int j = 0; j < doubles.length; j++) {
        doubles[j] = Double.NEGATIVE_INFINITY;
      }
    }
  }
}
