package com.cisco.dsb.common.sip.stack.util;

public class IPValidator {
  /**
   * Determine if the given host string is a numeric IP address. This works for IPv4 and IPv6.
   *
   * @param host either a host name or IPv4 or IPv6 address
   * @return true if host String is an IP addr, false otherwise
   */
  public static boolean hostIsIPAddr(String host) {
    return hostIsIPv4Addr(host) || hostIsIPv6Addr(host);
  }

  /**
   * Determine if the given host string is a numeric IP address.
   *
   * @param host either a host name or IPv4 or IPv6 address
   * @return true if <code>host</code> is an IPv4 addr, false otherwise
   */
  public static boolean hostIsIPv4Addr(String host) {
    if (host == null || host.length() == 0) {
      return false;
    }

    // optimized version of below - jsm
    char firstChar = host.charAt(0);
    if (firstChar > '9' || firstChar < '0') {
      return false;
    }

    int len = host.length();
    int dotCount = 0;

    // already handled first char above
    for (int i = 1; i < len; i++) {
      switch (host.charAt(i)) {
        case '0':
        case '1':
        case '2':
        case '3':
        case '4':
        case '5':
        case '6':
        case '7':
        case '8':
        case '9':
          break;
        case '.':
          dotCount++;
          break;
        default:
          return false;
      }
    }
    return dotCount == 3;
  }

  // IPv6address = hexpart [ ":" IPv4address ]
  // IPv4address = 1*3DIGIT "." 1*3DIGIT "." 1*3DIGIT "." 1*3DIGIT

  // IPv6prefix  = hexpart "/" 1*2DIGIT

  // hexpart = hexseq | hexseq "::" [ hexseq ] | "::" [ hexseq ]
  // hexseq  = hex4 *( ":" hex4)
  // hex4    = 1*4HEXDIG

  /**
   * Determine if the given host string is an IPv6 address.
   *
   * @param host either a host name or IPv4 or IPv6 address
   * @return true if <code>host</code> is an IP addr, false otherwise
   */
  public static boolean hostIsIPv6Addr(String host) {
    // This does not verify that it is an IPv6 address.
    // It does ensure that it is not an IPv4 address or a host name,
    // hence IPv6 is all that is left, besides some invalid, and it
    // might as well be an invalid IPv6 address vs. an invalid host or IPv4.
    // Just look for the : since that must appear in an IPv6 address
    // and must not in a hostname or IPv4 address.
    return host != null && host.length() != 0 && host.indexOf(':') != -1;
  }
}
