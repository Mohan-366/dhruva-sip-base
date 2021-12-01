package com.cisco.dhruva.application.calltype;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;
import static org.mockito.Mockito.times;

import com.cisco.dhruva.application.SIPConfig;
import com.cisco.dhruva.application.constants.SipParamConstants;
import com.cisco.dhruva.normalisation.RuleListenerImpl;
import com.cisco.dhruva.normalisation.rules.RemoveOpnDpnCallTypeRule;
import com.cisco.dsb.common.exception.DhruvaException;
import com.cisco.dsb.common.sip.bean.SIPListenPoint;
import com.cisco.dsb.common.sip.stack.dto.DhruvaNetwork;
import com.cisco.dsb.proxy.messaging.ProxySIPRequest;
import com.cisco.dsb.proxy.messaging.ProxySIPResponse;
import com.cisco.dsb.proxy.sip.ProxyCookieImpl;
import com.cisco.dsb.trunk.dto.Destination;
import gov.nist.javax.sip.address.SipUri;
import gov.nist.javax.sip.header.To;
import gov.nist.javax.sip.message.SIPRequest;
import java.text.ParseException;
import javax.sip.message.Request;
import javax.sip.message.Response;
import org.junit.Assert;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.BeforeTest;
import org.testng.annotations.Test;
import reactor.core.publisher.Mono;

public class DialOutB2BTest {
  @Mock ProxySIPRequest proxySIPRequest;
  @Mock SIPRequest sipRequest;
  @Mock SIPListenPoint sipListenPoint;
  @Mock ProxySIPResponse proxySIPResponse;
  @Mock ProxyCookieImpl cookie;
  Object normRule;

  @BeforeTest
  public void init() throws DhruvaException {
    MockitoAnnotations.initMocks(this);
    when(sipListenPoint.getName()).thenReturn(SIPConfig.NETWORK_PSTN);
    DhruvaNetwork.createNetwork(SIPConfig.NETWORK_PSTN, sipListenPoint);
    normRule = new RemoveOpnDpnCallTypeRule();
  }

  @BeforeMethod
  public void setup() {
    reset(proxySIPRequest, sipRequest, proxySIPResponse, cookie);
    when(proxySIPRequest.getRequest()).thenReturn(sipRequest);
    when(proxySIPRequest.getCookie()).thenReturn(cookie);
    when(proxySIPResponse.getCookie()).thenReturn(cookie);
  }

  @Test(
      description =
          "request has OPN,DPN,calltype,dtg(in RURI and To:), verify that all these params are removed"
              + "and also proxycookie contains calltype"
              + "'RemoveOpnDpnCallTypeRule' condition and action succeeds "
              + "remove dtg from rURI and To Header")
  public void testProcessRequest() throws ParseException {
    DialOutB2B dialOutB2B = new DialOutB2B(new RuleListenerImpl(), normRule);
    when(sipRequest.getMethod()).thenReturn(Request.INVITE);
    SipUri sipUri = new SipUri();
    sipUri.setParameter(SipParamConstants.X_CISCO_DPN, SipParamConstants.DPN_OUT);
    sipUri.setParameter(SipParamConstants.X_CISCO_OPN, SipParamConstants.OPN_OUT);
    sipUri.setParameter(SipParamConstants.CALLTYPE, SipParamConstants.DIAL_OUT_TAG);
    sipUri.setParameter(SipParamConstants.DTG, "CcpFusionUS");
    To toHeader = new To();
    toHeader.setParameter(SipParamConstants.DTG, "CcpFusionUS");
    when(sipRequest.getRequestURI()).thenReturn(sipUri);
    when(sipRequest.getToHeader()).thenReturn(toHeader);
    doAnswer(
            invocationOnMock -> {
              Destination destination = invocationOnMock.getArgument(0);
              when(proxySIPRequest.getDestination()).thenReturn(destination);
              return null;
            })
        .when(proxySIPRequest)
        .setDestination(any(Destination.class));

    // call
    dialOutB2B.processRequest().accept(Mono.just(proxySIPRequest));

    // verify
    Assert.assertFalse(
        sipUri.hasParameter(SipParamConstants.X_CISCO_DPN)
            || sipUri.hasParameter(SipParamConstants.X_CISCO_OPN)
            || sipUri.hasParameter(SipParamConstants.DTG)
            || sipUri.hasParameter(SipParamConstants.CALLTYPE));
    Assert.assertFalse(toHeader.hasParameter(SipParamConstants.DTG));
    Assert.assertEquals(SIPConfig.dtg.get("CcpFusionUS"), sipUri.getHost());
    Destination destination = proxySIPRequest.getDestination();
    Assert.assertEquals(destination.getDestinationType(), Destination.DestinationType.SERVER_GROUP);
    Assert.assertEquals(destination.getUri(), proxySIPRequest.getRequest().getRequestURI());
    Assert.assertEquals(SIPConfig.dtg.get("CcpFusionUS"), destination.getAddress());
    verify(proxySIPRequest, times(1)).proxy();
    verify(cookie, times(1)).setCalltype(dialOutB2B);
  }

  @Test(description = "request has OPN,DPN, no DTG, reject the call with 404")
  public void testProcessRequestNoDTG() throws ParseException {
    DialOutB2B dialOutB2B = new DialOutB2B(new RuleListenerImpl(), normRule);
    when(sipRequest.getMethod()).thenReturn(Request.INVITE);
    SipUri sipUri = new SipUri();
    sipUri.setParameter(SipParamConstants.X_CISCO_DPN, SipParamConstants.DPN_OUT);
    sipUri.setParameter(SipParamConstants.X_CISCO_OPN, SipParamConstants.OPN_OUT);
    when(sipRequest.getRequestURI()).thenReturn(sipUri);

    // call
    dialOutB2B.processRequest().accept(Mono.just(proxySIPRequest));

    // verifu
    verify(proxySIPRequest, times(1)).reject(Response.NOT_FOUND);
    verify(cookie, times(0)).setCalltype(dialOutB2B);
  }

  @Test(description = "request has OPN,DPN and DTG whose mapping is not present, reject with 404")
  public void testProcessRequestInvalidDTG() throws ParseException {
    DialOutB2B dialOutB2B = new DialOutB2B(new RuleListenerImpl(), normRule);
    when(sipRequest.getMethod()).thenReturn(Request.INVITE);
    SipUri sipUri = new SipUri();
    sipUri.setParameter(SipParamConstants.X_CISCO_DPN, SipParamConstants.DPN_OUT);
    sipUri.setParameter(SipParamConstants.X_CISCO_OPN, SipParamConstants.OPN_OUT);
    sipUri.setParameter(SipParamConstants.DTG, "Invalid");
    when(sipRequest.getRequestURI()).thenReturn(sipUri);

    // call
    dialOutB2B.processRequest().accept(Mono.just(proxySIPRequest));

    // verify
    verify(proxySIPRequest, times(1)).reject(Response.NOT_FOUND);
    verify(cookie, times(0)).setCalltype(dialOutB2B);
  }

  @Test(description = "Unable to create destination as network is not present,reject with 500")
  public void testRequestException() throws ParseException, DhruvaException {
    DialOutB2B dialOutB2B = new DialOutB2B(new RuleListenerImpl(), normRule);
    SipUri sipUri = new SipUri();
    sipUri.setParameter(SipParamConstants.X_CISCO_DPN, SipParamConstants.DPN_OUT);
    sipUri.setParameter(SipParamConstants.X_CISCO_OPN, SipParamConstants.OPN_OUT);
    sipUri.setParameter(SipParamConstants.TEST_CALL, null);
    DhruvaNetwork.removeNetwork(SIPConfig.NETWORK_PSTN);
    when(sipRequest.getRequestURI()).thenReturn(sipUri);
    when(sipRequest.getMethod()).thenReturn(Request.INVITE);

    // call
    dialOutB2B.processRequest().accept(Mono.just(proxySIPRequest));
    DhruvaNetwork.createNetwork(SIPConfig.NETWORK_PSTN, sipListenPoint);
    // verify
    verify(proxySIPRequest, times(1)).reject(Response.SERVER_INTERNAL_ERROR);
  }
}
