package com.cisco.dsb.proxy.bootstrap;

import com.cisco.dsb.common.executor.DhruvaExecutorService;
import com.cisco.dsb.common.service.MetricService;
import com.cisco.dsb.common.sip.stack.dto.DhruvaNetwork;
import com.cisco.dsb.common.transport.Transport;
import com.cisco.dsb.proxy.bootstrap.proxyserver.SipUDPServer;
import javax.sip.SipListener;

public class ServerFactory {

  private static ServerFactory serverFactory = new ServerFactory();

  public static ServerFactory newInstance() {
    serverFactory = new ServerFactory();
    return serverFactory;
  }

  public static ServerFactory getInstance() {
    return serverFactory;
  }

  public Server getServer(
      Transport transport,
      SipListener handler,
      DhruvaNetwork networkConfig,
      DhruvaExecutorService executorService,
      MetricService metricService)
      throws Exception {
    switch (transport) {
      case UDP:
        Server udpServer = new SipUDPServer(handler, executorService, networkConfig, metricService);
        return udpServer;

      default:
        throw new Exception("Transport " + transport.name() + " not supported");
    }
  }
}
