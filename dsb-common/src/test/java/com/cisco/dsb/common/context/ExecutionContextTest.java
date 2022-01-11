package com.cisco.dsb.common.context;

import java.util.Map;
import org.testng.Assert;
import org.testng.annotations.Test;

public class ExecutionContextTest {

  @Test
  public void testBoolean() {
    ExecutionContext context = new ExecutionContext();
    Assert.assertFalse(context.getBoolean("test"));
    Assert.assertTrue(context.getBoolean("test", true));

    context.setBoolean("test");
    Assert.assertTrue(context.getBoolean("test"));
  }

  @Test
  public void testCommonKeys() {
    ExecutionContext context = new ExecutionContext();
    context.set(CommonContext.PROXY_CONTROLLER, new Object());
    context.set(CommonContext.PROXY_CONSUMER, null);

    Assert.assertNotNull(context.get(CommonContext.PROXY_CONTROLLER));
    Assert.assertNull(context.get(CommonContext.PROXY_CONSUMER));
  }

  @Test
  public void testAddExtraHeaders() {
    ExecutionContext context = new ExecutionContext();
    context.addExtraHeader("X-Cisco-Header1", "test1");
    context.addExtraHeader("X-Cisco-Header2", "test2");
    context.addExtraHeader("X-Cisco-Header2", "new-value");
    Map<String, String> headers = context.getExtraHeaders();
    Assert.assertEquals(headers.get("X-Cisco-Header1"), "test1");
    Assert.assertEquals(headers.get("X-Cisco-Header2"), "test2");
  }

  @Test
  public void testSetError() {
    ExecutionContext context = new ExecutionContext();
    context.setError(new Exception("test error"));
    Assert.assertTrue(context.getError() instanceof Throwable);
  }

  @Test
  public void testCopy() {
    ExecutionContext context = new ExecutionContext();
    context.set("test1", "Dhruva");
    context.set("test2", "sample");
    ExecutionContext copyContext = context.copy();
    context.set("test1", "test");
    Assert.assertEquals(copyContext.get("test1"), "Dhruva");
    Assert.assertEquals(copyContext.get("test2"), "sample");

    ExecutionContext cloneContext = context.clone();
    context.set("test1", "dhruva");
    Assert.assertNotEquals(cloneContext.get("test1"), "Dhruva");
  }
}
