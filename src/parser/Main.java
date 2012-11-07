package parser;

import edu.stanford.nlp.trees.MemoryTreebank;
import edu.stanford.nlp.util.StringUtils;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import base.ClosureMatrix;
import base.RelationMatrix;
import base.ProbRule;


import util.RuleFile;
import util.Util;

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
    System.err.println("Main -in inFile -out outPrefix " + "(-grammar grammarFile | -treebank treebankFile) \n" + 
        "\t[-root rootSymbol] [-sparse] [-normalprob] [-scale] [-io] [-verbose opt]");
    // [-leftwildcard] 
    // [-id indexFileName] 
    
    // compulsory
    System.err.println("\tCompulsory:");
    System.err.println("\t\t in \t\t input filename, i.e. sentences to parse");
    System.err.println("\t\tgrammar|treebank \t\t either read directly from a grammar file or from a treebank." +
    		"For the latter, a grammar file will be output as outPrefix.grammar .");
    System.err.println("\t\t out \t\t output prefix to name output files");
    System.err.println();
    
    // optional
    System.err.println("\t Optional:");
    System.err.println("\t\t root \t\t specify the start symbol of sentences (default \"ROOT\")");
    System.err.println("\t\t sparse \t\t optimize for sparse grammars (default: run with dense grammars)");
    System.err.println("\t\t normalprob \t\t if specified, perform numeric computation in normal prob (cf. log-prob). This switch is best to be used with -scale.");
    System.err.println("\t\t scale \t\t if specified, perform rescaling");
    System.err.println("\t\t io \t\t if specified, inside-outside: compute outside probabilities to estimate expected rule probabilities. By default, the program compute inner/forward probabilities as well as surprisal values");
    System.err.println("\t\t verbose \t\t -1 -- no debug info (default), " + 
        "0: surprisal per word, 1-4 -- increasing more details");
    
//    System.err.println("\t\t leftwildcard \t\t if specified, using left wildcard to group edges: " + 
//        "Earley edges having the same parent and expecting children " +
//        "(children on the right of the dot) are collapsed into the same edge X -> * . \\alpha");
    
    System.exit(1);
  }
  
  public static void main(String[] args) {
    if(args.length==0){
      printHelp(args, "No argument");
    }
    System.err.println("EarleyParser invoked with arguments " + Arrays.asList(args));
    
    /* Default parameters */        
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
    flags.put("-root", new Integer(1)); // root symbol
    
    // optional
    flags.put("-id", new Integer(1)); // sentence indices
    flags.put("-sparse", new Integer(0)); // optimize for sparse grammars
    flags.put("-normalprob", new Integer(0)); // normal prob 
    flags.put("-scale", new Integer(0)); // scaling
    flags.put("-io", new Integer(0)); // inside-outside computation 
    flags.put("-verbose", new Integer(1)); 
    
    Map<String, String[]> argsMap = StringUtils.argsToMap(args, flags);
    args = argsMap.get(null);
    
    /* root symbol */
    String rootSymbol = "ROOT";
    if (argsMap.keySet().contains("-root")) {
      rootSymbol = argsMap.get("-root")[0];
    }
    
    /* verbose option */
    int verbose = -1;
    if (argsMap.keySet().contains("-verbose")) {
      verbose = Integer.parseInt(argsMap.get("-verbose")[0]);
      RelationMatrix.verbose = verbose;
      ClosureMatrix.verbose = verbose;
      EdgeSpace.verbose = verbose;
      Prediction.verbose = verbose;
      Completion.verbose = verbose;
      EarleyParser.verbose = verbose;
    }
    
    /* sparse opt */
    int parserOpt = 0; // dense
    if (argsMap.keySet().contains("-sparse")) {
      parserOpt = 1; // sparse
    }
    
    /* normalprob */
    boolean isLogProb = true;
    if (argsMap.keySet().contains("-normalprob")) {
      isLogProb = false;
    }
    
    /* scale opt */
    boolean isScaling = false;
    if (argsMap.keySet().contains("-scale")) {
      isScaling = true;
    }
    
    /* io opt */
    boolean isComputeOutside = false;
    if (argsMap.keySet().contains("-io")) {
      isComputeOutside = true;
    }
    
    System.err.println("# Root symbol = " + rootSymbol);
    System.err.println("# isSparse = " + (parserOpt==1));
    System.err.println("# isLogProb = " + isLogProb);
    System.err.println("# isScaling = " + isScaling);
    System.err.println("# isComputeOutside = " + isComputeOutside);
    System.err.println("# Verbose opt = " + verbose);

    /******************/
    /* get input data */
    /******************/
    List<String> sentences = null;
    if (argsMap.keySet().contains("-in")) { // read from file
      String sentencesFileName = argsMap.get("-in")[0];
      System.err.println("# Input file =" + sentencesFileName);
      try {
        sentences = Util.loadFile(sentencesFileName);
      } catch (IOException e) {
        System.err.println("! Main: error loading input file " + sentencesFileName);
        System.err.println(e);
        System.exit(1);
      }
    } else {
      printHelp(args, "No inpu file, -in option");
    }
    
    /* input indices */
    List<String> indices = null;
    if (argsMap.keySet().contains("-id")) {
      String idFile = argsMap.get("-id")[0];
      try {
        indices = Util.loadFile(idFile);
      } catch (IOException e) {
        System.err.println("! Main: error loading id file " + idFile);
        System.exit(1);
      }
    } else {
      indices = new ArrayList<String>();
      for (int i = 0; i < sentences.size(); i++) {
        indices.add(i + "");
      }
    }
    

    /*****************/
    /* output prefix */
    /*****************/
    String outPrefix = null;
    if (argsMap.keySet().contains("-out")) {
      outPrefix = argsMap.get("-out")[0];
      File outDir = (new File(outPrefix)).getParentFile();
      if(!outDir.exists()){
        System.err.println("# Creating output directory " + outDir.getAbsolutePath());
        outDir.mkdirs();
      }
    } else {
      printHelp(args, "No output prefix, -out option");
    }
    
    /******************/
    /* grammar option */
    /******************/
    
    if (argsMap.keySet().contains("-grammar") && argsMap.keySet().contains("-treebank")){
      printHelp(args, "-grammar and -treebank are mutually exclusive");
    } else if (argsMap.keySet().contains("-grammar")) { // read from grammar file
      String inGrammarFile = argsMap.get("-grammar")[0];
      System.err.println("In grammar file = " + inGrammarFile);
      
      if(parserOpt==0){ // dense
        parser = new EarleyParserDense(inGrammarFile, rootSymbol, isScaling, 
            isLogProb, isComputeOutside);
      } else if(parserOpt==1){ // sparse
        parser = new EarleyParserSparse(inGrammarFile, rootSymbol, isScaling, 
            isLogProb, isComputeOutside);
      } else {
        assert(false);
      }
      
    } else if (argsMap.keySet().contains("-treebank")) { // read from treebank file      
      // transform trees
      String treeFile = argsMap.get("-treebank")[0];
      MemoryTreebank treebank = Util.transformTrees(treeFile, transformerClassName, treebankPackClassName);
      
      if(parserOpt==0){ // dense
        parser = new EarleyParserDense(treebank, rootSymbol, isScaling, 
            isLogProb, isComputeOutside);
      } else if(parserOpt==1){ // sparse
        parser = new EarleyParserSparse(treebank, rootSymbol, isScaling, 
            isLogProb, isComputeOutside);
      } else {
        assert(false);
      }
      
      // save grammar
      String outGrammarFile = outPrefix + ".grammar"; //argsMap.get("-saveGrammar")[0];
      System.err.println("Out grammar file = " + outGrammarFile);
      
      boolean isExp = true;      
      try {
        // ignore root rule
        Collection<ProbRule> newRules = new ArrayList<ProbRule>();
        ProbRule rootRule = parser.getRootRule();
        for(ProbRule rule : parser.getRules()){
          if(!rule.equals(rootRule)){
            newRules.add(rule);
          }
        }
        RuleFile.printRules(outGrammarFile, newRules, parser.getLexicon().getTag2wordsMap(), 
            parser.getParserWordIndex(), parser.getParserTagIndex(), isExp);
      } catch (IOException e) {
        System.err.println("! Main: error printing rules to " + outGrammarFile);
        System.exit(1);
      }
    } else {
      printHelp(args, "No -grammar or -treebank option");
    }
        
    /***********/
    /* Parsing */
    /***********/
    try {
      parser.parseSentences(sentences, indices, outPrefix);
    } catch (IOException e) {
      System.err.println("! Main: error printing output during parsing to outprefix " + outPrefix);
      System.exit(1);
    }  
    //System.err.println("String probability: " + Math.exp(parser.stringProbability()));
    //parser.dumpChart();
  }

    
}

/************* Unused code ************/
///* leftwildcard opt */
//boolean isLeftWildcard = false;
//if (argsMap.keySet().contains("-leftwildcard")) {
//isLeftWildcard = true;
//}

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
