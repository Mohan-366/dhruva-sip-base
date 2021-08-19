package com.cisco.dsb.proxy.bootstrap;

import com.cisco.dsb.common.sip.stack.dto.DhruvaNetwork;
import com.cisco.dsb.common.transport.Transport;
import java.net.InetAddress;
import java.util.concurrent.CompletableFuture;
import javax.sip.SipListener;

public interface DhruvaServer {
  public CompletableFuture startListening(
      Transport transportType,
      DhruvaNetwork transportConfig,
      InetAddress address,
      int port,
      SipListener handler);
}
