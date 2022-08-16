package com.cisco.dhruva.application;

import com.cisco.dhruva.calltype.DefaultCallType;
import com.cisco.dsb.proxy.ProxyService;
import com.cisco.dsb.proxy.dto.ProxyAppConfig;
import com.cisco.dsb.proxy.messaging.ProxySIPRequest;
import java.util.function.Consumer;
import java.util.function.Supplier;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

@Service
public class DhruvaSampleApp {
  private ProxyService proxyService;
  private ProxyAppConfig proxyAppConfig;
  private DefaultCallType defaultCallType;

  @Autowired
  DhruvaSampleApp(ProxyService proxyService, DefaultCallType defaultCallType) {
    this.proxyService = proxyService;
    this.defaultCallType = defaultCallType;
    init();
  }

  public void init() {
    proxyAppConfig =
        ProxyAppConfig.builder()
            ._1xx(false)
            ._2xx(true)
            ._3xx(true)
            ._4xx(true)
            ._5xx(true)
            ._6xx(true)
            .midDialog(false)
            .isMaintenanceEnabled(isMaintenance)
            .requestConsumer(getRequestConsumer())
            .build();

    proxyService.register(proxyAppConfig);
  }

  private Supplier<Boolean> isMaintenance = () -> false;

  private Consumer<ProxySIPRequest> getRequestConsumer() {
    return proxySIPRequest -> {
      defaultCallType.processRequest(proxySIPRequest);
    };
  }
}
