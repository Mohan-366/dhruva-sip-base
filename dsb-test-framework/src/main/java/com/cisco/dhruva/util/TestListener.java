package com.cisco.dhruva.util;

import static com.cisco.dhruva.util.TestLog.TEST_LOGGER;

import org.testng.ISuite;
import org.testng.ISuiteListener;
import reactor.blockhound.BlockHound;

public class TestListener implements ISuiteListener {

  @Override
  public void onStart(ISuite suite) {
    BlockHound.install();
    TEST_LOGGER.info("BlockHound installed on start of 'dsb-fts'!!");
  }

  @Override
  public void onFinish(ISuite suite) {}
}
