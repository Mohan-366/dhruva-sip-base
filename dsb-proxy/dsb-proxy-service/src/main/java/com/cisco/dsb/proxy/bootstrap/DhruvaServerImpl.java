package com.cisco.dsb.proxy.bootstrap;

import com.cisco.dsb.common.config.sip.CommonConfigurationProperties;
import com.cisco.dsb.common.executor.DhruvaExecutorService;
import com.cisco.dsb.common.ratelimiter.DsbRateLimiter;
import com.cisco.dsb.common.service.MetricService;
import com.cisco.dsb.common.sip.tls.DsbTrustManager;
import com.cisco.dsb.common.transport.Transport;
import com.cisco.dsb.proxy.bootstrap.proxyserver.SipServer;
import java.net.InetAddress;
import java.util.concurrent.CompletableFuture;
import javax.net.ssl.KeyManager;
import javax.sip.SipListener;
import javax.sip.SipStack;
import org.springframework.stereotype.Component;

@Component
public class DhruvaServerImpl implements DhruvaServer {

  public DhruvaServerImpl() {}

  @Override
  public CompletableFuture<SipStack> startListening(
      CommonConfigurationProperties commonConfigurationProperties,
      Transport transportType,
      InetAddress address,
      int port,
      boolean isEnableRateLimiting,
      DsbRateLimiter dsbRateLimiter,
      DsbTrustManager dsbTrustManager,
      KeyManager keyManager,
      DhruvaExecutorService executorService,
      MetricService metricService,
      SipListener handler) {
    CompletableFuture<SipStack> serverStartFuture = new CompletableFuture();
    if (transportType == null || address == null || handler == null) {
      serverStartFuture.completeExceptionally(
          new NullPointerException(
              "TransportType or address or messageForwarder passed to Dhruva server startListening is null"));
      return serverStartFuture;
    }
    try {
      Server server =
          new SipServer(
              transportType,
              handler,
              executorService,
              metricService,
              commonConfigurationProperties);
      server.startListening(
          address,
          port,
          isEnableRateLimiting,
          dsbRateLimiter,
          handler,
          dsbTrustManager,
          keyManager,
          serverStartFuture);
    } catch (Exception e) {
      serverStartFuture.completeExceptionally(e);
    }
    return serverStartFuture;
  }
}
