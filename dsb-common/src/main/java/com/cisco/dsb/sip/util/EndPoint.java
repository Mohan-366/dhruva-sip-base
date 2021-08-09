package com.cisco.dsb.sip.util;

import com.cisco.dsb.transport.Transport;
import com.cisco.dsb.util.log.DhruvaLoggerFactory;
import com.cisco.dsb.util.log.Logger;
import lombok.*;

/**
 * This class describes/represents the end address to a network application. It encapsulates the
 * device address, port and the protocol and has accessor methods to get and set them.
 */
@Getter
@NoArgsConstructor
@ToString
public class EndPoint implements Cloneable {

  private static final Logger Log = DhruvaLoggerFactory.getLogger(EndPoint.class);

  /* The logical network for this end point */
  protected String network;

  /* The host name of the end point */
  protected String host;

  /* The port number of the end point */
  protected int port = 5060;

  /* The protocol for this end point */
  protected Transport protocol = Transport.UDP;

  @ToString.Exclude private String key = null;
  @ToString.Exclude private String _intern = null;
  @ToString.Exclude private int hashCode = -1;

  /**
   * Creates an EndPoint object from the specified network, host, protocol, port number. Note that
   * the application using this object should make sure that the port number is non negative .
   *
   * @param network The network name of this end point/address.
   * @param host The Host name of this end point/address.
   * @param port The port number.
   * @param protocol The int representing the protocol.
   */
  public EndPoint(String network, String host, int port, Transport protocol) {
    Log.debug("Entering EndPoint()");
    this.network = network;
    this.host = host;
    if (port > 0) this.port = port;

    if (protocol != null) this.protocol = protocol;
    createKey();
    Log.debug("Leaving EndPoint()");
  }

  /** our equals implementation */
  public boolean equals(Object obj) {
    if (obj == null) return false;
    EndPoint ep = (EndPoint) obj;
    return (_intern.equals(ep._intern));
  }

  public final int hashCode() {
    return hashCode;
  }

  private void createKey() {
    key = network + ":" + host + ":" + port + ":" + protocol;
    _intern = key.intern();
    hashCode = key.hashCode();
  }
}
