package org.sonar.java.se.checks;

import org.junit.Test;
import org.sonar.java.se.JavaCheckVerifier;

public class MinMaxRangeCheckTest {

  @Test
  public void test() {
    JavaCheckVerifier.verify("src/test/java/org/sonar/java/resolve/targets/se/MinMaxRangeCheck.java", new MinMaxRangeCheck());
  }

}
