package com.cisco.dhruva.application;

import static org.mockito.Mockito.*;

import com.cisco.dhruva.application.calltype.CallType;
import com.cisco.dhruva.application.calltype.CallTypeEnum;
import com.cisco.dhruva.application.exceptions.InvalidCallTypeException;
import com.cisco.dhruva.application.filters.Filter;
import com.cisco.dhruva.ratelimiter.CallingAppRateLimiterConfigurator;
import com.cisco.dsb.common.exception.DhruvaRuntimeException;
import com.cisco.dsb.common.record.DhruvaAppRecord;
import com.cisco.dsb.common.service.MetricService;
import com.cisco.dsb.common.util.log.event.DhruvaEvent;
import com.cisco.dsb.common.util.log.event.EventingService;
import com.cisco.dsb.common.util.log.event.LoggingEvent;
import com.cisco.dsb.proxy.ProxyService;
import com.cisco.dsb.proxy.dto.ProxyAppConfig;
import com.cisco.dsb.proxy.messaging.ProxySIPRequest;
import com.cisco.dsb.proxy.messaging.ProxySIPResponse;
import com.google.common.collect.ImmutableList;
import java.util.List;
import java.util.function.Consumer;
import javax.sip.message.Response;
import org.mockito.*;
import org.springframework.context.event.ContextRefreshedEvent;
import org.testng.Assert;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeTest;
import org.testng.annotations.Test;

public class DhruvaCallingAppTest {
  @Mock ProxyService proxyService;
  @Mock Filter filter;
  @Mock CallingAppConfigurationProperty callingAppConfigurationProperty;
  @Mock CallingAppRateLimiterConfigurator callingAppRateLimiterConfigurator;
  @Mock MetricService metricService;
  @InjectMocks DhruvaCallingApp dhruvaCallingApp;
  @Mock ProxySIPRequest proxySIPRequest;
  @Mock ProxySIPResponse proxySIPResponse;
  @Mock CallType callType;
  @Mock EventingService eventingService;
  ProxyAppConfig proxyAppConfig;

  @BeforeTest
  public void init() {
    MockitoAnnotations.openMocks(this);
    when(callingAppConfigurationProperty.getRateLimiterMetricPerInterval()).thenReturn(100);
    // Calling this explicity , ideally will be invoked by spring once all the beans are loaded.
    dhruvaCallingApp.init(mock(ContextRefreshedEvent.class));
  }

  @AfterMethod
  public void setup() {
    reset(filter, proxySIPRequest, proxySIPResponse);
    when(proxySIPRequest.getAppRecord()).thenReturn(new DhruvaAppRecord());
  }

  @Test
  public void testAppInitialization() throws Exception {
    ImmutableList<CallTypeEnum> calltypes_expected =
        ImmutableList.of(
            com.cisco.dhruva.application.calltype.CallTypeEnum.DIAL_IN_PSTN,
            com.cisco.dhruva.application.calltype.CallTypeEnum.DIAL_IN_B2B,
            com.cisco.dhruva.application.calltype.CallTypeEnum.DIAL_OUT_WXC,
            com.cisco.dhruva.application.calltype.CallTypeEnum.DIAL_OUT_B2B);
    ArgumentCaptor<ProxyAppConfig> proxyAppConfig = ArgumentCaptor.forClass(ProxyAppConfig.class);
    ArgumentCaptor<List<CallTypeEnum>> interestedCallTypes = ArgumentCaptor.forClass(List.class);
    InOrder order = inOrder(filter, proxyService);
    order.verify(filter).register(interestedCallTypes.capture());
    order.verify(proxyService).init();
    order.verify(proxyService).register(proxyAppConfig.capture());
    this.proxyAppConfig = proxyAppConfig.getValue();
    List<CallTypeEnum> calltypes_actual = interestedCallTypes.getValue();
    Assert.assertEquals(calltypes_actual, calltypes_expected);

    ImmutableList<Class<? extends DhruvaEvent>> interestedEventsExpected =
        ImmutableList.of(LoggingEvent.class);

    ArgumentCaptor<List<Class<? extends DhruvaEvent>>> interestedEvents =
        ArgumentCaptor.forClass(List.class);
    verify(eventingService).register(interestedEvents.capture());
    List<Class<? extends DhruvaEvent>> interestedEventValues = interestedEvents.getValue();

    Assert.assertEquals(interestedEventValues, interestedEventsExpected);
  }

  @Test(
      description = "Request which has matching calltype",
      dependsOnMethods = {"testAppInitialization"})
  public void testRequestConsumer_1() throws InvalidCallTypeException {
    Consumer<ProxySIPRequest> requestConsumer = proxyAppConfig.getRequestConsumer();
    Assert.assertNotNull(requestConsumer);
    when(filter.filter(proxySIPRequest)).thenReturn(callType);

    requestConsumer.accept(proxySIPRequest);

    verify(callType, times(1)).processRequest(proxySIPRequest);
  }

  @Test(
      description = "No calltype found",
      dependsOnMethods = {"testAppInitialization"})
  public void testRequestConsumer_2() throws InvalidCallTypeException {
    Consumer<ProxySIPRequest> requestConsumer = proxyAppConfig.getRequestConsumer();
    Assert.assertNotNull(requestConsumer);

    when(filter.filter(proxySIPRequest)).thenThrow(InvalidCallTypeException.class);
    when(proxySIPRequest.getCallId()).thenReturn("::This is callid::");
    requestConsumer.accept(proxySIPRequest);

    verify(proxySIPRequest, times(1))
        .reject(
            Response.NOT_FOUND,
            "Rejecting with 404, Unable to find the calltype for request callid: ::This is callid::");
  }

  @Test(
      description = "Unhandled Run time exception",
      dependsOnMethods = {"testAppInitialization"})
  public void testRequestConsumer_3() throws InvalidCallTypeException {
    Consumer<ProxySIPRequest> requestConsumer = proxyAppConfig.getRequestConsumer();
    Assert.assertNotNull(requestConsumer);

    when(filter.filter(proxySIPRequest)).thenThrow(DhruvaRuntimeException.class);
    when(proxySIPRequest.getCallId()).thenReturn("::This is callid::");
    requestConsumer.accept(proxySIPRequest);

    verify(proxySIPRequest, times(1))
        .reject(
            Response.SERVER_INTERNAL_ERROR,
            "Unhandled exception, sending back 500 error for request callid: ::This is callid::");
  }
}
