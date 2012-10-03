package parser;

import edu.stanford.nlp.trees.MemoryTreebank;
import edu.stanford.nlp.util.StringUtils;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * An implementation of the Stolcke 1995 chart parsing algorithm that calculates the prefix probability
 * of a string S with respect to a probabilistic context-free grammar G.  The prefix probability is defined as the
 * total probability of the set of all trees T such that S is a prefix of T.  As a side effect,
 * the parser also calculates the total probability of S being a complete sentence in G.
 * <p/>
 * <p/>
 * <p/>
 * The intended usage is as follows: a PrefixProbabilityParser is vended by the {@link EarleyParser#getParser()}
 * method of a a {@link EarleyParser} generator instance, which in turn is obtained by training a generator on a collection of context-free trees (see the
 * {@link EarleyParser#getGenerator(edu.stanford.nlp.trees.Treebank, java.lang.String)} method).
 * Then use the {@link #loadSentence(java.util.List)} method to load a sentence (a {@link List} of words (Strings)) into the parser.
 * The parse chart for the string can then be incrementally constructed with the {@link #parseNextWord()} method.
 * At any time, the current prefix probability and string probability of the words parsed so far can be accessed with the
 * {@link #prefixProbability()} and {@link #stringProbability()} methods respectively.  See the source code of the main method
 * of this class for further details of use.
 *
 * @author Original code based on Roger Levy
 * @author Minh-Thang Luong 2012
 */
public class Main {

  /* note that we use the following conventions:
 *
 * o in an edge combination, <i,j,k> are the leftmost, center, and rightmost parts of the edge
 * combination.  This is in contrast to Stolcke's usage; he uses <k,j,i>
 *
 * o we always use log-probs
 *
 * o unary rules never get actively involved in the chart parsing, so the result of an (i,i) edge
 * combined with an (i,k) edge must be active, not passive.
 *
 * Another note: because we're not doing bona-fide scanning, we tabulate prefix probabilities
 * a bit unorthodoxly.
 *
 */

  public static void printHelp(String[] args, String message){
    System.err.println("! " + message);
    System.err.println(args[0] + " -in inputFileName -out outPrefixName " + 
        "(-grammar grammarFile | -treebank treebankFile) [-id indexFileName]");
    System.exit(1);
  }
  
  public static void main(String[] args) throws Exception {
    System.err.println("EarleyParser invoked with arguments " + Arrays.asList(args));
    
    /* Default parameters */
    String rootSymbol = "ROOT";        
    String transformerClassName = null;
    String treebankPackClassName = "edu.stanford.nlp.parser.lexparser.EnglishTreebankParserParams";
    EarleyParser parser = null;
        
    /* Define flags */
    Map<String, Integer> flags = new HashMap<String, Integer>();
    // compulsory
    flags.put("-in", new Integer(1)); // input filename
    flags.put("-out", new Integer(1)); // output prefix name
    flags.put("-grammar", new Integer(1)); // input grammar file
    flags.put("-treebank", new Integer(1)); // input treebank file, mutually exclusive with -grammar
    
    // optional
    flags.put("-id", new Integer(1)); // sentence indices
    flags.put("-save", new Integer(1)); // save option, 0: save nothing (default), 1: save grammar
    flags.put("-opt", new Integer(1)); // 0: default, 1: old way of handling extended rules
    flags.put("-verbose", new Integer(1));     // 0: no debug info (default), 1: progress info, 2: closure matrices, combine/predict parsing info, 3: details edge/rule info, parser chart, prediction/completion list info, trie
    flags.put("-debug", new Integer(1));
    
    Map<String, String[]> argsMap = StringUtils.argsToMap(args, flags);
    args = argsMap.get(null);
    
    /* verbose option */
    int verbose = 0;
    if (argsMap.keySet().contains("-verbose")) {
      verbose = Integer.parseInt(argsMap.get("-verbose")[0]);
//      StateSpace.verbose = verbose;
//      Grammar.verbose = verbose;
//      RelationMatrix.verbose = verbose;
//      ClosureMatrix.verbose = verbose;
//      PrefixProbabilityParserOld.verbose = verbose;
    }
    
    /* parser opt */
    int parserOpt = 0; // 0: default
    if (argsMap.keySet().contains("-opt")) {
      parserOpt = Integer.parseInt(argsMap.get("-opt")[0]);
    }
    System.err.println("# Parser opt =" + parserOpt);
    System.err.println("# Verbose opt =" + verbose);

    /******************/
    /* get input data */
    /******************/
    List<String> sentences = null;
    if (argsMap.keySet().contains("-in")) { // read from file
      String sentencesFileName = argsMap.get("-in")[0];
      System.err.println("# Input file =" + sentencesFileName);
      sentences = Utility.loadFile(sentencesFileName);
    } else {
      printHelp(args, "No input file, -in option");
    }
    
    /* input indices */
    List<String> indices = null;
    if (argsMap.keySet().contains("-id")) {
      String idFile = argsMap.get("-id")[0];
      indices = Utility.loadFile(idFile);
    } else {
      indices = new ArrayList<String>();
      for (int i = 0; i < sentences.size(); i++) {
        indices.add(i + "");
      }
    }
    
    /******************/
    /* grammar option */
    /******************/
    if (argsMap.keySet().contains("-grammar") && argsMap.keySet().contains("-treebank")){
      printHelp(args, "-grammar and -treebank are mutually exclusive");
    } else if (argsMap.keySet().contains("-grammar")) { // read from grammar file
      String inGrammarFile = argsMap.get("-grammar")[0];
      System.err.println("In grammar file = " + inGrammarFile);
      parser = new EarleyParser(inGrammarFile, rootSymbol);
    } else if (argsMap.keySet().contains("-treebank")) { // read from treebank file      
      // transform trees
      String treeFile = argsMap.get("-treebank")[0];
      MemoryTreebank treebank = Utility.transformTrees(treeFile, transformerClassName, treebankPackClassName);
      parser = new EarleyParser(treebank, rootSymbol);
    } else {
      printHelp(args, "No -grammar or -treebank option");
    }
    
    /*****************/
    /* output prefix */
    /*****************/
    String outPrefix = null;
    if (argsMap.keySet().contains("-out")) {
      outPrefix = argsMap.get("-out")[0];
    
      /* output grammar to file */
      if (argsMap.keySet().contains("-saveGrammar")) {
        String outGrammarFile = outPrefix + ".grammar"; //argsMap.get("-saveGrammar")[0];
        System.err.println("Out grammar file = " + outGrammarFile);
        
        boolean isExp = true;      
        RuleFile.printRules(outGrammarFile, parser.getRules(), parser.getLexicon().getTag2wordsMap(), 
            EarleyParser.WORD_INDEX, EarleyParser.TAG_INDEX, isExp);
      }
    } else {
      printHelp(args, "No output prefix, -out option");
    }

    parser.parseSentences(sentences, indices, outPrefix);  
    //System.err.println("String probability: " + Math.exp(parser.stringProbability()));
    //parser.dumpChart();
  }

    
}

/************* Unused code ************/
// String encoding = "UTF-8";
//flags.put("-encoding", new Integer(1));
//flags.put("-tr", new Integer(1));
//flags.put("-tlpp", new Integer(1));
//flags.put("-penn", new Integer(1)); // output input trees into file using PTB format (filtering out unneeded symbols)
//flags.put("-prefix", new Integer(1)); // prefix model
//
///* encoding */
//if (argsMap.keySet().contains("-encoding")) {
//  encoding = argsMap.get("-encoding")[0];
//}
//

///* debug option */
//if (argsMap.keySet().contains("-debug")) {
//  int debugOpt = Integer.parseInt(argsMap.get("-debug")[0]);
//}

///* transformer name */
//if (argsMap.keySet().contains("-tr")) { // user-input transformer
//  transformerClassName = argsMap.get("-tr")[0];
//}
//
///* treebank params name */
//if (argsMap.keySet().contains("-tlpp")) {        
//  treebankPackClassName = argsMap.get("-tlpp")[0];
//}
//
//
//// penn
//String pennFile = null;
//BufferedWriter pennWriter = null;
//if (argsMap.keySet().contains("-penn")) {
//  pennFile = argsMap.get("-penn")[0];
//}
//if(pennFile != null){
//  pennWriter = new BufferedWriter(new FileWriter(pennFile));
//}
//if(pennWriter != null){
//  for(Tree tree : transformedTrees){
//    String pennString = tree.pennString();
//    pennString = pennString.replaceAll("\\s+", " ");
//    pennString = pennString.replaceAll("^\\(ROOT ", "(");
//    try {
//      pennWriter.write(pennString + "\n");
//    } catch (IOException e) {
//      e.printStackTrace();
//    }
//  }
//}
//
//if(outWriter != null){
//  outWriter.close();
//}
//if(synOutWriter != null){
//  synOutWriter.close();
//}
//if(lexOutWriter != null){
//  lexOutWriter.close();
//}
//if(stringOutWriter != null){
//  stringOutWriter.close();
//}
//if(idReader != null){
//  idReader.close();
//}
//
//
//if(pennWriter != null){
//  pennWriter.close();
//}
//
//// parse sentences
//String prefixModel = "edu.stanford.nlp.parser.prefixparser.PrefixProbabilityParserOld";
//if (argsMap.keySet().contains("-prefix")) {
//  prefixModel = argsMap.get("-prefix")[0];
//}
//PrefixProbabilityParser parser = generator.getParser(prefixModel);

/*
if(isBinarize.equalsIgnoreCase("true")){
  System.out.println("# Penn Tree Read ...");
  // treebank
  HeadFinder hf = new ModCollinsHeadFinder();
  TreebankLanguagePack tlp = new PennTreebankLanguagePack();
  
  TreeReaderFactory trf = new TreeReaderFactory() {
    public TreeReader newTreeReader(Reader in) {
      return new PennTreeReader(in, new LabeledScoredTreeFactory(
          new CategoryWordTagFactory()), new BobChrisTreeNormalizer());
    }
  };
  trees = new DiskTreebank(trf);
  
  TreeReaderFactory trf1 = new TreeReaderFactory() {
    public TreeReader newTreeReader(Reader in) {
      return new PennTreeReader(in, new LabeledScoredTreeFactory(
          new CategoryWordTagFactory()), new BobChrisTreeNormalizer());
    }
  };
  transformedTrees = new MemoryTreebank(trf1);
  
  // transformer
  boolean insideFactor = false;
  boolean mf = false;
  int mo = 1;
  boolean uwl = false;
  boolean uat = false;
  double sst = 20.0;
  boolean mfs = false;
  transformer = new TreeBinarizer(hf, tlp, insideFactor, mf, mo, uwl, uat, sst, mfs);
}
*/