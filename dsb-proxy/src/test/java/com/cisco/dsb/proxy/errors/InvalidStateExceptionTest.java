package com.cisco.dsb.proxy.errors;

import org.testng.Assert;
import org.testng.annotations.Test;

public class InvalidStateExceptionTest {
  @Test
  void testConstructor() {
    InvalidStateException actualInvalidStateException = new InvalidStateException("Msg");
    Assert.assertNull(actualInvalidStateException.getCause());
    Assert.assertEquals(
        "com.cisco.dsb.proxy.errors.InvalidStateException: Msg",
        actualInvalidStateException.toString());
    Assert.assertEquals(0, actualInvalidStateException.getSuppressed().length);
    Assert.assertEquals("Msg", actualInvalidStateException.getMessage());
    Assert.assertEquals("Msg", actualInvalidStateException.getLocalizedMessage());
  }
}
