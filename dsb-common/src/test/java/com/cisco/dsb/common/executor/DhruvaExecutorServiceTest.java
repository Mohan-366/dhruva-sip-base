package com.cisco.dsb.common.executor;

import static org.mockito.Mockito.when;

import com.cisco.wx2.util.stripedexecutor.StripedExecutorService;
import com.codahale.metrics.MetricRegistry;
import java.util.concurrent.*;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.springframework.core.env.Environment;
import org.testng.Assert;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.Test;

public class DhruvaExecutorServiceTest {

  @Mock Environment env;

  MetricRegistry metricRegistry;
  DhruvaExecutorService dhruvaExecutorService;

  @BeforeClass
  public void init() {
    MockitoAnnotations.initMocks(this);
    metricRegistry = new MetricRegistry();
    String prefix = "executor.testDhruvaPROXY_PROCESSOR";
    when(env.getProperty(prefix + ".queue.ttl.millis", Long.class, -1L)).thenReturn(-1L);
    when(env.getProperty(prefix + ".queue.ttl.action", String.class, "log")).thenReturn("log");

    when(env.getProperty(prefix + ".min", Integer.class, 10)).thenReturn(10);
    when(env.getProperty(prefix + ".max", Integer.class, 20)).thenReturn(20);
    when(env.getProperty(prefix + ".queue", Integer.class, 20)).thenReturn(20);
    when(env.getProperty(prefix + ".threadPriority", Integer.class, 5)).thenReturn(5);
    when(env.getProperty(prefix + ".keepalive-seconds", Integer.class, 60)).thenReturn(60);

    prefix = "executor.testDhruvaDNS_LOCATOR_SERVICE";
    when(env.getProperty(prefix + ".queue.ttl.millis", Long.class, -1L)).thenReturn(-1L);
    when(env.getProperty(prefix + ".queue.ttl.action", String.class, "log")).thenReturn("log");

    when(env.getProperty(prefix + ".min", Integer.class, 10)).thenReturn(10);
    when(env.getProperty(prefix + ".max", Integer.class, 20)).thenReturn(20);
    when(env.getProperty(prefix + ".queue", Integer.class, 20)).thenReturn(20);
    when(env.getProperty(prefix + ".keepalive-seconds", Integer.class, 60)).thenReturn(60);

    prefix = "executor.testDhruvaPROXY_CLIENT_TIMEOUT";
    when(env.getProperty(prefix + ".delayedExecutionThresholdMillis", Long.class, 100L))
        .thenReturn(100L);

    prefix = "executor.testDhruvaPROXY_SEND_MESSAGE";
    when(env.getProperty(prefix + ".delayedExecutionThresholdMillis", Long.class, 100L))
        .thenReturn(100L);

    dhruvaExecutorService = new DhruvaExecutorService("testDhruva", env, metricRegistry, 10, false);
  }

  @Test
  public void testStartExecutorService() throws InterruptedException, ExecutionException {
    dhruvaExecutorService.startExecutorService(ExecutorType.DNS_LOCATOR_SERVICE, 50);
    ExecutorService executorService =
        dhruvaExecutorService.getExecutorThreadPool(ExecutorType.DNS_LOCATOR_SERVICE);

    Assert.assertNotNull(executorService);
    Future<?> f =
        executorService.submit(
            () -> {
              System.out.println("submit runnable task");
            });
    Assert.assertNotNull(f);
    f.get();
    Assert.assertTrue(f.isDone());
    Assert.assertFalse(f.cancel(false)); // cannot cancel an already executed one

    // trying to start an executor that is already running. No impact, just logs this activity
    dhruvaExecutorService.startExecutorService(ExecutorType.DNS_LOCATOR_SERVICE, 50);

    executorService.shutdownNow();
  }

  @Test
  public void testStartScheduledExecutorService()
      throws InterruptedException, ExecutionException, TimeoutException {
    dhruvaExecutorService.startScheduledExecutorService(ExecutorType.PROXY_CLIENT_TIMEOUT, 1);
    ScheduledExecutorService executorService =
        dhruvaExecutorService.getScheduledExecutorThreadPool(ExecutorType.PROXY_CLIENT_TIMEOUT);
    ScheduledFuture<?> f =
        executorService.schedule(
            () -> {
              System.out.println("submit runnable task");
            },
            1,
            TimeUnit.MILLISECONDS);
    f.get(5, TimeUnit.SECONDS);
    Assert.assertFalse(
        f.isCancelled()); // no cancel was invoked during execution, so should return false
    Assert.assertTrue(f.isDone());

    DhruvaExecutorService.CustomScheduledThreadPoolExecutor.CustomScheduledTask
        customScheduledTask =
            (DhruvaExecutorService.CustomScheduledThreadPoolExecutor.CustomScheduledTask) f;
    Assert.assertFalse(customScheduledTask.isPeriodic());
    Assert.assertFalse(customScheduledTask.cancel(false));

    // trying to start an executor that is already running. No impact, just logs this activity
    dhruvaExecutorService.startScheduledExecutorService(ExecutorType.PROXY_CLIENT_TIMEOUT, 1);

    executorService.shutdownNow();
  }

  @Test
  public void testStartScheduledExecutorServiceWithCallableTask()
      throws InterruptedException, ExecutionException {
    dhruvaExecutorService.startScheduledExecutorService(ExecutorType.PROXY_SEND_MESSAGE, 1);
    ScheduledExecutorService executorService =
        dhruvaExecutorService.getScheduledExecutorThreadPool(ExecutorType.PROXY_SEND_MESSAGE);
    Callable c =
        () -> {
          System.out.println("submit callable task");
          return null;
        };
    ScheduledFuture<?> f = executorService.schedule(c, 1, TimeUnit.MILLISECONDS);

    // sleep till the future is executed to check status of completion
    Thread.sleep(10);
    Assert.assertTrue(f.isDone());

    // trying to start an executor that is already running. No impact, just logs this activity
    dhruvaExecutorService.startScheduledExecutorService(ExecutorType.PROXY_SEND_MESSAGE, 1);

    executorService.shutdownNow();
  }

  @Test
  public void testStartStripedExecutorService() throws InterruptedException, ExecutionException {
    dhruvaExecutorService.startStripedExecutorService(ExecutorType.PROXY_PROCESSOR);
    StripedExecutorService executorService =
        (StripedExecutorService)
            dhruvaExecutorService.getExecutorThreadPool(ExecutorType.PROXY_PROCESSOR);
    Assert.assertNotNull(executorService);
    Future<?> f =
        executorService.submit(
            () -> {
              System.out.println("submit striped runnable task");
            });
    Assert.assertNotNull(f);
    f.get();
    Assert.assertTrue(f.isDone());

    // trying to start an executor that is already running. No impact, just logs this activity
    dhruvaExecutorService.startStripedExecutorService(ExecutorType.PROXY_PROCESSOR);

    executorService.shutdown();
  }
}
