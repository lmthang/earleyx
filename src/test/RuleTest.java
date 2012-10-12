package test;

import java.util.Arrays;

import edu.stanford.nlp.util.HashIndex;
import edu.stanford.nlp.util.Index;

import parser.Rule;
import junit.framework.TestCase;


public class RuleTest extends TestCase{
  public void testBasic(){
    Index<String> motherIndex = new HashIndex<String>();
    Index<String> childIndex = new HashIndex<String>();
    Rule r1 = new Rule("X", Arrays.asList("a", "b", "c"), 0.1, motherIndex, childIndex);
    Rule r2 = new Rule("X", Arrays.asList("a", "b", "c"), 0.1, motherIndex, childIndex);
    Rule r3 = new Rule("X", Arrays.asList("a", "b", "d"), 0.1, motherIndex, childIndex);
    Rule r4 = new Rule("Y", Arrays.asList("a", "b", "c"), 0.1, motherIndex, childIndex);
    Rule r5 = new Rule("X", Arrays.asList("a", "b", "c"), 0.2, motherIndex, childIndex);
    assertEquals(r1.equals(r2), true);
    assertEquals(r1.hashCode() == r2.hashCode(), true);
    assertEquals(r1.equals(r3), false);
    assertEquals(r1.equals(r4), false);
    assertEquals(r1.equals(r5), false);
    assertEquals(r1.toString(motherIndex, childIndex), "X->[a b c] : 0.1");
    assertEquals(r1.getEdge().schemeString(motherIndex, childIndex, true), "(X (_ _a) (_ _b) (_ _c))");
    assertEquals(r1.getEdge().rhsString(childIndex, false), "a b c");
  }
}
