package com.cisco.dsb.sip.proxy;

import com.cisco.dsb.transport.Transport;

/**
 * the class is used to describe an interface (port/protocol for now) to listen on. The proxy uses
 * it to populate Via and Record-Route
 */
public interface ListenInterface {

  /** @return port to insert into Via header */
  int getPort();

  /** @return protocol to insert into Via header */
  Transport getProtocol();

  /** @return the interface to insert into Via header */
  String getAddress();

  /** @return status on whether to attach externalIp or not */
  boolean shouldAttachExternalIp();
}