package com.cisco.dhruva.sip.proxy;

import static org.mockito.Mockito.*;
import static org.testng.Assert.*;

import com.cisco.dsb.common.messaging.ProxySIPResponse;
import com.cisco.dsb.sip.jain.JainSipHelper;
import gov.nist.javax.sip.message.SIPResponse;
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

  public SIPResponse getSipResponse() {
    try {
      String response =
          "SIP/2.0 200 OK\n"
              + "Via: SIP/2.0/UDP 127.0.0.1:5070;branch=z9hG4bK-42753-1-0\n"
              + "From: \"Dhruva\" <sip:123@127.0.0.1:5070>;tag=42753SIPpTag001\n"
              + "To: \"sut\" <sip:service@127.0.0.1:5060>;tag=42605SIPpTag011\n"
              + "Call-ID: 1-42753@127.0.0.1\n"
              + "CSeq: 1 INVITE\n"
              + "Allow: UPDATE\n"
              + "Record-Route: <sip:service@127.0.0.1:5080;transport=UDP;lr>\n"
              + "Contact: <sip:service@127.0.0.1:5080;transport=UDP>\n"
              + "Content-Type: application/sdp\n"
              + "Content-Length: 121\n"
              + "\n"
              + "v=0\n"
              + "o=user1 53655765 2353687637 IN IP4 127.0.0.1\n"
              + "s=-\n"
              + "c=IN IP4 127.0.0.1\n"
              + "t=0 0\n"
              + "m=audio 5050 RTP/AVP 0\n"
              + "a=rtpmap:0 PCMU/8000";
      return (SIPResponse) JainSipHelper.getMessageFactory().createResponse(response);
      // return MessageConvertor.convertJainSipResponseMessageToDhruvaMessage(sipResponse,
      // mock(SipProvider.class), null, new ExecutionContext());
    } catch (Exception ex) {
      ex.printStackTrace();
    }
    return null;
  }

  @Test(description = "test to find ProxyTransaction for incoming Response")
  public void findProxyTransactionTest() {
    ResponseEvent responseEvent = mock(ResponseEvent.class);
    when(responseEvent.getResponse()).thenReturn(getSipResponse());
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
