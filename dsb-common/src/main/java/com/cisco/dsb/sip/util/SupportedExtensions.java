package com.cisco.dsb.sip.util;

import java.util.HashMap;
import java.util.function.Predicate;

public class SupportedExtensions {

  private static final HashMap<String, String> extensions = new HashMap<>();

  /** Checks whether an extension name appearing in Proxy-Require is registered as supported */
  public static final Predicate<String> isSupported = extensions::containsKey;

  /** Adds an extension to the extension list */
  public static synchronized void addExtension(String extension) {
    if (isSupported.test(extension)) return; // the extension is already listed as supported
    extensions.put(extension, extension);
  }

  /** removes an extension from the extensions list */
  public static synchronized void removeExtension(String extension) {
    if (!isSupported.test(extension)) return;
    extensions.remove(extension);
  }
}
