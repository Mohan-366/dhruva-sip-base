package com.cisco.dsb.proxy.sip;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

import com.cisco.dsb.common.context.ExecutionContext;
import com.cisco.dsb.common.exception.DhruvaException;
import com.cisco.dsb.common.sip.bean.SIPListenPoint;
import com.cisco.dsb.common.sip.stack.dto.DhruvaNetwork;
import com.cisco.dsb.proxy.messaging.MessageConvertor;
import com.cisco.dsb.proxy.messaging.ProxySIPRequest;
import com.cisco.dsb.proxy.messaging.ProxySIPResponse;
import com.cisco.dsb.proxy.util.RequestHelper;
import com.cisco.dsb.proxy.util.ResponseHelper;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import gov.nist.javax.sip.message.SIPRequest;
import gov.nist.javax.sip.message.SIPResponse;
import java.io.IOException;
import java.text.ParseException;
import javax.sip.*;
import javax.sip.message.Request;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.testng.Assert;
import org.testng.annotations.AfterClass;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.DataProvider;
import org.testng.annotations.Test;

public class ProxyClientTransactionTest {

  @Mock ProxyTransaction proxyTransaction;

  @Mock ClientTransaction clientTransaction;

  @Mock ProxyCookie proxyCookie;

  @Mock SipProvider sipProvider;

  @Mock ServerTransaction serverTransaction;

  @Mock ExecutionContext executionContext;

  @Mock Dialog dialog;

  DhruvaNetwork testNetwork1, testNetwork2;

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

  @BeforeClass
  void init() {}

  @DataProvider
  public Object[] getNetwork() throws JsonProcessingException, DhruvaException {
    MockitoAnnotations.initMocks(this);
    String jsonUDP =
        "{ \"name\": \"test_net_proxyclient\", \"hostIPAddress\": \"3.3.3.3\", \"port\": 5080, \"transport\": \"UDP\", "
            + "\"attachExternalIP\": \"false\", \"recordRoute\": \"true\"}";
    SIPListenPoint sipListenPoint =
        new ObjectMapper().readerFor(SIPListenPoint.class).readValue(jsonUDP);
    testNetwork1 = DhruvaNetwork.createNetwork("test_net_proxyclient", sipListenPoint);
    DhruvaNetwork.setSipProvider(testNetwork1.getName(), sipProvider);
    String jsonTCP =
        "{ \"name\": \"test_net_proxyclient_tcp\", \"hostIPAddress\": \"3.3.3.3\", \"port\": 5080, \"transport\": \"TCP\", "
            + "\"attachExternalIP\": \"false\", \"recordRoute\": \"true\"}";
    SIPListenPoint sipListenPointTCP =
        new ObjectMapper().readerFor(SIPListenPoint.class).readValue(jsonTCP);
    testNetwork2 = DhruvaNetwork.createNetwork("test_net_proxyclient_tcp", sipListenPointTCP);
    DhruvaNetwork.setSipProvider(testNetwork2.getName(), sipProvider);

    return new DhruvaNetwork[] {testNetwork1, testNetwork2};
  }

  @AfterClass
  void cleanUp() {
    DhruvaNetwork.removeSipProvider(testNetwork1.getName());
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
    proxyRequest.setOutgoingNetwork(testNetwork1.getName());
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

    verify(dialog, times(1)).createAck(any(long.class));
    verify(dialog, times(1)).sendAck(any(Request.class));
  }

  @Test(dataProvider = "getNetwork")
  public void testCancel(DhruvaNetwork network) throws SipException, ParseException, IOException {

    SIPRequest request = (SIPRequest) RequestHelper.getInviteRequest();

    ProxySIPRequest proxyRequest =
        MessageConvertor.convertJainSipRequestMessageToDhruvaMessage(
            request, sipProvider, serverTransaction, executionContext);

    proxyRequest.setClonedRequest((SIPRequest) request.clone());
    proxyRequest.setOutgoingNetwork(network.getName());
    ProxyClientTransaction proxyClientTransaction =
        new ProxyClientTransaction(proxyTransaction, clientTransaction, proxyCookie, proxyRequest);

    // Case 1: state = CANCEL_SENT; no CANCEL is sent again
    proxyClientTransaction.setState(ProxyClientTransaction.STATE_CANCEL_SENT);
    proxyClientTransaction.cancel();

    verify(sipProvider, times(0)).getNewClientTransaction(any(Request.class));

    // Case 2: state = REQUEST_SENT; send CANCEL
    proxyClientTransaction.setState(ProxyClientTransaction.STATE_REQUEST_SENT);

    Request cancelReq = mock(Request.class);
    ClientTransaction cancelTransaction = mock(ClientTransaction.class);
    when(clientTransaction.createCancel()).thenReturn(cancelReq);
    when(sipProvider.getNewClientTransaction(cancelReq)).thenReturn(cancelTransaction);
    doNothing().when(cancelTransaction).sendRequest();

    proxyClientTransaction.cancel();

    ArgumentCaptor<Request> captor = ArgumentCaptor.forClass(Request.class);
    verify(sipProvider, times(1)).getNewClientTransaction(captor.capture());

    Request req = captor.getValue();
    Assert.assertNotNull(req);
    Assert.assertEquals(req, cancelReq);

    verify(cancelTransaction).sendRequest();
    Assert.assertEquals(
        proxyClientTransaction.getState(), ProxyClientTransaction.STATE_CANCEL_SENT);
  }
}
