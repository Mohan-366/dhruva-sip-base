package com.cisco.dsb.proxy.errors;

import org.testng.Assert;
import org.testng.annotations.Test;

public class InternalProxyErrorExceptionTest {
  @Test
  void testConstructor() {
    InternalProxyErrorException actualInternalProxyErrorException =
        new InternalProxyErrorException("Msg");
    Assert.assertNull(actualInternalProxyErrorException.getCause());
    Assert.assertEquals(
        "com.cisco.dsb.proxy.errors.InternalProxyErrorException: Msg",
        actualInternalProxyErrorException.toString());
    Assert.assertEquals(0, actualInternalProxyErrorException.getSuppressed().length);
    Assert.assertEquals("Msg", actualInternalProxyErrorException.getMessage());
    Assert.assertEquals("Msg", actualInternalProxyErrorException.getLocalizedMessage());
  }
}
