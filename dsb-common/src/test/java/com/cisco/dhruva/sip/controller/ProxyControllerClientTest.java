package com.cisco.dhruva.sip.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

import com.cisco.dhruva.sip.proxy.*;
import com.cisco.dsb.common.CallType;
import com.cisco.dsb.common.context.ExecutionContext;
import com.cisco.dsb.common.executor.DhruvaExecutorService;
import com.cisco.dsb.common.executor.ExecutorType;
import com.cisco.dsb.common.messaging.ProxySIPRequest;
import com.cisco.dsb.common.messaging.models.DhruvaSipRequestMessage;
import com.cisco.dsb.config.sip.DhruvaSIPConfigProperties;
import com.cisco.dsb.exception.DhruvaException;
import com.cisco.dsb.sip.bean.SIPListenPoint;
import com.cisco.dsb.sip.jain.JainSipHelper;
import com.cisco.dsb.sip.stack.dto.DhruvaNetwork;
import com.cisco.dsb.util.SIPRequestBuilder;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import gov.nist.javax.sip.header.RecordRouteList;
import gov.nist.javax.sip.message.SIPRequest;
import java.net.InetAddress;
import java.text.ParseException;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import javax.sip.*;
import javax.sip.address.SipURI;
import javax.sip.address.URI;
import javax.sip.header.RecordRouteHeader;
import javax.sip.header.RouteHeader;
import javax.sip.message.Request;
import org.junit.runner.RunWith;
import org.mockito.*;
import org.mockito.junit.MockitoJUnitRunner;
import org.testng.Assert;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.Test;
import reactor.test.StepVerifier;

@RunWith(MockitoJUnitRunner.class)
public class ProxyControllerClientTest {

  DhruvaNetwork incomingNetwork;
  DhruvaNetwork outgoingNetwork;
  DhruvaNetwork testNetwork;

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
    SIPListenPoint sipListenPoint3 = createTestSipListenPoint();

    incomingNetwork = DhruvaNetwork.createNetwork("net_sp_udp", sipListenPoint1);
    outgoingNetwork = DhruvaNetwork.createNetwork("net_internal_udp", sipListenPoint2);
    testNetwork = DhruvaNetwork.createNetwork("test_net", sipListenPoint3);

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
            dhruvaSIPConfigProperties, controllerConfig, proxyFactory, dhruvaExecutorService);

    // Dont add 3rd network
    incomingSipProvider = mock(SipProvider.class);
    outgoingSipProvider = mock(SipProvider.class);
    DhruvaNetwork.setSipProvider(incomingNetwork.getName(), incomingSipProvider);
    DhruvaNetwork.setSipProvider(outgoingNetwork.getName(), outgoingSipProvider);

    DhruvaNetwork.setDhruvaConfigProperties(dhruvaSIPConfigProperties);

    // Chores
    when(dhruvaSIPConfigProperties.isHostPortEnabled()).thenReturn(false);
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

  @Test(description = "test proxy client creation for outgoing request")
  public void testOutgoingInviteRequestProxyTransaction() throws SipException {

    ServerTransaction serverTransaction = mock(ServerTransaction.class);

    ProxySIPRequest proxySIPRequest =
        getProxySipRequest(SIPRequestBuilder.RequestMethod.INVITE, serverTransaction);
    ProxyController proxyController = getProxyController(proxySIPRequest);

    doNothing().when(serverTransaction).setApplicationData(any(ProxyTransaction.class));

    ClientTransaction clientTransaction = mock(ClientTransaction.class);
    doNothing().when(clientTransaction).sendRequest();

    when(outgoingSipProvider.getNewClientTransaction(any(Request.class)))
        .thenReturn(clientTransaction);
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
  public void testOutgoingRequestSendStackException() throws SipException {

    ServerTransaction serverTransaction = mock(ServerTransaction.class);

    ProxySIPRequest proxySIPRequest =
        getProxySipRequest(SIPRequestBuilder.RequestMethod.INVITE, serverTransaction);
    ProxyController proxyController = getProxyController(proxySIPRequest);

    doNothing().when(serverTransaction).setApplicationData(any(ProxyTransaction.class));

    ClientTransaction clientTransaction = mock(ClientTransaction.class);

    // Throw SipException
    doThrow(SipException.class).when(clientTransaction).sendRequest();

    when(outgoingSipProvider.getNewClientTransaction(any(Request.class)))
        .thenReturn(clientTransaction);
    proxySIPRequest = proxyController.onNewRequest(proxySIPRequest);

    Location location = new Location(proxySIPRequest.getRequest().getRequestURI());
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
  public void testOutgoingRequestProviderException() throws SipException {

    ServerTransaction serverTransaction = mock(ServerTransaction.class);

    ProxySIPRequest proxySIPRequest =
        getProxySipRequest(SIPRequestBuilder.RequestMethod.INVITE, serverTransaction);
    ProxyController proxyController = getProxyController(proxySIPRequest);

    doNothing().when(serverTransaction).setApplicationData(any(ProxyTransaction.class));

    ClientTransaction clientTransaction = mock(ClientTransaction.class);

    proxySIPRequest = proxyController.onNewRequest(proxySIPRequest);

    Location location = new Location(proxySIPRequest.getRequest().getRequestURI());
    location.setProcessRoute(true);
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
  }

  @Test(description = "test proxy client creation for outgoing ACK request - mid-dialog")
  public void testOutgoingACKRequestProxyTransaction() throws SipException, ParseException {

    ServerTransaction serverTransaction = mock(ServerTransaction.class);

    ProxySIPRequest proxySIPRequest =
        getProxySipRequest(SIPRequestBuilder.RequestMethod.ACK, serverTransaction);
    SIPRequest sipRequest = proxySIPRequest.getRequest();
    RouteHeader ownRouteHeader =
        JainSipHelper.createRouteHeader("rr$n=net_internal_udp", "1.1.1.1", 5060, "udp");
    RouteHeader routeHeader =
        JainSipHelper.createRouteHeader("testDhruva", "10.1.1.1", 5080, "udp");

    sipRequest.addHeader(routeHeader);
    sipRequest.addFirst(ownRouteHeader);

    sipRequest.setRequestURI(ownRouteHeader.getAddress().getURI());

    proxySIPRequest.setLrFixUri(ownRouteHeader.getAddress().getURI());

    ProxyController proxyController = getProxyController(proxySIPRequest);

    doNothing().when(serverTransaction).setApplicationData(any(ProxyTransaction.class));

    ClientTransaction clientTransaction = mock(ClientTransaction.class);
    doNothing().when(clientTransaction).sendRequest();

    when(outgoingSipProvider.getNewClientTransaction(any(Request.class)))
        .thenReturn(clientTransaction);

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
              assert request.getLocation().getUri() == request.getRequest().getRequestURI();
              assert request.getClonedRequest() != null;
              assert request.getClonedRequest().getTopmostViaHeader().getHost().equals("2.2.2.2");
              assert request.getClonedRequest().getTopmostViaHeader().getPort() == 5080;
            })
        .verifyComplete();

    ArgumentCaptor<Request> argumentCaptor = ArgumentCaptor.forClass(Request.class);
    verify(outgoingSipProvider, Mockito.times(0)).getNewClientTransaction(argumentCaptor.capture());

    verify(clientTransaction, Mockito.times(0)).sendRequest();
  }
}
