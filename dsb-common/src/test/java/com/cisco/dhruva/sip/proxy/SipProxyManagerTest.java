package com.cisco.dhruva.sip.proxy;

import static org.mockito.Mockito.*;
import static org.testng.Assert.*;

import com.cisco.dsb.common.messaging.ProxySIPResponse;
import com.cisco.dsb.util.ResponseHelper;
import javax.sip.ClientTransaction;
import javax.sip.ResponseEvent;
import javax.sip.SipProvider;
import org.mockito.*;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.Test;

public class SipProxyManagerTest {

  @InjectMocks SipProxyManager sipProxyManager;

  @BeforeClass
  public void init() {
    MockitoAnnotations.initMocks(this);
  }

  @Test(description = "test to find ProxyTransaction for incoming Response")
  public void findProxyTransactionTest() {
    ResponseEvent responseEvent = mock(ResponseEvent.class);
    when(responseEvent.getResponse()).thenReturn(ResponseHelper.getSipResponse());
    when(responseEvent.getSource()).thenReturn(mock(SipProvider.class));
    ClientTransaction ct = mock(ClientTransaction.class);
    ProxyTransaction pt = mock(ProxyTransaction.class);
    when(responseEvent.getClientTransaction()).thenReturn(ct);
    when(ct.getApplicationData()).thenReturn(pt);
    ProxySIPResponse proxySIPResponse = sipProxyManager.findProxyTransaction().apply(responseEvent);
    assertEquals(proxySIPResponse.getProxyTransaction(), pt);
  }

  @Test(description = "test to find ProxyTransaction for incoming Stray Response")
  public void findProxyTransactionStrayResponseTest() {
    ResponseEvent responseEvent = mock(ResponseEvent.class);
    when(responseEvent.getClientTransaction()).thenReturn(null);
    ProxySIPResponse proxySIPResponse = sipProxyManager.findProxyTransaction().apply(responseEvent);
    assertNull(proxySIPResponse);
  }

  @Test(description = "test to process the proxyTransaction for provisional response")
  public void processProxyTransactionProvisionalTest() {
    ProxyTransaction pt = mock(ProxyTransaction.class);
    ProxySIPResponse proxySIPResponse = mock(ProxySIPResponse.class);

    when(proxySIPResponse.getProxyTransaction()).thenReturn(pt);
    when(proxySIPResponse.getResponseClass()).thenReturn(1);

    sipProxyManager.processProxyTransaction().apply(proxySIPResponse);
    verify(pt, Mockito.times(1)).provisionalResponse(proxySIPResponse);
  }

  @Test(description = "test to process the proxyTransaction for final response")
  public void processProxyTransactionFinalTest() {
    ProxyTransaction pt = mock(ProxyTransaction.class);
    ProxySIPResponse proxySIPResponse = mock(ProxySIPResponse.class);

    when(proxySIPResponse.getProxyTransaction()).thenReturn(pt);
    when(proxySIPResponse.getResponseClass()).thenReturn(2);

    sipProxyManager.processProxyTransaction().apply(proxySIPResponse);
    verify(pt, Mockito.times(1)).finalResponse(proxySIPResponse);
  }
}
