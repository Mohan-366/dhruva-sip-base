package com.cisco.dsb.proxy.bootstrap;

import java.net.InetAddress;
import java.util.concurrent.CompletableFuture;
import javax.sip.SipListener;
import javax.sip.SipStack;

public interface Server {
  public void startListening(
      InetAddress address,
      int port,
      SipListener handler,
      CompletableFuture<SipStack> serverStartFuture);
}
