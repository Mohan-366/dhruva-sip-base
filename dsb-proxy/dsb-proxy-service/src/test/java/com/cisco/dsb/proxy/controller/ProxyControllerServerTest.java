package com.cisco.dsb.proxy.controller;

import static org.mockito.Mockito.*;

import com.cisco.dsb.common.exception.DhruvaException;
import com.cisco.dsb.common.executor.DhruvaExecutorService;
import com.cisco.dsb.common.service.SipServerLocatorService;
import com.cisco.dsb.common.sip.bean.SIPListenPoint;
import com.cisco.dsb.common.sip.stack.dto.DhruvaNetwork;
import com.cisco.dsb.proxy.ProxyConfigurationProperties;
import com.cisco.dsb.proxy.dto.ProxyAppConfig;
import com.cisco.dsb.proxy.messaging.ProxySIPRequest;
import com.cisco.dsb.proxy.messaging.ProxySIPResponse;
import com.cisco.dsb.proxy.sip.ProxyCookieImpl;
import com.cisco.dsb.proxy.sip.ProxyFactory;
import com.cisco.dsb.proxy.sip.ProxyTransaction;
import gov.nist.javax.sip.address.AddressImpl;
import gov.nist.javax.sip.address.SipUri;
import gov.nist.javax.sip.header.HeaderFactoryImpl;
import gov.nist.javax.sip.header.Via;
import gov.nist.javax.sip.message.SIPRequest;
import gov.nist.javax.sip.message.SIPResponse;
import java.text.ParseException;
import javax.sip.ServerTransaction;
import javax.sip.SipProvider;
import javax.sip.address.Address;
import javax.sip.header.FromHeader;
import javax.sip.header.HeaderFactory;
import javax.sip.header.ToHeader;
import javax.sip.message.Request;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.MockitoAnnotations;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.BeforeTest;
import org.testng.annotations.Test;

public class ProxyControllerServerTest {
  public ProxyController proxyController;
  @Mock ServerTransaction serverTransaction;
  @Mock SipProvider sipProvider;
  @Mock ProxyConfigurationProperties proxyConfigurationProperties;
  @Mock ProxyFactory proxyFactory;
  @Mock ControllerConfig controllerConfig;
  @Mock DhruvaExecutorService dhruvaExecutorService;
  @Mock ProxyTransaction proxyTransaction;
  @Mock SIPRequest sipRequest;
  @Mock ProxySIPRequest proxySIPRequest;
  @Mock ProxySIPResponse proxySIPResponse;
  @Mock SIPResponse sipResponse;
  @Mock ProxyAppConfig proxyAppConfig;
  @Mock SIPListenPoint sipListenPoint;
  @Mock Via via;
  @Mock SipServerLocatorService sipServerLocatorService;

  @BeforeTest
  public void init() {
    MockitoAnnotations.initMocks(this);
    proxyController =
        new ProxyController(
            serverTransaction,
            sipProvider,
            proxyAppConfig,
            proxyConfigurationProperties,
            proxyFactory,
            controllerConfig,
            dhruvaExecutorService,
            sipServerLocatorService);
    proxyController = spy(proxyController);
  }

  @BeforeMethod
  public void setup() {
    reset(proxyTransaction, proxyController, proxyAppConfig);
  }

  @Test
  public void testProxyResponseStateFull() throws DhruvaException, ParseException {
    // setup
    when(sipListenPoint.getHostIPAddress()).thenReturn("1.1.1.1");
    when(sipListenPoint.getName()).thenReturn("network");
    when(proxySIPRequest.getRequest()).thenReturn(sipRequest);
    when(sipRequest.getMethod()).thenReturn(Request.INVITE);
    when(proxySIPResponse.getResponse()).thenReturn(sipResponse);
    when(proxySIPResponse.getCookie()).thenReturn(new ProxyCookieImpl());
    when(proxySIPResponse.getResponse()).thenReturn(sipResponse);
    when(via.getHost()).thenReturn("6.6.6.6");
    when(sipResponse.getTopmostVia()).thenReturn(via);
    HeaderFactory headerFactory = new HeaderFactoryImpl();
    Address address = new AddressImpl();
    address.setURI(new SipUri());
    ToHeader to = headerFactory.createToHeader(address, "destination");
    FromHeader from = headerFactory.createFromHeader(address, "source");
    when(sipResponse.getTo()).thenReturn(to);
    when(sipResponse.getFrom()).thenReturn(from);
    DhruvaNetwork.createNetwork("network", sipListenPoint);
    when(proxySIPRequest.getNetwork()).thenReturn("network");
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
