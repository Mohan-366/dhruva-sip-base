package com.cisco.dsb.proxy.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

import com.cisco.dsb.common.CallType;
import com.cisco.dsb.common.config.sip.DhruvaSIPConfigProperties;
import com.cisco.dsb.common.context.ExecutionContext;
import com.cisco.dsb.common.dto.Destination;
import com.cisco.dsb.common.exception.DhruvaException;
import com.cisco.dsb.common.exception.DhruvaRuntimeException;
import com.cisco.dsb.common.executor.DhruvaExecutorService;
import com.cisco.dsb.common.executor.ExecutorType;
import com.cisco.dsb.common.messaging.models.AbstractSipRequest;
import com.cisco.dsb.common.service.SipServerLocatorService;
import com.cisco.dsb.common.service.TrunkService;
import com.cisco.dsb.common.sip.bean.SIPListenPoint;
import com.cisco.dsb.common.sip.bean.SIPProxy;
import com.cisco.dsb.common.sip.jain.JainSipHelper;
import com.cisco.dsb.common.sip.stack.dto.DhruvaNetwork;
import com.cisco.dsb.common.sip.stack.dto.LocateSIPServersResponse;
import com.cisco.dsb.common.sip.stack.dto.SipDestination;
import com.cisco.dsb.common.sip.util.EndPoint;
import com.cisco.dsb.common.sip.util.SupportedExtensions;
import com.cisco.dsb.common.transport.Transport;
import com.cisco.dsb.common.util.SpringApplicationContext;
import com.cisco.dsb.proxy.ControllerInterface;
import com.cisco.dsb.proxy.dto.ProxyAppConfig;
import com.cisco.dsb.proxy.errors.InternalProxyErrorException;
import com.cisco.dsb.proxy.messaging.DhruvaSipRequestMessage;
import com.cisco.dsb.proxy.messaging.ProxySIPRequest;
import com.cisco.dsb.proxy.messaging.ProxySIPResponse;
import com.cisco.dsb.proxy.sip.ProxyFactory;
import com.cisco.dsb.proxy.sip.ProxyParamsInterface;
import com.cisco.dsb.proxy.sip.ProxyStatelessTransaction;
import com.cisco.dsb.proxy.sip.ProxyTransaction;
import com.cisco.dsb.proxy.util.QuadFunction;
import com.cisco.dsb.proxy.util.SIPRequestBuilder;
import com.cisco.wx2.dto.User;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import gov.nist.javax.sip.header.Allow;
import gov.nist.javax.sip.header.RecordRouteList;
import gov.nist.javax.sip.message.SIPRequest;
import gov.nist.javax.sip.message.SIPResponse;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.text.ParseException;
import java.util.Collections;
import java.util.ListIterator;
import java.util.concurrent.*;
import javax.sip.*;
import javax.sip.address.Hop;
import javax.sip.address.Router;
import javax.sip.address.SipURI;
import javax.sip.address.URI;
import javax.sip.header.*;
import javax.sip.message.Request;
import javax.sip.message.Response;
import org.mockito.*;
import org.springframework.context.ApplicationContext;
import org.testng.Assert;
import org.testng.annotations.*;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

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

  @Mock DhruvaSIPConfigProperties dhruvaSIPConfigProperties;

  @Mock SipServerLocatorService sipServerLocatorService;

  @Mock TrunkService trunkService;

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
    MockitoAnnotations.initMocks(this);

    sipStack = mock(SipStack.class);

    SIPListenPoint sipListenPoint1 = createIncomingTCPSipListenPoint();
    SIPListenPoint sipListenPoint2 = createOutgoingTCPSipListenPoint();
    SIPListenPoint sipListenPoint3 = createTestSipListenPoint();

    incomingNetwork = DhruvaNetwork.createNetwork("net_sp_tcp", sipListenPoint1);
    outgoingNetwork = DhruvaNetwork.createNetwork("net_internal_tcp", sipListenPoint2);
    testNetwork = DhruvaNetwork.createNetwork("test_net", sipListenPoint3);

    proxyFactory = new ProxyFactory();
    controllerConfig = new ControllerConfig(sipServerLocatorService, dhruvaSIPConfigProperties);
    // dhruvaSIPConfigProperties = new DhruvaSIPConfigProperties();
    dhruvaExecutorService = mock(DhruvaExecutorService.class);
    scheduledExecutor = mock(ScheduledThreadPoolExecutor.class);

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
        sipListenPoint3.getPort(),
        sipListenPoint3.getTransport(),
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

    DhruvaNetwork.setDhruvaConfigProperties(dhruvaSIPConfigProperties);

    when(dhruvaSIPConfigProperties.isHostPortEnabled()).thenReturn(false);
    SIPProxy sipProxy = mock(SIPProxy.class);
    when(sipProxy.getTimerCIntervalInMilliSec()).thenReturn((long) 2);
    when(dhruvaSIPConfigProperties.getSIPProxy()).thenReturn(sipProxy);

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

  public SIPListenPoint createIncomingTCPSipListenPoint() throws JsonProcessingException {
    String json =
        "{ \"name\": \"net_sp_tcp\", \"hostIPAddress\": \"1.1.1.1\", \"port\": 5060, \"transport\": \"TCP\", "
            + "\"attachExternalIP\": \"false\", \"recordRoute\": \"true\"}";
    return new ObjectMapper().readerFor(SIPListenPoint.class).readValue(json);
  }

  public SIPListenPoint createOutgoingTCPSipListenPoint() throws JsonProcessingException {
    String json =
        "{ \"name\": \"net_internal_tcp\", \"hostIPAddress\": \"2.2.2.2\", \"port\": 5080, \"transport\": \"TCP\", "
            + "\"attachExternalIP\": \"false\", \"recordRoute\": \"true\"}";
    return new ObjectMapper().readerFor(SIPListenPoint.class).readValue(json);
  }

  public SIPListenPoint createTestSipListenPoint() throws JsonProcessingException {
    String json =
        "{ \"name\": \"test_net\", \"hostIPAddress\": \"3.3.3.3\", \"port\": 5080, \"transport\": \"TCP\", "
            + "\"attachExternalIP\": \"false\", \"recordRoute\": \"true\"}";
    return new ObjectMapper().readerFor(SIPListenPoint.class).readValue(json);
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
    ProxyController controller =
        proxyControllerFactory
            .proxyController()
            .apply(
                proxySIPRequest.getServerTransaction(),
                proxySIPRequest.getProvider(),
                proxyAppConfig);
    return controller;
  }

  @BeforeMethod
  public void setup() throws SipException {
    reset(outgoingSipProvider);
    reset(incomingSipProvider);
    reset(sipServerLocatorService);
    reset(sipStack);
    reset(trunkService);
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
    controllerConfig.addListenInterface(
        udpNetworkIncoming,
        InetAddress.getByName(sipListenPointUdpIncoming.getHostIPAddress()),
        sipListenPointUdpIncoming.getPort(),
        sipListenPointUdpIncoming.getTransport(),
        InetAddress.getByName(sipListenPointUdpIncoming.getHostIPAddress()),
        false);
    controllerConfig.addListenInterface(
        udpNetworkOutgoing,
        InetAddress.getByName(sipListenPointUdpOutgoing.getHostIPAddress()),
        sipListenPointUdpOutgoing.getPort(),
        sipListenPointUdpOutgoing.getTransport(),
        InetAddress.getByName(sipListenPointUdpOutgoing.getHostIPAddress()),
        false);
    controllerConfig.addListenInterface(
        tcpNetworkIncoming,
        InetAddress.getByName(sipListenPointTcpIncoming.getHostIPAddress()),
        sipListenPointTcpIncoming.getPort(),
        sipListenPointTcpIncoming.getTransport(),
        InetAddress.getByName(sipListenPointTcpIncoming.getHostIPAddress()),
        false);
    controllerConfig.addListenInterface(
        tcpNetworkOutgoing,
        InetAddress.getByName(sipListenPointTcpOutgoing.getHostIPAddress()),
        sipListenPointTcpOutgoing.getPort(),
        sipListenPointTcpOutgoing.getTransport(),
        InetAddress.getByName(sipListenPointTcpOutgoing.getHostIPAddress()),
        false);
    controllerConfig.addListenInterface(
        tlsNetworkIncoming,
        InetAddress.getByName(sipListenPointTlsIncoming.getHostIPAddress()),
        sipListenPointTlsIncoming.getPort(),
        sipListenPointTlsIncoming.getTransport(),
        InetAddress.getByName(sipListenPointTlsIncoming.getHostIPAddress()),
        false);
    controllerConfig.addListenInterface(
        tlsNetworkOutgoing,
        InetAddress.getByName(sipListenPointTlsOutgoing.getHostIPAddress()),
        sipListenPointTlsOutgoing.getPort(),
        sipListenPointTlsOutgoing.getTransport(),
        InetAddress.getByName(sipListenPointTlsOutgoing.getHostIPAddress()),
        false);
    controllerConfig.addRecordRouteInterface(
        InetAddress.getByName(sipListenPointUdpIncoming.getHostIPAddress()),
        sipListenPointUdpIncoming.getPort(),
        sipListenPointUdpIncoming.getTransport(),
        udpNetworkIncoming);
    controllerConfig.addRecordRouteInterface(
        InetAddress.getByName(sipListenPointUdpOutgoing.getHostIPAddress()),
        sipListenPointUdpOutgoing.getPort(),
        sipListenPointUdpOutgoing.getTransport(),
        udpNetworkOutgoing);
    controllerConfig.addRecordRouteInterface(
        InetAddress.getByName(sipListenPointTcpIncoming.getHostIPAddress()),
        sipListenPointTcpIncoming.getPort(),
        sipListenPointTcpIncoming.getTransport(),
        tcpNetworkIncoming);
    controllerConfig.addRecordRouteInterface(
        InetAddress.getByName(sipListenPointTcpOutgoing.getHostIPAddress()),
        sipListenPointTcpOutgoing.getPort(),
        sipListenPointTcpOutgoing.getTransport(),
        tcpNetworkOutgoing);
    controllerConfig.addRecordRouteInterface(
        InetAddress.getByName(sipListenPointTlsIncoming.getHostIPAddress()),
        sipListenPointTlsIncoming.getPort(),
        sipListenPointTlsIncoming.getTransport(),
        tlsNetworkIncoming);
    controllerConfig.addRecordRouteInterface(
        InetAddress.getByName(sipListenPointTlsOutgoing.getHostIPAddress()),
        sipListenPointTlsOutgoing.getPort(),
        sipListenPointTlsOutgoing.getTransport(),
        tlsNetworkOutgoing);

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
      throws SipException, ExecutionException, InterruptedException {

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
    ProxyController proxyController = getProxyController(proxySIPRequest);

    doNothing().when(serverTransaction).setApplicationData(any(ProxyTransaction.class));

    ClientTransaction clientTransaction = mock(ClientTransaction.class);
    doNothing().when(clientTransaction).sendRequest();

    when(outgoingSipProvider1.getNewClientTransaction(any(Request.class)))
        .thenReturn(clientTransaction);

    LocateSIPServersResponse locateSIPServersResponse = mock(LocateSIPServersResponse.class);
    when(locateSIPServersResponse.getHops()).thenReturn(Collections.emptyList());

    when(sipServerLocatorService.locateDestination(
            nullable(User.class), any(SipDestination.class), nullable(String.class)))
        .thenReturn(locateSIPServersResponse);

    proxySIPRequest = proxyController.onNewRequest(proxySIPRequest);

    Destination destination =
        Destination.builder()
            .uri(proxySIPRequest.getRequest().getRequestURI())
            .network(outgoingNetwork1)
            .build();

    // proxyController.proxyRequest(proxySIPRequest, location);
    // using stepVerifier
    proxySIPRequest.setDestination(destination);
    SIPRequest preprocessedRequest = (SIPRequest) proxySIPRequest.getRequest().clone();
    proxyController.setPreprocessedRequest(preprocessedRequest);
    proxySIPRequest.setClonedRequest((SIPRequest) preprocessedRequest.clone());

    // Mock Trunk Service
    EndPoint endPoint = new EndPoint(outgoingNetwork1.getName(), "9.9.9.9", 5061, Transport.TLS);
    when(trunkService.getElementAsync(any(AbstractSipRequest.class), any(Destination.class)))
        .thenReturn(Mono.just(endPoint));

    StepVerifier.create(
            proxyController.proxyForwardRequest(
                destination, proxySIPRequest, proxyController.timeToTry))
        .assertNext(
            request -> {
              assert request.getProxyClientTransaction() != null;
              assert request.getProxyStatelessTransaction() != null;
              assert request.getOutgoingNetwork().equals(outgoingNetwork1.getName());
              assert request.getDestination().getUri() == request.getRequest().getRequestURI();
              assert request.getClonedRequest() != null;
              assert request.getClonedRequest().getTopmostViaHeader().getHost().equals("2.2.2.2");
              assert request.getClonedRequest().getTopmostViaHeader().getPort() == finalOutPort;

              // Outgoing record route header validation
              RecordRouteList recordRouteList = request.getClonedRequest().getRecordRouteHeaders();
              RecordRouteHeader recordRouteHeader = (RecordRouteHeader) recordRouteList.getFirst();
              URI uri = recordRouteHeader.getAddress().getURI();
              assert uri.isSipURI();
              SipURI routeUri = (SipURI) uri;
              assert routeUri.getTransportParam().equals(finalProtocol);
              assert routeUri.getPort() == outgoingNetwork1.getListenPoint().getPort();
              assert routeUri
                  .getHost()
                  .equals(outgoingNetwork1.getListenPoint().getHostIPAddress());
              assert routeUri.hasLrParam();
              assert routeUri.getUser().equals("rr$n=" + incomingNetwork1.getName());
            })
        .verifyComplete();

    ArgumentCaptor<Request> argumentCaptor = ArgumentCaptor.forClass(Request.class);
    verify(outgoingSipProvider1).getNewClientTransaction(argumentCaptor.capture());

    SIPRequest sendRequest = (SIPRequest) argumentCaptor.getValue();

    Assert.assertNotNull(sendRequest);
    Assert.assertEquals(sendRequest.getMethod(), Request.INVITE);
    Assert.assertEquals(sendRequest, proxySIPRequest.getClonedRequest());

    verify(clientTransaction).sendRequest();

    // Verify that we set the proxy object in applicationData of jain for future response
    ArgumentCaptor<ProxyTransaction> appArgumentCaptor =
        ArgumentCaptor.forClass(ProxyTransaction.class);
    verify(clientTransaction).setApplicationData(appArgumentCaptor.capture());

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

    Destination destination =
        Destination.builder()
            .uri(proxySIPRequest.getRequest().getRequestURI())
            .network(outgoingNetwork)
            .build();
    // setProcessRoute: lrescape will be invoked, it will put the top route header to requri and
    // requri to bottom of route list
    // proxyparams will be not be set before that.
    // This is set only  for mid dialog request by passing application

    proxySIPRequest.setDestination(destination);
    SIPRequest preprocessedRequest = (SIPRequest) proxySIPRequest.getRequest().clone();
    proxyController.setPreprocessedRequest(preprocessedRequest);
    proxySIPRequest.setClonedRequest((SIPRequest) preprocessedRequest.clone());

    // Mock Trunk Service
    EndPoint endPoint = new EndPoint(outgoingNetwork.getName(), "9.9.9.9", 5061, Transport.TLS);
    when(trunkService.getElementAsync(any(AbstractSipRequest.class), any(Destination.class)))
        .thenReturn(Mono.just(endPoint));

    StepVerifier.create(
            proxyController.proxyForwardRequest(
                destination, proxySIPRequest, proxyController.timeToTry))
        .verifyErrorMatches(err -> err instanceof SipException);

    verify(clientTransaction).sendRequest();
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
    when(outgoingSipProvider.getNewClientTransaction(any(Request.class)))
        .thenReturn(clientTransaction);
    DhruvaNetwork.removeSipProvider(outgoingNetwork.getName());
    LocateSIPServersResponse locateSIPServersResponse = mock(LocateSIPServersResponse.class);
    when(locateSIPServersResponse.getHops()).thenReturn(Collections.emptyList());

    when(sipServerLocatorService.locateDestination(
            nullable(User.class), any(SipDestination.class), nullable(String.class)))
        .thenReturn(locateSIPServersResponse);

    proxySIPRequest = proxyController.onNewRequest(proxySIPRequest);

    Destination destination =
        Destination.builder()
            .uri(proxySIPRequest.getRequest().getRequestURI())
            .network(testNetwork)
            .build();

    proxySIPRequest.setDestination(destination);
    SIPRequest preprocessedRequest = (SIPRequest) proxySIPRequest.getRequest().clone();
    proxyController.setPreprocessedRequest(preprocessedRequest);
    proxySIPRequest.setClonedRequest((SIPRequest) preprocessedRequest.clone());

    // Mock Trunk Service
    EndPoint endPoint = new EndPoint(outgoingNetwork.getName(), "9.9.9.9", 5061, Transport.TLS);
    when(trunkService.getElementAsync(any(AbstractSipRequest.class), any(Destination.class)))
        .thenReturn(Mono.just(endPoint));

    StepVerifier.create(
            proxyController.proxyForwardRequest(
                destination, proxySIPRequest, proxyController.timeToTry))
        .verifyErrorMatches(err -> err instanceof DhruvaRuntimeException);

    verify(clientTransaction, never()).sendRequest();
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
    ListIterator existingRoutes = sipRequest.getHeaders(RouteHeader.NAME);
    if (existingRoutes != null && existingRoutes.hasNext()) {
      while (existingRoutes.hasNext()) {
        sipRequest.removeHeader(RouteHeader.NAME);
        existingRoutes.next();
      }
    }

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

    Destination destination = Destination.builder().uri(proxySIPRequest.getLrFixUri()).build();
    // Do not set outgoing network, proxy should derive
    // location.setNetwork(outgoingNetwork);
    // proxyController.proxyRequest(proxySIPRequest, location);
    // using stepVerifier
    proxySIPRequest.setDestination(destination);
    SIPRequest preprocessedRequest = (SIPRequest) proxySIPRequest.getRequest().clone();
    proxyController.setPreprocessedRequest(preprocessedRequest);
    proxySIPRequest.setClonedRequest((SIPRequest) preprocessedRequest.clone());

    StepVerifier.create(
            proxyController.proxyForwardRequest(
                destination, proxySIPRequest, proxyController.timeToTry))
        .assertNext(
            request -> {
              assert request.getProxyClientTransaction() == null;
              assert request.getProxyStatelessTransaction() != null;
              assert request.getOutgoingNetwork().equals(outgoingNetwork.getName());

              assert request.getClonedRequest() != null;
              SipURI reqSipUri = (SipURI) request.getClonedRequest().getRequestURI();
              SipURI locSipUri = (SipURI) request.getDestination().getUri();

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
                assert routeValue.getTransportParam().equalsIgnoreCase("tcp");
              }
            })
        .verifyComplete();

    ArgumentCaptor<Request> argumentCaptor = ArgumentCaptor.forClass(Request.class);
    verify(outgoingSipProvider, never()).getNewClientTransaction(argumentCaptor.capture());

    verify(clientTransaction, never()).sendRequest();
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

    Destination destination =
        Destination.builder()
            .uri(proxySIPRequest.getRequest().getRequestURI())
            .network(outgoingNetwork)
            .build();
    // proxyController.proxyRequest(proxySIPRequest, location);
    // using stepVerifier
    proxySIPRequest.setDestination(destination);
    SIPRequest preprocessedRequest = (SIPRequest) proxySIPRequest.getRequest().clone();
    proxyController.setPreprocessedRequest(preprocessedRequest);
    proxySIPRequest.setClonedRequest((SIPRequest) preprocessedRequest.clone());

    // Mock Trunk Service
    EndPoint endPoint = new EndPoint(outgoingNetwork.getName(), "9.9.9.9", 5061, Transport.TLS);
    when(trunkService.getElementAsync(any(AbstractSipRequest.class), any(Destination.class)))
        .thenReturn(Mono.just(endPoint));
    StepVerifier.create(
            proxyController.proxyForwardRequest(
                destination, proxySIPRequest, proxyController.timeToTry))
        .assertNext(
            request -> {
              assert request.getProxyClientTransaction() != null;
              assert request.getProxyStatelessTransaction() != null;
              assert request.getOutgoingNetwork().equals(outgoingNetwork.getName());
              assert request.getDestination().getUri() == request.getRequest().getRequestURI();
              assert request.getClonedRequest() != null;
              assert request.getClonedRequest().getTopmostViaHeader().getHost().equals("2.2.2.2");
              assert request.getClonedRequest().getTopmostViaHeader().getPort() == 5080;

              // record route should not be added to outgoing BYE request
              RecordRouteList recordRouteList = request.getClonedRequest().getRecordRouteHeaders();
              assert recordRouteList == null;
            })
        .verifyComplete();

    ArgumentCaptor<Request> argumentCaptor = ArgumentCaptor.forClass(Request.class);
    verify(outgoingSipProvider).getNewClientTransaction(argumentCaptor.capture());

    SIPRequest sendRequest = (SIPRequest) argumentCaptor.getValue();

    Assert.assertNotNull(sendRequest);
    Assert.assertEquals(sendRequest.getMethod(), Request.BYE);
    Assert.assertEquals(sendRequest, proxySIPRequest.getClonedRequest());

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

    proxySIPRequest = proxyController.onNewRequest(proxySIPRequest);
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
  public void testIncomingRequestNoLrFix()
      throws ParseException, ExecutionException, InterruptedException {

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
    URI uri = JainSipHelper.createSipURI("testDhruva@9.9.9.9:5060;transport=tcp");
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
    URI uri = JainSipHelper.createSipURI("testDhruva@9.9.9.9:5060;transport=tcp");
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
    verify(serverTransaction).setApplicationData(argumentCaptor.capture());

    ProxyTransaction proxyTransaction = argumentCaptor.getValue();

    Assert.assertEquals(proxyTransaction, proxySIPRequest.getProxyStatelessTransaction());
  }

  @DataProvider
  public Object[] toggleValues() {
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
              .concat(Request.SUBSCRIBE)
              .concat(",")
              .concat(Request.REFER);

      when(dhruvaSIPConfigProperties.getAllowedMethods()).thenReturn(allowedMethods);

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

    ProxySIPRequest proxySIPRequest =
        getProxySipRequest(SIPRequestBuilder.RequestMethod.CANCEL, st);
    ProxyController proxyController = getProxyController(proxySIPRequest);

    ArgumentCaptor<Response> captor = ArgumentCaptor.forClass(Response.class);

    doNothing().when(st).sendResponse(any(Response.class));

    Assert.assertEquals(proxyController.handleRequest().apply(proxySIPRequest), proxySIPRequest);

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

    ProxyController proxyController = getProxyController(proxySIPRequest);
    proxyController.setCancelBranchesAutomatically(cancelBranches);

    proxyController.onSuccessResponse(proxyTransaction, proxySIPResponse);

    if (cancelBranches) {
      verify(proxyTransaction).cancel();
    } else {
      verify(proxyTransaction, never()).cancel();
    }
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
    when(proxyTransaction.getBestResponse()).thenReturn(proxySIPResponse);

    ProxyController proxyController = getProxyController(proxySIPRequest);
    proxyController.setCancelBranchesAutomatically(cancelBranches);

    proxyController.onGlobalFailureResponse(proxyTransaction);

    if (cancelBranches) {
      verify(proxyTransaction).cancel();
    } else {
      verify(proxyTransaction, never()).cancel();
    }
  }

  @Test(description = "An incoming OPTIONS request to the proxy must be responded with a 200 OK")
  public void testOptionsHandling() throws SipException, InvalidArgumentException {

    ServerTransaction st = mock(ServerTransaction.class);

    ProxySIPRequest proxySIPRequest =
        getProxySipRequest(SIPRequestBuilder.RequestMethod.OPTIONS, st);
    System.out.println("SIP Request :  " + proxySIPRequest.getRequest());
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

    when(dhruvaSIPConfigProperties.getAllowedMethods()).thenReturn(allowedMethods);

    ArgumentCaptor<Response> captor = ArgumentCaptor.forClass(Response.class);

    doNothing().when(st).sendResponse(any(Response.class));

    Assert.assertNull(proxyController.handleRequest().apply(proxySIPRequest));

    verify(st).sendResponse(captor.capture());
    Response response = captor.getValue();
    System.out.println("Response: " + response);
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

  @Test(description = "test proxy service and trunk service interaction for static server group")
  public void testTrunkServiceInvocationStaticServerGroupRouting()
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

    Destination destination =
        Destination.builder()
            .uri(proxySIPRequest.getRequest().getRequestURI())
            .destinationType(Destination.DestinationType.SERVER_GROUP)
            .address("cisco.webex.com")
            .network(outgoingNetwork)
            .build();

    // proxyController.proxyRequest(proxySIPRequest, location);
    // using stepVerifier
    proxySIPRequest.setDestination(destination);
    SIPRequest preprocessedRequest = (SIPRequest) proxySIPRequest.getRequest().clone();
    proxyController.setPreprocessedRequest(preprocessedRequest);
    proxySIPRequest.setClonedRequest((SIPRequest) preprocessedRequest.clone());

    // Mock Trunk Service
    EndPoint endPoint = new EndPoint(outgoingNetwork.getName(), "5.5.5.5", 5061, Transport.TLS);
    when(trunkService.getElementAsync(proxySIPRequest, destination))
        .thenReturn(Mono.just(endPoint));

    StepVerifier.create(
            proxyController.proxyForwardRequest(
                destination, proxySIPRequest, proxyController.timeToTry))
        .assertNext(
            request -> {
              assert request.getProxyClientTransaction() != null;
              assert request.getProxyStatelessTransaction() != null;
              assert request.getOutgoingNetwork().equals(outgoingNetwork.getName());
              assert request.getDestination().getUri() == request.getRequest().getRequestURI();
              assert request.getClonedRequest() != null;
              assert request.getClonedRequest().getTopmostViaHeader().getHost().equals("2.2.2.2");
              assert request.getClonedRequest().getTopmostViaHeader().getPort() == 5080;

              // Outgoing record route header validation
              RecordRouteList recordRouteList = request.getClonedRequest().getRecordRouteHeaders();
              RecordRouteHeader recordRouteHeader = (RecordRouteHeader) recordRouteList.getFirst();
              URI uri = recordRouteHeader.getAddress().getURI();
              assert uri.isSipURI();
              SipURI routeUri = (SipURI) uri;
              assert routeUri.getTransportParam().equals("tcp");
              assert routeUri.getPort() == outgoingNetwork.getListenPoint().getPort();
              assert routeUri.getHost().equals(outgoingNetwork.getListenPoint().getHostIPAddress());
              assert routeUri.hasLrParam();
              assert routeUri.getUser().equals("rr$n=" + incomingNetwork.getName());
            })
        .verifyComplete();

    ArgumentCaptor<Destination> argumentCaptorDestination =
        ArgumentCaptor.forClass(Destination.class);
    ArgumentCaptor<AbstractSipRequest> argumentCaptorRequest =
        ArgumentCaptor.forClass(AbstractSipRequest.class);
    verify(trunkService)
        .getElementAsync(argumentCaptorRequest.capture(), argumentCaptorDestination.capture());

    Destination destination1 = argumentCaptorDestination.getValue();
    Assert.assertEquals(destination, destination1);

    AbstractSipRequest abstractSipRequest = argumentCaptorRequest.getValue();
    Assert.assertEquals(proxySIPRequest, abstractSipRequest);

    ArgumentCaptor<Request> argumentCaptor = ArgumentCaptor.forClass(Request.class);
    verify(outgoingSipProvider).getNewClientTransaction(argumentCaptor.capture());

    SIPRequest sendRequest = (SIPRequest) argumentCaptor.getValue();

    Assert.assertNotNull(sendRequest);
    Assert.assertEquals(sendRequest.getMethod(), Request.INVITE);
    Assert.assertEquals(sendRequest, proxySIPRequest.getClonedRequest());

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
          "test proxy service and trunk service interaction when trunk service returns Mono.error ,"
              + "proxy should send 502 since its unable to find right SG")
  public void testTrunkServiceExceptionHandling()
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

    Destination destination =
        Destination.builder()
            .uri(proxySIPRequest.getRequest().getRequestURI())
            .destinationType(Destination.DestinationType.SERVER_GROUP)
            .address("webex.com")
            .network(outgoingNetwork)
            .build();

    // proxyController.proxyRequest(proxySIPRequest, location);
    // using stepVerifier
    proxySIPRequest.setDestination(destination);
    SIPRequest preprocessedRequest = (SIPRequest) proxySIPRequest.getRequest().clone();
    proxyController.setPreprocessedRequest(preprocessedRequest);
    proxySIPRequest.setClonedRequest((SIPRequest) preprocessedRequest.clone());

    // Mock Trunk Service, return exception

    when(trunkService.getElementAsync(proxySIPRequest, destination))
        .thenReturn(Mono.error(new DhruvaException("test")));

    // TODO Akshay this needs to be enhanced when we support proper error handling
    StepVerifier.create(
            proxyController.proxyForwardRequest(
                destination, proxySIPRequest, proxyController.timeToTry))
        .expectError()
        .verify();
  }

  @Test(description = "test proxy service and trunk service interaction for dynamic server group")
  public void testTrunkServiceInvocationDynamicServerGroupRouting()
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

    // REQURI is non IP and we have choosen default SIP routing
    Destination destination =
        Destination.builder()
            .uri(proxySIPRequest.getRequest().getRequestURI())
            .destinationType(Destination.DestinationType.DEFAULT_SIP)
            .network(outgoingNetwork)
            .build();

    // proxyController.proxyRequest(proxySIPRequest, location);
    // using stepVerifier
    proxySIPRequest.setDestination(destination);
    SIPRequest preprocessedRequest = (SIPRequest) proxySIPRequest.getRequest().clone();
    proxyController.setPreprocessedRequest(preprocessedRequest);
    proxySIPRequest.setClonedRequest((SIPRequest) preprocessedRequest.clone());

    // Mock Trunk Service
    EndPoint endPoint = new EndPoint(outgoingNetwork.getName(), "10.1.1.1", 5061, Transport.TLS);
    when(trunkService.getElementAsync(proxySIPRequest, destination))
        .thenReturn(Mono.just(endPoint));

    StepVerifier.create(
            proxyController.proxyForwardRequest(
                destination, proxySIPRequest, proxyController.timeToTry))
        .assertNext(
            request -> {
              assert request.getProxyClientTransaction() != null;
              assert request.getProxyStatelessTransaction() != null;
              assert request.getOutgoingNetwork().equals(outgoingNetwork.getName());
              assert request.getDestination().getUri() == request.getRequest().getRequestURI();
              assert request.getClonedRequest() != null;
              assert request.getClonedRequest().getTopmostViaHeader().getHost().equals("2.2.2.2");
              assert request.getClonedRequest().getTopmostViaHeader().getPort() == 5080;

              // Outgoing record route header validation
              RecordRouteList recordRouteList = request.getClonedRequest().getRecordRouteHeaders();
              RecordRouteHeader recordRouteHeader = (RecordRouteHeader) recordRouteList.getFirst();
              URI uri = recordRouteHeader.getAddress().getURI();
              assert uri.isSipURI();
              SipURI routeUri = (SipURI) uri;
              assert routeUri.getTransportParam().equals("tcp");
              assert routeUri.getPort() == outgoingNetwork.getListenPoint().getPort();
              assert routeUri.getHost().equals(outgoingNetwork.getListenPoint().getHostIPAddress());
              assert routeUri.hasLrParam();
              assert routeUri.getUser().equals("rr$n=" + incomingNetwork.getName());
            })
        .verifyComplete();

    ArgumentCaptor<Destination> argumentCaptorDestination =
        ArgumentCaptor.forClass(Destination.class);
    ArgumentCaptor<AbstractSipRequest> argumentCaptorRequest =
        ArgumentCaptor.forClass(AbstractSipRequest.class);
    verify(trunkService)
        .getElementAsync(argumentCaptorRequest.capture(), argumentCaptorDestination.capture());

    Destination destination1 = argumentCaptorDestination.getValue();
    Assert.assertEquals(destination, destination1);
    Destination.DestinationType destinationType = destination1.getDestinationType();
    Assert.assertEquals(destinationType, Destination.DestinationType.SRV);
  }

  @Test(
      description =
          "test proxy service and trunk service interaction for dynamic server group in mid dialog case having Route header")
  public void testTrunkServiceInvocationDynamicServerGroupMidDialogRouting()
      throws SipException, ExecutionException, InterruptedException, ParseException {

    ServerTransaction serverTransaction = mock(ServerTransaction.class);

    ProxySIPRequest proxySIPRequest =
        getProxySipRequest(SIPRequestBuilder.RequestMethod.INVITE, serverTransaction);
    ProxyController proxyController = getProxyController(proxySIPRequest);

    doNothing().when(serverTransaction).setApplicationData(any(ProxyTransaction.class));
    SIPRequest sipRequest = proxySIPRequest.getRequest();

    ListIterator existingRoutes = sipRequest.getHeaders(RouteHeader.NAME);
    if (existingRoutes != null && existingRoutes.hasNext()) {
      while (existingRoutes.hasNext()) {
        sipRequest.removeHeader(RouteHeader.NAME);
        existingRoutes.next();
      }
    }

    RouteHeader ownRouteHeader =
        JainSipHelper.createRouteHeader("rr$n=net_internal_tcp", "1.1.1.1", 5060, "tcp");
    RouteHeader routeHeader1 =
        JainSipHelper.createRouteHeader("testDhruva", "alpha.xyz.com", 5080, "tcp");

    sipRequest.addHeader(routeHeader1);
    sipRequest.addFirst(ownRouteHeader);

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

    // Route has non IP uri and we have choosen default SIP routing
    // Precedence is given to Route URI
    Destination destination =
        Destination.builder()
            .uri(proxySIPRequest.getRequest().getRequestURI())
            .destinationType(Destination.DestinationType.DEFAULT_SIP)
            .build();

    // using stepVerifier
    proxySIPRequest.setDestination(destination);
    SIPRequest preprocessedRequest = (SIPRequest) proxySIPRequest.getRequest().clone();
    proxyController.setPreprocessedRequest(preprocessedRequest);
    proxySIPRequest.setClonedRequest((SIPRequest) preprocessedRequest.clone());

    // Mock Trunk Service
    EndPoint endPoint = new EndPoint(outgoingNetwork.getName(), "10.1.1.1", 5061, Transport.TLS);
    when(trunkService.getElementAsync(proxySIPRequest, destination))
        .thenReturn(Mono.just(endPoint));

    StepVerifier.create(
            proxyController.proxyForwardRequest(
                destination, proxySIPRequest, proxyController.timeToTry))
        .assertNext(
            request -> {
              assert request.getProxyClientTransaction() != null;
              assert request.getProxyStatelessTransaction() != null;
              assert request.getOutgoingNetwork().equals(outgoingNetwork.getName());
              assert request.getDestination().getUri() == request.getRequest().getRequestURI();
              assert request.getClonedRequest() != null;
              assert request.getClonedRequest().getTopmostViaHeader().getHost().equals("2.2.2.2");
              assert request.getClonedRequest().getTopmostViaHeader().getPort() == 5080;
            })
        .verifyComplete();

    ArgumentCaptor<Destination> argumentCaptorDestination =
        ArgumentCaptor.forClass(Destination.class);
    ArgumentCaptor<AbstractSipRequest> argumentCaptorRequest =
        ArgumentCaptor.forClass(AbstractSipRequest.class);
    verify(trunkService)
        .getElementAsync(argumentCaptorRequest.capture(), argumentCaptorDestination.capture());

    Destination destination1 = argumentCaptorDestination.getValue();
    Assert.assertEquals(destination, destination1);
    Destination.DestinationType destinationType = destination1.getDestinationType();
    Assert.assertEquals(destinationType, Destination.DestinationType.SRV);
    Assert.assertEquals(destination1.getAddress(), "alpha.xyz.com");

    EndPoint ep = proxySIPRequest.getDownstreamElement();
    // Route address is set in endpoint
    Assert.assertEquals(ep.getHost(), "10.1.1.1");
  }

  @Test(
      description = "Mid dialog routing with IP address, no trunk service invocation should happen")
  public void testMidDialogRoutingWithNoTrunkServiceInvocation()
      throws SipException, ExecutionException, InterruptedException, ParseException {

    ServerTransaction serverTransaction = mock(ServerTransaction.class);

    ProxySIPRequest proxySIPRequest =
        getProxySipRequest(SIPRequestBuilder.RequestMethod.INVITE, serverTransaction);
    ProxyController proxyController = getProxyController(proxySIPRequest);

    doNothing().when(serverTransaction).setApplicationData(any(ProxyTransaction.class));
    SIPRequest sipRequest = proxySIPRequest.getRequest();

    ListIterator existingRoutes = sipRequest.getHeaders(RouteHeader.NAME);
    if (existingRoutes != null && existingRoutes.hasNext()) {
      while (existingRoutes.hasNext()) {
        sipRequest.removeHeader(RouteHeader.NAME);
        existingRoutes.next();
      }
    }

    RouteHeader ownRouteHeader =
        JainSipHelper.createRouteHeader("rr$n=net_internal_tcp", "1.1.1.1", 5060, "tcp");
    RouteHeader routeHeader1 =
        JainSipHelper.createRouteHeader("testDhruva", "11.1.1.1", 5080, "tcp");

    sipRequest.addHeader(routeHeader1);
    sipRequest.addFirst(ownRouteHeader);

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

    // Route has non IP uri and we have choosen default SIP routing
    // Precedence is given to Route URI
    Destination destination =
        Destination.builder()
            .uri(proxySIPRequest.getRequest().getRequestURI())
            .destinationType(Destination.DestinationType.DEFAULT_SIP)
            .build();

    // using stepVerifier
    proxySIPRequest.setDestination(destination);
    SIPRequest preprocessedRequest = (SIPRequest) proxySIPRequest.getRequest().clone();
    proxyController.setPreprocessedRequest(preprocessedRequest);
    proxySIPRequest.setClonedRequest((SIPRequest) preprocessedRequest.clone());

    // Mock Trunk Service
    EndPoint endPoint = new EndPoint(outgoingNetwork.getName(), "10.1.1.1", 5061, Transport.TLS);
    when(trunkService.getElementAsync(proxySIPRequest, destination))
        .thenReturn(Mono.just(endPoint));

    StepVerifier.create(
            proxyController.proxyForwardRequest(
                destination, proxySIPRequest, proxyController.timeToTry))
        .assertNext(
            request -> {
              assert request.getProxyClientTransaction() != null;
              assert request.getProxyStatelessTransaction() != null;
              assert request.getOutgoingNetwork().equals(outgoingNetwork.getName());
              assert request.getDestination().getUri() == request.getRequest().getRequestURI();
              assert request.getClonedRequest() != null;
              assert request.getClonedRequest().getTopmostViaHeader().getHost().equals("2.2.2.2");
              assert request.getClonedRequest().getTopmostViaHeader().getPort() == 5080;
            })
        .verifyComplete();

    ArgumentCaptor<Destination> argumentCaptorDestination =
        ArgumentCaptor.forClass(Destination.class);
    ArgumentCaptor<AbstractSipRequest> argumentCaptorRequest =
        ArgumentCaptor.forClass(AbstractSipRequest.class);
    // No Trunk Service invocation
    verify(trunkService, never())
        .getElementAsync(argumentCaptorRequest.capture(), argumentCaptorDestination.capture());

    EndPoint ep = proxySIPRequest.getDownstreamElement();
    // Route address is set in endpoint
    Assert.assertEquals(ep.getHost(), "11.1.1.1");
  }

  @Test(
      enabled = false,
      description = "test create proxy transaction exception and return 500 internal failure")
  public void testCreateProxyTransactionFailure()
      throws SipException, InvalidArgumentException, ExecutionException, InterruptedException,
          InternalProxyErrorException {
    ServerTransaction serverTransaction = mock(ServerTransaction.class);
    reset(serverTransaction);

    ProxySIPRequest proxySIPRequest =
        getProxySipRequest(SIPRequestBuilder.RequestMethod.INVITE, serverTransaction);

    ProxyFactory proxyFactoryMock = mock(ProxyFactory.class);

    // Get our own controller factory with mocked proxy factory
    ProxyControllerFactory proxyControllerFactoryMock =
        new ProxyControllerFactory(
            dhruvaSIPConfigProperties,
            controllerConfig,
            proxyFactoryMock,
            dhruvaExecutorService,
            trunkService);

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

    when(sipServerLocatorService.locateDestination(
            nullable(User.class), any(SipDestination.class), nullable(String.class)))
        .thenReturn(locateSIPServersResponse);

    proxyController.onNewRequest(proxySIPRequest);

    ArgumentCaptor<Response> responseArgumentCaptor = ArgumentCaptor.forClass(Response.class);
    verify(serverTransaction).sendResponse(responseArgumentCaptor.capture());

    SIPResponse response = (SIPResponse) responseArgumentCaptor.getValue();

    // Verify status code is 500 internal server error
    Assert.assertEquals(response.getStatusCode(), 500);
  }
}
