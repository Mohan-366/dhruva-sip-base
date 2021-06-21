package com.cisco.dhruva.bootstrap;

import com.cisco.dsb.sip.stack.dto.DhruvaNetwork;
import com.cisco.dsb.transport.Transport;
import java.net.InetAddress;
import java.util.concurrent.CompletableFuture;
import javax.sip.SipListener;

public interface Server {
  public void startListening(
      Transport transportType,
      DhruvaNetwork transportConfig,
      InetAddress address,
      int port,
      SipListener handler,
      CompletableFuture serverStartFuture);
}
