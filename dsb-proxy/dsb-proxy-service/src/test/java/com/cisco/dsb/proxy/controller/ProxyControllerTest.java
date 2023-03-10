package com.cisco.dsb.proxy.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

import com.cisco.dsb.common.CallType;
import com.cisco.dsb.common.config.sip.CommonConfigurationProperties;
import com.cisco.dsb.common.context.ExecutionContext;
import com.cisco.dsb.common.exception.DhruvaException;
import com.cisco.dsb.common.exception.DhruvaRuntimeException;
import com.cisco.dsb.common.exception.ErrorCode;
import com.cisco.dsb.common.executor.DhruvaExecutorService;
import com.cisco.dsb.common.executor.ExecutorType;
import com.cisco.dsb.common.maintanence.Maintenance;
import com.cisco.dsb.common.record.DhruvaAppRecord;
import com.cisco.dsb.common.service.SipServerLocatorService;
import com.cisco.dsb.common.sip.bean.SIPListenPoint;
import com.cisco.dsb.common.sip.jain.JainSipHelper;
import com.cisco.dsb.common.sip.stack.dto.DhruvaNetwork;
import com.cisco.dsb.common.sip.stack.dto.LocateSIPServersResponse;
import com.cisco.dsb.common.sip.stack.dto.SipDestination;
import com.cisco.dsb.common.sip.util.EndPoint;
import com.cisco.dsb.common.sip.util.SupportedExtensions;
import com.cisco.dsb.common.transport.Transport;
import com.cisco.dsb.common.util.SpringApplicationContext;
import com.cisco.dsb.proxy.ControllerInterface;
import com.cisco.dsb.proxy.ProxyConfigurationProperties;
import com.cisco.dsb.proxy.dto.ProxyAppConfig;
import com.cisco.dsb.proxy.errors.InternalProxyErrorException;
import com.cisco.dsb.proxy.messaging.DhruvaSipRequestMessage;
import com.cisco.dsb.proxy.messaging.ProxySIPRequest;
import com.cisco.dsb.proxy.messaging.ProxySIPResponse;
import com.cisco.dsb.proxy.sip.*;
import com.cisco.dsb.proxy.util.QuadFunction;
import com.cisco.dsb.proxy.util.SIPRequestBuilder;
import com.cisco.wx2.dto.User;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import gov.nist.javax.sip.SipStackImpl;
import gov.nist.javax.sip.header.*;
import gov.nist.javax.sip.message.SIPRequest;
import gov.nist.javax.sip.stack.HopImpl;
import gov.nist.javax.sip.stack.SIPTransaction;
import java.io.IOException;
import java.net.UnknownHostException;
import java.text.ParseException;
import java.util.Collections;
import java.util.ListIterator;
import java.util.concurrent.*;
import java.util.function.Supplier;
import javax.sip.*;
import javax.sip.address.Hop;
import javax.sip.address.Router;
import javax.sip.address.SipURI;
import javax.sip.address.URI;
import javax.sip.header.*;
import javax.sip.message.Request;
import javax.sip.message.Response;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.springframework.context.ApplicationContext;
import org.testng.Assert;
import org.testng.annotations.*;

public class ProxyControllerTest {

  DhruvaNetwork incomingNetwork;
  DhruvaNetwork outgoingNetwork;
  DhruvaNetwork testNetwork;

  DhruvaNetwork udpNetworkIncoming;
  DhruvaNetwork tcpNetworkIncoming;
  DhruvaNetwork tlsNetworkIncoming;
  DhruvaNetwork udpNetworkOutgoing;
  DhruvaNetwork tcpNetworkOutgoing;
  DhruvaNetwork tlsNetworkOutgoing;

  @Mock ProxyConfigurationProperties proxyConfigurationProperties;
  @Mock CommonConfigurationProperties commonConfigurationProperties;

  @Mock SipServerLocatorService sipServerLocatorService;

  @Mock ProxyAppConfig proxyAppConfig;
  DhruvaExecutorService dhruvaExecutorService;

  SipStack sipStack;

  ProxyFactory proxyFactory;
  ControllerConfig controllerConfig;
  ProxyControllerFactory proxyControllerFactory;

  SipProvider incomingSipProvider;
  SipProvider outgoingSipProvider;
  SipProvider udpSipProviderIncoming;
  SipProvider tcpSipProviderIncoming;
  SipProvider tlsSipProviderIncoming;
  SipProvider udpSipProviderOutgoing;
  SipProvider tcpSipProviderOutgoing;
  SipProvider tlsSipProviderOutgoing;

  SIPListenPoint sipListenPointUdpIncoming;
  SIPListenPoint sipListenPointTcpIncoming;
  SIPListenPoint sipListenPointTlsIncoming;
  SIPListenPoint sipListenPointUdpOutgoing;
  SIPListenPoint sipListenPointTcpOutgoing;
  SIPListenPoint sipListenPointTlsOutgoing;

  SpringApplicationContext springApplicationContext;
  ScheduledThreadPoolExecutor scheduledExecutor;
  //    @BeforeEach
  //    public void beforeEach(){
  //        ReflectionTestUtils.setField(proxyControllerFactory,"controllerConfig", new
  // ControllerConfig());
  //
  //    }

  @BeforeClass
  void init() throws Exception {
    MockitoAnnotations.openMocks(this);

    sipStack = mock(SipStack.class);

    SIPListenPoint sipListenPoint1 = createIncomingTCPSipListenPoint();
    SIPListenPoint sipListenPoint2 = createOutgoingTCPSipListenPoint();
    SIPListenPoint sipListenPoint3 = createTestSipListenPoint();

    incomingNetwork = DhruvaNetwork.createNetwork("net_sp_tcp", sipListenPoint1);
    outgoingNetwork = DhruvaNetwork.createNetwork("net_internal_tcp", sipListenPoint2);
    testNetwork = DhruvaNetwork.createNetwork("test_net", sipListenPoint3);

    proxyFactory = new ProxyFactory();
    controllerConfig = new ControllerConfig(sipServerLocatorService, proxyConfigurationProperties);
    // dhruvaSIPConfigProperties = new DhruvaSIPConfigProperties();
    dhruvaExecutorService = mock(DhruvaExecutorService.class);
    scheduledExecutor = mock(ScheduledThreadPoolExecutor.class);

    controllerConfig.addListenInterface(sipListenPoint1);

    controllerConfig.addListenInterface(sipListenPoint2);

    controllerConfig.addListenInterface(sipListenPoint3);

    proxyControllerFactory =
        new ProxyControllerFactory(
            proxyConfigurationProperties,
            controllerConfig,
            proxyFactory,
            dhruvaExecutorService,
            sipServerLocatorService);

    // Dont add 3rd network
    incomingSipProvider = mock(SipProvider.class);
    outgoingSipProvider = mock(SipProvider.class);

    DhruvaNetwork.setDhruvaConfigProperties(commonConfigurationProperties);

    when(commonConfigurationProperties.isHostPortEnabled()).thenReturn(false);
    SIPProxy sipProxy = mock(SIPProxy.class);
    when(sipProxy.getTimerCIntervalInMilliSec()).thenReturn((long) 2);
    when(proxyConfigurationProperties.getSipProxy()).thenReturn(sipProxy);

    springApplicationContext = new SpringApplicationContext();
    ApplicationContext context = mock(ApplicationContext.class);
    springApplicationContext.setApplicationContext(context);

    when(context.getBean(DhruvaExecutorService.class)).thenReturn(dhruvaExecutorService);
    when(dhruvaExecutorService.getScheduledExecutorThreadPool(ExecutorType.PROXY_CLIENT_TIMEOUT))
        .thenReturn(scheduledExecutor);
  }

  //    @BeforeEach
  //    public void setUp() {
  //
  ////        doReturn(controllerConfig).when(ctx)
  ////                .getBean(ControllerConfig.class);
  //        //doReturn(dhruvaExecutorService).when(ctx).getBean(DhruvaExecutorService.class);
  //    }

  public SIPListenPoint createListenPoint(String networkName, int port, String transport)
      throws JsonProcessingException {
    String hostAddress = "1.1.1.1";
    if (networkName.contains("Outgoing")) {
      hostAddress = "2.2.2.2";
    }
    String json =
        "{ \"name\":\""
            + networkName
            + "\", \"hostIPAddress\": \""
            + hostAddress
            + "\", \"port\":"
            + port
            + ", \"transport\": \""
            + transport
            + "\", "
            + "\"attachExternalIP\": \"false\", \"recordRoute\": \"true\"}";
    return new ObjectMapper().readerFor(SIPListenPoint.class).readValue(json);
  }

  public SIPListenPoint createIncomingTCPSipListenPoint() {
    return SIPListenPoint.SIPListenPointBuilder()
        .setName("net_sp_tcp")
        .setHostIPAddress("1.1.1.1")
        .setPort(5060)
        .setTransport(Transport.TCP)
        .setRecordRoute(true)
        .build();
  }

  public SIPListenPoint createOutgoingTCPSipListenPoint() {
    return SIPListenPoint.SIPListenPointBuilder()
        .setName("net_internal_tcp")
        .setHostIPAddress("2.2.2.2")
        .setPort(5080)
        .setTransport(Transport.TCP)
        .setRecordRoute(true)
        .build();
  }

  public SIPListenPoint createTestSipListenPoint() {
    return SIPListenPoint.SIPListenPointBuilder()
        .setName("test_net")
        .setHostIPAddress("3.3.3.3")
        .setPort(5080)
        .setTransport(Transport.TCP)
        .setRecordRoute(true)
        .build();
  }

  public ProxySIPRequest getProxySipRequest(
      SIPRequestBuilder.RequestMethod method,
      ServerTransaction serverTransaction,
      DhruvaNetwork network) {
    try {
      ExecutionContext context = new ExecutionContext();
      SIPRequest request =
          SIPRequestBuilder.createRequest(new SIPRequestBuilder().getRequestAsString(method));

      SipProvider sipProvider;
      if (network == udpNetworkIncoming) {
        sipProvider = udpSipProviderIncoming;
      } else if (network == tcpNetworkIncoming) {
        sipProvider = tcpSipProviderIncoming;
      } else if (network == tlsNetworkIncoming) {
        sipProvider = tlsSipProviderIncoming;
      } else {
        sipProvider = incomingSipProvider;
      }

      return DhruvaSipRequestMessage.newBuilder()
          .withContext(context)
          .withPayload(request)
          .withTransaction(serverTransaction)
          .callType(CallType.SIP)
          .correlationId("ABCD")
          .reqURI("sip:test@webex.com")
          .sessionId("testSession")
          .network(network.getName())
          .withProvider(sipProvider)
          .build();
    } catch (Exception e) {
      System.out.println(e.getMessage());
      return null;
    }
  }

  public ProxySIPRequest getProxySipRequest(
      SIPRequestBuilder.RequestMethod method, ServerTransaction serverTransaction) {
    return getProxySipRequest(method, serverTransaction, incomingNetwork);
  }

  public ProxyController getProxyController(ProxySIPRequest proxySIPRequest) {
    return proxyControllerFactory
        .proxyController()
        .apply(
            proxySIPRequest.getServerTransaction(), proxySIPRequest.getProvider(), proxyAppConfig);
  }

  @BeforeMethod
  public void setup() throws SipException {
    reset(outgoingSipProvider);
    reset(incomingSipProvider);
    reset(sipServerLocatorService);
    reset(sipStack);
    Router router = mock(Router.class);
    when(outgoingSipProvider.getSipStack()).thenReturn(sipStack);
    if (udpSipProviderOutgoing != null) {
      when(udpSipProviderOutgoing.getSipStack()).thenReturn(sipStack);
    }
    if (tcpSipProviderOutgoing != null) {
      when(tcpSipProviderOutgoing.getSipStack()).thenReturn(sipStack);
    }
    if (tlsSipProviderOutgoing != null) {
      when(tlsSipProviderOutgoing.getSipStack()).thenReturn(sipStack);
    }
    when(sipStack.getRouter()).thenReturn(router);
    when(router.getNextHop(any(Request.class))).thenReturn(mock(Hop.class));
    DhruvaNetwork.setSipProvider(incomingNetwork.getName(), incomingSipProvider);
    DhruvaNetwork.setSipProvider(outgoingNetwork.getName(), outgoingSipProvider);
    doAnswer(invocationOnMock -> invocationOnMock.<HopImpl>getArgument(0))
        .when(sipServerLocatorService)
        .resolveAddress(any(Hop.class));
  }

  @AfterClass
  void cleanUp() {
    springApplicationContext.setApplicationContext(null);
  }

  @DataProvider
  public Object[][] getSipStack()
      throws JsonProcessingException, DhruvaException, UnknownHostException, ParseException {
    udpSipProviderIncoming = mock(SipProvider.class);
    tcpSipProviderIncoming = mock(SipProvider.class);
    tlsSipProviderIncoming = mock(SipProvider.class);
    udpSipProviderOutgoing = mock(SipProvider.class);
    tcpSipProviderOutgoing = mock(SipProvider.class);
    tlsSipProviderOutgoing = mock(SipProvider.class);
    sipListenPointUdpIncoming = createListenPoint("UDPNetworkIncoming", 5064, "UDP");
    sipListenPointTcpIncoming = createListenPoint("TCPNetworkIncoming", 5065, "TCP");
    sipListenPointTlsIncoming = createListenPoint("TLSNetworkIncoming", 5066, "TLS");
    sipListenPointUdpOutgoing = createListenPoint("UDPNetworkOutgoing", 5067, "UDP");
    sipListenPointTcpOutgoing = createListenPoint("TCPNetworkOutgoing", 5068, "TCP");
    sipListenPointTlsOutgoing = createListenPoint("TLSNetworkOutgoing", 5069, "TLS");
    udpNetworkIncoming =
        DhruvaNetwork.createNetwork("UDPNetworkIncoming", sipListenPointUdpIncoming);
    tcpNetworkIncoming =
        DhruvaNetwork.createNetwork("TCPNetworkIncoming", sipListenPointTcpIncoming);
    tlsNetworkIncoming =
        DhruvaNetwork.createNetwork("TLSNetworkIncoming", sipListenPointTlsIncoming);
    udpNetworkOutgoing =
        DhruvaNetwork.createNetwork("UDPNetworkOutgoing", sipListenPointUdpOutgoing);
    tcpNetworkOutgoing =
        DhruvaNetwork.createNetwork("TCPNetworkOutgoing", sipListenPointTcpOutgoing);
    tlsNetworkOutgoing =
        DhruvaNetwork.createNetwork("TLSNetworkOutgoing", sipListenPointTlsOutgoing);
    DhruvaNetwork.setSipProvider(udpNetworkIncoming.getName(), udpSipProviderIncoming);
    DhruvaNetwork.setSipProvider(tcpNetworkIncoming.getName(), tcpSipProviderIncoming);
    DhruvaNetwork.setSipProvider(tlsNetworkIncoming.getName(), tlsSipProviderIncoming);
    DhruvaNetwork.setSipProvider(udpNetworkOutgoing.getName(), udpSipProviderOutgoing);
    DhruvaNetwork.setSipProvider(tcpNetworkOutgoing.getName(), tcpSipProviderOutgoing);
    DhruvaNetwork.setSipProvider(tlsNetworkOutgoing.getName(), tlsSipProviderOutgoing);
    controllerConfig.addListenInterface(sipListenPointUdpIncoming);
    controllerConfig.addListenInterface(sipListenPointUdpIncoming);
    controllerConfig.addListenInterface(sipListenPointTcpIncoming);
    controllerConfig.addListenInterface(sipListenPointTcpOutgoing);
    controllerConfig.addListenInterface(sipListenPointTlsIncoming);
    controllerConfig.addListenInterface(sipListenPointTlsOutgoing);

    return new Object[][] {
      {udpNetworkIncoming, udpNetworkOutgoing, udpSipProviderOutgoing},
      {tcpNetworkIncoming, tcpNetworkOutgoing, tcpSipProviderOutgoing},
      {tlsNetworkIncoming, tlsNetworkOutgoing, tlsSipProviderOutgoing}
    };
  }

  @Test(
      description = "test proxy client creation for outgoing invite request",
      dataProvider = "getSipStack")
  public void testOutgoingInviteRequestProxyTransactionAllTransports(Object[] testParams)
      throws SipException, InterruptedException {

    DhruvaNetwork incomingNetwork1 = (DhruvaNetwork) testParams[0];
    DhruvaNetwork outgoingNetwork1 = (DhruvaNetwork) testParams[1];
    SipProvider outgoingSipProvider1 = (SipProvider) testParams[2];

    int outPort = 5080;
    if (outgoingNetwork1.getName().equals("UDPNetworkOutgoing")) {
      outPort = 5067;
    } else if (outgoingNetwork1.getName().equals("TCPNetworkOutgoing")) {
      outPort = 5068;
    } else if (outgoingNetwork1.getName().equals("TLSNetworkOutgoing")) {
      outPort = 5069;
    }
    int finalOutPort = outPort;
    String protocol = "tcp";
    if (outgoingNetwork1.getName().contains("UDP")) {
      protocol = "udp";
    } else if (outgoingNetwork1.getName().contains("TLS")) {
      protocol = "tls";
    }
    String finalProtocol = protocol;

    ServerTransaction serverTransaction = mock(ServerTransaction.class);
    ProxySIPRequest proxySIPRequest =
        getProxySipRequest(
            SIPRequestBuilder.RequestMethod.INVITE, serverTransaction, incomingNetwork1);
    proxySIPRequest.setAppRecord(new DhruvaAppRecord());
    ProxyController proxyController = getProxyController(proxySIPRequest);

    doNothing().when(serverTransaction).setApplicationData(any(ProxyTransaction.class));

    ClientTransaction clientTransaction = mock(ClientTransaction.class);
    Request request = mock(SIPRequest.class);
    when(clientTransaction.getRequest()).thenReturn(request);

    when(outgoingSipProvider1.getNewClientTransaction(any(Request.class)))
        .thenReturn(clientTransaction);

    LocateSIPServersResponse locateSIPServersResponse = mock(LocateSIPServersResponse.class);
    when(locateSIPServersResponse.getHops()).thenReturn(Collections.emptyList());

    when(sipServerLocatorService.locateDestinationAsync(
            nullable(User.class), any(SipDestination.class)))
        .thenReturn(CompletableFuture.completedFuture(locateSIPServersResponse));

    proxySIPRequest = proxyController.onNewRequest(proxySIPRequest).block();

    // Mock Trunk Service
    EndPoint endPoint =
        new EndPoint(outgoingNetwork1.getName(), "9.9.9.9", 5061, outgoingNetwork1.getTransport());
    CompletableFuture<ProxySIPResponse> responseCF =
        proxyController.proxyRequest(proxySIPRequest, endPoint);

    // adding sleep as we have to wait for ProxySendMessage.sendProxyRequestAsync to be invoked
    // after subscription
    Thread.sleep(200);
    assert ((ProxyCookieImpl) proxySIPRequest.getCookie()).getResponseCF() == responseCF;
    assert proxySIPRequest.getProxyClientTransaction() != null;
    assert proxySIPRequest.getProxyStatelessTransaction() != null;
    assert proxySIPRequest.getOutgoingNetwork().equals(outgoingNetwork1.getName());
    assert proxySIPRequest.getRequest() != null;
    assert proxySIPRequest.getRequest().getTopmostViaHeader().getHost().equals("2.2.2.2");
    assert proxySIPRequest.getRequest().getTopmostViaHeader().getPort() == finalOutPort;

    // Outgoing record route header validation
    RecordRouteList recordRouteList = proxySIPRequest.getRequest().getRecordRouteHeaders();
    RecordRouteHeader recordRouteHeader = (RecordRouteHeader) recordRouteList.getFirst();
    URI uri = recordRouteHeader.getAddress().getURI();
    assert uri.isSipURI();
    SipURI routeUri = (SipURI) uri;
    assert routeUri.getTransportParam().equals(finalProtocol);
    assert routeUri.getPort() == outgoingNetwork1.getListenPoint().getPort();
    assert routeUri.getHost().equals(outgoingNetwork1.getListenPoint().getHostIPAddress());
    assert routeUri.hasLrParam();
    assert routeUri.getUser().equals("rr$n=" + incomingNetwork1.getName());

    ArgumentCaptor<Request> argumentCaptor = ArgumentCaptor.forClass(Request.class);
    verify(outgoingSipProvider1).getNewClientTransaction(argumentCaptor.capture());

    SIPRequest sendRequest = (SIPRequest) argumentCaptor.getValue();

    Assert.assertNotNull(sendRequest);
    Assert.assertEquals(sendRequest.getMethod(), Request.INVITE);
    Assert.assertEquals(sendRequest, proxySIPRequest.getRequest());

    verify(clientTransaction).sendRequest();

    // Verify that we set the proxy object in applicationData of jain for future response
    ArgumentCaptor<ProxyTransaction> appArgumentCaptor =
        ArgumentCaptor.forClass(ProxyTransaction.class);
    verify(clientTransaction).setApplicationData(appArgumentCaptor.capture());

    ProxyTransaction proxyTransaction = appArgumentCaptor.getValue();

    Assert.assertNotNull(proxyTransaction);
    Assert.assertEquals(proxyTransaction, proxySIPRequest.getProxyStatelessTransaction());
  }

  @Test(description = "stack send request throws SipException due to Destination Unreachable")
  public void testOutgoingRequestSendStackException()
      throws SipException, InterruptedException, TimeoutException {

    ServerTransaction serverTransaction = mock(ServerTransaction.class);

    ProxySIPRequest proxySIPRequest =
        getProxySipRequest(SIPRequestBuilder.RequestMethod.INVITE, serverTransaction);
    proxySIPRequest.setAppRecord(new DhruvaAppRecord());
    ProxyController proxyController = getProxyController(proxySIPRequest);

    doNothing().when(serverTransaction).setApplicationData(any(ProxyTransaction.class));

    LocateSIPServersResponse locateSIPServersResponse = mock(LocateSIPServersResponse.class);
    when(locateSIPServersResponse.getHops()).thenReturn(Collections.emptyList());

    when(sipServerLocatorService.locateDestinationAsync(
            nullable(User.class), any(SipDestination.class)))
        .thenReturn(CompletableFuture.completedFuture(locateSIPServersResponse));

    ClientTransaction clientTransaction = mock(ClientTransaction.class);
    Request request = mock(SIPRequest.class);
    when(clientTransaction.getRequest()).thenReturn(request);

    // Throw SipException
    SipException sipException = new SipException("Destination Unreachable", new IOException());
    doThrow(sipException).when(clientTransaction).sendRequest();

    when(outgoingSipProvider.getNewClientTransaction(any(Request.class)))
        .thenReturn(clientTransaction);
    proxySIPRequest = proxyController.onNewRequest(proxySIPRequest).block();

    // setProcessRoute: lrescape will be invoked, it will put the top route header to requri and
    // requri to bottom of route list
    // proxyparams will be not be set before that.
    // This is set only  for mid dialog request by passing application

    // Mock Trunk Service
    EndPoint endPoint =
        new EndPoint(outgoingNetwork.getName(), "9.9.9.9", 5061, outgoingNetwork.getTransport());
    CompletableFuture<ProxySIPResponse> responseCF =
        proxyController.proxyRequest(proxySIPRequest, endPoint);

    Assert.assertEquals(
        ((ProxyCookieImpl) proxySIPRequest.getCookie()).getResponseCF(), responseCF);
    try {
      responseCF.get(100, TimeUnit.MILLISECONDS);
    } catch (ExecutionException ex) {
      assert (((DhruvaRuntimeException) ex.getCause()).getErrCode()
          == ErrorCode.DESTINATION_UNREACHABLE);
    }

    verify(clientTransaction).sendRequest();
  }

  @Test(
      description =
          "stack send request throws DhruvaException since provider for that network is not specified")
  public void testOutgoingRequestProviderException()
      throws SipException, InterruptedException, TimeoutException {

    ServerTransaction serverTransaction = mock(ServerTransaction.class);

    ProxySIPRequest proxySIPRequest =
        getProxySipRequest(SIPRequestBuilder.RequestMethod.INVITE, serverTransaction);
    proxySIPRequest.setAppRecord(new DhruvaAppRecord());
    ProxyController proxyController = getProxyController(proxySIPRequest);

    doNothing().when(serverTransaction).setApplicationData(any(ProxyTransaction.class));

    ClientTransaction clientTransaction = mock(ClientTransaction.class);
    when(outgoingSipProvider.getNewClientTransaction(any(Request.class)))
        .thenReturn(clientTransaction);
    DhruvaNetwork.removeSipProvider(outgoingNetwork.getName());
    LocateSIPServersResponse locateSIPServersResponse = mock(LocateSIPServersResponse.class);
    when(locateSIPServersResponse.getHops()).thenReturn(Collections.emptyList());

    when(sipServerLocatorService.locateDestinationAsync(
            nullable(User.class), any(SipDestination.class)))
        .thenReturn(CompletableFuture.completedFuture(locateSIPServersResponse));

    proxySIPRequest = proxyController.onNewRequest(proxySIPRequest).block();

    EndPoint endPoint =
        new EndPoint(outgoingNetwork.getName(), "9.9.9.9", 5061, outgoingNetwork.getTransport());

    CompletableFuture<ProxySIPResponse> responseCF =
        proxyController.proxyRequest(proxySIPRequest, endPoint);
    try {
      responseCF.get(100, TimeUnit.MILLISECONDS);
    } catch (ExecutionException ex) {
      assert (((DhruvaRuntimeException) ex.getCause()).getErrCode()
          == ErrorCode.REQUEST_NO_PROVIDER);
    }
    verify(clientTransaction, never()).sendRequest();
    reset(clientTransaction);
  }

  @Test(
      description =
          "test proxy client creation for outgoing ACK request - mid-dialog."
              + "This covers test for normal lrfix case")
  public void testOutgoingACKRequestProxyTransaction()
      throws SipException, ParseException, ExecutionException, InterruptedException,
          TimeoutException {

    ServerTransaction serverTransaction = mock(ServerTransaction.class);

    ProxySIPRequest proxySIPRequest =
        getProxySipRequest(SIPRequestBuilder.RequestMethod.ACK, serverTransaction);
    proxySIPRequest.setAppRecord(new DhruvaAppRecord());
    SIPRequest sipRequest = proxySIPRequest.getRequest();
    // Removing top route header because route header will be added as part of endpoint
    RouteHeader ownRouteHeader =
        JainSipHelper.createRouteHeader("rr$n=net_internal_tcp", "1.1.1.1", 5060, "tcp");
    RouteHeader routeHeader1 =
        JainSipHelper.createRouteHeader("testDhruva", "10.1.1.1", 5080, "tcp");

    RouteHeader routeHeader2 =
        JainSipHelper.createRouteHeader("testDhruva", "20.1.1.1", 5080, "tcp");

    // Route 2 is last header, hence due to lrfix it will be removed
    // Own Route Header will be removed.
    // Reference of addHeader behavior in jain sip stack
    //    if (!(sipHeader instanceof ViaHeader) && !(sipHeader instanceof RecordRouteHeader)) {
    //      this.attachHeader(sh, false, false);
    //    } else {
    //      this.attachHeader(sh, false, true);
    //    }
    sipRequest.getRouteHeaders().clear();

    sipRequest.addHeader(routeHeader1);
    sipRequest.addHeader(routeHeader2);
    sipRequest.addFirst(ownRouteHeader);
    sipRequest.setRequestURI(ownRouteHeader.getAddress().getURI());

    ProxyController proxyController = getProxyController(proxySIPRequest);

    doNothing().when(serverTransaction).setApplicationData(any(ProxyTransaction.class));

    ClientTransaction clientTransaction = mock(ClientTransaction.class);

    when(outgoingSipProvider.getNewClientTransaction(any(Request.class)))
        .thenReturn(clientTransaction);

    Router router = mock(Router.class);
    when(outgoingSipProvider.getSipStack()).thenReturn(sipStack);
    when(sipStack.getRouter()).thenReturn(router);
    when(router.getNextHop(any(Request.class))).thenReturn(mock(Hop.class));

    proxySIPRequest = proxyController.onNewRequest(proxySIPRequest).block();

    CompletableFuture<ProxySIPResponse> responseCF = proxyController.proxyRequest(proxySIPRequest);
    // ACK is sent out and CF will be completed with null
    assert responseCF.get(0, TimeUnit.MILLISECONDS) == null;
    assert proxySIPRequest.getProxyClientTransaction() == null;
    assert proxySIPRequest.getProxyStatelessTransaction() != null;
    assert proxySIPRequest.getOutgoingNetwork().equals(outgoingNetwork.getName());

    assert proxySIPRequest.getRequest() != null;

    assert proxySIPRequest.getRequest().getTopmostViaHeader().getHost().equals("2.2.2.2");
    assert proxySIPRequest.getRequest().getTopmostViaHeader().getPort() == 5080;

    RouteHeader firstRouteHeader =
        (RouteHeader) proxySIPRequest.getRequest().getRouteHeaders().getFirst();
    SipURI firstRouteHeaderUri = (SipURI) firstRouteHeader.getAddress().getURI();
    assert !firstRouteHeaderUri.getHost().equals("1.1.1.1");
    assert firstRouteHeaderUri.getPort() != 5060;

    RouteList routes = proxySIPRequest.getRequest().getRouteHeaders();
    if (routes != null) {
      RouteHeader lastRouteHeader = ((Route) routes.getLast());
      SipURI routeValue = (SipURI) lastRouteHeader.getAddress().getURI();
      assert routeValue.getHost().equals("10.1.1.1");
      assert routeValue.getPort() == 5080;
    }
    ArgumentCaptor<Request> argumentCaptor = ArgumentCaptor.forClass(Request.class);
    verify(outgoingSipProvider, never()).getNewClientTransaction(argumentCaptor.capture());

    verify(clientTransaction, never()).sendRequest();

    // assert lrfix
    Assert.assertEquals(
        proxySIPRequest.getRequest().getRequestURI(), routeHeader2.getAddress().getURI());
    Assert.assertEquals(proxySIPRequest.getLrFixUri(), ownRouteHeader.getAddress().getURI());
  }

  @Test(description = "calling proxyrequest without setting outgoing network")
  public void testProxyRequestWithoutNetwork() {
    ServerTransaction serverTransaction = mock(ServerTransaction.class);

    ProxySIPRequest proxySIPRequest =
        getProxySipRequest(SIPRequestBuilder.RequestMethod.ACK, serverTransaction);
    proxySIPRequest.setAppRecord(new DhruvaAppRecord());
    ProxyController proxyController = getProxyController(proxySIPRequest);
    LocateSIPServersResponse locateSIPServersResponse = mock(LocateSIPServersResponse.class);
    when(locateSIPServersResponse.getHops()).thenReturn(Collections.emptyList());

    when(sipServerLocatorService.locateDestinationAsync(
            nullable(User.class), any(SipDestination.class)))
        .thenReturn(CompletableFuture.completedFuture(locateSIPServersResponse));
    proxyController.onNewRequest(proxySIPRequest);
    CompletableFuture<ProxySIPResponse> cf = proxyController.proxyRequest(proxySIPRequest);
    Assert.assertTrue(cf.isCompletedExceptionally());
    cf.whenComplete((msg, ex) -> Assert.assertSame(ex.getCause(), DhruvaRuntimeException.class));
  }

  @Test(description = "proxyrequest called with invalid network")
  public void testProxyRequestWithInvalidNetwork() {
    ServerTransaction serverTransaction = mock(ServerTransaction.class);

    ProxySIPRequest proxySIPRequest =
        getProxySipRequest(SIPRequestBuilder.RequestMethod.ACK, serverTransaction);
    proxySIPRequest.setAppRecord(new DhruvaAppRecord());
    ProxyController proxyController = getProxyController(proxySIPRequest);
    LocateSIPServersResponse locateSIPServersResponse = mock(LocateSIPServersResponse.class);
    when(locateSIPServersResponse.getHops()).thenReturn(Collections.emptyList());

    when(sipServerLocatorService.locateDestinationAsync(
            nullable(User.class), any(SipDestination.class)))
        .thenReturn(CompletableFuture.completedFuture(locateSIPServersResponse));
    proxyController.onNewRequest(proxySIPRequest);
    proxySIPRequest.setOutgoingNetwork("invalid");
    CompletableFuture<ProxySIPResponse> cf = proxyController.proxyRequest(proxySIPRequest);
    Assert.assertTrue(cf.isCompletedExceptionally());
    cf.whenComplete((msg, ex) -> Assert.assertSame(ex.getCause(), DhruvaRuntimeException.class));
  }

  @Test(description = "proxy request without route header, route using rURI")
  public void testProxyRequestWithoutRoute()
      throws ParseException, SipException, InterruptedException {
    ServerTransaction serverTransaction = mock(ServerTransaction.class);

    ProxySIPRequest proxySIPRequest =
        getProxySipRequest(SIPRequestBuilder.RequestMethod.ACK, serverTransaction);
    proxySIPRequest.setAppRecord(new DhruvaAppRecord());
    ProxyController proxyController = getProxyController(proxySIPRequest);
    SIPRequest sipRequest = proxySIPRequest.getRequest();
    sipRequest.getRouteHeaders().clear();
    RouteHeader ownRouteHeader =
        JainSipHelper.createRouteHeader("rr$n=net_internal_tcp", "1.1.1.1", 5060, "tcp");
    sipRequest.getRouteHeaders().addFirst((Route) ownRouteHeader);
    assert sipRequest.getRouteHeaders().size() == 1;
    proxyController.onNewRequest(proxySIPRequest).block();

    proxyController.proxyRequest(proxySIPRequest);
    Thread.sleep(200);
    ArgumentCaptor<SIPRequest> requestArgumentCaptor = ArgumentCaptor.forClass(SIPRequest.class);
    verify(outgoingSipProvider, times(1)).sendRequest(requestArgumentCaptor.capture());
    Assert.assertNull(requestArgumentCaptor.getValue().getRouteHeaders());
  }

  @DataProvider
  public Object[][] getTransport() {
    return new Object[][] {
      {ListeningPoint.TLS}, {ListeningPoint.TCP}, {ListeningPoint.UDP}, {"Invalid"}, {null}
    };
  }

  @Test(
      description =
          "using route header and outgoing network to route the call with different transport",
      dataProvider = "getTransport")
  public void testProxyRequestWithRoute(String transport)
      throws ParseException, SipException, InterruptedException {
    ServerTransaction serverTransaction = mock(ServerTransaction.class);

    ProxySIPRequest proxySIPRequest =
        getProxySipRequest(SIPRequestBuilder.RequestMethod.ACK, serverTransaction);
    proxySIPRequest.setAppRecord(new DhruvaAppRecord());
    ProxyController proxyController = getProxyController(proxySIPRequest);
    SIPRequest sipRequest = proxySIPRequest.getRequest();
    RouteList routeHeaders = sipRequest.getRouteHeaders();
    routeHeaders.clear();
    RouteHeader ownRouteHeader =
        JainSipHelper.createRouteHeader("rr$n=net_internal_tcp", "1.1.1.1", 5060, "tcp");
    RouteHeader routeHeader1 =
        JainSipHelper.createRouteHeader("testDhruva", "10.1.1.1", 5080, transport);
    routeHeaders.addFirst(((Route) routeHeader1));
    routeHeaders.addFirst(((Route) ownRouteHeader));
    proxyController.onNewRequest(proxySIPRequest).block();

    proxyController.proxyRequest(proxySIPRequest);
    Thread.sleep(200);
    ArgumentCaptor<SIPRequest> requestArgumentCaptor = ArgumentCaptor.forClass(SIPRequest.class);
    verify(outgoingSipProvider, times(1)).sendRequest(requestArgumentCaptor.capture());
    ArgumentCaptor<HopImpl> hopArgumentCaptor = ArgumentCaptor.forClass(HopImpl.class);
    verify(sipServerLocatorService, times(1)).resolveAddress(hopArgumentCaptor.capture());
    Assert.assertEquals(
        requestArgumentCaptor.getValue().getRouteHeaders().getFirst(), routeHeader1);
    String actualTransport = hopArgumentCaptor.getValue().getTransport();
    String expectedTransport;
    if (transport == null || transport.equals("Invalid")) {
      expectedTransport = ListeningPoint.UDP;
    } else expectedTransport = transport;
    Assert.assertEquals(actualTransport, expectedTransport);
  }

  @Test(description = "test proxy client creation for outgoing bye request")
  public void testOutgoingByeRequestProxyTransaction() throws SipException, InterruptedException {

    ServerTransaction serverTransaction = mock(ServerTransaction.class);

    ProxySIPRequest proxySIPRequest =
        getProxySipRequest(SIPRequestBuilder.RequestMethod.BYE, serverTransaction);
    proxySIPRequest.setAppRecord(new DhruvaAppRecord());
    ProxyController proxyController = getProxyController(proxySIPRequest);

    doNothing().when(serverTransaction).setApplicationData(any(ProxyTransaction.class));

    ClientTransaction clientTransaction = mock(ClientTransaction.class);

    doReturn(clientTransaction)
        .when(outgoingSipProvider)
        .getNewClientTransaction(any(Request.class));

    Request request = mock(SIPRequest.class);
    when(clientTransaction.getRequest()).thenReturn(request);
    LocateSIPServersResponse locateSIPServersResponse = mock(LocateSIPServersResponse.class);
    when(locateSIPServersResponse.getHops()).thenReturn(Collections.emptyList());

    when(sipServerLocatorService.locateDestinationAsync(
            nullable(User.class), any(SipDestination.class)))
        .thenReturn(CompletableFuture.completedFuture(locateSIPServersResponse));

    proxySIPRequest = proxyController.onNewRequest(proxySIPRequest).block();

    // proxyController.proxyRequest(proxySIPRequest, location);
    // using stepVerifier

    // Mock Trunk Service
    EndPoint endPoint =
        new EndPoint(outgoingNetwork.getName(), "9.9.9.9", 5061, outgoingNetwork.getTransport());

    CompletableFuture<ProxySIPResponse> responseCF =
        proxyController.proxyRequest(proxySIPRequest, endPoint);

    Thread.sleep(200);
    assert ((ProxyCookieImpl) proxySIPRequest.getCookie()).getResponseCF() == responseCF;
    assert proxySIPRequest.getProxyClientTransaction() != null;
    assert proxySIPRequest.getProxyStatelessTransaction() != null;
    assert proxySIPRequest.getOutgoingNetwork().equals(outgoingNetwork.getName());
    assert proxySIPRequest.getRequest() != null;
    assert proxySIPRequest.getRequest().getTopmostViaHeader().getHost().equals("2.2.2.2");
    assert proxySIPRequest.getRequest().getTopmostViaHeader().getPort() == 5080;

    // record route should not be added to outgoing BYE request
    RecordRouteList recordRouteList = proxySIPRequest.getRequest().getRecordRouteHeaders();
    assert recordRouteList == null;

    ArgumentCaptor<Request> argumentCaptor = ArgumentCaptor.forClass(Request.class);
    verify(outgoingSipProvider).getNewClientTransaction(argumentCaptor.capture());

    SIPRequest sendRequest = (SIPRequest) argumentCaptor.getValue();

    Assert.assertNotNull(sendRequest);
    Assert.assertEquals(sendRequest.getMethod(), Request.BYE);
    Assert.assertEquals(sendRequest, proxySIPRequest.getRequest());

    verify(clientTransaction).sendRequest();

    // Verify that we set the proxy object in applicationData of jain for future response
    ArgumentCaptor<ProxyTransaction> appArgumentCaptor =
        ArgumentCaptor.forClass(ProxyTransaction.class);
    verify(clientTransaction).setApplicationData(appArgumentCaptor.capture());

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
        JainSipHelper.createRouteHeader("rr$n=net_internal_tcp", "1.1.1.1", 5060, "tcp");

    ListIterator<SIPHeader> routes = sipRequest.getHeaders(RouteHeader.NAME);
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
        JainSipHelper.createRouteHeader("rr$n=net_internal_tcp", "1.1.1.1", 5060, "tcp");
    RouteHeader routeHeader1 =
        JainSipHelper.createRouteHeader("testDhruva", "10.1.1.1", 5080, "tcp");

    RouteHeader routeHeader2 =
        JainSipHelper.createRouteHeader("testDhruva", "20.1.1.1", 5080, "tcp");

    sipRequest.addHeader(routeHeader1);
    sipRequest.addHeader(routeHeader2);
    sipRequest.addFirst(ownRouteHeader);

    ProxyController proxyController = getProxyController(proxySIPRequest);

    doNothing().when(serverTransaction).setApplicationData(any(ProxyTransaction.class));

    proxySIPRequest = proxyController.onNewRequest(proxySIPRequest).block();
    // Check that lrfix variable is set even if requri does not belong to proxy and has top route
    // matching the proxy
    assert proxySIPRequest.getLrFixUri() != null;
    SipURI lrfixUri = (SipURI) proxySIPRequest.getLrFixUri();

    assert lrfixUri.getHost().equals("1.1.1.1");
    assert lrfixUri.getUser().equals("rr$n=net_internal_tcp");

    RouteHeader topRouteHeader =
        (RouteHeader) proxySIPRequest.getRequest().getHeader(RouteHeader.NAME);
    SipURI routeUri = (SipURI) topRouteHeader.getAddress().getURI();

    // Check if top most route header which belongs to proxy is removed
    assert !routeUri.getHost().equals("1.1.1.1");
    assert !routeUri.getUser().equals("rr$n=net_internal_tcp");
  }

  @Test(
      description =
          "incoming request handling for mid dialog.ReqURI and top route don't match proxy."
              + "LrFix variable should not be set")
  public void testIncomingRequestNoLrFix() throws ParseException {

    ServerTransaction serverTransaction = mock(ServerTransaction.class);

    ProxySIPRequest proxySIPRequest =
        getProxySipRequest(SIPRequestBuilder.RequestMethod.ACK, serverTransaction);
    SIPRequest sipRequest = proxySIPRequest.getRequest();
    RouteHeader ownRouteHeader =
        JainSipHelper.createRouteHeader("rr$n=net_internal_tcp", "1.1.1.1", 5060, "tcp");
    RouteHeader routeHeader1 =
        JainSipHelper.createRouteHeader("testDhruva", "10.1.1.1", 5080, "tcp");

    RouteHeader routeHeader2 =
        JainSipHelper.createRouteHeader("testDhruva", "20.1.1.1", 5080, "tcp");

    sipRequest.addHeader(routeHeader1);
    sipRequest.addHeader(routeHeader2);

    ProxyController proxyController = getProxyController(proxySIPRequest);

    doNothing().when(serverTransaction).setApplicationData(any(ProxyTransaction.class));

    LocateSIPServersResponse locateSIPServersResponse = mock(LocateSIPServersResponse.class);
    when(locateSIPServersResponse.getHops()).thenReturn(Collections.emptyList());

    when(sipServerLocatorService.locateDestinationAsync(
            nullable(User.class), any(SipDestination.class)))
        .thenReturn(CompletableFuture.completedFuture(locateSIPServersResponse));
    proxySIPRequest = proxyController.onNewRequest(proxySIPRequest).block();
    // Check that lrfix variable is set even if requri does not belong to proxy and has top route
    // not matching the proxy
    assert proxySIPRequest.getLrFixUri() == null;

    RouteHeader lastRouteHeader = null;
    ListIterator<SIPHeader> routes = proxySIPRequest.getRequest().getHeaders(RouteHeader.NAME);
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
  public void testIncomingRequestMAddrRemoval() throws ParseException {

    ServerTransaction serverTransaction = mock(ServerTransaction.class);

    ProxySIPRequest proxySIPRequest =
        getProxySipRequest(SIPRequestBuilder.RequestMethod.INVITE, serverTransaction);
    SIPRequest sipRequest = proxySIPRequest.getRequest();
    URI uri = JainSipHelper.createSipURI("rr$n=net_sp_tcp@9.9.9.9:5060;transport=tcp");
    sipRequest.setRequestURI(uri);
    SipURI mAddr = (SipURI) sipRequest.getRequestURI();
    mAddr.setMAddrParam("1.1.1.1");
    ProxyController proxyController = getProxyController(proxySIPRequest);

    doNothing().when(serverTransaction).setApplicationData(any(ProxyTransaction.class));

    LocateSIPServersResponse locateSIPServersResponse = mock(LocateSIPServersResponse.class);
    when(locateSIPServersResponse.getHops()).thenReturn(Collections.emptyList());

    when(sipServerLocatorService.locateDestinationAsync(
            nullable(User.class), any(SipDestination.class)))
        .thenReturn(CompletableFuture.completedFuture(locateSIPServersResponse));
    proxySIPRequest =
        proxyController.processIncomingProxyRequestMAddr.apply(proxySIPRequest).block();
    SipURI requestURI = (SipURI) proxySIPRequest.getRequest().getRequestURI();

    assert requestURI.getMAddrParam() == null;
  }

  @Test(
      description =
          "test incoming request handling MAddr processing."
              + "Maddr does not match proxy, hence should not be removed")
  public void testIncomingRequestMAddrDoesNotMatch() throws ParseException {

    ServerTransaction serverTransaction = mock(ServerTransaction.class);

    ProxySIPRequest proxySIPRequest =
        getProxySipRequest(SIPRequestBuilder.RequestMethod.INVITE, serverTransaction);
    SIPRequest sipRequest = proxySIPRequest.getRequest();
    URI uri = JainSipHelper.createSipURI("testDhruva@9.9.9.9:5060;transport=tcp");
    sipRequest.setRequestURI(uri);
    SipURI mAddr = (SipURI) sipRequest.getRequestURI();
    mAddr.setMAddrParam("10.1.1.1");
    ProxyController proxyController = getProxyController(proxySIPRequest);

    doNothing().when(serverTransaction).setApplicationData(any(ProxyTransaction.class));

    LocateSIPServersResponse locateSIPServersResponse = mock(LocateSIPServersResponse.class);
    when(locateSIPServersResponse.getHops()).thenReturn(Collections.emptyList());

    when(sipServerLocatorService.locateDestinationAsync(
            nullable(User.class), any(SipDestination.class)))
        .thenReturn(CompletableFuture.completedFuture(locateSIPServersResponse));
    proxySIPRequest =
        proxyController.processIncomingProxyRequestMAddr.apply(proxySIPRequest).block();
    SipURI requestURI = (SipURI) proxySIPRequest.getRequest().getRequestURI();

    assert requestURI.getMAddrParam() != null;
  }

  @Test(
      description =
          "test incoming new invite request, proxy transaction and server transaction creation")
  public void testIncomingInviteRequestProxyTransaction() {

    ServerTransaction serverTransaction = mock(ServerTransaction.class);

    ProxySIPRequest proxySIPRequest =
        getProxySipRequest(SIPRequestBuilder.RequestMethod.INVITE, serverTransaction);
    ProxyController proxyController = getProxyController(proxySIPRequest);

    doNothing().when(serverTransaction).setApplicationData(any(ProxyTransaction.class));

    LocateSIPServersResponse locateSIPServersResponse = mock(LocateSIPServersResponse.class);
    when(locateSIPServersResponse.getHops()).thenReturn(Collections.emptyList());

    when(sipServerLocatorService.locateDestinationAsync(
            nullable(User.class), any(SipDestination.class)))
        .thenReturn(CompletableFuture.completedFuture(locateSIPServersResponse));

    proxySIPRequest = proxyController.onNewRequest(proxySIPRequest).block();

    ArgumentCaptor<ProxyTransaction> argumentCaptor =
        ArgumentCaptor.forClass(ProxyTransaction.class);
    verify(serverTransaction).setApplicationData(argumentCaptor.capture());

    ProxyTransaction proxyTransaction = argumentCaptor.getValue();

    Assert.assertEquals(proxyTransaction, proxySIPRequest.getProxyStatelessTransaction());
  }

  @DataProvider
  public Object[][] toggleValues() {
    return new Boolean[][] {{true}, {false}};
  }

  @Test(
      dataProvider = "toggleValues",
      description =
          "Based on 'processRegisterRequest' toggle, either reject REGISTER request with 405(Method not allowed) response with a Allow-header listing the supported methods by the proxy"
              + "(or) handover to app for processing")
  public void testRegisterHandling(boolean processRegisterRequest)
      throws SipException, InvalidArgumentException {

    ServerTransaction st = mock(ServerTransaction.class);
    SIPProxy sipProxy = mock(SIPProxy.class);

    ProxySIPRequest proxySIPRequest =
        getProxySipRequest(SIPRequestBuilder.RequestMethod.REGISTER, st);
    proxySIPRequest.setAppRecord(new DhruvaAppRecord());
    System.out.println("SIP Request :  " + proxySIPRequest.getRequest());
    ProxyController proxyController = getProxyController(proxySIPRequest);

    when(proxyConfigurationProperties.getSipProxy()).thenReturn(sipProxy);
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
              .concat(Request.SUBSCRIBE)
              .concat(",")
              .concat(Request.REFER);

      when(proxyConfigurationProperties.getAllowedMethods()).thenReturn(allowedMethods);

      ArgumentCaptor<Response> captor = ArgumentCaptor.forClass(Response.class);

      doNothing().when(st).sendResponse(any(Response.class));

      Assert.assertNull(proxyController.handleRequest().apply(proxySIPRequest));

      verify(st).sendResponse(captor.capture());
      Response response = captor.getValue();
      System.out.println("Error response: " + response);
      Assert.assertEquals(response.getStatusCode(), 405);
      Assert.assertEquals(response.getReasonPhrase(), "Method not allowed");
      Assert.assertEquals(
          ((Allow) response.getHeader(AllowHeader.NAME)).getHeaderValue(), allowedMethods);
    }
  }

  @Test(
      description =
          "An incoming CANCEL request to the proxy must be responded with a 200 OK before being forwarded")
  public void testCancelHandling() throws SipException, InvalidArgumentException {

    ServerTransaction st = mock(ServerTransaction.class);

    SipStackImpl sipStack = mock(SipStackImpl.class);
    ProxyTransaction proxyTransaction = mock(ProxyTransaction.class);

    ProxySIPRequest proxySIPRequest =
        getProxySipRequest(SIPRequestBuilder.RequestMethod.CANCEL, st);
    ProxyController proxyController = getProxyController(proxySIPRequest);
    when(proxySIPRequest.getProvider().getSipStack()).thenReturn(sipStack);
    SIPTransaction sipTransaction = mock(SIPTransaction.class);

    when((sipStack).findCancelTransaction(any(), eq(true))).thenReturn(sipTransaction);
    when(sipTransaction.getApplicationData()).thenReturn(proxyTransaction);

    ArgumentCaptor<Response> captor = ArgumentCaptor.forClass(Response.class);

    doNothing().when(st).sendResponse(any(Response.class));

    Assert.assertNull(proxyController.handleRequest().apply(proxySIPRequest));

    verify(proxyTransaction).cancel();
    verify(st).sendResponse(captor.capture());
    Response response = captor.getValue();
    System.out.println("Response: " + response);
    Assert.assertEquals(response.getStatusCode(), 200);
    Assert.assertEquals(response.getReasonPhrase(), "OK");
  }

  @Test(
      dataProvider = "toggleValues",
      description =
          "If we want to cancel transactions (based on toggle) upon receiving a success (2xx) response, invoke cancel() on ProxyTransaction. "
              + "Else, do not.")
  public void testCancelInvocationForSuccessResponse(boolean cancelBranches) {
    ServerTransaction st = mock(ServerTransaction.class);
    SipProvider sp = mock(SipProvider.class);
    ProxySIPRequest proxySIPRequest = mock(ProxySIPRequest.class);
    ProxySIPResponse proxySIPResponse = mock(ProxySIPResponse.class);
    ProxyTransaction proxyTransaction = mock(ProxyTransaction.class);

    when(proxySIPRequest.getServerTransaction()).thenReturn(st);
    when(proxySIPRequest.getProvider()).thenReturn(sp);
    when(proxySIPResponse.getResponseClass()).thenReturn(2);
    ProxyController proxyController = getProxyController(proxySIPRequest);
    proxyController.setProxyTransaction(proxyTransaction);
    proxyController.setCancelBranchesAutomatically(cancelBranches);
    ProxyCookieImpl cookie = new ProxyCookieImpl();
    CompletableFuture<ProxySIPResponse> responseCF = mock(CompletableFuture.class);
    cookie.setResponseCF(responseCF);
    proxyController.onFinalResponse(cookie, proxySIPResponse);

    if (cancelBranches) {
      verify(proxyTransaction).cancel();
    } else {
      verify(proxyTransaction, never()).cancel();
    }
    verify(responseCF, times(1)).complete(proxySIPResponse);
  }

  @Test(
      dataProvider = "toggleValues",
      description =
          "If we want to cancel transactions (based on toggle) upon receiving a global failure (6xx) response, invoke cancel() on ProxyTransaction. "
              + "Else, do not.")
  public void testCancelInvocationForGlobalFailureResponse(boolean cancelBranches) {
    ServerTransaction st = mock(ServerTransaction.class);
    SipProvider sp = mock(SipProvider.class);
    ProxySIPRequest proxySIPRequest = mock(ProxySIPRequest.class);
    ProxySIPResponse proxySIPResponse = mock(ProxySIPResponse.class);
    ProxyTransaction proxyTransaction = mock(ProxyTransaction.class);

    when(proxySIPRequest.getServerTransaction()).thenReturn(st);
    when(proxySIPRequest.getProvider()).thenReturn(sp);
    when(proxySIPResponse.getResponseClass()).thenReturn(6);

    ProxyController proxyController = getProxyController(proxySIPRequest);
    proxyController.setProxyTransaction(proxyTransaction);
    proxyController.setCancelBranchesAutomatically(cancelBranches);
    ProxyCookieImpl cookie = new ProxyCookieImpl();
    CompletableFuture<ProxySIPResponse> responseCF = mock(CompletableFuture.class);
    cookie.setResponseCF(responseCF);
    proxyController.onFinalResponse(cookie, proxySIPResponse);

    if (cancelBranches) {
      verify(proxyTransaction).cancel();
    } else {
      verify(proxyTransaction, never()).cancel();
    }
    verify(responseCF, times(1)).complete(proxySIPResponse);
  }

  @Test(
      description =
          "1. An incoming OPTIONS request to the proxy must be responded with a 200 OK (when not in maintenance mode)"
              + "2. During maintenance mode, no 200 OK for OPTIONS -> the msg is processed at the application level (trunk based maintenance policy applied)")
  public void testOptionsHandling() throws SipException, InvalidArgumentException {

    ServerTransaction st = mock(ServerTransaction.class);

    ProxySIPRequest proxySIPRequest =
        getProxySipRequest(SIPRequestBuilder.RequestMethod.OPTIONS, st);
    ProxyController proxyController = getProxyController(proxySIPRequest);

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
            .concat(Request.SUBSCRIBE)
            .concat(",")
            .concat(Request.REFER);

    SupportedExtensions.addExtension("feature1");

    /// maintenance mode - enabled
    Supplier<Maintenance> maintenanceSupplier =
        () -> Maintenance.MaintenanceBuilder().setEnabled(true).build();
    when(proxyAppConfig.getMaintenance()).thenReturn(maintenanceSupplier);
    Assert.assertEquals(proxyController.handleRequest().apply(proxySIPRequest), proxySIPRequest);
    verify(st, times(0)).sendResponse(any(Response.class));

    // maintenance mode - not enabled
    maintenanceSupplier = () -> Maintenance.MaintenanceBuilder().setEnabled(false).build();
    when(proxyAppConfig.getMaintenance()).thenReturn(maintenanceSupplier);
    when(proxyConfigurationProperties.getAllowedMethods()).thenReturn(allowedMethods);
    doNothing().when(st).sendResponse(any(Response.class));

    ArgumentCaptor<Response> captor = ArgumentCaptor.forClass(Response.class);
    Assert.assertNull(proxyController.handleRequest().apply(proxySIPRequest));
    verify(st).sendResponse(captor.capture());
    Response response = captor.getValue();
    Assert.assertEquals(response.getStatusCode(), 200);
    Assert.assertEquals(response.getReasonPhrase(), "OK");
    Assert.assertEquals(
        ((Allow) response.getHeader(AllowHeader.NAME)).getHeaderValue(), allowedMethods);
    // TODO: Assert.assertEquals(((Supported)
    // response.getHeader(SupportedHeader.NAME)).getHeaderValue(),
    // SupportedExtensions.getExtensions().st);
    Assert.assertEquals(
        (response.getHeader(AcceptHeader.NAME)).toString().trim(), "Accept: application/sdp");

    SupportedExtensions.removeExtension("feature1");
  }

  @Test(
      description = "test create proxy transaction exception and return 500 internal failure",
      expectedExceptions = DhruvaRuntimeException.class)
  public void testCreateProxyTransactionFailure()
      throws SipException, InvalidArgumentException, InternalProxyErrorException {
    ServerTransaction serverTransaction = mock(ServerTransaction.class);
    reset(serverTransaction);

    ProxySIPRequest proxySIPRequest =
        getProxySipRequest(SIPRequestBuilder.RequestMethod.INVITE, serverTransaction);

    ProxyFactory proxyFactoryMock = mock(ProxyFactory.class);

    // Get our own controller factory with mocked proxy factory
    ProxyControllerFactory proxyControllerFactoryMock =
        new ProxyControllerFactory(
            proxyConfigurationProperties,
            controllerConfig,
            proxyFactoryMock,
            dhruvaExecutorService,
            sipServerLocatorService);

    ProxyController proxyController =
        proxyControllerFactoryMock
            .proxyController()
            .apply(
                proxySIPRequest.getServerTransaction(),
                proxySIPRequest.getProvider(),
                proxyAppConfig);

    QuadFunction<
            ControllerInterface,
            ProxyParamsInterface,
            ServerTransaction,
            SIPRequest,
            ProxyStatelessTransaction>
        function1 = mock(QuadFunction.class);
    when(function1.apply(
            any(ControllerInterface.class),
            any(ProxyParamsInterface.class),
            any(ServerTransaction.class),
            any(SIPRequest.class)))
        .thenThrow(new InternalProxyErrorException("test error"));
    when(proxyFactoryMock.proxyTransaction()).thenReturn(function1);

    doNothing().when(serverTransaction).sendResponse(any(Response.class));

    LocateSIPServersResponse locateSIPServersResponse = mock(LocateSIPServersResponse.class);
    when(locateSIPServersResponse.getHops()).thenReturn(Collections.emptyList());

    when(sipServerLocatorService.locateDestinationAsync(
            nullable(User.class), any(SipDestination.class)))
        .thenReturn(CompletableFuture.completedFuture(locateSIPServersResponse));

    proxyController.onNewRequest(proxySIPRequest).block();
  }

  @Test(
      description =
          "test proxy client creation for outgoing ACK request - mid-dialog."
              + "This test covers failure scenario where proxy is not able to forward request since outgoing network is not set"
              + "Route headers are not set in this case.We should not send back any error response to client for ACK")
  public void testOutgoingACKRequestFailureProxyTransaction() throws SipException {

    ServerTransaction serverTransaction = mock(ServerTransaction.class);

    ProxySIPRequest proxySIPRequest =
        getProxySipRequest(SIPRequestBuilder.RequestMethod.ACK, serverTransaction);
    proxySIPRequest.setAppRecord(new DhruvaAppRecord());
    SIPRequest sipRequest = proxySIPRequest.getRequest();
    sipRequest.getRouteHeaders().clear();

    ProxyController proxyController = getProxyController(proxySIPRequest);

    doNothing().when(serverTransaction).setApplicationData(any(ProxyTransaction.class));

    ClientTransaction clientTransaction = mock(ClientTransaction.class);

    when(outgoingSipProvider.getNewClientTransaction(any(Request.class)))
        .thenReturn(clientTransaction);

    Router router = mock(Router.class);
    when(outgoingSipProvider.getSipStack()).thenReturn(sipStack);
    when(sipStack.getRouter()).thenReturn(router);
    when(router.getNextHop(any(Request.class))).thenReturn(mock(Hop.class));

    proxySIPRequest = proxyController.onNewRequest(proxySIPRequest).block();

    CompletableFuture<ProxySIPResponse> responseCF = proxyController.proxyRequest(proxySIPRequest);
    Assert.assertTrue(responseCF.isCompletedExceptionally());
  }

  @Test(description = "create ProxySIPResponse with valid response code")
  public void testCreateResponse() throws DhruvaException, ParseException {
    int respCode = Response.SERVICE_UNAVAILABLE;
    String details = "Service Unavailable";
    ServerTransaction serverTransaction = mock(ServerTransaction.class);
    ProxyTransaction proxyTransaction = mock(ProxyTransaction.class);
    ProxyServerTransaction proxyServerTransaction = mock(ProxyServerTransaction.class);
    ProxySIPRequest proxySIPRequest =
        getProxySipRequest(SIPRequestBuilder.RequestMethod.INVITE, serverTransaction);
    ProxyController proxyController = getProxyController(proxySIPRequest);
    proxyController.setProxyTransaction(proxyTransaction);
    when(proxyTransaction.getServerTransaction()).thenReturn(proxyServerTransaction);

    ProxySIPResponse proxySIPResponse =
        proxyController.createResponse(respCode, proxySIPRequest, details);

    Assert.assertEquals(proxySIPResponse.getStatusCode(), respCode);
    verify(proxyServerTransaction, times(1)).setInternallyGeneratedResponse(eq(true));
    verify(proxyServerTransaction, times(1)).setAdditionalDetails(eq(details));
    Assert.assertEquals(proxySIPResponse.getProxyInterface(), proxyController);
  }

  @Test(
      description = "Exception while creating proxySIPResponse",
      expectedExceptions = DhruvaException.class)
  public void testCreateResponseException() throws DhruvaException, ParseException {
    int respCode = Response.SERVICE_UNAVAILABLE;
    String details = "Service Unavailable";
    ServerTransaction serverTransaction = mock(ServerTransaction.class);
    ProxySIPRequest proxySIPRequest =
        getProxySipRequest(SIPRequestBuilder.RequestMethod.INVITE, serverTransaction);
    ProxyController proxyController = getProxyController(proxySIPRequest);
    proxyController.createResponse(respCode, proxySIPRequest, details);
  }
}
