package com.cisco.dsb.common.util;

import org.testng.IRetryAnalyzer;
import org.testng.ITestResult;

public class LbWeightRetryAnalyser implements IRetryAnalyzer {
  int counter = 0;
  int retryLimit = 10;

  @Override
  public boolean retry(ITestResult iTestResult) {
    if (counter < retryLimit) {
      counter++;
      return true;
    }
    return false;
  }
}
