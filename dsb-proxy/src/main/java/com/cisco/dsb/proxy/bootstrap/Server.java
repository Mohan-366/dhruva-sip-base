package com.cisco.dsb.proxy.bootstrap;

import com.cisco.dsb.common.sip.tls.DsbTrustManager;
import java.net.InetAddress;
import java.util.concurrent.CompletableFuture;
import javax.net.ssl.KeyManager;
import javax.sip.SipListener;
import javax.sip.SipStack;

public interface Server {
  public void startListening(
      InetAddress address,
      int port,
      SipListener handler,
      DsbTrustManager dsbTrustManager,
      KeyManager keyManager,
      CompletableFuture<SipStack> serverStartFuture);
}
