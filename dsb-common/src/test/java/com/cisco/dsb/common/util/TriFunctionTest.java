package com.cisco.dsb.common.util;

import java.util.function.Function;
import org.testng.Assert;
import org.testng.annotations.Test;

public class TriFunctionTest {

  @Test
  public void testAndThen() {
    TriFunction<Integer, Integer, Integer, Integer> performAddition = (a, b, c) -> a + b + c;
    Function<Integer, String> printResult = i -> "Final result is " + i;
    TriFunction<Integer, Integer, Integer, String> composeFuncs =
        performAddition.andThen(printResult);
    Assert.assertEquals(composeFuncs.apply(4, 6, 2), "Final result is 12");
  }
}
