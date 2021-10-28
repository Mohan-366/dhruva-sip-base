package com.cisco.dsb.proxy.sip;

import org.testng.Assert;
import org.testng.annotations.Test;

public class SIPProxyTest {

  @Test(description = "SIPProxy object construction using builder")
  public void testSIPProxyObjectCreation() {

    SIPProxy sipProxy =
        SIPProxy.builder()
            .errorAggregator(true)
            .createDNSServerGroup(true)
            .processRouteHeader(true)
            .processRegisterRequest(true)
            .timerCIntervalInMilliSec(60000)
            .build();
    Assert.assertNotNull(sipProxy);
    Assert.assertTrue(sipProxy.isErrorAggregator());
    Assert.assertTrue(sipProxy.isCreateDNSServerGroup());
    Assert.assertTrue(sipProxy.isProcessRouteHeader());
    Assert.assertTrue(sipProxy.isProcessRegisterRequest());
    Assert.assertEquals(sipProxy.getTimerCIntervalInMilliSec(), 60000);
  }
}
