package com.cisco.dsb.proxy;

import static org.mockito.Mockito.*;

import com.cisco.dsb.common.CallType;
import com.cisco.dsb.common.config.sip.DhruvaSIPConfigProperties;
import com.cisco.dsb.common.context.ExecutionContext;
import com.cisco.dsb.common.executor.DhruvaExecutorService;
import com.cisco.dsb.common.service.MetricService;
import com.cisco.dsb.common.service.SipServerLocatorService;
import com.cisco.dsb.common.service.TrunkService;
import com.cisco.dsb.common.sip.bean.SIPListenPoint;
import com.cisco.dsb.common.sip.stack.dto.DhruvaNetwork;
import com.cisco.dsb.common.transport.Transport;
import com.cisco.dsb.proxy.bootstrap.DhruvaServer;
import com.cisco.dsb.proxy.bootstrap.DhruvaServerImpl;
import com.cisco.dsb.proxy.controller.ControllerConfig;
import com.cisco.dsb.proxy.controller.ProxyControllerFactory;
import com.cisco.dsb.proxy.dto.ProxyAppConfig;
import com.cisco.dsb.proxy.messaging.DhruvaSipRequestMessage;
import com.cisco.dsb.proxy.messaging.ProxySIPRequest;
import com.cisco.dsb.proxy.messaging.ProxySIPResponse;
import com.cisco.dsb.proxy.sip.ProxyFactory;
import com.cisco.dsb.proxy.sip.ProxyPacketProcessor;
import com.cisco.dsb.proxy.sip.SipProxyManager;
import com.cisco.dsb.proxy.util.RequestHelper;
import gov.nist.javax.sip.message.SIPRequest;
import java.net.BindException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.function.Consumer;
import java.util.function.Function;
import javax.sip.*;
import org.mockito.*;
import org.testng.Assert;
import org.testng.annotations.*;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

public class ProxyServiceTest {

  @Mock DhruvaSIPConfigProperties dhruvaSIPConfigProperties;

  @Mock public MetricService metricsService;

  @Mock SipServerLocatorService resolver;

  @Mock private ProxyPacketProcessor proxyPacketProcessor;

  @Mock TrunkService trunkService;

  @Mock DhruvaExecutorService dhruvaExecutorService;

  @Spy DhruvaServer server = new DhruvaServerImpl(dhruvaExecutorService, metricsService);

  @Mock Dialog dialog;

  @Mock ProxyFactory proxyFactory;

  @Mock ControllerConfig controllerConfig;

  @Mock ServerTransaction serverTransaction;

  @Mock ProxyControllerFactory proxyControllerFactory;

  @Mock SipProxyManager sipProxyManager;
  // = new SipProxyManager(proxyControllerFactory, controllerConfig);

  @InjectMocks ProxyService proxyService;

  SIPListenPoint udpListenPoint1;
  SIPListenPoint udpListenPoint2;
  SIPListenPoint tcpListenPoint3;
  List<SIPListenPoint> sipListenPointList;

  @BeforeClass
  public void setup() throws Exception {
    MockitoAnnotations.initMocks(this);

    udpListenPoint1 =
        new SIPListenPoint.SIPListenPointBuilder()
            .setName("UDPNetwork1")
            .setHostIPAddress("127.0.0.1")
            .setTransport(Transport.UDP)
            .setPort(9060)
            .setRecordRoute(true)
            .setAttachExternalIP(false)
            .build();

    udpListenPoint2 =
        new SIPListenPoint.SIPListenPointBuilder()
            .setName("UDPNetwork2")
            .setHostIPAddress("127.0.0.1")
            .setTransport(Transport.UDP)
            .setPort(9063)
            .setRecordRoute(true)
            .setAttachExternalIP(false)
            .build();

    tcpListenPoint3 =
        new SIPListenPoint.SIPListenPointBuilder()
            .setName("TCPNetwork1")
            .setHostIPAddress("127.0.0.1")
            .setTransport(Transport.TCP)
            .setPort(8081)
            .setRecordRoute(true)
            .setAttachExternalIP(false)
            .build();

    sipListenPointList = new ArrayList<>();
    sipListenPointList.add(udpListenPoint1);
    sipListenPointList.add(udpListenPoint2);
    sipListenPointList.add(tcpListenPoint3);

    when(dhruvaSIPConfigProperties.getListeningPoints()).thenReturn(sipListenPointList);

    doNothing()
        .when(controllerConfig)
        .addRecordRouteInterface(
            any(InetAddress.class), any(int.class), any(Transport.class), any(DhruvaNetwork.class));

    doNothing()
        .when(controllerConfig)
        .addListenInterface(
            any(DhruvaNetwork.class),
            any(InetAddress.class),
            any(int.class),
            any(Transport.class),
            any(InetAddress.class),
            any(boolean.class));

    proxyService.init();
  }

  @AfterClass
  public void cleanUp() {
    proxyService.releaseServiceResources();
  }

  @Test()
  public void testUDPListeningPoints() throws Exception {
    Consumer<ProxySIPRequest> requestConsumer = proxySIPRequest -> {};
    Consumer<ProxySIPResponse> responseConsumer = proxySIPResponse -> {};

    for (SIPListenPoint sipListeningPoint : sipListenPointList) {
      if (sipListeningPoint.getTransport() != Transport.UDP) {
        continue;
      }
      Socket socket = new Socket();
      socket.bind(
          new InetSocketAddress(sipListeningPoint.getHostIPAddress(), sipListeningPoint.getPort()));
      socket.close();
    }
    proxyService.register(
        ProxyAppConfig.builder()
            .requestConsumer(requestConsumer)
            .responseConsumer(responseConsumer)
            .build());
  }

  @Test(expectedExceptions = {BindException.class})
  public void testTCPListeningPoints() throws Exception {
    Consumer<ProxySIPRequest> requestConsumer = proxySIPRequest -> {};
    Consumer<ProxySIPResponse> responseConsumer = proxySIPResponse -> {};

    for (SIPListenPoint sipListeningPoint : sipListenPointList) {
      if (sipListeningPoint.getTransport() == Transport.UDP) {
        continue;
      }
      Socket socket = new Socket();
      socket.bind(
          new InetSocketAddress(sipListeningPoint.getHostIPAddress(), sipListeningPoint.getPort()));
      socket.close();
    }
    proxyService.register(
        ProxyAppConfig.builder()
            .requestConsumer(requestConsumer)
            .responseConsumer(responseConsumer)
            .build());
  }

  @Test(description = "test the request pipeline in proxy service all the way up to app")
  public void testSipRequestPipeline() throws Exception {

    SIPRequest request = (SIPRequest) RequestHelper.getInviteRequest();
    ExecutionContext context = new ExecutionContext();

    Consumer<ProxySIPRequest> requestConsumer =
        proxySIPRequest -> {
          Assert.assertEquals(proxySIPRequest.getRequest(), request);
        };
    Consumer<ProxySIPResponse> responseConsumer = proxySIPResponse -> {};

    Optional<SipStack> optionalSipStack1 = proxyService.getSipStack(udpListenPoint1.getName());
    Optional<SipStack> optionalSipStack2 = proxyService.getSipStack(udpListenPoint2.getName());
    Optional<SipStack> optionalSipStack3 = proxyService.getSipStack(tcpListenPoint3.getName());
    SipStack sipStack1 =
        optionalSipStack1.orElseThrow(() -> new RuntimeException("exception fetching sip stack"));
    SipStack sipStack2 =
        optionalSipStack2.orElseThrow(() -> new RuntimeException("exception fetching sip stack"));
    SipStack sipStack3 =
        optionalSipStack3.orElseThrow(() -> new RuntimeException("exception fetching sip stack"));

    Optional<SipProvider> optionalSipProvider1 =
        proxyService.getSipProvider(sipStack1, udpListenPoint1);
    SipProvider sipProvider1 =
        optionalSipProvider1.orElseThrow(() -> new RuntimeException("sip provider 1 is not set"));

    Optional<SipProvider> optionalSipProvider2 =
        proxyService.getSipProvider(sipStack2, udpListenPoint2);

    Optional<SipProvider> optionalSipProvider3 =
        proxyService.getSipProvider(sipStack3, tcpListenPoint3);
    SipProvider sipProvider3 =
        optionalSipProvider3.orElseThrow(() -> new RuntimeException("sip provider 3 is not set"));

    ProxySIPRequest proxySIPRequest =
        DhruvaSipRequestMessage.newBuilder()
            .withContext(context)
            .withPayload(request)
            .withTransaction(serverTransaction)
            .callType(CallType.SIP)
            .correlationId("ABCD")
            .reqURI("sip:test@webex.com")
            .sessionId("testSession")
            .network("udpListenPoint1")
            .withProvider(sipProvider1)
            .build();

    request.setLocalAddress(InetAddress.getByName("127.0.0.1"));
    request.setRemoteAddress(InetAddress.getByName("127.0.0.1"));

    request.setLocalPort(9000);
    request.setRemotePort(8000);

    RequestEvent requestEvent1 = new RequestEvent(sipProvider1, serverTransaction, dialog, request);
    proxyService.register(
        ProxyAppConfig.builder()
            .requestConsumer(requestConsumer)
            .responseConsumer(responseConsumer)
            .build());

    Consumer<RequestEvent> consumer = mock(Consumer.class);
    doAnswer(ans -> null).when(consumer).accept(requestEvent1);

    when(sipProxyManager.getManageLogAndMetricsForRequest()).thenReturn(consumer);

    Function<RequestEvent, ProxySIPRequest> function1 = mock(Function.class);
    when(function1.apply(any(RequestEvent.class))).thenReturn(proxySIPRequest);
    when(sipProxyManager.createServerTransactionAndProxySIPRequest()).thenReturn(function1);

    Function<ProxySIPRequest, ProxySIPRequest> function2 = mock(Function.class);
    when(function2.apply(any(ProxySIPRequest.class))).thenReturn(proxySIPRequest);
    when(sipProxyManager.getProxyController(any(ProxyAppConfig.class))).thenReturn(function2);

    Function<ProxySIPRequest, ProxySIPRequest> function3 = mock(Function.class);
    when(function3.apply(any(ProxySIPRequest.class))).thenReturn(proxySIPRequest);
    when(sipProxyManager.validateRequest()).thenReturn(function3);

    Function<ProxySIPRequest, ProxySIPRequest> function4 = mock(Function.class);
    when(function4.apply(any(ProxySIPRequest.class))).thenReturn(proxySIPRequest);
    when(sipProxyManager.proxyAppController(any(boolean.class))).thenReturn(function4);

    StepVerifier.create(proxyService.requestPipeline(Mono.just(requestEvent1)))
        .assertNext(
            proxyRequest -> {
              Assert.assertEquals(proxyRequest.getRequest(), request);
            })
        .verifyComplete();
  }

  @Test(description = "test response pipeline of proxy service until app")
  public void testSipResponsePipeline() {

    Consumer<ProxySIPRequest> requestConsumer =
        proxySIPRequest -> {
          System.out.println("got request from proxy layer");
        };
    Consumer<ProxySIPResponse> responseConsumer =
        proxySIPResponse -> {
          System.out.println("got response from proxy layer");
        };

    Optional<SipStack> optionalSipStack1 = proxyService.getSipStack(udpListenPoint1.getName());
    Optional<SipStack> optionalSipStack2 = proxyService.getSipStack(udpListenPoint2.getName());
    SipStack sipStack1 =
        optionalSipStack1.orElseThrow(() -> new RuntimeException("exception fetching sip stack"));
    SipStack sipStack2 =
        optionalSipStack2.orElseThrow(() -> new RuntimeException("exception fetching sip stack"));

    ProxySIPResponse proxySIPResponse = mock(ProxySIPResponse.class);
    ResponseEvent responseEvent = mock(ResponseEvent.class);

    proxyService.register(
        ProxyAppConfig.builder()
            .requestConsumer(requestConsumer)
            .responseConsumer(responseConsumer)
            ._2xx(true)
            .build());

    Consumer<ResponseEvent> consumer = mock(Consumer.class);
    doAnswer(ans -> null).when(consumer).accept(responseEvent);

    when(sipProxyManager.getManageLogAndMetricsForResponse()).thenReturn(consumer);

    Function<ResponseEvent, ProxySIPResponse> function1 = mock(Function.class);
    when(function1.apply(any(ResponseEvent.class))).thenReturn(proxySIPResponse);
    when(sipProxyManager.findProxyTransaction()).thenReturn(function1);

    Function<ProxySIPResponse, ProxySIPResponse> function2 = mock(Function.class);
    when(function2.apply(any(ProxySIPResponse.class))).thenReturn(proxySIPResponse);
    when(sipProxyManager.processProxyTransaction()).thenReturn(function2);
    when(sipProxyManager.processToApp(proxySIPResponse, true)).thenReturn(proxySIPResponse);
    when(proxySIPResponse.getResponseClass()).thenReturn(2);
    StepVerifier.create(proxyService.responsePipeline(Mono.just(responseEvent)))
        .assertNext(
            proxyResponse -> {
              Assert.assertEquals(proxyResponse, proxySIPResponse);
            })
        .verifyComplete();
  }

  @Test(description = "test timeout pipeline of proxy service until app")
  public void testTimeoutPipeline() {

    Consumer<ProxySIPRequest> requestConsumer =
        proxySIPRequest -> {
          System.out.println("got request from proxy layer");
        };
    Consumer<ProxySIPResponse> responseConsumer =
        proxySIPResponse -> {
          System.out.println("got response from proxy layer");
        };

    ProxySIPResponse proxySIPResponse = mock(ProxySIPResponse.class);
    TimeoutEvent timeoutEvent = mock(TimeoutEvent.class);

    proxyService.register(
        ProxyAppConfig.builder()
            .requestConsumer(requestConsumer)
            .responseConsumer(responseConsumer)
            .build());

    Function<TimeoutEvent, ProxySIPResponse> function1 = mock(Function.class);
    when(function1.apply(any(TimeoutEvent.class))).thenReturn(proxySIPResponse);
    when(sipProxyManager.handleProxyTimeoutEvent()).thenReturn(function1);

    StepVerifier.create(proxyService.timeOutPipeline(Mono.just(timeoutEvent)))
        .assertNext(
            proxyResponse -> {
              Assert.assertEquals(proxyResponse, proxySIPResponse);
            })
        .verifyComplete();
  }
}
