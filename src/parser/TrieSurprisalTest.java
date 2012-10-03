package parser;

import java.util.Arrays;

import edu.stanford.nlp.parser.lexparser.IntTaggedWord;
import edu.stanford.nlp.util.HashIndex;
import edu.stanford.nlp.util.Index;
import edu.stanford.nlp.util.Pair;

import junit.framework.TestCase;


public class TrieSurprisalTest extends TestCase {
  public void testBasicTrie(){
    TrieSurprisal trie = new TrieSurprisal(false); // no log score
    Index<String> wordIndex = new HashIndex<String>();
    
    IntTaggedWord o = new IntTaggedWord(wordIndex.indexOf("o", true), IntTaggedWord.ANY_TAG_INT);
    IntTaggedWord n = new IntTaggedWord(wordIndex.indexOf("n", true), IntTaggedWord.ANY_TAG_INT);
    IntTaggedWord e = new IntTaggedWord(wordIndex.indexOf("e", true), IntTaggedWord.ANY_TAG_INT);
    IntTaggedWord l = new IntTaggedWord(wordIndex.indexOf("l", true), IntTaggedWord.ANY_TAG_INT);
    IntTaggedWord y = new IntTaggedWord(wordIndex.indexOf("y", true), IntTaggedWord.ANY_TAG_INT);
    
    String trieStr = null;
    trie.append(Arrays.asList(o, n , e), new Pair<Integer, Double>(1, 1.0));
    trieStr = trie.toString(wordIndex);
    System.err.println(trieStr);
    assertEquals("\no:prefix={1=1.0}\n n:prefix={1=1.0}\n  e:prefix={1=1.0}, end={1=1.0}", trieStr);
    
    trie.append(Arrays.asList(o, n , l, y), new Pair<Integer, Double>(2, 2.0));
    trieStr = trie.toString(wordIndex);
    System.err.println(trieStr);
    assertEquals("\no:prefix={1=1.0, 2=2.0}\n n:prefix={1=1.0, 2=2.0}\n  e:prefix={1=1.0}, end={1=1.0}\n  l:prefix={2=2.0}\n   y:prefix={2=2.0}, end={2=2.0}", trieStr);
    
    trie.append(Arrays.asList(o, n , e), new Pair<Integer, Double>(1, 1.0));
    trieStr = trie.toString(wordIndex);
    System.err.println(trieStr);
    assertEquals("\no:prefix={1=2.0, 2=2.0}\n n:prefix={1=2.0, 2=2.0}\n  e:prefix={1=2.0}, end={1=2.0}\n  l:prefix={2=2.0}\n   y:prefix={2=2.0}, end={2=2.0}", trieStr);
    
    trie.append(Arrays.asList(o, n , l, y), new Pair<Integer, Double>(2, 2.0));
    trieStr = trie.toString(wordIndex);
    System.err.println(trieStr);
    assertEquals("\no:prefix={1=2.0, 2=4.0}\n n:prefix={1=2.0, 2=4.0}\n  e:prefix={1=2.0}, end={1=2.0}\n  l:prefix={2=4.0}\n   y:prefix={2=4.0}, end={2=4.0}", trieStr);
    
//    Pair<List<Pair<Integer, Double>>, Map<Integer, Integer>> pair = trie.findAllWithPairIdMap(Arrays.asList(o, n , e));
//    assertEquals("([(1,2.0)],{1=0})", pair);
//    pair = trie.findAllWithPairIdMap(Arrays.asList(o, n , l, y));
//    assertEquals("([(2,4.0)],{2=0})", pair);
  }
}
