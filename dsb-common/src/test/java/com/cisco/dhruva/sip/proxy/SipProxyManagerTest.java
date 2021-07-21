package com.cisco.dhruva.sip.proxy;

import static org.mockito.Mockito.*;
import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertNull;

import com.cisco.dhruva.sip.RequestHelper;
import com.cisco.dhruva.sip.controller.ControllerConfig;
import com.cisco.dhruva.sip.controller.ProxyController;
import com.cisco.dsb.common.context.ExecutionContext;
import com.cisco.dsb.common.executor.DhruvaExecutorService;
import com.cisco.dsb.common.messaging.MessageConvertor;
import com.cisco.dsb.common.messaging.ProxySIPRequest;
import com.cisco.dsb.common.messaging.ProxySIPResponse;
import com.cisco.dsb.config.sip.DhruvaSIPConfigProperties;
import com.cisco.dsb.sip.jain.JainSipHelper;
import com.cisco.dsb.util.ResponseHelper;
import gov.nist.javax.sip.header.Unsupported;
import gov.nist.javax.sip.message.SIPRequest;
import java.util.List;
import javax.sip.ClientTransaction;
import javax.sip.ResponseEvent;
import javax.sip.ServerTransaction;
import javax.sip.SipProvider;
import javax.sip.address.SipURI;
import javax.sip.address.URI;
import javax.sip.header.MaxForwardsHeader;
import javax.sip.header.ProxyRequireHeader;
import javax.sip.header.UnsupportedHeader;
import javax.sip.message.Request;
import javax.sip.message.Response;
import org.mockito.*;
import org.testng.Assert;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.Test;

public class SipProxyManagerTest {

  @InjectMocks SipProxyManager sipProxyManager;
  @Mock ProxyFactory proxyFactory;
  @Mock ControllerConfig controllerConfig;
  @Mock DhruvaExecutorService dhruvaExecutorService;

  @BeforeClass
  public void init() {
    MockitoAnnotations.initMocks(this);
  }

  @Test(description = "test to find ProxyTransaction for incoming Response")
  public void findProxyTransactionTest() {
    ResponseEvent responseEvent = mock(ResponseEvent.class);
    when(responseEvent.getResponse()).thenReturn(ResponseHelper.getSipResponse());
    when(responseEvent.getSource()).thenReturn(mock(SipProvider.class));
    ClientTransaction ct = mock(ClientTransaction.class);
    ProxyTransaction pt = mock(ProxyTransaction.class);
    when(responseEvent.getClientTransaction()).thenReturn(ct);
    when(ct.getApplicationData()).thenReturn(pt);
    ProxySIPResponse proxySIPResponse = sipProxyManager.findProxyTransaction().apply(responseEvent);
    assertEquals(proxySIPResponse.getProxyTransaction(), pt);
  }

  @Test(description = "test to find ProxyTransaction for incoming Stray Response")
  public void findProxyTransactionStrayResponseTest() {
    ResponseEvent responseEvent = mock(ResponseEvent.class);
    when(responseEvent.getClientTransaction()).thenReturn(null);
    ProxySIPResponse proxySIPResponse = sipProxyManager.findProxyTransaction().apply(responseEvent);
    assertNull(proxySIPResponse);
  }

  @Test(description = "test to process the proxyTransaction for provisional response")
  public void processProxyTransactionProvisionalTest() {
    ProxyTransaction pt = mock(ProxyTransaction.class);
    ProxySIPResponse proxySIPResponse = mock(ProxySIPResponse.class);

    when(proxySIPResponse.getProxyTransaction()).thenReturn(pt);
    when(proxySIPResponse.getResponseClass()).thenReturn(1);

    sipProxyManager.processProxyTransaction().apply(proxySIPResponse);
    verify(pt, Mockito.times(1)).provisionalResponse(proxySIPResponse);
  }

  @Test(description = "test to process the proxyTransaction for final response")
  public void processProxyTransactionFinalTest() {
    ProxyTransaction pt = mock(ProxyTransaction.class);
    ProxySIPResponse proxySIPResponse = mock(ProxySIPResponse.class);

    when(proxySIPResponse.getProxyTransaction()).thenReturn(pt);
    when(proxySIPResponse.getResponseClass()).thenReturn(2);

    sipProxyManager.processProxyTransaction().apply(proxySIPResponse);
    verify(pt, Mockito.times(1)).finalResponse(proxySIPResponse);
  }

  @Test(
      description =
          "URI Scheme check passes if reqUri is unavailable"
              + "(or) scheme in a reqUri is unavailable"
              + "(or) scheme is sip/sips/tel [satisfied implicitly by other tests in this class]")
  public void passUriSchemeCheck() {

    SIPRequest req = mock(SIPRequest.class);
    SipProxyManager proxyManager = new SipProxyManager();

    when(req.getRequestURI()).thenReturn(null);
    Assert.assertFalse(proxyManager.uriSchemeCheckFailure.test(req));

    URI uri = mock(SipURI.class);
    when(req.getRequestURI()).thenReturn(uri);
    when(uri.getScheme()).thenReturn(null);
    Assert.assertFalse(proxyManager.uriSchemeCheckFailure.test(req));
  }

  @Test(
      description =
          "validate incoming request for right uri scheme, on check failure 416(Unsupported URI Scheme) error response should be generated")
  public void failUriSchemeCheck() throws Exception {

    ServerTransaction st = mock(ServerTransaction.class);
    SipProvider sp = mock(SipProvider.class);
    ProxyController proxyController =
        new ProxyController(
            st,
            sp,
            mock(DhruvaSIPConfigProperties.class),
            proxyFactory,
            controllerConfig,
            dhruvaExecutorService);
    ExecutionContext context = new ExecutionContext();

    Request request = RequestHelper.getDOInvite("abcd:shrihran@cisco.com");
    ProxySIPRequest proxyRequest =
        MessageConvertor.convertJainSipRequestMessageToDhruvaMessage(request, sp, st, context);
    proxyController.setController(proxyRequest);

    SipProxyManager proxyManager = new SipProxyManager();
    ArgumentCaptor<Response> captor = ArgumentCaptor.forClass(Response.class);

    doNothing().when(st).sendResponse(any(Response.class));
    Assert.assertNull(proxyManager.validateRequest.apply(proxyRequest));

    Thread.sleep(1000);
    verify(st, times(1)).sendResponse(captor.capture());
    Response response = captor.getValue();
    System.out.println("Error response: " + response);
    Assert.assertEquals(response.getStatusCode(), 416);
    Assert.assertEquals(response.getReasonPhrase(), "Unsupported URI Scheme");
  }

  @Test(
      description =
          "Max-Forwards check passes if the header is unavailable "
              + "(or) header is available with a value of 70 [satisfied implicitly by other tests in this class]"
              + "(or) header is available with a value of 0 for a REGISTER request")
  public void passMaxForwardsCheck() {

    SIPRequest req = mock(SIPRequest.class);
    SipProxyManager proxyManager = new SipProxyManager();

    when(req.getHeader(MaxForwardsHeader.NAME)).thenReturn(null);
    Assert.assertFalse(proxyManager.maxForwardsCheckFailure.test(req));

    MaxForwardsHeader mf = mock(MaxForwardsHeader.class);
    when(req.getHeader(MaxForwardsHeader.NAME)).thenReturn(mf);
    when(mf.getMaxForwards()).thenReturn(0);
    when(req.getMethod()).thenReturn(null);
    Assert.assertFalse(proxyManager.maxForwardsCheckFailure.test(req));

    when(req.getMethod()).thenReturn("REGISTER");
    Assert.assertFalse(proxyManager.maxForwardsCheckFailure.test(req));
  }

  @Test(
      description =
          "validate incoming request for max-forwards value, on check failure 483(Too many hops) error response should be generated")
  public void failMaxForwardsCheck() throws Exception {

    ServerTransaction st = mock(ServerTransaction.class);
    SipProvider sp = mock(SipProvider.class);
    ProxyController proxyController =
        new ProxyController(
            st,
            sp,
            mock(DhruvaSIPConfigProperties.class),
            proxyFactory,
            controllerConfig,
            dhruvaExecutorService);
    ExecutionContext context = new ExecutionContext();

    Request request = RequestHelper.getInviteRequest();
    MaxForwardsHeader mf = (MaxForwardsHeader) request.getHeader(MaxForwardsHeader.NAME);
    mf.setMaxForwards(0);
    ProxySIPRequest proxyRequest =
        MessageConvertor.convertJainSipRequestMessageToDhruvaMessage(request, sp, st, context);
    proxyController.setController(proxyRequest);

    SipProxyManager proxyManager = new SipProxyManager();
    ArgumentCaptor<Response> captor = ArgumentCaptor.forClass(Response.class);

    doNothing().when(st).sendResponse(any(Response.class));
    Assert.assertNull(proxyManager.validateRequest.apply(proxyRequest));

    Thread.sleep(1000);
    verify(st, times(1)).sendResponse(captor.capture());
    Response response = captor.getValue();
    System.out.println("Error response: " + response);
    Assert.assertEquals(response.getStatusCode(), 483);
    Assert.assertEquals(response.getReasonPhrase(), "Too many hops");
  }

  @Test(
      description =
          "Proxy-Require check passes if the header is unavailable "
              + "(or) header is available with supported features")
  public void passProxyRequireCheck() throws Exception {

    SipProxyManager proxyManager = new SipProxyManager();
    Request request = RequestHelper.getInviteRequest();

    Assert.assertNull(request.getHeader(ProxyRequireHeader.NAME));

    // header is absent
    List<Unsupported> unsup =
        proxyManager.proxyRequireHeaderCheckFailure.apply((SIPRequest) request);
    Assert.assertEquals(unsup.size(), 0);

    // supported features in proxy-Require header
    SupportedExtensions.addExtension("feature1");
    ProxyRequireHeader proxyRequire1 =
        JainSipHelper.getHeaderFactory().createProxyRequireHeader("feature1");
    request.addHeader(proxyRequire1);

    unsup = proxyManager.proxyRequireHeaderCheckFailure.apply((SIPRequest) request);
    Assert.assertEquals(unsup.size(), 0);

    SupportedExtensions.removeExtension("feature1");
  }

  @Test(
      description =
          "validate incoming request for proxy-require header, on check failure 420(Bad extension) error response should be generated")
  public void failProxyRequireCheck() throws Exception {

    ServerTransaction st = mock(ServerTransaction.class);
    SipProvider sp = mock(SipProvider.class);
    ProxyController proxyController =
        new ProxyController(
            st,
            sp,
            mock(DhruvaSIPConfigProperties.class),
            proxyFactory,
            controllerConfig,
            dhruvaExecutorService);
    ExecutionContext context = new ExecutionContext();

    SupportedExtensions.addExtension("feature1");

    Request request = RequestHelper.getInviteRequest();
    ProxyRequireHeader proxyRequire1 =
        JainSipHelper.getHeaderFactory().createProxyRequireHeader("feature1");
    request.addHeader(proxyRequire1);
    ProxyRequireHeader proxyRequire2 =
        JainSipHelper.getHeaderFactory().createProxyRequireHeader("feature2");
    request.addHeader(proxyRequire2);

    ProxySIPRequest proxyRequest =
        MessageConvertor.convertJainSipRequestMessageToDhruvaMessage(request, sp, st, context);
    proxyController.setController(proxyRequest);
    proxyRequest = spy(proxyRequest);
    SipProxyManager proxyManager = new SipProxyManager();
    ArgumentCaptor<Response> captor = ArgumentCaptor.forClass(Response.class);
    SipProvider sipProvider = mock(SipProvider.class);
    doNothing().when(sipProvider).sendResponse(any());
    when(proxyRequest.getProvider()).thenReturn(sipProvider);
    Assert.assertNull(proxyManager.validateRequest.apply(proxyRequest));

    Thread.sleep(1000);
    verify(sipProvider, times(1)).sendResponse(captor.capture());
    Response response = captor.getValue();
    System.out.println("Error response: " + response);
    Assert.assertEquals(response.getStatusCode(), 420);
    Assert.assertEquals(response.getReasonPhrase(), "Bad extension");
    UnsupportedHeader unsup = (UnsupportedHeader) response.getHeader(UnsupportedHeader.NAME);
    Assert.assertNotNull(unsup);
    Assert.assertEquals(unsup.getOptionTag(), "feature2");

    // Proxy supports all features now, so all request validation passes & request is sent to next
    // stages
    SupportedExtensions.addExtension("feature2");
    Assert.assertEquals(proxyManager.validateRequest.apply(proxyRequest), proxyRequest);

    SupportedExtensions.removeExtension("feature1");
    SupportedExtensions.removeExtension("feature2");
  }
}
