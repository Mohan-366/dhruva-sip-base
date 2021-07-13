package com.cisco.dsb.sip.stack.util;

import java.security.SecureRandom;

/**
 * This class represents a tag as specified in RFC 3261. It provides methods to build, access,
 * modify, serialize and clone the header.
 */
public class SipTag implements Cloneable {
  /** A prefix for all tags. */
  private static final String DEFAULT_PROLOG = "dsb";

  /** The tag itself. */
  private String m_strTag;

  /** Default constructor. */
  public SipTag() {}

  /**
   * Constructor which sets the prolog.
   *
   * @param prolog the prolog value.
   */
  public SipTag(String prolog) {
    reGenerate(prolog);
  }

  /**
   * Retrieves the tag information.
   *
   * @return The tag.
   */
  public String getValue() {
    return m_strTag;
  }

  /**
   * Regenerates the tag value.
   *
   * @param prolog the prolog value.
   */
  public void reGenerate(String prolog) {
    m_strTag = generateTag(prolog);
  }

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
    return new String(sb.toString());
  }
}
