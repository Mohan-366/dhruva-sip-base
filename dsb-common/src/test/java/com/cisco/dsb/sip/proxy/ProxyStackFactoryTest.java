package com.cisco.dsb.sip.proxy;

import java.util.Properties;
import org.testng.Assert;
import org.testng.annotations.Test;

public class ProxyStackFactoryTest {

  @Test(description = "default Proxy stack properties check")
  public void testDefaultProxyStackProperties() {
    Properties prop = ProxyStackFactory.getDefaultProxyStackProperties("Test-Proxy");
    Assert.assertEquals(prop.size(), 10);
    Assert.assertEquals(prop.getProperty("javax.sip.STACK_NAME"), "Test-Proxy");
  }
}
