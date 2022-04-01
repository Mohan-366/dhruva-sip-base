package com.cisco.dsb.common.service;

import static org.mockito.Mockito.*;

import com.cisco.dsb.common.executor.DhruvaExecutorService;
import com.cisco.dsb.common.metric.Metric;
import com.cisco.dsb.common.metric.MetricClient;
import com.cisco.wx2.dto.health.ServiceHealth;
import com.cisco.wx2.metrics.InfluxPoint;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicIntegerArray;
import org.mockito.*;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.test.util.ReflectionTestUtils;
import org.testng.Assert;
import org.testng.annotations.BeforeTest;
import org.testng.annotations.Test;

@Test
public class MetricServiceV2Test {
  private ArgumentCaptor<Metric> metricArgumentCaptor;

  @InjectMocks MetricService metricService;

  @Mock DhruvaExecutorService dhruvaExecutorServiceMock;

  @Mock ScheduledThreadPoolExecutor scheduledExecutorMock;

  @Mock MetricClient metricClientMock;

  @Mock Metric metricMock;

  @Mock ServiceHealth serviceHealthMock;

  @Qualifier("asyncMetricsExecutor")
  Executor executorService;

  @BeforeTest
  public void beforeTest() {
    MockitoAnnotations.initMocks(this);
    ReflectionTestUtils.setField(
        metricService, "scheduledExecutor", new ScheduledThreadPoolExecutor(1));
    ReflectionTestUtils.setField(metricService, "executorService", Executors.newFixedThreadPool(1));

    metricArgumentCaptor = ArgumentCaptor.forClass(Metric.class);
  }

  @Test(
      description =
          "Test to check no. of times cps metrics emitted when interval = 100ms & total time frame = 1000ms thus, no of window > 1 & < 1000/100 ")
  public void testEmitCPSMetricPerInterval() throws InterruptedException {
    Map<String, AtomicInteger> cpsCounterMapTest = new HashMap<>();
    cpsCounterMapTest.put("DialInPSTNTest", new AtomicInteger(5));
    cpsCounterMapTest.put("DialInB2BTest", new AtomicInteger(2));
    metricService.setCpsCounterMap(cpsCounterMapTest);

    metricService.emitCPSMetricPerInterval(10, TimeUnit.MILLISECONDS);
    Thread.sleep(100L);

    ArgumentCaptor<Set<Metric>> metricSetCaptor = ArgumentCaptor.forClass(Set.class);
    verify(metricClientMock, atLeast(9)).sendMetrics(metricSetCaptor.capture());

    List<Set<Metric>> capturedMetricSet = metricSetCaptor.getAllValues();

    Assert.assertTrue(capturedMetricSet.size() > 9);
  }

  @Test(description = "Test to check supplier emitting metric set with cps counter information")
  public void testCpsMetricSupplier() {
    Map<String, AtomicInteger> cpsCounterMapTest = new HashMap<>();
    cpsCounterMapTest.put("DialInPSTNTest", new AtomicInteger(5));
    cpsCounterMapTest.put("DialInB2BTest", new AtomicInteger(2));

    metricService.setCpsCounterMap(cpsCounterMapTest);

    Set<Metric> cpsSupplierOp = metricService.cpsMetricSupplier().get();
    Assert.assertEquals(cpsSupplierOp.size(), 2);

    cpsSupplierOp.forEach(
        eachMetric -> {
          Assert.assertTrue(((InfluxPoint) eachMetric.get()).getTags().containsKey("callType"));
          Assert.assertTrue(((InfluxPoint) eachMetric.get()).getFields().containsKey("count"));
        });
  }

  @Test(
      description =
          "Test to check supplier emitting metric set with cps counter information, when counter value can be zero")
  public void testCpsMetricSupplierWhenCounterZero() {
    Map<String, AtomicInteger> cpsCounterMapTest = new HashMap<>();
    cpsCounterMapTest.put("DialInPSTNTest", new AtomicInteger(5));
    cpsCounterMapTest.put("DialInB2BTest", new AtomicInteger(0));

    metricService.setCpsCounterMap(cpsCounterMapTest);

    Set<Metric> cpsSupplierOp = metricService.cpsMetricSupplier().get();
    /* supplier will not consider creating a metric point for "DialInB2BTest" as the count is zero */
    Assert.assertEquals(cpsSupplierOp.size(), 1);

    cpsSupplierOp.forEach(
        eachMetric -> {
          Assert.assertTrue(((InfluxPoint) eachMetric.get()).getTags().containsKey("callType"));
          Assert.assertTrue(((InfluxPoint) eachMetric.get()).getFields().containsKey("count"));
        });
  }

  @Test(
      description =
          "Test to check supplier emitting metric set with cps counter information, when counter is empty")
  public void testCpsMetricSupplierWhenCounterEmpty() {
    Map<String, AtomicInteger> cpsCounterMapTest = new HashMap<>();

    metricService.setCpsCounterMap(cpsCounterMapTest);

    Set<Metric> cpsSupplierOp = metricService.cpsMetricSupplier().get();
    /* supplier will not consider creating any metric point as the counter is empty */
    Assert.assertEquals(cpsSupplierOp.size(), 0);
  }

  @Test(description = "tests to cover getters and setters of CPS counter map")
  public void testGetCpsCounterMap() {
    Map<String, AtomicInteger> cpsCounterMap = metricService.getCpsCounterMap();
    Assert.assertNotNull(cpsCounterMap);

    metricService.setCpsCounterMap(null);
    Assert.assertNull(metricService.getCpsCounterMap());
  }

  @Test(
      description =
          "Test to check supplier emitting metric set with trunk cps counter information, when counter is empty")
  public void testTrunkCpsMetricSupplierWhenCounterEmpty() {
    Map<String, AtomicIntegerArray> trunkcpsCounterMapTest = new HashMap<>();

    metricService.setCpsTrunkCounterMap(trunkcpsCounterMapTest);

    Set<Metric> cpsSupplierOp = metricService.cpsTrunkMetricSupplier("trunkcps").get();
    Assert.assertEquals(cpsSupplierOp.size(), 0);
  }

  @Test(description = "tests to cover getters and setters of trunk CPS counter map")
  public void testGetTrunkCpsCounterMap() {
    Map<String, AtomicIntegerArray> cpsTrunkCounterMap = metricService.getCpsTrunkCounterMap();
    Assert.assertNotNull(cpsTrunkCounterMap);

    metricService.setCpsTrunkCounterMap(null);
    Assert.assertNull(metricService.getCpsTrunkCounterMap());
  }

  @Test(
      description =
          "Test to check in case of map is null, even though its initialized at bean creation",
      expectedExceptions = NullPointerException.class)
  public void testGetCpsCounterMapWhenNull() {
    metricService.setCpsCounterMap(null);
    metricService.getCpsCounterMap().get("trigger for null pointer exception");
  }

  @Test(
      description = "Test to check supplier emitting metric set with trunk cps counter information")
  public void testTrunkCpsMetricSupplier() {
    Map<String, AtomicIntegerArray> trunkcpsCounterMapTest = new HashMap<>();
    AtomicIntegerArray atomicIntegerAnatres = new AtomicIntegerArray(2);
    AtomicIntegerArray atomicIntegerPSTN = new AtomicIntegerArray(2);

    atomicIntegerAnatres.set(0, 10);

    atomicIntegerPSTN.set(0, 10);
    atomicIntegerPSTN.set(1, 20);

    trunkcpsCounterMapTest.put("Antares", atomicIntegerAnatres);
    trunkcpsCounterMapTest.put("PSTN", atomicIntegerPSTN);
    metricService.setCpsTrunkCounterMap(trunkcpsCounterMapTest);
    Set<Metric> cpsSupplierOp = metricService.cpsTrunkMetricSupplier("trunkcps").get();
    Assert.assertEquals(cpsSupplierOp.size(), 2);

    cpsSupplierOp.forEach(
        eachMetric -> {
          {
            Assert.assertTrue(((InfluxPoint) eachMetric.get()).getTags().containsKey("trunk"));
          }
          {
            if (((InfluxPoint) eachMetric.get()).getTags().get("trunk").equals("PSTN")) {
              Assert.assertTrue(
                  ((InfluxPoint) eachMetric.get()).getFields().containsKey("outboundCount"));
              Assert.assertEquals(
                  ((InfluxPoint) eachMetric.get()).getFields().get("outboundCount"), 20);

            } else {
              Assert.assertFalse(
                  ((InfluxPoint) eachMetric.get()).getFields().containsKey("outboundCount"));
            }
          }
          {
            Assert.assertTrue(
                ((InfluxPoint) eachMetric.get()).getFields().containsKey("inboundCount"));
            Assert.assertEquals(
                ((InfluxPoint) eachMetric.get()).getFields().get("inboundCount"), 10);
          }
        });
  }
}
