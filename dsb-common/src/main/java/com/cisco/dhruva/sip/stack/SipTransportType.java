package com.cisco.dhruva.sip.stack;

/** This class defines transport types as specified in RFC 3261. */
public class SipTransportType {
  /*
   * The constants which indicate the Transport type
   */

  /** Represents the class type as Integer.TYPE. */
  public static final Class TYPE = Integer.TYPE;

  /** Integer constant for the no transport type. */
  public static final int NONE = 0;
  /** Integer constant for the UDP transport type. */
  public static final int UDP = 1;
  /** Integer constant for the TCP transport type. */
  public static final int TCP = 2;
  /** Integer constant for the MULTICAST transport type. */
  public static final int MULTICAST = 3;
  /** Integer constant for the TLS transport type. */
  public static final int TLS = 4;
  /** Integer constant for the SCTP transport type. */
  public static final int SCTP = 5;
  /** Integer constant for the array size that holds transport types. */
  public static final int ARRAY_SIZE = 6;

  /** Byte mask constant for the UDP transport type. */
  public static final byte UDP_MASK = 1;
  /** Byte mask constant for the TCP transport type. */
  public static final byte TCP_MASK = 2;
  /** Byte mask constant for the MULTICAST transport type. */
  public static final byte MULTICAST_MASK = 4;
  /** Byte mask constant for the TLS transport type. */
  public static final byte TLS_MASK = 8;
  /** Byte mask constant for the SCTP transport type. */
  public static final byte SCTP_MASK = 16;

  /** An integer array that holds Integer values for various transport types. */
  public static final Integer TRANSPORT_ARRAY[];

  /** Lower case string representation of no transport type. */
  public static final String STR_NONE = "none";
  /** Lower case string representation of UDP transport type. */
  public static final String STR_UDP = "udp";
  /** Lower case string representation of TCP transport type. */
  public static final String STR_TCP = "tcp";
  /** Lower case string representation of MULTICAST transport type. */
  public static final String STR_MULTICAST = "multicast";
  /** Lower case string representation of TLS transport type. */
  public static final String STR_TLS = "tls";
  /** Lower case string representation of SCTP transport type. */
  public static final String STR_SCTP = "sctp";

  /** Upper case string representation of no transport type. */
  public static final String UC_STR_NONE = "NONE";
  /** Upper case string representation of UDP transport type. */
  public static final String UC_STR_UDP = "UDP";
  /** Upper case string representation of TCP transport type. */
  public static final String UC_STR_TCP = "TCP";
  /** Upper case string representation of MULTICAST transport type. */
  public static final String UC_STR_MULTICAST = "MULTICAST";
  /** Upper case string representation of TLS transport type. */
  public static final String UC_STR_TLS = "TLS";
  /** Upper case string representation of SCTP transport type. */
  public static final String UC_STR_SCTP = "SCTP";

  /** Holds reference to an instance of DsSipTransportType representing NONE transport. */
  public static final SipTransportType T_NONE;
  /** Holds reference to an instance of DsSipTransportType representing UDP transport. */
  public static final SipTransportType T_UDP;
  /** Holds reference to an instance of DsSipTransportType representing MULTICAST transport. */
  public static final SipTransportType T_MULTICAST;
  /** Holds reference to an instance of DsSipTransportType representing TCP transport. */
  public static final SipTransportType T_TCP;
  /** Holds reference to an instance of DsSipTransportType representing TLS transport. */
  public static final SipTransportType T_TLS;
  /** Holds reference to an instance of DsSipTransportType representing SCTP transport. */
  public static final SipTransportType T_SCTP;

  static {
    T_NONE = new SipTransportType(STR_NONE, NONE, 5060, false);
    T_UDP = new SipTransportType(STR_UDP, UDP, 5060, false);
    T_MULTICAST = new SipTransportType(STR_MULTICAST, MULTICAST, 5060, false);

    T_TCP = new SipTransportType(STR_TCP, TCP, 5060, true);
    T_TLS = new SipTransportType(STR_TLS, TLS, 5061, true);
    T_SCTP = new SipTransportType(STR_SCTP, SCTP, 5060, true);

    TRANSPORT_ARRAY = new Integer[ARRAY_SIZE];
    TRANSPORT_ARRAY[NONE] = new Integer(NONE);
    TRANSPORT_ARRAY[UDP] = new Integer(UDP);
    TRANSPORT_ARRAY[TCP] = new Integer(TCP);
    TRANSPORT_ARRAY[MULTICAST] = new Integer(MULTICAST);
    TRANSPORT_ARRAY[TLS] = new Integer(TLS);
    TRANSPORT_ARRAY[SCTP] = new Integer(SCTP);
  }

  /** The integer representation of this transport type. */
  private int m_intRep;
  /** The default port for this transport type. */
  private int m_defaultPort;
  /** The string representation of this transport type. */
  private String m_stringRep;
  /** The string representation of this transport type, in upper case. */
  private String m_stringRepUC;
  /** <code>true</code> if this transport type is reliable. */
  private boolean m_reliable;

  /**
   * Internal construction only.
   *
   * @param string_rep the string representation of this transport type.
   * @param int_rep the integer representation of this transport type.
   * @param def_port the default port for this transport type.
   * @param reliable <code>true</code> if this transport type is reliable.
   */
  private SipTransportType(String string_rep, int int_rep, int def_port, boolean reliable) {
    m_intRep = int_rep;
    m_defaultPort = def_port;
    m_stringRep = string_rep;
    m_reliable = reliable;
    m_stringRepUC = string_rep.toUpperCase();
  }

  /**
   * Returns the default port number as per the transport.
   *
   * @return the transport's default port.
   */
  public int getDefaultPort() {
    return m_defaultPort;
  }

  /**
   * Returns the integer representation of this transport.
   *
   * @return the integer representation of the transport.
   */
  public int getAsInt() {
    return m_intRep;
  }

  /**
   * Returns the lower case string representation of the transport.
   *
   * @return the lower case string representation of the transport.
   */
  public String toString() {
    return m_stringRep;
  }

  /**
   * Returns the upper case string representation of the transport.
   *
   * @return the upper case string representation of the transport.
   */
  public String toUCString() {
    return m_stringRepUC;
  }

  /**
   * Tells whether this transport type is reliable or unreliable.
   *
   * @return <code>true</code> if the transport is reliable, otherwise returns <code>false</code>.
   */
  public boolean isReliable() {
    return m_reliable;
  }

  /**
   * Retrieves the transport type as a string.
   *
   * @param type the Transport type.
   * @return the Transport as a String.
   */
  public static String getTypeAsString(int type) {
    switch (type) {
      case NONE:
        return (STR_NONE);
      case UDP:
        return (STR_UDP);
      case TCP:
        return (STR_TCP);
      case MULTICAST:
        return (STR_MULTICAST);
      case TLS:
        return (STR_TLS);
      case SCTP:
        return (STR_SCTP);
      default:
        return (STR_UDP);
    }
  }

  /**
   * Retrieves the transport type as an integer.
   *
   * @param type the Transport as a String.
   * @return the Transport type as an integer.
   */
  public static int getTypeAsInt(String type) {
    // optimmize for UDP/TCP/TLS
    // case insensitive comparisons are slow.  This is about 12x as fast as the old one.
    if (type.length() == 3) {
      char ch = type.charAt(0);
      if (ch == 'U' || ch == 'u') // UDP
      {
        ch = type.charAt(1);
        if (ch == 'D' || ch == 'd') {
          ch = type.charAt(2);
          if (ch == 'P' || ch == 'p') {
            return UDP;
          }
        }
      } else if (ch == 'T' || ch == 't') // TCP or TLS
      {
        ch = type.charAt(1);
        if (ch == 'C' || ch == 'c') // TCP
        {
          ch = type.charAt(2);
          if (ch == 'P' || ch == 'p') {
            return TCP;
          }
        } else if (ch == 'L' || ch == 'l') // TLS
        {
          ch = type.charAt(2);
          if (ch == 'S' || ch == 's') {
            return TLS;
          }
        }
      }
    }

    // these are the rare cases, if we get here, just start comparing
    if (type.equalsIgnoreCase(UC_STR_NONE)) {
      return NONE;
    }

    if (type.equalsIgnoreCase(UC_STR_MULTICAST)) {
      return MULTICAST;
    }

    if (type.equalsIgnoreCase(UC_STR_SCTP)) {
      return SCTP;
    }

    // default is UDP
    return UDP;
  }
}
