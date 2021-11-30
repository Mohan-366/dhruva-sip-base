package com.cisco.dhruva.application.calltype;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

import com.cisco.dhruva.application.SIPConfig;
import com.cisco.dhruva.application.constants.SipParamConstants;
import com.cisco.dhruva.normalisation.RuleListenerImpl;
import com.cisco.dhruva.normalisation.rules.AddOpnDpnRule;
import com.cisco.dsb.common.exception.DhruvaException;
import com.cisco.dsb.common.sip.bean.SIPListenPoint;
import com.cisco.dsb.common.sip.stack.dto.DhruvaNetwork;
import com.cisco.dsb.proxy.messaging.ProxySIPRequest;
import com.cisco.dsb.proxy.messaging.ProxySIPResponse;
import com.cisco.dsb.proxy.sip.ProxyCookieImpl;
import com.cisco.dsb.trunk.dto.Destination;
import gov.nist.javax.sip.address.SipUri;
import gov.nist.javax.sip.message.SIPRequest;
import java.text.ParseException;
import javax.sip.message.Request;
import javax.sip.message.Response;
import org.junit.Assert;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.MockitoAnnotations;
import org.testng.annotations.*;
import reactor.core.publisher.Mono;

public class DialInPSTNTest {
  @Mock ProxySIPRequest proxySIPRequest;
  @Mock SIPRequest sipRequest;
  @Mock SIPListenPoint sipListenPoint;
  @Mock ProxySIPResponse proxySIPResponse;
  Object normRule;

  @BeforeTest
  public void init() throws DhruvaException {
    MockitoAnnotations.initMocks(this);
    when(sipListenPoint.getName()).thenReturn(SIPConfig.NETWORK_B2B);
    DhruvaNetwork.createNetwork(SIPConfig.NETWORK_B2B, sipListenPoint);
    normRule = new AddOpnDpnRule(SipParamConstants.DPN_IN, SipParamConstants.OPN_IN);
  }

  @AfterTest
  public void clean() {
    DhruvaNetwork.removeNetwork(SIPConfig.NETWORK_B2B);
  }

  @BeforeMethod
  public void setup() {
    reset(proxySIPRequest, proxySIPResponse, sipRequest);
    when(proxySIPRequest.getRequest()).thenReturn(sipRequest);
    when(proxySIPRequest.getCookie()).thenReturn(mock(ProxyCookieImpl.class));
  }

  @Test(
      description =
          "INVITE with proper OPN,DPN and callType params, but without test call tag. "
              + "'AddOpnDpnRule' rule condition and action succeeds")
  public void testRequestPipeline() {
    DialInPSTN dialInPSTN = new DialInPSTN(new RuleListenerImpl(), normRule);
    SipUri sipUri = new SipUri();
    when(sipRequest.getRequestURI()).thenReturn(sipUri);
    when(sipRequest.getMethod()).thenReturn(Request.INVITE);
    doAnswer(
            invocationOnMock -> {
              Destination destination = invocationOnMock.getArgument(0);
              when(proxySIPRequest.getDestination()).thenReturn(destination);
              return null;
            })
        .when(proxySIPRequest)
        .setDestination(any(Destination.class));

    dialInPSTN.processRequest().accept(Mono.just(proxySIPRequest));

    SipUri rUri = (SipUri) proxySIPRequest.getRequest().getRequestURI();
    Assert.assertEquals(rUri.getParameter(SipParamConstants.X_CISCO_DPN), SipParamConstants.DPN_IN);
    Assert.assertEquals(rUri.getParameter(SipParamConstants.X_CISCO_OPN), SipParamConstants.OPN_IN);
    Assert.assertEquals(
        rUri.getParameter(SipParamConstants.CALLTYPE), SipParamConstants.DIAL_IN_TAG);

    Destination destination = proxySIPRequest.getDestination();
    Assert.assertEquals(destination.getDestinationType(), Destination.DestinationType.A);
    Assert.assertEquals(destination.getUri(), proxySIPRequest.getRequest().getRequestURI());
    Assert.assertEquals(destination.getAddress(), String.join(":", SIPConfig.B2B_A_RECORD));
  }

  @Test(description = "INVITE with proper OPN,DPN and callType params and with test call tag")
  public void testRequestPipelineTestCall() throws ParseException {
    DialInPSTN dialInPSTN = new DialInPSTN(new RuleListenerImpl(), normRule);
    SipUri sipUri = new SipUri();
    when(sipRequest.getRequestURI()).thenReturn(sipUri);
    when(sipRequest.getMethod()).thenReturn(Request.INVITE);
    doAnswer(
            invocationOnMock -> {
              Destination destination = invocationOnMock.getArgument(0);
              when(proxySIPRequest.getDestination()).thenReturn(destination);
              return null;
            })
        .when(proxySIPRequest)
        .setDestination(any(Destination.class));
    sipUri.setParameter(SipParamConstants.TEST_CALL, null);

    dialInPSTN.processRequest().accept(Mono.just(proxySIPRequest));

    SipUri rUri = (SipUri) proxySIPRequest.getRequest().getRequestURI();
    Assert.assertEquals(rUri.getParameter(SipParamConstants.X_CISCO_DPN), SipParamConstants.DPN_IN);
    Assert.assertEquals(rUri.getParameter(SipParamConstants.X_CISCO_OPN), SipParamConstants.OPN_IN);
    Assert.assertEquals(
        rUri.getParameter(SipParamConstants.CALLTYPE), SipParamConstants.DIAL_IN_TAG);

    Destination destination = proxySIPRequest.getDestination();
    Assert.assertEquals(destination.getDestinationType(), Destination.DestinationType.A);
    Assert.assertEquals(destination.getUri(), proxySIPRequest.getRequest().getRequestURI());
    Assert.assertEquals(
        destination.getAddress(),
        String.join(":", SIPConfig.B2B_A_RECORD) + ":" + SipParamConstants.INJECTED_DNS_UUID);
  }

  @Test(
      description =
          "Do not add opn, dpn for a non-INVITE request." + "'AddOpnDpnRule' rule condition fails")
  public void testRequestPipelineNonInvite() throws ParseException {
    DialInPSTN dialInPSTN = new DialInPSTN(new RuleListenerImpl(), normRule);
    SipUri sipUri = new SipUri();
    when(sipRequest.getRequestURI()).thenReturn(sipUri);
    when(sipRequest.getMethod()).thenReturn(Request.ACK);
    doAnswer(
            invocationOnMock -> {
              Destination destination = invocationOnMock.getArgument(0);
              when(proxySIPRequest.getDestination()).thenReturn(destination);
              return null;
            })
        .when(proxySIPRequest)
        .setDestination(any(Destination.class));
    sipUri.setParameter(SipParamConstants.TEST_CALL, null);

    dialInPSTN.processRequest().accept(Mono.just(proxySIPRequest));

    SipUri rUri = (SipUri) proxySIPRequest.getRequest().getRequestURI();
    Assert.assertFalse(rUri.hasParameter(SipParamConstants.X_CISCO_DPN));
    Assert.assertFalse(rUri.hasParameter(SipParamConstants.X_CISCO_OPN));
    Assert.assertTrue(rUri.hasParameter(SipParamConstants.CALLTYPE));

    Destination destination = proxySIPRequest.getDestination();
    Assert.assertEquals(destination.getDestinationType(), Destination.DestinationType.A);
    Assert.assertEquals(destination.getUri(), proxySIPRequest.getRequest().getRequestURI());
    Assert.assertEquals(
        destination.getAddress(),
        String.join(":", SIPConfig.B2B_A_RECORD) + ":" + SipParamConstants.INJECTED_DNS_UUID);
  }

  @Test(description = "Exception while adding OPN DPN" + "'AddOpnDpnRule' rule action fails")
  public void testRequestPipelineExceptionOPN() throws ParseException {
    DialInPSTN dialInPSTN = new DialInPSTN(new RuleListenerImpl(), normRule);
    SipUri sipUri = Mockito.mock(SipUri.class);
    doThrow(new ParseException("Unable to add OPN", 10)).when(sipUri).setParameter(any(), any());
    when(sipRequest.getRequestURI()).thenReturn(sipUri);
    when(sipRequest.getMethod()).thenReturn(Request.INVITE);

    dialInPSTN.processRequest().accept(Mono.just(proxySIPRequest));

    verify(proxySIPRequest, Mockito.times(1)).reject(Response.SERVER_INTERNAL_ERROR);
  }

  @Test(description = "Exception while creating Destination")
  public void testRequestPipelineExceptionNoNetwork() throws DhruvaException {
    DialInPSTN dialInPSTN = new DialInPSTN(new RuleListenerImpl(), normRule);
    SipUri sipUri = Mockito.mock(SipUri.class);
    when(sipRequest.getRequestURI()).thenReturn(sipUri);
    when(sipRequest.getMethod()).thenReturn(Request.INVITE);
    DhruvaNetwork.removeNetwork(SIPConfig.NETWORK_B2B);

    dialInPSTN.processRequest().accept(Mono.just(proxySIPRequest));
    verify(proxySIPRequest, Mockito.times(1)).reject(Response.SERVER_INTERNAL_ERROR);

    DhruvaNetwork.createNetwork(SIPConfig.NETWORK_B2B, sipListenPoint);
  }

  @Test(description = "full call flow")
  public void testProxyRequest() {
    DialInPSTN dialInPSTN = new DialInPSTN(new RuleListenerImpl(), normRule);
    SipUri sipUri = new SipUri();
    when(sipRequest.getRequestURI()).thenReturn(sipUri);
    when(sipRequest.getMethod()).thenReturn(Request.INVITE);
    doAnswer(
            invocationOnMock -> {
              Destination destination = invocationOnMock.getArgument(0);
              when(proxySIPRequest.getDestination()).thenReturn(destination);
              return null;
            })
        .when(proxySIPRequest)
        .setDestination(any(Destination.class));

    dialInPSTN.processRequest().accept(Mono.just(proxySIPRequest));
    verify(proxySIPRequest, Mockito.times(1)).proxy();
  }

  @Test(description = "Uncaught exception in request pipeline")
  public void testProxyRequestError() {
    DialInPSTN dialInPSTN = new DialInPSTN(new RuleListenerImpl(), normRule);

    dialInPSTN.processRequest().accept(Mono.just(proxySIPRequest));
    verify(proxySIPRequest, Mockito.times(1)).reject(Response.SERVER_INTERNAL_ERROR);
  }

  @Test
  public void testResponse() {
    DialInPSTN dialInPSTN = new DialInPSTN(new RuleListenerImpl(), normRule);

    dialInPSTN.processResponse().accept(Mono.just(proxySIPResponse));
    verify(proxySIPResponse, Mockito.times(1)).proxy();
  }
}
