package com.cisco.dsb.proxy.bootstrap;

import com.cisco.dsb.common.config.sip.DhruvaSIPConfigProperties;
import com.cisco.dsb.common.executor.DhruvaExecutorService;
import com.cisco.dsb.common.service.MetricService;
import com.cisco.dsb.common.transport.Transport;
import com.cisco.dsb.proxy.bootstrap.proxyserver.SipServer;
import java.net.InetAddress;
import java.util.concurrent.CompletableFuture;
import javax.sip.SipListener;
import javax.sip.SipStack;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

@Component
public class DhruvaServerImpl implements DhruvaServer {

  DhruvaExecutorService executorService;

  public void setExecutorService(DhruvaExecutorService executorService) {
    this.executorService = executorService;
  }

  public void setMetricService(MetricService metricService) {
    this.metricService = metricService;
  }

  MetricService metricService;

  @Autowired
  public DhruvaServerImpl(DhruvaExecutorService executorService, MetricService metricService) {
    this.executorService = executorService;
    this.metricService = metricService;
  }

  @Override
  public CompletableFuture<SipStack> startListening(
      DhruvaSIPConfigProperties dhruvaSIPConfigProperties,
      Transport transportType,
      InetAddress address,
      int port,
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
              transportType, handler, executorService, metricService, dhruvaSIPConfigProperties);
      server.startListening(address, port, handler, serverStartFuture);
    } catch (Exception e) {
      serverStartFuture.completeExceptionally(e);
    }
    return serverStartFuture;
  }
}
