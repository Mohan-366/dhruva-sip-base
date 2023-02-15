package com.cisco.dsb.common.sip.util;

import com.cisco.dsb.common.servergroup.ServerGroup;
import com.cisco.dsb.common.servergroup.ServerGroupElement;
import com.cisco.dsb.common.transport.Transport;
import lombok.*;
import org.apache.commons.lang3.builder.EqualsBuilder;

/**
 * This class describes/represents the end address to a network application. It encapsulates the
 * device address, port and the protocol and has accessor methods to get and set them.
 */
@Getter
@ToString
@CustomLog
public class EndPoint implements Cloneable {

  /* The logical network for this end point */
  protected final String network;

  /* The host name of the end point */
  protected final String host;

  protected String serverGroupName;

  /* The port number of the end point */
  protected final int port;

  /* The protocol for this end point */
  protected final Transport protocol;

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
   * @param serverGroup SG HostName
   */
  public EndPoint(String network, String host, int port, Transport protocol, String serverGroup) {
    this.network = network;
    this.host = host;
    this.serverGroupName = serverGroup;
    this.port = port;
    this.protocol = protocol;
    createKey();
  }

  public EndPoint(String network, String host, int port, Transport protocol) {
    this(network, host, port, protocol, null);
  }

  public EndPoint(ServerGroup serverGroup, ServerGroupElement serverGroupElement) {
    this(
        serverGroup.getNetworkName(),
        serverGroupElement.getIpAddress(),
        serverGroupElement.getPort(),
        serverGroupElement.getTransport(),
        serverGroup.getHostName());
  }

  /** our equals implementation */
  @Override
  public boolean equals(Object obj) {
    if (this == obj) return true;
    if (obj instanceof EndPoint) {
      EndPoint ep = (EndPoint) obj;
      return new EqualsBuilder().append(_intern, ep._intern).isEquals();
    }
    return false;
  }

  @Override
  public final int hashCode() {
    return hashCode;
  }

  private void createKey() {
    key = network + ":" + host + ":" + port + ":" + protocol;
    _intern = key.intern();
    hashCode = key.hashCode();
  }
}
