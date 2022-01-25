package com.cisco.dsb.common.sip.stack.util;

import java.security.SecureRandom;

/**
 * This class represents a tag as specified in RFC 3261. It provides methods to build, access,
 * modify, serialize and clone the header.
 */
public class SipTag implements Cloneable {
  /** A prefix for all tags. */
  private static final String DEFAULT_PROLOG = "dsb";

  /**
   * Generates and return a new tag value.
   *
   * @return a new tag value.
   */
  public static String generateTag() {
    return generateTag(DEFAULT_PROLOG);
  }

  /**
   * Generates a new tag value with the specified prolog string and returns the same.
   *
   * @param prolog the prolog string to be used while generating the tag.
   * @return a new tag value.
   */
  public static String generateTag(String prolog) {
    StringBuffer sb = new StringBuffer(10);
    if (prolog != null) {
      sb.append(prolog);
    }
    int aRandom = (int) (new SecureRandom().nextInt(Integer.MAX_VALUE) * 65535);
    int current_time = (int) (System.currentTimeMillis() % 65535);
    sb.append(Integer.toHexString(aRandom));
    sb.append(Integer.toHexString(current_time));
    return sb.toString();
  }
}
