package com.cisco.dhruva.util;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.testng.ISuite;
import org.testng.ISuiteListener;
import reactor.blockhound.BlockHound;

public class TestListener implements ISuiteListener {

  public static final Logger TEST_LOGGER = LoggerFactory.getLogger(TestListener.class);

  @Override
  public void onStart(ISuite suite) {
    BlockHound.install();
    TEST_LOGGER.info("BlockHound installed on start of 'dsb-test-framework' test suite !!");
  }

  @Override
  public void onFinish(ISuite suite) {}
}
