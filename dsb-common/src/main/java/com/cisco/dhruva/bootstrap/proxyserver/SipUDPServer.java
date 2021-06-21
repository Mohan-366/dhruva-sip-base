package com.cisco.dhruva.bootstrap.proxyserver;

import com.cisco.dhruva.bootstrap.Server;
import com.cisco.dsb.common.executor.DhruvaExecutorService;
import com.cisco.dsb.service.MetricService;
import com.cisco.dsb.sip.jain.JainSipHelper;
import com.cisco.dsb.sip.jain.JainStackInitializer;
import com.cisco.dsb.sip.stack.dto.DhruvaNetwork;
import com.cisco.dsb.transport.Transport;
import java.net.InetAddress;
import java.util.concurrent.CompletableFuture;
import javax.sip.SipFactory;
import javax.sip.SipListener;

public class SipUDPServer implements Server {
  private SipListener sipListener;
  private DhruvaNetwork networkConfig;
  private MetricService metricService;
  private DhruvaExecutorService executorService;

  public SipUDPServer(
      SipListener handler,
      DhruvaExecutorService executorService,
      DhruvaNetwork networkConfig,
      MetricService metricService) {
    this.metricService = metricService;
    this.networkConfig = networkConfig;
    this.sipListener = handler;
    this.executorService = executorService;
  }

  @Override
  public void startListening(
      Transport transportType,
      DhruvaNetwork transportConfig,
      InetAddress address,
      int port,
      SipListener handler,
      CompletableFuture serverStartFuture) {

    SipFactory sipFactory = JainSipHelper.getSipFactory();
    try {
      JainStackInitializer.getSimpleStack(
          sipFactory,
          "gov.nist",
          JainStackInitializer.createProxyStackProperties(),
          address.toString(),
          port,
          transportType.toString(),
          handler);
      serverStartFuture.complete(null);
    } catch (Exception e) {
      serverStartFuture.completeExceptionally(e.getCause());
    }
  }
}
