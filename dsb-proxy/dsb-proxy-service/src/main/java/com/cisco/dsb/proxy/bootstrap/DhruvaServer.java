package com.cisco.dsb.proxy.bootstrap;

import com.cisco.dsb.common.sip.bean.SIPListenPoint;
import com.cisco.dsb.common.sip.tls.DsbTrustManager;
import java.util.concurrent.CompletableFuture;
import javax.net.ssl.KeyManager;
import javax.sip.SipStack;

public interface DhruvaServer {
  CompletableFuture<SipStack> startListening(
      SIPListenPoint listenPoint, DsbTrustManager dsbTrustManager, KeyManager keyManager);
}
