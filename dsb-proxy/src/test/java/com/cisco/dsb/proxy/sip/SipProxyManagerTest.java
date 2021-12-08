package com.cisco.dsb.proxy.sip;

import static org.mockito.Mockito.*;
import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertNull;

import com.cisco.dsb.common.CallType;
import com.cisco.dsb.common.context.ExecutionContext;
import com.cisco.dsb.common.executor.DhruvaExecutorService;
import com.cisco.dsb.common.service.MetricService;
import com.cisco.dsb.common.sip.jain.JainSipHelper;
import com.cisco.dsb.common.sip.stack.dto.DhruvaNetwork;
import com.cisco.dsb.common.sip.util.SupportedExtensions;
import com.cisco.dsb.common.transport.Transport;
import com.cisco.dsb.common.util.TriFunction;
import com.cisco.dsb.proxy.controller.ControllerConfig;
import com.cisco.dsb.proxy.controller.ProxyController;
import com.cisco.dsb.proxy.controller.ProxyControllerFactory;
import com.cisco.dsb.proxy.dto.ProxyAppConfig;
import com.cisco.dsb.proxy.messaging.DhruvaSipRequestMessage;
import com.cisco.dsb.proxy.messaging.ProxySIPRequest;
import com.cisco.dsb.proxy.messaging.ProxySIPResponse;
import com.cisco.dsb.proxy.util.RequestHelper;
import com.cisco.dsb.proxy.util.ResponseHelper;
import com.cisco.dsb.proxy.util.SIPRequestBuilder;
import gov.nist.javax.sip.SipProviderImpl;
import gov.nist.javax.sip.header.*;
import gov.nist.javax.sip.message.SIPRequest;
import gov.nist.javax.sip.message.SIPResponse;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.text.ParseException;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import javax.sip.*;
import javax.sip.address.SipURI;
import javax.sip.address.URI;
import javax.sip.header.*;
import javax.sip.message.Request;
import javax.sip.message.Response;
import org.mockito.*;
import org.testng.Assert;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.DataProvider;
import org.testng.annotations.Test;

public class SipProxyManagerTest {

  @InjectMocks SipProxyManager sipProxyManager;
  @Mock ProxyControllerFactory proxyControllerFactory;
  @Mock ControllerConfig controllerConfig;
  @Mock DhruvaExecutorService dhruvaExecutorService;
  @Mock MetricService metricService;

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
  public void findProxyTransactionStrayResponseTest() throws ParseException, SipException {
    ResponseEvent responseEvent = mock(ResponseEvent.class);
    when(responseEvent.getClientTransaction()).thenReturn(null);
    SIPResponse sipResponse = mock(SIPResponse.class);

    // Test: Topmost via is null
    when(responseEvent.getResponse()).thenReturn(sipResponse);
    when(sipResponse.getStatusCode()).thenReturn(Response.OK);
    ProxySIPResponse proxySIPResponse = sipProxyManager.findProxyTransaction().apply(responseEvent);
    assertNull(proxySIPResponse);
    verify(sipResponse, Mockito.times(1)).getTopmostViaHeader();
    reset(sipResponse);

    // Test: Top via with no Branch
    ViaList viaList = new ViaList();
    Via via1 = mock(Via.class);
    viaList.addFirst(via1);
    when(sipResponse.getTopmostViaHeader()).thenReturn((Via) viaList.getFirst());
    when(sipResponse.getStatusCode()).thenReturn(Response.OK);
    proxySIPResponse = sipProxyManager.findProxyTransaction().apply(responseEvent);
    verify(sipResponse, Mockito.times(1)).getTopmostViaHeader();
    verify(via1, Mockito.times(1)).getBranch();
    assertNull(proxySIPResponse);
    reset(sipResponse, via1);

    // Test: Top via with branch but does not match any listenIf
    when(sipResponse.getTopmostViaHeader()).thenReturn((Via) viaList.getFirst());
    when(sipResponse.getStatusCode()).thenReturn(Response.OK);
    when(via1.getBranch()).thenReturn("testbranch");
    when(via1.getHost()).thenReturn("testHost");
    when(via1.getPort()).thenReturn(5060);
    when(via1.getTransport()).thenReturn(Transport.TCP.name());
    when(controllerConfig.recognize(any(String.class), eq(5060), any(Transport.class)))
        .thenReturn(false);
    proxySIPResponse = sipProxyManager.findProxyTransaction().apply(responseEvent);
    assertNull(proxySIPResponse);
    verify(sipResponse, Mockito.times(0)).removeFirst(eq(ViaHeader.NAME));
    reset(sipResponse);

    // Test: Top via with matching listenIf but no Via left after top via removal
    when(sipResponse.getTopmostViaHeader()).thenReturn((Via) viaList.getFirst());
    when(sipResponse.getStatusCode()).thenReturn(Response.OK);
    when(controllerConfig.recognize(any(String.class), eq(5060), any(Transport.class)))
        .thenReturn(true);
    doAnswer(
            invocationOnMock -> {
              when(sipResponse.getTopmostViaHeader()).thenReturn(null);
              return null;
            })
        .when(sipResponse)
        .removeFirst(eq(ViaHeader.NAME));
    proxySIPResponse = sipProxyManager.findProxyTransaction().apply(responseEvent);
    assertNull(proxySIPResponse);
    verify(sipResponse, Mockito.times(1)).removeFirst(eq(ViaHeader.NAME));
    verify(controllerConfig, Mockito.times(0)).doRecordRoute();
    reset(sipResponse, controllerConfig);

    // Test: Valid ViaList but RR does not contain outbound network to send out the response
    when(sipResponse.getTopmostViaHeader()).thenReturn((Via) viaList.getFirst());
    when(sipResponse.getStatusCode()).thenReturn(Response.OK);
    when(controllerConfig.recognize(any(String.class), eq(5060), any(Transport.class)))
        .thenReturn(true);
    doAnswer(
            invocationOnMock -> {
              when(sipResponse.getTopmostViaHeader()).thenReturn(via1);
              return null;
            })
        .when(sipResponse)
        .removeFirst(eq(ViaHeader.NAME));
    when(controllerConfig.doRecordRoute()).thenReturn(true);
    proxySIPResponse = sipProxyManager.findProxyTransaction().apply(responseEvent);
    verify(controllerConfig, Mockito.times(1))
        .setRecordRouteInterface(eq(sipResponse), eq(true), eq(-1));
    // Can't assert properly because DhruvaNetwork.getProviderFromNetwork is static method
    assertNull(proxySIPResponse);
    reset(controllerConfig);

    // Test: Valid ViaList, RR does contain outbound network but network not present in listenIf
    when(controllerConfig.recognize(any(String.class), eq(5060), any(Transport.class)))
        .thenReturn(true);
    when(sipResponse.getStatusCode()).thenReturn(Response.OK);
    doAnswer(
            invocationOnMock -> {
              when(sipResponse.getTopmostViaHeader()).thenReturn(via1);
              return null;
            })
        .when(sipResponse)
        .removeFirst(eq(ViaHeader.NAME));
    when(controllerConfig.doRecordRoute()).thenReturn(true);
    when(sipResponse.getApplicationData()).thenReturn("not_our_network");
    SipProviderImpl sipProvider = mock(SipProviderImpl.class);
    DhruvaNetwork.setSipProvider("test_out_network", sipProvider);
    proxySIPResponse = sipProxyManager.findProxyTransaction().apply(responseEvent);
    assertNull(proxySIPResponse);
    verify(sipProvider, Mockito.times(0)).sendResponse(sipResponse);

    reset(sipProvider);

    // Test: Valid ViaList, RR does contain outbound network and network a valid listenIf
    when(sipResponse.getApplicationData()).thenReturn("test_out_network");
    when(sipResponse.getStatusCode()).thenReturn(Response.OK);
    proxySIPResponse = sipProxyManager.findProxyTransaction().apply(responseEvent);
    assertNull(proxySIPResponse);
    verify(sipProvider, Mockito.times(1)).sendResponse(sipResponse);
    // verify previous testcase also here, i.e invalid network in RR
    verify(controllerConfig, Mockito.times(2))
        .setRecordRouteInterface(eq(sipResponse), eq(true), eq(-1));
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

  @DataProvider
  public Object[] getServerTransaction() {
    return new ServerTransaction[][] {{null}, {mock(ServerTransaction.class)}};
  }

  @Test(
      dataProvider = "getServerTransaction",
      description =
          "Do not create server transaction, if it already exists (or) if it is ACK request(no separate transaction is created for this)")
  public void testNoServerTransactionCreation(ServerTransaction serverTransaction)
      throws TransactionAlreadyExistsException, TransactionUnavailableException {

    RequestEvent requestEvent = mock(RequestEvent.class);
    SIPRequest request = mock(SIPRequest.class);
    SipProvider sp = mock(SipProvider.class);
    ProxySIPRequest proxySipRequest = mock(ProxySIPRequest.class);

    if (serverTransaction == null) {
      when(request.getMethod()).thenReturn(Request.ACK);
    }

    when(requestEvent.getRequest()).thenReturn(request);
    when(requestEvent.getServerTransaction()).thenReturn(serverTransaction);
    when(requestEvent.getSource()).thenReturn(sp);

    when(controllerConfig.isStateful()).thenReturn(true);
    TriFunction<Request, ServerTransaction, SipProvider, Request> mockSendProvisional =
        (rq, stx, spd) -> rq;
    TriFunction<Request, ServerTransaction, SipProvider, ProxySIPRequest> mockCreateProxyRequest =
        (rq, stx, spd) -> proxySipRequest;
    SipProxyManager spySipProxyManager = Mockito.spy(sipProxyManager);

    when(spySipProxyManager.sendProvisionalResponse()).thenReturn(mockSendProvisional);
    when(spySipProxyManager.createProxySipRequest()).thenReturn(mockCreateProxyRequest);

    Assert.assertEquals(
        spySipProxyManager.createServerTransactionAndProxySIPRequest().apply(requestEvent),
        proxySipRequest);
    verify(sp, times(0)).getNewServerTransaction(request);
  }

  @Test(
      description =
          "Create a new server transaction for fresh requests(like INVITE,BYE,etc) except ACK")
  public void testNewServerTransactionCreation()
      throws TransactionAlreadyExistsException, TransactionUnavailableException {
    RequestEvent requestEvent = mock(RequestEvent.class);
    SIPRequest request = mock(SIPRequest.class);
    SipProvider sp = mock(SipProvider.class);
    ProxySIPRequest proxySipRequest = mock(ProxySIPRequest.class);
    ServerTransaction st = mock(ServerTransaction.class);

    when(requestEvent.getRequest()).thenReturn(request);
    when(requestEvent.getServerTransaction()).thenReturn(null);
    when(requestEvent.getSource()).thenReturn(sp);

    when(controllerConfig.isStateful()).thenReturn(true);
    when(request.getMethod()).thenReturn(Request.INVITE);

    TriFunction<Request, ServerTransaction, SipProvider, Request> mockSendProvisional =
        (rq, stx, spd) -> rq;
    TriFunction<Request, ServerTransaction, SipProvider, ProxySIPRequest> mockCreateProxyRequest =
        (rq, stx, spd) -> proxySipRequest;
    SipProxyManager spySipProxyManager = Mockito.spy(sipProxyManager);

    when(sp.getNewServerTransaction(request)).thenReturn(st);
    when(spySipProxyManager.sendProvisionalResponse()).thenReturn(mockSendProvisional);
    when(spySipProxyManager.createProxySipRequest()).thenReturn(mockCreateProxyRequest);

    Assert.assertEquals(
        spySipProxyManager.createServerTransactionAndProxySIPRequest().apply(requestEvent),
        proxySipRequest);
    verify(sp, times(1)).getNewServerTransaction(request);
  }

  @DataProvider
  public Object[] getMethod() {
    return new String[][] {{Request.ACK}, {Request.INVITE}};
  }

  @Test(dataProvider = "getMethod", description = "Send 100 trying response only for INVITE")
  public void testSendingProvisionalResponse(String requestType)
      throws SipException, InvalidArgumentException {

    SIPRequest request = mock(SIPRequest.class);
    SipProvider sp = mock(SipProvider.class);
    ServerTransaction st = mock(ServerTransaction.class);
    SIPResponse sipResponse = mock(SIPResponse.class);

    SipProxyManager spySipProxyManager = Mockito.spy(sipProxyManager);
    ArgumentCaptor<Response> captor = ArgumentCaptor.forClass(Response.class);

    when(request.getMethod()).thenReturn(requestType);
    when(request.createResponse(100)).thenReturn(sipResponse);
    doNothing().when(st).sendResponse(any(Response.class));

    assertEquals(spySipProxyManager.sendProvisionalResponse().apply(request, st, sp), request);
    if (requestType.equals(Request.INVITE)) {
      verify(st, times(1)).sendResponse(any(Response.class));
    } else {
      verify(st, times(0)).sendResponse(any(Response.class));
    }
  }

  @Test(description = "Creates a new ProxyController for a proxy SIP Request")
  public void testProxyControllerCreation() {
    ProxySIPRequest proxySIPRequest = mock(ProxySIPRequest.class);
    SIPRequest request = mock(SIPRequest.class);
    ProxyController proxyController = mock(ProxyController.class);

    when(proxySIPRequest.getRequest()).thenReturn(request);
    when(proxySIPRequest.getServerTransaction()).thenReturn(null);
    TriFunction<ServerTransaction, SipProvider, ProxyAppConfig, ProxyController>
        mockProxyController = (stx, spd, pc) -> proxyController;
    when(proxyControllerFactory.proxyController()).thenReturn(mockProxyController);
    when(proxyController.onNewRequest(proxySIPRequest)).thenReturn(proxySIPRequest);

    Assert.assertEquals(
        sipProxyManager.getProxyController(mock(ProxyAppConfig.class)).apply(proxySIPRequest),
        proxySIPRequest);
    verify(proxySIPRequest).setProxyInterface(proxyController);
    verify(proxyController).onNewRequest(proxySIPRequest);
  }

  @Test(
      description =
          "No new ProxyController is created for a proxy SIP Request, if it already exists (eg: ACK for a 4xx has this scenario)")
  public void testProxyControllerAlreadyExists() {
    ProxySIPRequest proxySIPRequest = mock(ProxySIPRequest.class);
    SIPRequest request = mock(SIPRequest.class);
    ServerTransaction serverTransaction = mock(ServerTransaction.class);
    ProxyTransaction proxyTransaction = mock(ProxyTransaction.class);
    ProxyController proxyController = mock(ProxyController.class);

    when(proxySIPRequest.getRequest()).thenReturn(request);
    when(request.getMethod()).thenReturn(Request.ACK);
    when(proxySIPRequest.getServerTransaction()).thenReturn(serverTransaction);
    when(serverTransaction.getApplicationData()).thenReturn(proxyTransaction);
    when(proxyTransaction.getController()).thenReturn(proxyController);

    Assert.assertNull(
        sipProxyManager.getProxyController(mock(ProxyAppConfig.class)).apply(proxySIPRequest));
    verify(proxyController).onAck(proxyTransaction);
  }

  @Test(
      description =
          "URI Scheme check passes if reqUri is unavailable"
              + "(or) scheme in a reqUri is unavailable"
              + "(or) scheme is sip/sips/tel [satisfied implicitly by other tests in this class]")
  public void passUriSchemeCheck() {

    SIPRequest req = mock(SIPRequest.class);

    when(req.getRequestURI()).thenReturn(null);
    Assert.assertFalse(sipProxyManager.uriSchemeCheckFailure.test(req));

    URI uri = mock(SipURI.class);
    when(req.getRequestURI()).thenReturn(uri);
    when(uri.getScheme()).thenReturn(null);
    Assert.assertFalse(sipProxyManager.uriSchemeCheckFailure.test(req));
  }

  @Test(
      description =
          "validate incoming request for right uri scheme, on check failure 416(Unsupported URI Scheme) error response should be generated")
  public void failUriSchemeCheck() throws Exception {

    ServerTransaction st = mock(ServerTransaction.class);
    SipProvider sp = mock(SipProvider.class);

    SipProxyManager proxyManager =
        new SipProxyManager(proxyControllerFactory, controllerConfig, metricService);

    Request request = RequestHelper.getDOInvite("abcd:shrihran@cisco.com");
    ProxySIPRequest proxyRequest = proxyManager.createProxySipRequest().apply(request, st, sp);

    ArgumentCaptor<Response> captor = ArgumentCaptor.forClass(Response.class);

    doNothing().when(st).sendResponse(any(Response.class));
    Assert.assertNull(proxyManager.validateRequest().apply(proxyRequest));

    verify(st, times(1)).sendResponse(captor.capture());
    Response response = captor.getValue();
    System.out.println("Error response: " + response);
    Assert.assertEquals(response.getStatusCode(), 416);
    Assert.assertEquals(response.getReasonPhrase(), "Unsupported URI Scheme");
  }

  @Test(
      description =
          "Max-Forwards check passes if the header is unavailable "
              + "(or) header is available with a value of 70 and this should be decremented"
              + "(or) header is available with a value of 0 for a REGISTER request")
  public void passMaxForwardsCheck() throws InvalidArgumentException {

    SIPRequest req = mock(SIPRequest.class);
    SipProxyManager proxyManager =
        new SipProxyManager(proxyControllerFactory, controllerConfig, metricService);

    when(req.getHeader(MaxForwardsHeader.NAME)).thenReturn(null);
    Assert.assertFalse(proxyManager.maxForwardsCheckFailure.test(req));

    MaxForwardsHeader mf = new MaxForwards(70);
    when(req.getHeader(MaxForwardsHeader.NAME)).thenReturn(mf);
    when(req.getMethod()).thenReturn(Request.INVITE);
    Assert.assertFalse(proxyManager.maxForwardsCheckFailure.test(req));
    Assert.assertEquals(mf.getMaxForwards(), 69);

    mf.setMaxForwards(0);
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

    Request request = RequestHelper.getInviteRequest();
    MaxForwardsHeader mf = (MaxForwardsHeader) request.getHeader(MaxForwardsHeader.NAME);
    mf.setMaxForwards(0);

    SipProxyManager proxyManager =
        new SipProxyManager(proxyControllerFactory, controllerConfig, metricService);
    ProxySIPRequest proxyRequest = proxyManager.createProxySipRequest().apply(request, st, sp);

    ArgumentCaptor<Response> captor = ArgumentCaptor.forClass(Response.class);

    doNothing().when(st).sendResponse(any(Response.class));
    Assert.assertNull(proxyManager.validateRequest().apply(proxyRequest));

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

    SipProxyManager proxyManager =
        new SipProxyManager(proxyControllerFactory, controllerConfig, metricService);
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

    SupportedExtensions.addExtension("feature1");

    Request request = RequestHelper.getInviteRequest();
    ProxyRequireHeader proxyRequire1 =
        JainSipHelper.getHeaderFactory().createProxyRequireHeader("feature1");
    request.addHeader(proxyRequire1);
    ProxyRequireHeader proxyRequire2 =
        JainSipHelper.getHeaderFactory().createProxyRequireHeader("feature2");
    request.addHeader(proxyRequire2);

    SipProxyManager proxyManager =
        new SipProxyManager(proxyControllerFactory, controllerConfig, metricService);
    ProxySIPRequest proxyRequest = proxyManager.createProxySipRequest().apply(request, st, sp);

    ArgumentCaptor<Response> captor = ArgumentCaptor.forClass(Response.class);

    doNothing().when(st).sendResponse(any(Response.class));
    Assert.assertNull(proxyManager.validateRequest().apply(proxyRequest));

    verify(st, times(1)).sendResponse(captor.capture());
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
    Assert.assertEquals(proxyManager.validateRequest().apply(proxyRequest), proxyRequest);

    SupportedExtensions.removeExtension("feature1");
    SupportedExtensions.removeExtension("feature2");
  }

  @Test(
      description = "test to process the proxyTransaction for timeout event in client transaction")
  public void processProxyTransactionClientTimeoutTest() {
    TimeoutEvent timeoutEvent = mock(TimeoutEvent.class);
    SipProvider sipProvider = mock(SipProvider.class);
    ClientTransaction ct = mock(ClientTransaction.class);
    ProxyTransaction pt = mock(ProxyTransaction.class);
    ProxySIPResponse proxySIPResponse = mock(ProxySIPResponse.class);

    when(timeoutEvent.isServerTransaction()).thenReturn(false);
    when(timeoutEvent.getSource()).thenReturn(sipProvider);
    when(timeoutEvent.getClientTransaction()).thenReturn(ct);
    when(ct.getApplicationData()).thenReturn(pt);
    when(proxySIPResponse.isToApplication()).thenReturn(true);
    ProxySIPResponse response = sipProxyManager.handleProxyTimeoutEvent().apply(timeoutEvent);

    Assert.assertNull(response);
    verify(pt, Mockito.times(1)).timeOut(ct, sipProvider);
  }

  @Test(
      description = "test to process the proxyTransaction for timeout event in server transaction")
  public void processProxyTransactionServerTimeoutTest() {
    TimeoutEvent timeoutEvent = mock(TimeoutEvent.class);
    ServerTransaction st = mock(ServerTransaction.class);
    ProxyTransaction pt = mock(ProxyTransaction.class);

    when(timeoutEvent.isServerTransaction()).thenReturn(true);
    when(timeoutEvent.getServerTransaction()).thenReturn(st);
    when(st.getApplicationData()).thenReturn(pt);

    ProxySIPResponse proxySIPResponse =
        sipProxyManager.handleProxyTimeoutEvent().apply(timeoutEvent);
    Assert.assertNull(proxySIPResponse);

    verify(pt, Mockito.times(1)).timeOut(st);
  }

  @Test(description = "app interested in mid-dialog")
  public void processProxyAppControllerTest() throws ParseException {

    ServerTransaction serverTransaction = mock(ServerTransaction.class);

    ProxySIPRequest proxySIPRequest =
        getProxySipRequest(SIPRequestBuilder.RequestMethod.INVITE, serverTransaction);

    ProxySIPRequest proxySIPRequest1 =
        sipProxyManager.proxyAppController(false).apply(proxySIPRequest);

    Assert.assertNotNull(proxySIPRequest1);
    Assert.assertEquals(proxySIPRequest, proxySIPRequest1);

    ProxySIPRequest proxySIPRequestAck =
        getProxySipRequest(SIPRequestBuilder.RequestMethod.ACK, serverTransaction);
    SIPRequest ack = proxySIPRequestAck.getRequest();
    RouteHeader ownRouteHeader =
        JainSipHelper.createRouteHeader("rr$n=net_internal_udp", "1.1.1.1", 5060, "udp");

    // Add to tag and route to qualify for mid dialog
    ack.addHeader(ownRouteHeader);
    ack.setToTag("testackmiddialog");

    // Mock proxy interface
    ProxyInterface proxyInterface = mock(ProxyInterface.class);
    proxySIPRequestAck.setProxyInterface(proxyInterface);
    doNothing().when(proxyInterface).sendRequestToApp(any(boolean.class));

    // App is interested in mid dialog messages
    ProxySIPRequest proxySIPRequestAckRetVal =
        sipProxyManager.proxyAppController(true).apply(proxySIPRequestAck);

    Assert.assertNotNull(proxySIPRequestAckRetVal);
    Assert.assertEquals(proxySIPRequestAckRetVal, proxySIPRequestAck);
  }

  @Test(description = "route mid-dialog based on route header ACK")
  public void testMidDialogAck() {

    ServerTransaction serverTransaction = mock(ServerTransaction.class);
    ProxySIPRequest proxySIPRequestAck =
        getProxySipRequest(SIPRequestBuilder.RequestMethod.ACK, serverTransaction);
    SIPRequest ack = proxySIPRequestAck.getRequest();
    ack.setToTag("testackmiddialog");
    ProxyInterface proxyInterface = mock(ProxyInterface.class);
    when(proxyInterface.proxyRequest(proxySIPRequestAck))
        .thenReturn(CompletableFuture.completedFuture(null));
    proxySIPRequestAck.setProxyInterface(proxyInterface);
    proxySIPRequestAck.setOutgoingNetwork(
        "net_internal_udp_spmtest"); // this will be set by controller.onNewRequest
    ProxySIPRequest proxySIPRequestRetVal =
        sipProxyManager.proxyAppController(false).apply(proxySIPRequestAck);

    verify(proxyInterface).proxyRequest(eq(proxySIPRequestAck));
    assertNull(proxySIPRequestRetVal);
  }

  @Test(description = "route mid-dialog based on route header Bye")
  public void testMidDialogBye() throws InterruptedException {
    ServerTransaction serverTransaction = mock(ServerTransaction.class);
    ProxySIPRequest proxySIPRequestBye =
        getProxySipRequest(SIPRequestBuilder.RequestMethod.BYE, serverTransaction);
    ProxySIPResponse proxySIPResponse_200 = mock(ProxySIPResponse.class);
    SIPRequest bye = proxySIPRequestBye.getRequest();
    bye.setToTag("testackmiddialog");
    ProxyInterface proxyInterface = mock(ProxyInterface.class);
    when(proxyInterface.proxyRequest(proxySIPRequestBye))
        .thenReturn(CompletableFuture.completedFuture(proxySIPResponse_200));
    proxySIPRequestBye.setProxyInterface(proxyInterface);
    proxySIPRequestBye.setOutgoingNetwork(
        "net_internal_udp_spmtest"); // this will be set by controller.onNewRequest
    ProxySIPRequest proxySIPRequestRetVal =
        sipProxyManager.proxyAppController(false).apply(proxySIPRequestBye);
    Thread.sleep(50);
    verify(proxyInterface).proxyRequest(eq(proxySIPRequestBye));
    assertNull(proxySIPRequestRetVal);
    verify(proxySIPResponse_200, times(1)).proxy();
  }

  @Test(
      description =
          "test LMA stage in both request and response handling pipeline.This will change, "
              + "now just to increase coverage :)")
  public void logAndMetricsPipelineTest() throws UnknownHostException {

    // Request path tests
    ServerTransaction serverTransaction = mock(ServerTransaction.class);
    ProxySIPRequest proxySIPRequest =
        getProxySipRequest(SIPRequestBuilder.RequestMethod.INVITE, serverTransaction);

    SIPRequest request = proxySIPRequest.getRequest();
    request.setLocalAddress(InetAddress.getByName("127.0.0.1"));
    request.setRemoteAddress(InetAddress.getByName("127.0.0.1"));

    request.setLocalPort(9000);
    request.setRemotePort(8000);
    SipProvider sipProvider = mock(SipProvider.class);

    ListeningPoint[] lps = new ListeningPoint[1];
    lps[0] = getTestListeningPoint();

    when(sipProvider.getListeningPoints()).thenReturn(lps);

    RequestEvent requestEvent =
        new RequestEvent(sipProvider, serverTransaction, mock(Dialog.class), request);

    sipProxyManager.manageLogAndMetricsForRequest.accept(requestEvent);

    // Response path tests
    SIPResponse sipResponse = ResponseHelper.getSipResponse();
    sipResponse.setLocalAddress(InetAddress.getByName("127.0.0.1"));
    sipResponse.setRemoteAddress(InetAddress.getByName("127.0.0.1"));
    sipResponse.setLocalPort(9000);
    sipResponse.setRemotePort(40000);
    ResponseEvent responseEvent =
        new ResponseEvent(
            sipProvider, mock(ClientTransaction.class), mock(Dialog.class), sipResponse);
    sipProxyManager.manageLogAndMetricsForResponse.accept(responseEvent);
  }

  public ProxySIPRequest getProxySipRequest(
      SIPRequestBuilder.RequestMethod method, ServerTransaction serverTransaction) {
    try {
      ExecutionContext context = new ExecutionContext();
      SIPRequest request =
          SIPRequestBuilder.createRequest(new SIPRequestBuilder().getRequestAsString(method));

      return DhruvaSipRequestMessage.newBuilder()
          .withContext(context)
          .withPayload(request)
          .withTransaction(serverTransaction)
          .callType(CallType.SIP)
          .correlationId("ABCD")
          .reqURI("sip:test@webex.com")
          .sessionId("testSession")
          .network("default")
          .withProvider(mock(SipProvider.class))
          .build();
    } catch (Exception e) {
      System.out.println(e.getMessage());
      return null;
    }
  }

  private ListeningPoint getTestListeningPoint() {
    return new ListeningPoint() {
      @Override
      public int getPort() {
        return 5060;
      }

      @Override
      public String getTransport() {
        return "udp";
      }

      @Override
      public String getIPAddress() {
        return "1.1.1.1";
      }

      @Override
      public void setSentBy(String s) {}

      @Override
      public String getSentBy() {
        return null;
      }
    };
  }
}
