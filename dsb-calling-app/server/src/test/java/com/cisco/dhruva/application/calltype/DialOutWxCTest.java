package com.cisco.dhruva.application.calltype;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;
import static org.testng.Assert.*;

import com.cisco.dhruva.application.SIPConfig;
import com.cisco.dhruva.application.constants.SipParamConstants;
import com.cisco.dhruva.normalisation.RuleListenerImpl;
import com.cisco.dhruva.normalisation.rules.AddOpnDpnRule;
import com.cisco.dsb.common.exception.DhruvaException;
import com.cisco.dsb.common.sip.bean.SIPListenPoint;
import com.cisco.dsb.common.sip.stack.dto.DhruvaNetwork;
import com.cisco.dsb.proxy.messaging.ProxySIPRequest;
import com.cisco.dsb.trunk.dto.Destination;
import gov.nist.javax.sip.address.SipUri;
import gov.nist.javax.sip.message.SIPRequest;
import javax.sip.message.Request;
import org.testng.annotations.AfterTest;
import org.testng.annotations.BeforeTest;
import org.testng.annotations.Test;
import reactor.core.publisher.Mono;

public class DialOutWxCTest {

  @BeforeTest
  public void init() throws DhruvaException {
    SIPListenPoint sipListenPoint = mock(SIPListenPoint.class);
    when(sipListenPoint.getName()).thenReturn(SIPConfig.NETWORK_B2B);
    DhruvaNetwork.createNetwork(SIPConfig.NETWORK_B2B, sipListenPoint);
  }

  @AfterTest
  public void clean() {
    DhruvaNetwork.removeNetwork(SIPConfig.NETWORK_B2B);
  }

  @Test(description = "Add opn, dpn and calltype=DialOut to the reqUri during normalisation")
  public void testAddCallType() {
    SIPRequest sipRequest = mock(SIPRequest.class);
    ProxySIPRequest proxySIPRequest = mock(ProxySIPRequest.class);
    DialOutWxC dialOutWxC = new DialOutWxC(new RuleListenerImpl(), new AddOpnDpnRule());
    SipUri sipUri = new SipUri();
    when(sipRequest.getRequestURI()).thenReturn(sipUri);
    when(sipRequest.getMethod()).thenReturn(Request.INVITE);
    when(proxySIPRequest.getRequest()).thenReturn(sipRequest);
    doAnswer(
            invocationOnMock -> {
              Destination destination = invocationOnMock.getArgument(0);
              when(proxySIPRequest.getDestination()).thenReturn(destination);
              return null;
            })
        .when(proxySIPRequest)
        .setDestination(any(Destination.class));

    dialOutWxC.processRequest().accept(Mono.just(proxySIPRequest));

    assertEquals(
        sipUri.getParameter(SipParamConstants.X_CISCO_OPN), SipParamConstants.X_CISCO_OPN_VALUE);
    assertEquals(
        sipUri.getParameter(SipParamConstants.X_CISCO_DPN), SipParamConstants.X_CISCO_DPN_VALUE);
    assertEquals(sipUri.getParameter(SipParamConstants.CALLTYPE), SipParamConstants.DIAL_OUT_TAG);
  }
}
