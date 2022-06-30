package com.cisco.dhruva.normalisation.calltypeNormalization;

import static org.mockito.Mockito.when;
import static org.testng.Assert.assertEquals;

import com.cisco.dhruva.normalisation.callTypeNormalization.DialInPSTNNorm;
import com.cisco.dhruva.util.RequestHelper;
import com.cisco.dsb.common.sip.util.EndPoint;
import com.cisco.dsb.proxy.messaging.ProxySIPRequest;
import com.cisco.dsb.trunk.trunks.AbstractTrunk.TrunkCookie;
import com.cisco.dsb.trunk.util.SipParamConstants;
import gov.nist.javax.sip.address.SipUri;
import gov.nist.javax.sip.message.SIPRequest;
import java.text.ParseException;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.Test;

public class DialInPSTNNormTest {
  private DialInPSTNNorm dialInPSTNNorm;
  @Mock ProxySIPRequest proxySIPRequest;
  @Mock TrunkCookie cookie;
  @Mock EndPoint endpoint;
  SIPRequest request;

  @BeforeClass
  public void prepare() {
    MockitoAnnotations.openMocks(this);
    dialInPSTNNorm = new DialInPSTNNorm();
  }

  @Test
  public void preNormalizeTest() throws ParseException {
    request = (SIPRequest) RequestHelper.getInviteRequest();
    when(proxySIPRequest.getRequest()).thenReturn(request);

    // testing preNormalize
    dialInPSTNNorm.preNormalize().accept(proxySIPRequest);

    assertEquals(
        ((SipUri) request.getRequestURI()).getParameter(SipParamConstants.X_CISCO_OPN),
        SipParamConstants.OPN_IN);
    assertEquals(
        ((SipUri) request.getRequestURI()).getParameter(SipParamConstants.X_CISCO_DPN),
        SipParamConstants.DPN_IN);
    assertEquals(
        ((SipUri) request.getRequestURI()).getParameter(SipParamConstants.CALLTYPE),
        SipParamConstants.DIAL_IN_TAG);
  }

  @Test
  public void postNormalizeTest() throws ParseException {
    request = (SIPRequest) RequestHelper.getInviteRequest();
    when(cookie.getClonedRequest()).thenReturn(proxySIPRequest);
    when(proxySIPRequest.getRequest()).thenReturn(request);
    when(endpoint.getHost()).thenReturn("1.2.3.4");
    when(endpoint.getPort()).thenReturn(5060);

    // testing postNormalize
    dialInPSTNNorm.postNormalize().accept(cookie, endpoint);
    assertEquals(((SipUri) request.getRequestURI()).getHost(), "1.2.3.4");
    assertEquals(((SipUri) request.getRequestURI()).getPort(), 5060);
  }
}
