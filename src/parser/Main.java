package parser;

import edu.stanford.nlp.ling.Label;
import edu.stanford.nlp.ling.TaggedWord;
import edu.stanford.nlp.parser.lexparser.IntTaggedWord;
import edu.stanford.nlp.parser.lexparser.TreebankLangParserParams;
import edu.stanford.nlp.stats.Counter;
import edu.stanford.nlp.stats.Counters;
import edu.stanford.nlp.trees.*;
import edu.stanford.nlp.util.StringUtils;

import java.io.*;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;


/**
 * An implementation of the Stolcke 1995 chart parsing algorithm that calculates the prefix probability
 * of a string S with respect to a probabilistic context-free grammar G.  The prefix probability is defined as the
 * total probability of the set of all trees T such that S is a prefix of T.  As a side effect,
 * the parser also calculates the total probability of S being a complete sentence in G.
 * <p/>
 * <p/>
 * <p/>
 * The intended usage is as follows: a PrefixProbabilityParser is vended by the {@link PrefixProbabilityParserGenerator#getParser()}
 * method of a a {@link PrefixProbabilityParserGenerator} generator instance, which in turn is obtained by training a generator on a collection of context-free trees (see the
 * {@link PrefixProbabilityParserGenerator#getGenerator(edu.stanford.nlp.trees.Treebank, java.lang.String)} method).
 * Then use the {@link #loadSentence(java.util.List)} method to load a sentence (a {@link List} of words (Strings)) into the parser.
 * The parse chart for the string can then be incrementally constructed with the {@link #parseNextWord()} method.
 * At any time, the current prefix probability and string probability of the words parsed so far can be accessed with the
 * {@link #prefixProbability()} and {@link #stringProbability()} methods respectively.  See the source code of the main method
 * of this class for further details of use.
 *
 * @author Roger Levy
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

  public static void main(String[] args) throws Exception {
    System.err.println("PrefixProbabilityParser invoked with arguments " + Arrays.asList(args));
    BufferedReader sentenceReader;
    String encoding = "UTF-8";
    Map<String, Integer> flags = new HashMap<String, Integer>();
    flags.put("-tr", new Integer(1));
    flags.put("-s", new Integer(1));
    flags.put("-encoding", new Integer(1));
    flags.put("-tlpp", new Integer(1));
    
    // Thang v110901: additional flags 
    flags.put("-prefix", new Integer(1)); // prefix model
    flags.put("-grammar", new Integer(1)); // read grammar file
    flags.put("-saveGrammar", new Integer(1)); // rule output filename
    flags.put("-opt", new Integer(1)); // 0: default, 1: old way of handling extended rules
    
    // 0: no debug info, 1: progress info, 2: closure matrices, combine/predict parsing info
    // , 3: details edge/rule info, parser chart, prediction/completion list info, trie
    flags.put("-verbose", new Integer(1));
    flags.put("-debug", new Integer(1));
    flags.put("-id", new Integer(1)); // input id filename
    flags.put("-out", new Integer(1)); // output filename
    flags.put("-penn", new Integer(1)); // output input trees into file using PTB format (filtering out unneeded symbols)
    
    Map<String, String[]> argsMap = StringUtils.argsToMap(args, flags);
    args = argsMap.get(null);
    if (argsMap.keySet().contains("-encoding")) {
      encoding = argsMap.get("-encoding")[0];
    }
    
    // get input sentence (if any)
    String sentencesFileName = null;
    if (argsMap.keySet().contains("-s")) {
      sentencesFileName = argsMap.get("-s")[0];
    } 
    String inputSentence = "Are_VBP tourists_NNS"; //"Try this wine"; //"the man fled";
    if (args.length > 1) {
      inputSentence = args[1];
    }
    //System.out.println("# Input sentence: " + inputSentence);
    
    // verbose
    int verbose = 0;
    if (argsMap.keySet().contains("-verbose")) {
      verbose = Integer.parseInt(argsMap.get("-verbose")[0]);
      StateSpace.verbose = verbose;
      Grammar.verbose = verbose;
      RelationMatrix.verbose = verbose;
      ClosureMatrix.verbose = verbose;
      PrefixProbabilityParserOld.verbose = verbose;
    }
    
    // in id
    String idFile = null;
    BufferedReader idReader = null;
    if (argsMap.keySet().contains("-id")) {
      idFile = argsMap.get("-id")[0];
    }
    if(idFile != null){
      idReader = new BufferedReader(new FileReader(idFile));
    }
    
    // out
    String outFile = null;
    BufferedWriter outWriter = null;
    BufferedWriter synOutWriter = null;
    BufferedWriter lexOutWriter = null;
    BufferedWriter stringOutWriter = null; // string prob
    if (argsMap.keySet().contains("-out")) {
      outFile = argsMap.get("-out")[0];
    }
    if(outFile != null){
      outWriter = new BufferedWriter(new FileWriter(outFile + ".srprsl"));
      synOutWriter = new BufferedWriter(new FileWriter(outFile + ".SynSp"));
      lexOutWriter = new BufferedWriter(new FileWriter(outFile + ".LexSp"));
      stringOutWriter = new BufferedWriter(new FileWriter(outFile + ".string"));
    }
    
    // penn
    String pennFile = null;
    BufferedWriter pennWriter = null;
    if (argsMap.keySet().contains("-penn")) {
      pennFile = argsMap.get("-penn")[0];
    }
    if(pennFile != null){
      pennWriter = new BufferedWriter(new FileWriter(pennFile));
    }
    
    // debug
    if (argsMap.keySet().contains("-debug")) {
      int debugOpt = Integer.parseInt(argsMap.get("-debug")[0]);
      if(debugOpt == 1){
        PrefixProbabilityParser.debugOpt = 1; // to set uniform prob to OOV words
      } else if(debugOpt == 2){
        PrefixProbabilityParser.debugOpt = 2; // output rule and prob for OOV words
      }
    }
    
    // parser opt
    int parserOpt = 0; // 0: default, 1: old way of handling extended rules
    if (argsMap.keySet().contains("-opt")) {
      parserOpt = Integer.parseInt(argsMap.get("-opt")[0]);
    }
    if(parserOpt == 0){
      RuleFile.isPseudoTag = false;
      Grammar.useTrie = true;
    } else if(parserOpt == 1){
      RuleFile.isPseudoTag = true;
      Grammar.useTrie = false;
    } else {
      System.err.println("#! Invalid parser option " + parserOpt);
      System.exit(1);
    }
    System.err.println("# Parser opt=" + parserOpt);
    
    // root
    String rootSymbol = "ROOT";        
    PrefixProbabilityParserGenerator generator;
    Collection<Rule> rules = new ArrayList<Rule>();
    Collection<TaggedWord> taggedWords = new ArrayList<TaggedWord>();

    // BUG REMOVE
//    StateSpace.verbose = 2;
//    Grammar.verbose = 2;
//    RelationMatrix.verbose = 2;
//    ClosureMatrix.verbose = 2;
//    PrefixProbabilityParserOld.verbose = 2;
    
    if (argsMap.keySet().contains("-grammar")) { // Thang v110901: read from grammar file
      String ruleFile = argsMap.get("-grammar")[0];
      System.err.println("Grammar file = " + ruleFile);
      
      // extract rules and taggedWords from grammar file
      Map<IntTaggedWord, Counter<IntTaggedWord>> wordCounterTagMap = new HashMap<IntTaggedWord, Counter<IntTaggedWord>>();
      Map<IntTaggedWord, Set<IntTaggedWord>> tagsForWord = new HashMap<IntTaggedWord, Set<IntTaggedWord>>();
      Set<IntTaggedWord> preterminalSet = new HashSet<IntTaggedWord>();
      Map<Label, Counter<String>> tagHash = new HashMap<Label, Counter<String>>();
      Set<String> seenEnd = new HashSet<String>();
      Collection<Rule> extendedRules = new ArrayList<Rule>();
      
      RuleFile.parseRuleFile(ruleFile, rules, extendedRules, wordCounterTagMap, 
          tagsForWord, preterminalSet, tagHash, seenEnd);
      
      // log
      for(IntTaggedWord iT : wordCounterTagMap.keySet()){
        Counter<IntTaggedWord> counter = wordCounterTagMap.get(iT);
        Counters.logInPlace(counter);
      }
      for(Label label : tagHash.keySet()){
        Counter<String> counter = tagHash.get(label);
        Counters.logInPlace(counter);
      }
      generator = PrefixProbabilityParserGenerator.getGeneratorLexDistribution(rules, extendedRules, wordCounterTagMap, tagsForWord, 
          preterminalSet, tagHash, 
          seenEnd, rootSymbol);
    } else {
      /* transformer name */
      String transformerClassName = null;
      if (argsMap.keySet().contains("-tr")) { // user-input transformer
        transformerClassName = argsMap.get("-tr")[0];
      }
      
      /* treebank params name */
      String treebankPackClassName = "edu.stanford.nlp.parser.lexparser.EnglishTreebankParserParams";
      if (argsMap.keySet().contains("-tlpp")) {        
        treebankPackClassName = argsMap.get("-tlpp")[0];
      }
      
      // transform trees
      String treeFile = null;
      if(args.length > 0){
        treeFile = args[0];
      } else {
        System.err.println("! Empty argument no treeFile");
        System.exit(1);
      }
      MemoryTreebank transformedTrees = transformTrees(treeFile, transformerClassName, treebankPackClassName, pennWriter);
            
       // extract rules and taggedWords from parse trees
//      Set<IntTaggedWord> preterminals = new HashSet<IntTaggedWord>();
//      Set<IntTaggedWord> terminals = new HashSet<IntTaggedWord>();
      
      PrefixProbabilityParserGenerator.extractRulesWords(transformedTrees, taggedWords, rules); //, preterminals, terminals);
      generator = PrefixProbabilityParserGenerator.getGeneratorFromLexCounts(rules, taggedWords, rootSymbol); //, isSmooth); //, preterminals, terminals);
    }
    
    if(pennWriter != null){
      pennWriter.close();
    }
    
    //  Thang v110901: output rules to file
    if (argsMap.keySet().contains("-saveGrammar")) { // Thang v110901: read root symbol
      String ruleFile = argsMap.get("-saveGrammar")[0];
      //System.err.println("# Lexion: " + generator.getBasicLexicon());
      Map<IntTaggedWord, Counter<IntTaggedWord>> wordCounterTagMap = generator.getBasicLexicon().getWordCounterTagMap();
      
      // exp
      Map<IntTaggedWord, Counter<IntTaggedWord>> newWordCounterTagMap = new HashMap<IntTaggedWord, Counter<IntTaggedWord>>();
      for(IntTaggedWord iT : wordCounterTagMap.keySet()){
        Counter<IntTaggedWord> counter = wordCounterTagMap.get(iT);
        Counter<IntTaggedWord> newCounter = Counters.getCopy(counter); // IMPORTANT: this only works with the new version of getCopy
        Counters.expInPlace(newCounter);
        
        newWordCounterTagMap.put(iT, newCounter);
      }
      RuleFile.printRules(ruleFile, rules, newWordCounterTagMap);
    }
    
    // parse sentences
    String prefixModel = "edu.stanford.nlp.parser.prefixparser.PrefixProbabilityParserOld";
    if (argsMap.keySet().contains("-prefix")) {
      prefixModel = argsMap.get("-prefix")[0];
    }
    PrefixProbabilityParser parser = generator.getParser(prefixModel);
    
    if(sentencesFileName != null){ // read from file 
      sentenceReader = new BufferedReader(new InputStreamReader(new FileInputStream(sentencesFileName), encoding));
    } else { // read from string
      sentenceReader = new BufferedReader(new StringReader(inputSentence));
    }
  
    parser.parseSentences(sentenceReader, idReader, outWriter, synOutWriter, lexOutWriter, stringOutWriter);

    if(outWriter != null){
      outWriter.close();
    }
    if(synOutWriter != null){
      synOutWriter.close();
    }
    if(lexOutWriter != null){
      lexOutWriter.close();
    }
    if(stringOutWriter != null){
      stringOutWriter.close();
    }
    if(idReader != null){
      idReader.close();
    }
    
    //System.err.println("String probability: " + Math.exp(parser.stringProbability()));
    //parser.dumpChart();
  }

  /**
   * transform trees into a form that could be processed by the system
   * @param treeFile
   * @param transformerClassName
   * @param treebankPackClassName
   * @param pennWriter
   * @return
   */
  public static MemoryTreebank transformTrees(String treeFile, String transformerClassName, 
      String treebankPackClassName, BufferedWriter pennWriter){
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
    System.err.print("# Thang: loading tree file " + treeFile + " ... ");
    trees.loadPath(treeFile);
    System.err.println("Done!");

    /* transform trees */
    for (Tree t : trees) {
      Tree newTree = transformer.transformTree(t);
      transformedTrees.add(newTree); //transformTree(t, tf));
      if(pennWriter != null){
        String pennString = newTree.pennString();
        pennString = pennString.replaceAll("\\s+", " ");
        pennString = pennString.replaceAll("^\\(ROOT ", "(");
        try {
          pennWriter.write(pennString + "\n");
        } catch (IOException e) {
          e.printStackTrace();
        }
      }
      //System.err.println(t + "\n"); newTree.pennPrint();
    }
    
    return transformedTrees;
  }
  
  /*****************************************/
  /************* Utility method ************/
  /*****************************************/
  /**
   * Process POS file, return reader without POS tags, and construct a lexicon that maps a word into a set of POS tags
   * @param reader
   * @param terminals
   * @return
   * @throws IOException
   */
  public static BufferedReader processPOSFile(BufferedReader reader, Map<String, Set<String>> lexicon) throws IOException{
    BufferedReader sentenceReader = new BufferedReader(reader);
    String sentenceString;
    
    StringBuffer sb = new StringBuffer("");
    Pattern p = Pattern.compile("(.+)_(.+)");
    while ((sentenceString = sentenceReader.readLine()) != null) {
      String[] words = sentenceString.split("\\s+");
      
      int numWords = words.length;
      for (int i = 0; i < numWords; i++) {
        Matcher m = p.matcher(words[i]);
        if(!m.matches()){
          System.out.println("! Matches \"" + words[i] + "\"");
          System.exit(1);
        }
        
        String word = m.group(1);
        String tag = m.group(2);
        
        // update lexicon
        if(!lexicon.containsKey(word)){
          Set<String> tags = new HashSet<String>();
          tags.add(tag);
          lexicon.put(word, tags);
        } else {
          Set<String> tags = lexicon.get(word);
          tags.add(tag);
        }
        
        // update stringbuffer
        if(i<(numWords-1)){
          sb.append(word + " ");
        } else { // end of sentence
          sb.append(word + "\n");
        }
      }
    }
    
    return new BufferedReader(new StringReader(sb.toString()));
  }
}

/************* Unused code ************/
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