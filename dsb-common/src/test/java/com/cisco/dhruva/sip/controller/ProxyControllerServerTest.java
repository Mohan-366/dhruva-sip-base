package com.cisco.dhruva.sip.controller;

import static org.mockito.Mockito.*;

import com.cisco.dhruva.sip.proxy.ProxyFactory;
import com.cisco.dhruva.sip.proxy.ProxyTransaction;
import com.cisco.dsb.common.executor.DhruvaExecutorService;
import com.cisco.dsb.common.messaging.ProxySIPRequest;
import com.cisco.dsb.common.messaging.ProxySIPResponse;
import com.cisco.dsb.config.sip.DhruvaSIPConfigProperties;
import gov.nist.javax.sip.message.SIPRequest;
import gov.nist.javax.sip.message.SIPResponse;
import javax.sip.ServerTransaction;
import javax.sip.SipProvider;
import javax.sip.message.Request;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.MockitoAnnotations;
import org.mockito.junit.MockitoJUnitRunner;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.BeforeTest;
import org.testng.annotations.Test;

@RunWith(MockitoJUnitRunner.class)
public class ProxyControllerServerTest {
  public ProxyController proxyController;
  @Mock ServerTransaction serverTransaction;
  @Mock SipProvider sipProvider;
  @Mock DhruvaSIPConfigProperties dhruvaSIPConfigProperties;
  @Mock ProxyFactory proxyFactory;
  @Mock ControllerConfig controllerConfig;
  @Mock DhruvaExecutorService dhruvaExecutorService;
  @Mock ProxyTransaction proxyTransaction;
  @Mock SIPRequest sipRequest;
  @Mock ProxySIPRequest proxySIPRequest;
  @Mock ProxySIPResponse proxySIPResponse;
  @Mock SIPResponse sipResponse;

  @BeforeTest
  public void init() {
    MockitoAnnotations.initMocks(this);
    proxyController =
        new ProxyController(
            serverTransaction,
            sipProvider,
            dhruvaSIPConfigProperties,
            proxyFactory,
            controllerConfig,
            dhruvaExecutorService);
  }

  @BeforeMethod
  public void setup() {
    reset(proxyTransaction);
  }

  @Test
  public void testProxyResponseStateFull() {
    // setup
    when(proxySIPRequest.getRequest()).thenReturn(sipRequest);
    when(sipRequest.getMethod()).thenReturn(Request.INVITE);
    when(proxySIPResponse.getResponse()).thenReturn(sipResponse);
    proxyController.setOurRequest(proxySIPRequest);
    proxyController.setProxyTransaction(proxyTransaction);
    // call
    proxyController.proxyResponse(proxySIPResponse);
    // verify
    verify(proxyTransaction, Mockito.times(1)).respond(sipResponse);
  }

  @Test
  public void testProxyResponseStateless() {
    when(proxySIPRequest.getRequest()).thenReturn(sipRequest);
    when(sipRequest.getMethod()).thenReturn(Request.ACK);
    when(proxySIPResponse.getResponse()).thenReturn(sipResponse);
    proxyController.setOurRequest(proxySIPRequest);
    proxyController.setProxyTransaction(proxyTransaction);
    // call
    proxyController.proxyResponse(proxySIPResponse);
    // verify
    verify(proxyTransaction, Mockito.times(0)).respond(sipResponse);
  }
}
