package com.cisco.dsb.common.executor;

import static org.mockito.Mockito.when;

import com.cisco.wx2.util.stripedexecutor.StripedExecutorService;
import com.codahale.metrics.MetricRegistry;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.springframework.core.env.Environment;
import org.springframework.mock.env.MockEnvironment;
import org.testng.Assert;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.Test;

public class DhruvaExecutorServiceTest {

  @Mock Environment env = new MockEnvironment();

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
    when(env.getProperty(prefix + ".keepalive-seconds", Integer.class, 60)).thenReturn(60);

    prefix = "executor.testDhruvaDNS_LOCATOR_SERVICE";
    when(env.getProperty(prefix + ".queue.ttl.millis", Long.class, -1L)).thenReturn(-1L);
    when(env.getProperty(prefix + ".queue.ttl.action", String.class, "log")).thenReturn("log");

    when(env.getProperty(prefix + ".min", Integer.class, 10)).thenReturn(10);
    when(env.getProperty(prefix + ".max", Integer.class, 20)).thenReturn(20);
    when(env.getProperty(prefix + ".queue", Integer.class, 20)).thenReturn(20);
    when(env.getProperty(prefix + ".keepalive-seconds", Integer.class, 60)).thenReturn(60);
    dhruvaExecutorService = new DhruvaExecutorService("testDhruva", env, metricRegistry, 10, false);
  }

  @Test
  public void testStartExecutorService()
      throws InterruptedException, ExecutionException, TimeoutException {
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

    executorService.shutdownNow();
  }

  @Test
  public void testStartScheduledExecutorService()
      throws InterruptedException, ExecutionException, TimeoutException {
    dhruvaExecutorService.startScheduledExecutorService(ExecutorType.PROXY_CLIENT_TIMEOUT, 1);
    ScheduledThreadPoolExecutor executorService =
        dhruvaExecutorService.getScheduledExecutorThreadPool(ExecutorType.PROXY_CLIENT_TIMEOUT);
    AtomicBoolean executed = new AtomicBoolean(false);
    ScheduledFuture<?> f =
        executorService.schedule(
            () -> {
              System.out.println("submit runnable task");
              executed.set(true);
            },
            1,
            TimeUnit.MILLISECONDS);
    f.get();
    Assert.assertTrue(executed.get());

    executorService.shutdownNow();
  }

  @Test
  public void testStartStripedExecutorService()
      throws InterruptedException, ExecutionException, TimeoutException {
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

    executorService.shutdown();
  }
}
