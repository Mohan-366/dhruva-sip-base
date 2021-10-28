package com.cisco.dsb.proxy.controller;

import static org.mockito.Mockito.*;
import static org.testng.Assert.*;

import com.cisco.dsb.common.exception.ErrorCode;
import com.cisco.dsb.common.executor.DhruvaExecutorService;
import com.cisco.dsb.common.sip.util.EndPoint;
import com.cisco.dsb.proxy.ProxyConfigurationProperties;
import com.cisco.dsb.proxy.dto.ProxyAppConfig;
import com.cisco.dsb.proxy.messaging.ProxySIPRequest;
import com.cisco.dsb.proxy.messaging.ProxySIPResponse;
import com.cisco.dsb.proxy.sip.ProxyClientTransaction;
import com.cisco.dsb.proxy.sip.ProxyCookieImpl;
import com.cisco.dsb.proxy.sip.ProxyFactory;
import com.cisco.dsb.proxy.sip.ProxyTransaction;
import com.cisco.dsb.proxy.util.SIPRequestBuilder;
import com.cisco.dsb.trunk.dto.Destination;
import com.cisco.dsb.trunk.loadbalancer.LBWeight;
import com.cisco.dsb.trunk.service.TrunkService;
import gov.nist.javax.sip.message.SIPRequest;
import gov.nist.javax.sip.message.SIPResponse;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;
import javax.sip.ServerTransaction;
import javax.sip.SipProvider;
import javax.sip.message.Request;
import javax.sip.message.Response;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.MockitoAnnotations;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.BeforeTest;
import org.testng.annotations.Test;
import reactor.core.publisher.Mono;

public class ProxyControllerServerTest {
  public ProxyController proxyController;
  @Mock ServerTransaction serverTransaction;
  @Mock SipProvider sipProvider;
  @Mock ProxyConfigurationProperties proxyConfigurationProperties;
  @Mock ProxyFactory proxyFactory;
  @Mock ControllerConfig controllerConfig;
  @Mock DhruvaExecutorService dhruvaExecutorService;
  @Mock ProxyTransaction proxyTransaction;
  @Mock SIPRequest sipRequest;
  @Mock ProxySIPRequest proxySIPRequest;
  @Mock ProxySIPResponse proxySIPResponse;
  @Mock SIPResponse sipResponse;
  @Mock TrunkService trunkService;
  @Mock ProxyAppConfig proxyAppConfig;

  @BeforeTest
  public void init() {
    MockitoAnnotations.initMocks(this);
    proxyController =
        new ProxyController(
            serverTransaction,
            sipProvider,
            proxyAppConfig,
            proxyConfigurationProperties,
            proxyFactory,
            controllerConfig,
            dhruvaExecutorService,
            trunkService);
    proxyController = spy(proxyController);
  }

  @BeforeMethod
  public void setup() {
    reset(proxyTransaction, proxyController, trunkService, proxyAppConfig);
  }

  @Test
  public void testProxyResponseStateFull() {
    // setup
    when(proxySIPRequest.getRequest()).thenReturn(sipRequest);
    when(sipRequest.getMethod()).thenReturn(Request.INVITE);
    when(proxySIPResponse.getResponse()).thenReturn(sipResponse);
    proxyController.setOurRequest(proxySIPRequest);
    proxyController.setProxyTransaction(proxyTransaction);
    // call
    proxyController.proxyResponse(proxySIPResponse);
    // verify
    verify(proxyTransaction, Mockito.times(1)).respond(sipResponse);
  }

  @Test
  public void testProxyResponseStateless() {
    when(proxySIPRequest.getRequest()).thenReturn(sipRequest);
    when(sipRequest.getMethod()).thenReturn(Request.ACK);
    when(proxySIPResponse.getResponse()).thenReturn(sipResponse);
    proxyController.setOurRequest(proxySIPRequest);
    proxyController.setProxyTransaction(proxyTransaction);
    // call
    proxyController.proxyResponse(proxySIPResponse);
    // verify
    verify(proxyTransaction, Mockito.times(0)).respond(sipResponse);
  }

  @Test
  public void testOnGlobalFailureResponse() {
    // Test 1:Cancel Branch set to false
    // setup
    proxyController.setCancelBranchesAutomatically(false);
    when(proxyTransaction.getBestResponse()).thenReturn(proxySIPResponse);
    proxyController.sendRequestToApp(true);
    doNothing().when(proxyController).proxyResponse(eq(proxySIPResponse));
    // call
    proxyController.onGlobalFailureResponse(proxyTransaction);

    // Test 2: Cancel Branch set to true
    // setup
    proxyController.setCancelBranchesAutomatically(true);
    when(proxyAppConfig.getInterest(anyInt())).thenReturn(true);
    Consumer responseConsumer = mock(Consumer.class);
    when(proxyAppConfig.getResponseConsumer()).thenReturn(responseConsumer);
    // call
    proxyController.onGlobalFailureResponse(proxyTransaction);
    // verify
    verify(proxyTransaction, Mockito.times(2)).getBestResponse(); // verify testcase 1 also
    verify(proxyTransaction, Mockito.times(1)).cancel();
    verify(proxyController, Mockito.times(1)).proxyResponse(eq(proxySIPResponse));
    verify(responseConsumer, Mockito.times(1)).accept(eq(proxySIPResponse));
  }

  @Test
  public void testOnSuccessResponse() {
    // Test 1:Cancel Branch set to false
    // setup
    proxyController.setCancelBranchesAutomatically(false);
    proxyController.sendRequestToApp(false);
    doNothing().when(proxyController).proxyResponse(eq(proxySIPResponse));
    // call
    proxyController.onSuccessResponse(proxyTransaction, proxySIPResponse);

    // Test 2: Cancel Branch set to true
    // setup
    proxyController.setCancelBranchesAutomatically(true);
    // call
    proxyController.onSuccessResponse(proxyTransaction, proxySIPResponse);
    // verify
    verify(proxyTransaction, Mockito.times(0)).getBestResponse();
    verify(proxyTransaction, Mockito.times(1)).cancel();
    verify(proxyController, Mockito.times(2)).proxyResponse(eq(proxySIPResponse));
  }

  @Test(
      description =
          "Unable to send out first request and no more elements to try\n"
              + "error code Destination Unreachable")
  public void testOnProxyFailure_1() throws Exception {
    // setup
    ErrorCode errCode = ErrorCode.DESTINATION_UNREACHABLE;
    ProxyCookieImpl cookie = mock(ProxyCookieImpl.class);
    ProxyClientTransaction proxyClientTransaction = mock(ProxyClientTransaction.class);
    SIPRequest sipRequest =
        SIPRequestBuilder.createRequest(
            new SIPRequestBuilder().getRequestAsString(SIPRequestBuilder.RequestMethod.INVITE));
    when(proxySIPRequest.getOriginalRequest()).thenReturn(sipRequest);
    when(proxyTransaction.getClientTransaction()).thenReturn(proxyClientTransaction);
    doAnswer(
            invocationOnMock -> {
              ProxySIPResponse proxySIPResponse = invocationOnMock.getArgument(0);
              when(proxyTransaction.getBestResponse()).thenReturn(proxySIPResponse);
              return null;
            })
        .when(proxyTransaction)
        .updateBestResponse(any(ProxySIPResponse.class));
    // call
    proxyController.onProxyFailure(
        proxyTransaction, cookie, errCode, "Destination Unreachable", null);
    // verify
    ArgumentCaptor<ProxySIPResponse> captor = ArgumentCaptor.forClass(ProxySIPResponse.class);
    verify(proxyTransaction, Mockito.times(1)).updateBestResponse(captor.capture());
    ProxySIPResponse bestResponse = captor.getValue();
    assert bestResponse.getResponse().getStatusCode() == Response.BAD_GATEWAY;
    // also verify that the ProxyClientTransaction's timer C is removed if there was any proxy
    // error. This is a cleanup.
    verify(proxyClientTransaction).removeTimerC();
  }

  @Test(
      description =
          "Unable to send out first request and no more elements to try\n"
              + "error code ProxyError")
  public void testOnProxyFailure_2() throws Exception {
    // setup
    ErrorCode errCode = ErrorCode.REQUEST_PARSE_ERROR;
    ProxyCookieImpl cookie = mock(ProxyCookieImpl.class);
    SIPRequest sipRequest =
        SIPRequestBuilder.createRequest(
            new SIPRequestBuilder().getRequestAsString(SIPRequestBuilder.RequestMethod.INVITE));
    when(proxySIPRequest.getOriginalRequest()).thenReturn(sipRequest);
    when(proxyTransaction.getClientTransaction()).thenReturn(mock(ProxyClientTransaction.class));
    doAnswer(
            invocationOnMock -> {
              ProxySIPResponse proxySIPResponse = invocationOnMock.getArgument(0);
              when(proxyTransaction.getBestResponse()).thenReturn(proxySIPResponse);
              return null;
            })
        .when(proxyTransaction)
        .updateBestResponse(any(ProxySIPResponse.class));
    // call
    proxyController.onProxyFailure(
        proxyTransaction, cookie, errCode, "Destination Unreachable", null);
    // verify
    ArgumentCaptor<ProxySIPResponse> captor = ArgumentCaptor.forClass(ProxySIPResponse.class);
    verify(proxyTransaction, Mockito.times(1)).updateBestResponse(captor.capture());
    ProxySIPResponse bestResponse = captor.getValue();
    assert bestResponse.getResponse().getStatusCode() == Response.SERVER_INTERNAL_ERROR;
  }

  @Test(
      description =
          "First Element-> unable to send because of destinationUnreachable(502)"
              + "Second Element, Unable to send out because of proxy_send_error, some NPE, 500"
              + "No more elements")
  public void testOnProxyFailure_3() throws Exception {
    // setup
    ErrorCode errCode = ErrorCode.DESTINATION_UNREACHABLE;
    ProxyCookieImpl cookie = mock(ProxyCookieImpl.class);
    Destination destination = mock(Destination.class);
    LBWeight lbWeight = mock(LBWeight.class);
    EndPoint ep = mock(EndPoint.class);
    ProxySIPRequest proxySIPRequest = mock(ProxySIPRequest.class);
    SIPRequest sipRequest =
        SIPRequestBuilder.createRequest(
            new SIPRequestBuilder().getRequestAsString(SIPRequestBuilder.RequestMethod.INVITE));
    when(cookie.getDestination()).thenReturn(destination);
    when(destination.getLoadBalancer()).thenReturn(lbWeight);
    AtomicInteger count = new AtomicInteger(1);
    when(trunkService.getNextElement(eq(lbWeight), anyInt()))
        .thenAnswer(invocationOnMock -> count.getAndDecrement() > 0 ? ep : null);
    when(proxySIPRequest.getCookie()).thenReturn(cookie);
    when(proxySIPRequest.getRequest()).thenReturn(sipRequest);
    when(proxySIPRequest.getOriginalRequest()).thenReturn(sipRequest);
    proxyController.setOurRequest(proxySIPRequest);
    proxyController.setStateMode(ControllerConfig.STATEFUL);
    proxyController.setProxyTransaction(proxyTransaction);
    when(proxyTransaction.getClientTransaction()).thenReturn(mock(ProxyClientTransaction.class));
    doAnswer(
            invocationOnMock -> {
              ProxySIPResponse proxySIPResponse = invocationOnMock.getArgument(0);
              when(proxyTransaction.getBestResponse()).thenReturn(proxySIPResponse);
              return null;
            })
        .when(proxyTransaction)
        .updateBestResponse(any(ProxySIPResponse.class));
    ArgumentCaptor<SIPResponse> resp_captor = ArgumentCaptor.forClass(SIPResponse.class);
    doNothing().when(proxyTransaction).respond(any(SIPResponse.class));
    // call
    proxyController.onProxyFailure(
        proxyTransaction, cookie, errCode, "Destination Unreachable", null);
    // verify
    ArgumentCaptor<ProxySIPResponse> captor = ArgumentCaptor.forClass(ProxySIPResponse.class);
    verify(proxyTransaction, Mockito.times(2)).updateBestResponse(captor.capture());
    verify(proxyTransaction, Mockito.times(1)).respond(resp_captor.capture());
    List<ProxySIPResponse> bestResponses = captor.getAllValues();
    assert bestResponses.get(0).getResponse().getStatusCode() == Response.BAD_GATEWAY;
    assert bestResponses.get(1).getResponse().getStatusCode() == Response.SERVER_INTERNAL_ERROR;
    assert resp_captor.getValue().getStatusCode() == Response.SERVER_INTERNAL_ERROR;
    verify(trunkService, Mockito.times(1)).getNextElement(eq(lbWeight), anyInt());
  }

  @Test(
      description =
          "First Element-> unable to send because of destinationUnreachable(502)"
              + "Second EndPoint -> sends 404 NotFound response"
              + "No more EndPoints found, send 404 as best response")
  public void testOnProxyFailure_4() throws Exception {
    // setup
    ErrorCode errCode = ErrorCode.DESTINATION_UNREACHABLE;
    ProxyCookieImpl cookie = mock(ProxyCookieImpl.class);
    Destination destination = mock(Destination.class);
    LBWeight lbWeight = mock(LBWeight.class);
    EndPoint ep = mock(EndPoint.class);
    ProxySIPRequest proxySIPRequest = mock(ProxySIPRequest.class);
    SIPRequest sipRequest =
        SIPRequestBuilder.createRequest(
            new SIPRequestBuilder().getRequestAsString(SIPRequestBuilder.RequestMethod.INVITE));
    ProxySIPResponse proxySIPResponse_404 = mock(ProxySIPResponse.class);
    SIPResponse sipResponse_404 = mock(SIPResponse.class);
    Consumer<ProxySIPResponse> responseConsumer = mock(Consumer.class);
    when(cookie.getDestination()).thenReturn(destination);
    when(destination.getLoadBalancer()).thenReturn(lbWeight);
    AtomicInteger count = new AtomicInteger(1);
    when(trunkService.getNextElement(eq(lbWeight), anyInt()))
        .thenAnswer(invocationOnMock -> count.getAndDecrement() > 0 ? ep : null);
    when(proxySIPRequest.getCookie()).thenReturn(cookie);
    when(proxySIPRequest.getRequest()).thenReturn(sipRequest);
    when(proxySIPRequest.getOriginalRequest()).thenReturn(sipRequest);
    proxyController.setOurRequest(proxySIPRequest);
    proxyController.setStateMode(ControllerConfig.STATEFUL);
    proxyController.setProxyTransaction(proxyTransaction);
    when(proxyTransaction.getClientTransaction()).thenReturn(mock(ProxyClientTransaction.class));
    when(proxyAppConfig.getInterest(4)).thenReturn(true);
    when(proxyAppConfig.getResponseConsumer()).thenReturn(responseConsumer);
    when(proxySIPResponse_404.getResponseClass()).thenReturn(4);
    when(proxySIPResponse_404.getResponse()).thenReturn(sipResponse_404);
    when(sipResponse_404.getStatusCode()).thenReturn(Response.NOT_FOUND);
    doAnswer(
            invocationOnMock -> {
              ProxySIPResponse proxySIPResponse = invocationOnMock.getArgument(0);
              when(proxyTransaction.getBestResponse()).thenReturn(proxySIPResponse);
              return null;
            })
        .when(proxyTransaction)
        .updateBestResponse(any(ProxySIPResponse.class));
    ArgumentCaptor<ProxySIPResponse> resp_captor = ArgumentCaptor.forClass(ProxySIPResponse.class);
    doNothing().when(proxyTransaction).respond(any(SIPResponse.class));
    doAnswer(ans -> Mono.just(proxySIPRequest)).when(proxyController).proxyToEndpoint(any(), any());
    doAnswer(
            ans -> {
              when(proxyTransaction.getBestResponse()).thenReturn(proxySIPResponse_404);
              proxyController.onFailureResponse(
                  proxyTransaction,
                  cookie,
                  proxyTransaction.getClientTransaction(),
                  proxySIPResponse_404);
              return null;
            })
        .when(proxyController)
        .onProxySuccess(any(), any(), any());
    // call
    proxyController.onProxyFailure(
        proxyTransaction, cookie, errCode, "Destination Unreachable", null);
    // verify
    ArgumentCaptor<ProxySIPResponse> captor = ArgumentCaptor.forClass(ProxySIPResponse.class);
    verify(proxyTransaction, Mockito.times(1)).updateBestResponse(captor.capture());
    // sending response to application
    verify(responseConsumer, Mockito.times(1)).accept(resp_captor.capture());
    List<ProxySIPResponse> bestResponses = captor.getAllValues();
    assert bestResponses.get(0).getResponse().getStatusCode() == Response.BAD_GATEWAY;
    assert bestResponses.size() == 1;
    assertEquals(resp_captor.getValue(), proxySIPResponse_404);
    verify(trunkService, Mockito.times(2)).getNextElement(eq(lbWeight), anyInt());
  }

  @Test(
      description =
          "First Element-> unable to send because of destinationUnreachable(502)"
              + "Second EndPoint -> sends 600 NotFound response"
              + "Don't try any endpoints, send out best response")
  public void testOnProxyFailure_5() throws Exception {
    // setup
    ErrorCode errCode = ErrorCode.DESTINATION_UNREACHABLE;
    ProxyCookieImpl cookie = mock(ProxyCookieImpl.class);
    Destination destination = mock(Destination.class);
    LBWeight lbWeight = mock(LBWeight.class);
    EndPoint ep = mock(EndPoint.class);
    ProxySIPRequest proxySIPRequest = mock(ProxySIPRequest.class);
    SIPRequest sipRequest =
        SIPRequestBuilder.createRequest(
            new SIPRequestBuilder().getRequestAsString(SIPRequestBuilder.RequestMethod.INVITE));
    ProxySIPResponse proxySIPResponse_600 = mock(ProxySIPResponse.class);
    when(cookie.getDestination()).thenReturn(destination);
    when(destination.getLoadBalancer()).thenReturn(lbWeight);
    AtomicInteger count = new AtomicInteger(1);
    when(trunkService.getNextElement(eq(lbWeight), anyInt()))
        .thenAnswer(invocationOnMock -> count.getAndDecrement() > 0 ? ep : null);
    when(proxySIPRequest.getCookie()).thenReturn(cookie);
    when(proxySIPRequest.getRequest()).thenReturn(sipRequest);
    when(proxySIPRequest.getOriginalRequest()).thenReturn(sipRequest);
    proxyController.setOurRequest(proxySIPRequest);
    proxyController.setStateMode(ControllerConfig.STATEFUL);
    proxyController.setProxyTransaction(proxyTransaction);
    when(proxyTransaction.getClientTransaction()).thenReturn(mock(ProxyClientTransaction.class));
    when(proxyAppConfig.getInterest(6)).thenReturn(false);
    when(proxySIPResponse_600.getResponseClass()).thenReturn(6);
    doAnswer(
            invocationOnMock -> {
              ProxySIPResponse proxySIPResponse = invocationOnMock.getArgument(0);
              when(proxyTransaction.getBestResponse()).thenReturn(proxySIPResponse);
              return null;
            })
        .when(proxyTransaction)
        .updateBestResponse(any(ProxySIPResponse.class));
    ArgumentCaptor<SIPResponse> resp_captor = ArgumentCaptor.forClass(SIPResponse.class);
    doNothing().when(proxyTransaction).respond(any(SIPResponse.class));
    /*when(proxyController.proxyToEndpoint(eq(ep),any(ProxySIPRequest.class)))
    .thenReturn(Mono.just(proxySIPRequest));*/
    // means request was sent out successfully
    doAnswer(ans -> Mono.just(proxySIPRequest)).when(proxyController).proxyToEndpoint(any(), any());
    doAnswer(
            ans -> {
              proxyController.onGlobalFailureResponse(proxyTransaction);
              return null;
            })
        .when(proxyController)
        .onProxySuccess(any(), any(), any());
    // call
    proxyController.onProxyFailure(
        proxyTransaction, cookie, errCode, "Destination Unreachable", null);
    // verify
    ArgumentCaptor<ProxySIPResponse> captor = ArgumentCaptor.forClass(ProxySIPResponse.class);
    verify(proxyTransaction, Mockito.times(1)).updateBestResponse(captor.capture());
    verify(proxyTransaction, Mockito.times(1)).respond(resp_captor.capture());
    List<ProxySIPResponse> bestResponses = captor.getAllValues();
    assert bestResponses.get(0).getResponse().getStatusCode() == Response.BAD_GATEWAY;
    assert bestResponses.size() == 1;
    assert resp_captor.getValue().getStatusCode() == Response.BAD_GATEWAY;
    verify(trunkService, Mockito.times(1)).getNextElement(eq(lbWeight), anyInt());
  }

  @Test(
      description =
          "First Element-> unable to send because of destinationUnreachable(502)"
              + "Second EndPoint -> sends 200OK response"
              + "Don't try any endpoints, send out 200OK response")
  public void testOnProxyFailure_6() throws Exception {
    // setup
    ErrorCode errCode = ErrorCode.DESTINATION_UNREACHABLE;
    ProxyCookieImpl cookie = mock(ProxyCookieImpl.class);
    Destination destination = mock(Destination.class);
    LBWeight lbWeight = mock(LBWeight.class);
    EndPoint ep = mock(EndPoint.class);
    ProxySIPRequest proxySIPRequest = mock(ProxySIPRequest.class);
    SIPRequest sipRequest =
        SIPRequestBuilder.createRequest(
            new SIPRequestBuilder().getRequestAsString(SIPRequestBuilder.RequestMethod.INVITE));
    ProxySIPResponse proxySIPResponse_200 = mock(ProxySIPResponse.class);
    Consumer<ProxySIPResponse> responseConsumer = mock(Consumer.class);
    when(cookie.getDestination()).thenReturn(destination);
    when(destination.getLoadBalancer()).thenReturn(lbWeight);
    AtomicInteger count = new AtomicInteger(1);
    when(trunkService.getNextElement(eq(lbWeight), anyInt()))
        .thenAnswer(invocationOnMock -> count.getAndDecrement() > 0 ? ep : null);
    when(proxySIPRequest.getCookie()).thenReturn(cookie);
    when(proxySIPRequest.getRequest()).thenReturn(sipRequest);
    when(proxySIPRequest.getOriginalRequest()).thenReturn(sipRequest);
    proxyController.setOurRequest(proxySIPRequest);
    proxyController.setStateMode(ControllerConfig.STATEFUL);
    proxyController.setProxyTransaction(proxyTransaction);
    when(proxyTransaction.getClientTransaction()).thenReturn(mock(ProxyClientTransaction.class));
    when(proxyAppConfig.getInterest(2)).thenReturn(true);
    when(proxyAppConfig.getResponseConsumer()).thenReturn(responseConsumer);
    when(proxySIPResponse_200.getResponseClass()).thenReturn(2);
    doAnswer(
            invocationOnMock -> {
              ProxySIPResponse proxySIPResponse = invocationOnMock.getArgument(0);
              when(proxyTransaction.getBestResponse()).thenReturn(proxySIPResponse);
              return null;
            })
        .when(proxyTransaction)
        .updateBestResponse(any(ProxySIPResponse.class));
    ArgumentCaptor<ProxySIPResponse> resp_captor = ArgumentCaptor.forClass(ProxySIPResponse.class);
    doNothing().when(proxyTransaction).respond(any(SIPResponse.class));
    /*when(proxyController.proxyToEndpoint(eq(ep),any(ProxySIPRequest.class)))
    .thenReturn(Mono.just(proxySIPRequest));*/
    // means request was sent out successfully
    doAnswer(ans -> Mono.just(proxySIPRequest)).when(proxyController).proxyToEndpoint(any(), any());
    doAnswer(
            ans -> {
              proxyController.onSuccessResponse(proxyTransaction, proxySIPResponse_200);
              return null;
            })
        .when(proxyController)
        .onProxySuccess(any(), any(), any());
    // call
    proxyController.onProxyFailure(
        proxyTransaction, cookie, errCode, "Destination Unreachable", null);
    // verify
    ArgumentCaptor<ProxySIPResponse> captor = ArgumentCaptor.forClass(ProxySIPResponse.class);
    verify(proxyTransaction, Mockito.times(1)).updateBestResponse(captor.capture());
    verify(proxyTransaction, Mockito.times(0)).respond(any());
    verify(responseConsumer, Mockito.times(1)).accept(resp_captor.capture());
    List<ProxySIPResponse> bestResponses = captor.getAllValues();
    assert bestResponses.get(0).getResponse().getStatusCode() == Response.BAD_GATEWAY;
    assert bestResponses.size() == 1;
    assertEquals(resp_captor.getValue(), proxySIPResponse_200);
    verify(trunkService, Mockito.times(1)).getNextElement(eq(lbWeight), anyInt());
  }

  @Test(
      description =
          "First Element-> unable to send because of destinationUnreachable(502)"
              + "Second EndPoint -> sends 180 Ringing response"
              + "Don't try any endpoints, send out 180Ringing response")
  public void testOnProxyFailure_7() throws Exception {
    // setup
    ErrorCode errCode = ErrorCode.DESTINATION_UNREACHABLE;
    ProxyCookieImpl cookie = mock(ProxyCookieImpl.class);
    Destination destination = mock(Destination.class);
    LBWeight lbWeight = mock(LBWeight.class);
    EndPoint ep = mock(EndPoint.class);
    ProxySIPRequest proxySIPRequest = mock(ProxySIPRequest.class);
    SIPResponse sipResponse_180 = mock(SIPResponse.class);
    SIPRequest sipRequest =
        SIPRequestBuilder.createRequest(
            new SIPRequestBuilder().getRequestAsString(SIPRequestBuilder.RequestMethod.INVITE));
    ProxySIPResponse proxySIPResponse_180 = mock(ProxySIPResponse.class);
    Consumer<ProxySIPResponse> responseConsumer = mock(Consumer.class);
    when(cookie.getDestination()).thenReturn(destination);
    when(destination.getLoadBalancer()).thenReturn(lbWeight);
    AtomicInteger count = new AtomicInteger(1);
    when(trunkService.getNextElement(eq(lbWeight), anyInt()))
        .thenAnswer(invocationOnMock -> count.getAndDecrement() > 0 ? ep : null);
    when(proxySIPRequest.getCookie()).thenReturn(cookie);
    when(proxySIPRequest.getRequest()).thenReturn(sipRequest);
    when(proxySIPRequest.getOriginalRequest()).thenReturn(sipRequest);
    proxyController.setOurRequest(proxySIPRequest);
    proxyController.setStateMode(ControllerConfig.STATEFUL);
    proxyController.setProxyTransaction(proxyTransaction);
    when(proxyTransaction.getClientTransaction()).thenReturn(mock(ProxyClientTransaction.class));
    when(proxyAppConfig.getInterest(2)).thenReturn(true);
    when(proxyAppConfig.getResponseConsumer()).thenReturn(responseConsumer);
    when(proxySIPResponse_180.getResponseClass()).thenReturn(1);
    when(proxySIPResponse_180.getResponse()).thenReturn(sipResponse_180);
    doAnswer(
            invocationOnMock -> {
              ProxySIPResponse proxySIPResponse = invocationOnMock.getArgument(0);
              when(proxyTransaction.getBestResponse()).thenReturn(proxySIPResponse);
              return null;
            })
        .when(proxyTransaction)
        .updateBestResponse(any(ProxySIPResponse.class));
    ArgumentCaptor<SIPResponse> resp_captor = ArgumentCaptor.forClass(SIPResponse.class);
    doNothing().when(proxyTransaction).respond(any(SIPResponse.class));
    /*when(proxyController.proxyToEndpoint(eq(ep),any(ProxySIPRequest.class)))
    .thenReturn(Mono.just(proxySIPRequest));*/
    // means request was sent out successfully
    doAnswer(ans -> Mono.just(proxySIPRequest)).when(proxyController).proxyToEndpoint(any(), any());
    doAnswer(
            ans -> {
              proxyController.onProvisionalResponse(
                  proxyTransaction,
                  cookie,
                  proxyTransaction.getClientTransaction(),
                  proxySIPResponse_180);
              return null;
            })
        .when(proxyController)
        .onProxySuccess(any(), any(), any());
    // call
    proxyController.onProxyFailure(
        proxyTransaction, cookie, errCode, "Destination Unreachable", null);
    // verify
    ArgumentCaptor<ProxySIPResponse> captor = ArgumentCaptor.forClass(ProxySIPResponse.class);
    verify(proxyTransaction, Mockito.times(1)).updateBestResponse(captor.capture());
    verify(proxyTransaction, Mockito.times(1)).respond(resp_captor.capture());
    verify(responseConsumer, Mockito.times(0)).accept(any());
    List<ProxySIPResponse> bestResponses = captor.getAllValues();
    assert bestResponses.get(0).getResponse().getStatusCode() == Response.BAD_GATEWAY;
    assert bestResponses.size() == 1;
    assertEquals(resp_captor.getValue(), sipResponse_180);
    verify(trunkService, Mockito.times(1)).getNextElement(eq(lbWeight), anyInt());
  }

  @Test(
      description =
          "First Element-> unable to send because of destinationUnreachable(502)"
              + "Second EndPoint -> sends 3xx response"
              + "Don't try any endpoints, send out 3xx response")
  public void testOnProxyFailure_8() throws Exception {
    // setup
    ErrorCode errCode = ErrorCode.DESTINATION_UNREACHABLE;
    ProxyCookieImpl cookie = mock(ProxyCookieImpl.class);
    Destination destination = mock(Destination.class);
    LBWeight lbWeight = mock(LBWeight.class);
    EndPoint ep = mock(EndPoint.class);
    ProxySIPRequest proxySIPRequest = mock(ProxySIPRequest.class);
    SIPResponse sipResponse_3xx = mock(SIPResponse.class);
    SIPRequest sipRequest =
        SIPRequestBuilder.createRequest(
            new SIPRequestBuilder().getRequestAsString(SIPRequestBuilder.RequestMethod.INVITE));
    ProxySIPResponse proxySIPResponse_3xx = mock(ProxySIPResponse.class);
    Consumer<ProxySIPResponse> responseConsumer = mock(Consumer.class);
    when(cookie.getDestination()).thenReturn(destination);
    when(destination.getLoadBalancer()).thenReturn(lbWeight);
    AtomicInteger count = new AtomicInteger(1);
    when(trunkService.getNextElement(eq(lbWeight), anyInt()))
        .thenAnswer(invocationOnMock -> count.getAndDecrement() > 0 ? ep : null);
    when(proxySIPRequest.getCookie()).thenReturn(cookie);
    when(proxySIPRequest.getRequest()).thenReturn(sipRequest);
    when(proxySIPRequest.getOriginalRequest()).thenReturn(sipRequest);
    proxyController.setOurRequest(proxySIPRequest);
    proxyController.setStateMode(ControllerConfig.STATEFUL);
    proxyController.setProxyTransaction(proxyTransaction);
    when(proxyTransaction.getClientTransaction()).thenReturn(mock(ProxyClientTransaction.class));
    when(proxyAppConfig.getInterest(3)).thenReturn(false);
    when(proxyAppConfig.getResponseConsumer()).thenReturn(responseConsumer);
    when(proxySIPResponse_3xx.getResponseClass()).thenReturn(3);
    when(proxySIPResponse_3xx.getResponse()).thenReturn(sipResponse_3xx);
    doAnswer(
            invocationOnMock -> {
              ProxySIPResponse proxySIPResponse = invocationOnMock.getArgument(0);
              when(proxyTransaction.getBestResponse()).thenReturn(proxySIPResponse);
              return null;
            })
        .when(proxyTransaction)
        .updateBestResponse(any(ProxySIPResponse.class));
    ArgumentCaptor<SIPResponse> resp_captor = ArgumentCaptor.forClass(SIPResponse.class);
    doNothing().when(proxyTransaction).respond(any(SIPResponse.class));
    /*when(proxyController.proxyToEndpoint(eq(ep),any(ProxySIPRequest.class)))
    .thenReturn(Mono.just(proxySIPRequest));*/
    // means request was sent out successfully
    doAnswer(ans -> Mono.just(proxySIPRequest)).when(proxyController).proxyToEndpoint(any(), any());
    doAnswer(
            ans -> {
              proxyController.onRedirectResponse(proxySIPResponse_3xx);
              return null;
            })
        .when(proxyController)
        .onProxySuccess(any(), any(), any());
    // call
    proxyController.onProxyFailure(
        proxyTransaction, cookie, errCode, "Destination Unreachable", null);
    // verify
    ArgumentCaptor<ProxySIPResponse> captor = ArgumentCaptor.forClass(ProxySIPResponse.class);
    verify(proxyTransaction, Mockito.times(1)).updateBestResponse(captor.capture());
    verify(proxyTransaction, Mockito.times(1)).respond(resp_captor.capture());
    verify(responseConsumer, Mockito.times(0)).accept(any());
    List<ProxySIPResponse> bestResponses = captor.getAllValues();
    assert bestResponses.get(0).getResponse().getStatusCode() == Response.BAD_GATEWAY;
    assert bestResponses.size() == 1;
    assertEquals(resp_captor.getValue(), sipResponse_3xx);
    verify(trunkService, Mockito.times(1)).getNextElement(eq(lbWeight), anyInt());
  }

  @Test(
      description =
          "First Element-> unable to send because of destinationUnreachable(502)"
              + "Second EndPoint -> no response sent"
              + "No more elements to try, send best response i.e 408")
  public void testOnProxyFailure_9() throws Exception {
    // setup
    ErrorCode errCode = ErrorCode.DESTINATION_UNREACHABLE;
    ProxyCookieImpl cookie = mock(ProxyCookieImpl.class);
    Destination destination = mock(Destination.class);
    LBWeight lbWeight = mock(LBWeight.class);
    EndPoint ep = mock(EndPoint.class);
    ProxySIPRequest proxySIPRequest = mock(ProxySIPRequest.class);
    SIPResponse sipResponse_408 = mock(SIPResponse.class);
    SIPRequest sipRequest =
        SIPRequestBuilder.createRequest(
            new SIPRequestBuilder().getRequestAsString(SIPRequestBuilder.RequestMethod.INVITE));
    ProxySIPResponse proxySIPResponse_408 = mock(ProxySIPResponse.class);
    Consumer<ProxySIPResponse> responseConsumer = mock(Consumer.class);
    when(cookie.getDestination()).thenReturn(destination);
    when(destination.getLoadBalancer()).thenReturn(lbWeight);
    AtomicInteger count = new AtomicInteger(1);
    when(trunkService.getNextElement(eq(lbWeight), anyInt()))
        .thenAnswer(invocationOnMock -> count.getAndDecrement() > 0 ? ep : null);
    when(proxySIPRequest.getCookie()).thenReturn(cookie);
    when(proxySIPRequest.getRequest()).thenReturn(sipRequest);
    when(proxySIPRequest.getOriginalRequest()).thenReturn(sipRequest);
    proxyController.setOurRequest(proxySIPRequest);
    proxyController.setStateMode(ControllerConfig.STATEFUL);
    proxyController.setProxyTransaction(proxyTransaction);
    when(proxyTransaction.getClientTransaction()).thenReturn(mock(ProxyClientTransaction.class));
    when(proxyAppConfig.getInterest(4)).thenReturn(false);
    when(proxyAppConfig.getResponseConsumer()).thenReturn(responseConsumer);
    when(proxySIPResponse_408.getResponseClass()).thenReturn(4);
    when(proxySIPResponse_408.getResponse()).thenReturn(sipResponse_408);
    doAnswer(
            invocationOnMock -> {
              ProxySIPResponse proxySIPResponse = invocationOnMock.getArgument(0);
              when(proxyTransaction.getBestResponse()).thenReturn(proxySIPResponse);
              return null;
            })
        .when(proxyTransaction)
        .updateBestResponse(any(ProxySIPResponse.class));
    ArgumentCaptor<SIPResponse> resp_captor = ArgumentCaptor.forClass(SIPResponse.class);
    doNothing().when(proxyTransaction).respond(any(SIPResponse.class));
    /*when(proxyController.proxyToEndpoint(eq(ep),any(ProxySIPRequest.class)))
    .thenReturn(Mono.just(proxySIPRequest));*/
    // means request was sent out successfully
    doAnswer(ans -> Mono.just(proxySIPRequest)).when(proxyController).proxyToEndpoint(any(), any());
    doAnswer(
            ans -> {
              when(proxyTransaction.getBestResponse()).thenReturn(proxySIPResponse_408);
              proxyController.onRequestTimeOut(
                  proxyTransaction, cookie, proxyTransaction.getClientTransaction());
              return null;
            })
        .when(proxyController)
        .onProxySuccess(any(), any(), any());
    // call
    proxyController.onProxyFailure(
        proxyTransaction, cookie, errCode, "Destination Unreachable", null);
    // verify
    ArgumentCaptor<ProxySIPResponse> captor = ArgumentCaptor.forClass(ProxySIPResponse.class);
    verify(proxyTransaction, Mockito.times(1)).updateBestResponse(captor.capture());
    verify(proxyTransaction, Mockito.times(1)).respond(resp_captor.capture());
    verify(responseConsumer, Mockito.times(0)).accept(any());
    List<ProxySIPResponse> bestResponses = captor.getAllValues();
    assert bestResponses.get(0).getResponse().getStatusCode() == Response.BAD_GATEWAY;
    assert bestResponses.size() == 1;
    assertEquals(resp_captor.getValue(), sipResponse_408);
    verify(trunkService, Mockito.times(2)).getNextElement(eq(lbWeight), anyInt());
  }

  @Test(
      description =
          "first end point -> received 420"
              + "second end point -> received 404"
              + "no more elements left, send out best response i.e 404")
  public void testOnFailureResponse_1() throws Exception {
    // setup
    ProxyCookieImpl cookie = mock(ProxyCookieImpl.class);
    Destination destination = mock(Destination.class);
    LBWeight lbWeight = mock(LBWeight.class);
    EndPoint ep = mock(EndPoint.class);
    ProxySIPRequest proxySIPRequest = mock(ProxySIPRequest.class);
    SIPRequest sipRequest =
        SIPRequestBuilder.createRequest(
            new SIPRequestBuilder().getRequestAsString(SIPRequestBuilder.RequestMethod.INVITE));
    ProxySIPResponse proxySIPResponse_420 = mock(ProxySIPResponse.class);
    ProxySIPResponse proxySIPResponse_404 = mock(ProxySIPResponse.class);
    SIPResponse sipResponse_404 = mock(SIPResponse.class);
    SIPResponse sipResponse_420 = mock(SIPResponse.class);
    when(sipResponse_420.getStatusCode()).thenReturn(Response.BAD_EXTENSION);
    when(sipResponse_404.getStatusCode()).thenReturn(Response.NOT_FOUND);
    Consumer<ProxySIPResponse> responseConsumer = mock(Consumer.class);
    when(cookie.getDestination()).thenReturn(destination);
    when(destination.getLoadBalancer()).thenReturn(lbWeight);
    AtomicInteger count = new AtomicInteger(1);
    when(trunkService.getNextElement(eq(lbWeight), anyInt()))
        .thenAnswer(invocationOnMock -> count.getAndDecrement() > 0 ? ep : null);
    when(proxySIPRequest.getCookie()).thenReturn(cookie);
    when(proxySIPRequest.getRequest()).thenReturn(sipRequest);
    when(proxySIPRequest.getOriginalRequest()).thenReturn(sipRequest);
    proxyController.setOurRequest(proxySIPRequest);
    proxyController.setStateMode(ControllerConfig.STATEFUL);
    proxyController.setProxyTransaction(proxyTransaction);
    when(proxyTransaction.getClientTransaction()).thenReturn(mock(ProxyClientTransaction.class));
    when(proxyAppConfig.getInterest(4)).thenReturn(false);
    when(proxyAppConfig.getResponseConsumer()).thenReturn(responseConsumer);
    when(proxySIPResponse_420.getResponseClass()).thenReturn(4);
    when(proxyTransaction.getBestResponse()).thenReturn(proxySIPResponse_404);
    when(proxySIPResponse_404.getResponse()).thenReturn(sipResponse_404);
    when(proxySIPResponse_420.getResponse()).thenReturn(sipResponse_420);
    ArgumentCaptor<SIPResponse> resp_captor = ArgumentCaptor.forClass(SIPResponse.class);
    doNothing().when(proxyTransaction).respond(any(SIPResponse.class));
    doAnswer(ans -> Mono.just(proxySIPRequest)).when(proxyController).proxyToEndpoint(any(), any());
    doAnswer(
            ans -> {
              proxyController.onFailureResponse(
                  proxyTransaction,
                  cookie,
                  proxyTransaction.getClientTransaction(),
                  proxySIPResponse_404);
              return null;
            })
        .when(proxyController)
        .onProxySuccess(any(), any(), any());

    // call
    proxyController.onFailureResponse(
        proxyTransaction, cookie, proxyTransaction.getClientTransaction(), proxySIPResponse_420);

    // verify
    verify(proxyTransaction, Mockito.times(1)).respond(resp_captor.capture());
    verify(responseConsumer, Mockito.times(0)).accept(any());
    assertEquals(resp_captor.getValue(), sipResponse_404);
    verify(trunkService, Mockito.times(2)).getNextElement(eq(lbWeight), anyInt());
  }

  @Test(
      description =
          "first end point -> received 420"
              + "second end point -> received 200OK"
              + "don't try any other elements, send out 200OK")
  public void testOnFailureResponse_2() throws Exception {
    // setup
    ProxyCookieImpl cookie = mock(ProxyCookieImpl.class);
    Destination destination = mock(Destination.class);
    LBWeight lbWeight = mock(LBWeight.class);
    EndPoint ep = mock(EndPoint.class);
    ProxySIPRequest proxySIPRequest = mock(ProxySIPRequest.class);
    SIPRequest sipRequest =
        SIPRequestBuilder.createRequest(
            new SIPRequestBuilder().getRequestAsString(SIPRequestBuilder.RequestMethod.INVITE));
    ProxySIPResponse proxySIPResponse_420 = mock(ProxySIPResponse.class);
    ProxySIPResponse proxySIPResponse_200 = mock(ProxySIPResponse.class);
    SIPResponse sipResponse_200 = mock(SIPResponse.class);
    SIPResponse sipResponse_420 = mock(SIPResponse.class);
    Consumer<ProxySIPResponse> responseConsumer = mock(Consumer.class);
    when(cookie.getDestination()).thenReturn(destination);
    when(destination.getLoadBalancer()).thenReturn(lbWeight);
    AtomicInteger count = new AtomicInteger(1);
    when(trunkService.getNextElement(eq(lbWeight), anyInt()))
        .thenAnswer(invocationOnMock -> count.getAndDecrement() > 0 ? ep : null);
    when(proxySIPRequest.getCookie()).thenReturn(cookie);
    when(proxySIPRequest.getRequest()).thenReturn(sipRequest);
    when(proxySIPRequest.getOriginalRequest()).thenReturn(sipRequest);
    proxyController.setOurRequest(proxySIPRequest);
    proxyController.setStateMode(ControllerConfig.STATEFUL);
    proxyController.setProxyTransaction(proxyTransaction);
    when(proxyTransaction.getClientTransaction()).thenReturn(mock(ProxyClientTransaction.class));
    when(proxyAppConfig.getInterest(2)).thenReturn(false);
    when(proxyAppConfig.getResponseConsumer()).thenReturn(responseConsumer);
    when(proxySIPResponse_420.getResponseClass()).thenReturn(4);
    when(proxySIPResponse_420.getResponse()).thenReturn(sipResponse_420);
    when(sipResponse_420.getStatusCode()).thenReturn(Response.BAD_EXTENSION);
    when(proxyTransaction.getBestResponse()).thenReturn(proxySIPResponse_200);
    when(proxySIPResponse_200.getResponse()).thenReturn(sipResponse_200);
    ArgumentCaptor<SIPResponse> resp_captor = ArgumentCaptor.forClass(SIPResponse.class);
    doNothing().when(proxyTransaction).respond(any(SIPResponse.class));
    doAnswer(ans -> Mono.just(proxySIPRequest)).when(proxyController).proxyToEndpoint(any(), any());
    doAnswer(
            ans -> {
              proxyController.onSuccessResponse(proxyTransaction, proxySIPResponse_200);
              return null;
            })
        .when(proxyController)
        .onProxySuccess(any(), any(), any());

    // call
    proxyController.onFailureResponse(
        proxyTransaction, cookie, proxyTransaction.getClientTransaction(), proxySIPResponse_420);

    // verify
    verify(proxyTransaction, Mockito.times(1)).respond(resp_captor.capture());
    verify(responseConsumer, Mockito.times(0)).accept(any());
    assertEquals(resp_captor.getValue(), sipResponse_200);
    verify(trunkService, Mockito.times(1)).getNextElement(eq(lbWeight), anyInt());
  }

  @Test(description = "100 trying, drop the response")
  public void testOnProvisionalResponse() {
    ProxyCookieImpl cookie = mock(ProxyCookieImpl.class);
    ProxySIPResponse proxySIPResponse_100 = mock(ProxySIPResponse.class);
    SIPResponse sipResponse_100 = mock(SIPResponse.class);
    Consumer<ProxySIPResponse> responseConsumer = mock(Consumer.class);
    when(proxySIPResponse_100.getResponse()).thenReturn(sipResponse_100);
    when(sipResponse_100.getStatusCode()).thenReturn(100);
    when(proxyAppConfig.getResponseConsumer()).thenReturn(responseConsumer);
    when(proxyAppConfig.getInterest(1)).thenReturn(false);
    // call
    proxyController.onProvisionalResponse(
        proxyTransaction, cookie, proxyTransaction.getClientTransaction(), proxySIPResponse_100);

    // verify
    verify(proxyController, Mockito.times(0)).proxyResponse(any());
    verify(responseConsumer, Mockito.times(0)).accept(any());
  }

  @Test(
      description =
          "got request timeout(generated 408, not as received response) "
              + "send to next element, got 200Ok "
              + "send out 200Ok, don't try other elements")
  public void testOnRequestTimeOut() throws Exception {
    ProxyCookieImpl cookie = mock(ProxyCookieImpl.class);
    Destination destination = mock(Destination.class);
    LBWeight lbWeight = mock(LBWeight.class);
    EndPoint ep = mock(EndPoint.class);
    ProxySIPRequest proxySIPRequest = mock(ProxySIPRequest.class);
    SIPRequest sipRequest =
        SIPRequestBuilder.createRequest(
            new SIPRequestBuilder().getRequestAsString(SIPRequestBuilder.RequestMethod.INVITE));
    ProxySIPResponse proxySIPResponse_408 = mock(ProxySIPResponse.class);
    ProxySIPResponse proxySIPResponse_200 = mock(ProxySIPResponse.class);
    SIPResponse sipResponse_200 = mock(SIPResponse.class);
    Consumer<ProxySIPResponse> responseConsumer = mock(Consumer.class);
    when(cookie.getDestination()).thenReturn(destination);
    when(destination.getLoadBalancer()).thenReturn(lbWeight);
    AtomicInteger count = new AtomicInteger(1);
    when(trunkService.getNextElement(eq(lbWeight), anyInt()))
        .thenAnswer(invocationOnMock -> count.getAndDecrement() > 0 ? ep : null);
    when(proxySIPRequest.getCookie()).thenReturn(cookie);
    when(proxySIPRequest.getRequest()).thenReturn(sipRequest);
    when(proxySIPRequest.getOriginalRequest()).thenReturn(sipRequest);
    proxyController.setOurRequest(proxySIPRequest);
    proxyController.setStateMode(ControllerConfig.STATEFUL);
    proxyController.setProxyTransaction(proxyTransaction);
    when(proxyTransaction.getClientTransaction()).thenReturn(mock(ProxyClientTransaction.class));
    when(proxyAppConfig.getInterest(2)).thenReturn(false);
    when(proxyAppConfig.getResponseConsumer()).thenReturn(responseConsumer);
    when(proxySIPResponse_408.getResponseClass()).thenReturn(4);
    when(proxyTransaction.getBestResponse()).thenReturn(proxySIPResponse_200);
    when(proxySIPResponse_200.getResponse()).thenReturn(sipResponse_200);
    ArgumentCaptor<SIPResponse> resp_captor = ArgumentCaptor.forClass(SIPResponse.class);
    doNothing().when(proxyTransaction).respond(any(SIPResponse.class));
    doAnswer(ans -> Mono.just(proxySIPRequest)).when(proxyController).proxyToEndpoint(any(), any());
    doAnswer(
            ans -> {
              proxyController.onSuccessResponse(proxyTransaction, proxySIPResponse_200);
              return null;
            })
        .when(proxyController)
        .onProxySuccess(any(), any(), any());

    // call
    proxyController.onRequestTimeOut(
        proxyTransaction, cookie, proxyTransaction.getClientTransaction());

    // verify
    verify(proxyTransaction, Mockito.times(1)).respond(resp_captor.capture());
    verify(responseConsumer, Mockito.times(0)).accept(any());
    assertEquals(resp_captor.getValue(), sipResponse_200);
    verify(trunkService, Mockito.times(1)).getNextElement(eq(lbWeight), anyInt());
  }

  @Test(description = "on sending out the request successfully")
  public void testOnProxySuccess() {}
}
