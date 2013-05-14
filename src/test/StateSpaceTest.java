package test;

import java.util.Arrays;

import base.Rule;
import base.Edge;

import parser.EdgeSpace;
import parser.LeftWildcardEdgeSpace;

import edu.stanford.nlp.util.HashIndex;
import edu.stanford.nlp.util.Index;

import junit.framework.TestCase;


public class StateSpaceTest extends TestCase{
  public void testBasic(){
    Index<String> tagIndex = new HashIndex<String>();
    Index<String> wordIndex = new HashIndex<String>();
    Rule r1 = new Rule("X", Arrays.asList("a", "b", "c"), tagIndex, wordIndex, true);
    Rule r2 = new Rule("X", Arrays.asList("a", "b", "d"), tagIndex, wordIndex, true);
    Rule r3 = new Rule("Y", Arrays.asList("a", "b", "c"), tagIndex, wordIndex, true);
    assertEquals(r1.equals(r3), false);
    EdgeSpace ss = new LeftWildcardEdgeSpace(tagIndex, wordIndex);
    assertEquals(ss.toString(), "");
    
    Edge e1 = new Edge(r1, 0);
    Edge e2 = new Edge(r2, 0);
    Edge e3 = new Edge(r3, 0);
    
    ss.addEdge(e1);
    assertEquals(ss.toString(), "<active=0, to=2 (X -> . a b c)>\n<passive=1 (a -> .)>\n<active=2, to=4 (X -> . b c)>\n<passive=3 (b -> .)>\n<active=4, to=6 (X -> . c)>\n<passive=5 (c -> .)>\n<passive=6 (X -> .)>\n");
    
    ss.addEdge(e2);
    assertEquals(ss.toString(), "<active=0, to=2 (X -> . a b c)>\n<passive=1 (a -> .)>\n<active=2, to=4 (X -> . b c)>\n<passive=3 (b -> .)>\n<active=4, to=6 (X -> . c)>\n<passive=5 (c -> .)>\n<passive=6 (X -> .)>\n<active=7, to=8 (X -> . a b d)>\n<active=8, to=9 (X -> . b d)>\n<active=9, to=6 (X -> . d)>\n<passive=10 (d -> .)>\n");
    
    ss.addEdge(e3);
    assertEquals(ss.toString(), "<active=0, to=2 (X -> . a b c)>\n<passive=1 (a -> .)>\n<active=2, to=4 (X -> . b c)>\n<passive=3 (b -> .)>\n<active=4, to=6 (X -> . c)>\n<passive=5 (c -> .)>\n<passive=6 (X -> .)>\n<active=7, to=8 (X -> . a b d)>\n<active=8, to=9 (X -> . b d)>\n<active=9, to=6 (X -> . d)>\n<passive=10 (d -> .)>\n<active=11, to=12 (Y -> . a b c)>\n<active=12, to=13 (Y -> . b c)>\n<active=13, to=14 (Y -> . c)>\n<passive=14 (Y -> .)>\n");
    
//    assertEquals(ss.getActiveIndices().toString(), "[0, 5]");
    assertEquals(tagIndex.toString(), "[0=X,1=a,2=b,3=c,4=d,5=Y]");
    assertEquals(ss.getActiveEdges().toString(), "[0, 2, 4, 7, 8, 9, 11, 12, 13]");
//    assertEquals(ss.getPassiveEdges().toString(), "[1, 3, 5, 6, 10, 14]");
  }
}

// <passive=0 (X -> .)>\n<passive=1 (a -> .)>\n<passive=2 (b -> .)>\n<passive=3 (c -> .)>\n<passive=4 (d -> .)>\n<passive=5 (Y -> .)>\n