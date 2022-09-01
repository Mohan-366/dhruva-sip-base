package com.cisco.dhruva.application.calltype;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

import com.cisco.dhruva.application.CallTypeConfig;
import com.cisco.dhruva.application.CallingAppConfigurationProperty;
import com.cisco.dhruva.application.errormapping.ErrorMappingPolicy;
import com.cisco.dhruva.application.errormapping.Mappings;
import com.cisco.dhruva.normalisation.callTypeNormalization.DialInB2BToCallingCoreNorm;
import com.cisco.dhruva.normalisation.callTypeNormalization.DialInPSTNToB2BNorm;
import com.cisco.dhruva.normalisation.callTypeNormalization.DialOutB2BToPSTNNorm;
import com.cisco.dhruva.normalisation.callTypeNormalization.DialOutWXCToB2BNorm;
import com.cisco.dhruva.util.RequestHelper;
import com.cisco.dhruva.util.ResponseHelper;
import com.cisco.dsb.common.context.ExecutionContext;
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
import gov.nist.javax.sip.message.SIPResponse;
import java.text.ParseException;
import java.util.*;
import java.util.function.Function;
import javax.sip.ClientTransaction;
import javax.sip.SipProvider;
import javax.sip.message.Response;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.springframework.context.ApplicationContext;
import org.testng.Assert;
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
  Map<String, CallTypeConfig> callTypeConfigMap = new HashMap<>();
  ErrorMappingPolicy errorMappingPolicy = new ErrorMappingPolicy();
  List<Integer> errorCodes1 = new ArrayList<>();
  List<Integer> errorCodes2 = new ArrayList<>();
  private Map<Integer, Mappings> errorCodeToMappingMap = new HashMap<>();

  @BeforeTest
  public void init() {
    MockitoAnnotations.openMocks(this);

    metricService = mock(MetricService.class);
    errorMappingPolicy.setName("abc");
    List<Mappings> mappingsList = new ArrayList<>();
    errorCodes1.add(503);
    errorCodes1.add(404);
    errorCodes2.add(408);
    errorCodes2.add(500);
    mappingsList.add(
        Mappings.builder()
            .setMappedResponseCode(502)
            .setMappedResponsePhrase("damn")
            .setErrorCodes(errorCodes1)
            .build());
    mappingsList.add(
        Mappings.builder()
            .setMappedResponseCode(503)
            .setMappedResponsePhrase("damn")
            .setErrorCodes(errorCodes2)
            .build());
    errorMappingPolicy.setMappings(mappingsList);
    callTypeConfigMap.put(
        DialInPSTN.getCallTypeNameStr(), new CallTypeConfig("abc", errorMappingPolicy));
    mappingsList.forEach(
        (mapping) -> {
          for (Integer e : mapping.getErrorCodes()) {
            errorCodeToMappingMap.put(e, mapping);
          }
        });
    when(configurationProperty.getCallingEgress()).thenReturn("NS");
    when(configurationProperty.getPstnIngress()).thenReturn("CcpFusionUS");
    when(configurationProperty.getB2bEgress()).thenReturn("antares");
    when(configurationProperty.getCallTypesMap()).thenReturn(callTypeConfigMap);
  }

  @BeforeMethod
  public void setup() {

    reset(proxySIPRequest, proxySIPResponse, trunkManager, metricService);
    context = mock(ApplicationContext.class);
    when(context.getBean(MetricService.class)).thenReturn(metricService);

    springApplicationContext.setApplicationContext(context);
    when(context.getBean(MetricService.class)).thenReturn(metricService);
    when(trunkManager.handleEgress(
            any(TrunkType.class), any(ProxySIPRequest.class), anyString(), any()))
        .thenReturn(Mono.just(proxySIPResponse));
    when(proxySIPRequest.getAppRecord()).thenReturn(new DhruvaAppRecord());
  }

  @DataProvider
  public Object[] getCallTypes() {
    return new Object[] {
      new DialInPSTN(trunkManager, configurationProperty, new DialInPSTNToB2BNorm()),
      new DialInB2B(
          trunkManager,
          configurationProperty,
          new DialInB2BToCallingCoreNorm(configurationProperty)),
      new DialOutWxC(trunkManager, configurationProperty, new DialOutWXCToB2BNorm()),
      new DialOutB2B(
          trunkManager, configurationProperty, new DialOutB2BToPSTNNorm(configurationProperty))
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
        .handleIngress(
            callType.getIngressTrunk(),
            proxySIPRequest,
            callType.getIngressKey(),
            callType.getNormalization());
    verify(trunkManager, times(1))
        .handleEgress(
            callType.getEgressTrunk(),
            proxySIPRequest,
            callType.getEgressKey(proxySIPRequest),
            callType.getNormalization());

    verify(metricService, times(1)).sendTrunkMetric(callType.getIngressKey(), 0, null);
  }

  @Test(description = "dtg null DialOutB2B")
  public void nullDtgTest() throws ParseException {
    CallType callType =
        new DialOutB2B(
            trunkManager, configurationProperty, new DialOutB2BToPSTNNorm(configurationProperty));
    SIPRequest sipRequest = mock(SIPRequest.class);
    SipUri sipUri = (SipUri) JainSipHelper.createSipURI("sip:abc@akg.com");
    when(sipRequest.getRequestURI()).thenReturn(sipUri);
    when(proxySIPRequest.getRequest()).thenReturn(sipRequest);

    callType.processRequest(proxySIPRequest);

    verify(proxySIPRequest, times(1)).reject(Response.NOT_FOUND);
  }

  @Test(description = "handle egress throws exception")
  public void testHandleEgressException() {
    DialInPSTNToB2BNorm normalization = new DialInPSTNToB2BNorm();
    CallType callType = new DialInPSTN(trunkManager, configurationProperty, normalization);
    DhruvaRuntimeException dhruvaRuntimeException =
        new DhruvaRuntimeException(ErrorCode.APP_REQ_PROC, "Error while proxying the request");
    when(trunkManager.handleEgress(
            any(TrunkType.class), any(ProxySIPRequest.class), anyString(), any()))
        .thenReturn(Mono.error(dhruvaRuntimeException));
    callType.processRequest(proxySIPRequest);
    verify(proxySIPRequest, times(1)).reject(dhruvaRuntimeException.getErrCode().getResponseCode());

    verify(metricService, times(1))
        .sendTrunkMetric(
            callType.getIngressKey(), dhruvaRuntimeException.getErrCode().getResponseCode(), null);
    reset(proxySIPRequest, metricService);

    when(trunkManager.handleEgress(
            any(TrunkType.class), any(ProxySIPRequest.class), anyString(), any()))
        .thenReturn(Mono.error(new NullPointerException()));
    when(proxySIPRequest.getAppRecord()).thenReturn(new DhruvaAppRecord());
    callType.processRequest(proxySIPRequest);
    verify(proxySIPRequest, times(1)).reject(Response.SERVER_INTERNAL_ERROR);
    verify(metricService, times(1))
        .sendTrunkMetric(callType.getIngressKey(), Response.SERVER_INTERNAL_ERROR, null);
  }

  @Test(
      description =
          "test out the error mapping for responses functionality, only dialInPSTN is included")
  public void testResponseMapperDialInPSTN() throws ParseException {
    DialInPSTNToB2BNorm normalization = new DialInPSTNToB2BNorm();
    DialInPSTN callType = new DialInPSTN(trunkManager, configurationProperty, normalization);
    callType.setCallTypeConfig(callTypeConfigMap.get(DialInPSTN.getCallTypeNameStr()));
    callType.setErrorMappingPolicy(errorMappingPolicy);
    callType.setErrorCodeToMappingMap(errorCodeToMappingMap);

    Function<ProxySIPResponse, ProxySIPResponse> responseFunction = callType.getResponseMapper();
    when(proxySIPResponse.getStatusCode()).thenReturn(502);

    SIPRequest sipRequest = (SIPRequest) RequestHelper.getInviteRequest();
    SIPResponse sipResponse = ResponseHelper.getSipResponse(503, sipRequest);
    ExecutionContext executionContext = new ExecutionContext();
    ClientTransaction clientTransaction = mock(ClientTransaction.class);
    SipProvider sipProvider = mock(SipProvider.class);

    // Case 1
    // Response code 503 matches from first element in list
    // 503 maps to 502
    ProxySIPResponse proxySIPResponse1 =
        new ProxySIPResponse(executionContext, sipProvider, sipResponse, clientTransaction);
    ProxySIPResponse mappedResponse = responseFunction.apply(proxySIPResponse1);
    Assert.assertEquals(mappedResponse.getStatusCode(), 502);

    // Case 2
    // No mapping for error code found i.e 600, hence no change in response
    SIPResponse sipResponse1 = ResponseHelper.getSipResponse(600, sipRequest);
    proxySIPResponse1 =
        new ProxySIPResponse(executionContext, sipProvider, sipResponse1, clientTransaction);
    mappedResponse = responseFunction.apply(proxySIPResponse1);
    Assert.assertEquals(mappedResponse.getStatusCode(), 600);

    // Case 3
    // 408 matches from second element in list
    // 408 must map to mapped response 503 that is set.
    SIPResponse sipResponse2 = ResponseHelper.getSipResponse(408, sipRequest);
    proxySIPResponse1 =
        new ProxySIPResponse(executionContext, sipProvider, sipResponse2, clientTransaction);
    mappedResponse = responseFunction.apply(proxySIPResponse1);
    Assert.assertEquals(mappedResponse.getStatusCode(), 503);
  }

  @Test(
      description =
          "Response Mapper should not be applied for this callType since we have not configured it with error mapping policy")
  public void testResponseMapperDialOutWxC() throws ParseException {
    // Check for DialOutWxC call type where we have not set config and error mapping policy
    // There should be no change in response

    SIPRequest sipRequest = (SIPRequest) RequestHelper.getInviteRequest();
    ExecutionContext executionContext = new ExecutionContext();
    ClientTransaction clientTransaction = mock(ClientTransaction.class);
    SipProvider sipProvider = mock(SipProvider.class);

    DialOutWXCToB2BNorm dialOutWXCToB2BNorm = new DialOutWXCToB2BNorm();
    DialOutWxC dialOutWxC =
        new DialOutWxC(trunkManager, configurationProperty, dialOutWXCToB2BNorm);
    SIPResponse dialOutWxCSipResponse = ResponseHelper.getSipResponse(503, sipRequest);
    ProxySIPResponse dialOutWxCProxySipResponse =
        new ProxySIPResponse(
            executionContext, sipProvider, dialOutWxCSipResponse, clientTransaction);
    Function<ProxySIPResponse, ProxySIPResponse> dialOutWxCResponseFunction =
        dialOutWxC.getResponseMapper();
    ProxySIPResponse dialOutWxCMappedResponse =
        dialOutWxCResponseFunction.apply(dialOutWxCProxySipResponse);
    Assert.assertEquals(dialOutWxCMappedResponse.getStatusCode(), 503);
  }
}
