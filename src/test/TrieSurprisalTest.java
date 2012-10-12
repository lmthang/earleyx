package test;

import java.util.Arrays;

import utility.TrieSurprisal;

import edu.stanford.nlp.util.HashIndex;
import edu.stanford.nlp.util.Index;
import edu.stanford.nlp.util.Pair;

import junit.framework.TestCase;


public class TrieSurprisalTest extends TestCase {
  public void testBasicTrie(){
    TrieSurprisal trie = new TrieSurprisal(false); // no log score
    Index<String> wordIndex = new HashIndex<String>();
    Index<String> tagIndex = new HashIndex<String>();
    
    int o = wordIndex.indexOf("o", true);
    int n = wordIndex.indexOf("n", true);
    int e = wordIndex.indexOf("e", true);
    int l = wordIndex.indexOf("l", true);
    int y = wordIndex.indexOf("y", true);
    int xIndex = tagIndex.indexOf("X", true);
    int yIndex = tagIndex.indexOf("Y", true);
    
    String trieStr = null;
    trie.append(Arrays.asList(o, n , e), new Pair<Integer, Double>(xIndex, 1.0));
    trieStr = trie.toString(wordIndex, tagIndex);
    System.err.println(trieStr);
    assertEquals("\no:prefix={X=1.0}\n n:prefix={X=1.0}\n  e:prefix={X=1.0}, end={X=1.0}", trieStr);
    
    trie.append(Arrays.asList(o, n , l, y), new Pair<Integer, Double>(yIndex, 2.0));
    trieStr = trie.toString(wordIndex, tagIndex);
    System.err.println(trieStr);
    assertEquals("\no:prefix={X=1.0, Y=2.0}\n n:prefix={X=1.0, Y=2.0}\n  e:prefix={X=1.0}, end={X=1.0}\n  l:prefix={Y=2.0}\n   y:prefix={Y=2.0}, end={Y=2.0}", trieStr);
    
    trie.append(Arrays.asList(o, n , e), new Pair<Integer, Double>(xIndex, 1.0));
    trieStr = trie.toString(wordIndex, tagIndex);
    System.err.println(trieStr);
    assertEquals("\no:prefix={X=2.0, Y=2.0}\n n:prefix={X=2.0, Y=2.0}\n  e:prefix={X=2.0}, end={X=2.0}\n  l:prefix={Y=2.0}\n   y:prefix={Y=2.0}, end={Y=2.0}", trieStr);
    
    trie.append(Arrays.asList(o, n , l, y, o, n, e), new Pair<Integer, Double>(yIndex, 2.0));
    trieStr = trie.toString(wordIndex, tagIndex);
    System.err.println(trieStr);
    assertEquals("\no:prefix={X=2.0, Y=4.0}\n n:prefix={X=2.0, Y=4.0}\n  e:prefix={X=2.0}, end={X=2.0}\n  l:prefix={Y=4.0}\n   y:prefix={Y=4.0}, end={Y=2.0}\n    o:prefix={Y=2.0}\n     n:prefix={Y=2.0}\n      e:prefix={Y=2.0}, end={Y=2.0}", trieStr);
  }
  
  public void testLogTrie(){
    TrieSurprisal trie = new TrieSurprisal(); // no log score
    Index<String> wordIndex = new HashIndex<String>();
    Index<String> tagIndex = new HashIndex<String>();
    
    int o = wordIndex.indexOf("o", true);
    int n = wordIndex.indexOf("n", true);
    int e = wordIndex.indexOf("e", true);
    int l = wordIndex.indexOf("l", true);
    int y = wordIndex.indexOf("y", true);
    int xIndex = tagIndex.indexOf("X", true);
    int yIndex = tagIndex.indexOf("Y", true);
    
    String trieStr = null;
    trie.append(Arrays.asList(o, n , e), new Pair<Integer, Double>(xIndex, Math.log(1.0)));
    trieStr = trie.toString(wordIndex, tagIndex);
    System.err.println(trieStr);
    assertEquals("\no:prefix={X=0.0}\n n:prefix={X=0.0}\n  e:prefix={X=0.0}, end={X=0.0}", trieStr);
    
    trie.append(Arrays.asList(o, n , l, y), new Pair<Integer, Double>(yIndex, Math.log(2.0)));
    trieStr = trie.toString(wordIndex, tagIndex);
    System.err.println(trieStr);
    assertEquals("\no:prefix={X=0.0, Y=0.7}\n n:prefix={X=0.0, Y=0.7}\n  e:prefix={X=0.0}, end={X=0.0}\n  l:prefix={Y=0.7}\n   y:prefix={Y=0.7}, end={Y=0.7}", trieStr);
    
    trie.append(Arrays.asList(o, n , e), new Pair<Integer, Double>(xIndex, Math.log(1.0)));
    trieStr = trie.toString(wordIndex, tagIndex);
    System.err.println(trieStr);
    assertEquals("\no:prefix={X=0.7, Y=0.7}\n n:prefix={X=0.7, Y=0.7}\n  e:prefix={X=0.7}, end={X=0.7}\n  l:prefix={Y=0.7}\n   y:prefix={Y=0.7}, end={Y=0.7}", trieStr);
    
    trie.append(Arrays.asList(o, n , l, y, o, n, e), new Pair<Integer, Double>(yIndex, Math.log(3.0)));
    trieStr = trie.toString(wordIndex, tagIndex);
    System.err.println(trieStr);
    assertEquals("\no:prefix={X=0.7, Y=1.6}\n n:prefix={X=0.7, Y=1.6}\n  e:prefix={X=0.7}, end={X=0.7}\n  l:prefix={Y=1.6}\n   y:prefix={Y=1.6}, end={Y=0.7}\n    o:prefix={Y=1.1}\n     n:prefix={Y=1.1}\n      e:prefix={Y=1.1}, end={Y=1.1}", trieStr);
  }
}
