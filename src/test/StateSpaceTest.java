package test;

import java.util.Arrays;

import edu.stanford.nlp.util.HashIndex;
import edu.stanford.nlp.util.Index;

import parser.Edge;
import parser.BaseEdge;
import parser.EdgeSpace;
import junit.framework.TestCase;


public class StateSpaceTest extends TestCase{
  public void testBasic(){
    Index<String> tagIndex = new HashIndex<String>();
    BaseEdge r1 = new BaseEdge("X", Arrays.asList("a", "b", "c"), tagIndex, tagIndex);
    BaseEdge r2 = new BaseEdge("X", Arrays.asList("a", "b", "d"), tagIndex, tagIndex);
    BaseEdge r3 = new BaseEdge("Y", Arrays.asList("a", "b", "c"), tagIndex, tagIndex);
    assertEquals(r1.equals(r3), false);
    EdgeSpace ss = new EdgeSpace(tagIndex);
    EdgeSpace.verbose = 3;
    assertEquals(ss.toString(), "");
    
    Edge e1 = new Edge(r1, 0);
    Edge e2 = new Edge(r2, 0);
    Edge e3 = new Edge(r3, 0);
    
    ss.addEdge(e1);
    assertEquals(ss.toString(), "<passive=0 (X -> .)>\n<active=1, via=2, to=3 (X -> . a b c)>\n<passive=2 (a -> .)>\n<active=3, via=4, to=5 (X -> a . b c)>\n<passive=4 (b -> .)>\n<active=5, via=6, to=0 (X -> a b . c)>\n<passive=6 (c -> .)>\n");
    
    ss.addEdge(e2);
    assertEquals(ss.toString(), "<passive=0 (X -> .)>\n<active=1, via=2, to=3 (X -> . a b c)>\n<passive=2 (a -> .)>\n<active=3, via=4, to=5 (X -> a . b c)>\n<passive=4 (b -> .)>\n<active=5, via=6, to=0 (X -> a b . c)>\n<passive=6 (c -> .)>\n<active=7, via=2, to=8 (X -> . a b d)>\n<active=8, via=4, to=9 (X -> a . b d)>\n<active=9, via=10, to=0 (X -> a b . d)>\n<passive=10 (d -> .)>\n");
    
    ss.addEdge(e3);
    assertEquals(ss.toString(), "<passive=0 (X -> .)>\n<active=1, via=2, to=3 (X -> . a b c)>\n<passive=2 (a -> .)>\n<active=3, via=4, to=5 (X -> a . b c)>\n<passive=4 (b -> .)>\n<active=5, via=6, to=0 (X -> a b . c)>\n<passive=6 (c -> .)>\n<active=7, via=2, to=8 (X -> . a b d)>\n<active=8, via=4, to=9 (X -> a . b d)>\n<active=9, via=10, to=0 (X -> a b . d)>\n<passive=10 (d -> .)>\n<passive=11 (Y -> .)>\n<active=12, via=2, to=13 (Y -> . a b c)>\n<active=13, via=4, to=14 (Y -> a . b c)>\n<active=14, via=6, to=11 (Y -> a b . c)>\n");
    
    assertEquals(ss.getActiveIndices().toString(), "[0, 5]");
    assertEquals(tagIndex.toString(), "[0=X,1=a,2=b,3=c,4=d,5=Y]");
    assertEquals(ss.getActiveEdges().toString(), "[1, 3, 5, 7, 8, 9, 12, 13, 14]");
    assertEquals(ss.getPassiveEdges().toString(), "[0, 2, 4, 6, 10, 11]");
  }
}

// <passive=0 (X -> .)>\n<passive=1 (a -> .)>\n<passive=2 (b -> .)>\n<passive=3 (c -> .)>\n<passive=4 (d -> .)>\n<passive=5 (Y -> .)>\n