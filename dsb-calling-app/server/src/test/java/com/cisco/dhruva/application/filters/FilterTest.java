package com.cisco.dhruva.application.filters;

import static org.mockito.Mockito.*;

import com.cisco.dhruva.application.CallingAppConfigurationProperty;
import com.cisco.dhruva.application.calltype.*;
import com.cisco.dhruva.application.calltype.CallTypeEnum;
import com.cisco.dhruva.application.exceptions.FilterTreeException;
import com.cisco.dhruva.application.exceptions.InvalidCallTypeException;
import com.cisco.dsb.common.util.SpringApplicationContext;
import com.cisco.dsb.proxy.messaging.ProxySIPRequest;
import com.cisco.dsb.trunk.util.SipParamConstants;
import gov.nist.javax.sip.address.SipUri;
import gov.nist.javax.sip.header.RequestLine;
import gov.nist.javax.sip.message.SIPRequest;
import java.text.ParseException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.springframework.context.ApplicationContext;
import org.testng.Assert;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.BeforeTest;
import org.testng.annotations.Test;

public class FilterTest {
  Filter filter;
  @Mock ProxySIPRequest proxySIPRequest;
  @Mock CallingAppConfigurationProperty configurationProperty;
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

    when(configurationProperty.getNetworkPSTN()).thenReturn("net_sp");
    when(configurationProperty.getNetworkB2B()).thenReturn("net_b2b");
    when(configurationProperty.getNetworkCallingCore()).thenReturn("net_cc");

    NetworkPSTN networkPSTN = new NetworkPSTN();
    networkPSTN.setConfigurationProperty(configurationProperty);
    NetworkB2B networkB2B = new NetworkB2B();
    networkB2B.setConfigurationProperty(configurationProperty);
    NetworkWxC networkWxC = new NetworkWxC();
    networkWxC.setConfigurationProperty(configurationProperty);

    FilterFactory filterFactory = new FilterFactory();
    filterFactory.setNetworkPSTN(networkPSTN);
    filterFactory.setNetworkB2B(networkB2B);
    filterFactory.setNetworkWxC(networkWxC);

    SpringApplicationContext springApplicationContext = new SpringApplicationContext();
    ApplicationContext applicationContext = mock(ApplicationContext.class);
    springApplicationContext.setApplicationContext(applicationContext);
    when(applicationContext.getBean(FilterFactory.class)).thenReturn(filterFactory);

    CallTypeFactory callTypeFactory = new CallTypeFactory();
    callTypeFactory.setDialInPSTN(new DialInPSTN(null, null));
    callTypeFactory.setDialInB2B(new DialInB2B(null, null));
    callTypeFactory.setDialOutWxC(new DialOutWxC(null, null));
    callTypeFactory.setDialOutB2B(new DialOutB2B(null, null));

    filter = new Filter(callTypeFactory, filterFactory);
    List<CallTypeEnum> interestedCallTypes = new ArrayList<>();
    interestedCallTypes.add(CallTypeEnum.DIAL_IN_PSTN);
    interestedCallTypes.add(CallTypeEnum.DIAL_IN_B2B);
    interestedCallTypes.add(CallTypeEnum.DIAL_OUT_WXC);
    interestedCallTypes.add(CallTypeEnum.DIAL_OUT_B2B);
    filter.register(interestedCallTypes);

    when(proxySIPRequest.getCache()).thenReturn(cache);
  }

  @BeforeMethod
  public void setup() {
    reset(proxySIPRequest);
    cache.clear();
  }

  @Test
  public void testDialInPSTN() throws ParseException, InvalidCallTypeException {
    // below is a check which makes sure we don't consider calltype tag for dial-in call
    ((SipUri) sipRequest.getRequestURI())
        .setParameter(SipParamConstants.CALLTYPE, SipParamConstants.DIAL_IN_TAG);
    when(proxySIPRequest.getNetwork()).thenReturn("net_sp");
    when(proxySIPRequest.getRequest()).thenReturn(sipRequest);
    when(proxySIPRequest.getCallId()).thenReturn("DialInPSTN");
    CallType callType = filter.filter(proxySIPRequest);
    Assert.assertEquals(callType.getClass(), DialInPSTN.class);
  }

  @Test
  public void testDialInB2B() throws ParseException, InvalidCallTypeException {
    ((SipUri) sipRequest.getRequestURI())
        .setParameter(SipParamConstants.CALLTYPE, SipParamConstants.DIAL_IN_TAG);
    when(proxySIPRequest.getNetwork()).thenReturn("net_b2b");
    when(proxySIPRequest.getRequest()).thenReturn(sipRequest);
    when(proxySIPRequest.getCallId()).thenReturn("DialInB2B");
    CallType callType = filter.filter(proxySIPRequest);
    Assert.assertEquals(callType.getClass(), DialInB2B.class);
  }

  @Test
  public void testDialOutWxC() throws ParseException, InvalidCallTypeException {
    // below is a check which makes sure we don't consider calltype tag for dial-in call
    ((SipUri) sipRequest.getRequestURI())
        .setParameter(SipParamConstants.CALLTYPE, SipParamConstants.DIAL_OUT_TAG);
    when(proxySIPRequest.getNetwork()).thenReturn("net_cc");
    when(proxySIPRequest.getRequest()).thenReturn(sipRequest);
    when(proxySIPRequest.getCallId()).thenReturn("DialOutWxC");
    CallType callType = filter.filter(proxySIPRequest);
    Assert.assertEquals(callType.getClass(), DialOutWxC.class);
  }

  @Test
  public void testDialOutB2B() throws ParseException, InvalidCallTypeException {
    ((SipUri) sipRequest.getRequestURI())
        .setParameter(SipParamConstants.CALLTYPE, SipParamConstants.DIAL_OUT_TAG);
    when(proxySIPRequest.getNetwork()).thenReturn("net_b2b");
    when(proxySIPRequest.getRequest()).thenReturn(sipRequest);
    when(proxySIPRequest.getCallId()).thenReturn("DialOutB2B");
    CallType callType = filter.filter(proxySIPRequest);
    Assert.assertEquals(callType.getClass(), DialOutB2B.class);
  }

  @Test(expectedExceptions = {InvalidCallTypeException.class})
  public void testInvalidCallType() throws ParseException, InvalidCallTypeException {
    ((SipUri) sipRequest.getRequestURI()).setParameter(SipParamConstants.CALLTYPE, "Invalid");
    when(proxySIPRequest.getNetwork()).thenReturn("net_b2b");
    when(proxySIPRequest.getRequest()).thenReturn(sipRequest);
    when(proxySIPRequest.getCallId()).thenReturn("InvalidCallType");
    filter.filter(proxySIPRequest);
  }
}
