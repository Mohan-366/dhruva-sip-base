package com.cisco.dsb.common.context;

import com.cisco.dsb.common.CommonContext;
import java.util.Map;
import org.testng.Assert;
import org.testng.annotations.Test;

public class ExecutionContextTest {

  @Test
  public void testBoolean() {
    ExecutionContext context = new ExecutionContext();
    Assert.assertEquals(context.getBoolean("test"), false);
    Assert.assertEquals(context.getBoolean("test", true), true);
  }

  @Test
  public void testCommonKeys() {
    ExecutionContext context = new ExecutionContext();
    context.set(CommonContext.PROXY_CONTROLLER, new Object());
    context.set(CommonContext.PROXY_CONSUMER, new Object());

    Assert.assertNotNull(context.get(CommonContext.PROXY_CONTROLLER));
    Assert.assertNotNull(context.get(CommonContext.PROXY_CONSUMER));
  }

  @Test
  public void testAddExtraHeaders() {
    ExecutionContext context = new ExecutionContext();
    context.addExtraHeader("X-Cisco-Header1", "test1");
    context.addExtraHeader("X-Cisco-Header2", "test2");
    Map headers = context.getExtraHeaders();
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
    ExecutionContext copyContext = context.copy();
    context.set("test1", "test");

    Assert.assertEquals(copyContext.get("test1"), "Dhruva");

    ExecutionContext cloneContext = context.clone();
    context.set("test1", "dhruva");
    Assert.assertNotEquals(cloneContext.get("test1"), "Dhruva");
  }
}
