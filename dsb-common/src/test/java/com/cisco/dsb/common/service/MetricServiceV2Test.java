package com.cisco.dsb.common.service;

import static org.mockito.Mockito.*;
import static org.testng.Assert.assertEquals;

import com.cisco.dsb.common.dto.RateLimitInfo;
import com.cisco.dsb.common.dto.RateLimitInfo.Action;
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
import java.util.function.Supplier;
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
    Thread.sleep(500L);

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
    assertEquals(cpsSupplierOp.size(), 2);

    cpsSupplierOp.forEach(
        eachMetric -> {
          Assert.assertTrue(((InfluxPoint) eachMetric.get()).getTags().containsKey("callType"));
          Assert.assertTrue(((InfluxPoint) eachMetric.get()).getFields().containsKey("count"));
        });
  }

  @Test
  public void testRateLimitInfoMetricSupplier() {
    RateLimitInfo rateLimitInfo1 =
        RateLimitInfo.builder()
            .remoteIP("1.1.1.1")
            .isRequest(true)
            .policyName("policy1")
            .localIP("2.2.2.2")
            .action(Action.DENY)
            .build();
    RateLimitInfo rateLimitInfo2 =
        RateLimitInfo.builder()
            .remoteIP("3.3.3.3")
            .isRequest(true)
            .policyName("policy2")
            .localIP("4.4.4.4")
            .action(Action.RATE_LIMIT)
            .build();

    metricService.updateRateLimiterInfo(rateLimitInfo1);
    metricService.updateRateLimiterInfo(rateLimitInfo1);
    metricService.updateRateLimiterInfo(rateLimitInfo1);
    metricService.updateRateLimiterInfo(rateLimitInfo1);
    metricService.updateRateLimiterInfo(rateLimitInfo1);
    metricService.updateRateLimiterInfo(rateLimitInfo1);
    metricService.updateRateLimiterInfo(rateLimitInfo1);
    metricService.updateRateLimiterInfo(rateLimitInfo1);
    metricService.updateRateLimiterInfo(rateLimitInfo2);
    metricService.updateRateLimiterInfo(rateLimitInfo2);
    metricService.updateRateLimiterInfo(rateLimitInfo2);
    metricService.updateRateLimiterInfo(rateLimitInfo2);
    metricService.updateRateLimiterInfo(rateLimitInfo2);
    metricService.updateRateLimiterInfo(rateLimitInfo2);
    metricService.updateRateLimiterInfo(rateLimitInfo2);
    metricService.updateRateLimiterInfo(rateLimitInfo2);
    metricService.updateRateLimiterInfo(rateLimitInfo2);
    metricService.updateRateLimiterInfo(rateLimitInfo2);

    assertEquals(metricService.getRateLimiterMap().get(rateLimitInfo1), 8);
    assertEquals(metricService.getRateLimiterMap().get(rateLimitInfo2), 10);
    Supplier<Set<Metric>> supplierRateLimiter =
        metricService.rateLimitInfoMetricSupplier("ratelimiter");
    Set<Metric> metrics = supplierRateLimiter.get();
    assertEquals(metrics.size(), 2);
    AtomicInteger atomicInteger = new AtomicInteger(0);
    metrics.forEach(
        metric -> {
          if (((InfluxPoint) metric.get()).getTags().get("remoteIP").equals("1.1.1.1")) {
            assertEquals(((InfluxPoint) metric.get()).getTags().get("localIP"), "2.2.2.2");
            assertEquals(((InfluxPoint) metric.get()).getTags().get("policyName"), "policy1");
            assertEquals(((InfluxPoint) metric.get()).getTags().get("action"), "DENY");
            assertEquals(((InfluxPoint) metric.get()).getTags().get("isRequest"), "true");
            assertEquals(((InfluxPoint) metric.get()).getField("count"), 8);
            atomicInteger.getAndIncrement();
          } else {
            assertEquals(((InfluxPoint) metric.get()).getTags().get("remoteIP"), "3.3.3.3");
            assertEquals(((InfluxPoint) metric.get()).getTags().get("localIP"), "4.4.4.4");
            assertEquals(((InfluxPoint) metric.get()).getTags().get("policyName"), "policy2");
            assertEquals(((InfluxPoint) metric.get()).getTags().get("action"), "RATE_LIMIT");
            assertEquals(((InfluxPoint) metric.get()).getTags().get("isRequest"), "true");
            assertEquals(((InfluxPoint) metric.get()).getField("count"), 10);
            atomicInteger.getAndIncrement();
          }
        });
    assertEquals(atomicInteger.getPlain(), 2); // to ensure both the metrics were validated.
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
    assertEquals(cpsSupplierOp.size(), 1);

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
    assertEquals(cpsSupplierOp.size(), 0);
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
    assertEquals(cpsSupplierOp.size(), 0);
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
    assertEquals(cpsSupplierOp.size(), 2);

    cpsSupplierOp.forEach(
        eachMetric -> {
          {
            Assert.assertTrue(((InfluxPoint) eachMetric.get()).getTags().containsKey("trunk"));
          }
          {
            if (((InfluxPoint) eachMetric.get()).getTags().get("trunk").equals("PSTN")) {
              Assert.assertTrue(
                  ((InfluxPoint) eachMetric.get()).getFields().containsKey("outboundCount"));
              assertEquals(((InfluxPoint) eachMetric.get()).getFields().get("outboundCount"), 20);

            } else {
              Assert.assertFalse(
                  ((InfluxPoint) eachMetric.get()).getFields().containsKey("outboundCount"));
            }
          }
          {
            Assert.assertTrue(
                ((InfluxPoint) eachMetric.get()).getFields().containsKey("inboundCount"));
            assertEquals(((InfluxPoint) eachMetric.get()).getFields().get("inboundCount"), 10);
          }
        });
  }

  @Test(
      description = "Test to check supplier emitting metric set with trunk cps counter information")
  public void testTrunkLBMetricSupplier() {
    Map<String, String> trunkLBAlgoTest = new ConcurrentHashMap<>();

    ConcurrentHashMap<String, ConcurrentHashMap<String, Long>> trunkLBCounterMapTest =
        new ConcurrentHashMap<>();

    ConcurrentHashMap<String, Long> trunkAElements = new ConcurrentHashMap<>();
    trunkAElements.put("elementA", 5L);
    trunkAElements.put("elementB", 5L);
    trunkAElements.put("elementC", 1L);

    ConcurrentHashMap<String, Long> trunkBElements = new ConcurrentHashMap<>();
    trunkLBCounterMapTest.put("trunkA", trunkAElements);
    trunkLBCounterMapTest.put("trunkB", trunkBElements);
    trunkLBAlgoTest.put("trunkA", "WEIGHT");
    trunkLBAlgoTest.put("trunkB", "HIGHEST_Q");

    metricService.setTrunkLBMap(trunkLBCounterMapTest);
    metricService.setTrunkLBAlgorithm(trunkLBAlgoTest);
    Set<Metric> lbSupplierOp = metricService.sendLBDistribution("trunklbs").get();
    assertEquals(lbSupplierOp.size(), 3);

    lbSupplierOp.forEach(
        eachMetric -> {
          {
            Assert.assertTrue(((InfluxPoint) eachMetric.get()).getTags().containsKey("trunk"));
            Assert.assertTrue(((InfluxPoint) eachMetric.get()).getTags().containsValue("trunkA"));
            Assert.assertTrue(
                ((InfluxPoint) eachMetric.get()).getTags().containsKey("serverGroupElement"));
          }

          {
            Assert.assertTrue(((InfluxPoint) eachMetric.get()).getTags().containsKey("trunkAlgo"));
            Assert.assertTrue(((InfluxPoint) eachMetric.get()).getTags().containsValue("WEIGHT"));
          }
          {
            Assert.assertTrue(((InfluxPoint) eachMetric.get()).getFields().containsKey("lbcount"));
          }
          {
            if (((InfluxPoint) eachMetric.get())
                .getTags()
                .get("serverGroupElement")
                .equals("elementC")) {
              assertEquals(((InfluxPoint) eachMetric.get()).getFields().get("lbcount"), 1L);
            } else assertEquals(((InfluxPoint) eachMetric.get()).getFields().get("lbcount"), 5L);
          }
        });
  }
}
