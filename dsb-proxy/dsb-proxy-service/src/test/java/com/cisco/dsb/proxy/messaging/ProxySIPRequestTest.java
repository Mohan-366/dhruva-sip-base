package com.cisco.dsb.proxy.messaging;

import static org.mockito.Mockito.*;

import com.cisco.dsb.common.context.ExecutionContext;
import com.cisco.dsb.common.metric.SipMetricsContext;
import com.cisco.dsb.common.service.MetricService;
import com.cisco.dsb.common.sip.util.EndPoint;
import com.cisco.dsb.proxy.sip.ProxyInterface;
import gov.nist.javax.sip.message.SIPRequest;
import java.io.IOException;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import javax.servlet.ServletException;
import javax.sip.ServerTransaction;
import javax.sip.SipProvider;
import javax.sip.header.CSeqHeader;
import javax.sip.header.CallIdHeader;
import org.testng.Assert;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

public class ProxySIPRequestTest {

  ExecutionContext executionContext;
  SipProvider provider;
  SIPRequest request;
  ServerTransaction serverTransaction;
  CallIdHeader callIdHeader;
  CSeqHeader cSeqHeader;

  @BeforeClass
  public void init() {
    executionContext = mock(ExecutionContext.class);
    provider = mock(SipProvider.class);
    serverTransaction = mock(ServerTransaction.class);
    request = mock(SIPRequest.class);
    callIdHeader = mock(CallIdHeader.class);
    cSeqHeader = mock(CSeqHeader.class);
  }

  @BeforeMethod
  public void setUp() {
    when(request.getHeader(CallIdHeader.NAME)).thenReturn(callIdHeader);
    when(callIdHeader.getCallId()).thenReturn("webexCallId");
    when(request.getHeader(CSeqHeader.NAME)).thenReturn(cSeqHeader);
    when(cSeqHeader.getSeqNumber()).thenReturn(111111L);
  }

  @AfterMethod
  public void tearDown() {
    reset(executionContext);
    reset(request);
    reset(serverTransaction);
    reset(provider);
  }

  @Test(description = "test proxy function of proxySipRequest")
  public void testProxy1() throws ExecutionException, InterruptedException {
    ProxyInterface proxyInterface = mock(ProxyInterface.class);
    EndPoint endPoint = mock(EndPoint.class);
    ProxySIPRequest proxySIPRequest =
        new ProxySIPRequest(executionContext, provider, request, serverTransaction);
    proxySIPRequest.setProxyInterface(proxyInterface);
    ProxySIPResponse proxySIPResponse = mock(ProxySIPResponse.class);
    CompletableFuture<ProxySIPResponse> f1 = new CompletableFuture<ProxySIPResponse>();
    when(proxyInterface.proxyRequest(proxySIPRequest, endPoint)).thenReturn(f1);
    CompletableFuture<ProxySIPResponse> responseCompletableFuture = proxySIPRequest.proxy(endPoint);
    f1.complete(proxySIPResponse);
    verify(proxyInterface).proxyRequest(proxySIPRequest, endPoint);
    Assert.assertEquals(responseCompletableFuture.get(), proxySIPResponse);
  }

  @Test(
      description = "proxyInterface not set, expect runtime exception",
      expectedExceptions = RuntimeException.class)
  public void testProxy2() {

    EndPoint endPoint = mock(EndPoint.class);
    ProxySIPRequest proxySIPRequest =
        new ProxySIPRequest(executionContext, provider, request, serverTransaction);

    ProxySIPResponse proxySIPResponse = mock(ProxySIPResponse.class);
    CompletableFuture<ProxySIPResponse> f1 = new CompletableFuture<ProxySIPResponse>();

    CompletableFuture<ProxySIPResponse> responseCompletableFuture = proxySIPRequest.proxy(endPoint);
    f1.complete(proxySIPResponse);
  }

  @Test(description = "test reject api of proxySipRequest")
  public void testReject1() {
    ProxyInterface proxyInterface = mock(ProxyInterface.class);

    ProxySIPRequest proxySIPRequest =
        new ProxySIPRequest(executionContext, provider, request, serverTransaction);
    proxySIPRequest.setProxyInterface(proxyInterface);

    doNothing().when(proxyInterface).respond(200, proxySIPRequest);
    proxySIPRequest.reject(200);
    verify(proxyInterface).respond(200, proxySIPRequest);
  }

  @Test(
      description = "proxyInterface not set, expect runtime exception",
      expectedExceptions = RuntimeException.class)
  public void testReject2() {
    ProxySIPRequest proxySIPRequest =
        new ProxySIPRequest(executionContext, provider, request, serverTransaction);
    proxySIPRequest.reject(404);
  }

  @Test(description = "test second constructor for ProxySipRequest")
  public void testConstructor() {
    ProxyInterface proxyInterface = mock(ProxyInterface.class);
    ProxySIPRequest proxySIPRequest1 =
        new ProxySIPRequest(executionContext, provider, request, serverTransaction);
    proxySIPRequest1.setProxyInterface(proxyInterface);
    proxySIPRequest1 = spy(proxySIPRequest1);

    when(proxySIPRequest1.getRequest()).thenReturn(request);
    ProxySIPRequest proxySIPRequest2 = new ProxySIPRequest(proxySIPRequest1);
    Assert.assertNotNull(proxySIPRequest2.getProxyInterface());
  }

  @Test(description = "test setters and getters")
  public void testProxySipRequest() throws ServletException, IOException {
    ProxyInterface proxyInterface = mock(ProxyInterface.class);
    ProxySIPRequest proxySIPRequest =
        new ProxySIPRequest(executionContext, provider, request, serverTransaction);
    proxySIPRequest.setProxyInterface(proxyInterface);

    Assert.assertTrue(proxySIPRequest.isRequest());
    Assert.assertFalse(proxySIPRequest.isSipConference());
    Assert.assertFalse(proxySIPRequest.applyRateLimitFilter());
    Assert.assertFalse(proxySIPRequest.validate());
    Assert.assertFalse(proxySIPRequest.isMidCall());

    proxySIPRequest.setMidCall(true);
    Assert.assertTrue(proxySIPRequest.isMidCall());

    Assert.assertFalse(proxySIPRequest.isResponse());
    Assert.assertNotNull(proxySIPRequest.getCookie());

    // These apis are not doing anything today, just for numbers
    proxySIPRequest.sendFailureResponse();
    proxySIPRequest.sendSuccessResponse();
  }

  @Test(
      description =
          "test handling of proxy events to manage latency metrics.we can add other metrics in future")
  public void testProxyEvent() {
    MetricService metricService = mock(MetricService.class);
    doNothing().when(metricService).handleMetricsEvent(any(SipMetricsContext.class));

    ProxyInterface proxyInterface = mock(ProxyInterface.class);
    ProxySIPRequest proxySIPRequest =
        new ProxySIPRequest(executionContext, provider, request, serverTransaction);
    proxySIPRequest.setProxyInterface(proxyInterface);

    proxySIPRequest.handleProxyEvent(
        metricService, SipMetricsContext.State.proxyNewRequestReceived);
    verify(metricService).handleMetricsEvent(any(SipMetricsContext.class));
  }
}
