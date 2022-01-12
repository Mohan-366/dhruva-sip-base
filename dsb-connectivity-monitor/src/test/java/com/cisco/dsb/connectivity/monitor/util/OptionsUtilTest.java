package com.cisco.dsb.connectivity.monitor.util;

import com.cisco.dsb.common.transport.Transport;
import org.testng.Assert;
import org.testng.annotations.Test;

public class OptionsUtilTest {

  @Test
  public void testTLSNumTries() {
    int expected =
        1; // TODO remove hardcoded values once optionsPingConfigProperties reads from config
    Assert.assertEquals(OptionsUtil.getNumRetry(Transport.TLS), expected);
  }

  @Test
  public void testTCPNumTries() {
    int expected = 1;
    Assert.assertEquals(OptionsUtil.getNumRetry(Transport.TCP), expected);
  }

  @Test
  public void testUDPNumTries() {
    int expected = 10;
    Assert.assertEquals(OptionsUtil.getNumRetry(Transport.UDP), expected);
  }

  @Test
  public void testDefaultNumTries() {
    int expected = 0;
    Assert.assertEquals(OptionsUtil.getNumRetry(Transport.NONE), expected);
  }
}
