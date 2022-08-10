package com.cisco.dhruva.callingIntegration.util;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.testng.ISuite;
import org.testng.ISuiteListener;
import reactor.blockhound.BlockHound;

public class TestSuiteListener implements ISuiteListener {

  public static final Logger logger = LoggerFactory.getLogger(TestSuiteListener.class);

  @Override
  public void onStart(ISuite suite) {
    BlockHound.install();
    logger.info("BlockHound installed on start of 'dsb-calling-app/integration' test suite !!");
  }

  @Override
  public void onFinish(ISuite suite) {}
}
