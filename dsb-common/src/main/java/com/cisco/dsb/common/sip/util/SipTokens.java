package com.cisco.dsb.common.sip.util;

import com.cisco.wx2.util.Token;

/** This class contains some common sip tokens which is extended from CSB's Token class */
public class SipTokens extends Token {

  public static final String Comma = ",";

  // sip tokens
  public static final String SipColonDoubleSlash = SipColon + DoubleSlash;

  // sips tokens
  public static final String SipsColonDoubleSlash = SipsColon + DoubleSlash;

  // tel tokens
  public static final String Tel = "tel";
  public static final String TelColon = Tel + Colon;
  public static final String SipsSrvPrefix = Underscore + Sips + Dot;

  public static final String Tcp = "tcp";
  public static final String Tls = "tls";

  //   _sip._tcp.
  public static final String SipTcpSrvPrefix = Underscore + Sip + Dot + Underscore + Tcp + Dot;
  //   _sips._tcp.
  public static final String SipTlsSrvPrefix = Underscore + Sips + Dot + Underscore + Tcp + Dot;
}
