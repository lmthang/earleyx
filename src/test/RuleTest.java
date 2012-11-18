package test;

import java.util.Arrays;

import base.ProbRule;
import base.Rule;
import base.TagRule;
import base.TerminalRule;

import edu.stanford.nlp.util.HashIndex;
import edu.stanford.nlp.util.Index;

import junit.framework.TestCase;


public class RuleTest extends TestCase{
  public void testBasic(){
    Index<String> tagIndex = new HashIndex<String>();
    Index<String> wordIndex = new HashIndex<String>();
    ProbRule r1 = new ProbRule(new TerminalRule("X", Arrays.asList("a", "b", "c"), tagIndex, wordIndex), 0.1);
    ProbRule r2 = new ProbRule(new TerminalRule("X", Arrays.asList("a", "b", "c"), tagIndex, wordIndex), 0.1);
    ProbRule r3 = new ProbRule(new TerminalRule("X", Arrays.asList("a", "b", "d"), tagIndex, wordIndex), 0.1);
    ProbRule r4 = new ProbRule(new TerminalRule("Y", Arrays.asList("a", "b", "c"), tagIndex, wordIndex), 0.1);
    ProbRule r5 = new ProbRule(new TerminalRule("X", Arrays.asList("a", "b", "c"), tagIndex, wordIndex), 0.2);
    assertEquals(r1.equals(r2), true);
    assertEquals(r1.hashCode() == r2.hashCode(), true);
    assertEquals(r1.equals(r3), false);
    assertEquals(r1.equals(r4), false);
    assertEquals(r1.equals(r5), false);
    assertEquals(r1.toString(tagIndex, wordIndex), "X->[_a _b _c] : 0.1");
    assertEquals(r1.schemeString(tagIndex, wordIndex), "(X (_ _a) (_ _b) (_ _c))");
    assertEquals(r1.markString(tagIndex, wordIndex), "0.1 X --> _a _b _c");
    
    ProbRule r6 = new ProbRule(new TagRule("X", Arrays.asList("Y", "Z"), tagIndex), 1.0);
    assertEquals(r6.toString(tagIndex, wordIndex), "X->[Y Z] : 1.0");
    assertEquals(r6.schemeString(tagIndex, wordIndex), "(X (_ Y) (_ Z))");
    assertEquals(r6.markString(tagIndex, wordIndex), "1.0 X --> Y Z");
    
    Rule r7 = new TerminalRule("X", Arrays.asList("a", "b", "c"), tagIndex, wordIndex);
    Rule r8 = new TerminalRule("X", Arrays.asList("a", "b", "c"), tagIndex, wordIndex);
    Rule r9 = new TerminalRule("X", Arrays.asList("a", "b", "d"), tagIndex, wordIndex);
    Rule r10 = new TagRule("X", Arrays.asList("Y", "Z"), tagIndex);
    Rule r11 = new TagRule("X", Arrays.asList("Y", "Z"), tagIndex);
    Rule r12 = new TagRule("X", Arrays.asList("Y", "T"), tagIndex);
    
    Rule r13 = new TagRule("X", Arrays.asList("a", "b", "c"), tagIndex);
    
    assertEquals(r7.equals(r8), true);
    assertEquals(r7.hashCode()==r8.hashCode(), true);
    assertEquals(r7.equals(r9), false);
    
    assertEquals(r10.equals(r11), true);
    assertEquals(r10.hashCode()==r11.hashCode(), true);
    assertEquals(r10.equals(r12), false);
    
    assertEquals(r7.equals(r13), false);
  }
}
