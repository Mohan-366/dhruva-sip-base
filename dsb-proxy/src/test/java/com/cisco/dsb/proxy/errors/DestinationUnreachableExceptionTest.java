package com.cisco.dsb.proxy.errors;

import org.testng.Assert;
import org.testng.annotations.Test;

public class DestinationUnreachableExceptionTest {
  @Test
  void testConstructor() {
    DestinationUnreachableException actualDestinationUnreachableException =
        new DestinationUnreachableException("Msg");
    Assert.assertNull(actualDestinationUnreachableException.getCause());
    Assert.assertEquals(
        "com.cisco.dsb.proxy.errors.DestinationUnreachableException: Msg",
        actualDestinationUnreachableException.toString());
    Assert.assertEquals(0, actualDestinationUnreachableException.getSuppressed().length);
    Assert.assertEquals("Msg", actualDestinationUnreachableException.getMessage());
    Assert.assertEquals("Msg", actualDestinationUnreachableException.getLocalizedMessage());
  }

  @Test
  void testConstructor2() {
    Exception exception = new Exception("foo");
    DestinationUnreachableException actualDestinationUnreachableException =
        new DestinationUnreachableException("An error occurred", exception);

    Assert.assertNull(actualDestinationUnreachableException.getCause());
    Assert.assertEquals(
        "com.cisco.dsb.proxy.errors.DestinationUnreachableException: An error occurred",
        actualDestinationUnreachableException.toString());
    Throwable[] suppressed = actualDestinationUnreachableException.getSuppressed();
    Assert.assertEquals(0, suppressed.length);
    Assert.assertEquals("An error occurred", actualDestinationUnreachableException.getMessage());
    Assert.assertEquals(
        "An error occurred", actualDestinationUnreachableException.getLocalizedMessage());
    Assert.assertNull(exception.getCause());
    Assert.assertEquals("java.lang.Exception: foo", exception.toString());
    Assert.assertSame(suppressed, exception.getSuppressed());
    Assert.assertEquals("foo", exception.getMessage());
    Assert.assertEquals("foo", exception.getLocalizedMessage());
  }
}
