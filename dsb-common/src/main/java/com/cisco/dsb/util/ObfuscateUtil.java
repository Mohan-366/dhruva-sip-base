package com.cisco.dsb.util;

import com.cisco.wx2.util.Utilities;
import java.util.function.Function;
import java.util.regex.Pattern;

public class ObfuscateUtil {
  private static final Pattern CRYPTO_PATTERN =
      Pattern.compile("((a=crypto:[\\w ]*inline:)|(a=ice-pwd:))([\\w+/]+)");
  private static final String CRYPTO_MASK = "************";

  private static final String Quote = "\"";
  private static final String digits = "digits=";
  private static final Pattern DTMF_DIGITS_PATTERN =
      Pattern.compile(digits + Quote + "([0-9a-dA-D#\\*]*)" + Quote);
  private static final String DTMF_DIGITS_MASK = digits + Quote + "OBFUSCATED" + Quote;

  private static Function<String, String> obfuscateSdp =
      (message) ->
          Utilities.replaceAll(
              message, CRYPTO_PATTERN, (matcher) -> matcher.group(1) + CRYPTO_MASK);
  private static Function<String, String> obfuscateDtmf =
      (message) ->
          Utilities.replaceAll(message, DTMF_DIGITS_PATTERN, (matcher) -> DTMF_DIGITS_MASK);

  private static Function<String, String> obfuscateMsg = obfuscateSdp.andThen(obfuscateDtmf);

  public static String obfuscateMsg(String sipMsg) {
    return obfuscateMsg.apply(sipMsg);
  }
}
