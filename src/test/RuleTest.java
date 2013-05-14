package test;

import java.util.Arrays;

import base.ProbRule;
import base.Rule;

import edu.stanford.nlp.util.HashIndex;
import edu.stanford.nlp.util.Index;

import junit.framework.TestCase;


public class RuleTest extends TestCase{
  public void testBasic(){
    Index<String> tagIndex = new HashIndex<String>();
    Index<String> wordIndex = new HashIndex<String>();
    ProbRule r1 = new ProbRule(new Rule("X", Arrays.asList("a", "b", "c"), tagIndex, wordIndex, false), 0.1);
    ProbRule r2 = new ProbRule(new Rule("X", Arrays.asList("a", "b", "c"), tagIndex, wordIndex, false), 0.1);
    ProbRule r3 = new ProbRule(new Rule("X", Arrays.asList("a", "b", "d"), tagIndex, wordIndex, false), 0.1);
    ProbRule r4 = new ProbRule(new Rule("Y", Arrays.asList("a", "b", "c"), tagIndex, wordIndex, false), 0.1);
    ProbRule r5 = new ProbRule(new Rule("X", Arrays.asList("a", "b", "c"), tagIndex, wordIndex, false), 0.2);
    assertEquals(r1.equals(r2), true);
    assertEquals(r1.hashCode() == r2.hashCode(), true);
    assertEquals(r1.equals(r3), false);
    assertEquals(r1.equals(r4), false);
    assertEquals(r1.equals(r5), false);
    assertEquals(r1.toString(tagIndex, wordIndex), "X->[_a _b _c] : 0.100000");
    assertEquals(r1.schemeString(tagIndex, wordIndex), "(X (_ _a) (_ _b) (_ _c))");
    assertEquals(r1.markString(tagIndex, wordIndex), "0.100000 X --> _a _b _c");
    assertEquals(r1.timString(tagIndex, wordIndex), "-2.30259 X _a _b _c");
    
    ProbRule r6 = new ProbRule(new Rule("X", Arrays.asList("Y", "Z"), tagIndex, wordIndex, true), 1.0);
    assertEquals(r6.toString(tagIndex, wordIndex), "X->[Y Z] : 1.00000");
    assertEquals(r6.schemeString(tagIndex, wordIndex), "(X (_ Y) (_ Z))");
    assertEquals(r6.markString(tagIndex, wordIndex), "1.00000 X --> Y Z");
    assertEquals(r6.timString(tagIndex, wordIndex), "0.00000 X Y Z");
    
    Rule r7 = new Rule("X", Arrays.asList("a", "b", "c"), tagIndex, wordIndex, false);
    Rule r8 = new Rule("X", Arrays.asList("a", "b", "c"), tagIndex, wordIndex, false);
    Rule r9 = new Rule("X", Arrays.asList("a", "b", "d"), tagIndex, wordIndex, false);
    Rule r10 = new Rule("X", Arrays.asList("Y", "Z"), tagIndex, wordIndex, true);
    Rule r11 = new Rule("X", Arrays.asList("Y", "Z"), tagIndex, wordIndex, true);
    Rule r12 = new Rule("X", Arrays.asList("Y", "T"), tagIndex, wordIndex, true);
    
    Rule r13 = new Rule("X", Arrays.asList("a", "b", "c"), tagIndex, wordIndex, true);
    
    assertEquals(r7.equals(r8), true);
    assertEquals(r7.hashCode()==r8.hashCode(), true);
    assertEquals(r7.equals(r9), false);
    
    assertEquals(r10.equals(r11), true);
    assertEquals(r10.hashCode()==r11.hashCode(), true);
    assertEquals(r10.equals(r12), false);
    
    assertEquals(r7.equals(r13), false);
    
    Rule r14 = new Rule("X", Arrays.asList("a", "B", "d"), tagIndex, wordIndex, new boolean[]{false, true, false});
    Rule r15 = new Rule("X", Arrays.asList("a", "B", "d"), tagIndex, wordIndex, new boolean[]{false, true, false});
    Rule r16 = new Rule("X", Arrays.asList("A", "B", "d"), tagIndex, wordIndex, new boolean[]{true, true, false});
    assertEquals("X->[_a B _d]", r14.toString(tagIndex, wordIndex));
    assertEquals("X->[_a B _d]", r15.toString(tagIndex, wordIndex));
    assertEquals("X->[A B _d]", r16.toString(tagIndex, wordIndex));
    assertEquals(true, r14.equals(r15));
    assertEquals(true, r14.hashCode() == r15.hashCode());
    assertEquals(false, r14.equals(r16));
    
    boolean[] flags = r16.getRhsTagFlags(0);
    assertEquals(true, flags[0]);
    assertEquals(true, flags[1]);
    assertEquals(false, flags[2]);
    
    
    flags = r16.getRhsTagFlags(1);
    assertEquals(true, flags[0]);
    assertEquals(false, flags[1]);
    
    flags = r16.getRhsTagFlags(2);
    assertEquals(false, flags[0]);
    
    Rule r17 = Rule.buildLhsOnlyRule(0);
    Rule r18 = Rule.buildLhsOnlyRule(0);
    assertEquals(true, r17.equals(r18));
    assertEquals(true, r17.hashCode() == r18.hashCode());
  }
}
