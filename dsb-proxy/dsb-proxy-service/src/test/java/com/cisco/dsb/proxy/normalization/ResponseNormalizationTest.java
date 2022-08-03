package com.cisco.dsb.proxy.normalization;

import static com.cisco.dsb.proxy.normalization.NormalizationUtil.doResponseNormalization;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.cisco.dsb.common.exception.DhruvaException;
import com.cisco.dsb.proxy.messaging.ProxySIPResponse;
import com.cisco.dsb.proxy.sip.ProxyCookieImpl;
import com.cisco.dsb.proxy.util.ResponseHelper;
import gov.nist.javax.sip.message.SIPResponse;
import java.text.ParseException;
import java.util.function.Consumer;
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
  public void testResponseNormalization() throws DhruvaException, ParseException {
    SIPResponse response = ResponseHelper.getSipResponse();
    when(proxyCookie.getResponseNormConsumer()).thenReturn(responseConsumer);
    when(proxySIPResponse.getResponse()).thenReturn(response);
    when(proxySIPResponse.getCookie()).thenReturn(proxyCookie);
    doResponseNormalization(proxySIPResponse);
    verify(responseConsumer, times(1)).accept(proxySIPResponse);
  }
}
