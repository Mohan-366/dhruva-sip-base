package com.cisco.dhruva.application;

import com.cisco.dsb.common.maintanence.Maintenance;
import com.cisco.dsb.proxy.ProxyService;
import com.cisco.dsb.proxy.dto.ProxyAppConfig;
import com.cisco.dsb.proxy.messaging.ProxySIPRequest;
import com.cisco.dsb.proxy.sip.ProxyInterface;
import java.util.function.Consumer;
import java.util.function.Supplier;
import javax.annotation.PostConstruct;
import javax.sip.message.Response;
import lombok.CustomLog;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationContext;
import org.springframework.stereotype.Service;

@Service
@CustomLog
public class DhruvaApp {
  @Autowired ProxyService proxyService;
  @Autowired private ApplicationContext appContext;

  private Supplier<Maintenance> maintenance = () -> Maintenance.MaintenanceBuilder().build();

  private Consumer<ProxySIPRequest> requestConsumer =
      proxySIPRequest -> {
        logger.info("-------App: Got SIPMessage->Type:SIPRequest------");
        // TODO Can we do this in 'for loop' as it's faster for small amount of iteration
        proxySIPRequest.setOutgoingNetwork("SampleNetwork");
        ProxyInterface proxyInterface = proxySIPRequest.getProxyInterface();

        proxyInterface
            .proxyRequest(proxySIPRequest)
            .whenComplete(
                (proxySIPResponse, throwable) -> {
                  if (proxySIPResponse != null) {
                    proxySIPResponse.proxy();
                    return;
                  }
                  if (throwable != null) {
                    logger.error("Error while sending out request", throwable);
                    proxySIPRequest.reject(Response.SERVER_INTERNAL_ERROR, throwable.getMessage());
                  }
                });
      };

  @PostConstruct
  public void init() {
    // TODO change to single method register(res,req)
    ProxyAppConfig appConfig =
        ProxyAppConfig.builder()
            ._2xx(true)
            ._4xx(true)
            .maintenance(maintenance)
            .requestConsumer(requestConsumer)
            .build();
    try {
      proxyService.init();
    } catch (Exception e) {
      logger.error("Unable to initialize proxy, exiting!!!", e);
      System.exit(-1);
    }
    proxyService.register(appConfig);
  }
}
