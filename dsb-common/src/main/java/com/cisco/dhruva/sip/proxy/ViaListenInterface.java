package com.cisco.dhruva.sip.proxy;

import java.net.InetAddress;

public interface ViaListenInterface extends ListenInterface {

  /** @return port to be used as the source port of outgoing requests */
  public int getSourcePort();

  /** @return the interface to be used as the source port of outgoing requests */
  public InetAddress getSourceAddress();
}
