package com.cisco.dhruva.application;

import com.cisco.dhruva.ProxyService;
import com.cisco.dhruva.application.calltype.CallType;
import com.cisco.dhruva.application.calltype.CallType1;
import com.cisco.dhruva.application.calltype.CallType2;
import com.cisco.dhruva.application.calltype.DefaultCallType;
import com.cisco.dsb.common.messaging.ProxySIPRequest;
import com.cisco.dsb.common.messaging.ProxySIPResponse;
import com.cisco.dsb.util.log.DhruvaLoggerFactory;
import com.cisco.dsb.util.log.Logger;
import java.util.ArrayList;
import java.util.function.Consumer;
import javax.annotation.PostConstruct;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

@Service
public class DhruvaApp {
  Logger logger = DhruvaLoggerFactory.getLogger(ProxyService.class);
  private ArrayList<CallType> callTypes;
  @Autowired ProxyService proxyService;

  @Autowired CallType1 callType1;
  @Autowired CallType2 callType2;
  @Autowired DefaultCallType defaultCallType;
  private Consumer<ProxySIPRequest> requestConsumer =
      proxySIPRequest -> {
        logger.info("-------App: Got SIPMessage->Type:SIPRequest------");
        callTypes.stream()
            .filter(callType -> callType.filter().test(proxySIPRequest))
            .findFirst()
            .orElse(defaultCallType)
            .getSinkRequest()
            .tryEmitNext(proxySIPRequest);
      };

  private Consumer<ProxySIPResponse> responseConsumer =
      dsipResponseMessage -> {
        logger.info(
            "-------App: Got SIPMessage->Type:SIPResponse->CallId {}------",
            dsipResponseMessage.getCallId());
        CallType callType =
            (CallType)
                dsipResponseMessage
                    .getContext()
                    .getOrDefault(dsipResponseMessage.getCallId(), defaultCallType);
        callType.getSinkResponse().tryEmitNext(dsipResponseMessage);
      };

  @PostConstruct
  public void init() {
    // TODO change to single method register(res,req)
    proxyService.registerForRequest(requestConsumer);
    proxyService.registerForResponse(responseConsumer);

    // register for interested CallTypes
    callTypes = new ArrayList<>();
    callTypes.add(callType1);
    callTypes.add(callType2);
    callTypes.add(defaultCallType);
  }
}
