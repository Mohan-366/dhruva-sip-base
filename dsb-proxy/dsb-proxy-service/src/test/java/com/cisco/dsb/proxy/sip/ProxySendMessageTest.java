package com.cisco.dsb.proxy.sip;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.*;

import com.cisco.dsb.common.context.ExecutionContext;
import com.cisco.dsb.common.exception.DhruvaException;
import com.cisco.dsb.common.record.DhruvaAppRecord;
import com.cisco.dsb.common.sip.header.RemotePartyID;
import com.cisco.dsb.proxy.messaging.MessageConvertor;
import com.cisco.dsb.proxy.messaging.ProxySIPRequest;
import com.cisco.dsb.proxy.util.RequestHelper;
import gov.nist.javax.sip.header.Accept;
import gov.nist.javax.sip.header.AcceptEncodingList;
import gov.nist.javax.sip.message.SIPRequest;
import gov.nist.javax.sip.message.SIPResponse;
import gov.nist.javax.sip.stack.SIPServerTransactionImpl;
import java.text.ParseException;
import javax.sip.*;
import javax.sip.address.Hop;
import javax.sip.address.Router;
import javax.sip.message.Response;
import org.testng.Assert;
import org.testng.annotations.Test;
import reactor.test.StepVerifier;

public class ProxySendMessageTest {

  @Test
  void testSendResponseAsync() throws ParseException, InvalidArgumentException, SipException {

    SipProvider sipProvider = mock(SipProvider.class);
    ServerTransaction serverTransaction = mock(ServerTransaction.class);
    SIPRequest request = (SIPRequest) RequestHelper.getInviteRequest();
    String TEST_CALLTYPE = "testCalltype";
    doNothing().when(serverTransaction).sendResponse(any());

    StepVerifier.create(
            ProxySendMessage.sendResponseAsync(
                200, sipProvider, serverTransaction, request, TEST_CALLTYPE, null))
        .expectNextCount(0)
        .verifyComplete();
  }

  @Test
  void testSendResponseAsync2() throws ParseException, InvalidArgumentException, SipException {
    SipProvider sipProvider = mock(SipProvider.class);
    ServerTransaction serverTransaction = mock(ServerTransaction.class);
    SIPRequest request = (SIPRequest) RequestHelper.getInviteRequest();
    String TEST_CALLTYPE = "testCalltype";

    doThrow(new SipException("dummy jain sip exception"))
        .when(serverTransaction)
        .sendResponse(any());

    StepVerifier.create(
            ProxySendMessage.sendResponseAsync(
                200, sipProvider, serverTransaction, request, TEST_CALLTYPE, null))
        .expectError()
        .verify();
  }

  @Test
  void testSendResponseAsync3() throws ParseException, SipException {
    SipProvider sipProvider = mock(SipProvider.class);

    doNothing().when(sipProvider).sendResponse(any());

    SIPRequest request = (SIPRequest) RequestHelper.getInviteRequest();
    String TEST_CALLTYPE = "testCalltype";

    StepVerifier.create(
            ProxySendMessage.sendResponseAsync(
                404, sipProvider, null, request, TEST_CALLTYPE, null))
        .expectNextCount(0)
        .verifyComplete();
  }

  @Test
  void testSendResponse() {
    assertThrows(
        DhruvaException.class,
        () -> ProxySendMessage.sendResponse(1, "testCallType", null, null, new SIPRequest(), null));
    assertThrows(
        DhruvaException.class,
        () ->
            ProxySendMessage.sendResponse(
                new SIPResponse(), null, null, true, "testCallType", null));
  }

  @Test
  void testSendResponse2() throws SipException, DhruvaException, ParseException {
    SIPServerTransactionImpl sipServerTransactionImpl = mock(SIPServerTransactionImpl.class);
    when(sipServerTransactionImpl.getSipProvider()).thenReturn(null);
    SIPResponse sipResponse = new SIPResponse();
    sipResponse.setStatusCode(Response.OK);
    doNothing().when(sipServerTransactionImpl).sendResponse(sipResponse);
    ProxySendMessage.sendResponse(sipServerTransactionImpl, sipResponse, true, null, null);
    verify(sipServerTransactionImpl).getSipProvider();
    verify(sipServerTransactionImpl).sendResponse(any());
  }

  @Test
  void testSendResponse3() throws SipException, DhruvaException, ParseException {
    SIPServerTransactionImpl sipServerTransactionImpl = mock(SIPServerTransactionImpl.class);
    when(sipServerTransactionImpl.getSipProvider()).thenReturn(null);
    doNothing().when(sipServerTransactionImpl).sendResponse(any());

    SIPResponse sipResponse = new SIPResponse();
    sipResponse.addHeader(new RemotePartyID());
    sipResponse.setStatusCode(Response.OK);
    ProxySendMessage.sendResponse(sipServerTransactionImpl, sipResponse, true, null, null);
    verify(sipServerTransactionImpl).getSipProvider();
    verify(sipServerTransactionImpl).sendResponse(any());
  }

  @Test
  void testSendResponse5() throws DhruvaException, SipException {
    SIPServerTransactionImpl sipServerTransactionImpl = mock(SIPServerTransactionImpl.class);
    when(sipServerTransactionImpl.getSipProvider()).thenReturn(null);
    doNothing().when(sipServerTransactionImpl).sendResponse(any());

    SIPResponse sipResponse = new SIPResponse();
    sipResponse.setReasonPhrase("Just cause");
    ProxySendMessage.sendResponse(sipServerTransactionImpl, sipResponse, true, null, null);
    verify(sipServerTransactionImpl).getSipProvider();
    verify(sipServerTransactionImpl).sendResponse(any());
  }

  @Test
  void testSendResponse6() throws DhruvaException, SipException {
    SIPServerTransactionImpl sipServerTransactionImpl = mock(SIPServerTransactionImpl.class);
    when(sipServerTransactionImpl.getSipProvider()).thenReturn(null);
    doNothing().when(sipServerTransactionImpl).sendResponse(any());

    SIPResponse sipResponse = new SIPResponse();
    sipResponse.setReasonPhrase("Just cause");
    sipResponse.addHeader(new Accept());
    ProxySendMessage.sendResponse(sipServerTransactionImpl, sipResponse, true, null, null);
    verify(sipServerTransactionImpl).getSipProvider();
    verify(sipServerTransactionImpl).sendResponse(any());
  }

  @Test
  void testSendResponse7() throws DhruvaException, SipException {
    SIPServerTransactionImpl sipServerTransactionImpl = mock(SIPServerTransactionImpl.class);
    when(sipServerTransactionImpl.getSipProvider()).thenReturn(null);
    doNothing().when(sipServerTransactionImpl).sendResponse(any());

    SIPResponse sipResponse = new SIPResponse();
    sipResponse.setReasonPhrase("Just cause");
    sipResponse.addHeader(new AcceptEncodingList());
    ProxySendMessage.sendResponse(sipServerTransactionImpl, sipResponse, true, null, null);
    verify(sipServerTransactionImpl).getSipProvider();
    verify(sipServerTransactionImpl).sendResponse(any());
  }

  @Test
  void testSendResponse8() throws DhruvaException, ParseException, SipException {
    SIPServerTransactionImpl sipServerTransactionImpl = mock(SIPServerTransactionImpl.class);
    when(sipServerTransactionImpl.getSipProvider()).thenReturn(null);
    doNothing().when(sipServerTransactionImpl).sendResponse(any());

    Accept accept = new Accept();
    accept.setParameter("sip:xx", "sip:xx");

    SIPResponse sipResponse = new SIPResponse();
    sipResponse.setReasonPhrase("Just cause");
    sipResponse.addHeader(accept);
    ProxySendMessage.sendResponse(sipServerTransactionImpl, sipResponse, true, null, null);
    verify(sipServerTransactionImpl).getSipProvider();
    verify(sipServerTransactionImpl).sendResponse(any());
  }

  @Test
  void testSendRequest() {
    assertThrows(
        DhruvaException.class,
        () -> ProxySendMessage.sendRequest(new SIPRequest(), null, null, null));
  }

  @Test
  void testSendRequest1() throws SipException, ParseException, DhruvaException {
    SIPRequest request = (SIPRequest) RequestHelper.getInviteRequest();
    ClientTransaction clientTransaction = mock(ClientTransaction.class);
    doNothing().when(clientTransaction).sendRequest();
    ProxySendMessage.sendRequest(request, clientTransaction, null, null);
    verify(clientTransaction).sendRequest();
  }

  @Test
  void testSendRequest2() throws SipException, ParseException, DhruvaException {
    SIPRequest request = (SIPRequest) RequestHelper.getInviteRequest();
    SipProvider sipProvider = mock(SipProvider.class);
    doNothing().when(sipProvider).sendRequest(request);
    ProxySendMessage.sendRequest(request, null, sipProvider, null);
    verify(sipProvider).sendRequest(request);
  }

  @Test
  void testSendProxyRequestAsync() throws SipException, ParseException {
    SIPRequest request = (SIPRequest) RequestHelper.getInviteRequest();
    SipStack sipStack = mock(SipStack.class);
    Router router = mock(Router.class);
    Hop hop = mock(Hop.class);
    SipProvider sipProvider = mock(SipProvider.class);

    when(sipProvider.getSipStack()).thenReturn(sipStack);
    when(sipStack.getRouter()).thenReturn(router);
    when(router.getNextHop(request)).thenReturn(hop);
    doNothing().when(sipProvider).sendRequest(request);

    when(hop.getHost()).thenReturn("1.2.3.4");
    when(hop.getPort()).thenReturn(5080);

    ProxySIPRequest proxyRequest =
        MessageConvertor.convertJainSipRequestMessageToDhruvaMessage(
            request, sipProvider, mock(ServerTransaction.class), mock(ExecutionContext.class));
    proxyRequest.setAppRecord(new DhruvaAppRecord());
    StepVerifier.create(ProxySendMessage.sendProxyRequestAsync(sipProvider, null, proxyRequest))
        .assertNext(proxySIPRequest -> Assert.assertEquals(proxyRequest, proxySIPRequest))
        .verifyComplete();
  }

  @Test
  void testSendProxyRequestAsync1() throws ParseException, SipException {
    SIPRequest request = (SIPRequest) RequestHelper.getInviteRequest();
    SipStack sipStack = mock(SipStack.class);
    Router router = mock(Router.class);
    Hop hop = mock(Hop.class);
    SipProvider sipProvider = mock(SipProvider.class);

    when(sipProvider.getSipStack()).thenReturn(sipStack);
    when(sipStack.getRouter()).thenReturn(router);
    when(router.getNextHop(request)).thenReturn(hop);
    doNothing().when(sipProvider).sendRequest(request);

    when(hop.getHost()).thenReturn("1.2.3.4");
    when(hop.getPort()).thenReturn(5080);
    ClientTransaction clientTransaction = mock(ClientTransaction.class);
    when(clientTransaction.getRequest()).thenReturn(request);
    doNothing().when(clientTransaction).sendRequest();
    ProxySIPRequest proxyRequest =
        MessageConvertor.convertJainSipRequestMessageToDhruvaMessage(
            request,
            mock(SipProvider.class),
            mock(ServerTransaction.class),
            mock(ExecutionContext.class));
    proxyRequest.setAppRecord(new DhruvaAppRecord());
    StepVerifier.create(
            ProxySendMessage.sendProxyRequestAsync(sipProvider, clientTransaction, proxyRequest))
        .assertNext(proxySIPRequest -> Assert.assertEquals(proxyRequest, proxySIPRequest))
        .verifyComplete();
  }

  @Test
  void testSendProxyRequestAsync2() throws ParseException, SipException {
    SIPRequest request = (SIPRequest) RequestHelper.getInviteRequest();
    SipStack sipStack = mock(SipStack.class);
    Router router = mock(Router.class);
    Hop hop = mock(Hop.class);
    SipProvider sipProvider = mock(SipProvider.class);

    when(sipProvider.getSipStack()).thenReturn(sipStack);
    when(sipStack.getRouter()).thenReturn(router);
    when(router.getNextHop(request)).thenReturn(hop);
    doNothing().when(sipProvider).sendRequest(request);

    when(hop.getHost()).thenReturn("1.2.3.4");
    when(hop.getPort()).thenReturn(5080);
    ClientTransaction clientTransaction = mock(ClientTransaction.class);
    doThrow(new SipException("dummy sip exception")).when(clientTransaction).sendRequest();

    ProxySIPRequest proxyRequest =
        MessageConvertor.convertJainSipRequestMessageToDhruvaMessage(
            request,
            mock(SipProvider.class),
            mock(ServerTransaction.class),
            mock(ExecutionContext.class));

    StepVerifier.create(
            ProxySendMessage.sendProxyRequestAsync(sipProvider, clientTransaction, proxyRequest))
        .expectError()
        .verify();
  }
}
