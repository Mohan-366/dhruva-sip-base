package com.cisco.dsb.connectivity.monitor.sip;

import static org.mockito.Mockito.*;

import com.cisco.dsb.common.executor.DhruvaExecutorService;
import com.cisco.dsb.common.executor.ExecutorType;
import com.cisco.dsb.common.sip.bean.SIPListenPoint;
import com.cisco.dsb.common.sip.stack.dto.DhruvaNetwork;
import com.cisco.dsb.common.transport.Transport;
import com.cisco.dsb.common.util.log.event.EventingService;
import com.cisco.dsb.connectivity.monitor.dto.ApplicationDataCookie;
import com.cisco.dsb.connectivity.monitor.dto.ApplicationDataCookie.Type;
import gov.nist.javax.sip.message.SIPRequest;
import gov.nist.javax.sip.message.SIPResponse;
import java.util.concurrent.*;
import javax.sip.*;
import javax.sip.header.CSeqHeader;
import javax.sip.message.Request;
import org.mockito.*;
import org.testng.Assert;
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
  @Mock EventingService eventingService;

  @BeforeClass
  void init() throws TransactionUnavailableException {
    MockitoAnnotations.openMocks(this);
    when(dhruvaExecutorService.getExecutorThreadPool(ExecutorType.OPTIONS_PING))
        .thenReturn(Executors.newSingleThreadExecutor());

    when(sipListenPoint.getTransport()).thenReturn(Transport.TCP);
    when(sipListenPoint.getPingTimeout()).thenReturn(500);
    when(dhruvaNetwork.getListenPoint()).thenReturn(sipListenPoint);
    when(sipProvider.getNewClientTransaction(any())).thenReturn(clientTransaction);
    doNothing().when(eventingService).publishEvents(any());
  }

  @Test(description = "send OptionsPing request" + "receive the response without any failures")
  void testCompletableFutureResponse()
      throws SipException, ExecutionException, InterruptedException {

    OptionsPingTransaction optionsPingTransaction =
        new OptionsPingTransaction(dhruvaExecutorService, eventingService);
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
        new OptionsPingTransaction(dhruvaExecutorService, eventingService);
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
        new OptionsPingTransaction(dhruvaExecutorService, eventingService);

    optionsPingTransaction.processResponse(responseEvent);
    verify(responseEvent, times(0)).getClientTransaction();
  }

  @Test(description = "UDP port should be same as listen network")
  void testForceUDPSourcePort() throws SipException {
    SIPRequest request1 = new SIPRequest();
    request = Mockito.spy(request1);

    OptionsPingTransaction optionsPingTransaction =
        new OptionsPingTransaction(dhruvaExecutorService, eventingService);
    when(sipListenPoint.getTransport()).thenReturn(Transport.UDP);
    when(sipListenPoint.getPort()).thenReturn(5999);
    request.setLocalPort(5888);
    optionsPingTransaction.proxySendOutBoundRequest(request, dhruvaNetwork, sipProvider);
    Assert.assertTrue(request.getLocalPort() == 5999);
  }

  @Test(expectedExceptions = {NullPointerException.class})
  void testNullSIPRequest() throws SipException {
    OptionsPingTransaction optionsPingTransaction =
        new OptionsPingTransaction(dhruvaExecutorService, eventingService);
    optionsPingTransaction.proxySendOutBoundRequest(null, null, null);
  }

  @Test(expectedExceptions = {NullPointerException.class})
  void testNullNetwork() throws SipException {
    OptionsPingTransaction optionsPingTransaction =
        new OptionsPingTransaction(dhruvaExecutorService, eventingService);
    optionsPingTransaction.proxySendOutBoundRequest(new SIPRequest(), null, null);
  }

  @Test(expectedExceptions = {NullPointerException.class})
  void testNullSipProvider() throws SipException {
    OptionsPingTransaction optionsPingTransaction =
        new OptionsPingTransaction(dhruvaExecutorService, eventingService);
    optionsPingTransaction.proxySendOutBoundRequest(
        new SIPRequest(), DhruvaNetwork.getDefault(), null);
  }

  @Test
  public void testNullClientTransaction() {
    OptionsPingTransaction optionsPingTransaction =
        new OptionsPingTransaction(dhruvaExecutorService, eventingService);
    SIPResponse sipResponse = new SIPResponse();
    Assert.assertNull(optionsPingTransaction.getValidOptionsResponse(null, sipResponse));
  }

  @Test
  public void testCorruptedApplicationDataInClientTransaction() {
    ApplicationDataCookie applicationDataCookie = new ApplicationDataCookie();
    applicationDataCookie.setPayloadType(Type.DEFAULT);
    ClientTransaction mockClientTransaction = mock(ClientTransaction.class);
    when(mockClientTransaction.getApplicationData()).thenReturn(applicationDataCookie);
    OptionsPingTransaction optionsPingTransaction =
        new OptionsPingTransaction(dhruvaExecutorService, eventingService);
    Assert.assertNull(
        optionsPingTransaction.getValidOptionsResponse(mockClientTransaction, new SIPResponse()));
  }

  @Test
  public void testDefaultValueForApplicationDataCookie() {
    OptionsPingTransaction optionsPingTransaction =
        new OptionsPingTransaction(dhruvaExecutorService, eventingService);
    Assert.assertNull(optionsPingTransaction.getApplicationDataCookie(Type.DEFAULT, null));
  }

  @Test(description = "test pingTimeout if CompletableFuture does not timeout by stack event")
  public void testTimeout() throws SipException {
    OptionsPingTransaction optionsPingTransaction =
        new OptionsPingTransaction(dhruvaExecutorService, eventingService);
    when(sipProvider.getNewClientTransaction(any(Request.class))).thenReturn(clientTransaction);
    doNothing().when(clientTransaction).sendRequest();
    CompletableFuture<SIPResponse> responseCompletableFuture =
        optionsPingTransaction.proxySendOutBoundRequest(request, dhruvaNetwork, sipProvider);

    try {
      responseCompletableFuture.get(2000, TimeUnit.MILLISECONDS);
    } catch (ExecutionException | InterruptedException e) {
      if (e.getCause() instanceof TimeoutException) {
        System.out.println(
            "Caught timeout exception as expected,verifying client transaction clean up");
        return;
      }
      Assert.fail("Should have thrown Timeout exception");
    } catch (TimeoutException e) {
      Assert.fail("Should have thrown Timeout exception");
      return;
    }
    Assert.fail("Should have thrown Timeout exception");
  }
}
