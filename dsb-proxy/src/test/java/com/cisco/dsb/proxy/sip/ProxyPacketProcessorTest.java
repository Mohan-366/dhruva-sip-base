package com.cisco.dsb.proxy.sip;

import static org.mockito.Mockito.*;

import gov.nist.javax.sip.message.SIPResponse;
import javax.sip.RequestEvent;
import javax.sip.ResponseEvent;
import javax.sip.TimeoutEvent;
import javax.sip.TransactionTerminatedEvent;
import javax.sip.header.CSeqHeader;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.testng.Assert;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.Test;

public class ProxyPacketProcessorTest {
  @Mock ProxyEventListener proxyEventListener;

  @InjectMocks ProxyPacketProcessor proxyPacketProcessor;

  @BeforeClass
  void init() {
    MockitoAnnotations.initMocks(this);
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

  @Test(
      description =
          "check if 'TimeoutEvent' received by SipListener from stack is handed over to next layer i.e ProxyEventListener without any modification")
  public void testTimeoutEvent() {
    TimeoutEvent timeoutEvent = mock(TimeoutEvent.class);
    doNothing().when(proxyEventListener).timeOut(timeoutEvent);
    proxyPacketProcessor.processTimeout(timeoutEvent);

    ArgumentCaptor<TimeoutEvent> argumentCaptor = ArgumentCaptor.forClass(TimeoutEvent.class);
    verify(proxyEventListener, times(1)).timeOut(argumentCaptor.capture());
    TimeoutEvent timeoutEventTest = argumentCaptor.getValue();
    Assert.assertEquals(timeoutEvent, timeoutEventTest);
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
