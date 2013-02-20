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
    System.err.println("n=" + n);
    System.err.println("sloppy: " + SloppyMath.gamma(n));
    System.err.println("sloppy log: " + SloppyMath.lgamma(n));
    System.err.println("mallet: " + Maths.gamma(n));
    System.err.println("mallet log: " + Maths.logGamma(n));
    n = 200;
    System.err.println("n=" + n);
    System.err.println("sloppy: " + SloppyMath.gamma(n));
    System.err.println("sloppy log: " + SloppyMath.lgamma(n));
    System.err.println("mallet: " + Maths.gamma(n));
    System.err.println("mallet log: " + Maths.logGamma(n));
    n = 1e10;
    System.err.println("n=" + n);
    System.err.println("sloppy: " + SloppyMath.gamma(n));
    System.err.println("sloppy log: " + SloppyMath.lgamma(n));
    System.err.println("mallet: " + Maths.gamma(n));
    System.err.println("mallet log: " + Maths.logGamma(n));
  }

}
