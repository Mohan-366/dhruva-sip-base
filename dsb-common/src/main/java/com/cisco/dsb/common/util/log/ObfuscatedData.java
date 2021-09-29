package com.cisco.dsb.common.util.log;

import javax.sip.address.URI;

public class ObfuscatedData {
  private final String obfuscated;
  private final String revealed;

  private ObfuscatedData(String obfuscated, String revealed) {
    this.obfuscated = obfuscated;
    this.revealed = revealed;
  }

  public static ObfuscatedData createObfuscatedIdentityUri(URI uri) {
    return new ObfuscatedData(LogUtils.obfuscateIdentityURI(uri), uri.toString());
  }

  public static ObfuscatedData createUnobfuscated(String s) {
    return new ObfuscatedData(s, s);
  }

  public static ObfuscatedData createObfuscatedString(String revealed) {
    return new ObfuscatedData(LogUtils.obfuscate(revealed), revealed);
  }

  public static ObfuscatedData format(String format, Object... args) {
    Object[] obfusArgs = new Object[args.length];
    Object[] revealedArgs = new Object[args.length];
    for (int i = 0; i < args.length; i++) {
      if (args[i] instanceof ObfuscatedData) {
        ObfuscatedData m = (ObfuscatedData) args[i];
        obfusArgs[i] = m.getObfuscatedString();
        revealedArgs[i] = m.getRevealedString();
      } else {
        obfusArgs[i] = args[i];
        revealedArgs[i] = args[i];
      }
    }
    return new ObfuscatedData(
        String.format(format, obfusArgs), String.format(format, revealedArgs));
  }

  public static ObfuscatedData format(String format, ObfuscatedData... args) {
    Object[] obfusArgs = new Object[args.length];
    Object[] revealedArgs = new Object[args.length];
    for (int i = 0; i < args.length; i++) {
      obfusArgs[i] = args[i].getObfuscatedString();
      revealedArgs[i] = args[i].getRevealedString();
    }
    return new ObfuscatedData(
        String.format(format, obfusArgs), String.format(format, revealedArgs));
  }

  public String toString() {
    return getObfuscatedString();
  }

  public String getObfuscatedString() {
    return obfuscated;
  }

  public String getRevealedString() {
    return revealed;
  }
}
