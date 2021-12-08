package com.cisco.dhruva.application;

import static org.mockito.Mockito.*;

import com.cisco.dhruva.application.calltype.CallType;
import com.cisco.dhruva.application.calltype.CallTypeEnum;
import com.cisco.dhruva.application.exceptions.FilterTreeException;
import com.cisco.dhruva.application.exceptions.InvalidCallTypeException;
import com.cisco.dhruva.application.filters.Filter;
import com.cisco.dsb.proxy.ProxyService;
import com.cisco.dsb.proxy.dto.ProxyAppConfig;
import com.cisco.dsb.proxy.messaging.ProxySIPRequest;
import com.cisco.dsb.proxy.messaging.ProxySIPResponse;
import com.google.common.collect.ImmutableList;
import java.util.List;
import java.util.Optional;
import java.util.function.Consumer;
import javax.sip.message.Response;
import org.mockito.*;
import org.testng.Assert;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeTest;
import org.testng.annotations.Test;

public class DhruvaCallingAppTest {
  @Mock ProxyService proxyService;
  @Mock Filter filter;
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
    ImmutableList<CallTypeEnum> calltypes_expected =
        ImmutableList.of(
            com.cisco.dhruva.application.calltype.CallTypeEnum.DIAL_IN_PSTN,
            com.cisco.dhruva.application.calltype.CallTypeEnum.DIAL_IN_B2B,
            com.cisco.dhruva.application.calltype.CallTypeEnum.DIAL_OUT_WXC,
            com.cisco.dhruva.application.calltype.CallTypeEnum.DIAL_OUT_B2B);
    ArgumentCaptor<ProxyAppConfig> proxyAppConfig = ArgumentCaptor.forClass(ProxyAppConfig.class);
    ArgumentCaptor<List<CallTypeEnum>> interestedCallTypes = ArgumentCaptor.forClass(List.class);
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

    requestConsumer.accept(proxySIPRequest);

    verify(proxySIPRequest, times(1)).reject(Response.NOT_FOUND);
  }
}