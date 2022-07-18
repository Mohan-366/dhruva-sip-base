package com.cisco.dsb.proxy.normalization;

import static com.cisco.dsb.proxy.normalization.NormalizationUtil.doResponseNormalization;
import static com.cisco.dsb.proxy.normalization.NormalizationUtil.doStrayResponseDefaultNormalization;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.testng.Assert.assertNotEquals;
import static org.testng.AssertJUnit.assertEquals;

import com.cisco.dsb.common.exception.DhruvaException;
import com.cisco.dsb.common.sip.bean.SIPListenPoint;
import com.cisco.dsb.common.sip.stack.dto.DhruvaNetwork;
import com.cisco.dsb.common.transport.Transport;
import com.cisco.dsb.proxy.messaging.ProxySIPResponse;
import com.cisco.dsb.proxy.sip.ProxyCookieImpl;
import com.cisco.dsb.proxy.util.ResponseHelper;
import gov.nist.javax.sip.message.SIPResponse;
import java.text.ParseException;
import java.util.function.Consumer;
import javax.sip.address.SipURI;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.Test;

public class ResponseNormalizationTest {
  @Mock ProxySIPResponse proxySIPResponse;
  @Mock ProxyCookieImpl proxyCookie;
  @Mock Consumer responseConsumer;

  @BeforeClass
  public void init() {
    MockitoAnnotations.openMocks(this);
  }

  @Test
  public void testStrayResponseNormalization() throws DhruvaException, ParseException {
    SIPResponse response = ResponseHelper.getSipResponse();
    response.getTopmostVia().setHost("10.10.10.10");
    String network = "outNetwork";
    SIPListenPoint sipListenPoint =
        SIPListenPoint.SIPListenPointBuilder()
            .setName(network)
            .setHostIPAddress("20.20.20.20")
            .setPort(5080)
            .setTransport(Transport.UDP)
            .build();
    DhruvaNetwork.createNetwork(network, sipListenPoint);
    response.setApplicationData(network);
    assertNotEquals("20.20.20.20", ((SipURI) response.getTo().getAddress().getURI()).getHost());
    assertNotEquals("10.10.10.10", ((SipURI) response.getFrom().getAddress().getURI()).getHost());
    doStrayResponseDefaultNormalization(response, network, response.getTopmostVia());
    assertEquals("20.20.20.20", ((SipURI) response.getTo().getAddress().getURI()).getHost());
    assertEquals("10.10.10.10", ((SipURI) response.getFrom().getAddress().getURI()).getHost());
  }

  @Test
  public void testResponseNormalization() throws DhruvaException, ParseException {
    SIPResponse response = ResponseHelper.getSipResponse();
    when(proxyCookie.getResponseNormConsumer()).thenReturn(responseConsumer);
    when(proxySIPResponse.getResponse()).thenReturn(response);
    when(proxySIPResponse.getCookie()).thenReturn(proxyCookie);
    doResponseNormalization(proxySIPResponse);
    verify(responseConsumer, times(1)).accept(proxySIPResponse);
  }
}
