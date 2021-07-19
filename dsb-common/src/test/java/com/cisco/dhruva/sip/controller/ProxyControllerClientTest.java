package com.cisco.dhruva.sip.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

import com.cisco.dhruva.sip.proxy.Location;
import com.cisco.dhruva.sip.proxy.ProxyFactory;
import com.cisco.dhruva.sip.proxy.ProxyTransaction;
import com.cisco.dsb.common.CallType;
import com.cisco.dsb.common.context.ExecutionContext;
import com.cisco.dsb.common.executor.DhruvaExecutorService;
import com.cisco.dsb.common.executor.ExecutorType;
import com.cisco.dsb.common.messaging.ProxySIPRequest;
import com.cisco.dsb.common.messaging.models.DhruvaSipRequestMessage;
import com.cisco.dsb.config.sip.DhruvaSIPConfigProperties;
import com.cisco.dsb.sip.bean.SIPListenPoint;
import com.cisco.dsb.sip.stack.dto.DhruvaNetwork;
import com.cisco.dsb.util.SIPRequestBuilder;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import gov.nist.javax.sip.message.SIPRequest;
import java.net.InetAddress;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import javax.sip.*;
import javax.sip.message.Request;
import org.junit.runner.RunWith;
import org.mockito.*;
import org.mockito.junit.MockitoJUnitRunner;
import org.springframework.context.ApplicationContext;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.Test;

@RunWith(MockitoJUnitRunner.class)
public class ProxyControllerClientTest {

  DhruvaNetwork incomingNetwork;
  DhruvaNetwork outgoingNetwork;
  @Mock private ApplicationContext ctx;

  @Mock DhruvaSIPConfigProperties dhruvaSIPConfigProperties;

  DhruvaExecutorService dhruvaExecutorService;

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

    SIPListenPoint sipListenPoint1 = createIncomingUDPSipListenPoint();
    SIPListenPoint sipListenPoint2 = createOutgoingUDPSipListenPoint();

    incomingNetwork = DhruvaNetwork.createNetwork("net_sp_udp", sipListenPoint1);
    outgoingNetwork = DhruvaNetwork.createNetwork("net_internal_udp", sipListenPoint2);

    proxyFactory = new ProxyFactory();
    controllerConfig = new ControllerConfig();
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

    proxyControllerFactory =
        new ProxyControllerFactory(
            dhruvaSIPConfigProperties, controllerConfig, proxyFactory, dhruvaExecutorService);

    incomingSipProvider = mock(SipProvider.class);
    outgoingSipProvider = mock(SipProvider.class);
    DhruvaNetwork.setSipProvider(incomingNetwork.getName(), incomingSipProvider);
    DhruvaNetwork.setSipProvider(outgoingNetwork.getName(), outgoingSipProvider);

    DhruvaNetwork.setDhruvaConfigProperties(dhruvaSIPConfigProperties);

    // Chores
    when(dhruvaSIPConfigProperties.isHostPortEnabled()).thenReturn(false);
    //
    // doNothing().when(dhruvaExecutorService).startScheduledExecutorService(ExecutorType.PROXY_CLIENT_TIMEOUT, any());
    //
    // when(dhruvaExecutorService.startScheduledExecutorService(ExecutorType.PROXY_CLIENT_TIMEOUT);)
    ScheduledThreadPoolExecutor scheduledThreadPoolExecutor =
        mock(ScheduledThreadPoolExecutor.class, Mockito.RETURNS_DEEP_STUBS);
    ScheduledFuture scheduledFuture = mock(ScheduledFuture.class);
    when(scheduledThreadPoolExecutor.getQueue()).thenReturn(new ArrayBlockingQueue<>(1));
    when(scheduledThreadPoolExecutor.schedule(
            any(Runnable.class), eq(32), eq(TimeUnit.MILLISECONDS)))
        .thenReturn(scheduledFuture);

    when(dhruvaExecutorService.getScheduledExecutorThreadPool(ExecutorType.PROXY_CLIENT_TIMEOUT))
        .thenReturn(scheduledThreadPoolExecutor);
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
        "{ \"name\": \"net_internal_udp\", \"hostIPAddress\": \"1.1.1.1\", \"port\": 5080, \"transport\": \"UDP\", "
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

  @Test(description = "test proxy client creation for outgoing request")
  public void testOutgoingRequestProxyTransaction() throws SipException {

    // doNothing().when(dhruvaExecutorService).getScheduledExecutorThreadPool(any(ExecutorType.class));
    ServerTransaction serverTransaction = mock(ServerTransaction.class);

    ProxySIPRequest proxySIPRequest =
        getProxySipRequest(SIPRequestBuilder.RequestMethod.INVITE, serverTransaction);
    ProxyController proxyController = getProxyController(proxySIPRequest);

    doNothing().when(serverTransaction).setApplicationData(any(ProxyTransaction.class));
    // doReturn(mock(ClientTransaction.class)).when(outgoingSipProvider.getNewClientTransaction(any(Request.class)));

    ClientTransaction clientTransaction = mock(ClientTransaction.class);
    doNothing().when(clientTransaction).sendRequest();

    when(outgoingSipProvider.getNewClientTransaction(any(Request.class)))
        .thenReturn(clientTransaction);
    proxySIPRequest = proxyController.onNewRequest(proxySIPRequest);

    Location location = new Location(proxySIPRequest.getRequest().getRequestURI());
    location.setProcessRoute(true);
    location.setNetwork(outgoingNetwork);
    proxyController.proxyRequest(proxySIPRequest, location);
    // using stepVerifier
    //        proxySIPRequest.setLocation(location);
    //        SIPRequest preprocessedRequest = (SIPRequest) proxySIPRequest.getRequest().clone();
    //        proxyController.setPreprocessedRequest(preprocessedRequest);
    //        proxySIPRequest.setClonedRequest((SIPRequest) preprocessedRequest.clone());
    //
    //        //Mono<ProxySIPRequest> monoResponse = proxyController.proxyForwardRequest(location,
    // proxySIPRequest, proxyController.timeToTry);
    //
    //
    //        StepVerifier.create(proxyController.proxyForwardRequest(location, proxySIPRequest,
    // proxyController.timeToTry)).expectComplete().verify();

    verify(clientTransaction, Mockito.times(1)).sendRequest();
  }
}
