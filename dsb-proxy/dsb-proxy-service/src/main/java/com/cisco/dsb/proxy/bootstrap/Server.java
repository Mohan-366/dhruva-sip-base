package com.cisco.dsb.proxy.bootstrap;

import com.cisco.dsb.common.ratelimiter.DsbRateLimiter;
import com.cisco.dsb.common.sip.bean.SIPListenPoint;
import com.cisco.dsb.common.sip.tls.DsbTrustManager;
import java.util.concurrent.CompletableFuture;
import javax.net.ssl.KeyManager;
import javax.sip.SipListener;
import javax.sip.SipStack;

public interface Server {
  public void startListening(
      SIPListenPoint listenPoint,
      DsbRateLimiter dsbRateLimiter,
      SipListener handler,
      DsbTrustManager dsbTrustManager,
      KeyManager keyManager,
      CompletableFuture<SipStack> serverStartFuture);
}
