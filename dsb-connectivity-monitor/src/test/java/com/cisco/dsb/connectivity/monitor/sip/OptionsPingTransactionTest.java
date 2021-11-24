package com.cisco.dsb.connectivity.monitor.sip;

import static org.mockito.Mockito.*;

import com.cisco.dsb.common.executor.DhruvaExecutorService;
import com.cisco.dsb.common.executor.ExecutorType;
import com.cisco.dsb.common.sip.bean.SIPListenPoint;
import com.cisco.dsb.common.sip.stack.dto.DhruvaNetwork;
import com.cisco.dsb.common.transport.Transport;
import gov.nist.javax.sip.message.SIPRequest;
import gov.nist.javax.sip.message.SIPResponse;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executors;
import javax.sip.*;
import javax.sip.header.CSeqHeader;
import org.junit.Assert;
import org.mockito.*;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.Test;

public class OptionsPingTransactionTest {

  @Spy ClientTransaction clientTransaction;
  @Spy SIPRequest request;
  @Mock DhruvaNetwork dhruvaNetwork;
  @Mock ResponseEvent responseEvent;
  @Mock SIPListenPoint sipListenPoint;
  @Mock SipProvider sipProvider;
  @Mock DhruvaExecutorService dhruvaExecutorService;

  @BeforeClass
  void init() throws TransactionUnavailableException {
    MockitoAnnotations.initMocks(this);
    when(dhruvaExecutorService.getExecutorThreadPool(ExecutorType.OPTIONS_PING))
        .thenReturn(Executors.newSingleThreadExecutor());

    when(sipListenPoint.getTransport()).thenReturn(Transport.TCP);
    when(dhruvaNetwork.getListenPoint()).thenReturn(sipListenPoint);
    when(sipProvider.getNewClientTransaction(any())).thenReturn(clientTransaction);
  }

  @Test(description = "send OptionsPing request" + "recieve the response without any failures")
  void testCompletableFutureResponse()
      throws SipException, ExecutionException, InterruptedException {

    OptionsPingTransaction optionsPingTransaction =
        new OptionsPingTransaction(dhruvaExecutorService);
    doNothing().when(clientTransaction).sendRequest();
    CompletableFuture<SIPResponse> responseCompletableFuture =
        optionsPingTransaction.proxySendOutBoundRequest(request, dhruvaNetwork, sipProvider);

    SIPResponse response = mock(SIPResponse.class);
    CSeqHeader header = mock(CSeqHeader.class);
    when(header.getMethod()).thenReturn("OPTIONS");
    when(response.getCSeq()).thenReturn(header);
    when(response.getCSeq().getMethod()).thenReturn("OPTIONS");

    when(response.getStatusCode()).thenReturn(200);

    when(responseEvent.getClientTransaction()).thenReturn(clientTransaction);
    when(responseEvent.getResponse()).thenReturn(response);

    when(clientTransaction.getApplicationData())
        .thenReturn(optionsPingTransaction.applicationDataCookie);

    optionsPingTransaction.processResponse(responseEvent);
    Assert.assertTrue(responseCompletableFuture.get().getStatusCode() == 200);
  }

  @Test(description = "options ping request sending failure ")
  void testCFOptionWithException() throws SipException, InterruptedException {

    OptionsPingTransaction optionsPingTransaction =
        new OptionsPingTransaction(dhruvaExecutorService);
    when(clientTransaction.getRequest()).thenReturn(request);
    when(((SIPRequest) clientTransaction.getRequest()).getRemoteAddress()).thenReturn(null);
    when(((SIPRequest) clientTransaction.getRequest()).getRemotePort()).thenReturn(123);
    doThrow(SipException.class).when(clientTransaction).sendRequest();
    CompletableFuture<SIPResponse> responseCompletableFuture =
        optionsPingTransaction.proxySendOutBoundRequest(request, dhruvaNetwork, sipProvider);
    Thread.sleep(1000);
    Assert.assertTrue(responseCompletableFuture.isCompletedExceptionally());
  }

  @Test(description = "test with Invite/ any request other than OPTIONS " + "shouldn't process ")
  void testAgainstInviteResponse() {
    SIPResponse response = mock(SIPResponse.class);
    CSeqHeader header = mock(CSeqHeader.class);
    when(response.getCSeq()).thenReturn(header);
    when(response.getCSeq().getMethod()).thenReturn("INVITE");
    ResponseEvent responseEvent = mock(ResponseEvent.class);
    when(responseEvent.getResponse()).thenReturn(response);
    when(response.getCSeq().getMethod()).thenReturn("INVITE");

    OptionsPingTransaction optionsPingTransaction =
        new OptionsPingTransaction(dhruvaExecutorService);

    optionsPingTransaction.processResponse(responseEvent);
    verify(responseEvent, times(0)).getClientTransaction();
  }

  @Test(description = "UDP port should be same as listen network")
  void testForceUDPSourcePort() throws SipException {
    SIPRequest request1 = new SIPRequest();
    request = Mockito.spy(request1);

    OptionsPingTransaction optionsPingTransaction =
        new OptionsPingTransaction(dhruvaExecutorService);
    when(sipListenPoint.getTransport()).thenReturn(Transport.UDP);
    when(sipListenPoint.getPort()).thenReturn(5999);
    request.setLocalPort(5888);
    optionsPingTransaction.proxySendOutBoundRequest(request, dhruvaNetwork, sipProvider);
    Assert.assertTrue(request.getLocalPort() == 5999);
  }
}
