/*
 * Copyright (c) 2020  by Cisco Systems, Inc.All Rights Reserved.
 */

package com.cisco.dsb.sip.util;

import com.cisco.dsb.sip.proxy.ListenInterface;
import com.cisco.dsb.sip.stack.dto.DhruvaNetwork;
import com.cisco.dsb.transport.Transport;
import com.cisco.dsb.util.log.DhruvaLoggerFactory;
import com.cisco.dsb.util.log.Logger;
import java.net.InetAddress;
import java.net.UnknownHostException;

/** the class describing an interface (port/protocol for now) to listen on */
public class ListenIf implements ListenInterface {

  protected int port;
  protected Transport protocol;
  protected String addressStr = null;

  protected InetAddress addressInet = null;
  protected static final int UDP_CLEANUP_TIME = 90; // in secs

  protected InetAddress translatedAddressInet = null;

  protected DhruvaNetwork network =
      null; // this data member indicated INTERNAL, EXTERNAL or EXTERNAL_OBTAIN in listen command

  // hold translated address/port from listen external command
  protected String translatedAddressStr = null;
  protected int translatedPort = -1;

  protected boolean attachExternalIp;
  // Our Log object
  private static final Logger Log = DhruvaLoggerFactory.getLogger(ListenIf.class);

  /**
   * Creates a new ListenIf that based on the resolveAddress parameter, will try create an
   * internally stored InetAddress used when startListening() is called.
   *
   * @param port The port to listen on
   * @param interfaceIP The host name or ip address to listen on
   * @param translatedAddress The address that will be put in the via if this is an external
   *     interface. Usefull if you are listening on one interface, but want external devices to see
   *     another.
   * @param translatedPort The port that will be put in the via if this is an external interface.
   *     Usefull if you are listening on one interface, but want external devices to see another.
   */
  public ListenIf(
      int port,
      Transport protocol,
      String interfaceIP,
      InetAddress address,
      DhruvaNetwork direction,
      String translatedInterfaceIP,
      InetAddress translatedAddress,
      int translatedPort,
      boolean attachExternalIp) {
    this.port = port;
    this.protocol = protocol;
    this.network = direction;

    this.addressInet = address;
    addressStr = interfaceIP;

    if (translatedInterfaceIP != null && translatedAddress != null) {
      this.translatedAddressStr = translatedInterfaceIP;
      this.translatedPort = translatedPort;
      this.translatedAddressInet = translatedAddress;
    } else {
      this.translatedAddressStr = addressStr;
      this.translatedAddressInet = address;
      this.translatedPort = port;
    }

    this.attachExternalIp = attachExternalIp;
  }

  public ListenIf(
      int port,
      Transport protocol,
      String interfaceIP,
      DhruvaNetwork direction,
      String translatedInterfaceIP,
      int translatedPort,
      boolean attachExternalIp)
      throws UnknownHostException {
    this(
        port,
        protocol,
        interfaceIP,
        InetAddress.getByName(interfaceIP.toString()),
        direction,
        translatedInterfaceIP,
        null,
        translatedPort,
        attachExternalIp);

    this.addressInet = InetAddress.getByName(interfaceIP.toString());

    if (translatedInterfaceIP != null) {
      this.translatedAddressInet = InetAddress.getByName(translatedInterfaceIP.toString());
    } else {
      this.translatedAddressInet = this.addressInet;
    }
  }

  /**
   * This constructor is useful when creating temporary ListenIfs to do lookups in a hashmap with,
   * since the hash and equality depends only on these three values. Using this constructor will
   * cause getAddress to return null.
   */
  public ListenIf(int port, Transport protocol, InetAddress address) {
    this(port, protocol, null, address, null, null, null, 0, false);
  }

  public int getPort() {
    return port;
  }

  /**
   * @return The normalized protocol this interface is listening on
   * @see //DsControllerConfig.normalizedProtocol()
   */
  public Transport getProtocol() {
    return protocol;
  }

  public InetAddress getInetAddress() {
    if (translatedAddressInet != null) {
      return translatedAddressInet;
    } else {
      return addressInet;
    }
  }

  /** @return the translated address for the external interface or null if none was specified; */
  public String getNonTranslatedAddress() {
    return addressStr;
  }
  /** @return the translated address for the external interface or null if none was specified; */
  public InetAddress getNonTranslatedInetAddress() {
    return addressInet;
  }

  /**
   * Returns a String representation of the interface this object represents. It will always be the
   * IP address of this address, even if a host name was used to construct it.
   */
  public String getAddress() {
    if (translatedAddressStr != null) {
      return translatedAddressStr;
    } else {
      return addressStr;
    }
  }

  /** @return the translated address for the external interface or null if none was specified; */
  public String getTranslatedAddress() {
    return translatedAddressStr;
  }

  /**
   * @return the translated port for the external interface or the listen port if none was
   *     specified;
   */
  public int getTranslatedPort() {
    return translatedPort;
  }

  /** @return status on whether to attach externalIP or not */
  public boolean shouldAttachExternalIp() {
    return attachExternalIp;
  }

  /** Returns true if the port, protocol and address are the same. */
  public boolean equals(Object o) {
    if (o == null) return false;

    ListenIf listenIf = (ListenIf) o;

    return listenIf.getPort() == this.port
        && listenIf.getProtocol() == this.protocol
        &&
        // listenIf.getAddress().equals( this.getAddress() ) )
        listenIf.getNonTranslatedInetAddress().equals(addressInet);
  }

  public int hashCode() {
    long sum = (port * addressInet.hashCode() * protocol.ordinal());
    return (int) (sum % Integer.MAX_VALUE);
  }

  public String toString() {
    return "ListenIf: addressStr="
        + this.addressStr
        + ", port = "
        + this.port
        + ", protocol= "
        + protocol
        + ", protocolInt= "
        + protocol
        + ", translatedAddressStr = "
        + this.translatedAddressStr
        + ", translatedPort = "
        + this.translatedPort
        + ", direction="
        + network
        + ", attachExternalIp = "
        + attachExternalIp;
  }

  public DhruvaNetwork getNetwork() {
    return network;
  }
}
