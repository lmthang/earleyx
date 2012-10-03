package parser;

import java.util.LinkedList;
import java.util.List;

import junit.framework.TestCase;

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
    IntTaggedWord DTagD = new IntTaggedWord("D", "TagD", wordIndex, tagIndex);
    IntTaggedWord c12TagC = new IntTaggedWord("c12", "TagC", wordIndex, tagIndex);
    
    trainITWs.add(aTagA);
    trainITWs.add(cbbTagB);
    trainITWs.add(cTagC);
    trainITWs.add(aTagA);
    trainITWs.add(DTagD);
    trainITWs.add(c12TagC);
    
    SmoothLexicon sl = new SmoothLexicon(wordIndex, tagIndex);
    sl.train(trainITWs);

    List<IntTaggedWord> testITWs = new LinkedList<IntTaggedWord>();
    IntTaggedWord ddTagD = new IntTaggedWord("dd", "TagD", wordIndex, tagIndex);
    IntTaggedWord dbbTagB = new IntTaggedWord("dbb", "TagB", wordIndex, tagIndex);
    IntTaggedWord c123TagC = new IntTaggedWord("c123", "TagC", wordIndex, tagIndex);
    IntTaggedWord eTagE = new IntTaggedWord("e", "TagE", wordIndex, tagIndex);
    testITWs.add(ddTagD);
    testITWs.add(dbbTagB);
    testITWs.add(c123TagC);
    testITWs.add(eTagE);

    List<Double> lexScores = new LinkedList<Double>();
    for(IntTaggedWord iTW : trainITWs){
      lexScores.add(sl.score(iTW));
    }
    
    for (IntTaggedWord iTW : testITWs) {
      lexScores.add(sl.score(iTW));
    }
    
    assertEquals(-0.4054651, lexScores.get(0), 1e-5);
    assertEquals(-1.0986123, lexScores.get(1), 1e-5);
    assertEquals(-1.609438, lexScores.get(2), 1e-5);
    assertEquals(-0.4054651, lexScores.get(3), 1e-5);
    assertEquals(-1.0986123, lexScores.get(4), 1e-5);
    assertEquals(-1.609438, lexScores.get(5), 1e-5);
    assertEquals(-1.0986123, lexScores.get(6), 1e-5);
    assertEquals(-1.0986123, lexScores.get(7), 1e-5);
    assertEquals(-1.609438, lexScores.get(8), 1e-5);
    assertEquals(Float.NEGATIVE_INFINITY, lexScores.get(9), 1e-5);
  }
}
