package com.cisco.dsb.common.ua;

import java.util.Properties;
import org.testng.Assert;
import org.testng.annotations.Test;

public class UAStackFactoryTest {

  @Test(description = "UA stack default properties check")
  public void testDefaultUAStackProperties() {

    Properties prop = UAStackFactory.getDefaultUAStackProperties("Test-UA");
    Assert.assertEquals(prop.size(), 10);
    Assert.assertEquals(prop.getProperty("javax.sip.STACK_NAME"), "Test-UA");
  }
}
