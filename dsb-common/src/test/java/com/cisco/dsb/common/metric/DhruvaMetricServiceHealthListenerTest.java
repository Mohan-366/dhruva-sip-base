package com.cisco.dsb.common.metric;

import static org.mockito.Mockito.*;

import com.cisco.dsb.common.executor.DhruvaExecutorService;
import com.cisco.dsb.common.executor.ExecutorType;
import com.cisco.dsb.common.service.MetricService;
import com.cisco.wx2.dto.health.ServiceHealth;
import com.cisco.wx2.dto.health.ServiceState;
import com.cisco.wx2.dto.health.ServiceType;
import com.cisco.wx2.server.health.ServiceHealthManager;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.testng.Assert;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

@Test(description = "For testing the service availability functionality")
public class DhruvaMetricServiceHealthListenerTest {

  @InjectMocks private DhruvaMetricServiceHealthListner dhruvaMetricServiceHealthListner;

  @Mock private ServiceHealthManager serviceHealthManagerMock;

  @Mock private MetricService metricServiceMock;

  @Mock private DhruvaExecutorService dhruvaExecutorServiceMock;

  @Mock private ServiceHealth serviceHealthMock;

  @Mock ScheduledThreadPoolExecutor scheduledThreadPoolExecutorMock;

  ArgumentCaptor<ServiceHealth> serviceHealthArgumentCaptor;
  ArgumentCaptor<Boolean> includedFlagCaptor;

  @BeforeMethod
  private void before() {
    MockitoAnnotations.initMocks(this);
    when(dhruvaExecutorServiceMock.getScheduledExecutorThreadPool(
            ExecutorType.HEALTH_MONITOR_SERVICE))
        .thenReturn(scheduledThreadPoolExecutorMock);
    serviceHealthArgumentCaptor = ArgumentCaptor.forClass(ServiceHealth.class);
    includedFlagCaptor = ArgumentCaptor.forClass(Boolean.class);
  }

  @Test(
      description =
          "To test captured metrics when service is being monitored along with upstream elements")
  public void reportServiceHealthUpstreamIncludedWhenDhruvaOnlineTest() {

    when(serviceHealthMock.isOnline()).thenReturn(true);
    when(serviceHealthMock.getServiceState()).thenReturn(ServiceState.ONLINE);

    when(serviceHealthManagerMock.getServiceHealth()).thenReturn(serviceHealthMock);

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

    dhruvaMetricServiceHealthListner.reportServiceHealth(null, true);

    verify(metricServiceMock)
        .emitServiceHealth(serviceHealthArgumentCaptor.capture(), includedFlagCaptor.capture());

    boolean includedFlagForMetric = includedFlagCaptor.getValue();
    ServiceHealth capturedServiceHealth = serviceHealthArgumentCaptor.getValue();

    Assert.assertTrue(includedFlagForMetric);
    Assert.assertEquals(capturedServiceHealth.getServiceState(), ServiceState.ONLINE);
    Assert.assertEquals(capturedServiceHealth.getMessage(), "Dhruva service is healthy");
  }

  @Test(description = "Test to check captured metrics when dhruva service is offline")
  public void reportServiceHealthUpstreamIncludedWhenDhruvaOfflineTest1() {
    when(serviceHealthMock.isOnline()).thenReturn(false);
    when(serviceHealthMock.getServiceState()).thenReturn(ServiceState.OFFLINE);

    when(serviceHealthManagerMock.getServiceHealth()).thenReturn(serviceHealthMock);

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
            .serviceState(ServiceState.OFFLINE)
            .message("offline")
            .build());

    when(serviceHealthMock.getUpstreamServices()).thenReturn(upstreamServiceList);
    dhruvaMetricServiceHealthListner.reportServiceHealth(null, true);

    verify(metricServiceMock)
        .emitServiceHealth(serviceHealthArgumentCaptor.capture(), includedFlagCaptor.capture());

    boolean includedFlagForMetric = includedFlagCaptor.getValue();
    ServiceHealth capturedServiceHealth = serviceHealthArgumentCaptor.getValue();

    Assert.assertTrue(includedFlagForMetric);
    Assert.assertEquals(capturedServiceHealth.getMessage(), "Offline: testUpstreamSrv2");
    Assert.assertEquals(capturedServiceHealth.getServiceState(), ServiceState.OFFLINE);
  }

  @Test(description = "Test when the upstream element/s is offline")
  public void reportServiceHealthUpstreamIncludedWhenDhruvaOfflineTest2() {
    when(serviceHealthMock.isOnline()).thenReturn(false);
    when(serviceHealthMock.getServiceState()).thenReturn(ServiceState.OFFLINE);

    when(serviceHealthManagerMock.getServiceHealth()).thenReturn(serviceHealthMock);

    List<ServiceHealth> upstreamServiceList = new ArrayList<>();

    upstreamServiceList.add(
        ServiceHealth.builder()
            .serviceName("testUpstreamSrv1")
            .serviceType(ServiceType.OPTIONAL)
            .serviceState(ServiceState.OFFLINE)
            .message("service is offline")
            .build());

    upstreamServiceList.add(
        ServiceHealth.builder()
            .serviceName("testUpstreamSrv2")
            .serviceType(ServiceType.REQUIRED)
            .serviceState(ServiceState.FAULT)
            .message("service is offline")
            .build());

    upstreamServiceList.add(
        ServiceHealth.builder()
            .serviceName("testUpstreamSrv3")
            .serviceType(ServiceType.REQUIRED)
            .serviceState(ServiceState.OFFLINE)
            .message("service is offline")
            .build());

    when(serviceHealthMock.getUpstreamServices()).thenReturn(upstreamServiceList);
    dhruvaMetricServiceHealthListner.reportServiceHealth(null, true);

    verify(metricServiceMock)
        .emitServiceHealth(serviceHealthArgumentCaptor.capture(), includedFlagCaptor.capture());

    boolean includedFlagForMetric = includedFlagCaptor.getValue();
    ServiceHealth capturedServiceHealth = serviceHealthArgumentCaptor.getValue();

    Assert.assertTrue(includedFlagForMetric);
    Assert.assertEquals(
        capturedServiceHealth.getMessage(), "Offline: testUpstreamSrv2,testUpstreamSrv3");
    Assert.assertEquals(capturedServiceHealth.getServiceState(), ServiceState.OFFLINE);
  }

  public void initHealthServiceTest() {
    dhruvaMetricServiceHealthListner.init();
  }

  @Test(description = "Test when the service health status is changed")
  public void serviceChangedTest() {
    // invoke service health changed
    // proceed with testing the same way as reporthealth()

    {
      when(serviceHealthMock.isOnline()).thenReturn(false);
      when(serviceHealthMock.getServiceState()).thenReturn(ServiceState.OFFLINE);

      when(serviceHealthManagerMock.getServiceHealth()).thenReturn(serviceHealthMock);

      List<ServiceHealth> upstreamServiceList = new ArrayList<>();

      upstreamServiceList.add(
          ServiceHealth.builder()
              .serviceName("testUpstreamSrv1")
              .serviceType(ServiceType.REQUIRED)
              .serviceState(ServiceState.OFFLINE)
              .message("service is offline")
              .build());

      when(serviceHealthMock.getUpstreamServices()).thenReturn(upstreamServiceList);

      ServiceHealth oldHealthMock = mock(ServiceHealth.class);
      ServiceHealth newHealthMock = mock(ServiceHealth.class);
      when(newHealthMock.getServiceName()).thenReturn("Test-Service");

      dhruvaMetricServiceHealthListner.serviceHealthChanged(oldHealthMock, newHealthMock);

      verify(metricServiceMock)
          .emitServiceHealth(serviceHealthArgumentCaptor.capture(), includedFlagCaptor.capture());

      boolean includedFlagForMetric = includedFlagCaptor.getValue();
      ServiceHealth capturedServiceHealth = serviceHealthArgumentCaptor.getValue();

      Assert.assertTrue(includedFlagForMetric);
      Assert.assertEquals(capturedServiceHealth.getServiceState(), ServiceState.OFFLINE);
      Assert.assertEquals(capturedServiceHealth.getMessage(), "Offline: testUpstreamSrv1");
    }
  }
}
