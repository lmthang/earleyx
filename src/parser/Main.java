package parser;

import edu.stanford.nlp.util.StringUtils;
import edu.stanford.nlp.util.concurrent.MulticoreWrapper;
import edu.stanford.nlp.util.concurrent.ThreadsafeProcessor;
import induction.InsideOutside;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

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
        "\t[-root rootSymbol] [-sparse] [-normalprob] [-scale] [-decode opt] [-verbose opt]" +
        "\t[-thread n] [-filter length]" + 
        "\t[-io opt -maxiteration n -intermediate n -minprob f]\n");
    
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
    System.err.println("\n\t\t decode \t\t perform decoding, " + 
        "output parse trees to outPrefix.opt " +
        "opt should be either \"viterbi\", \"marginal\" or \"socialmarginal\"");
    System.err.println("\t\t verbose \t\t -1 -- no debug info (default), " + 
        "0: surprisal per word, 1-4 -- increasing more details");
    
    System.err.println("\t\t thread \t\t if value > 1, use multi-threaded version of the parser");
    System.err.println("\t\t filter \t\t if value > 0, filter sentences that are >= filtered length");
    
    System.err.println("\n\t\t io \t\t run inside-outside algorithm, " + 
        "output final grammar to outPrefix.io.grammar. opt should be \"em\" or \"vb\"");
    System.err.println("\t\t maxiteration \t\t number of iterations to run Inside-Outside. If not specified, will run until convergence.");
    System.err.println("\t\t intermediate \t\t Output grammars and parse trees every intermediate iterations.");
    System.err.println("\t\t minprob \t\t prunning rules with probs below threshold. If not specified, no pruning.");
    System.exit(1);
  }
  
  public static void main(String[] args) throws IOException {
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
    flags.put("-id", new Integer(1)); // sentence indices
    flags.put("-sparse", new Integer(0)); // optimize for sparse grammars
    flags.put("-normalprob", new Integer(0)); // normal prob 
    flags.put("-scale", new Integer(0)); // scaling 
    flags.put("-decode", new Integer(1)); // decode option
    flags.put("-verbose", new Integer(1)); 
    
    flags.put("-thread", new Integer(1)); // thread option
    flags.put("-filter", new Integer(1)); // filter option
    
    flags.put("-io", new Integer(1)); // inside-outside computation
    flags.put("-maxiteration", new Integer(1)); // number of iterations to run IO
    flags.put("-minprob", new Integer(1)); // pruning threshold
    flags.put("-intermediate", new Integer(1)); // print grammars and parsers every intermediate iteration
    
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
      RuleFile.verbose = verbose;
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
    
    /* decode opt */
    String decodeOptStr = "";
    if (argsMap.keySet().contains("-decode")) {
      decodeOptStr = argsMap.get("-decode")[0];
      if(!decodeOptStr.equalsIgnoreCase("viterbi") && !decodeOptStr.equalsIgnoreCase("marginal") && !decodeOptStr.equalsIgnoreCase("socialmarginal")){
        printHelp(args, "! Invalid -decode option " + decodeOptStr);
      }
    }
    
    /* scale opt */
    boolean isScaling = false;
    if (argsMap.keySet().contains("-scale")) {
      isScaling = true;
    }
    
    /* thread option */
    int numThreads = 1;
    if (argsMap.keySet().contains("-thread")) {
      numThreads = Integer.parseInt(argsMap.get("-thread")[0]);
    }
    
    /* filter option */
    int filterLen = 0;
    if (argsMap.keySet().contains("-filter")) {
    	filterLen = Integer.parseInt(argsMap.get("-filter")[0]);
    }
    
    /* io opt */
    String ioOptStr = "";
    if (argsMap.keySet().contains("-io")) {
      ioOptStr = argsMap.get("-io")[0];
      if(!ioOptStr.equalsIgnoreCase("em") & !ioOptStr.equalsIgnoreCase("vb")){
        printHelp(args, "-io, opt should be either em or vb");
      }
    }
    int maxiteration = 0;
    if (argsMap.keySet().contains("-maxiteration")) {
      if(ioOptStr.equals("")){
        printHelp(args, "-maxiteration only used with -io");
      }
      maxiteration = Integer.parseInt(argsMap.get("-maxiteration")[0]);
      if(maxiteration<=0){
        printHelp(args, "maxiteration<=0");
      }
    }
    float minRuleProb = 0;
    if (argsMap.keySet().contains("-minprob")) {
      if(ioOptStr.equals("")){
        printHelp(args, "-minprob only used with -io");
      }
      minRuleProb = Integer.parseInt(argsMap.get("-minprob")[0]);
      if(minRuleProb<0.0){
        printHelp(args, "minprob<=0");
      }
    }
    int intermediate = 0;
    if (argsMap.keySet().contains("-intermediate")) {
      if(ioOptStr.equals("")){
        printHelp(args, "-intermediate only used with -io");
      }
      
      intermediate = Integer.parseInt(argsMap.get("-intermediate")[0]);
      if(intermediate<=0 || (maxiteration>0 && intermediate>maxiteration)){
        printHelp(args, "intermediate<=0 || (maxiteration>0 && intermediate>maxiteration)");
      }
    }
    
    /* obj opt */
    String objStr = "";
    if (argsMap.keySet().contains("-obj")) {
      objStr = argsMap.get("-obj")[0];
    }
    if(objStr.equals("")){
      // default values
      objStr = Measures.SURPRISAL + "," + Measures.STRINGPROB;
    }
    
    /* grammar opt */
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
    
    System.err.println("# Root symbol = " + rootSymbol);
    System.err.println("# Objectives = " + objStr);
    System.err.println("# isSparse = " + (parserOpt==1));
    System.err.println("# isLogProb = " + isLogProb);
    System.err.println("# isScaling = " + isScaling);
    System.err.println("# decodeOpt = " + decodeOptStr);
    System.err.println("# verbose opt = " + verbose);
    
    System.err.println("# Num threads = " + numThreads);
    System.err.println("# Filter length = " + filterLen);
    
    System.err.println("# ioOpt = " + ioOptStr);
    System.err.println("# maxIteration = " + maxiteration);

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
    Set<String> parsedSentIndices = new HashSet<String>(); // list of sentences we already parsed
    String outPrefix = null;
    if (argsMap.keySet().contains("-out")) {
      outPrefix = argsMap.get("-out")[0];
      File outDir = (new File(outPrefix)).getAbsoluteFile().getParentFile();
      if(!outDir.exists()){
        System.err.println("# Creating output directory " + outDir.getAbsolutePath());
        outDir.mkdirs();
      }
      
      // try to find out what sentences have been parsed if output files exist.
      File surprisalFile = new File(outPrefix + "." + Measures.SURPRISAL);
      if(surprisalFile.exists()){ 
      	BufferedReader br = new BufferedReader(new FileReader(surprisalFile));
      	String line;
      	while((line=br.readLine())!=null){
      		Util.error(!line.startsWith("# "), "! We expect an id line from the old output file: " + line);
      		String sentId = line.trim().substring(2);
      		parsedSentIndices.add(sentId);
      		while(true){
      			line = br.readLine();
      			if (line==null || line.startsWith("#! Done")) break;
      		}
      	}
      	br.close();
      	
      	System.err.println("# Already parsed " + parsedSentIndices.size() + " sentences: " + parsedSentIndices);
      }
    } else {
      printHelp(args, "No output prefix, -out option");
    }
    
    // filter out long sentences and those that we have parsed
  	List<String> remainedSents = new ArrayList<String>();
  	List<String> remainedIndices = new ArrayList<String>();
  	for (int i = 0; i < sentences.size(); i++) {
			if(!parsedSentIndices.contains(indices.get(i))){
				int numWords = sentences.get(i).split("\\s").length; 
				if(filterLen>0 && numWords>=filterLen){ // filter long sentences
					System.err.println("! Skip long sent, numWords=" + numWords + ". Sent: " + sentences.get(i));
				} else {
					remainedSents.add(sentences.get(i));
					remainedIndices.add(indices.get(i));
				}
			}
		}
  	sentences = remainedSents;
  	indices = remainedIndices;
  	System.err.println("# Need to parse " + indices.size());

  	
    
    /***************/
    /* load parser */
    /***************/
    EarleyParserGenerator parserGenerator = new EarleyParserGenerator(inGrammarFile, inGrammarType, rootSymbol, 
  			isScaling, isLogProb, ioOptStr, decodeOptStr, objStr);
    if (numThreads==1){ // single threaded
			if(parserOpt==0){ // dense
			  parser = parserGenerator.getParserDense();
			} else if(parserOpt==1){ // sparse
				parser = parserGenerator.getParserSparse();
			} else {
			  assert(false);
			}
    } else { // multi-threaded
    	// by default, exit on uncaught exception
      Thread.setDefaultUncaughtExceptionHandler(new Thread.UncaughtExceptionHandler() {
            @Override
            public void uncaughtException(Thread t, Throwable ex) {
              System.err.println("Uncaught exception from thread: " + t.getName());
              ex.printStackTrace();
              System.exit(-1);
            }
          });
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
      if(ioOptStr.equals("")){
      	if(numThreads==1){
      		parser.parseSentences(sentences, indices, outPrefix);
      	} else { // multi-threaded
      		// prepare writers
          Map<String, BufferedWriter> measureWriterMap = new HashMap<String, BufferedWriter>();
          Set<String> outputMeasures = parserGenerator.getOutputMeasures();
          if(!outPrefix.equals("")) {
            for (String measure : outputMeasures) {
              measureWriterMap.put(measure, new BufferedWriter(new 
                  FileWriter(outPrefix + "." + measure, true))); // append results
            }
          }
          
      		MulticoreWrapper<ParserInput,ParserOutput> wrapper = 
              new MulticoreWrapper<ParserInput, ParserOutput>(numThreads, new ThreadedParser(parserGenerator), false);
        	for (int i = 0; i < sentences.size(); i++) {
						wrapper.put(new ParserInput(sentences.get(i), indices.get(i)));
						
						while(wrapper.peek()) { // check if there's any new result
		          ParserOutput output = wrapper.poll();
		          
		          // output
		          for (String measure : measureWriterMap.keySet()) {
		            BufferedWriter measureWriter = measureWriterMap.get(measure);
		            measureWriter.write("# " + output.id + "\n");
		            Util.outputSentenceResult(output.sentence, measureWriter, output.measures.getSentList(measure));
		          }
		        }
					}
        	
        	wrapper.join();
        	while(wrapper.peek()) { // check if there's any new result
	          ParserOutput output = wrapper.poll();
	          
	          // output
	          for (String measure : measureWriterMap.keySet()) {
	            BufferedWriter measureWriter = measureWriterMap.get(measure);
	            measureWriter.write("# " + output.id + "\n");
	            Util.outputSentenceResult(output.sentence, measureWriter, output.measures.getSentList(measure));
	          }
	        }
        	
          // close
          for (String measure : measureWriterMap.keySet()) {
            BufferedWriter measureWriter = measureWriterMap.get(measure);
            measureWriter.close();
          }
      	} // end if numThreads
      } else {
        InsideOutside io = new InsideOutside(parser);
        List<Double> objectiveList = io.insideOutside(sentences, outPrefix, maxiteration, intermediate, minRuleProb);
        
        // output rule prob
        List<ProbRule> allRules = parser.getAllRules();
        String outGrammarFile = outPrefix + ".iogrammar"; //argsMap.get("-saveGrammar")[0];
        RuleFile.printRules(outGrammarFile, allRules, parser.getParserWordIndex(), parser.getParserTagIndex());
//        for (int i = 0; i < objectiveList.size(); i++) {
//          System.err.println((i+1) + "\t" + objectiveList.get(i));
//        }
        System.err.println("# Final objective = " + objectiveList.get(objectiveList.size()-1));;
      }
    } catch (IOException e) {
      System.err.println("! Main: error printing output during parsing to outprefix " + outPrefix);
      System.exit(1);
    }  
    //System.err.println("String probability: " + Math.exp(parser.stringProbability()));
    //parser.dumpChart();
  }

  /**
   * Input to the parser.
   *
   */
  private static class ParserInput {
    public final String sentence;
    public final String id;
    
    public ParserInput(String sentence, String id) {
	    this.sentence = sentence;
	    this.id = id;
    }
  }
  
  /**
   * Output from the parser
   *
   */
  private static class ParserOutput {
  	public final String sentence;
  	public final String id;
  	public final Measures measures; // store values for all measures, initialized for every sentence
    
    public ParserOutput(String sentence, String id, Measures measures) {
      this.sentence = sentence;
      this.id = id;
      this.measures = measures;
    }
  }
  
  /**
   * Multi-threaded parser
   *
   */
  private static class ThreadedParser implements ThreadsafeProcessor<ParserInput,ParserOutput> {
  	private EarleyParser parser;
    private EarleyParserGenerator parserGenerator;
    
    public ThreadedParser(EarleyParserGenerator parserGenerator) {
    	this.parserGenerator = parserGenerator;
      this.parser = parserGenerator.getParserDense();
    }

    @Override
    public ParserOutput process(ParserInput input) {
    	parser.setSentId(input.id);
    	if(!parser.parseSentence(input.sentence)){
    		System.err.print("! Failed to parse sentence " + input.id + ". " + input.sentence);
    		//System.exit(1);
    	}
      return new ParserOutput(input.sentence, input.id, parser.getMeasures());
    }

    @Override
    public ThreadsafeProcessor<ParserInput, ParserOutput> newInstance() {
      return new ThreadedParser(this.parserGenerator);
    }

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
