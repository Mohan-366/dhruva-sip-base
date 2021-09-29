package com.cisco.dhruva.application.filters;

import static org.mockito.Mockito.*;

import com.cisco.dhruva.application.SIPConfig;
import com.cisco.dhruva.application.calltype.*;
import com.cisco.dhruva.application.calltype.CallType;
import com.cisco.dhruva.application.constants.SipParamConstants;
import com.cisco.dhruva.application.exceptions.FilterTreeException;
import com.cisco.dsb.proxy.messaging.ProxySIPRequest;
import gov.nist.javax.sip.address.SipUri;
import gov.nist.javax.sip.header.RequestLine;
import gov.nist.javax.sip.message.SIPRequest;
import java.text.ParseException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Optional;
import javax.sip.message.Response;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.MockitoAnnotations;
import org.testng.Assert;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.BeforeTest;
import org.testng.annotations.Test;

public class FilterTest {
  Filter filter;
  @Mock ProxySIPRequest proxySIPRequest;
  SIPRequest sipRequest;
  HashMap<Object, Object> cache = new HashMap<>();

  @BeforeTest
  public void init() throws ParseException, FilterTreeException {
    MockitoAnnotations.initMocks(this);
    sipRequest = new SIPRequest();
    SipUri sipUri = new SipUri();
    sipUri.setHost("test.webex.com");
    sipUri.setTransportParam("udp");
    sipUri.setPort(5060);
    RequestLine requestLine = new RequestLine();
    requestLine.setMethod("INVITE");
    requestLine.setUri(sipUri);
    requestLine.setSipVersion("SIP/2.0");
    sipRequest.setRequestLine(requestLine);
    filter = new Filter(new CallTypeFactory());
    List<com.cisco.dhruva.application.calltype.CallType.CallTypes> interestedCallTypes =
        new ArrayList<>();
    interestedCallTypes.add(com.cisco.dhruva.application.calltype.CallType.CallTypes.DIAL_IN_PSTN);
    interestedCallTypes.add(com.cisco.dhruva.application.calltype.CallType.CallTypes.DIAL_IN_B2B);
    interestedCallTypes.add(com.cisco.dhruva.application.calltype.CallType.CallTypes.DIAL_OUT_WXC);
    interestedCallTypes.add(com.cisco.dhruva.application.calltype.CallType.CallTypes.DIAL_OUT_B2B);
    filter.register(interestedCallTypes);

    when(proxySIPRequest.getCache()).thenReturn(cache);
  }

  @BeforeMethod
  public void setup() {
    reset(proxySIPRequest);
    cache.clear();
  }

  @Test
  public void testDialInPSTN() throws ParseException {
    // below is a check which makes sure we don't consider calltype tag for dial-in call
    ((SipUri) sipRequest.getRequestURI())
        .setParameter(SipParamConstants.CALLTYPE, SipParamConstants.DIAL_IN_TAG);
    when(proxySIPRequest.getNetwork()).thenReturn(SIPConfig.NETWORK_PSTN);
    when(proxySIPRequest.getRequest()).thenReturn(sipRequest);
    when(proxySIPRequest.getCallId()).thenReturn("DialInPSTN");
    Optional<com.cisco.dhruva.application.calltype.CallType> callType =
        filter.filter(proxySIPRequest);
    Assert.assertEquals(callType.get().getClass(), DialInPSTN.class);
  }

  @Test
  public void testDialInB2B() throws ParseException {
    ((SipUri) sipRequest.getRequestURI())
        .setParameter(SipParamConstants.CALLTYPE, SipParamConstants.DIAL_IN_TAG);
    when(proxySIPRequest.getNetwork()).thenReturn(SIPConfig.NETWORK_B2B);
    when(proxySIPRequest.getRequest()).thenReturn(sipRequest);
    when(proxySIPRequest.getCallId()).thenReturn("DialInB2B");
    Optional<com.cisco.dhruva.application.calltype.CallType> callType =
        filter.filter(proxySIPRequest);
    Assert.assertEquals(callType.get().getClass(), DialInB2B.class);
  }

  @Test
  public void testDialOutWxC() throws ParseException {
    // below is a check which makes sure we don't consider calltype tag for dial-in call
    ((SipUri) sipRequest.getRequestURI())
        .setParameter(SipParamConstants.CALLTYPE, SipParamConstants.DIAL_OUT_TAG);
    when(proxySIPRequest.getNetwork()).thenReturn(SIPConfig.NETWORK_CALLING_CORE);
    when(proxySIPRequest.getRequest()).thenReturn(sipRequest);
    when(proxySIPRequest.getCallId()).thenReturn("DialOutWxC");
    Optional<com.cisco.dhruva.application.calltype.CallType> callType =
        filter.filter(proxySIPRequest);
    Assert.assertEquals(callType.get().getClass(), DialOutWxC.class);
  }

  @Test
  public void testDialOutB2B() throws ParseException {
    ((SipUri) sipRequest.getRequestURI())
        .setParameter(SipParamConstants.CALLTYPE, SipParamConstants.DIAL_OUT_TAG);
    when(proxySIPRequest.getNetwork()).thenReturn(SIPConfig.NETWORK_B2B);
    when(proxySIPRequest.getRequest()).thenReturn(sipRequest);
    when(proxySIPRequest.getCallId()).thenReturn("DialOutB2B");
    Optional<com.cisco.dhruva.application.calltype.CallType> callType =
        filter.filter(proxySIPRequest);
    Assert.assertEquals(callType.get().getClass(), DialOutB2B.class);
  }

  @Test
  public void testInvalidCallType() throws ParseException {
    ((SipUri) sipRequest.getRequestURI()).setParameter(SipParamConstants.CALLTYPE, "Invalid");
    when(proxySIPRequest.getNetwork()).thenReturn(SIPConfig.NETWORK_B2B);
    when(proxySIPRequest.getRequest()).thenReturn(sipRequest);
    when(proxySIPRequest.getCallId()).thenReturn("InvalidCallType");
    Optional<CallType> callType = filter.filter(proxySIPRequest);

    Assert.assertEquals(callType, Optional.empty());
    verify(proxySIPRequest, Mockito.times(1)).reject(eq(Response.NOT_FOUND));
  }
}
