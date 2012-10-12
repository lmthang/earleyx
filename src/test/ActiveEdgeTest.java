package test;

import java.util.ArrayList;
import java.util.Arrays;

import parser.Edge;
import parser.BaseEdge;

import junit.framework.TestCase;

import edu.stanford.nlp.util.HashIndex;
import edu.stanford.nlp.util.Index;


public class ActiveEdgeTest extends TestCase{
  public void testBasic(){
    Index<String> motherIndex = new HashIndex<String>();
    Index<String> childIndex = new HashIndex<String>();
    BaseEdge r1 = new BaseEdge("X", Arrays.asList("a", "b", "c"), motherIndex, childIndex);
    BaseEdge r2 = new BaseEdge("X", Arrays.asList("a", "b", "c"), motherIndex, childIndex);
    BaseEdge r3 = new BaseEdge("X", Arrays.asList("a", "b", "d"), motherIndex, childIndex);
    BaseEdge r4 = new BaseEdge("X", Arrays.asList("a", "d", "c"), motherIndex, childIndex);
    BaseEdge r5 = new BaseEdge("Y", Arrays.asList("a", "b", "c"), motherIndex, childIndex);
    BaseEdge r6 = new BaseEdge("X", new ArrayList<String>(), motherIndex, childIndex);
    
    Edge e1 = new Edge(r1, 0);
    Edge e2 = new Edge(r2, 0);
    Edge e3 = new Edge(r3, 0);
    Edge e4 = new Edge(r1, 1);
    Edge e5 = new Edge(r3, 2);
    Edge e6 = new Edge(r4, 2);
    Edge e7 = new Edge(r1, 2);
    Edge e8 = new Edge(r5, 0);
    Edge e9 = new Edge(r1, 3);
    Edge e10 = new Edge(r6, 0);
    
    assertEquals(e1.equals(e2), true);
    assertEquals(e1.hashCode() == e2.hashCode(), true);
    assertEquals(e6.equals(e7), true);
    assertEquals(e6.hashCode() == e7.hashCode(), true);
    assertEquals(e1.equals(e3), false);
    assertEquals(e1.equals(e4), false);
    assertEquals(e1.equals(e5), false);
    assertEquals(e1.equals(e6), false);
    assertEquals(e1.equals(e7), false);
    assertEquals(e1.equals(e8), false);
    assertEquals(e9.equals(e10), true);
    assertEquals(e9.hashCode() == e10.hashCode(), true);
    System.err.println(e5.toString(motherIndex, childIndex));
    
    assertEquals(e5.toString(motherIndex, childIndex), "X -> a b . d");
    
    // test index of ActiveEdge
    Index<Edge> index = new HashIndex<Edge>();
    index.addAll(Arrays.asList(e1, e2, e3, e4, e5, e6, e7, e8, e9, e10));
    assertEquals(index.indexOf(e1), 0);
    assertEquals(index.indexOf(e2), 0);
    assertEquals(index.indexOf(e3), 1);
    assertEquals(index.indexOf(e4), 2);
    assertEquals(index.indexOf(e5), 3);
    assertEquals(index.indexOf(e6), 4);
    assertEquals(index.indexOf(e7), 4);
    assertEquals(index.indexOf(e8), 5);
    assertEquals(index.indexOf(e9), 6);
    assertEquals(index.indexOf(e10), 6);
  }
}
