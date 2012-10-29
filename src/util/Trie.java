package util;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * Implementation of Trie based on the "S-Q Course Book" by Daniel Ellard and Penelope Ellard
 * For a sequence of elements:
 *   Retrieve a list of all associated values using getAllValues.
 *   Retrieve the first associated value using getFirstValue.
 *   Retrieve the associated trie using findTrie
 * @author Minh-Thang Luong, 2012
 *
 * @param <K, V>
 */
public class Trie<K, V> {
  private List<Trie<K, V>> trieList;
  protected List<K> keyList;

  protected int size;
  protected boolean isEnd; // indicate if this trie is the end of a string, i.e. a sequence of E
  private List<V> valueList; // list of values associated with the trie, only activates when isEnd = true

  public Trie() {
    trieList = new ArrayList<Trie<K, V>>();
    keyList = new ArrayList<K>();
    size = 0;
    isEnd = false;
    valueList = new ArrayList<V>();
  }

  /**
   * Find a sub-trie corresponds to the input element.
   * 
   * @param element
   * @return null if no child is found
   */
  protected Trie<K, V> findChild(K element) {
    for (int i = 0; i < size; i++) {
      if (keyList.get(i).equals(element)) {
        return trieList.get(i);
      }
    }
    return null;
  }
  
  /**
   * Find the trie corresponds to the list of elements
   * 
   * @param elements
   * @return
   */
  public Trie<K, V> findTrie(List<K> elements){
    Trie<K, V> curTrie = this;
    
    // go through each element
    for(K element : elements){
      curTrie = curTrie.findChild(element);
      
      if(curTrie == null){ // not found, not end of the string s
        break;
      }
    }
    
    return curTrie;
  }
  
  /**
   * Find sequences of key lists (List<List<K>>) below this trie, and are not longer than depth elements
   * For example: if the current trie corresponds to "c" "a" "r", we will return "e", "i" "n" "g", "s", which form complete words care, caring, and cars.
   * @param depth
   * @return
   */
  public List<List<K>> findSuffixes(int depth){
    List<List<K>> suffixList = new ArrayList<List<K>>();
    
    if(depth != 0){ // look further, note: we purposely put depth != 0, instead of depth > 0, to allow exhaustive search when depth=-1 
      for(int i=0; i<size; i++){
        K k = keyList.get(i);
        Trie<K, V> childTrie = trieList.get(i);
        
        // add single-element list
        if (childTrie.isEnd()){
          List<K> list = new ArrayList<K>();
          list.add(k);
          suffixList.add(list);
        }
        
        // add longer lists
        List<List<K>> childSuffixList = childTrie.findSuffixes(depth-1);
        for(List<K> childSuffix : childSuffixList){
          List<K> list = new ArrayList<K>();
          list.add(k);
          list.addAll(childSuffix);
          suffixList.add(list);
        }
      }
    }
    return suffixList;
  }
  
  /**
   * Find if the sequence elements exists, return the values associated with it. 
   * Otherwise, return null.
   * 
   * @param elements
   * @return
   */
  public List<V> getAllValues(List<K> elements) {
    Trie<K, V> curTrie = findTrie(elements);
    
    if(curTrie == null){ // not found, not end of the string s
      return null;
    }
    
    // see if this is the end
    // if so, we have a value to return at this trie
    if (curTrie.isEnd()) {
      return curTrie.getValues();
    } else { // not found, end of string s
      return null;
    }
  }
  
  /**
   * Same as findAll, but return only the first value 
   * from the list of values associated with the key elements.
   * 
   * @param elements
   * @return
   */
  public V getFirstValue(List<K> elements) {
    List<V> values = getAllValues(elements);
    if (values != null){
      return values.get(0);
    } else {
      return null;
    }
  }
  
  /**
   * Insert a single element into the current trie.
   * 
   * @param element
   * @return
   */
  protected Trie<K, V> insertChild(K element) {
    // is element already there?
    for (int i = 0; i < size; i++) {
      if (keyList.get(i).equals(element)) {
        Trie<K, V> result = trieList.get(i);
        return result;
      }
    }
    
    // element is not in trie
    Trie<K, V> result = new Trie<K, V>();
    keyList.add(element);
    trieList.add(result);
    size++;
    return result;
  }

  /**
   * Append a single value into the value list of 
   * the trie associated with they key elements.
   * If the key doesn't exist, create a new trie
   * associated to that key.
   * 
   * @param elements
   * @param value
   */
  public void append(List<K> elements, V value) {
    List<V> singleList = new ArrayList<V>();
    singleList.add(value);
    insert(elements, singleList, 0);
  }
  
  /**
   * Append a list of values into the value list of 
   * the trie associated with they key elements.
   * If the key doesn't exist, create a new trie
   * associated to that key.
   * 
   * @param elements
   * @param values
   */
  public void insert(List<K> elements, List<V> values) {
    insert(elements, values, 0);
  }
  
  /**
   * Insert element i in elements to the current child, 
   * and set end/value once i >= numElements.
   * 
   * @param elements
   * @param values
   * @param i
   */
  protected void insert(List<K> elements, List<V> values, int i) {
    // we've gotten to the end and 
    // the trie passed to us is empty (not null!)
    // setEnd and setValue
    int length = elements.size();
    if (i >= length) {
      setEnd(true);
      valueList.addAll(values);
      return;
    }
    
    Trie<K, V> nextT = insertChild(elements.get(i));
    //System.err.println(elements + "\t" + i + "\n" + nextT);
    nextT.insert(elements, values, i + 1);
  }
  
  /* Getters and Setters */
  public int getSize() {
    return size;
  }

  public boolean isEnd() {
    return isEnd;
  }

  protected void setEnd(boolean isEnd) {
    this.isEnd = isEnd;
  }

  protected List<V> getValues(){
    return valueList;
  }
  
  protected String indent = ""; // for printing purpose only
  public void setIndent(String indent){
    this.indent = indent;
  }
  
  public String toString(){
    StringBuffer sb = new StringBuffer();
    
    if(isEnd()){
      sb.append(valueList.toString());
    }
    
    int i=0;
    for(K element : keyList){
      trieList.get(i).setIndent(indent + " ");
      sb.append("\n" + indent + element + ":" + trieList.get(i));
      ++i;
    }
    return sb.toString();
  }
  
  public static void main(String[] args) {
    Trie<String, Boolean> ts = new Trie<String, Boolean>();
    ts.append(Arrays.asList("o", "n", "e"), true);
    ts.append(Arrays.asList("o", "n", "l", "y"), true);
    ts.append(Arrays.asList("o", "n", "e", "t", "o", "n"), true);
    ts.append(Arrays.asList("t", "w", "o"), true);
    
    System.out.println("one " + ts.getFirstValue(Arrays.asList("o", "n", "e")));
    System.out.println("only " + ts.getFirstValue(Arrays.asList("o", "n", "l", "y")));
    System.out.println("on " + ts.getFirstValue(Arrays.asList("o", "n")));
    System.out.println("onesin " + ts.getFirstValue(Arrays.asList("o", "n", "e", "s", "i", "n")));
    System.out.println("onetonly " + ts.getFirstValue(Arrays.asList("o", "n", "e", "o", "n", "l", "y")));
    System.out.println("twofer " + ts.getFirstValue(Arrays.asList("t", "w", "o", "f", "e", "r")));
    System.out.println("tw " + ts.getFirstValue(Arrays.asList("t", "w")));
    System.out.println("twitch " + ts.getFirstValue(Arrays.asList("t", "w", "i", "c", "h")));
    System.out.println("super " + ts.getFirstValue(Arrays.asList("s", "u", "p", "e", "r")));
    System.out.println("<empty> " + ts.getFirstValue(Arrays.asList("")));
    System.out.println(ts);
    
    Trie<String, Boolean> subTrie = ts.findTrie(Arrays.asList("o", "n"));
    System.out.println(subTrie);
    List<List<String>> suffixes = subTrie.findSuffixes(-1);
    System.out.println(suffixes);
  }
}

/** Unused code **/
//public K getChildKey(int index) {
//  if (index >= 0 && index < size){
//    return keyList.get(index);
//  } else {
//    return null;
//  }
//}
//
//public Trie<K, V> getChildTrie(int index){
//  if (index >= 0 && index < size){
//    return trieList.get(index);
//  } else {
//    return null;
//  }
//    
//}