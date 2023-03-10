package com.cisco.dsb.proxy.sip;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

import com.cisco.dsb.common.context.ExecutionContext;
import com.cisco.dsb.common.exception.DhruvaException;
import com.cisco.dsb.common.executor.DhruvaExecutorService;
import com.cisco.dsb.common.executor.ExecutorType;
import com.cisco.dsb.common.sip.bean.SIPListenPoint;
import com.cisco.dsb.common.sip.stack.dto.DhruvaNetwork;
import com.cisco.dsb.common.transport.Transport;
import com.cisco.dsb.common.util.SpringApplicationContext;
import com.cisco.dsb.proxy.messaging.MessageConvertor;
import com.cisco.dsb.proxy.messaging.ProxySIPRequest;
import com.cisco.dsb.proxy.messaging.ProxySIPResponse;
import com.cisco.dsb.proxy.util.RequestHelper;
import com.cisco.dsb.proxy.util.ResponseHelper;
import gov.nist.javax.sip.message.SIPRequest;
import gov.nist.javax.sip.message.SIPResponse;
import java.text.ParseException;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import javax.sip.*;
import javax.sip.message.Request;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.springframework.context.ApplicationContext;
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

  @Mock DhruvaExecutorService dhruvaExecutorService;

  DhruvaNetwork testNetwork1, testNetwork2;

  SpringApplicationContext springApplicationContext;

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

  @DataProvider
  public Object[][] getNetwork() throws DhruvaException {

    SIPListenPoint sipListenPoint =
        SIPListenPoint.SIPListenPointBuilder()
            .setName("test_net_proxyclient")
            .setHostIPAddress("3.3.3.3")
            .setPort(5080)
            .setTransport(Transport.UDP)
            .setRecordRoute(true)
            .build();
    testNetwork1 = DhruvaNetwork.createNetwork("test_net_proxyclient", sipListenPoint);
    DhruvaNetwork.setSipProvider(testNetwork1.getName(), sipProvider);
    SIPListenPoint sipListenPointTCP =
        SIPListenPoint.SIPListenPointBuilder()
            .setName("test_net_proxyclient_tcp")
            .setHostIPAddress("3.3.3.3")
            .setPort(5080)
            .setTransport(Transport.TCP)
            .setRecordRoute(true)
            .build();
    testNetwork2 = DhruvaNetwork.createNetwork("test_net_proxyclient_tcp", sipListenPointTCP);
    DhruvaNetwork.setSipProvider(testNetwork2.getName(), sipProvider);

    return new DhruvaNetwork[][] {{testNetwork1}, {testNetwork2}};
  }

  @BeforeClass
  void init() {
    MockitoAnnotations.openMocks(this);

    springApplicationContext = new SpringApplicationContext();
    ApplicationContext context = mock(ApplicationContext.class);
    springApplicationContext.setApplicationContext(context);

    when(context.getBean(DhruvaExecutorService.class)).thenReturn(dhruvaExecutorService);
  }

  @AfterClass
  void cleanUp() {
    DhruvaNetwork.removeSipProvider(testNetwork1.getName());
    springApplicationContext.setApplicationContext(null);
  }

  // For error responses, Jain Sip stack sends an ACK
  @Test(
      enabled = false,
      description = "dhruva should send an ACK for error response recieved in client transaction")
  public void testAckForErrorResponse()
      throws ParseException, InvalidArgumentException, SipException {
    SIPRequest request = (SIPRequest) RequestHelper.getInviteRequest();
    ProxySIPRequest proxyRequest =
        MessageConvertor.convertJainSipRequestMessageToDhruvaMessage(
            request, sipProvider, serverTransaction, executionContext);

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

    verify(dialog).createAck(any(long.class));
    verify(dialog).sendAck(any(Request.class));
  }

  @Test(
      description = "test a client transaction cancellation based on the transaction state",
      dataProvider = "getNetwork")
  public void testCancel(DhruvaNetwork network) throws SipException, ParseException {

    SIPRequest request = (SIPRequest) RequestHelper.getInviteRequest();

    ProxySIPRequest proxyRequest =
        MessageConvertor.convertJainSipRequestMessageToDhruvaMessage(
            request, sipProvider, serverTransaction, executionContext);

    proxyRequest.setOutgoingNetwork(network.getName());
    ProxyClientTransaction proxyClientTransaction =
        new ProxyClientTransaction(proxyTransaction, clientTransaction, proxyCookie, proxyRequest);

    // Case 1: state = CANCEL_SENT; no CANCEL is sent again
    proxyClientTransaction.setState(ProxyClientTransaction.STATE_CANCEL_SENT);
    proxyClientTransaction.cancel();

    verify(sipProvider, never()).getNewClientTransaction(any(Request.class));

    // Case 2: state = PROV_RECVD; send CANCEL
    proxyClientTransaction.setState(ProxyClientTransaction.STATE_PROV_RECVD);

    // Request cancelReq = mock(Request.class);
    Request cancelReq = mock(SIPRequest.class);
    ClientTransaction cancelTransaction = mock(ClientTransaction.class);
    when(clientTransaction.createCancel()).thenReturn(cancelReq);
    when(sipProvider.getNewClientTransaction(cancelReq)).thenReturn(cancelTransaction);
    doNothing().when(cancelTransaction).sendRequest();

    proxyClientTransaction.cancel();

    ArgumentCaptor<Request> captor = ArgumentCaptor.forClass(Request.class);
    verify(sipProvider).getNewClientTransaction(captor.capture());

    Request req = captor.getValue();
    Assert.assertNotNull(req);
    Assert.assertEquals(req, cancelReq);

    verify(cancelTransaction).sendRequest();
    Assert.assertEquals(
        proxyClientTransaction.getState(), ProxyClientTransaction.STATE_CANCEL_SENT);
  }

  @Test(description = "Schedule Timer C and when timeout event is triggered, remove the timer")
  public void testTimerCSchedulingAndRemovalWhenTxTimesOut() {

    ProxySIPRequest proxyRequest = mock(ProxySIPRequest.class);
    ScheduledThreadPoolExecutor scheduledExecutor = mock(ScheduledThreadPoolExecutor.class);
    ScheduledFuture timerC = mock(ScheduledFuture.class);

    when(dhruvaExecutorService.getScheduledExecutorThreadPool(ExecutorType.PROXY_CLIENT_TIMEOUT))
        .thenReturn(scheduledExecutor);
    when(scheduledExecutor.schedule(any(Runnable.class), anyLong(), any(TimeUnit.class)))
        .thenReturn(timerC);
    when(proxyRequest.getOutgoingNetwork()).thenReturn(testNetwork1.getName());

    ProxyClientTransaction proxyClientTransaction =
        new ProxyClientTransaction(proxyTransaction, clientTransaction, proxyCookie, proxyRequest);

    // Schedule Timer C
    ArgumentCaptor<Runnable> argumentCaptor1 = ArgumentCaptor.forClass(Runnable.class);
    ArgumentCaptor<Long> argumentCaptor2 = ArgumentCaptor.forClass(Long.class);
    ArgumentCaptor<TimeUnit> argumentCaptor3 = ArgumentCaptor.forClass(TimeUnit.class);

    proxyClientTransaction.scheduleTimerC(2);

    verify(scheduledExecutor)
        .schedule(argumentCaptor1.capture(), argumentCaptor2.capture(), argumentCaptor3.capture());
    Assert.assertNotNull(argumentCaptor1.getValue());
    Assert.assertEquals((long) argumentCaptor2.getValue(), 2L);
    Assert.assertEquals(argumentCaptor3.getValue(), TimeUnit.MILLISECONDS);

    // Transaction timed out, so remove Timer C
    proxyClientTransaction.timedOut();
    Assert.assertTrue(proxyClientTransaction.isTimedOut());
    verify(timerC).cancel(false);

    // Calling a 2nd invocation
    proxyClientTransaction.timedOut();
    Assert.assertTrue(proxyClientTransaction.isTimedOut());
    // when timedOut() is called for 2nd time - the cancel() is not invoked. So, totally only 1 call
    // to cancel() is done overall
    verify(timerC).cancel(false);
  }

  @Test(
      description =
          "Timer C should be removed/stopped when the client transaction has received a final response for a request it has sent before")
  public void testTimerCRemovalForAFinalResponse() {

    SIPResponse prevResponseInTx = mock(SIPResponse.class);
    SIPResponse latestResponseInTx = mock(SIPResponse.class);
    ProxySIPResponse proxySIPResponse = mock(ProxySIPResponse.class);
    ProxySIPRequest proxyRequest = mock(ProxySIPRequest.class);
    ScheduledThreadPoolExecutor scheduledExecutor = mock(ScheduledThreadPoolExecutor.class);
    ScheduledFuture timerC = mock(ScheduledFuture.class);

    when(dhruvaExecutorService.getScheduledExecutorThreadPool(ExecutorType.PROXY_CLIENT_TIMEOUT))
        .thenReturn(scheduledExecutor);
    when(scheduledExecutor.schedule(any(Runnable.class), anyLong(), any(TimeUnit.class)))
        .thenReturn(timerC);
    when(proxyRequest.getOutgoingNetwork()).thenReturn(testNetwork1.getName());

    ProxyClientTransaction proxyClientTransaction =
        new ProxyClientTransaction(proxyTransaction, clientTransaction, proxyCookie, proxyRequest);
    proxyClientTransaction.setResponse(prevResponseInTx);
    // Schedule Timer C
    proxyClientTransaction.scheduleTimerC(2);

    when(prevResponseInTx.getStatusCode())
        .thenReturn(180); // last response in the transaction was a provisional response
    when(proxySIPResponse.getResponse()).thenReturn(latestResponseInTx);
    when(proxySIPResponse.getResponseClass())
        .thenReturn(2); // latest response received on the transaction is a 2xx

    // call
    proxyClientTransaction.gotResponse(proxySIPResponse);

    verify(timerC).cancel(false);
  }

  @Test(
      description =
          "If ScheduledExecutorService 'PROXY_CLIENT_TIMEOUT' (which is used for Timeer C) is not started properly, then we cannot scchedule a  task on it")
  public void testTimerCExecutorService() {

    ProxySIPRequest proxyRequest = mock(ProxySIPRequest.class);

    when(dhruvaExecutorService.getScheduledExecutorThreadPool(ExecutorType.PROXY_CLIENT_TIMEOUT))
        .thenReturn(null);
    when(proxyRequest.getOutgoingNetwork()).thenReturn(testNetwork1.getName());

    ProxyClientTransaction proxyClientTransaction =
        new ProxyClientTransaction(proxyTransaction, clientTransaction, proxyCookie, proxyRequest);

    proxyClientTransaction.scheduleTimerC(2);

    verify(proxyTransaction, never()).timeOut(clientTransaction, sipProvider);
  }
}
