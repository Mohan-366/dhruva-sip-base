package com.cisco.dsb.proxy.dto;

import static org.testng.Assert.*;

import org.testng.annotations.Test;

public class ProxyAppConfigTest {

  ProxyAppConfig appConfig = ProxyAppConfig.builder()._2xx(true).build();

  @Test(description = "test to getInterest for 2xx when true")
  public void testGetInterest() {
    boolean interest_2xx = appConfig.getInterest(2);
    boolean interest_4xx = appConfig.getInterest(4);
    assertTrue(interest_2xx);
    assertFalse(interest_4xx);
  }

  @Test(
      description = "getInterest on invalid response class",
      expectedExceptions = {ArrayIndexOutOfBoundsException.class})
  public void testGetInterestException() {
    appConfig.getInterest(7);
  }
}
