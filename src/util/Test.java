package util;

import cc.mallet.util.Maths;
import edu.stanford.nlp.math.SloppyMath;

public class Test {

  /**
   * @param args
   */
  public static void main(String[] args) {
    double n = 0.001;
    System.err.println(1/810*Math.pow(n,6)); // bug in SloppyMath.gamma
    System.err.println(SloppyMath.gamma(n));
    System.err.println(Maths.gamma(n));
    n = 1e10;
    System.err.println(Maths.gamma(n));
  }

}
