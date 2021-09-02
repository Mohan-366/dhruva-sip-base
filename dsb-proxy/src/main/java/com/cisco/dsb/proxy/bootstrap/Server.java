package com.cisco.dsb.proxy.bootstrap;

import com.cisco.dsb.common.config.sip.DhruvaSIPConfigProperties;
import java.net.InetAddress;
import java.util.concurrent.CompletableFuture;
import javax.sip.SipListener;
import javax.sip.SipStack;

public interface Server {
  public void startListening(
      DhruvaSIPConfigProperties dhruvaSIPConfigProperties,
      InetAddress address,
      int port,
      SipListener handler,
      CompletableFuture<SipStack> serverStartFuture);
}
