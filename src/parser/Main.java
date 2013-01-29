package parser;

import edu.stanford.nlp.util.StringUtils;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import base.ClosureMatrix;
import base.RelationMatrix;
import base.ProbRule;


import util.RuleFile;
import util.Util;

/**
 * An implementation of the Stolcke 1995 chart parsing algorithm.
 *
 * @author Original code based on Roger Levy 2004
 * @author Rewritten by Minh-Thang Luong 2012 with many other features (see README)
 * 
 */
public class Main {
  public static void printHelp(String[] args, String message){
    System.err.println("! " + message);
    System.err.println("Main -in inFile  -out outPrefix (-grammar grammarFile | -treebank treebankFile) " +
        "-obj objectives\n" + 
        "\t[-root rootSymbol] [-io opt -maxiteration n -intermediate n] [-sparse] [-normalprob] [-scale] [-verbose opt]");
    
    // compulsory
    System.err.println("\tCompulsory:");
    System.err.println("\t\t in \t\t input filename, i.e. sentences to parse");
    System.err.println("\t\t out \t\t output prefix to name output files. ");
    System.err.println("\t\tgrammar|treebank \t\t either read directly from a grammar file or from a treebank." +
    		"For the latter, a grammar file will be output as outPrefix.grammar .");
    System.err.println("\t\t obj \t\t a comma separated list consitsing of any of the following values: " + 
        "surprisal, stringprob, viterbi, socialmarginal. Default is \"surprisal,stringprob,viterbi\". Output files will be outPrefix.obj .");
    System.err.println();

    // optional
    System.err.println("\t Optional:");
    System.err.println("\t\t root \t\t specify the start symbol of sentences (default \"ROOT\")");
    System.err.println("\t\t sparse \t\t optimize for sparse grammars (default: run with dense grammars)");
    System.err.println("\t\t normalprob \t\t perform numeric computation in normal prob (cf. log-prob). This switch is best to be used with -scale.");
    System.err.println("\t\t scale \t\t rescaling approach to parse extremely long sentences");
    System.err.println("\t\t verbose \t\t -1 -- no debug info (default), " + 
        "0: surprisal per word, 1-4 -- increasing more details");
    
    System.err.println("\n\t\t io \t\t run inside-outside algorithm, " + 
        "output final grammar to outPrefix.io.grammar. 1: EM, 2: VB");
    System.err.println("\t\t maxiteration \t\t number of iterations to run Inside-Outside. If not specified, will run until convergence.");
    System.err.println("\t\t intermediate \t\t Output grammars and parse trees every intermediate iterations.");
    System.exit(1);
  }
  
  public static void main(String[] args) {
    if(args.length==0){
      printHelp(args, "No argument");
    }
    System.err.println("EarleyParser invoked with arguments " + Arrays.asList(args));
    
    /* Default parameters */        
    EarleyParser parser = null;
        
    /* Define flags */
    Map<String, Integer> flags = new HashMap<String, Integer>();
    // compulsory
    flags.put("-in", new Integer(1)); // input filename
    flags.put("-out", new Integer(1)); // output prefix name
    flags.put("-grammar", new Integer(1)); // input grammar file
    flags.put("-treebank", new Integer(1)); // input treebank file, mutually exclusive with -grammar
    flags.put("-obj", new Integer(1)); // objective values
    
    // optional
    flags.put("-root", new Integer(1)); // root symbol
    flags.put("-io", new Integer(1)); // inside-outside computation
    flags.put("-maxiteration", new Integer(1)); // number of iterations to run IO
    flags.put("-id", new Integer(1)); // sentence indices
    flags.put("-sparse", new Integer(0)); // optimize for sparse grammars
    flags.put("-normalprob", new Integer(0)); // normal prob 
    flags.put("-scale", new Integer(0)); // scaling 
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
    int insideOutsideOpt = 0;
    if (argsMap.keySet().contains("-io")) {
      insideOutsideOpt = Integer.parseInt(argsMap.get("-io")[0]);
      if(insideOutsideOpt!=1 && insideOutsideOpt!=2){
        printHelp(args, "insideOutsideOpt!=1 && insideOutsideOpt!=2");
      }
    }
    int maxiteration = 0;
    if (argsMap.keySet().contains("-maxiteration")) {
      maxiteration = Integer.parseInt(argsMap.get("-maxiteration")[0]);
      if(maxiteration<=0){
        printHelp(args, "maxiteration<=0");
      }
    }
    int intermediate = 0;
    if (argsMap.keySet().contains("-intermediate")) {
      intermediate = Integer.parseInt(argsMap.get("-intermediate")[0]);
      if(intermediate<=0 || (maxiteration>0 && intermediate>maxiteration)){
        printHelp(args, "intermediate<=0 || (maxiteration>0 && intermediate>maxiteration)");
      }
    }
    
    /* obj opt */
    String objString = "";
    if (argsMap.keySet().contains("-obj")) {
      objString = argsMap.get("-obj")[0];
    }
    if(objString.equals("")){
      // default values
      objString = EarleyParser.SURPRISAL_OBJ + "," + EarleyParser.STRINGPROB_OBJ + "," + EarleyParser.VITERBI_OBJ;
    }
    
    System.err.println("# Root symbol = " + rootSymbol);
    System.err.println("# Objectives = " + objString);
    System.err.println("# isSparse = " + (parserOpt==1));
    System.err.println("# isLogProb = " + isLogProb);
    System.err.println("# isScaling = " + isScaling);
    System.err.println("# insideOutsideOpt = " + insideOutsideOpt);
    System.err.println("# maxIteration = " + maxiteration);
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
      printHelp(args, "No input file, -in option");
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
      File outDir = (new File(outPrefix)).getAbsoluteFile().getParentFile();
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
    String inGrammarFile = null;
    int inGrammarType = 0; // 1: grammar, 2: treebank
    if (argsMap.keySet().contains("-grammar") && argsMap.keySet().contains("-treebank")){
      printHelp(args, "-grammar and -treebank are mutually exclusive");
    } else if (argsMap.keySet().contains("-grammar")) { // read from grammar file
      inGrammarFile = argsMap.get("-grammar")[0];
      inGrammarType = 1;
      System.err.println("In grammar file = " + inGrammarFile);
    } else if (argsMap.keySet().contains("-treebank")) { // read from treebank file
      inGrammarFile = argsMap.get("-treebank")[0];
      inGrammarType = 2;
      System.err.println("In treebank file = " + inGrammarFile);
    } else {
      printHelp(args, "No -grammar or -treebank option");
    }
    
    if(parserOpt==0){ // dense
      parser = new EarleyParserDense(inGrammarFile, inGrammarType, rootSymbol, isScaling, 
          isLogProb, insideOutsideOpt, objString);
    } else if(parserOpt==1){ // sparse
      parser = new EarleyParserSparse(inGrammarFile, inGrammarType, rootSymbol, isScaling, 
          isLogProb, insideOutsideOpt, objString);
    } else {
      assert(false);
    }
    
    if(inGrammarType==2){
      // save grammar
      String outGrammarFile = outPrefix + ".grammar";
      
      try {
        List<ProbRule> allRules = parser.getAllRules();
        RuleFile.printRules(outGrammarFile, allRules, parser.getParserWordIndex(), parser.getParserTagIndex());
      } catch (IOException e) {
        System.err.println("! Main: error printing rules to " + outGrammarFile);
        System.exit(1);
      }
    }
        
    /***********/
    /* Parsing */
    /***********/
    try {
      if(insideOutsideOpt==0){
        parser.parseSentences(sentences, indices, outPrefix);
      } else if(insideOutsideOpt>0){
        parser.insideOutside(sentences, outPrefix, maxiteration, intermediate);
        
        // output rule prob
        List<ProbRule> allRules = parser.getAllRules();
        String outGrammarFile = outPrefix + ".iogrammar"; //argsMap.get("-saveGrammar")[0];
        RuleFile.printRules(outGrammarFile, allRules, parser.getParserWordIndex(), parser.getParserTagIndex());
      }
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
