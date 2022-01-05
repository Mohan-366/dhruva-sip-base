package com.cisco.dsb.common.service;

import static org.mockito.Mockito.*;

import com.cisco.dsb.common.executor.DhruvaExecutorService;
import com.cisco.dsb.common.executor.ExecutorType;
import com.cisco.dsb.common.metric.Metric;
import com.cisco.dsb.common.metric.MetricClient;
import com.cisco.dsb.common.metric.SipMetricsContext;
import com.cisco.dsb.common.transport.Connection;
import com.cisco.dsb.common.transport.Transport;
import com.cisco.dsb.common.util.log.event.Event;
import com.cisco.wx2.dto.health.ServiceHealth;
import com.cisco.wx2.dto.health.ServiceState;
import com.cisco.wx2.dto.health.ServiceType;
import com.cisco.wx2.metrics.InfluxPoint;
import com.google.common.cache.Cache;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import org.mockito.*;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.test.util.ReflectionTestUtils;
import org.testng.Assert;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

@Test(description = "Tests for metric service")
public class MetricServiceTest {

  private static final String method = "INVITE";
  private static String callId = "100-test-call-id";
  private static final String cseq = "TEST INVITE";
  private static final Event.MESSAGE_TYPE REQUEST_EVENT = Event.MESSAGE_TYPE.REQUEST;
  private static final Event.MESSAGE_TYPE RESPONSE_EVENT = Event.MESSAGE_TYPE.RESPONSE;
  private static final Transport TCP_TRANSPORT = Transport.TCP;
  private static final Event.DIRECTION DIRECTION_IN = Event.DIRECTION.IN;
  private static final Event.DIRECTION DIRECTION_OUT = Event.DIRECTION.OUT;

  private static final boolean INTERALLY_GENERATED_FALSE = false;
  private static final boolean INTERALLY_GENERATED_TRUE = true;

  private static final boolean IS_MID_CALL_FALSE = false;
  private static final String reqURI = "INVITE";

  private ArgumentCaptor<Metric> metricArgumentCaptor;

  @InjectMocks MetricService metricService;

  @Mock DhruvaExecutorService dhruvaExecutorServiceMock;

  @Mock MetricClient metricClientMock;

  @Mock Metric metricMock;

  @Mock ServiceHealth serviceHealthMock;

  @Qualifier("asyncMetricsExecutor")
  Executor executorService;

  @BeforeMethod
  public void beforeTest() {
    MockitoAnnotations.initMocks(this);
    when(dhruvaExecutorServiceMock.getExecutorThreadPool(ExecutorType.METRIC_SERVICE))
        .thenReturn(Executors.newSingleThreadExecutor());
    ReflectionTestUtils.setField(metricService, "executorService", Executors.newFixedThreadPool(1));
    // metricService.executorService = Executors.newFixedThreadPool(1);
    metricArgumentCaptor = ArgumentCaptor.forClass(Metric.class);
  }

  public void sendSipEventMetricRequestTest() {

    metricService.sendSipMessageMetric(
        method,
        callId,
        cseq,
        REQUEST_EVENT,
        TCP_TRANSPORT,
        DIRECTION_IN,
        IS_MID_CALL_FALSE,
        INTERALLY_GENERATED_FALSE,
        0L,
        reqURI);

    Mockito.verify(metricClientMock, atMost(2)).sendMetric(metricArgumentCaptor.capture());

    Metric capturedMetric = metricArgumentCaptor.getValue();
    Assert.assertNotNull(capturedMetric);

    InfluxPoint capturedMetricPoint = (InfluxPoint) capturedMetric.get();

    Assert.assertTrue(capturedMetricPoint.getTags().containsKey("method"));
    // add more asseert

    // scenario 2, dir-out , internally gen
    metricService.sendSipMessageMetric(
        method,
        callId,
        cseq,
        REQUEST_EVENT,
        TCP_TRANSPORT,
        DIRECTION_OUT,
        IS_MID_CALL_FALSE,
        INTERALLY_GENERATED_FALSE,
        0L,
        reqURI);

    Mockito.verify(metricClientMock, atMost(2)).sendMetric(metricArgumentCaptor.capture());

    capturedMetric = metricArgumentCaptor.getValue();
    Assert.assertNotNull(capturedMetric);

    capturedMetricPoint = (InfluxPoint) capturedMetric.get();

    Assert.assertTrue(capturedMetricPoint.getTags().containsKey("method"));
    // add more asseert

  }

  public void sendSipEventMetricResponseTest() {

    metricService.sendSipMessageMetric(
        "200",
        callId,
        cseq,
        RESPONSE_EVENT,
        TCP_TRANSPORT,
        DIRECTION_OUT,
        IS_MID_CALL_FALSE,
        INTERALLY_GENERATED_TRUE,
        0L,
        "SIP OK");

    Mockito.verify(metricClientMock).sendMetric(metricArgumentCaptor.capture());

    Metric capturedMetric = metricArgumentCaptor.getValue();
    Assert.assertNotNull(capturedMetric);

    InfluxPoint capturedMetricPoint = (InfluxPoint) capturedMetric.get();

    Assert.assertTrue(capturedMetricPoint.getTags().containsKey("method"));
  }

  public void sendConnectionMetricTest() {

    String testLocalIp = "127.0.0.1";
    int testLocalPort = 5060;
    String testRemoteIp = "127.0.0.2";
    int testRemotePort = 6060;
    Transport testTransport = Transport.TCP;
    Event.DIRECTION testDirection = Event.DIRECTION.IN;
    Connection.STATE testConnectionState = Connection.STATE.ACTIVE;

    // Positive scenario
    metricService.sendConnectionMetric(
        testLocalIp,
        testLocalPort,
        testRemoteIp,
        testRemotePort,
        testTransport,
        testDirection,
        testConnectionState);
    verify(metricClientMock, atMost(2)).sendMetric(metricArgumentCaptor.capture());

    Metric capturedMetric = metricArgumentCaptor.getValue();
    Assert.assertNotNull(capturedMetric);

    InfluxPoint capturedMetricPoint = (InfluxPoint) capturedMetric.get();

    Assert.assertEquals(capturedMetricPoint.getMeasurement(), "dhruva.connection");

    Assert.assertTrue(capturedMetricPoint.getTags().containsKey("transport"));
    Assert.assertTrue(capturedMetricPoint.getTags().containsKey("direction"));
    Assert.assertTrue(capturedMetricPoint.getTags().containsKey("connectionState"));
    Assert.assertTrue(capturedMetricPoint.getFields().containsKey("localIp"));
    Assert.assertTrue(capturedMetricPoint.getFields().containsKey("localPort"));
    Assert.assertTrue(capturedMetricPoint.getFields().containsKey("remoteIp"));
    Assert.assertTrue(capturedMetricPoint.getFields().containsKey("remotePort"));

    // negative scenario
    metricService.sendConnectionMetric(
        testLocalIp, testLocalPort, testRemoteIp, testRemotePort, null, null, null);
    verify(metricClientMock, atMost(2)).sendMetric(metricArgumentCaptor.capture());

    capturedMetric = metricArgumentCaptor.getValue();
    Assert.assertNotNull(capturedMetric);

    capturedMetricPoint = (InfluxPoint) capturedMetric.get();

    Assert.assertEquals(capturedMetricPoint.getMeasurement(), "dhruva.connection");

    Assert.assertFalse(capturedMetricPoint.getTags().containsKey("transport"));
    Assert.assertFalse(capturedMetricPoint.getTags().containsKey("direction"));
    Assert.assertFalse(capturedMetricPoint.getTags().containsKey("connectionState"));
  }

  public void sendDnsMetricTest() {

    String testQuery = "testQueryToCheckMetric";
    String queryType = "testQueryTypeForTest";
    long totalDurationInMillis = 0L;
    String errorMsg = null;

    metricService.sendDNSMetric(testQuery, queryType, totalDurationInMillis, errorMsg);

    ArgumentCaptor<Metric> metricArgCaptor = ArgumentCaptor.forClass(Metric.class);
    Mockito.verify(metricClientMock).sendMetric(metricArgCaptor.capture());

    Metric capturedMetric = metricArgCaptor.getValue();
    Assert.assertNotNull(capturedMetric);

    InfluxPoint capturedMetricPoint = (InfluxPoint) capturedMetric.get();

    // if tag is false, it will not be present in the final metrics, here this tag does have null
    // value
    Assert.assertFalse(capturedMetricPoint.getTags().containsKey("failureReason"));
  }

  public void latencyMetricExceptionTest() {

    // Callid set to null , context state is not defined
    SipMetricsContext metricsContext = new SipMetricsContext(metricService, null, null, true);

    Assert.assertEquals(metricsContext.getCallId(), "");
    Assert.assertNull(metricsContext.state);
    Assert.assertTrue(metricsContext.isSuccessful());

    // emitMetric set to false
    metricsContext = new SipMetricsContext(metricService, null, null, false);

    Assert.assertEquals(metricsContext.getCallId(), "");
    Assert.assertNull(metricsContext.state);
    Assert.assertFalse(metricsContext.isSuccessful());
  }

  public void latencyMetricTest() throws InterruptedException {


    SipMetricsContext contextAtStart =
        new SipMetricsContext(
            metricService, SipMetricsContext.State.latencyIncomingNewRequestStart, callId, true);

    Thread.sleep(100);

    SipMetricsContext contextAtEnd =
        new SipMetricsContext(
            metricService, SipMetricsContext.State.latencyIncomingNewRequestEnd, callId, true);

    // add assert checks for the case
    Mockito.verify(metricClientMock,times(1)).sendMetric(metricArgumentCaptor.capture());

    Metric capturedMetric = metricArgumentCaptor.getValue();

    Assert.assertNotNull(capturedMetric);

  }

  public void createMetricsForLatencyNegativeTest() {

    // Metric will be created without any tags and fields as count, duration nothing is evaluated
    metricService.update(
        "test.latency", 0L, 0L, TimeUnit.MILLISECONDS, 0L, TimeUnit.MILLISECONDS, true);

    verify(metricClientMock, atMost(10)).sendMetric(metricArgumentCaptor.capture());

    Metric capturedMetric = metricArgumentCaptor.getValue();
    Assert.assertNotNull(capturedMetric);

    InfluxPoint capturedMetricPoint = (InfluxPoint) capturedMetric.get();
    Assert.assertEquals(capturedMetricPoint.getTags().size(), 0);
    Assert.assertEquals(capturedMetricPoint.getFields().size(), 0);

    // Metric will be created without any tags and fields as count, duration nothing is evaluated
    metricService.update("test.latency", 0L, 1L, null, 1L, null, null);

    verify(metricClientMock, atMost(10)).sendMetric(metricArgumentCaptor.capture());

    capturedMetric = metricArgumentCaptor.getValue();
    Assert.assertNotNull(capturedMetric);

    capturedMetricPoint = (InfluxPoint) capturedMetric.get();
    Assert.assertEquals(capturedMetricPoint.getTags().size(), 0);
    Assert.assertEquals(capturedMetricPoint.getFields().size(), 0);
  }

  public void createMetricsForLatencyPositiveTest() {
    // Positive path
    metricService.update(
        "test.latency", 1L, 3L, TimeUnit.MILLISECONDS, 5L, TimeUnit.MILLISECONDS, true);

    verify(metricClientMock, atMost(10)).sendMetric(metricArgumentCaptor.capture());

    Metric capturedMetric = metricArgumentCaptor.getValue();
    Assert.assertNotNull(capturedMetric);

    InfluxPoint capturedMetricPoint = (InfluxPoint) capturedMetric.get();
    Assert.assertNotEquals(capturedMetricPoint.getFields().size(), 0);
  }

  public void startTimerTest() {

    String testCallId = null;
    String testMetricName = null;

    // negative case, null values
    metricService.getTimers().invalidateAll();
    metricService.startTimer(testCallId, testMetricName);
    Cache<String, Long> timers = metricService.getTimers();
    Assert.assertEquals(timers.size(), 0);

    // negative scenario 2
    testMetricName = "test.metrics";

    metricService.getTimers().invalidateAll();

    metricService.startTimer(testCallId, testMetricName);
    timers = metricService.getTimers();
    String key = MetricService.joiner.join(testCallId, testMetricName);
    Assert.assertNull(timers.getIfPresent(key));

    // negative scenario 3
    testCallId = "30434-test";
    testMetricName = null;

    metricService.getTimers().invalidateAll();

    metricService.startTimer(testCallId, testMetricName);
    timers = metricService.getTimers();
    key = MetricService.joiner.join(testCallId, testMetricName);
    Assert.assertNull(timers.getIfPresent(key));

    // positive case
    testCallId = "test-100";
    testMetricName = "test.latency";

    metricService.getTimers().invalidateAll();

    metricService.startTimer(testCallId, testMetricName);
    timers = metricService.getTimers();
    key = MetricService.joiner.join(testCallId, testMetricName);
    Assert.assertNotNull(timers.getIfPresent(key));
  }

  public void emitServiceMetricTest() {

    when(serviceHealthMock.isOnline()).thenReturn(true);
    when(serviceHealthMock.getServiceState()).thenReturn(ServiceState.ONLINE);

    List<ServiceHealth> upstreamServiceList = new ArrayList<>();

    upstreamServiceList.add(
        ServiceHealth.builder()
            .serviceName("testUpstreamSrv1")
            .serviceType(ServiceType.REQUIRED)
            .serviceState(ServiceState.ONLINE)
            .message("online")
            .build());

    upstreamServiceList.add(
        ServiceHealth.builder()
            .serviceName("testUpstreamSrv2")
            .serviceType(ServiceType.REQUIRED)
            .serviceState(ServiceState.ONLINE)
            .message("online")
            .build());

    upstreamServiceList.add(
        ServiceHealth.builder()
            .serviceName("testUpstreamSrv3")
            .serviceType(ServiceType.REQUIRED)
            .serviceState(ServiceState.OFFLINE)
            .message("offline")
            .build());

    when(serviceHealthMock.getUpstreamServices()).thenReturn(upstreamServiceList);

    metricService.emitServiceHealth(serviceHealthMock, true);

    verify(metricClientMock, atMost(4)).sendMetric(metricArgumentCaptor.capture());

    List<Metric> capturedMetric = metricArgumentCaptor.getAllValues();

    for (int i = 0; i < 4; i++) {

      Assert.assertNotNull(capturedMetric.get(i));
      InfluxPoint capturedMetricPoint = (InfluxPoint) capturedMetric.get(i).get();

      if ("dhruva.service.health".equals(capturedMetricPoint.getMeasurement())) {
        Assert.assertEquals(capturedMetricPoint.getTag("state"), ServiceState.ONLINE.toString());
        Assert.assertEquals(capturedMetricPoint.getField("availability"), 100.0);
      }

      if (capturedMetricPoint.getTags().containsValue("testUpstreamSrv1")) {
        Assert.assertEquals(capturedMetricPoint.getMeasurement(), "dhruva.service.upstream.health");
        Assert.assertEquals(capturedMetricPoint.getTag("state"), ServiceState.ONLINE.toString());
        Assert.assertEquals(capturedMetricPoint.getField("availability"), 100.0);
      }

      if (capturedMetricPoint.getTags().containsValue("testUpstreamSrv2")) {
        Assert.assertEquals(capturedMetricPoint.getMeasurement(), "dhruva.service.upstream.health");
        Assert.assertEquals(capturedMetricPoint.getTag("state"), ServiceState.ONLINE.toString());
        Assert.assertEquals(capturedMetricPoint.getField("availability"), 100.0);
      }

      if (capturedMetricPoint.getTags().containsValue("testUpstreamSrv3")) {
        Assert.assertEquals(capturedMetricPoint.getMeasurement(), "dhruva.service.upstream.health");
        Assert.assertEquals(capturedMetricPoint.getTag("state"), ServiceState.OFFLINE.toString());
        Assert.assertEquals(capturedMetricPoint.getField("availability"), 0.0);
      }
    }
  }

  public void emitServiceHealthMetricWithoutUpstreamService() {

    when(serviceHealthMock.isOnline()).thenReturn(true);
    when(serviceHealthMock.getServiceState()).thenReturn(ServiceState.ONLINE);

    List<ServiceHealth> upstreamServiceList = null;

    metricService.emitServiceHealth(serviceHealthMock, true);

    verify(metricClientMock, atMost(1)).sendMetric(metricArgumentCaptor.capture());

    List<Metric> capturedMetric = metricArgumentCaptor.getAllValues();

    Assert.assertEquals(capturedMetric.size(), 1);
    Assert.assertNotNull(capturedMetric);
    InfluxPoint capturedMetricPoint = (InfluxPoint) capturedMetric.get(0).get();

    if ("dhruva.service.health".equals(capturedMetricPoint.getMeasurement())) {
      Assert.assertEquals(capturedMetricPoint.getTag("state"), ServiceState.ONLINE.toString());
      Assert.assertEquals(capturedMetricPoint.getField("availability"), 100.0);
    }
  }

  public void emitServiceHealthMetricUpstreamFlagDisabled() {

    when(serviceHealthMock.isOnline()).thenReturn(false);
    when(serviceHealthMock.getServiceState()).thenReturn(ServiceState.OFFLINE);

    List<ServiceHealth> upstreamServiceList = new ArrayList<>();

    upstreamServiceList.add(
        ServiceHealth.builder()
            .serviceName("testUpstreamSrv1")
            .serviceType(ServiceType.REQUIRED)
            .serviceState(ServiceState.ONLINE)
            .message("online")
            .build());

    when(serviceHealthMock.getUpstreamServices()).thenReturn(upstreamServiceList);

    // flag disabled to include upstream service health, metrics will not be emitted for that
    metricService.emitServiceHealth(serviceHealthMock, false);
    verify(metricClientMock, atMost(1)).sendMetric(metricArgumentCaptor.capture());

    List<Metric> capturedMetric = metricArgumentCaptor.getAllValues();

    Assert.assertEquals(capturedMetric.size(), 1);
    Assert.assertNotNull(capturedMetric);
    InfluxPoint capturedMetricPoint = (InfluxPoint) capturedMetric.get(0).get();

    if ("dhruva.service.health".equals(capturedMetricPoint.getMeasurement())) {
      Assert.assertEquals(capturedMetricPoint.getTag("state"), ServiceState.OFFLINE.toString());
      Assert.assertEquals(capturedMetricPoint.getField("availability"), 0.0);
    }
  }
}
