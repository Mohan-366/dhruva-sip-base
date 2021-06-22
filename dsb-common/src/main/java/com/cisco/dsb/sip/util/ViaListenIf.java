package com.cisco.dsb.sip.util;

import com.cisco.dhruva.sip.proxy.ViaListenInterface;
import com.cisco.dsb.exception.DhruvaException;
import com.cisco.dsb.sip.stack.dto.DhruvaNetwork;
import com.cisco.dsb.transport.Transport;
import java.net.InetAddress;
import java.net.UnknownHostException;

/*
 * An implementation of the DsViaListenIf interface for storing the via address
 * the re should use.
 */
public class ViaListenIf extends ListenIf implements ViaListenInterface {

  protected int srcPort = -1;
  protected InetAddress srcAddress = null;
  protected InetAddress translatedSrcAddress = null;

  public ViaListenIf(
      int port,
      Transport protocol,
      String interfaceIP,
      boolean attachExternalIp,
      DhruvaNetwork direction,
      int srcPort,
      InetAddress scrAddress,
      String translatedAddress,
      InetAddress translatedSrcAddress,
      int translatedPort)
      throws UnknownHostException, DhruvaException {
    super(
        port,
        protocol,
        interfaceIP,
        direction,
        translatedAddress,
        translatedPort,
        attachExternalIp);
    this.srcPort = srcPort;
    this.srcAddress = srcAddress;
    this.translatedSrcAddress = translatedSrcAddress;
  }

  public int getSourcePort() {
    return this.srcPort;
  }

  public InetAddress getSourceAddress() {
    return this.srcAddress;
  }

  /*
   * To help with debuging
   */
  public String toString() {
    return "Via Address "
        + addressStr
        + ":"
        + port
        + " ["
        + protocol
        + "] "
        + "-- Src Address "
        + srcAddress;
  }
}
