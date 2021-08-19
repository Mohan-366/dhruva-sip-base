package com.cisco.dsb.proxy.sip;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

import com.cisco.dsb.common.service.ProxyService;
import com.cisco.dsb.proxy.handlers.ProxyEventHandler;
import com.cisco.dsb.proxy.handlers.SipRequestHandler;
import com.cisco.dsb.proxy.handlers.SipResponseHandler;
import com.cisco.dsb.proxy.handlers.SipTimeOutHandler;
import com.cisco.dsb.proxy.util.ResponseHelper;
import com.cisco.dsb.proxy.util.SIPRequestBuilder;
import com.cisco.wx2.util.stripedexecutor.StripedExecutorService;
import gov.nist.javax.sip.message.SIPRequest;
import gov.nist.javax.sip.message.SIPResponse;
import java.text.ParseException;
import java.util.concurrent.Future;
import javax.sip.*;
import org.mockito.*;
import org.testng.Assert;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

public class ProxyEventManagerTest {
  @Mock private StripedExecutorService executor;

  @Mock ProxyService proxyService;

  @InjectMocks ProxyEventManager proxyEventManager;

  @BeforeClass
  void init() {
    MockitoAnnotations.initMocks(this);
  }

  @BeforeMethod
  public void setup() {
    reset(executor);
    reset(proxyService);
  }

  private ListeningPoint getTestListeningPoint() {
    return new ListeningPoint() {
      @Override
      public int getPort() {
        return 5060;
      }

      @Override
      public String getTransport() {
        return "udp";
      }

      @Override
      public String getIPAddress() {
        return "1.1.1.1";
      }

      @Override
      public void setSentBy(String s) throws ParseException {}

      @Override
      public String getSentBy() {
        return null;
      }
    };
  }

  @Test(description = "test request handler for proxy")
  public void testRequestEventHandler() throws Exception {
    Future f = mock(Future.class);
    when(executor.submit(any(ProxyEventHandler.class))).thenReturn(f);
    SipProvider sipProvider = mock(SipProvider.class);
    ListeningPoint[] lps = new ListeningPoint[1];
    lps[0] = getTestListeningPoint();

    when(sipProvider.getListeningPoints()).thenReturn(lps);
    ServerTransaction serverTransaction = mock(ServerTransaction.class);
    Dialog dialog = mock(Dialog.class);

    SIPRequest request =
        SIPRequestBuilder.createRequest(
            new SIPRequestBuilder().getRequestAsString(SIPRequestBuilder.RequestMethod.INVITE));
    RequestEvent requestEvent = new RequestEvent(sipProvider, serverTransaction, dialog, request);

    ArgumentCaptor<SipRequestHandler> argumentCaptor =
        ArgumentCaptor.forClass(SipRequestHandler.class);
    proxyEventManager.request(requestEvent);

    verify(executor).submit(argumentCaptor.capture());

    SipRequestHandler sipRequestHandler = argumentCaptor.getValue();

    Assert.assertNotNull(sipRequestHandler);
    Assert.assertEquals(sipRequestHandler.getCallId(), request.getCallId().getCallId());
  }

  @Test(description = "test response handler for proxy")
  public void testResponseEventHandler() throws Exception {
    Future f = mock(Future.class);
    when(executor.submit(any(ProxyEventHandler.class))).thenReturn(f);
    SipProvider sipProvider = mock(SipProvider.class);
    ListeningPoint[] lps = new ListeningPoint[1];

    lps[0] = getTestListeningPoint();

    when(sipProvider.getListeningPoints()).thenReturn(lps);
    ClientTransaction clientTransaction = mock(ClientTransaction.class);
    Dialog dialog = mock(Dialog.class);
    SIPResponse response = ResponseHelper.getSipResponse();
    assert response != null;

    ResponseEvent responseEvent =
        new ResponseEvent(sipProvider, clientTransaction, dialog, response);
    ArgumentCaptor<SipResponseHandler> argumentCaptor =
        ArgumentCaptor.forClass(SipResponseHandler.class);
    proxyEventManager.response(responseEvent);

    verify(executor).submit(argumentCaptor.capture());

    SipResponseHandler sipResponseHandler = argumentCaptor.getValue();

    Assert.assertNotNull(sipResponseHandler);
    Assert.assertEquals(sipResponseHandler.getCallId(), response.getCallId().getCallId());
  }

  @Test(description = "test timeout handler for proxy")
  public void testTimeoutEventHandler() {
    Future f = mock(Future.class);
    when(executor.submit(any(ProxyEventHandler.class))).thenReturn(f);
    SipProvider sipProvider = mock(SipProvider.class);
    ListeningPoint[] lps = new ListeningPoint[1];
    lps[0] = getTestListeningPoint();

    when(sipProvider.getListeningPoints()).thenReturn(lps);
    ServerTransaction serverTransaction = mock(ServerTransaction.class);
    Timeout timeout = Timeout.TRANSACTION;

    TimeoutEvent timeoutEvent = new TimeoutEvent(sipProvider, serverTransaction, timeout);

    ArgumentCaptor<SipTimeOutHandler> argumentCaptor =
        ArgumentCaptor.forClass(SipTimeOutHandler.class);
    proxyEventManager.timeOut(timeoutEvent);

    verify(executor).submit(argumentCaptor.capture());

    SipTimeOutHandler sipTimeOutHandler = argumentCaptor.getValue();

    Assert.assertNotNull(sipTimeOutHandler);
    Assert.assertNull(sipTimeOutHandler.getCallId());
  }
}
