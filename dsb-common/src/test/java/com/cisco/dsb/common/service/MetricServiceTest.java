package com.cisco.dsb.common.service;

import static com.cisco.dsb.common.service.MetricService.joiner;
import static org.awaitility.Awaitility.with;
import static org.mockito.Mockito.*;

import com.cisco.dsb.common.executor.DhruvaExecutorService;
import com.cisco.dsb.common.executor.ExecutorType;
import com.cisco.dsb.common.metric.Metric;
import com.cisco.dsb.common.metric.MetricClient;
import com.cisco.dsb.common.metric.SipMetricsContext;
import com.cisco.dsb.common.sip.jain.channelCache.ConnectionMetricRunnable;
import com.cisco.dsb.common.sip.jain.channelCache.DsbSipTCPMessageProcessor;
import com.cisco.dsb.common.transport.Connection;
import com.cisco.dsb.common.transport.Transport;
import com.cisco.dsb.common.util.log.event.Event;
import com.cisco.wx2.dto.health.ServiceHealth;
import com.cisco.wx2.dto.health.ServiceState;
import com.cisco.wx2.dto.health.ServiceType;
import com.cisco.wx2.metrics.InfluxPoint;
import com.google.common.cache.Cache;
import gov.nist.core.Host;
import gov.nist.core.HostPort;
import gov.nist.javax.sip.stack.ConnectionOrientedMessageChannel;
import java.util.*;
import java.util.concurrent.*;
import org.apache.commons.lang3.time.StopWatch;
import org.mockito.*;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.test.util.ReflectionTestUtils;
import org.testng.Assert;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.DataProvider;
import org.testng.annotations.Test;

@Test(description = "Tests for metric service")
public class MetricServiceTest {

  private static final String method = "INVITE";
  private static String callId = "1-test@192.168.0.1";
  private static final String cseq = "TEST INVITE";
  private static final Event.MESSAGE_TYPE REQUEST_EVENT = Event.MESSAGE_TYPE.REQUEST;
  private static final Event.MESSAGE_TYPE RESPONSE_EVENT = Event.MESSAGE_TYPE.RESPONSE;
  private static final Transport TCP_TRANSPORT = Transport.TCP;
  private static final Event.DIRECTION DIRECTION_IN = Event.DIRECTION.IN;
  private static final Event.DIRECTION DIRECTION_OUT = Event.DIRECTION.OUT;

  private static final boolean INTERALLY_GENERATED_FALSE = false;
  private static final boolean INTERALLY_GENERATED_TRUE = true;

  private static final boolean IS_MID_CALL_FALSE = false;
  private static final String reqURI = "sip:test@test.abc.com";
  private static final String CALLTYPE_TEST = "TEST_CALLTYPE";

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
    when(dhruvaExecutorServiceMock.getScheduledExecutorThreadPool(ExecutorType.METRIC_SERVICE))
        .thenReturn(new ScheduledThreadPoolExecutor(1));
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
        reqURI,
        CALLTYPE_TEST,
        null);

    Mockito.verify(metricClientMock, atMost(1)).sendMetric(metricArgumentCaptor.capture());

    Metric capturedMetric = metricArgumentCaptor.getValue();
    Assert.assertNotNull(capturedMetric);

    InfluxPoint capturedMetricPoint = (InfluxPoint) capturedMetric.get();

    Map<String, String> capturedTags = capturedMetricPoint.getTags();
    Map<String, Object> capturedFields = capturedMetricPoint.getFields();

    Assert.assertTrue(capturedTags.containsKey("method"));
    Assert.assertTrue(capturedTags.containsKey("messageType"));
    Assert.assertTrue(capturedTags.containsKey("direction"));
    Assert.assertTrue(capturedTags.containsKey("isMidCall"));
    Assert.assertTrue(capturedTags.containsKey("isInternallyGenerated"));
    Assert.assertTrue(capturedFields.containsKey("callId"));
    Assert.assertTrue(capturedFields.containsKey("cSeq"));
    Assert.assertTrue(capturedFields.containsKey("requestUri"));
    Assert.assertFalse(capturedFields.containsKey("processingDelayInMillis"));
    Assert.assertTrue(capturedTags.containsKey("callType"));
    Assert.assertTrue(capturedTags.containsValue(CALLTYPE_TEST));

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
        reqURI,
        null, // for sipRequests calltype can be null
        null);

    Mockito.verify(metricClientMock, atMost(2)).sendMetric(metricArgumentCaptor.capture());

    capturedMetric = metricArgumentCaptor.getValue();
    Assert.assertNotNull(capturedMetric);

    capturedMetricPoint = (InfluxPoint) capturedMetric.get();

    capturedTags = capturedMetricPoint.getTags();
    capturedFields = capturedMetricPoint.getFields();

    Assert.assertTrue(capturedTags.containsKey("method"));
    Assert.assertTrue(capturedTags.containsKey("messageType"));
    Assert.assertTrue(capturedTags.containsKey("direction"));
    Assert.assertTrue(capturedTags.containsKey("isMidCall"));
    Assert.assertTrue(capturedTags.containsKey("isInternallyGenerated"));
    Assert.assertTrue(capturedFields.containsKey("callId"));
    Assert.assertTrue(capturedFields.containsKey("cSeq"));
    Assert.assertTrue(capturedFields.containsKey("requestUri"));
    Assert.assertFalse(
        capturedTags.containsKey(
            "callType")); // calltype tag value will be null so no tag will be emitted in form of
    // influx point/ metric point
    Assert.assertTrue(capturedFields.containsKey("processingDelayInMillis"));
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
        "200 OK",
        CALLTYPE_TEST,
        "Some additional details");

    Mockito.verify(metricClientMock).sendMetric(metricArgumentCaptor.capture());

    Metric capturedMetric = metricArgumentCaptor.getValue();
    Assert.assertNotNull(capturedMetric);

    InfluxPoint capturedMetricPoint = (InfluxPoint) capturedMetric.get();

    Map<String, String> capturedTags = capturedMetricPoint.getTags();
    Map<String, Object> capturedFields = capturedMetricPoint.getFields();

    Assert.assertTrue(capturedTags.containsKey("method"));
    Assert.assertTrue(capturedTags.containsKey("messageType"));
    Assert.assertTrue(capturedTags.containsKey("direction"));
    Assert.assertTrue(capturedTags.containsKey("isMidCall"));
    Assert.assertTrue(capturedTags.containsKey("isInternallyGenerated"));
    Assert.assertTrue(capturedFields.containsKey("callId"));
    Assert.assertTrue(capturedFields.containsKey("cSeq"));
    Assert.assertTrue(capturedFields.containsKey("responseCode"));
    Assert.assertTrue(capturedFields.containsKey("responseReason"));
    Assert.assertTrue(capturedFields.containsKey("additionalDetails"));
    Assert.assertFalse(capturedFields.containsKey("processingDelayInMillis"));
    Assert.assertTrue(capturedTags.containsKey("callType"));
    Assert.assertTrue(capturedTags.containsValue(CALLTYPE_TEST));
  }

  @Test(description = "Test to verify emitted connection metrics")
  public void connectionMetricTest1() {

    // scenario -> if channel is null then no metrics will be emitted
    metricService.emitConnectionMetrics(
        DIRECTION_IN.toString(), null, Connection.STATE.CONNECTED.toString());
    verify(metricClientMock, times(0)).sendMetric(any());
  }

  @Test(description = "Test for connectionmetric for tcp/tls transport per interval ")
  public void connectionOrientedTransportMetricTestPerInterval() throws InterruptedException {

    DsbSipTCPMessageProcessor mockedTcpMessageProcessor = mock(DsbSipTCPMessageProcessor.class);

    ConnectionMetricRunnable connectionMetricRunnableForTest =
        new ConnectionMetricRunnable(
            mockedTcpMessageProcessor, metricService, dhruvaExecutorServiceMock);
    ConnectionOrientedMessageChannel mockedChannel1 = mock(ConnectionOrientedMessageChannel.class);

    when(mockedChannel1.getHost()).thenReturn("120.0.0.1");
    when(mockedChannel1.getPort()).thenReturn(5060);
    when(mockedChannel1.getPeerAddress()).thenReturn("127.0.0.1");
    when(mockedChannel1.getPeerPort()).thenReturn(5070);

    HostPort hostport1 = new HostPort();
    hostport1.setHost(new Host("127.0.0.1"));
    hostport1.setPort(5060);
    when(mockedChannel1.getHostPort()).thenReturn(hostport1);

    HostPort peerAddress1 = new HostPort();
    peerAddress1.setHost(new Host("127.0.0.1"));
    peerAddress1.setPort(5070);
    when(mockedChannel1.getPeerHostPort()).thenReturn(peerAddress1);
    when(mockedChannel1.getTransport()).thenReturn("TCP");
    when(mockedChannel1.getPeerProtocol()).thenReturn("TCP");

    ConnectionOrientedMessageChannel mockedChannel2 = mock(ConnectionOrientedMessageChannel.class);

    when(mockedChannel2.getHost()).thenReturn("120.0.0.1");
    when(mockedChannel2.getPort()).thenReturn(5070);
    when(mockedChannel2.getPeerAddress()).thenReturn("127.0.0.1");
    when(mockedChannel2.getPeerPort()).thenReturn(7060);

    HostPort hostport2 = new HostPort();
    hostport2.setHost(new Host("127.0.0.1"));
    hostport2.setPort(5070);
    when(mockedChannel1.getHostPort()).thenReturn(hostport2);

    HostPort peerAddress2 = new HostPort();
    peerAddress2.setHost(new Host("127.0.0.1"));
    peerAddress2.setPort(7060);
    when(mockedChannel1.getPeerHostPort()).thenReturn(peerAddress2);
    when(mockedChannel1.getTransport()).thenReturn("TCP");
    when(mockedChannel1.getPeerProtocol()).thenReturn("TCP");

    // create 2 channels one for out one for in
    when(mockedTcpMessageProcessor.getIncomingMessageChannels())
        .thenReturn(Collections.singletonList(mockedChannel1));
    when(mockedTcpMessageProcessor.getOutgoingMessageChannels())
        .thenReturn(Collections.singletonList(mockedChannel2));

    // Start runnable to emit connection metric with the inbound and outbound channel
    connectionMetricRunnableForTest.setInitialDelay(10L);
    connectionMetricRunnableForTest.setDelay(10L);
    connectionMetricRunnableForTest.start();
    Thread.sleep(100L);
    connectionMetricRunnableForTest.stop();

    // this will be more than 1 in the interval
    verify(metricClientMock, atLeast(2)).sendMetric(metricArgumentCaptor.capture());

    List<Metric> capturedMetric = metricArgumentCaptor.getAllValues();
    Assert.assertNotNull(capturedMetric);
    Assert.assertTrue(capturedMetric.size() >= 2);

    for (Metric eachMetric : capturedMetric) {
      InfluxPoint capturedMetricPoint = (InfluxPoint) eachMetric.get();
      Assert.assertEquals(capturedMetricPoint.getMeasurement(), "dhruva.connection");
      // add more assert to check the metrics

      Map<String, String> capturedTags = capturedMetricPoint.getTags();
      Map<String, Object> capturedFields = capturedMetricPoint.getFields();

      Assert.assertTrue(capturedTags.containsKey("transport"));
      Assert.assertTrue(capturedTags.containsKey("direction"));
      Assert.assertTrue(capturedTags.containsKey("connectionState"));
      Assert.assertTrue(capturedFields.containsKey("id"));
      Assert.assertTrue(capturedFields.containsKey("localAddress"));
      Assert.assertTrue(capturedFields.containsKey("localPort"));
      Assert.assertTrue(capturedFields.containsKey("remoteAddress"));
      Assert.assertTrue(capturedFields.containsKey("remotePort"));
    }
  }

  @Test(description = "To test tcp/tls channel info, when there are no active channels")
  public void connectionMetricPerIntervalTestWithoutChannel() throws InterruptedException {
    DsbSipTCPMessageProcessor mockedTcpMessageProcessor = mock(DsbSipTCPMessageProcessor.class);

    // When there is no cached channel returning empty list
    when(mockedTcpMessageProcessor.getIncomingMessageChannels())
        .thenReturn(Collections.emptyList());
    when(mockedTcpMessageProcessor.getOutgoingMessageChannels())
        .thenReturn(Collections.emptyList());

    // Start runnable to emit connection metric without inbound and outbound channel
    ConnectionMetricRunnable connectionMetricRunnable =
        new ConnectionMetricRunnable(
            mockedTcpMessageProcessor, metricService, dhruvaExecutorServiceMock);
    connectionMetricRunnable.setInitialDelay(10L);
    connectionMetricRunnable.setDelay(10L);
    connectionMetricRunnable.start();
    Thread.sleep(30L);
    connectionMetricRunnable.stop();

    // this will be 0 as there are no channels
    verify(metricClientMock, times(0)).sendMetric(metricArgumentCaptor.capture());

    List<Metric> capturedMetric = metricArgumentCaptor.getAllValues();
    Assert.assertTrue(capturedMetric == null || capturedMetric.isEmpty());
  }

  @DataProvider(name = "connectionFailureMetricData")
  private Object[][] connectionFailureMetricTestData() {
    return new Object[][] {
      {5060, "127.0.0.1", 54317, "127.0.0.1", "TCP", "Could not connect to /127.0.0.1:54317"},
      {5070, "127.0.0.1", 7060, "127.0.0.1", "TCP", "Could not connect to /127.0.0.1:7060"},
      {
        5060,
        "127.0.0.1",
        7060,
        "127.0.0.1",
        "TLS",
        "PKIX path building failed: sun.security.provider.certpath.SunCertPathBuilderException: unable to find valid certification path to requested target"
      },
    };
  }

  @Test(
      description = "Testing emitted connection failure metric",
      dataProvider = "connectionFailureMetricData")
  public void connectionMetricFailureTestWithData(
      int localPort,
      String localAddress,
      int remotePort,
      String remoteAddress,
      String transport,
      String errorMessage) {

    ConnectionOrientedMessageChannel mockedChannel = mock(ConnectionOrientedMessageChannel.class);

    when(mockedChannel.getHost()).thenReturn(localAddress);
    when(mockedChannel.getPort()).thenReturn(localPort);
    when(mockedChannel.getPeerAddress()).thenReturn(remoteAddress);
    when(mockedChannel.getPeerPort()).thenReturn(remotePort);
    when(mockedChannel.getTransport()).thenReturn(transport);
    when(mockedChannel.getPeerProtocol()).thenReturn(transport);

    metricService.emitConnectionErrorMetric(mockedChannel, false, errorMessage);
    verify(metricClientMock, times(1)).sendMetric(metricArgumentCaptor.capture());

    Metric capturedMetric = metricArgumentCaptor.getValue();
    Assert.assertNotNull(capturedMetric);

    InfluxPoint capturedMetricPoint = (InfluxPoint) capturedMetric.get();

    Assert.assertEquals(capturedMetricPoint.getMeasurement(), "dhruva.connection.failure");

    Map<String, String> capturedTags = capturedMetricPoint.getTags();
    Map<String, Object> capturedFields = capturedMetricPoint.getFields();

    Assert.assertTrue(capturedTags.containsKey("transport"));
    Assert.assertEquals(capturedTags.get("transport"), transport);

    Assert.assertTrue(capturedFields.containsKey("localAddress"));
    Assert.assertEquals(capturedFields.get("localAddress"), localAddress);
    Assert.assertTrue(capturedFields.containsKey("localPort"));
    Assert.assertEquals(capturedFields.get("localPort"), localPort);
    Assert.assertTrue(capturedFields.containsKey("remoteAddress"));
    Assert.assertEquals(capturedFields.get("remoteAddress"), remoteAddress);
    Assert.assertTrue(capturedFields.containsKey("remotePort"));
    Assert.assertEquals(capturedFields.get("remotePort"), remotePort);
    Assert.assertTrue(capturedFields.containsKey("errorMessage"));
    Assert.assertEquals(capturedFields.get("errorMessage"), errorMessage);
  }

  public void sendDnsMetricTest() {

    String testQuery = "testdomain.com";
    String queryType = "testQueryTypeARec";
    long totalDurationInMillis = 0L;
    String errorMsg = null;

    metricService.sendDNSMetric(testQuery, queryType, totalDurationInMillis, errorMsg);

    ArgumentCaptor<Metric> metricArgCaptor = ArgumentCaptor.forClass(Metric.class);
    Mockito.verify(metricClientMock).sendMetric(metricArgCaptor.capture());

    Metric capturedMetric = metricArgCaptor.getValue();
    Assert.assertNotNull(capturedMetric);

    InfluxPoint capturedMetricPoint = (InfluxPoint) capturedMetric.get();

    Assert.assertTrue(capturedMetricPoint.getTags().containsKey("queryType"));
    Assert.assertTrue(capturedMetricPoint.getFields().containsKey("dnsProcessingDelayMillis"));
    Assert.assertTrue(capturedMetricPoint.getFields().containsKey("query"));
    // if tag is false, it will not be present in the final metrics, here this tag does have null
    // value
    Assert.assertFalse(capturedMetricPoint.getTags().containsKey("failureReason"));
  }

  public void sendSGEMetricTest() {

    String sgName = "SG1";
    String sgeName = "127.0.0.1:5060:UDP";

    Boolean status = false;

    metricService.sendSGElementMetric(sgName, sgeName, status);

    ArgumentCaptor<Metric> metricArgCaptor = ArgumentCaptor.forClass(Metric.class);
    Mockito.verify(metricClientMock).sendMetric(metricArgCaptor.capture());

    Metric capturedMetric = metricArgCaptor.getValue();
    Assert.assertNotNull(capturedMetric);

    InfluxPoint capturedMetricPoint = (InfluxPoint) capturedMetric.get();

    Assert.assertTrue(capturedMetricPoint.getTags().containsKey("sgName"));
    Assert.assertTrue(capturedMetricPoint.getTags().containsKey("sgeName"));

    Assert.assertTrue(capturedMetricPoint.getFields().containsKey("status"));
  }

  public void sendSGMetricTest() {

    String sgName = "SG1";
    Boolean status = true;

    metricService.sendSGMetric(sgName, status);

    ArgumentCaptor<Metric> metricArgCaptor = ArgumentCaptor.forClass(Metric.class);
    Mockito.verify(metricClientMock).sendMetric(metricArgCaptor.capture());

    Metric capturedMetric = metricArgCaptor.getValue();
    Assert.assertNotNull(capturedMetric);

    InfluxPoint capturedMetricPoint = (InfluxPoint) capturedMetric.get();

    Assert.assertTrue(capturedMetricPoint.getTags().containsKey("sgName"));
    Assert.assertTrue(capturedMetricPoint.getFields().containsKey("status"));
  }

  public void sendTrunkMetricTest() {

    String trunk = "antares";
    int response = 200;

    metricService.sendTrunkMetric(trunk, 200, callId);

    ArgumentCaptor<Metric> metricArgCaptor = ArgumentCaptor.forClass(Metric.class);
    Mockito.verify(metricClientMock).sendMetric(metricArgCaptor.capture());

    Metric capturedMetric = metricArgCaptor.getValue();
    Assert.assertNotNull(capturedMetric);

    InfluxPoint capturedMetricPoint = (InfluxPoint) capturedMetric.get();
    Assert.assertEquals(capturedMetric.measurement(), "dhruva.trunkMetric");
    Assert.assertTrue(capturedMetricPoint.getTags().containsKey("trunk"));
    Assert.assertTrue(capturedMetricPoint.getTag("trunk").equals(trunk));
    Assert.assertTrue(capturedMetricPoint.getFields().containsKey("response"));
    Assert.assertTrue(capturedMetricPoint.getField("response").equals(response));
    Assert.assertTrue(capturedMetricPoint.getFields().containsKey("callId"));
    Assert.assertTrue(capturedMetricPoint.getField("callId").equals(callId));
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

  @Test(timeOut = 2000, description = "tests latency metric in different call states")
  public void latencyMetricTest() throws InterruptedException {

    SipMetricsContext contextAtStart =
        new SipMetricsContext(
            metricService, SipMetricsContext.State.proxyNewRequestReceived, callId, true);

    with()
        .pollInSameThread()
        .await()
        .atMost(100, TimeUnit.MILLISECONDS)
        .pollInterval(10, TimeUnit.MILLISECONDS)
        .until(() -> metricService.getStopWatchTimers().get(callId + "." + "call.latency") != null);
    with()
        .pollInSameThread()
        .await()
        .atMost(100, TimeUnit.MILLISECONDS)
        .pollInterval(10, TimeUnit.MILLISECONDS)
        .until(
            () ->
                metricService.getStopWatchTimers().get(callId + "." + "call.latency").isStarted());

    SipMetricsContext contextInBetween =
        new SipMetricsContext(
            metricService, SipMetricsContext.State.proxyNewRequestSendSuccess, callId, true);

    with()
        .pollInSameThread()
        .await()
        .atMost(100, TimeUnit.MILLISECONDS)
        .pollInterval(10, TimeUnit.MILLISECONDS)
        .until(
            () ->
                metricService.getStopWatchTimers().get(callId + "." + "call.latency").getSplitTime()
                    > 0);

    SipMetricsContext contextAtEnd =
        new SipMetricsContext(
            metricService,
            SipMetricsContext.State.proxyNewRequestFinalResponseProcessed,
            callId,
            true);

    with()
        .pollInSameThread()
        .await()
        .atMost(100, TimeUnit.MILLISECONDS)
        .pollInterval(10, TimeUnit.MILLISECONDS)
        .until(() -> metricService.getStopWatchTimers().get(callId + "." + "call.latency") == null);

    SipMetricsContext contextAtStart2 =
        new SipMetricsContext(
            metricService, SipMetricsContext.State.proxyNewRequestReceived, callId, true);
    with()
        .pollInSameThread()
        .await()
        .atMost(100, TimeUnit.MILLISECONDS)
        .pollInterval(10, TimeUnit.MILLISECONDS)
        .until(
            () ->
                metricService.getStopWatchTimers().get(callId + "." + "call.latency").isStarted());

    SipMetricsContext contextInBetween2 =
        new SipMetricsContext(
            metricService, SipMetricsContext.State.proxyNewRequestSendFailure, callId, true);

    with()
        .pollInSameThread()
        .await()
        .atMost(100, TimeUnit.MILLISECONDS)
        .pollInterval(10, TimeUnit.MILLISECONDS)
        .until(
            () ->
                metricService.getStopWatchTimers().get(callId + "." + "call.latency").getSplitTime()
                    > 0);

    long t1 = metricService.getStopWatchTimers().get(callId + "." + "call.latency").getSplitTime();

    SipMetricsContext contextRetry2 =
        new SipMetricsContext(
            metricService, SipMetricsContext.State.proxyNewRequestRetryNextElement, callId, true);

    with()
        .pollInSameThread()
        .await()
        .atMost(100, TimeUnit.MILLISECONDS)
        .pollInterval(10, TimeUnit.MILLISECONDS)
        .until(
            () ->
                metricService.getStopWatchTimers().get(callId + "." + "call.latency").getSplitTime()
                    > t1);

    long t2 = metricService.getStopWatchTimers().get(callId + "." + "call.latency").getSplitTime();

    SipMetricsContext contextInBetween22 =
        new SipMetricsContext(
            metricService, SipMetricsContext.State.proxyNewRequestSendSuccess, callId, true);

    with()
        .pollInSameThread()
        .await()
        .atMost(100, TimeUnit.MILLISECONDS)
        .pollInterval(10, TimeUnit.MILLISECONDS)
        .until(
            () ->
                metricService.getStopWatchTimers().get(callId + "." + "call.latency").getSplitTime()
                    > t2);

    SipMetricsContext contextAtEnd2 =
        new SipMetricsContext(
            metricService,
            SipMetricsContext.State.proxyNewRequestFinalResponseProcessed,
            callId,
            CALLTYPE_TEST,
            true);
    with()
        .pollInSameThread()
        .await()
        .atMost(100, TimeUnit.MILLISECONDS)
        .pollInterval(10, TimeUnit.MILLISECONDS)
        .until(() -> metricService.getStopWatchTimers().get(callId + "." + "call.latency") == null);

    // verify metrics is emitted for latency
    Mockito.verify(metricClientMock, times(2)).sendMetric(metricArgumentCaptor.capture());

    Metric capturedMetric = metricArgumentCaptor.getValue();
    InfluxPoint capturedMetricPoint = (InfluxPoint) capturedMetric.get();

    Assert.assertTrue(capturedMetricPoint.getFields().containsKey("count"));
    Assert.assertTrue(capturedMetricPoint.getFields().containsKey("duration"));
    Assert.assertFalse(capturedMetricPoint.getFields().containsKey("durationExpected"));
    Assert.assertTrue(capturedMetricPoint.getFields().containsKey("eventSuccess"));
    Assert.assertTrue(capturedMetricPoint.getTags().containsKey("callType"));
  }

  @Test(description = "test case to check various negative scenarios for emitting latency metric")
  public void createMetricsForLatencyNegativeTest() {

    // Metric will be created without any tags and fields as count, duration nothing is evaluated
    metricService.update(
        "test.latency",
        0L,
        0L,
        TimeUnit.MILLISECONDS,
        0L,
        TimeUnit.MILLISECONDS,
        true,
        null,
        CALLTYPE_TEST);

    verify(metricClientMock, atMost(1)).sendMetric(metricArgumentCaptor.capture());

    Metric capturedMetric = metricArgumentCaptor.getValue();
    Assert.assertNotNull(capturedMetric);

    InfluxPoint capturedMetricPoint = (InfluxPoint) capturedMetric.get();
    Assert.assertEquals(capturedMetricPoint.getTags().size(), 0);
    Assert.assertEquals(capturedMetricPoint.getFields().size(), 0);

    // Metric will be created without any tags and fields as count, duration nothing is evaluated
    metricService.update("test.latency", 0L, 1L, null, 1L, null, null, null, null);

    verify(metricClientMock, atMost(2)).sendMetric(metricArgumentCaptor.capture());

    capturedMetric = metricArgumentCaptor.getValue();
    Assert.assertNotNull(capturedMetric);

    capturedMetricPoint = (InfluxPoint) capturedMetric.get();
    Assert.assertEquals(capturedMetricPoint.getTags().size(), 0);
    Assert.assertEquals(capturedMetricPoint.getFields().size(), 0);
  }

  public void createMetricsForLatencyPositiveTest() {
    // Positive path
    metricService.update(
        "test.latency",
        1L,
        3L,
        TimeUnit.MILLISECONDS,
        5L,
        TimeUnit.MILLISECONDS,
        true,
        callId,
        CALLTYPE_TEST);

    verify(metricClientMock, atMost(1)).sendMetric(metricArgumentCaptor.capture());

    Metric capturedMetric = metricArgumentCaptor.getValue();
    Assert.assertNotNull(capturedMetric);

    InfluxPoint capturedMetricPoint = (InfluxPoint) capturedMetric.get();
    Map<String, Object> capturedFields = capturedMetricPoint.getFields();
    Map<String, String> capturedTags = capturedMetricPoint.getTags();

    Assert.assertNotEquals(capturedFields.size(), 0);

    // callid
    Assert.assertTrue(capturedFields.containsKey("callId"));
    Assert.assertTrue(capturedFields.containsValue(callId));

    // calltype
    Assert.assertTrue(capturedTags.containsKey("callType"));
    Assert.assertTrue(capturedTags.containsValue(CALLTYPE_TEST));
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
    String key = joiner.join(testCallId, testMetricName);
    Assert.assertNull(timers.getIfPresent(key));

    // negative scenario 3
    testCallId = "1-test@192.168.0.1";
    testMetricName = null;

    metricService.getTimers().invalidateAll();

    metricService.startTimer(testCallId, testMetricName);
    timers = metricService.getTimers();
    key = joiner.join(testCallId, testMetricName);
    Assert.assertNull(timers.getIfPresent(key));

    // positive case
    testCallId = "1-test@192.168.0.1";
    testMetricName = "test.latency";

    metricService.getTimers().invalidateAll();

    metricService.startTimer(testCallId, testMetricName);
    timers = metricService.getTimers();
    key = joiner.join(testCallId, testMetricName);
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

  @Test(description = "test various scenarios of metricService using stopWatch")
  public void testStopWatch() {
    String callId = "ABCDWebEx123";
    String metric = "dhruva.latency";
    String key = joiner.join(callId, metric);

    // Start the stop watch timer for given callID
    metricService.startStopWatch(callId, metric);
    HashMap<String, StopWatch> timers = metricService.getStopWatchTimers();
    Assert.assertNotNull(timers.get(key));
    StopWatch stopWatch = timers.get(key);
    Assert.assertTrue(stopWatch.isStarted());

    // Pause
    metricService.pauseStopWatch(callId, metric);
    Assert.assertTrue(stopWatch.isSuspended());

    // Resume
    metricService.resumeStopWatch(callId, metric);
    Assert.assertTrue(stopWatch.isStarted());

    // split
    metricService.splitStopWatch(callId, metric);

    TimeUnit timeUnit = TimeUnit.MILLISECONDS;
    long splitTime1 = timeUnit.convert(stopWatch.getSplitNanoTime(), TimeUnit.NANOSECONDS);
    Assert.assertTrue(splitTime1 >= 0);

    long splitTime2 = metricService.getSplitTimeStopWatch(callId, metric);
    Assert.assertEquals(splitTime2, splitTime1);

    // End
    metricService.endStopWatch(callId, metric);
    Assert.assertTrue(stopWatch.isStopped());

    // Make sure key is removed
    Assert.assertNull(timers.get(key));

    metricService.startStopWatch(callId, metric);
    doSomeTask(100);

    Assert.assertTrue(metricService.getSplitTimeStopWatch(callId, metric) < 0);
  }

  private void doSomeTask(long sleep) {
    try {
      Thread.sleep(sleep);
    } catch (InterruptedException e) {
      e.printStackTrace();
    }
  }
}
