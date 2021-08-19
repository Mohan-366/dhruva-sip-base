package com.cisco.dsb.common.sip.util;

import java.util.regex.Pattern;

public class SipPatterns {
  /**
   * Pattern matches a leading sip: or sips: scheme. Pattern is case-insensitive. Any leading
   * whitespace is ignored.
   */
  public static Pattern sipSchemePattern = Pattern.compile("(?i)^\\s*sips?:");
}
