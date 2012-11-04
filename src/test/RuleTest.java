package test;

import java.util.Arrays;

import base.ProbRule;

import edu.stanford.nlp.util.HashIndex;
import edu.stanford.nlp.util.Index;

import junit.framework.TestCase;


public class RuleTest extends TestCase{
  public void testBasic(){
    Index<String> motherIndex = new HashIndex<String>();
    Index<String> childIndex = new HashIndex<String>();
    ProbRule r1 = new ProbRule("X", Arrays.asList("a", "b", "c"), 0.1, motherIndex, childIndex);
    ProbRule r2 = new ProbRule("X", Arrays.asList("a", "b", "c"), 0.1, motherIndex, childIndex);
    ProbRule r3 = new ProbRule("X", Arrays.asList("a", "b", "d"), 0.1, motherIndex, childIndex);
    ProbRule r4 = new ProbRule("Y", Arrays.asList("a", "b", "c"), 0.1, motherIndex, childIndex);
    ProbRule r5 = new ProbRule("X", Arrays.asList("a", "b", "c"), 0.2, motherIndex, childIndex);
    assertEquals(r1.equals(r2), true);
    assertEquals(r1.hashCode() == r2.hashCode(), true);
    assertEquals(r1.equals(r3), false);
    assertEquals(r1.equals(r4), false);
    assertEquals(r1.equals(r5), false);
    assertEquals(r1.toString(motherIndex, childIndex), "X->[a b c] : 0.1");
    assertEquals(r1.getRule().schemeString(motherIndex, childIndex, true), "(X (_ _a) (_ _b) (_ _c))");
    assertEquals(r1.getRule().rhsString(childIndex, false), "a b c");
  }
}
