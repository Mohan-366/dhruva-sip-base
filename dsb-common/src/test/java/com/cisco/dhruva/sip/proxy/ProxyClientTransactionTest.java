package com.cisco.dhruva.sip.proxy;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

import com.cisco.dhruva.sip.RequestHelper;
import com.cisco.dsb.common.context.ExecutionContext;
import com.cisco.dsb.common.messaging.MessageConvertor;
import com.cisco.dsb.common.messaging.ProxySIPRequest;
import com.cisco.dsb.common.messaging.ProxySIPResponse;
import com.cisco.dsb.exception.DhruvaException;
import com.cisco.dsb.sip.bean.SIPListenPoint;
import com.cisco.dsb.sip.jain.JainSipHelper;
import com.cisco.dsb.sip.proxy.ProxyStackFactory;
import com.cisco.dsb.sip.stack.dto.DhruvaNetwork;
import com.cisco.dsb.util.ResponseHelper;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import gov.nist.javax.sip.message.SIPRequest;
import gov.nist.javax.sip.message.SIPResponse;
import java.io.IOException;
import java.text.ParseException;
import java.util.TooManyListenersException;
import javax.sip.*;
import javax.sip.message.Request;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.testng.Assert;
import org.testng.annotations.AfterClass;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

public class ProxyClientTransactionTest {

  @Mock ProxyTransaction proxyTransaction;

  @Mock ClientTransaction clientTransaction;

  @Mock ProxyCookie proxyCookie;

  @Mock SipProvider sipProvider;

  @Mock ServerTransaction serverTransaction;

  @Mock ExecutionContext executionContext;

  @Mock Dialog dialog;

  @Mock ProxyPacketProcessor proxyPacketProcessor;

  DhruvaNetwork testNetwork;

  @BeforeMethod
  public void setup() {
    reset(proxyTransaction);
    reset(clientTransaction);
    reset(proxyCookie);
    reset(sipProvider);
    reset(serverTransaction);
    reset(executionContext);
    when(clientTransaction.getDialog()).thenReturn(dialog);
  }

  public SIPListenPoint createTestSipListenPoint() throws JsonProcessingException {
    String json =
        "{ \"name\": \"test_net_proxyclient\", \"hostIPAddress\": \"3.3.3.3\", \"port\": 5080, \"transport\": \"UDP\", "
            + "\"attachExternalIP\": \"false\", \"recordRoute\": \"true\"}";
    return new ObjectMapper().readerFor(SIPListenPoint.class).readValue(json);
  }

  @BeforeClass
  void init() throws JsonProcessingException, DhruvaException {
    MockitoAnnotations.initMocks(this);
    SIPListenPoint sipListenPoint = createTestSipListenPoint();
    testNetwork = DhruvaNetwork.createNetwork("test_net_proxyclient", sipListenPoint);
    DhruvaNetwork.setSipProvider(testNetwork.getName(), sipProvider);
  }

  @AfterClass
  void cleanUp() {
    DhruvaNetwork.removeSipProvider(testNetwork.getName());
  }

  // For error responses, Jain Sip stack sends an ACK
  @Test(
      enabled = false,
      description = "dhruva should send an ACK for error response recieved in client transaction")
  public void testAckForErrorResponse()
      throws ParseException, IOException, InvalidArgumentException, DhruvaException, SipException {
    SIPRequest request = (SIPRequest) RequestHelper.getInviteRequest();
    ProxySIPRequest proxyRequest =
        MessageConvertor.convertJainSipRequestMessageToDhruvaMessage(
            request, sipProvider, serverTransaction, executionContext);

    proxyRequest.setClonedRequest((SIPRequest) request.clone());
    proxyRequest.setOutgoingNetwork(testNetwork.getName());
    ProxyClientTransaction proxyClientTransaction =
        new ProxyClientTransaction(proxyTransaction, clientTransaction, proxyCookie, proxyRequest);
    SIPResponse errorResponse =
        ResponseHelper.getSipResponse(SIPResponse.SERVER_INTERNAL_ERROR, request);
    ProxySIPResponse proxySIPResponse =
        MessageConvertor.convertJainSipResponseMessageToDhruvaMessage(
            errorResponse, sipProvider, clientTransaction, executionContext);
    proxyClientTransaction.gotResponse(proxySIPResponse);
    when(dialog.createAck(any(long.class))).thenReturn(mock(SIPRequest.class));
    doNothing().when(dialog).sendAck(any(SIPRequest.class));
    proxyClientTransaction.ack();

    verify(dialog, times(1)).createAck(any(long.class));
    verify(dialog, times(1)).sendAck(any(Request.class));
  }

  @Test()
  public void testCancel()
      throws SipException, InvalidArgumentException, TooManyListenersException, ParseException,
          IOException {

    SipStack sipStack =
        JainSipHelper.getSipFactory()
            .createSipStack(ProxyStackFactory.getDefaultProxyStackProperties("testSipServer"));
    sipStack.start();
    ListeningPoint lp = sipStack.createListeningPoint("127.0.0.1", 5090, "udp");
    SipProvider sp = sipStack.createSipProvider(lp);
    sp.addSipListener(proxyPacketProcessor);

    SIPRequest request = (SIPRequest) RequestHelper.getInviteRequest();

    ProxySIPRequest proxyRequest =
        MessageConvertor.convertJainSipRequestMessageToDhruvaMessage(
            request, sp, serverTransaction, executionContext);

    proxyRequest.setClonedRequest((SIPRequest) request.clone());
    proxyRequest.setOutgoingNetwork(testNetwork.getName());
    ProxyClientTransaction proxyClientTransaction =
        new ProxyClientTransaction(proxyTransaction, clientTransaction, proxyCookie, proxyRequest);
    proxyClientTransaction.setState(ProxyClientTransaction.STATE_CANCEL_SENT);
    proxyClientTransaction.cancel();

    verify(sipProvider, times(0)).getNewClientTransaction(any(Request.class));

    proxyClientTransaction.setState(ProxyClientTransaction.STATE_REQUEST_SENT);

    when(sipProvider.getNewClientTransaction(any(Request.class))).thenReturn(clientTransaction);
    doNothing().when(clientTransaction).sendRequest();
    Request cancelReq = mock(Request.class);
    when(clientTransaction.createCancel()).thenReturn(cancelReq);
    proxyClientTransaction.cancel();

    ArgumentCaptor<Request> captor = ArgumentCaptor.forClass(Request.class);
    verify(sipProvider, times(1)).getNewClientTransaction(captor.capture());

    Request req = captor.getValue();
    Assert.assertNotNull(req);
    Assert.assertEquals(req, cancelReq);

    verify(clientTransaction).sendRequest();
    Assert.assertEquals(
        proxyClientTransaction.getState(), ProxyClientTransaction.STATE_CANCEL_SENT);

    sipStack.stop();
  }
}
