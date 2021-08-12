package com.cisco.dsb.sip.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

import com.cisco.dsb.common.CallType;
import com.cisco.dsb.common.context.ExecutionContext;
import com.cisco.dsb.common.executor.DhruvaExecutorService;
import com.cisco.dsb.common.messaging.ProxySIPRequest;
import com.cisco.dsb.common.messaging.models.DhruvaSipRequestMessage;
import com.cisco.dsb.config.sip.DhruvaSIPConfigProperties;
import com.cisco.dsb.exception.DhruvaException;
import com.cisco.dsb.service.SipServerLocatorService;
import com.cisco.dsb.service.TrunkService;
import com.cisco.dsb.sip.bean.SIPListenPoint;
import com.cisco.dsb.sip.bean.SIPProxy;
import com.cisco.dsb.sip.jain.JainSipHelper;
import com.cisco.dsb.sip.proxy.Location;
import com.cisco.dsb.sip.proxy.ProxyFactory;
import com.cisco.dsb.sip.proxy.ProxyTransaction;
import com.cisco.dsb.sip.stack.dto.DhruvaNetwork;
import com.cisco.dsb.sip.stack.dto.LocateSIPServersResponse;
import com.cisco.dsb.sip.stack.dto.SipDestination;
import com.cisco.dsb.util.SIPRequestBuilder;
import com.cisco.wx2.dto.User;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import gov.nist.javax.sip.header.Allow;
import gov.nist.javax.sip.header.RecordRouteList;
import gov.nist.javax.sip.message.SIPRequest;
import java.net.InetAddress;
import java.text.ParseException;
import java.util.Collections;
import java.util.ListIterator;
import java.util.concurrent.*;
import javax.sip.*;
import javax.sip.address.Hop;
import javax.sip.address.Router;
import javax.sip.address.SipURI;
import javax.sip.address.URI;
import javax.sip.header.AllowHeader;
import javax.sip.header.RecordRouteHeader;
import javax.sip.header.RouteHeader;
import javax.sip.message.Request;
import javax.sip.message.Response;
import org.mockito.*;
import org.testng.Assert;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.DataProvider;
import org.testng.annotations.Test;
import reactor.test.StepVerifier;

public class ProxyControllerTest {

  DhruvaNetwork incomingNetwork;
  DhruvaNetwork outgoingNetwork;
  DhruvaNetwork testNetwork;

  @Mock DhruvaSIPConfigProperties dhruvaSIPConfigProperties;

  @Mock SipServerLocatorService sipServerLocatorService;

  @Mock TrunkService trunkService;

  DhruvaExecutorService dhruvaExecutorService;

  SipStack sipStack;

  ProxyFactory proxyFactory;
  ControllerConfig controllerConfig;
  ProxyControllerFactory proxyControllerFactory;

  SipProvider incomingSipProvider;
  SipProvider outgoingSipProvider;

  //    @BeforeEach
  //    public void beforeEach(){
  //        ReflectionTestUtils.setField(proxyControllerFactory,"controllerConfig", new
  // ControllerConfig());
  //
  //    }

  @BeforeClass
  void init() throws Exception {
    MockitoAnnotations.initMocks(this);

    sipStack = mock(SipStack.class);

    SIPListenPoint sipListenPoint1 = createIncomingUDPSipListenPoint();
    SIPListenPoint sipListenPoint2 = createOutgoingUDPSipListenPoint();
    SIPListenPoint sipListenPoint3 = createTestSipListenPoint();

    incomingNetwork = DhruvaNetwork.createNetwork("net_sp_udp", sipListenPoint1);
    outgoingNetwork = DhruvaNetwork.createNetwork("net_internal_udp", sipListenPoint2);
    testNetwork = DhruvaNetwork.createNetwork("test_net", sipListenPoint3);

    proxyFactory = new ProxyFactory();
    controllerConfig = new ControllerConfig(sipServerLocatorService, dhruvaSIPConfigProperties);
    // dhruvaSIPConfigProperties = new DhruvaSIPConfigProperties();
    dhruvaSIPConfigProperties = mock(DhruvaSIPConfigProperties.class);
    dhruvaExecutorService = mock(DhruvaExecutorService.class);

    controllerConfig.addListenInterface(
        incomingNetwork,
        InetAddress.getByName(sipListenPoint1.getHostIPAddress()),
        sipListenPoint1.getPort(),
        sipListenPoint1.getTransport(),
        InetAddress.getByName(sipListenPoint1.getHostIPAddress()),
        false);

    controllerConfig.addListenInterface(
        outgoingNetwork,
        InetAddress.getByName(sipListenPoint2.getHostIPAddress()),
        sipListenPoint2.getPort(),
        sipListenPoint2.getTransport(),
        InetAddress.getByName(sipListenPoint2.getHostIPAddress()),
        false);

    controllerConfig.addListenInterface(
        testNetwork,
        InetAddress.getByName(sipListenPoint3.getHostIPAddress()),
        sipListenPoint2.getPort(),
        sipListenPoint2.getTransport(),
        InetAddress.getByName(sipListenPoint3.getHostIPAddress()),
        false);

    controllerConfig.addRecordRouteInterface(
        InetAddress.getByName(sipListenPoint1.getHostIPAddress()),
        sipListenPoint1.getPort(),
        sipListenPoint1.getTransport(),
        incomingNetwork);

    controllerConfig.addRecordRouteInterface(
        InetAddress.getByName(sipListenPoint2.getHostIPAddress()),
        sipListenPoint2.getPort(),
        sipListenPoint2.getTransport(),
        outgoingNetwork);

    controllerConfig.addRecordRouteInterface(
        InetAddress.getByName(sipListenPoint3.getHostIPAddress()),
        sipListenPoint3.getPort(),
        sipListenPoint3.getTransport(),
        testNetwork);

    proxyControllerFactory =
        new ProxyControllerFactory(
            dhruvaSIPConfigProperties,
            controllerConfig,
            proxyFactory,
            dhruvaExecutorService,
            trunkService);

    // Dont add 3rd network
    incomingSipProvider = mock(SipProvider.class);
    outgoingSipProvider = mock(SipProvider.class);
    DhruvaNetwork.setSipProvider(incomingNetwork.getName(), incomingSipProvider);
    DhruvaNetwork.setSipProvider(outgoingNetwork.getName(), outgoingSipProvider);

    DhruvaNetwork.setDhruvaConfigProperties(dhruvaSIPConfigProperties);

    when(dhruvaSIPConfigProperties.isHostPortEnabled()).thenReturn(false);
  }

  //    @BeforeEach
  //    public void setUp() {
  //
  ////        doReturn(controllerConfig).when(ctx)
  ////                .getBean(ControllerConfig.class);
  //        //doReturn(dhruvaExecutorService).when(ctx).getBean(DhruvaExecutorService.class);
  //    }

  public SIPListenPoint createIncomingUDPSipListenPoint() throws JsonProcessingException {
    String json =
        "{ \"name\": \"net_sp_udp\", \"hostIPAddress\": \"1.1.1.1\", \"port\": 5060, \"transport\": \"UDP\", "
            + "\"attachExternalIP\": \"false\", \"recordRoute\": \"true\"}";
    return new ObjectMapper().readerFor(SIPListenPoint.class).readValue(json);
  }

  public SIPListenPoint createOutgoingUDPSipListenPoint() throws JsonProcessingException {
    String json =
        "{ \"name\": \"net_internal_udp\", \"hostIPAddress\": \"2.2.2.2\", \"port\": 5080, \"transport\": \"UDP\", "
            + "\"attachExternalIP\": \"false\", \"recordRoute\": \"true\"}";
    return new ObjectMapper().readerFor(SIPListenPoint.class).readValue(json);
  }

  public SIPListenPoint createTestSipListenPoint() throws JsonProcessingException {
    String json =
        "{ \"name\": \"test_net\", \"hostIPAddress\": \"3.3.3.3\", \"port\": 5080, \"transport\": \"UDP\", "
            + "\"attachExternalIP\": \"false\", \"recordRoute\": \"true\"}";
    return new ObjectMapper().readerFor(SIPListenPoint.class).readValue(json);
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
          .network(incomingNetwork.getName())
          .withProvider(incomingSipProvider)
          .build();
    } catch (Exception e) {
      System.out.println(e.getMessage());
      return null;
    }
  }

  public ProxyController getProxyController(ProxySIPRequest proxySIPRequest) {
    ProxyController controller =
        proxyControllerFactory
            .proxyController()
            .apply(proxySIPRequest.getServerTransaction(), proxySIPRequest.getProvider());
    return controller;
  }

  @BeforeMethod
  public void setup() throws SipException {
    reset(outgoingSipProvider);
    reset(incomingSipProvider);
    reset(sipServerLocatorService);
    reset(sipStack);
    Router router = mock(Router.class);
    when(outgoingSipProvider.getSipStack()).thenReturn((SipStack) sipStack);
    when(sipStack.getRouter()).thenReturn(router);
    when(router.getNextHop(any(Request.class))).thenReturn(mock(Hop.class));
  }

  @Test(description = "test proxy client creation for outgoing invite request")
  public void testOutgoingInviteRequestProxyTransaction()
      throws SipException, ExecutionException, InterruptedException {

    ServerTransaction serverTransaction = mock(ServerTransaction.class);

    ProxySIPRequest proxySIPRequest =
        getProxySipRequest(SIPRequestBuilder.RequestMethod.INVITE, serverTransaction);
    ProxyController proxyController = getProxyController(proxySIPRequest);

    doNothing().when(serverTransaction).setApplicationData(any(ProxyTransaction.class));

    ClientTransaction clientTransaction = mock(ClientTransaction.class);
    doNothing().when(clientTransaction).sendRequest();

    when(outgoingSipProvider.getNewClientTransaction(any(Request.class)))
        .thenReturn(clientTransaction);

    LocateSIPServersResponse locateSIPServersResponse = mock(LocateSIPServersResponse.class);
    when(locateSIPServersResponse.getHops()).thenReturn(Collections.emptyList());

    when(sipServerLocatorService.locateDestination(
            nullable(User.class), any(SipDestination.class), nullable(String.class)))
        .thenReturn(locateSIPServersResponse);

    proxySIPRequest = proxyController.onNewRequest(proxySIPRequest);

    Location location = new Location(proxySIPRequest.getRequest().getRequestURI());
    location.setProcessRoute(false);
    location.setNetwork(outgoingNetwork);
    // proxyController.proxyRequest(proxySIPRequest, location);
    // using stepVerifier
    proxySIPRequest.setLocation(location);
    SIPRequest preprocessedRequest = (SIPRequest) proxySIPRequest.getRequest().clone();
    proxyController.setPreprocessedRequest(preprocessedRequest);
    proxySIPRequest.setClonedRequest((SIPRequest) preprocessedRequest.clone());

    StepVerifier.create(
            proxyController.proxyForwardRequest(
                location, proxySIPRequest, proxyController.timeToTry))
        .assertNext(
            request -> {
              assert request.getProxyClientTransaction() != null;
              assert request.getProxyStatelessTransaction() != null;
              assert request.getOutgoingNetwork().equals(outgoingNetwork.getName());
              assert request.getLocation().getUri() == request.getRequest().getRequestURI();
              assert request.getClonedRequest() != null;
              assert request.getClonedRequest().getTopmostViaHeader().getHost().equals("2.2.2.2");
              assert request.getClonedRequest().getTopmostViaHeader().getPort() == 5080;

              // Outgoing record route header validation
              RecordRouteList recordRouteList = request.getClonedRequest().getRecordRouteHeaders();
              RecordRouteHeader recordRouteHeader = (RecordRouteHeader) recordRouteList.getFirst();
              URI uri = recordRouteHeader.getAddress().getURI();
              assert uri.isSipURI();
              SipURI routeUri = (SipURI) uri;
              assert routeUri.getTransportParam().equals("udp");
              assert routeUri.getPort() == outgoingNetwork.getListenPoint().getPort();
              assert routeUri.getHost().equals(outgoingNetwork.getListenPoint().getHostIPAddress());
              assert routeUri.hasLrParam();
              assert routeUri.getUser().equals("rr$n=" + incomingNetwork.getName());
            })
        .verifyComplete();

    ArgumentCaptor<Request> argumentCaptor = ArgumentCaptor.forClass(Request.class);
    verify(outgoingSipProvider, Mockito.times(1)).getNewClientTransaction(argumentCaptor.capture());

    SIPRequest sendRequest = (SIPRequest) argumentCaptor.getValue();

    Assert.assertNotNull(sendRequest);
    Assert.assertEquals(sendRequest.getMethod(), Request.INVITE);
    Assert.assertEquals(sendRequest, proxySIPRequest.getClonedRequest());

    verify(clientTransaction, Mockito.times(1)).sendRequest();

    // Verify that we set the proxy object in applicationData of jain for future response
    ArgumentCaptor<ProxyTransaction> appArgumentCaptor =
        ArgumentCaptor.forClass(ProxyTransaction.class);
    verify(clientTransaction, Mockito.times(1)).setApplicationData(appArgumentCaptor.capture());

    ProxyTransaction proxyTransaction = appArgumentCaptor.getValue();

    Assert.assertNotNull(proxyTransaction);
    Assert.assertEquals(proxyTransaction, proxySIPRequest.getProxyStatelessTransaction());
  }

  @Test(description = "stack send request throws SipException")
  public void testOutgoingRequestSendStackException()
      throws SipException, ExecutionException, InterruptedException {

    ServerTransaction serverTransaction = mock(ServerTransaction.class);

    ProxySIPRequest proxySIPRequest =
        getProxySipRequest(SIPRequestBuilder.RequestMethod.INVITE, serverTransaction);
    ProxyController proxyController = getProxyController(proxySIPRequest);

    doNothing().when(serverTransaction).setApplicationData(any(ProxyTransaction.class));

    LocateSIPServersResponse locateSIPServersResponse = mock(LocateSIPServersResponse.class);
    when(locateSIPServersResponse.getHops()).thenReturn(Collections.emptyList());

    when(sipServerLocatorService.locateDestination(
            nullable(User.class), any(SipDestination.class), nullable(String.class)))
        .thenReturn(locateSIPServersResponse);

    ClientTransaction clientTransaction = mock(ClientTransaction.class);

    // Throw SipException
    doThrow(SipException.class).when(clientTransaction).sendRequest();

    when(outgoingSipProvider.getNewClientTransaction(any(Request.class)))
        .thenReturn(clientTransaction);
    proxySIPRequest = proxyController.onNewRequest(proxySIPRequest);

    Location location = new Location(proxySIPRequest.getRequest().getRequestURI());
    // setProcessRoute: lrescape will be invoked, it will put the top route header to requri and
    // requri to bottom of route list
    // proxyparams will be not be set before that.
    // This is set only  for mid dialog request by passing application
    location.setProcessRoute(true);
    location.setNetwork(outgoingNetwork);

    proxySIPRequest.setLocation(location);
    SIPRequest preprocessedRequest = (SIPRequest) proxySIPRequest.getRequest().clone();
    proxyController.setPreprocessedRequest(preprocessedRequest);
    proxySIPRequest.setClonedRequest((SIPRequest) preprocessedRequest.clone());

    StepVerifier.create(
            proxyController.proxyForwardRequest(
                location, proxySIPRequest, proxyController.timeToTry))
        .verifyErrorMatches(err -> err instanceof SipException);

    verify(clientTransaction, Mockito.times(1)).sendRequest();
  }

  @Test(
      description =
          "stack send request throws DhruvaException since provider for that network is not specified")
  public void testOutgoingRequestProviderException()
      throws SipException, ExecutionException, InterruptedException {

    ServerTransaction serverTransaction = mock(ServerTransaction.class);

    ProxySIPRequest proxySIPRequest =
        getProxySipRequest(SIPRequestBuilder.RequestMethod.INVITE, serverTransaction);
    ProxyController proxyController = getProxyController(proxySIPRequest);

    doNothing().when(serverTransaction).setApplicationData(any(ProxyTransaction.class));

    ClientTransaction clientTransaction = mock(ClientTransaction.class);

    LocateSIPServersResponse locateSIPServersResponse = mock(LocateSIPServersResponse.class);
    when(locateSIPServersResponse.getHops()).thenReturn(Collections.emptyList());

    when(sipServerLocatorService.locateDestination(
            nullable(User.class), any(SipDestination.class), nullable(String.class)))
        .thenReturn(locateSIPServersResponse);

    proxySIPRequest = proxyController.onNewRequest(proxySIPRequest);

    Location location = new Location(proxySIPRequest.getRequest().getRequestURI());
    location.setProcessRoute(false);
    location.setNetwork(testNetwork);

    proxySIPRequest.setLocation(location);
    SIPRequest preprocessedRequest = (SIPRequest) proxySIPRequest.getRequest().clone();
    proxyController.setPreprocessedRequest(preprocessedRequest);
    proxySIPRequest.setClonedRequest((SIPRequest) preprocessedRequest.clone());

    StepVerifier.create(
            proxyController.proxyForwardRequest(
                location, proxySIPRequest, proxyController.timeToTry))
        .verifyErrorMatches(err -> err instanceof DhruvaException);

    verify(clientTransaction, Mockito.times(0)).sendRequest();
    reset(clientTransaction);
  }

  @Test(
      description =
          "test proxy client creation for outgoing ACK request - mid-dialog."
              + "This covers test for normal lrfix case")
  public void testOutgoingACKRequestProxyTransaction() throws SipException, ParseException {

    ServerTransaction serverTransaction = mock(ServerTransaction.class);

    ProxySIPRequest proxySIPRequest =
        getProxySipRequest(SIPRequestBuilder.RequestMethod.ACK, serverTransaction);
    SIPRequest sipRequest = proxySIPRequest.getRequest();
    RouteHeader ownRouteHeader =
        JainSipHelper.createRouteHeader("rr$n=net_internal_udp", "1.1.1.1", 5060, "udp");
    RouteHeader routeHeader1 =
        JainSipHelper.createRouteHeader("testDhruva", "10.1.1.1", 5080, "udp");

    RouteHeader routeHeader2 =
        JainSipHelper.createRouteHeader("testDhruva", "20.1.1.1", 5080, "udp");

    // Route 2 is last header, hence due to lrfix it will be removed
    // Own Route Header will be removed.
    // Reference of addHeader behavior in jain sip stack
    //    if (!(sipHeader instanceof ViaHeader) && !(sipHeader instanceof RecordRouteHeader)) {
    //      this.attachHeader(sh, false, false);
    //    } else {
    //      this.attachHeader(sh, false, true);
    //    }
    sipRequest.addHeader(routeHeader1);
    sipRequest.addHeader(routeHeader2);
    sipRequest.addFirst(ownRouteHeader);

    sipRequest.setRequestURI(ownRouteHeader.getAddress().getURI());

    proxySIPRequest.setLrFixUri(ownRouteHeader.getAddress().getURI());

    ProxyController proxyController = getProxyController(proxySIPRequest);

    doNothing().when(serverTransaction).setApplicationData(any(ProxyTransaction.class));

    ClientTransaction clientTransaction = mock(ClientTransaction.class);
    doNothing().when(clientTransaction).sendRequest();

    when(outgoingSipProvider.getNewClientTransaction(any(Request.class)))
        .thenReturn(clientTransaction);

    Router router = mock(Router.class);
    when(outgoingSipProvider.getSipStack()).thenReturn((SipStack) sipStack);
    when(sipStack.getRouter()).thenReturn(router);
    when(router.getNextHop(any(Request.class))).thenReturn(mock(Hop.class));

    proxySIPRequest = proxyController.onNewRequest(proxySIPRequest);

    Location location = new Location(proxySIPRequest.getLrFixUri());
    location.setProcessRoute(false);
    // Do not set outgoing network, proxy should derive
    // location.setNetwork(outgoingNetwork);
    // proxyController.proxyRequest(proxySIPRequest, location);
    // using stepVerifier
    proxySIPRequest.setLocation(location);
    SIPRequest preprocessedRequest = (SIPRequest) proxySIPRequest.getRequest().clone();
    proxyController.setPreprocessedRequest(preprocessedRequest);
    proxySIPRequest.setClonedRequest((SIPRequest) preprocessedRequest.clone());

    StepVerifier.create(
            proxyController.proxyForwardRequest(
                location, proxySIPRequest, proxyController.timeToTry))
        .assertNext(
            request -> {
              assert request.getProxyClientTransaction() == null;
              assert request.getProxyStatelessTransaction() != null;
              assert request.getOutgoingNetwork().equals(outgoingNetwork.getName());

              assert request.getClonedRequest() != null;
              SipURI reqSipUri = (SipURI) request.getClonedRequest().getRequestURI();
              SipURI locSipUri = (SipURI) request.getLocation().getUri();

              assert locSipUri.getHost().equalsIgnoreCase(reqSipUri.getHost());
              assert locSipUri.getPort() == reqSipUri.getPort();
              assert locSipUri.getUser().equals(reqSipUri.getUser());
              assert locSipUri.getTransportParam().equalsIgnoreCase(reqSipUri.getTransportParam());

              assert request.getClonedRequest().getTopmostViaHeader().getHost().equals("2.2.2.2");
              assert request.getClonedRequest().getTopmostViaHeader().getPort() == 5080;

              RouteHeader firstRouteHeader =
                  (RouteHeader) request.getClonedRequest().getRouteHeaders().getFirst();
              SipURI firstRouteHeaderUri = (SipURI) firstRouteHeader.getAddress().getURI();
              assert !firstRouteHeaderUri.getHost().equals("1.1.1.1");
              assert firstRouteHeaderUri.getPort() != 5060;

              ListIterator routes = request.getClonedRequest().getHeaders(RouteHeader.NAME);
              assert routes != null;
              if (routes.hasNext()) {
                RouteHeader lastRouteHeader;

                // Get to the last value
                do lastRouteHeader = (RouteHeader) routes.next();
                while (routes.hasNext());
                SipURI routeValue = (SipURI) lastRouteHeader.getAddress().getURI();
                assert routeValue.getHost().equals("10.1.1.1");
                assert routeValue.getPort() == 5080;
                assert routeValue.getTransportParam().equalsIgnoreCase("udp");
              }
            })
        .verifyComplete();

    ArgumentCaptor<Request> argumentCaptor = ArgumentCaptor.forClass(Request.class);
    verify(outgoingSipProvider, Mockito.times(0)).getNewClientTransaction(argumentCaptor.capture());

    verify(clientTransaction, Mockito.times(0)).sendRequest();
  }

  @Test(description = "test proxy client creation for outgoing bye request")
  public void testOutgoingByeRequestProxyTransaction()
      throws SipException, ExecutionException, InterruptedException {

    ServerTransaction serverTransaction = mock(ServerTransaction.class);

    ProxySIPRequest proxySIPRequest =
        getProxySipRequest(SIPRequestBuilder.RequestMethod.BYE, serverTransaction);
    ProxyController proxyController = getProxyController(proxySIPRequest);

    doNothing().when(serverTransaction).setApplicationData(any(ProxyTransaction.class));

    ClientTransaction clientTransaction = mock(ClientTransaction.class);
    doNothing().when(clientTransaction).sendRequest();

    when(outgoingSipProvider.getNewClientTransaction(any(Request.class)))
        .thenReturn(clientTransaction);

    LocateSIPServersResponse locateSIPServersResponse = mock(LocateSIPServersResponse.class);
    when(locateSIPServersResponse.getHops()).thenReturn(Collections.emptyList());

    when(sipServerLocatorService.locateDestination(
            nullable(User.class), any(SipDestination.class), nullable(String.class)))
        .thenReturn(locateSIPServersResponse);

    proxySIPRequest = proxyController.onNewRequest(proxySIPRequest);

    Location location = new Location(proxySIPRequest.getRequest().getRequestURI());
    location.setProcessRoute(false);
    location.setNetwork(outgoingNetwork);
    // proxyController.proxyRequest(proxySIPRequest, location);
    // using stepVerifier
    proxySIPRequest.setLocation(location);
    SIPRequest preprocessedRequest = (SIPRequest) proxySIPRequest.getRequest().clone();
    proxyController.setPreprocessedRequest(preprocessedRequest);
    proxySIPRequest.setClonedRequest((SIPRequest) preprocessedRequest.clone());

    StepVerifier.create(
            proxyController.proxyForwardRequest(
                location, proxySIPRequest, proxyController.timeToTry))
        .assertNext(
            request -> {
              assert request.getProxyClientTransaction() != null;
              assert request.getProxyStatelessTransaction() != null;
              assert request.getOutgoingNetwork().equals(outgoingNetwork.getName());
              assert request.getLocation().getUri() == request.getRequest().getRequestURI();
              assert request.getClonedRequest() != null;
              assert request.getClonedRequest().getTopmostViaHeader().getHost().equals("2.2.2.2");
              assert request.getClonedRequest().getTopmostViaHeader().getPort() == 5080;

              // record route should not be added to outgoing BYE request
              RecordRouteList recordRouteList = request.getClonedRequest().getRecordRouteHeaders();
              assert recordRouteList == null;
            })
        .verifyComplete();

    ArgumentCaptor<Request> argumentCaptor = ArgumentCaptor.forClass(Request.class);
    verify(outgoingSipProvider, Mockito.times(1)).getNewClientTransaction(argumentCaptor.capture());

    SIPRequest sendRequest = (SIPRequest) argumentCaptor.getValue();

    Assert.assertNotNull(sendRequest);
    Assert.assertEquals(sendRequest.getMethod(), Request.BYE);
    Assert.assertEquals(sendRequest, proxySIPRequest.getClonedRequest());

    verify(clientTransaction, Mockito.times(1)).sendRequest();

    // Verify that we set the proxy object in applicationData of jain for future response
    ArgumentCaptor<ProxyTransaction> appArgumentCaptor =
        ArgumentCaptor.forClass(ProxyTransaction.class);
    verify(clientTransaction, Mockito.times(1)).setApplicationData(appArgumentCaptor.capture());

    ProxyTransaction proxyTransaction = appArgumentCaptor.getValue();

    Assert.assertNotNull(proxyTransaction);
    Assert.assertEquals(proxyTransaction, proxySIPRequest.getProxyStatelessTransaction());
  }

  @Test(
      description =
          "incoming mid dialog request.ReqURI matches proxy , " + "but no route header available",
      expectedExceptions = {RuntimeException.class})
  public void testIncomingACKRequestLrFixRuntimeException() throws ParseException {

    ServerTransaction serverTransaction = mock(ServerTransaction.class);

    ProxySIPRequest proxySIPRequest =
        getProxySipRequest(SIPRequestBuilder.RequestMethod.ACK, serverTransaction);
    SIPRequest sipRequest = proxySIPRequest.getRequest();
    RouteHeader ownRouteHeader =
        JainSipHelper.createRouteHeader("rr$n=net_internal_udp", "1.1.1.1", 5060, "udp");

    ListIterator routes = sipRequest.getHeaders(RouteHeader.NAME);
    if (routes != null && routes.hasNext()) {
      while (routes.hasNext()) {
        sipRequest.removeHeader(RouteHeader.NAME, true);
        routes.next();
      }
    }

    sipRequest.setRequestURI(ownRouteHeader.getAddress().getURI());

    ProxyController proxyController = getProxyController(proxySIPRequest);

    doNothing().when(serverTransaction).setApplicationData(any(ProxyTransaction.class));

    proxyController.onNewRequest(proxySIPRequest);
  }

  @Test(
      description =
          "incoming request handling for mid dialog."
              + "ReqURI does not match but top route matches proxy.LrFix variable should be set based on Route Header")
  public void testIncomingRequestLrFixReqUriNotMatching() throws ParseException, SipException {

    ServerTransaction serverTransaction = mock(ServerTransaction.class);

    ProxySIPRequest proxySIPRequest =
        getProxySipRequest(SIPRequestBuilder.RequestMethod.ACK, serverTransaction);
    SIPRequest sipRequest = proxySIPRequest.getRequest();
    RouteHeader ownRouteHeader =
        JainSipHelper.createRouteHeader("rr$n=net_internal_udp", "1.1.1.1", 5060, "udp");
    RouteHeader routeHeader1 =
        JainSipHelper.createRouteHeader("testDhruva", "10.1.1.1", 5080, "udp");

    RouteHeader routeHeader2 =
        JainSipHelper.createRouteHeader("testDhruva", "20.1.1.1", 5080, "udp");

    sipRequest.addHeader(routeHeader1);
    sipRequest.addHeader(routeHeader2);
    sipRequest.addFirst(ownRouteHeader);

    ProxyController proxyController = getProxyController(proxySIPRequest);

    doNothing().when(serverTransaction).setApplicationData(any(ProxyTransaction.class));

    proxySIPRequest = proxyController.onNewRequest(proxySIPRequest);
    // Check that lrfix variable is set even if requri does not belong to proxy and has top route
    // matching the proxy
    assert proxySIPRequest.getLrFixUri() != null;
    SipURI lrfixUri = (SipURI) proxySIPRequest.getLrFixUri();

    assert lrfixUri.getHost().equals("1.1.1.1");
    assert lrfixUri.getUser().equals("rr$n=net_internal_udp");

    RouteHeader topRouteHeader =
        (RouteHeader) proxySIPRequest.getRequest().getHeader(RouteHeader.NAME);
    SipURI routeUri = (SipURI) topRouteHeader.getAddress().getURI();

    // Check if top most route header which belongs to proxy is removed
    assert !routeUri.getHost().equals("1.1.1.1");
    assert !routeUri.getUser().equals("rr$n=net_internal_udp");
  }

  @Test(
      description =
          "incoming request handling for mid dialog.ReqURI and top route don't match proxy."
              + "LrFix variable should not be set")
  public void testIncomingRequestNoLrFix()
      throws ParseException, ExecutionException, InterruptedException {

    ServerTransaction serverTransaction = mock(ServerTransaction.class);

    ProxySIPRequest proxySIPRequest =
        getProxySipRequest(SIPRequestBuilder.RequestMethod.ACK, serverTransaction);
    SIPRequest sipRequest = proxySIPRequest.getRequest();
    RouteHeader ownRouteHeader =
        JainSipHelper.createRouteHeader("rr$n=net_internal_udp", "1.1.1.1", 5060, "udp");
    RouteHeader routeHeader1 =
        JainSipHelper.createRouteHeader("testDhruva", "10.1.1.1", 5080, "udp");

    RouteHeader routeHeader2 =
        JainSipHelper.createRouteHeader("testDhruva", "20.1.1.1", 5080, "udp");

    sipRequest.addHeader(routeHeader1);
    sipRequest.addHeader(routeHeader2);

    ProxyController proxyController = getProxyController(proxySIPRequest);

    doNothing().when(serverTransaction).setApplicationData(any(ProxyTransaction.class));

    LocateSIPServersResponse locateSIPServersResponse = mock(LocateSIPServersResponse.class);
    when(locateSIPServersResponse.getHops()).thenReturn(Collections.emptyList());

    when(sipServerLocatorService.locateDestination(
            nullable(User.class), any(SipDestination.class), nullable(String.class)))
        .thenReturn(locateSIPServersResponse);
    proxySIPRequest = proxyController.onNewRequest(proxySIPRequest);
    // Check that lrfix variable is set even if requri does not belong to proxy and has top route
    // not matching the proxy
    assert proxySIPRequest.getLrFixUri() == null;

    RouteHeader lastRouteHeader = null;
    ListIterator routes = proxySIPRequest.getRequest().getHeaders(RouteHeader.NAME);
    if (routes != null && routes.hasNext()) {
      // Get to the last value
      do lastRouteHeader = (RouteHeader) routes.next();
      while (routes.hasNext());
    }
    assert lastRouteHeader != null;
    SipURI routeUri = (SipURI) lastRouteHeader.getAddress().getURI();

    // Check if bottom most route header remains unchanged
    assert routeUri.getHost().equals("20.1.1.1");
    assert routeUri.getUser().equals("testDhruva");
  }

  @Test(
      description =
          "test incoming request handling MAddr processing."
              + "Maddr matches proxy, hence should not be removed")
  public void testIncomingRequestMAddrRemoval()
      throws ParseException, ExecutionException, InterruptedException {

    ServerTransaction serverTransaction = mock(ServerTransaction.class);

    ProxySIPRequest proxySIPRequest =
        getProxySipRequest(SIPRequestBuilder.RequestMethod.INVITE, serverTransaction);
    SIPRequest sipRequest = proxySIPRequest.getRequest();
    URI uri = JainSipHelper.createSipURI("testDhruva@9.9.9.9:5060;transport=udp");
    sipRequest.setRequestURI(uri);
    SipURI mAddr = (SipURI) sipRequest.getRequestURI();
    mAddr.setMAddrParam("1.1.1.1");
    ProxyController proxyController = getProxyController(proxySIPRequest);

    doNothing().when(serverTransaction).setApplicationData(any(ProxyTransaction.class));

    LocateSIPServersResponse locateSIPServersResponse = mock(LocateSIPServersResponse.class);
    when(locateSIPServersResponse.getHops()).thenReturn(Collections.emptyList());

    when(sipServerLocatorService.locateDestination(
            nullable(User.class), any(SipDestination.class), nullable(String.class)))
        .thenReturn(locateSIPServersResponse);
    proxySIPRequest = proxyController.processIncomingProxyRequestMAddr.apply(proxySIPRequest);
    SipURI requestURI = (SipURI) proxySIPRequest.getRequest().getRequestURI();

    assert requestURI.getMAddrParam() == null;
  }

  @Test(
      description =
          "test incoming request handling MAddr processing."
              + "Maddr does not match proxy, hence should not be removed")
  public void testIncomingRequestMAddrDoesNotMatch()
      throws ParseException, ExecutionException, InterruptedException {

    ServerTransaction serverTransaction = mock(ServerTransaction.class);

    ProxySIPRequest proxySIPRequest =
        getProxySipRequest(SIPRequestBuilder.RequestMethod.INVITE, serverTransaction);
    SIPRequest sipRequest = proxySIPRequest.getRequest();
    URI uri = JainSipHelper.createSipURI("testDhruva@9.9.9.9:5060;transport=udp");
    sipRequest.setRequestURI(uri);
    SipURI mAddr = (SipURI) sipRequest.getRequestURI();
    mAddr.setMAddrParam("10.1.1.1");
    ProxyController proxyController = getProxyController(proxySIPRequest);

    doNothing().when(serverTransaction).setApplicationData(any(ProxyTransaction.class));

    LocateSIPServersResponse locateSIPServersResponse = mock(LocateSIPServersResponse.class);
    when(locateSIPServersResponse.getHops()).thenReturn(Collections.emptyList());

    when(sipServerLocatorService.locateDestination(
            nullable(User.class), any(SipDestination.class), nullable(String.class)))
        .thenReturn(locateSIPServersResponse);
    proxySIPRequest = proxyController.processIncomingProxyRequestMAddr.apply(proxySIPRequest);
    SipURI requestURI = (SipURI) proxySIPRequest.getRequest().getRequestURI();

    assert requestURI.getMAddrParam() != null;
  }

  @Test(
      description =
          "test incoming new invite request, proxy transaction and server transaction creation")
  public void testIncomingInviteRequestProxyTransaction()
      throws ExecutionException, InterruptedException {

    ServerTransaction serverTransaction = mock(ServerTransaction.class);

    ProxySIPRequest proxySIPRequest =
        getProxySipRequest(SIPRequestBuilder.RequestMethod.INVITE, serverTransaction);
    ProxyController proxyController = getProxyController(proxySIPRequest);

    doNothing().when(serverTransaction).setApplicationData(any(ProxyTransaction.class));

    LocateSIPServersResponse locateSIPServersResponse = mock(LocateSIPServersResponse.class);
    when(locateSIPServersResponse.getHops()).thenReturn(Collections.emptyList());

    when(sipServerLocatorService.locateDestination(
            nullable(User.class), any(SipDestination.class), nullable(String.class)))
        .thenReturn(locateSIPServersResponse);

    proxySIPRequest = proxyController.onNewRequest(proxySIPRequest);

    ArgumentCaptor<ProxyTransaction> argumentCaptor =
        ArgumentCaptor.forClass(ProxyTransaction.class);
    verify(serverTransaction, Mockito.times(1)).setApplicationData(argumentCaptor.capture());

    ProxyTransaction proxyTransaction = argumentCaptor.getValue();

    Assert.assertEquals(proxyTransaction, proxySIPRequest.getProxyStatelessTransaction());
  }

  @DataProvider
  public Object[] shouldProcessRegisterReq() {
    return new Boolean[][] {{true}, {false}};
  }

  @Test(
      dataProvider = "shouldProcessRegisterReq",
      description =
          "Based on 'processRegisterRequest' toggle, either reject REGISTER request with 405(Method not allowed) response with a Allow-header listing the supported methods by the proxy"
              + "(or) handover to app for processing")
  public void testRegisterHandling(boolean processRegisterRequest)
      throws SipException, InvalidArgumentException {

    ServerTransaction st = mock(ServerTransaction.class);
    ProxyTransaction pt = mock(ProxyTransaction.class);
    SIPProxy sipProxy = mock(SIPProxy.class);

    ProxySIPRequest proxySIPRequest =
        getProxySipRequest(SIPRequestBuilder.RequestMethod.REGISTER, st);
    System.out.println("SIP Request :  " + proxySIPRequest.getRequest());
    ProxyController proxyController = getProxyController(proxySIPRequest);

    when(dhruvaSIPConfigProperties.getSIPProxy()).thenReturn(sipProxy);
    when(sipProxy.isProcessRegisterRequest()).thenReturn(processRegisterRequest);

    if (processRegisterRequest)
      Assert.assertEquals(proxyController.handleRequest().apply(proxySIPRequest), proxySIPRequest);
    else {
      String allowedMethods =
          Request.INVITE
              .concat(",")
              .concat(Request.ACK)
              .concat(",")
              .concat(Request.BYE)
              .concat(",")
              .concat(Request.CANCEL)
              .concat(",")
              .concat(Request.OPTIONS)
              .concat(",")
              .concat(Request.INFO)
              .concat(",")
              .concat(Request.SUBSCRIBE);

      when(dhruvaSIPConfigProperties.getAllowedMethods()).thenReturn(allowedMethods);

      ArgumentCaptor<Response> captor = ArgumentCaptor.forClass(Response.class);

      doNothing().when(st).sendResponse(any(Response.class));

      Assert.assertNull(proxyController.handleRequest().apply(proxySIPRequest));

      verify(st, times(1)).sendResponse(captor.capture());
      Response response = captor.getValue();
      System.out.println("Error response: " + response);
      Assert.assertEquals(response.getStatusCode(), 405);
      Assert.assertEquals(response.getReasonPhrase(), "Method not allowed");
      Assert.assertEquals(
          ((Allow) response.getHeader(AllowHeader.NAME)).getHeaderValue(), allowedMethods);
    }
  }
}
