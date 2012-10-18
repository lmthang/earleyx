package test;

import java.util.LinkedList;
import java.util.List;

import utility.Utility;


import junit.framework.TestCase;
import lexicon.SmoothLexicon;

import edu.stanford.nlp.parser.lexparser.IntTaggedWord;
import edu.stanford.nlp.util.HashIndex;
import edu.stanford.nlp.util.Index;

public class SmoothLexiconTest extends TestCase{
  public void testBasic(){
    Index<String> wordIndex = new HashIndex<String>();
    Index<String> tagIndex = new HashIndex<String>();
    
    List<IntTaggedWord> trainITWs = new LinkedList<IntTaggedWord>();
    IntTaggedWord aTagA = new IntTaggedWord("a", "TagA", wordIndex, tagIndex);
    IntTaggedWord cbbTagB = new IntTaggedWord("cbb", "TagB", wordIndex, tagIndex);
    IntTaggedWord cTagC = new IntTaggedWord("c", "TagC", wordIndex, tagIndex);
    IntTaggedWord cTagF = new IntTaggedWord("c", "TagF", wordIndex, tagIndex);
    IntTaggedWord DTagD = new IntTaggedWord("D", "TagD", wordIndex, tagIndex);
    IntTaggedWord c12TagC = new IntTaggedWord("c12", "TagC", wordIndex, tagIndex);
    
    trainITWs.add(aTagA);
    trainITWs.add(cbbTagB);
    trainITWs.add(cTagC);
    trainITWs.add(cTagF);
    trainITWs.add(aTagA);
    trainITWs.add(DTagD);
    trainITWs.add(c12TagC);
    
    SmoothLexicon sl = new SmoothLexicon(wordIndex, tagIndex);
    sl.train(trainITWs);

    List<IntTaggedWord> testITWs = new LinkedList<IntTaggedWord>();
    IntTaggedWord ddTagD = new IntTaggedWord("dd", "TagD", wordIndex, tagIndex);
    IntTaggedWord dbbTagB = new IntTaggedWord("dbb", "TagB", wordIndex, tagIndex);
    IntTaggedWord c123TagC = new IntTaggedWord("c123", "TagC", wordIndex, tagIndex);
    //IntTaggedWord eTagE = new IntTaggedWord("e", "TagE", wordIndex, tagIndex);
    testITWs.add(ddTagD);
    testITWs.add(dbbTagB);
    testITWs.add(c123TagC);
    //testITWs.add(eTagE);

    assertEquals(wordIndex.toString(), "[0=a,1=cbb,2=c,3=D,4=c12,5=UNK,6=UNK-LC,7=UNK-LC-DIG,8=UNK-ALLC,9=dd,10=dbb,11=c123]"); //,13=e]");
    assertEquals(tagIndex.toString(), "[0=TagA,1=TagB,2=TagC,3=TagF,4=TagD]"); //,5=TagE]");
    
    assertEquals(Utility.sprint(sl.tagsForWord("c"), wordIndex, tagIndex), "[c/TagC, c/TagF]");
    //assertEquals(sl.tagsForWord("c").toString(), "[2/2, 2/3]");
    
    assertEquals(Utility.sprint(sl.tagsForWord("ccc"), wordIndex, tagIndex), "[ccc/TagD, ccc/TagA, ccc/TagB, ccc/TagC, ccc/TagF]");    
    //assertEquals(sl.tagsForWord("ccc").toString(), "[5/4, 5/1, 5/0, 5/3, 5/2]");
    

    assertEquals(wordIndex.toString(), "[0=a,1=cbb,2=c,3=D,4=c12,5=UNK,6=UNK-LC,7=UNK-LC-DIG,8=UNK-ALLC,9=dd,10=dbb,11=c123,12=ccc]"); //13=e,14=ccc]");
    
    List<Double> lexScores = new LinkedList<Double>();
    for(IntTaggedWord iTW : trainITWs){
      lexScores.add(sl.score(iTW));
      System.err.println(iTW.toString(wordIndex, tagIndex) + "\t" + sl.score(iTW));
    }
    System.err.println();
    for (IntTaggedWord iTW : testITWs) {
      lexScores.add(sl.score(iTW));
      System.err.println(iTW.toString(wordIndex, tagIndex) + "\t" + sl.score(iTW));
    }
    
    assertEquals(-0.4054651, lexScores.get(0), 1e-5);
    assertEquals(-1.0986123, lexScores.get(1), 1e-5);
    assertEquals(-1.609438, lexScores.get(2), 1e-5);
    assertEquals(-1.0986123, lexScores.get(3), 1e-5);
    assertEquals(-0.4054651, lexScores.get(4), 1e-5);
    assertEquals(-1.0986123, lexScores.get(5), 1e-5);
    assertEquals(-1.609438, lexScores.get(6), 1e-5);
    assertEquals(0.0, lexScores.get(7), 1e-5);
    assertEquals(-1.0986123, lexScores.get(8), 1e-5);
    assertEquals(-1.609438, lexScores.get(9), 1e-5);
    //assertEquals(Float.NEGATIVE_INFINITY, lexScores.get(10), 1e-5);
  }
}
