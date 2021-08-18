package com.cisco.dhruva.sip.proxy;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

import com.cisco.dhruva.sip.proxy.errors.DestinationUnreachableException;
import com.cisco.dhruva.sip.proxy.errors.InternalProxyErrorException;
import com.cisco.dsb.common.messaging.ProxySIPResponse;
import com.cisco.dsb.util.SIPRequestBuilder;
import com.cisco.dsb.util.TriFunction;
import gov.nist.javax.sip.header.ViaList;
import gov.nist.javax.sip.message.SIPRequest;
import gov.nist.javax.sip.message.SIPResponse;
import java.util.*;
import javax.sip.*;
import javax.sip.header.ViaHeader;
import javax.sip.message.Request;
import javax.sip.message.Response;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.MockitoAnnotations;
import org.testng.Assert;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.BeforeTest;
import org.testng.annotations.DataProvider;
import org.testng.annotations.Test;

public class ProxyTransactionTest {

  ProxyTransaction proxyTransaction;
  @Mock ControllerInterface controllerInterface;
  @Mock ProxyParamsInterface proxyParamsInterface;
  @Mock ServerTransaction serverTransaction;
  @Mock SIPRequest sipRequest;
  @Mock ProxyServerTransaction proxyServerTransaction;
  @Mock ProxySIPResponse proxySIPResponse;
  @Mock ProxyClientTransaction proxyClientTransaction;
  @Mock SIPResponse sipResponse;
  @Mock ProxyFactory proxyFactory;
  @Mock TriFunction getMockProxyServerTransaction;

  @BeforeTest
  public void init() {
    MockitoAnnotations.initMocks(this);
  }

  @BeforeMethod
  public void setup() throws InternalProxyErrorException {

    reset(sipResponse, proxyClientTransaction, controllerInterface, proxyServerTransaction);
    when(getMockProxyServerTransaction.apply(any(), any(), any()))
        .thenReturn(proxyServerTransaction);
    when(proxyFactory.proxyServerTransaction()).thenReturn(getMockProxyServerTransaction);
    when(controllerInterface.getProxyFactory()).thenReturn(proxyFactory);
    when(sipRequest.getMethod()).thenReturn(Request.INVITE);
    when(proxySIPResponse.getResponse()).thenReturn(sipResponse);
    ViaList viaList = new ViaList();
    when(sipResponse.getViaHeaders()).thenReturn(viaList);
    proxyTransaction =
        new ProxyTransaction(
            controllerInterface, proxyParamsInterface, serverTransaction, sipRequest);
  }

  @Test(description = "Handling provisional Response in ProxyTransaction")
  public void testProvisionalResponse() {
    proxyTransaction.setM_isForked(false);
    // test behaviour when proxyClientTransaction is null
    proxyTransaction.setM_originalProxyClientTrans(null);
    proxyTransaction.provisionalResponse(proxySIPResponse);

    verify(sipResponse, Mockito.times(0)).removeFirst(ViaHeader.NAME);
    verify(proxyClientTransaction, Mockito.times(0)).gotResponse(proxySIPResponse);
    verify(controllerInterface, Mockito.times(0)).onProvisionalResponse(any(), any(), any(), any());

    // Test for proxyTransaction with ProxyClientTransaction
    proxyTransaction.setM_originalProxyClientTrans(proxyClientTransaction);
    proxyTransaction.provisionalResponse(proxySIPResponse);

    verify(sipResponse, Mockito.times(1)).removeFirst(ViaHeader.NAME);
    verify(proxyClientTransaction, Mockito.times(1)).gotResponse(proxySIPResponse);
    verify(controllerInterface, Mockito.times(1)).onProvisionalResponse(any(), any(), any(), any());

    reset(sipResponse, proxyClientTransaction, controllerInterface);

    // test when no via left after removing top via
    reset(sipResponse, proxyClientTransaction, controllerInterface);
    when(sipResponse.getViaHeaders()).thenReturn(null);

    proxyTransaction.provisionalResponse(proxySIPResponse);

    verify(sipResponse, Mockito.times(1)).removeFirst(ViaHeader.NAME);
    verify(proxyClientTransaction, Mockito.times(0)).gotResponse(proxySIPResponse);
    verify(controllerInterface, Mockito.times(0)).onProvisionalResponse(any(), any(), any(), any());
    verify(controllerInterface, Mockito.times(1))
        .onResponseFailure(
            any(),
            any(),
            eq(ControllerInterface.NO_VIA_LEFT),
            eq("Response is meant for proxy, no more Vias left"),
            eq(null));
  }

  @Test(description = "Handling Response which has no or single Via")
  public void testFinalResponseNoVia() {
    // setup
    proxyTransaction.setM_originalProxyClientTrans(proxyClientTransaction);
    when(sipResponse.getViaHeaders()).thenReturn(null);
    // call
    proxyTransaction.provisionalResponse(proxySIPResponse);
    // verify
    verify(sipResponse, Mockito.times(1)).removeFirst(ViaHeader.NAME);
    verify(proxyClientTransaction, Mockito.times(0)).gotResponse(proxySIPResponse);
    verify(controllerInterface, Mockito.times(1))
        .onResponseFailure(
            any(),
            any(),
            eq(ControllerInterface.NO_VIA_LEFT),
            eq("Response is meant for proxy, no more Vias left"),
            eq(null));
  }

  @Test(description = "Handling 2xx Response in ProxyTransaction")
  public void testFinalResponse2xx() {
    // setup
    proxyTransaction.setM_originalProxyClientTrans(proxyClientTransaction);
    when(proxySIPResponse.getResponseClass()).thenReturn(2);
    when(proxyClientTransaction.getState()).thenReturn(ProxyClientTransaction.STATE_FINAL_RECVD);
    // call
    proxyTransaction.finalResponse(proxySIPResponse);
    // verify
    verify(controllerInterface, Mockito.times(1)).onResponse(proxySIPResponse);
    verify(sipResponse, Mockito.times(1)).removeFirst(ViaHeader.NAME);
    assert proxyTransaction.getBestResponse() == proxySIPResponse;
    verify(proxyClientTransaction, Mockito.times(1)).gotResponse(proxySIPResponse);
    verify(controllerInterface, Mockito.times(1))
        .onSuccessResponse(eq(proxyTransaction), eq(proxySIPResponse));

    //
  }

  @Test(description = "Handling 4xx,5xx Response in ProxyTransaction")
  public void testFinalResponse4xx5xx() {
    // setup
    proxyTransaction.setM_originalProxyClientTrans(proxyClientTransaction);
    when(proxySIPResponse.getResponseClass()).thenReturn(4);
    when(proxyClientTransaction.isInvite()).thenReturn(true);

    // call
    proxyTransaction.finalResponse(proxySIPResponse);

    // verify
    // ACK is sent by JAIN stack
    verify(controllerInterface, Mockito.times(1))
        .onFailureResponse(
            eq(proxyTransaction), any(), eq(proxyClientTransaction), eq(proxySIPResponse));
  }

  @Test(description = "Handling 6xx Response in ProxyTransaction")
  public void testFinalResponse6xx() {
    // setup
    proxyTransaction.setM_originalProxyClientTrans(proxyClientTransaction);
    when(proxySIPResponse.getResponseClass()).thenReturn(6);
    when(proxyClientTransaction.isInvite()).thenReturn(true);

    // call
    proxyTransaction.finalResponse(proxySIPResponse);

    // verify
    // ACK is sent by JAIN stack
    verify(controllerInterface, Mockito.times(1)).onGlobalFailureResponse(eq(proxyTransaction));
  }

  @Test(description = "test to send out best response received so far")
  public void testBestRespond() {
    // Test scenario 1:when best response is null
    // call
    proxyTransaction.respond();
    // verify
    verify(controllerInterface, Mockito.times(1))
        .onResponseFailure(
            eq(proxyTransaction),
            eq(proxyServerTransaction),
            eq(ControllerInterface.INVALID_STATE),
            eq("No final response received so far!"),
            eq(null));

    // Test scenario 2:when best response is not null
    // setup
    proxyTransaction = spy(proxyTransaction);
    proxyTransaction.setBestResponse(proxySIPResponse);

    // call
    proxyTransaction.respond();
    // verify
    verify(proxyTransaction, Mockito.times(1)).respond(sipResponse);
  }

  @Test(description = "test to send out SIPResponse for NOT_STRAY request")
  public void testRespondNotStray() throws DestinationUnreachableException {
    // setup
    when(sipResponse.getStatusCode()).thenReturn(200);
    proxyTransaction.setStrayRequest(ProxyStatelessTransaction.NOT_STRAY);
    // call
    proxyTransaction.respond(sipResponse);
    // verify
    verify(proxyServerTransaction, Mockito.times(1)).respond(sipResponse);
  }

  @Test(description = "test to send out SIPResponse for STRAY_CANCEL request")
  public void testRespondStray() throws DestinationUnreachableException {
    // setup
    when(sipResponse.getStatusCode()).thenReturn(200);
    proxyTransaction.setStrayRequest(ProxyStatelessTransaction.STRAY_CANCEL);
    // call
    proxyTransaction.respond(sipResponse);
    // verify
    verify(proxyServerTransaction, Mockito.times(0)).respond(sipResponse);
  }

  @Test(description = "test to handle response when ProxyTransaction is in invalid state")
  public void testRespondInvalidState() {
    // setup
    when(sipResponse.getStatusCode()).thenReturn(404);
    proxyTransaction.setCurrentServerState(ProxyTransaction.PROXY_FINISHED_200);

    // call
    proxyTransaction.respond(sipResponse);

    // verify
    verify(controllerInterface, Mockito.times(1))
        .onResponseFailure(
            eq(proxyTransaction),
            eq(proxyServerTransaction),
            eq(ControllerInterface.INVALID_STATE),
            eq(
                "Cannot send "
                    + 4
                    + "xx response in "
                    + ProxyTransaction.PROXY_FINISHED_200
                    + " state"),
            eq(null));
  }

  @Test(description = "test to handle DestinationUnreachableException while sending out response")
  public void testRespondDestinationUnException() throws DestinationUnreachableException {
    // setup
    when(sipResponse.getStatusCode()).thenReturn(200);
    proxyTransaction.setStrayRequest(ProxyStatelessTransaction.NOT_STRAY);
    DestinationUnreachableException due = mock(DestinationUnreachableException.class);
    when(due.getMessage()).thenReturn("Destination Unreachable");
    doThrow(due).when(proxyServerTransaction).respond(sipResponse);

    // call
    proxyTransaction.respond(sipResponse);
    // verify
    verify(controllerInterface, Mockito.times(1))
        .onResponseFailure(
            eq(proxyTransaction),
            eq(proxyServerTransaction),
            eq(ControllerInterface.DESTINATION_UNREACHABLE),
            eq("Destination Unreachable"),
            eq(due));
  }

  @Test
  public void testResponseUnhandledException() throws DestinationUnreachableException {
    // setup
    when(sipResponse.getStatusCode()).thenReturn(200);
    proxyTransaction.setStrayRequest(ProxyStatelessTransaction.NOT_STRAY);
    Throwable due = mock(RuntimeException.class);
    when(due.getMessage()).thenReturn("Unexpected Exception");
    doThrow(due).when(proxyServerTransaction).respond(sipResponse);

    // call
    proxyTransaction.respond(sipResponse);
    // verify
    verify(controllerInterface, Mockito.times(1))
        .onResponseFailure(
            eq(proxyTransaction),
            eq(proxyServerTransaction),
            eq(ControllerInterface.UNKNOWN_ERROR),
            eq("Unexpected Exception"),
            eq(due));
  }

  @Test(
      description =
          "Handling timeout event for an invalid client transaction. "
              + "No 408 (Request timeout) response is generated in this case")
  public void testClientTransactionTimeoutEventOnInvalidTransaction() throws Exception {
    // setup
    proxyTransaction.setM_originalProxyClientTrans(null);
    ClientTransaction clientTransaction = mock(ClientTransaction.class);
    SipProvider sipProvider = mock(SipProvider.class);

    SIPRequest request =
        SIPRequestBuilder.createRequest(
            new SIPRequestBuilder().getRequestAsString(SIPRequestBuilder.RequestMethod.INVITE));
    proxyTransaction.setOriginalRequest(request);

    // call
    proxyTransaction.timeOut(clientTransaction, sipProvider);

    verify(controllerInterface, Mockito.times(0))
        .onRequestTimeOut(eq(proxyTransaction), any(ProxyCookie.class), eq(proxyClientTransaction));

    ProxySIPResponse bestResponse = proxyTransaction.getBestResponse();
    Assert.assertNull(bestResponse);
  }

  @Test(
      description =
          "Handling timeout event for client transaction when timeout was already fired. "
              + "No 408 (Request timeout) response is generated in this case")
  public void testClientTransactionTimeoutEventAlreadyTimedout() throws Exception {
    // setup
    proxyTransaction.setM_originalProxyClientTrans(proxyClientTransaction);
    when(proxyClientTransaction.getCookie()).thenReturn(mock(ProxyCookie.class));
    ClientTransaction clientTransaction = mock(ClientTransaction.class);
    SipProvider sipProvider = mock(SipProvider.class);

    SIPRequest request =
        SIPRequestBuilder.createRequest(
            new SIPRequestBuilder().getRequestAsString(SIPRequestBuilder.RequestMethod.INVITE));
    proxyTransaction.setOriginalRequest(request);
    // call
    when(proxyClientTransaction.isTimedOut()).thenReturn(true);
    proxyTransaction.timeOut(clientTransaction, sipProvider);

    verify(controllerInterface, Mockito.times(0))
        .onRequestTimeOut(eq(proxyTransaction), any(ProxyCookie.class), eq(proxyClientTransaction));

    ProxySIPResponse bestResponse = proxyTransaction.getBestResponse();
    Assert.assertNull(bestResponse);
  }

  @Test(
      description =
          "Handling timeout event for client transaction when the transaction is in state other than REQUEST_SENT (or) PROV_RECVD. "
              + "No 408 (Request timeout) response is generated in this case")
  public void testClientTransactionTimeoutEventInNoActionState() throws Exception {
    // setup
    proxyTransaction.setM_originalProxyClientTrans(proxyClientTransaction);
    when(proxyClientTransaction.getCookie()).thenReturn(mock(ProxyCookie.class));
    ClientTransaction clientTransaction = mock(ClientTransaction.class);
    SipProvider sipProvider = mock(SipProvider.class);

    SIPRequest request =
        SIPRequestBuilder.createRequest(
            new SIPRequestBuilder().getRequestAsString(SIPRequestBuilder.RequestMethod.INVITE));
    proxyTransaction.setOriginalRequest(request);
    // call
    when(proxyClientTransaction.getState()).thenReturn(ProxyClientTransaction.STATE_FINAL_RECVD);
    proxyTransaction.timeOut(clientTransaction, sipProvider);

    verify(controllerInterface, Mockito.times(0))
        .onRequestTimeOut(eq(proxyTransaction), any(ProxyCookie.class), eq(proxyClientTransaction));

    ProxySIPResponse bestResponse = proxyTransaction.getBestResponse();
    Assert.assertNull(bestResponse);
  }

  @DataProvider
  public Object[] getClientTransactionState() {
    return new Integer[][] {
      {ProxyClientTransaction.STATE_PROV_RECVD}, {ProxyClientTransaction.STATE_REQUEST_SENT}
    };
  }

  @Test(
      dataProvider = "getClientTransactionState",
      description =
          "Handling timeout event for client transaction. "
              + "a) If ProxyClientTransaction is in 'STATE_PROV_RECVD' & got timeout event, then 408 (Request timeout) response is sent on server transaction & CANCEL is sent to this client transaction. "
              + "b) If ProxyClientTransaction is 'not in STATE_PROV_RECVD' & got timeout event, only 408 (Request timeout) response is sent on server transaction. CANCEL should not be sent on this client transaction in this scenario")
  public void testClientTransactionTimeoutCancelFlow(int state) throws Exception {
    // setup
    proxyTransaction.setM_originalProxyClientTrans(proxyClientTransaction);
    when(proxyClientTransaction.getCookie()).thenReturn(mock(ProxyCookie.class));
    ClientTransaction clientTransaction = mock(ClientTransaction.class);
    SipProvider sipProvider = mock(SipProvider.class);
    when(proxyClientTransaction.getState()).thenReturn(state);

    SIPRequest request =
        SIPRequestBuilder.createRequest(
            new SIPRequestBuilder().getRequestAsString(SIPRequestBuilder.RequestMethod.INVITE));
    proxyTransaction.setOriginalRequest(request);
    // call
    proxyTransaction.timeOut(clientTransaction, sipProvider);

    // Only if transaction is in 'Provisional response received state' -> then send a cancel out
    if (state == ProxyClientTransaction.STATE_PROV_RECVD) {
      // Verify cancel is invoked on client transaction
      verify(proxyClientTransaction, Mockito.times(1)).cancel();
    }

    verify(controllerInterface, Mockito.times(1))
        .onRequestTimeOut(eq(proxyTransaction), any(ProxyCookie.class), eq(proxyClientTransaction));

    ProxySIPResponse bestResponse = proxyTransaction.getBestResponse();
    Assert.assertEquals(bestResponse.getStatusCode(), Response.REQUEST_TIMEOUT);
  }

  @Test(description = "Handling time out event for server transaction")
  public void testServerTransactionTimeout() {
    // setup
    proxyTransaction.setM_originalProxyClientTrans(proxyClientTransaction);
    when(proxyClientTransaction.getCookie()).thenReturn(mock(ProxyCookie.class));
    SIPResponse sipResponse = mock(SIPResponse.class);
    when(sipResponse.getStatusCode()).thenReturn(Response.NOT_FOUND);
    when(proxyServerTransaction.getResponse()).thenReturn(sipResponse);

    // call
    proxyTransaction.timeOut(serverTransaction);

    verify(controllerInterface, Mockito.times(1))
        .onResponseTimeOut(eq(proxyTransaction), eq(proxyServerTransaction));
  }

  @Test(
      description =
          "If transaction is not forked, then test invocation of cancel() on the original client transaction alone")
  public void testCancelClientTransactionWhenNotForked() {

    proxyTransaction.setM_isForked(false);
    proxyTransaction.setM_originalProxyClientTrans(proxyClientTransaction);

    proxyTransaction.cancel();

    verify(proxyClientTransaction, times(1)).cancel();
  }

  @Test(
      description =
          "If transaction is forked, then test invocation of cancel() on all client transactions")
  public void testCancelClientTransactionWhenForked() {

    ClientTransaction ct1 = mock(ClientTransaction.class);
    ClientTransaction ct2 = mock(ClientTransaction.class);

    ProxyClientTransaction pct1 = mock(ProxyClientTransaction.class);
    ProxyClientTransaction pct2 = mock(ProxyClientTransaction.class);

    Map<ClientTransaction, ProxyClientTransaction> branches = new HashMap<>(2);
    branches.put(ct1, pct1);
    branches.put(ct2, pct2);

    proxyTransaction.setBranches(branches);
    proxyTransaction.setM_isForked(true);

    proxyTransaction.cancel();

    verify(pct1, times(1)).cancel();
    verify(pct2, times(1)).cancel();
  }
}
