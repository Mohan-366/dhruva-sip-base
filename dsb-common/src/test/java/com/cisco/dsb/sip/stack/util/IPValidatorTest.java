package com.cisco.dsb.sip.stack.util;

import org.testng.Assert;
import org.testng.annotations.Test;

public class IPValidatorTest {

  @Test
  public void testValidIpv4Address() {
    Assert.assertTrue(IPValidator.hostIsIPAddr("192.168.1.1"));
    Assert.assertTrue(IPValidator.hostIsIPv4Addr("10.0.0.1"));
    Assert.assertFalse(IPValidator.hostIsIPAddr("ciscowebex.com"));
    Assert.assertTrue(IPValidator.hostIsIPv6Addr("2001:db8:3333:4444:5555:6666:7777:8888"));
    Assert.assertFalse(IPValidator.hostIsIPv6Addr("10.78.98.1"));
  }
}
