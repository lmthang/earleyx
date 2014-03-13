package test;

import java.util.ArrayList;
import java.util.Arrays;

import base.FragmentRule;
import base.Rule;
import base.Edge;


import junit.framework.TestCase;

import edu.stanford.nlp.util.HashIndex;
import edu.stanford.nlp.util.Index;


public class EdgeTest extends TestCase{
  public void testBasic(){
    Index<String> tagIndex = new HashIndex<String>();
    Index<String> wordIndex = new HashIndex<String>();
    Rule r1 = new FragmentRule("X", Arrays.asList("A", "B", "C"), tagIndex, wordIndex, true);
    Rule r2 = new FragmentRule("X", Arrays.asList("A", "B", "C"), tagIndex, wordIndex, true);
    Rule r3 = new FragmentRule("X", Arrays.asList("A", "B", "D"), tagIndex, wordIndex, true);
    Rule r4 = new FragmentRule("X", Arrays.asList("A", "D", "C"), tagIndex, wordIndex, true);
    Rule r5 = new FragmentRule("Y", Arrays.asList("A", "B", "C"), tagIndex, wordIndex, true);
    Rule r6 = new FragmentRule("X", new ArrayList<String>(), tagIndex, wordIndex, true);
    
    Edge e1 = new Edge(r1, 0); // X -> . A B C
    Edge e2 = new Edge(r2, 0); // X -> . A B C
    Edge e3 = new Edge(r3, 0); // X -> . A B D
    Edge e4 = new Edge(r1, 1); // X -> A . B C
    Edge e5 = new Edge(r3, 2); // X -> A B . D
    Edge e6 = new Edge(r4, 1); // X -> A . D C
    Edge e7 = new Edge(r1, 1); // X -> A . B C
    Edge e8 = new Edge(r5, 0); // Y -> . A B C
    Edge e9 = new Edge(r1, 3); // X -> A B C .
    Edge e10 = new Edge(r6, 0);// X -> .
    
    assertEquals(e1.equals(e2), true);
    assertEquals(e1.hashCode() == e2.hashCode(), true);
    assertEquals(e1.equals(e3), false);
    assertEquals(e1.equals(e4), false);
    assertEquals(e1.equals(e5), false);
    assertEquals(e1.equals(e6), false);
    assertEquals(e1.equals(e7), false);
    assertEquals(e4.equals(e7), true);
    assertEquals(e4.hashCode() == e7.hashCode(), true);
    assertEquals(e1.equals(e8), false);
    assertEquals(e6.equals(e7), false);
    assertEquals(e9.equals(e10), false);
    
    // test getToEdge
//    assertEquals(e6.getToEdge().equals(e7.getToEdge()), true);
//    assertEquals(e6.getToEdge().hashCode() == e7.getToEdge().hashCode(), true);
    
    // test getMotherEdge
    assertEquals(e1.getMotherEdge().equals(e3.getMotherEdge()), true);
    assertEquals(e1.getMotherEdge().hashCode() == e3.getMotherEdge().hashCode(), true);

    // test getViaEdge
    System.err.println(e1.toString(tagIndex, wordIndex) + "\t" + e1);
    System.err.println(e3.toString(tagIndex, wordIndex) + "\t" + e3);
    assertEquals(e1.getViaEdge().equals(e3.getViaEdge()), true);
    assertEquals(e1.getViaEdge().hashCode() == e3.getViaEdge().hashCode(), true);
    
    assertEquals(e9.getMotherEdge().equals(e10), true);
    assertEquals(e9.getMotherEdge().hashCode() == e10.hashCode(), true);
    assertEquals(e5.toString(tagIndex, wordIndex), "X -> A B . D");
    
    // test getPrevEdge
    assertEquals(e9.getPrevEdge().getPrevEdge().getPrevEdge().equals(e1), true);
    
    // test index of Edge
    Index<Edge> index = new HashIndex<Edge>();
    index.addAll(Arrays.asList(e1, e2, e3, e4, e5, e6, e7, e8, e9, e10));
    //System.err.println(Util.sprint(index, tagIndex, wordIndex));
    assertEquals(index.indexOf(e1), 0);
    assertEquals(index.indexOf(e2), 0);
    assertEquals(index.indexOf(e3), 1);
    assertEquals(index.indexOf(e4), 2);
    assertEquals(index.indexOf(e5), 3);
    assertEquals(index.indexOf(e6), 4);
    assertEquals(index.indexOf(e7), 2);
    assertEquals(index.indexOf(e8), 5);
    assertEquals(index.indexOf(e9), 6);
    assertEquals(index.indexOf(e10), 7);
  }
}
