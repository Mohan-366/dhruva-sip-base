package com.cisco.dsb.trunk.util;

import lombok.CustomLog;
import org.testng.ISuite;
import org.testng.ISuiteListener;
import reactor.blockhound.BlockHound;

@CustomLog
public class TestSuiteListener implements ISuiteListener {

  @Override
  public void onStart(ISuite suite) {
    BlockHound.install();
    logger.info("BlockHound installed on start of 'dsb-trunk-service' test suite !!");
  }

  @Override
  public void onFinish(ISuite suite) {}
}