package com.cisco.dsb.proxy.sip;

import static org.mockito.Mockito.*;

import com.cisco.dsb.proxy.handlers.OptionsPingResponseListener;
import com.cisco.dsb.proxy.util.RequestHelper;
import gov.nist.javax.sip.message.SIPResponse;
import gov.nist.javax.sip.stack.SIPClientTransactionImpl;
import java.text.ParseException;
import javax.sip.RequestEvent;
import javax.sip.ResponseEvent;
import javax.sip.TimeoutEvent;
import javax.sip.TransactionTerminatedEvent;
import javax.sip.header.CSeqHeader;
import org.mockito.*;
import org.testng.Assert;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

public class ProxyPacketProcessorTest {
  @Mock ProxyEventListener proxyEventListener;

  @Mock OptionsPingResponseListener optionsPingResponseListener;

  @InjectMocks ProxyPacketProcessor proxyPacketProcessor;

  @BeforeClass
  void init() {
    MockitoAnnotations.initMocks(this);
  }

  @BeforeMethod
  void reset() {
    Mockito.reset(proxyEventListener, optionsPingResponseListener);
    proxyPacketProcessor.registerOptionsListener(optionsPingResponseListener);
  }

  @Test(
      description =
          "check if 'RequestEvent' received by SipListener from stack is handed over to next layer i.e ProxyEventListener without any modification")
  public void testRequestEvent() {
    RequestEvent requestEvent = mock(RequestEvent.class);
    doNothing().when(proxyEventListener).request(requestEvent);
    proxyPacketProcessor.processRequest(requestEvent);

    ArgumentCaptor<RequestEvent> argumentCaptor = ArgumentCaptor.forClass(RequestEvent.class);
    verify(proxyEventListener, times(1)).request(argumentCaptor.capture());
    RequestEvent requestEventTest = argumentCaptor.getValue();
    Assert.assertEquals(requestEvent, requestEventTest);
  }

  @Test(
      description =
          "check if 'ResponseEvent' received by SipListener from stack is handed over to next layer i.e ProxyEventListener without any modification")
  public void testResponseEvent() {
    ResponseEvent responseEvent = mock(ResponseEvent.class);
    SIPResponse response = mock(SIPResponse.class);
    CSeqHeader header = mock(CSeqHeader.class);
    when(header.getMethod()).thenReturn("INVITE");
    when(responseEvent.getResponse()).thenReturn(response);
    when(response.getCSeq()).thenReturn(header);
    doNothing().when(proxyEventListener).response(responseEvent);
    proxyPacketProcessor.processResponse(responseEvent);

    ArgumentCaptor<ResponseEvent> argumentCaptor = ArgumentCaptor.forClass(ResponseEvent.class);
    verify(proxyEventListener, times(1)).response(argumentCaptor.capture());
    ResponseEvent responseEventTest = argumentCaptor.getValue();
    Assert.assertEquals(responseEvent, responseEventTest);
  }

  @Test
  public void testResponseEventForOptions() {
    ResponseEvent responseEvent = mock(ResponseEvent.class);
    SIPResponse response = mock(SIPResponse.class);
    CSeqHeader header = mock(CSeqHeader.class);
    when(header.getMethod()).thenReturn("OPTIONS");
    when(responseEvent.getResponse()).thenReturn(response);
    when(response.getCSeq()).thenReturn(header);
    doNothing().when(optionsPingResponseListener).processResponse(responseEvent);
    proxyPacketProcessor.processResponse(responseEvent);

    ArgumentCaptor<ResponseEvent> argumentCaptor = ArgumentCaptor.forClass(ResponseEvent.class);
    verify(optionsPingResponseListener, times(1)).processResponse(argumentCaptor.capture());
    ResponseEvent responseEventTest = argumentCaptor.getValue();
    Assert.assertEquals(responseEvent, responseEventTest);
  }

  @Test(
      description =
          "check if 'TimeoutEvent' received by SipListener from stack is handed over to next layer i.e ProxyEventListener without any modification")
  public void testTimeoutEvent() {
    TimeoutEvent timeoutEvent = mock(TimeoutEvent.class);
    doNothing().when(proxyEventListener).timeOut(timeoutEvent);
    when(timeoutEvent.isServerTransaction()).thenReturn(true);
    proxyPacketProcessor.processTimeout(timeoutEvent);

    ArgumentCaptor<TimeoutEvent> argumentCaptor = ArgumentCaptor.forClass(TimeoutEvent.class);
    verify(proxyEventListener, times(1)).timeOut(argumentCaptor.capture());
    TimeoutEvent timeoutEventTest = argumentCaptor.getValue();
    Assert.assertEquals(timeoutEvent, timeoutEventTest);
  }

  @Test(description = "handling timeout for OPTIONS ping")
  public void testOptionsTimeOut() throws ParseException {
    TimeoutEvent timeoutEvent = mock(TimeoutEvent.class);
    SIPClientTransactionImpl clientTransaction = mock(SIPClientTransactionImpl.class);
    doNothing().when(proxyEventListener).timeOut(timeoutEvent);
    when(timeoutEvent.isServerTransaction()).thenReturn(false);
    when(timeoutEvent.getClientTransaction()).thenReturn(clientTransaction);
    when(clientTransaction.getRequest()).thenReturn(RequestHelper.getOptionsRequest());
    proxyPacketProcessor.registerOptionsListener(optionsPingResponseListener);

    proxyPacketProcessor.processTimeout(timeoutEvent);

    verify(optionsPingResponseListener, times(1)).processTimeout(timeoutEvent);
    verify(proxyEventListener, times(0)).timeOut(timeoutEvent);
  }

  @Test(description = "received timeout for OPTIONS with no listener registered")
  public void testOptionsTimeoutNoListener() throws ParseException {
    TimeoutEvent timeoutEvent = mock(TimeoutEvent.class);
    SIPClientTransactionImpl clientTransaction = mock(SIPClientTransactionImpl.class);
    doNothing().when(proxyEventListener).timeOut(timeoutEvent);
    when(timeoutEvent.isServerTransaction()).thenReturn(false);
    when(timeoutEvent.getClientTransaction()).thenReturn(clientTransaction);
    when(clientTransaction.getRequest()).thenReturn(RequestHelper.getOptionsRequest());
    proxyPacketProcessor.registerOptionsListener(null);

    verify(optionsPingResponseListener, times(0)).processTimeout(timeoutEvent);
    verify(proxyEventListener, times(0)).timeOut(timeoutEvent);
  }

  @Test(
      description =
          "check if 'TransactionTerminatedEvent' received by SipListener from stack is handed over to next layer i.e ProxyEventListener without any modification")
  public void testTransactionTerminatedEvent() {
    TransactionTerminatedEvent transactionTerminatedEvent = mock(TransactionTerminatedEvent.class);
    doNothing().when(proxyEventListener).transactionTerminated(transactionTerminatedEvent);
    proxyPacketProcessor.processTransactionTerminated(transactionTerminatedEvent);

    ArgumentCaptor<TransactionTerminatedEvent> argumentCaptor =
        ArgumentCaptor.forClass(TransactionTerminatedEvent.class);
    verify(proxyEventListener).transactionTerminated(argumentCaptor.capture());
    TransactionTerminatedEvent transactionTerminatedEventTest = argumentCaptor.getValue();
    Assert.assertEquals(transactionTerminatedEvent, transactionTerminatedEventTest);
  }
}
