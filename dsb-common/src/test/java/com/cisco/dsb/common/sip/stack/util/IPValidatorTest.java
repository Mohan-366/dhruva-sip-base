package com.cisco.dsb.common.sip.stack.util;

import org.testng.Assert;
import org.testng.annotations.DataProvider;
import org.testng.annotations.Test;

public class IPValidatorTest {

  @DataProvider
  public Object[][] ipv4() {
    return new Object[][] {
      {"10.0.0.1", true},
      {null, false},
      {"", false},
      {"10.0.0.1.2", false},
      {"1.abcd.2.3", false}
    };
  }

  @Test
  public void testIpValidation() {
    Assert.assertTrue(IPValidator.hostIsIPAddr("192.168.1.1"));
    Assert.assertFalse(IPValidator.hostIsIPAddr("ciscowebex.com"));
    Assert.assertTrue(IPValidator.hostIsIPAddr("2001:db8:3333:4444:5555:6666:7777:8888"));

    Assert.assertFalse(IPValidator.hostIsIPv6Addr("10.78.98.1"));
  }

  @Test(dataProvider = "ipv4")
  public void testIpv4AddrValidation(String address, boolean expectedResult) {
    Assert.assertEquals(IPValidator.hostIsIPAddr(address), expectedResult);
  }
}
