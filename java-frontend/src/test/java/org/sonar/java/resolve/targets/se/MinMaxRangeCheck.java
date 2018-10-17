package org.sonar.java.resolve.targets.se;

class MinMaxRangeCheck {

  private static final int UPPER = 20;
  private static final int LOWER = 0;
  private int otherValue = 0;

  public int doRangeCheckNOK1(int num) { // Let's say num = 12
    int result = Math.min(LOWER, num); // result = 0
    return Math.max(UPPER, result); // Noncompliant; result is now 20: even though 12 was in the range
  }

  public int doRangeCheckNOK2(int num) { // Let's say num = 12
    int result = Math.min(num, LOWER); // result = 0
    return Math.max(result, UPPER); // Noncompliant; result is now 20: even though 12 was in the range
  }

  public int doRangeCheckOK1(int num) { // Let's say num = 12
    int result = Math.min(UPPER, num); // result = 12
    return Math.max(LOWER, result); // Compliant; result is still 12
  }

  public int doRangeCheckOK2(int a, int b) {
    int result = Math.min(a, b);
    return Math.max(LOWER, result); // Compliant; result could be LOWER, a or b
  }

  public int doRangeCheckOK3(int a, int b) {
    int result = Math.min(a, b);
    return Math.max(otherValue, result); // Compliant; result could be otherValue, a or b
  }

  public void foo() {
    foo();
  }
}
