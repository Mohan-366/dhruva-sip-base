package com.cisco.dsb.sip.proxy;

import static org.mockito.Mockito.*;

import javax.sip.RequestEvent;
import javax.sip.ResponseEvent;
import javax.sip.TimeoutEvent;
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

  @Test()
  public void testRequestEvent() {
    RequestEvent requestEvent = mock(RequestEvent.class);
    doNothing().when(proxyEventListener).request(requestEvent);
    proxyPacketProcessor.processRequest(requestEvent);

    ArgumentCaptor<RequestEvent> argumentCaptor = ArgumentCaptor.forClass(RequestEvent.class);
    verify(proxyEventListener, times(1)).request(argumentCaptor.capture());
    RequestEvent requestEventTest = argumentCaptor.getValue();
    Assert.assertEquals(requestEvent, requestEventTest);
  }

  @Test()
  public void testResponseEvent() {
    ResponseEvent responseEvent = mock(ResponseEvent.class);
    doNothing().when(proxyEventListener).response(responseEvent);
    proxyPacketProcessor.processResponse(responseEvent);

    ArgumentCaptor<ResponseEvent> argumentCaptor = ArgumentCaptor.forClass(ResponseEvent.class);
    verify(proxyEventListener, times(1)).response(argumentCaptor.capture());
    ResponseEvent responseEventTest = argumentCaptor.getValue();
    Assert.assertEquals(responseEvent, responseEventTest);
  }

  @Test()
  public void testTimeoutEvent() {
    TimeoutEvent timeoutEvent = mock(TimeoutEvent.class);
    doNothing().when(proxyEventListener).timeOut(timeoutEvent);
    proxyPacketProcessor.processTimeout(timeoutEvent);

    ArgumentCaptor<TimeoutEvent> argumentCaptor = ArgumentCaptor.forClass(TimeoutEvent.class);
    verify(proxyEventListener, times(1)).timeOut(argumentCaptor.capture());
    TimeoutEvent timeoutEventTest = argumentCaptor.getValue();
    Assert.assertEquals(timeoutEvent, timeoutEventTest);
  }
}
