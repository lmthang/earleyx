package test;

import java.util.ArrayList;
import java.util.Arrays;

import util.Util;

import base.Rule;
import base.Edge;


import junit.framework.TestCase;

import edu.stanford.nlp.util.HashIndex;
import edu.stanford.nlp.util.Index;


public class ActiveEdgeTest extends TestCase{
  public void testBasic(){
    Index<String> motherIndex = new HashIndex<String>();
    Index<String> childIndex = new HashIndex<String>();
    Rule r1 = new Rule("X", Arrays.asList("a", "b", "c"), motherIndex, childIndex);
    Rule r2 = new Rule("X", Arrays.asList("a", "b", "c"), motherIndex, childIndex);
    Rule r3 = new Rule("X", Arrays.asList("a", "b", "d"), motherIndex, childIndex);
    Rule r4 = new Rule("X", Arrays.asList("a", "d", "c"), motherIndex, childIndex);
    Rule r5 = new Rule("Y", Arrays.asList("a", "b", "c"), motherIndex, childIndex);
    Rule r6 = new Rule("X", new ArrayList<String>(), motherIndex, childIndex);
    
    Edge e1 = new Edge(r1, 0); // X -> . a b c
    Edge e2 = new Edge(r2, 0); // X -> . a b c
    Edge e3 = new Edge(r3, 0); // X -> . a b d
    Edge e4 = new Edge(r1, 1); // X -> a . b c
    Edge e5 = new Edge(r3, 2); // X -> a b . d
    Edge e6 = new Edge(r4, 1); // X -> a . d c
    Edge e7 = new Edge(r1, 1); // X -> a . b c
    Edge e8 = new Edge(r5, 0); // Y -> . a b c
    Edge e9 = new Edge(r1, 3); // X -> a b c .
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
    System.err.println(e6.getToEdge().toString(motherIndex, childIndex));
    System.err.println(e7.getToEdge().toString(motherIndex, childIndex));
    assertEquals(e6.getToEdge().equals(e7.getToEdge()), true);
    assertEquals(e6.getToEdge().hashCode() == e7.getToEdge().hashCode(), true);
    
    // test getMotherEdge
    assertEquals(e1.getMotherEdge().equals(e3.getMotherEdge()), true);
    assertEquals(e1.getMotherEdge().hashCode() == e3.getMotherEdge().hashCode(), true);

    // test getViaEdge
    assertEquals(e1.getViaEdge().equals(e3.getViaEdge()), true);
    assertEquals(e1.getViaEdge().hashCode() == e3.getViaEdge().hashCode(), true);
    
    assertEquals(e9.getMotherEdge().equals(e10), true);
    assertEquals(e9.getMotherEdge().hashCode() == e10.hashCode(), true);
    assertEquals(e5.toString(motherIndex, childIndex), "X -> a b . d");
    
    // test index of ActiveEdge
    Index<Edge> index = new HashIndex<Edge>();
    index.addAll(Arrays.asList(e1, e2, e3, e4, e5, e6, e7, e8, e9, e10));
    System.err.println(Util.sprint(index, motherIndex, childIndex));
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
