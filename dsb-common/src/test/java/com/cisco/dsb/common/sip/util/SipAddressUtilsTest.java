package com.cisco.dsb.common.sip.util;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import javax.sip.address.TelURL;
import org.testng.Assert;
import org.testng.annotations.Test;

public class SipAddressUtilsTest {

  @Test
  public void testConvertTelToSipUriUser() {
    runConvertTelToSipUriUser("3309", "3309");
    runConvertTelToSipUriUser("3*309", "3*309");
    runConvertTelToSipUriUser("3-309#", "3309%23");
  }

  private void runConvertTelToSipUriUser(String tel, String expect) {
    String result = SipAddressUtils.convertTelToSipUriUser(tel);
    String msg = String.format("FAILED tel=[%s] expect=[%s] result=[%s]", tel, expect, result);
    Assert.assertEquals(result, expect, msg);
  }

  @Test
  public void testPhoneNoFromTel() {
    TelURL url = mock(TelURL.class);
    String expected = "12345";
    when(url.getPhoneNumber()).thenReturn(expected);

    when(url.isGlobal()).thenReturn(true);
    Assert.assertEquals(SipAddressUtils.phoneNumberFromTelUrl(url), "+" + expected);

    when(url.isGlobal()).thenReturn(false);
    Assert.assertEquals(SipAddressUtils.phoneNumberFromTelUrl(url), expected);
  }
}
