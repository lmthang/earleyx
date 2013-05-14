/**
 * 
 */
package induction;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import parser.EarleyParser;
import util.Operator;
import util.RuleFile;
import util.Util;
import base.BaseLexicon;
import base.ProbRule;
import base.RuleSet;
import cc.mallet.types.Dirichlet;
import cc.mallet.util.Maths;
import edu.stanford.nlp.stats.Counter;
import edu.stanford.nlp.util.Index;
import edu.stanford.nlp.util.Pair;

/**
 * @author Minh-Thang Luong, 2012
 *
 */
public class InsideOutside {
  protected EarleyParser parser;
  protected Operator operator;
  protected BaseLexicon lex;
  protected RuleSet ruleSet;
  protected Index<String> parserTagIndex;
  protected Index<String> parserWordIndex;
  protected int insideOutsideOpt;
  protected int verbose;

  public InsideOutside(EarleyParser parser) {
    this.parser = parser;
    operator = parser.getOperator();
    lex = parser.getLex();
    ruleSet = parser.getRuleSet();
    parserTagIndex = parser.getParserTagIndex();
    parserWordIndex = parser.getParserWordIndex();
    insideOutsideOpt = parser.getInsideOutsideOpt();
    this.verbose = EarleyParser.verbose;
  }
  
  public InsideOutside(EarleyParser parser, int verbose) {
    this(parser);
    this.verbose = verbose;
  }
  
  public List<Double> insideOutside(List<String> sentences, double minRuleProb) throws IOException{
    return insideOutside(sentences, "", minRuleProb);
  }
  
  public List<Double> insideOutside(List<String> sentences, String outPrefix, double minRuleProb) throws IOException{
    return insideOutside(sentences, outPrefix, 0, 0, minRuleProb);
  }
  
  public List<Double> insideOutside(List<String> sentences, String outPrefix, 
      int maxIteration, int intermediate, double minRuleProb) throws IOException{
    int minIteration = 1;
    double stopTol = 1e-7;
    
    System.err.println("## Inside-Outisde stopTol=" + stopTol + ", minRuleProb=" + minRuleProb);
    List<Double> objectiveList = new ArrayList<Double>();
    int numIterations = 0;
    double prevObjective = Double.POSITIVE_INFINITY;
    double objective = Double.POSITIVE_INFINITY; 
    
    BufferedWriter bw = new BufferedWriter(new FileWriter(new File(outPrefix + ".obj")));
    while(true){
      numIterations++;
      if(verbose>=3){
        System.err.println(ruleSet.toString(parserTagIndex, parserWordIndex));
      }
      
      // sumLogProbs
      List<Double> sentLogProbs = parser.parseSentences(sentences);
      double sumNegLogProb = 0.0;
      for (Double sentLogProb : sentLogProbs) {
        sumNegLogProb -= sentLogProb;
      }
      
      // update rule probs
      int numRules = 0;
      if(verbose>=3){
        System.err.println("\n# Update rule probs");
      }
      if(insideOutsideOpt==1){ // EM, objective sumNegLogProb
        numRules = emUpdate(minRuleProb);
        if(verbose>=-1){
          System.err.println("# iteration " + numIterations + ", numRules=" + numRules 
              + ", sumNegLogProb = " + sumNegLogProb);
        }
        objective = sumNegLogProb;
      } else if(insideOutsideOpt==2){ // VB, objective free energy
        Pair<Integer, Double> pair = vbUpdate(minRuleProb, sumNegLogProb);
        numRules = pair.first();
        double freeEnergy = pair.second();
        System.err.println("# iteration " + numIterations + ", numRules=" + numRules 
            + ", sumNegLogProb = " + sumNegLogProb + ", freeEnergy=" + freeEnergy);
        objective = freeEnergy;
      } else {
        System.err.println("! Invalid inside outside opt " + insideOutsideOpt);
      }
      if(objective>prevObjective){
        System.err.println("Objective increased! Stop");
        break;
      }
      objectiveList.add(objective);
      bw.write("iteration " + numIterations + " " + sumNegLogProb + " " + objective + "\n");
      bw.flush();
      
//      System.err.println("\n# iteration " + numIterations + "\n" + Util.sprint(ruleSet.getTagRules(), parserTagIndex, parserWordIndex));
//      if(numIterations == 2){
//        System.exit(1);
//      }
      
      /** update model params **/
      updateModel();
      
      // output intermediate IO grammars & parses
      if (intermediate>0 && numIterations % intermediate == 0 && !outPrefix.equals("")){ 
        String outGrammarFile = outPrefix + "." + numIterations + ".iogrammar" ;
        try {
          RuleFile.printRules(outGrammarFile, parser.getAllRules(), parserWordIndex, parserTagIndex);
        } catch (IOException e) {
          System.err.println("! Error outputing intermediate grammar " + outGrammarFile);
          System.exit(1);
        }
        
        int oldVerbose = EarleyParser.verbose;
        EarleyParser.verbose = -1;
        parser.parseSentences(sentences, outPrefix + "." + numIterations);
        EarleyParser.verbose = oldVerbose;
      }
      
      // convergence test
      boolean isStop = isStop(numIterations, minIteration, maxIteration, stopTol, objective, prevObjective);
      if(isStop){
        break;
      }
      
      // reset
      parser.setExpectedCounts(new HashMap<Integer, Double>());
      prevObjective = objective;
    }
    
    bw.close();
    // if we do parsing exptected counts will be double
    //parseSentences(sentences, outPrefix);
    return objectiveList;
  }
  
  private void updateModel(){
    parser.buildGrammar();
    Map<Integer, Counter<Integer>> tag2wordsMap = lex.getTag2wordsMap();
    
    // reset lex
    for(int tag : tag2wordsMap.keySet()){ // tag
      Counter<Integer> counter = tag2wordsMap.get(tag);
      
      for(int word : counter.keySet()){
        counter.setCount(word, Double.NEGATIVE_INFINITY); // zero
      }
    }
    
    // update lex
    for(ProbRule probRule : ruleSet.getTerminalRules()){
      tag2wordsMap.get(probRule.getMother()).setCount(probRule.getChild(0), Math.log(probRule.getProb()));
    }
  }
  
  private boolean isStop(int numIterations, int minIteration, int maxIteration, double stopTol
      , double objective, double prevObjective){
    boolean isStop = false;
    if(numIterations>=minIteration) {// && numRules==prevNumRules){
      if(maxIteration>0){
        if(numIterations>=maxIteration){ // exceed max iterations
          if(verbose>=3){
            System.err.println("# Exceed number of iterations " + maxIteration + ", stop");
          }
          isStop = true;
        }
      } else {
        if(objective==0){
          if(verbose>=0){
            System.err.println("# Reach minimum objective = 0.0, stop");
          }
          isStop = true;
        } else {
          double relativeChange = (prevObjective-objective)/Math.abs(objective);
          if (relativeChange<stopTol){ // change is too small
            if(verbose>=0){
              System.err.println("# Relative change " + relativeChange + " < " + stopTol + ", stop");
            }
            isStop = true;
          }
        }
      }
    }
    
    return isStop;
  }
  
  public int emUpdate(double minRuleProb){
    Map<Integer, Double> expectedCounts = parser.getExpectedCounts();
    
    // compute sums per tag
    Map<Integer, Double> tagSums = new HashMap<Integer, Double>();
    for (int ruleId : expectedCounts.keySet()) {
      int tag = ruleSet.getMother(ruleId);
      
      if(!tagSums.containsKey(tag)){
        tagSums.put(tag, operator.zero());
      }
      
      tagSums.put(tag, operator.add(tagSums.get(tag), expectedCounts.get(ruleId)));
    }
    
    // normalized probs
    int numRules = 0;
    
    for (int ruleId = 0; ruleId < ruleSet.size(); ruleId++) {
      if(expectedCounts.containsKey(ruleId)){
        assert(operator.getProb(expectedCounts.get(ruleId))>0);
        int tag = ruleSet.getMother(ruleId);
        
        double newProb = operator.getProb(operator.divide(expectedCounts.get(ruleId), 
            tagSums.get(tag)));
      
        if(newProb<minRuleProb){ // filter
          System.err.println("Filter: " + newProb + "\t" + ruleSet.get(ruleId).getRule().toString(parserTagIndex, parserWordIndex));
          newProb = 0.0; 
        } else {
          numRules++;
          if(verbose>=3){
            System.err.println(newProb + "\t" + ruleSet.get(ruleId).getRule().toString(parserTagIndex, parserWordIndex));
          }
        }
        
        ruleSet.setProb(ruleId, newProb);
      }
    }
    
    return numRules;
  }
  
  public Pair<Integer, Double> vbUpdate(double minRuleProb, double sumNegLogProb){
    Map<Integer, Double> expectedCounts = parser.getExpectedCounts();
    
    // free energy: follow formula (8) in "Variational Bayesian Grammar Induction for Natural Language
    double freeEnergy = sumNegLogProb;
    
    // posterior bias index by ruleId
    Map<Integer, Double> posteriorBiases = new HashMap<Integer, Double>();
    
    // prior/posterior bias sums per tag
    Map<Integer, Double> priorBiasSums = new HashMap<Integer, Double>();
    Map<Integer, Double> posteriorBiasSums = new HashMap<Integer, Double>();
    
    for (int ruleId : expectedCounts.keySet()) { // go through each rule
      double priorBias = ruleSet.getBias(ruleId);
      int tag = ruleSet.getMother(ruleId);
      
      // posterior bias = prior bias + expected count
      double posteriorBias = priorBias + operator.getProb(expectedCounts.get(ruleId));
      posteriorBiases.put(ruleId, posteriorBias);
      
      // update sum
      if(!posteriorBiasSums.containsKey(tag)){
        posteriorBiasSums.put(tag, 0.0);
        priorBiasSums.put(tag, 0.0);
      }
      posteriorBiasSums.put(tag, posteriorBiasSums.get(tag) + posteriorBias);
      priorBiasSums.put(tag, priorBiasSums.get(tag) + priorBias);
      
      // free energy
      freeEnergy -= (Maths.logGamma(posteriorBias) - Maths.logGamma(priorBias));
//      System.err.println("freeEnergy2 " + freeEnergy + "\t" + posteriorBias + "\t" + priorBias 
//          + "\t" + ruleSet.get(ruleId).toString(parserTagIndex, parserWordIndex));
    }
    
    // free energy
    for(int tag : posteriorBiasSums.keySet()){
      freeEnergy += Maths.logGamma(posteriorBiasSums.get(tag)) - Maths.logGamma(priorBiasSums.get(tag));
//      System.err.println("freeEnergy3 " + freeEnergy + "\t" + posteriorBiasSums.get(tag) 
//          + "\t" + priorBiasSums.get(tag));
    }
    
    // reestimate rule probabilities
    int numRules = 0;
    for (int ruleId = 0; ruleId < ruleSet.size(); ruleId++) {
      double priorBias = ruleSet.getBias(ruleId);
      
      if(posteriorBiases.containsKey(ruleId)){
        double posteriorBias = posteriorBiases.get(ruleId);
        assert(posteriorBias>0);
        int tag = ruleSet.getMother(ruleId);
        double logProb = Dirichlet.digamma(posteriorBias) - 
        Dirichlet.digamma(posteriorBiasSums.get(tag));
        double newProb = Math.exp(logProb);
        
        // free energy
        freeEnergy += (posteriorBias - priorBias)*logProb;
//        System.err.println("freeEnergy4 " + freeEnergy + "\t" + logProb);
        
        if(newProb<minRuleProb){ // filter
          System.err.println("Filter: " + newProb + "\t" + ruleSet.get(ruleId).getRule().toString(parserTagIndex, parserWordIndex)
              + "\t" + posteriorBias + 
              ", " + posteriorBiasSums.get(tag));
          newProb = 0.0; 
        } else {
          numRules++;
          if(verbose>=3){
            System.err.println(newProb + "\t" + ruleSet.get(ruleId).getRule().toString(parserTagIndex, parserWordIndex));
          }
        }
        
        ruleSet.setProb(ruleId, newProb);
      }
    }
    
    if(Double.isNaN(freeEnergy) || Double.isInfinite(freeEnergy)){
      System.err.println("Fee energy = NaN or Infinity");
      System.exit(1);
    }
    return new Pair<Integer, Double>(numRules, freeEnergy);
  }
  

//// for VB we need to renormalize later
//Map<Integer, Double> vbTagLogSums = new HashMap<Integer, Double>();
//Map<Integer, Double> vbRuleLogProbs = new HashMap<Integer, Double>();
//vbRuleLogProbs.put(ruleId, logProb);
//if(!vbTagLogSums.containsKey(tag)){
//  vbTagLogSums.put(tag, Double.NEGATIVE_INFINITY);
//}
//
//vbTagLogSums.put(tag, SloppyMath.logAdd(vbTagLogSums.get(tag),logProb));

//// VB, renormalize
//for (int ruleId = 0; ruleId < ruleSet.size(); ruleId++) {
//  if(posteriorBiases.containsKey(ruleId)){
//    int tag = ruleSet.getMother(ruleId);
//    double newProb = Math.exp(vbRuleLogProbs.get(ruleId)- vbTagLogSums.get(tag));
//    
//  }
//}
  
}
