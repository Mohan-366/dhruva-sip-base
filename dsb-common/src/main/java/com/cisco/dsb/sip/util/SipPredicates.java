package com.cisco.dsb.sip.util;

import com.cisco.wx2.util.Token;
import java.util.function.Predicate;
import javax.sip.message.Request;

public class SipPredicates {

  // sip schemes
  public static final Predicate<String> sipScheme = uri -> uri.equals(Token.Sip);
  public static final Predicate<String> sipsScheme = uri -> uri.equals(Token.Sips);
  public static final Predicate<String> telScheme = uri -> uri.equals(Token.Tel);

  // sip methods
  public static final Predicate<String> options = m -> m.equals(Request.OPTIONS);
  public static final Predicate<String> register = m -> m.equals(Request.REGISTER);
}
