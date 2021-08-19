package com.cisco.dsb.common.sip.bean;

import org.testng.Assert;
import org.testng.annotations.Test;

public class SIPProxyTest {

  @Test(description = "SIPProxy object construction using builder")
  public void testSIPProxyObjectCreation() {

    SIPProxy sipProxy =
        new SIPProxy.SIPProxyBuilder()
            .setErrorAggregator(true)
            .setCreateDNSServergroup(true)
            .setProcessRouteHeader(true)
            .setProcessRegisterRequest(true)
            .build();

    Assert.assertNotNull(sipProxy);
    Assert.assertTrue(sipProxy.isErrorAggregator());
    Assert.assertTrue(sipProxy.isCreateDNSServerGroup());
    Assert.assertTrue(sipProxy.isProcessRouteHeader());
    Assert.assertTrue(sipProxy.isProcessRegisterRequest());
  }
}
