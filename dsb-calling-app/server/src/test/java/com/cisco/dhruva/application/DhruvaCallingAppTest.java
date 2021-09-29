package com.cisco.dhruva.application;

import static org.mockito.Mockito.*;
import static org.testng.Assert.assertEquals;

import com.cisco.dhruva.application.calltype.CallType;
import com.cisco.dhruva.application.exceptions.FilterTreeException;
import com.cisco.dhruva.application.filters.Filter;
import com.cisco.dsb.common.dns.DnsInjectionService;
import com.cisco.dsb.proxy.ProxyService;
import com.cisco.dsb.proxy.dto.ProxyAppConfig;
import com.cisco.dsb.proxy.messaging.ProxySIPRequest;
import com.cisco.dsb.proxy.messaging.ProxySIPResponse;
import com.cisco.dsb.proxy.sip.ProxyCookieImpl;
import com.google.common.collect.ImmutableList;
import java.util.List;
import java.util.Optional;
import java.util.function.Consumer;
import org.mockito.*;
import org.testng.Assert;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeTest;
import org.testng.annotations.Test;

public class DhruvaCallingAppTest {
  @Mock ProxyService proxyService;
  @Mock Filter filter;
  @Mock DnsInjectionService dnsInjectionService;
  @InjectMocks DhruvaCallingApp dhruvaCallingApp;
  @Mock ProxySIPRequest proxySIPRequest;
  @Mock ProxySIPResponse proxySIPResponse;
  @Mock CallType callType;
  Optional<CallType> optionalCallType;
  ProxyAppConfig proxyAppConfig;

  @BeforeTest
  public void init() {
    MockitoAnnotations.initMocks(this);
  }

  @AfterMethod
  public void setup() {
    reset(filter, proxySIPRequest, proxySIPResponse);
  }

  @Test
  public void testAppInitialization() throws FilterTreeException {
    ImmutableList<CallType.CallTypes> calltypes_expected =
        ImmutableList.of(
            CallType.CallTypes.DIAL_IN_PSTN,
            CallType.CallTypes.DIAL_IN_B2B,
            CallType.CallTypes.DIAL_OUT_WXC,
            CallType.CallTypes.DIAL_OUT_B2B);
    ArgumentCaptor<ProxyAppConfig> proxyAppConfig = ArgumentCaptor.forClass(ProxyAppConfig.class);
    ArgumentCaptor<List<CallType.CallTypes>> interestedCallTypes =
        ArgumentCaptor.forClass(List.class);
    InOrder order = inOrder(filter, proxyService);
    order.verify(filter, Mockito.times(1)).register(interestedCallTypes.capture());
    order.verify(proxyService, Mockito.times(1)).register(proxyAppConfig.capture());
    this.proxyAppConfig = proxyAppConfig.getValue();
    List calltypes_actual = interestedCallTypes.getValue();
    Assert.assertEquals(calltypes_actual, calltypes_expected);
  }

  @Test(
      description = "Request which has matching calltype",
      dependsOnMethods = {"testAppInitialization"})
  public void testRequestConsumer_1() {
    Consumer<ProxySIPRequest> requestConsumer = proxyAppConfig.getRequestConsumer();
    Assert.assertNotNull(requestConsumer);
    optionalCallType = Optional.of(callType);
    when(filter.filter(proxySIPRequest)).thenReturn(optionalCallType);
    when(proxySIPRequest.getCallId()).thenReturn("TestCall");
    when(callType.getOutBoundNetwork()).thenReturn("NetOut");
    when(callType.processRequest())
        .thenReturn(
            proxySIPRequestMono -> {
              proxySIPRequestMono.subscribe(
                  request -> {
                    // verify request was sent to calltype
                    assertEquals(request, proxySIPRequest);
                  });
            });
    requestConsumer.accept(proxySIPRequest);
  }

  @Test(
      description = "No calltype found",
      dependsOnMethods = {"testAppInitialization"})
  public void testRequestConsumer_2() {
    Consumer<ProxySIPRequest> requestConsumer = proxyAppConfig.getRequestConsumer();
    Assert.assertNotNull(requestConsumer);

    when(filter.filter(proxySIPRequest)).thenReturn(Optional.empty());

    requestConsumer.accept(proxySIPRequest);

    verifyNoInteractions(proxySIPRequest);
  }

  @Test(
      description = "Response, corresponding calltype found",
      dependsOnMethods = {"testAppInitialization"})
  public void testResponseConsumer_1() {
    Consumer<ProxySIPResponse> responseConsumer = proxyAppConfig.getResponseConsumer();
    Assert.assertNotNull(responseConsumer);
    // using these values from testRequestConsumer_1
    ProxyCookieImpl cookie = mock(ProxyCookieImpl.class);
    when(cookie.getCalltype()).thenReturn(callType);
    when(proxySIPResponse.getCookie()).thenReturn(cookie);
    when(callType.processResponse())
        .thenReturn(
            proxySIPResponseMono -> {
              proxySIPResponseMono.subscribe(
                  response -> {
                    // verify request was sent to calltype
                    assertEquals(response, proxySIPResponse);
                  });
            });
    responseConsumer.accept(proxySIPResponse);
    verify(callType, Mockito.times(1)).processResponse();
  }

  @Test(
      description = "Response, no cookie set or no calltype found",
      dependsOnMethods = {"testAppInitialization"})
  public void testResponseConsumer_2() {
    Consumer<ProxySIPResponse> responseConsumer = proxyAppConfig.getResponseConsumer();
    Assert.assertNotNull(responseConsumer);
    // Key not found
    when(proxySIPResponse.getCookie()).thenReturn(null);
    responseConsumer.accept(proxySIPResponse);
    verify(proxySIPResponse, times(1)).proxy();

    reset(proxySIPResponse);

    when(proxySIPResponse.getCookie()).thenReturn(mock(ProxyCookieImpl.class));
    responseConsumer.accept(proxySIPResponse);
    verify(proxySIPResponse, times(1)).proxy();
  }
}
