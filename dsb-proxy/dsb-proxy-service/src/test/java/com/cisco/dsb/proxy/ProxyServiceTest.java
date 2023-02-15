package com.cisco.dsb.proxy;

import static org.mockito.Mockito.*;

import com.cisco.dsb.common.CallType;
import com.cisco.dsb.common.config.DhruvaConfig;
import com.cisco.dsb.common.config.TruststoreConfigurationProperties;
import com.cisco.dsb.common.config.sip.CommonConfigurationProperties;
import com.cisco.dsb.common.context.ExecutionContext;
import com.cisco.dsb.common.executor.DhruvaExecutorService;
import com.cisco.dsb.common.maintanence.Maintenance;
import com.cisco.dsb.common.service.MetricService;
import com.cisco.dsb.common.service.SipServerLocatorService;
import com.cisco.dsb.common.sip.bean.SIPListenPoint;
import com.cisco.dsb.common.sip.tls.DsbTrustManager;
import com.cisco.dsb.common.sip.tls.DsbTrustManagerFactory;
import com.cisco.dsb.common.transport.Transport;
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
import com.cisco.wx2.util.stripedexecutor.StripedExecutorService;
import gov.nist.javax.sip.message.SIPRequest;
import java.net.BindException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Supplier;
import javax.net.ssl.KeyManager;
import javax.sip.*;
import org.mockito.*;
import org.testng.Assert;
import org.testng.annotations.*;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

public class ProxyServiceTest {

  @Mock CommonConfigurationProperties commonConfigurationProperties;

  @Mock MetricService metricsService;

  @Mock SipServerLocatorService resolver;

  @Mock ProxyPacketProcessor proxyPacketProcessor;

  @Mock DhruvaExecutorService dhruvaExecutorService;

  @Mock KeyManager keyManager;

  DhruvaServerImpl dhruvaServer;

  @Mock Dialog dialog;

  @Mock ProxyFactory proxyFactory;

  @Mock ControllerConfig controllerConfig;

  @Mock ServerTransaction serverTransaction;

  @Mock ProxyControllerFactory proxyControllerFactory;

  @Mock SipProxyManager sipProxyManager;
  // = new SipProxyManager(proxyControllerFactory, controllerConfig);

  @Mock ScheduledThreadPoolExecutor scheduledThreadPoolExecutor;
  @Mock SipServerLocatorService sipServerLocatorService;
  @Mock StripedExecutorService stripedExecutorService;
  @Mock DsbTrustManager dsbTrustManager;
  @InjectMocks DsbTrustManagerFactory dsbTrustManagerFactory;
  @InjectMocks DhruvaConfig dhruvaConfig;
  @InjectMocks ProxyService proxyService;

  SIPListenPoint udpListenPoint1;
  SIPListenPoint udpListenPoint2;
  SIPListenPoint tcpListenPoint3;
  SIPListenPoint tlsListenPoint4;
  List<SIPListenPoint> sipListenPointList;

  public ProxyServiceTest() {}

  @BeforeClass
  public void setup() throws Exception {

    dsbTrustManagerFactory = spy(new DsbTrustManagerFactory());
    MockitoAnnotations.initMocks(this);
    when(dhruvaExecutorService.getScheduledExecutorThreadPool(any()))
        .thenReturn(scheduledThreadPoolExecutor);
    when(dhruvaExecutorService.getExecutorThreadPool(any())).thenReturn(stripedExecutorService);
    when(commonConfigurationProperties.getKeepAlivePeriod()).thenReturn(5000L);
    when(commonConfigurationProperties.getReliableKeepAlivePeriod()).thenReturn("25");
    when(commonConfigurationProperties.getMinKeepAliveTimeSeconds()).thenReturn("20");
    TruststoreConfigurationProperties truststoreConfigurationProperties =
        new TruststoreConfigurationProperties();
    String keyStorePath =
        ProxyServiceTest.class.getClassLoader().getResource("keystore.jks").getPath();
    truststoreConfigurationProperties.setTrustStoreFilePath(keyStorePath);
    truststoreConfigurationProperties.setTrustStoreType("jks");
    truststoreConfigurationProperties.setTrustStorePassword("dsb123");
    truststoreConfigurationProperties.setKeyStoreFilePath(keyStorePath);
    truststoreConfigurationProperties.setKeyStorePassword("dsb123");
    truststoreConfigurationProperties.setKeyStoreType("jks");
    when(commonConfigurationProperties.getTruststoreConfig())
        .thenReturn(truststoreConfigurationProperties);

    udpListenPoint1 =
        SIPListenPoint.SIPListenPointBuilder()
            .setName("UDPNetwork1")
            .setHostIPAddress("127.0.0.1")
            .setTransport(Transport.UDP)
            .setPort(9060)
            .setRecordRoute(true)
            .build();

    udpListenPoint2 =
        SIPListenPoint.SIPListenPointBuilder()
            .setName("UDPNetwork2")
            .setHostIPAddress("127.0.0.1")
            .setTransport(Transport.UDP)
            .setPort(9063)
            .setRecordRoute(true)
            .build();

    tcpListenPoint3 =
        SIPListenPoint.SIPListenPointBuilder()
            .setName("TCPNetwork1")
            .setHostIPAddress("127.0.0.1")
            .setTransport(Transport.TCP)
            .setPort(8081)
            .setRecordRoute(true)
            .build();
    tlsListenPoint4 =
        SIPListenPoint.SIPListenPointBuilder()
            .setName("TLSNetwork1")
            .setHostIPAddress("127.0.0.1")
            .setTransport(Transport.TLS)
            .setPort(8082)
            .setRecordRoute(true)
            .build();

    sipListenPointList = new ArrayList<>();
    sipListenPointList.add(udpListenPoint1);
    sipListenPointList.add(udpListenPoint2);
    sipListenPointList.add(tcpListenPoint3);
    sipListenPointList.add(tlsListenPoint4);

    when(commonConfigurationProperties.getListenPoints()).thenReturn(sipListenPointList);

    doNothing().when(controllerConfig).addListenInterface(any(SIPListenPoint.class));

    dhruvaServer = new DhruvaServerImpl();
    dhruvaServer.setCommonConfigurationProperties(commonConfigurationProperties);
    dhruvaServer.setExecutorService(dhruvaExecutorService);
    dhruvaServer.setMetricService(metricsService);
    dhruvaServer.setHandler(proxyPacketProcessor);
    dhruvaServer.setAddressResolver(sipServerLocatorService);
    proxyService.setServer(dhruvaServer);
    proxyService.init();
  }

  @AfterClass
  public void cleanUp() {
    proxyService.releaseServiceResources();
  }

  @Test()
  public void testUDPListeningPoints() throws Exception {
    Consumer<ProxySIPRequest> requestConsumer = proxySIPRequest -> {};
    for (SIPListenPoint sipListeningPoint : sipListenPointList) {
      if (sipListeningPoint.getTransport() != Transport.UDP) {
        continue;
      }
      Socket socket = new Socket();
      socket.bind(
          new InetSocketAddress(sipListeningPoint.getHostIPAddress(), sipListeningPoint.getPort()));
      socket.close();
    }
    proxyService.register(ProxyAppConfig.builder().requestConsumer(requestConsumer).build());
  }

  @Test(expectedExceptions = {BindException.class})
  public void testTCPListeningPoints() throws Exception {
    Consumer<ProxySIPRequest> requestConsumer = proxySIPRequest -> {};

    for (SIPListenPoint sipListeningPoint : sipListenPointList) {
      if (sipListeningPoint.getTransport() != Transport.TCP) {
        continue;
      }
      Socket socket = new Socket();
      socket.bind(
          new InetSocketAddress(sipListeningPoint.getHostIPAddress(), sipListeningPoint.getPort()));
      socket.close();
    }
    proxyService.register(ProxyAppConfig.builder().requestConsumer(requestConsumer).build());
  }

  @Test(expectedExceptions = {BindException.class})
  public void testTLSListeningPoints() throws Exception {
    Consumer<ProxySIPRequest> requestConsumer = proxySIPRequest -> {};

    for (SIPListenPoint sipListeningPoint : sipListenPointList) {
      if (sipListeningPoint.getTransport() != Transport.TLS) {
        continue;
      }
      Socket socket = new Socket();
      socket.bind(
          new InetSocketAddress(sipListeningPoint.getHostIPAddress(), sipListeningPoint.getPort()));
      socket.close();
    }
    proxyService.register(ProxyAppConfig.builder().requestConsumer(requestConsumer).build());
  }

  @Test(description = "test the request pipeline in proxy service all the way up to app")
  public void testSipRequestPipeline() throws Exception {

    SIPRequest request = (SIPRequest) RequestHelper.getInviteRequest();
    ExecutionContext context = new ExecutionContext();

    Supplier<Maintenance> suspend = () -> Maintenance.MaintenanceBuilder().build();
    ProxyAppConfig proxyAppConfig = mock(ProxyAppConfig.class);
    when(proxyAppConfig.getMaintenance()).thenReturn(suspend);

    Consumer<ProxySIPRequest> requestConsumer =
        proxySIPRequest -> Assert.assertEquals(proxySIPRequest.getRequest(), request);

    Optional<SipStack> optionalSipStack1 = proxyService.getSipStack(udpListenPoint1.getName());
    Optional<SipStack> optionalSipStack2 = proxyService.getSipStack(udpListenPoint2.getName());
    Optional<SipStack> optionalSipStack3 = proxyService.getSipStack(tcpListenPoint3.getName());
    Optional<SipStack> optionalSipStack4 = proxyService.getSipStack(tlsListenPoint4.getName());
    SipStack sipStack1 =
        optionalSipStack1.orElseThrow(() -> new RuntimeException("exception fetching sip stack"));
    SipStack sipStack2 =
        optionalSipStack2.orElseThrow(() -> new RuntimeException("exception fetching sip stack"));
    SipStack sipStack3 =
        optionalSipStack3.orElseThrow(() -> new RuntimeException("exception fetching sip stack"));
    SipStack sipStack4 =
        optionalSipStack4.orElseThrow(() -> new RuntimeException("exception fetching sip stack"));
    Optional<SipProvider> optionalSipProvider1 =
        proxyService.getSipProvider(sipStack1, udpListenPoint1);
    SipProvider sipProvider1 =
        optionalSipProvider1.orElseThrow(() -> new RuntimeException("sip provider 1 is not set"));

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
    proxyService.register(ProxyAppConfig.builder().requestConsumer(requestConsumer).build());

    Consumer<RequestEvent> consumer = mock(Consumer.class);
    doAnswer(ans -> null).when(consumer).accept(requestEvent1);

    when(sipProxyManager.getManageMetricsForRequest()).thenReturn(consumer);

    Function<RequestEvent, ProxySIPRequest> function1 = mock(Function.class);
    when(function1.apply(any(RequestEvent.class))).thenReturn(proxySIPRequest);
    when(sipProxyManager.createServerTransactionAndProxySIPRequest(any(ProxyAppConfig.class)))
        .thenReturn(function1);

    Function<ProxySIPRequest, Mono<ProxySIPRequest>> function2 = mock(Function.class);
    when(function2.apply(any(ProxySIPRequest.class))).thenReturn(Mono.just(proxySIPRequest));
    when(sipProxyManager.getProxyController(any(ProxyAppConfig.class))).thenReturn(function2);

    Function<ProxySIPRequest, ProxySIPRequest> function3 = mock(Function.class);
    when(function3.apply(any(ProxySIPRequest.class))).thenReturn(proxySIPRequest);
    when(sipProxyManager.validateRequest()).thenReturn(function3);

    Function<ProxySIPRequest, ProxySIPRequest> function4 = mock(Function.class);
    when(function4.apply(any(ProxySIPRequest.class))).thenReturn(proxySIPRequest);
    when(sipProxyManager.proxyAppController(any(boolean.class))).thenReturn(function4);

    StepVerifier.create(proxyService.requestPipeline(Mono.just(requestEvent1)))
        .assertNext(proxyRequest -> Assert.assertEquals(proxyRequest.getRequest(), request))
        .verifyComplete();
  }

  @Test(description = "test timeout pipeline of proxy service until app")
  public void testTimeoutPipeline() {

    Consumer<ProxySIPRequest> requestConsumer =
        proxySIPRequest -> System.out.println("got request from proxy layer");

    ProxySIPResponse proxySIPResponse = mock(ProxySIPResponse.class);
    TimeoutEvent timeoutEvent = mock(TimeoutEvent.class);

    proxyService.register(ProxyAppConfig.builder().requestConsumer(requestConsumer).build());

    Function<TimeoutEvent, ProxySIPResponse> function1 = mock(Function.class);
    when(function1.apply(any(TimeoutEvent.class))).thenReturn(proxySIPResponse);
    when(sipProxyManager.handleProxyTimeoutEvent()).thenReturn(function1);

    StepVerifier.create(proxyService.timeOutPipeline(Mono.just(timeoutEvent)))
        .assertNext(proxyResponse -> Assert.assertEquals(proxyResponse, proxySIPResponse))
        .verifyComplete();
  }
}
