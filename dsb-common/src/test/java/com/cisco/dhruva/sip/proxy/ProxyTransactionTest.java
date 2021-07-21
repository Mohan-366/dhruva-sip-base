package com.cisco.dhruva.sip.proxy;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

import com.cisco.dhruva.sip.proxy.errors.DestinationUnreachableException;
import com.cisco.dhruva.sip.proxy.errors.InternalProxyErrorException;
import com.cisco.dsb.common.messaging.ProxySIPResponse;
import com.cisco.dsb.exception.DhruvaException;
import com.cisco.dsb.util.TriFunction;
import gov.nist.javax.sip.message.SIPRequest;
import gov.nist.javax.sip.message.SIPResponse;
import java.io.IOException;
import javax.sip.InvalidArgumentException;
import javax.sip.ServerTransaction;
import javax.sip.SipException;
import javax.sip.header.ViaHeader;
import javax.sip.message.Request;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.MockitoAnnotations;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.BeforeTest;
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
    proxyTransaction =
        new ProxyTransaction(
            controllerInterface, proxyParamsInterface, serverTransaction, sipRequest);
  }

  @Test(description = "Handling provisional Response in ProxyTransaction")
  public void testProvisionalResponse() {

    // proxyTransaction = spy(proxyTransaction);
    proxyTransaction.setM_isForked(false);
    proxyTransaction.setM_originalProxyClientTrans(proxyClientTransaction);
    // Test for proxyTransaction with ProxyClientTransaction
    proxyTransaction.provisionalResponse(proxySIPResponse);

    verify(sipResponse, Mockito.times(1)).removeFirst(ViaHeader.NAME);
    verify(proxyClientTransaction, Mockito.times(1)).gotResponse(proxySIPResponse);
    verify(controllerInterface, Mockito.times(1)).onProvisionalResponse(any(), any(), any(), any());

    reset(sipResponse, proxyClientTransaction, controllerInterface);

    // test behaviour when proxyClientTransaction is null
    proxyTransaction.setM_originalProxyClientTrans(null);
    proxyTransaction.provisionalResponse(proxySIPResponse);

    verify(sipResponse, Mockito.times(0)).removeFirst(ViaHeader.NAME);
    verify(proxyClientTransaction, Mockito.times(0)).gotResponse(proxySIPResponse);
    verify(controllerInterface, Mockito.times(0)).onProvisionalResponse(any(), any(), any(), any());
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
        .onSuccessResponse(
            eq(proxyTransaction), any(), eq(proxyClientTransaction), eq(proxySIPResponse));
    verify(controllerInterface, Mockito.times(1))
        .onBestResponse(eq(proxyTransaction), eq(proxySIPResponse));
  }

  @Test(description = "Handling 2xx Response Retransmission in ProxyTransaction")
  public void testFinalResponse2xxRetransmit()
      throws InvalidArgumentException, DhruvaException, SipException {
    // setup
    proxyTransaction.setM_originalProxyClientTrans(proxyClientTransaction);
    when(proxySIPResponse.getResponseClass()).thenReturn(2);
    when(proxyClientTransaction.getState())
        .thenReturn(ProxyClientTransaction.STATE_FINAL_RETRANSMISSION_RECVD);

    // call
    proxyTransaction.finalResponse(proxySIPResponse);
    // verify
    verify(controllerInterface, Mockito.times(1)).onResponse(proxySIPResponse);
    verify(sipResponse, Mockito.times(1)).removeFirst(ViaHeader.NAME);
    verify(proxyClientTransaction, Mockito.times(1)).gotResponse(proxySIPResponse);
    assert proxyTransaction.getBestResponse() == proxySIPResponse;
    verify(proxyServerTransaction, Mockito.times(1)).retransmit200();
    verify(controllerInterface, Mockito.times(0)).onSuccessResponse(any(), any(), any(), any());
    verify(controllerInterface, Mockito.times(0)).onBestResponse(any(), any());
  }

  @Test(description = "Handling 4xx,5xx Response in ProxyTransaction")
  public void testFinalResponse4xx5xx()
      throws InvalidArgumentException, DhruvaException, IOException, SipException {
    // setup
    proxyTransaction.setM_originalProxyClientTrans(proxyClientTransaction);
    when(proxySIPResponse.getResponseClass()).thenReturn(4);
    when(proxyClientTransaction.isInvite()).thenReturn(true);

    // call
    proxyTransaction.finalResponse(proxySIPResponse);

    // verify
    verify(proxyClientTransaction, Mockito.times(1)).ack();
    verify(controllerInterface, Mockito.times(1))
        .onFailureResponse(
            eq(proxyTransaction), any(), eq(proxyClientTransaction), eq(proxySIPResponse));
  }

  @Test(description = "Handling 6xx Response in ProxyTransaction")
  public void testFinalResponse6xx()
      throws InvalidArgumentException, DhruvaException, IOException, SipException {
    // setup
    proxyTransaction.setM_originalProxyClientTrans(proxyClientTransaction);
    when(proxySIPResponse.getResponseClass()).thenReturn(6);
    when(proxyClientTransaction.isInvite()).thenReturn(true);

    // call
    proxyTransaction.finalResponse(proxySIPResponse);

    // verify
    verify(proxyClientTransaction, Mockito.times(1)).ack();
    verify(controllerInterface, Mockito.times(1))
        .onGlobalFailureResponse(
            eq(proxyTransaction), any(), eq(proxyClientTransaction), eq(proxySIPResponse));
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
}
