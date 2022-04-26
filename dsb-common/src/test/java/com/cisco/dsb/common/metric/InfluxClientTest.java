package com.cisco.dsb.common.metric;

import static org.mockito.Mockito.*;

import com.cisco.dsb.common.config.DhruvaProperties;
import com.cisco.dsb.common.service.MetricService;
import com.cisco.wx2.metrics.InfluxPoint;
import com.cisco.wx2.server.InfluxDBClientHelper;
import java.util.HashSet;
import java.util.Set;
import org.mockito.*;
import org.testng.Assert;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

@Test
public class InfluxClientTest {

  Metric testMetric1;
  Metric testMetric2;

  @InjectMocks InfluxClient influxClient;
  @Mock InfluxDBClientHelper influxDBClientHelper;
  @Mock DhruvaProperties dhruvaProperties;

  @BeforeMethod
  public void setup() {
    MockitoAnnotations.initMocks(this);

    testMetric1 =
        Metrics.newMetric()
            .measurement("testMeasurement")
            .tag("testTag1", "randomTagValue1")
            .tag("testTag2", 839)
            .tag("isTesting", true)
            .field("testField1", "randomFieldValue1")
            .field("testField2", 930);

    testMetric2 =
        Metrics.newMetric()
            .measurement("testMeasurement2")
            .tag("testTag1", "randomTagValue1")
            .tag("testTag2", 394)
            .tag("isTesting", false)
            .field("testField1", null)
            .field("testField2", 100);
  }

  public void sendMetricsTest() {
    InfluxClient influxClientMock = mock(InfluxClient.class);

    doAnswer(
            invocation -> {
              Object arg0 = invocation.getArgument(0);
              // ((Metric) arg0).get()
              Assert.assertEquals(testMetric1.get(), ((InfluxPoint) ((Metric) arg0).get()));
              return null;
            })
        .when(influxClientMock)
        .sendMetric(any());

    influxClientMock.sendMetric(testMetric1);

    verify(influxClientMock, times(1)).sendMetric(any());
  }

  @Test(expectedExceptions = NullPointerException.class)
  public void sendMetricsTestNullCase() {
    InfluxClient influxClientMock2 = mock(InfluxClient.class);

    doThrow(NullPointerException.class).when(influxClientMock2).sendMetric(isNull());

    influxClientMock2.sendMetric(null);

    // verify(influxClient).sendMetric(metricArgumentCaptor.capture());
  }

  public void instanceNameTest() {

    // case -> env - null , pod_name - null
    Assert.assertEquals(influxClient.getInstanceName(), "");

    // case -> env: null, pod_name : valid

    when(dhruvaProperties.getPodNameEnvVar()).thenReturn("test-dhruva-0");
    when(dhruvaProperties.getEnvironment()).thenReturn(null);
    Assert.assertEquals(influxClient.getInstanceName(), "-test-dhruva-0");

    // case -> env: valid , podName : null
    when(dhruvaProperties.getPodNameEnvVar()).thenReturn(null);
    when(dhruvaProperties.getEnvironment()).thenReturn("TESTENV");

    Assert.assertEquals(influxClient.getInstanceName(), "TESTENV");

    // positive path
    when(dhruvaProperties.getPodNameEnvVar()).thenReturn("test-dhruva-0");
    when(dhruvaProperties.getEnvironment()).thenReturn("TESTENV");

    Assert.assertEquals(influxClient.getInstanceName(), "TESTENV-test-dhruva-0");
  }

  @Test(description = "test for sending single influx point as a metric")
  public void influxClientSendMetricTest() {
    ArgumentCaptor<InfluxPoint> metricCaptor = ArgumentCaptor.forClass(InfluxPoint.class);

    doNothing().when(influxDBClientHelper).writePointAsync(any());
    influxClient.sendMetric(testMetric1);
    verify(influxDBClientHelper, times(1)).writePointAsync(metricCaptor.capture());
    InfluxPoint receivedMetric = metricCaptor.getValue();

    Assert.assertNotNull(receivedMetric);
    // equals check for all the fields/tags of metric emitted
    Assert.assertEquals(
        receivedMetric.getMeasurement(), ((InfluxMetric) testMetric1).measurement());
  }

  @Test(description = "test case for checking multiple influxpoints")
  public void influxClientSendMetricsTest() {

    ArgumentCaptor<Set<InfluxPoint>> metricsCaptor = ArgumentCaptor.forClass(Set.class);

    doNothing().when(influxDBClientHelper).writePointAsync(any());

    Set<Metric> metricsSet = new HashSet<>();
    metricsSet.add(testMetric1);
    metricsSet.add(testMetric2);

    influxClient.sendMetrics(metricsSet);
    verify(influxDBClientHelper, times(1)).writePoints(metricsCaptor.capture());

    Set<InfluxPoint> receivedMetric = metricsCaptor.getValue();

    Assert.assertEquals(receivedMetric.size(), 2);
    Assert.assertTrue(receivedMetric.contains((InfluxPoint) testMetric1.get()));
    Assert.assertTrue(receivedMetric.contains((InfluxPoint) testMetric2.get()));
  }

  @Test(description = "Tests for validating various scenarious of sipmetriccontext")
  public void sipMetricContextNegativeTest() {
    MetricService metricServiceMock = mock(MetricService.class);

    // scenario where value, callid, success flag is not set
    SipMetricsContext testSipContext =
        new SipMetricsContext(
            metricServiceMock, SipMetricsContext.State.proxyNewRequestReceived, null);

    testSipContext.setSuccessful();
    Assert.assertTrue(testSipContext.isSuccessful());
    Assert.assertEquals(testSipContext.getCallId(), "");
    Assert.assertEquals(testSipContext.getValue(), 1);

    testSipContext.setState(SipMetricsContext.State.proxyNewRequestFinalResponseProcessed);
    Assert.assertEquals(
        testSipContext.state, SipMetricsContext.State.proxyNewRequestFinalResponseProcessed);
    testSipContext.setSuccessful("1-test@192.168.0.1");
    Assert.assertEquals(testSipContext.getCallId(), "1-test@192.168.0.1");
    testSipContext.setSuccessful();
    Assert.assertNotNull(testSipContext.getCallId());
  }
}
