package com.cisco.dsb.common.sip.util;

import javax.sip.address.TelURL;

/** Utilities for javax.sip.address */
public class SipAddressUtils {

  public static String phoneNumberFromTelUrl(TelURL telUrl) {
    String number = telUrl.getPhoneNumber();
    if (telUrl.isGlobal()) {
      // TelURL::getPhoneNumber does not include the leading/optional +
      // For Spark clients + Locus, we are supposed to keep the plus
      number = "+" + number;
    }
    return number;
  }

  /**
   * If calling a telephone number from Spark, CallService puts it into a SIP URI as the user
   * portion. This requires we munge it a bit so it works in that new context.
   *
   * <p>tel URI formatting: - Locus seems to remove all formatting except for dashes, so no handling
   * for those. - Locus does not remove dashes, which seems consistent with the examples in RFC 3966
   * (tel URI RFC) but since those are not ignored as formatting if in a SIP URI, we remove them.
   * Example: If I call my own extension (via hybrid) 3309 versus 33-09 from Spark, the former works
   * but the latter does not. We get 404 Not Found back from the latter and the called URI is
   * "33-09@cisco.com;user=phone".
   *
   * <p>Real digits: - # is a valid phone keypad character but reserved in a SIP URI, so encode
   * those. (https://jira-eng-gpk2.cisco.com/jira/browse/SPARK-4673) - * pass as-is through the SIP
   * URI fine.
   *
   * @param tel
   * @return
   */
  public static String convertTelToSipUriUser(String tel) {
    return tel.replaceAll("-", "").replaceAll("#", "%23");
  }
}
