package com.cisco.dhruva.application.calltype;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

import com.cisco.dhruva.application.CallingAppConfigurationProperty;
import com.cisco.dsb.common.exception.DhruvaRuntimeException;
import com.cisco.dsb.common.exception.ErrorCode;
import com.cisco.dsb.common.record.DhruvaAppRecord;
import com.cisco.dsb.common.service.MetricService;
import com.cisco.dsb.common.sip.jain.JainSipHelper;
import com.cisco.dsb.common.util.SpringApplicationContext;
import com.cisco.dsb.proxy.messaging.ProxySIPRequest;
import com.cisco.dsb.proxy.messaging.ProxySIPResponse;
import com.cisco.dsb.trunk.TrunkManager;
import com.cisco.dsb.trunk.trunks.TrunkType;
import gov.nist.javax.sip.address.SipUri;
import gov.nist.javax.sip.message.SIPRequest;
import java.text.ParseException;
import javax.sip.message.Response;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.springframework.context.ApplicationContext;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.BeforeTest;
import org.testng.annotations.DataProvider;
import org.testng.annotations.Test;
import reactor.core.publisher.Mono;

public class CallTypeTest {
  @Mock TrunkManager trunkManager;
  @Mock CallingAppConfigurationProperty configurationProperty;
  @Mock ProxySIPRequest proxySIPRequest;
  @Mock ProxySIPResponse proxySIPResponse;
  MetricService metricService;
  ApplicationContext context;
  SpringApplicationContext springApplicationContext = new SpringApplicationContext();

  @BeforeTest
  public void init() {
    MockitoAnnotations.openMocks(this);

    metricService = mock(MetricService.class);

    when(configurationProperty.getCallingEgress()).thenReturn("NS");
    when(configurationProperty.getPstnIngress()).thenReturn("CcpFusionUS");
    when(configurationProperty.getB2bEgress()).thenReturn("antares");
  }

  @BeforeMethod
  public void setup() {

    reset(proxySIPRequest, proxySIPResponse, trunkManager, metricService);
    context = mock(ApplicationContext.class);
    when(context.getBean(MetricService.class)).thenReturn(metricService);

    springApplicationContext.setApplicationContext(context);
    when(context.getBean(MetricService.class)).thenReturn(metricService);
    when(trunkManager.handleEgress(any(TrunkType.class), any(ProxySIPRequest.class), anyString()))
        .thenReturn(Mono.just(proxySIPResponse));
    when(proxySIPRequest.getAppRecord()).thenReturn(new DhruvaAppRecord());
  }

  @DataProvider
  public Object[] getCallTypes() {
    return new Object[] {
      new DialInPSTN(trunkManager, configurationProperty),
      new DialInB2B(trunkManager, configurationProperty),
      new DialOutWxC(trunkManager, configurationProperty),
      new DialOutB2B(trunkManager, configurationProperty)
    };
  }

  @Test(
      description = "all calltypes with ingress and egress key present",
      dataProvider = "getCallTypes")
  public void testProcessRequest(CallType callType) throws ParseException {
    if (callType instanceof DialOutB2B) {
      SIPRequest sipRequest = mock(SIPRequest.class);
      SipUri sipUri = (SipUri) JainSipHelper.createSipURI("sip:abc@akg.com;dtg=\"CcpFusionUS\"");
      when(sipRequest.getRequestURI()).thenReturn(sipUri);
      when(proxySIPRequest.getRequest()).thenReturn(sipRequest);
    }
    callType.processRequest(proxySIPRequest);
    verify(proxySIPResponse, times(1)).proxy();
    verify(trunkManager, times(1))
        .handleIngress(callType.getIngressTrunk(), proxySIPRequest, callType.getIngressKey());
    verify(trunkManager, times(1))
        .handleEgress(
            callType.getEgressTrunk(), proxySIPRequest, callType.getEgressKey(proxySIPRequest));

    verify(metricService, times(1)).sendTrunkMetric(callType.getIngressKey(), 0, null);
  }

  @Test(description = "dtg null DialOutB2B")
  public void nullDtgTest() throws ParseException {
    CallType callType = new DialOutB2B(trunkManager, configurationProperty);
    SIPRequest sipRequest = mock(SIPRequest.class);
    SipUri sipUri = (SipUri) JainSipHelper.createSipURI("sip:abc@akg.com");
    when(sipRequest.getRequestURI()).thenReturn(sipUri);
    when(proxySIPRequest.getRequest()).thenReturn(sipRequest);

    callType.processRequest(proxySIPRequest);

    verify(proxySIPRequest, times(1)).reject(Response.NOT_FOUND);
  }

  @Test(description = "handle egress throws exception")
  public void testHandleEgressException() {
    CallType callType = new DialInPSTN(trunkManager, configurationProperty);
    DhruvaRuntimeException dhruvaRuntimeException =
        new DhruvaRuntimeException(ErrorCode.APP_REQ_PROC, "Error while proxying the request");
    when(trunkManager.handleEgress(any(TrunkType.class), any(ProxySIPRequest.class), anyString()))
        .thenReturn(Mono.error(dhruvaRuntimeException));
    callType.processRequest(proxySIPRequest);
    verify(proxySIPRequest, times(1)).reject(dhruvaRuntimeException.getErrCode().getResponseCode());

    verify(metricService, times(1))
        .sendTrunkMetric(
            callType.getIngressKey(), dhruvaRuntimeException.getErrCode().getResponseCode(), null);
    reset(proxySIPRequest, metricService);

    when(trunkManager.handleEgress(any(TrunkType.class), any(ProxySIPRequest.class), anyString()))
        .thenReturn(Mono.error(new NullPointerException()));
    when(proxySIPRequest.getAppRecord()).thenReturn(new DhruvaAppRecord());
    callType.processRequest(proxySIPRequest);
    verify(proxySIPRequest, times(1)).reject(Response.SERVER_INTERNAL_ERROR);
    verify(metricService, times(1))
        .sendTrunkMetric(callType.getIngressKey(), Response.SERVER_INTERNAL_ERROR, null);
  }
}
