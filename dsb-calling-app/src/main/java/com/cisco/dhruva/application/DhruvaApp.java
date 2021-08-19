package com.cisco.dhruva.application;

import com.cisco.dhruva.application.calltype.CallType;
import com.cisco.dhruva.application.calltype.DefaultCallType;
import com.cisco.dsb.common.util.log.DhruvaLoggerFactory;
import com.cisco.dsb.common.util.log.Logger;
import com.cisco.dsb.proxy.ProxyService;
import com.cisco.dsb.proxy.dto.ProxyAppConfig;
import com.cisco.dsb.proxy.messaging.ProxySIPRequest;
import com.cisco.dsb.proxy.messaging.ProxySIPResponse;
import java.util.ArrayList;
import java.util.function.Consumer;
import javax.annotation.PostConstruct;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

@Service
public class DhruvaApp {
  Logger logger = DhruvaLoggerFactory.getLogger(ProxyService.class);
  private ArrayList<CallType> callTypes;
  @Autowired ProxyService proxyService;

  @Autowired DefaultCallType defaultCallType;
  private Consumer<ProxySIPRequest> requestConsumer =
      proxySIPRequest -> {
        logger.info("-------App: Got SIPMessage->Type:SIPRequest------");
        // TODO Can we do this in 'for loop' as it's faster for small amount of iteration
        callTypes.stream()
            .filter(callType -> callType.filter().test(proxySIPRequest))
            .findFirst()
            .orElse(defaultCallType)
            .processRequest()
            .accept(Mono.just(proxySIPRequest));
      };

  private Consumer<ProxySIPResponse> responseConsumer =
      proxySIPResponse -> {
        logger.info(
            "-------App: Got SIPMessage->Type:SIPResponse->CallId {}------",
            proxySIPResponse.getCallId());
        CallType callType =
            (CallType)
                proxySIPResponse
                    .getContext()
                    .getOrDefault(proxySIPResponse.getCallId(), defaultCallType);
        callType.processResponse().accept(Mono.just(proxySIPResponse));
      };

  @PostConstruct
  public void init() {
    // TODO change to single method register(res,req)
    ProxyAppConfig appConfig =
        ProxyAppConfig.builder()
            ._2xx(true)
            ._4xx(true)
            .requestConsumer(requestConsumer)
            .responseConsumer(responseConsumer)
            .build();
    proxyService.register(appConfig);

    // register for interested CallTypes
    callTypes = new ArrayList<>();
    callTypes.add(defaultCallType);
  }
}
