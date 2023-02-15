package com.cisco.dsb.proxy.bootstrap;

import com.cisco.dsb.common.config.sip.CommonConfigurationProperties;
import com.cisco.dsb.common.executor.DhruvaExecutorService;
import com.cisco.dsb.common.ratelimiter.DsbRateLimiter;
import com.cisco.dsb.common.service.MetricService;
import com.cisco.dsb.common.service.SipServerLocatorService;
import com.cisco.dsb.common.sip.bean.SIPListenPoint;
import com.cisco.dsb.common.sip.tls.DsbTrustManager;
import com.cisco.dsb.proxy.bootstrap.proxyserver.SipServer;
import gov.nist.core.net.AddressResolver;
import java.util.concurrent.CompletableFuture;
import javax.net.ssl.KeyManager;
import javax.sip.SipListener;
import javax.sip.SipStack;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

@Component
public class DhruvaServerImpl implements DhruvaServer {

  public DhruvaServerImpl() {}

  private CommonConfigurationProperties commonConfigurationProperties;
  private DsbRateLimiter dsbRateLimiter;
  private DhruvaExecutorService executorService;
  private MetricService metricService;
  private SipListener handler;
  private AddressResolver addressResolver;

  @Autowired
  public void setCommonConfigurationProperties(
      CommonConfigurationProperties commonConfigurationProperties) {
    this.commonConfigurationProperties = commonConfigurationProperties;
  }

  @Autowired
  public void setDsbRateLimiter(DsbRateLimiter dsbRateLimiter) {
    this.dsbRateLimiter = dsbRateLimiter;
  }

  @Autowired
  public void setExecutorService(DhruvaExecutorService dhruvaExecutorService) {
    this.executorService = dhruvaExecutorService;
  }

  @Autowired
  public void setMetricService(MetricService metricService) {
    this.metricService = metricService;
  }

  @Autowired
  public void setHandler(SipListener handler) {
    this.handler = handler;
  }

  @Autowired
  public void setAddressResolver(SipServerLocatorService sipServerLocatorService) {
    this.addressResolver = sipServerLocatorService;
  }

  @Override
  public CompletableFuture<SipStack> startListening(
      SIPListenPoint listenPoint, DsbTrustManager dsbTrustManager, KeyManager keyManager) {
    CompletableFuture<SipStack> serverStartFuture = new CompletableFuture<>();
    if (handler == null) {
      serverStartFuture.completeExceptionally(
          new NullPointerException(
              " messageForwarder passed to Dhruva server startListening is null"));
      return serverStartFuture;
    }
    try {
      Server server =
          new SipServer(
              listenPoint.getTransport(),
              handler,
              executorService,
              metricService,
              commonConfigurationProperties,
              addressResolver);
      server.startListening(
          listenPoint,
          dsbRateLimiter,
          handler,
          dsbTrustManager,
          keyManager,
          serverStartFuture,
          addressResolver);
    } catch (Exception e) {
      serverStartFuture.completeExceptionally(e);
    }
    return serverStartFuture;
  }
}
