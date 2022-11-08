package com.cisco.dsb.proxy.bootstrap;

import com.cisco.dsb.common.config.sip.CommonConfigurationProperties;
import com.cisco.dsb.common.executor.DhruvaExecutorService;
import com.cisco.dsb.common.ratelimiter.DsbRateLimiter;
import com.cisco.dsb.common.service.MetricService;
import com.cisco.dsb.common.sip.bean.SIPListenPoint;
import com.cisco.dsb.common.sip.tls.DsbTrustManager;
import java.util.concurrent.CompletableFuture;
import javax.net.ssl.KeyManager;
import javax.sip.SipListener;

public interface DhruvaServer {
  public CompletableFuture startListening(
      CommonConfigurationProperties commonConfigurationProperties,
      SIPListenPoint listenPoint,
      DsbRateLimiter dsbRateLimiter,
      DsbTrustManager trustManager,
      KeyManager keyManager,
      DhruvaExecutorService executorService,
      MetricService metricService,
      SipListener handler);
}
