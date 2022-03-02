package com.cisco.dhruva.util;

public class Token {

  public static final String AT_SIGN = "@";
  public static final String COLON = ":";

  public static final String SLASH = "/";
  public static final String DOUBLE_SLASH = SLASH + SLASH;

  // sip tokens
  public static final String SIP = "sip";
  public static final String SIP_COLON = SIP + COLON;
  public static final String SIP_COLON_DOUBLE_SLASH = SIP_COLON + DOUBLE_SLASH;

  // sips tokens
  public static final String SIPS = SIP + "s";
  public static final String SIPS_COLON = SIPS + COLON;
  public static final String SIPS_COLON_DOUBLE_SLASH = SIPS_COLON + DOUBLE_SLASH;

  public static final String UDP = "udp";
  public static final String TLS = "tls";
  public static final String TCP = "tcp";
}
