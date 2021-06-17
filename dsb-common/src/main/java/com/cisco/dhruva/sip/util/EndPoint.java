package com.cisco.dhruva.sip.util;

import com.cisco.dhruva.transport.Transport;
import com.cisco.dhruva.util.log.Trace;

/**
 * This class describes/represents the end address to a network application. It encapsulates the
 * device address, port and the protocol and has accessor methods to get and set them.
 */
public class EndPoint implements Cloneable {

  private static final Trace Log = Trace.getTrace(EndPoint.class.getName());

  /* The logical network for this end point */
  protected String network;

  /* The host name of the end point */
  protected String host;

  /* The port number of the end point */
  protected int port = 5060;

  /* The protocol for this end point */
  protected Transport protocol = Transport.UDP;

  private String key = null;
  private String _intern = null;
  private int hashCode = -1;

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
    if (Log.on && Log.isDebugEnabled()) Log.debug("Entering EndPoint()");
    this.network = network;
    this.host = host;
    if (port > 0) this.port = port;

    if (protocol != null) this.protocol = protocol;
    createKey();
    if (Log.on && Log.isDebugEnabled()) Log.debug("Leaving EndPoint()");
  }

  /**
   * Gets the network name.
   *
   * @return the network name.
   */
  public final String getNetwork() {
    return network;
  }

  /**
   * Gets the domain name.
   *
   * @return the domain name.
   */
  public final String getHost() {
    return host;
  }

  /**
   * returns the port number of this end point
   *
   * @return port number
   */
  public final int getPort() {
    return port;
  }

  /**
   * returns the protocol of this end point address
   *
   * @return int representing the protocol.
   */
  public final Transport getProtocol() {
    return protocol;
  }

  /** our equals implementation */
  public boolean equals(Object obj) {
    if (obj == null) return false;
    EndPoint ep = (EndPoint) obj;
    return (_intern.equals(ep._intern));
  }

  public String toString() {
    return key;
  }

  public final int hashCode() {
    return hashCode;
  }

  public final String getHashKey() {
    return key;
  }

  private void createKey() {
    key = network + ":" + host + ":" + port + ":" + protocol;
    _intern = key.intern();
    hashCode = key.hashCode();
  }
}
