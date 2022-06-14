package com.cisco.dhruva.application;

import com.cisco.dhruva.application.calltype.CallType;
import com.cisco.dhruva.application.calltype.CallTypeEnum;
import com.cisco.dhruva.application.exceptions.FilterTreeException;
import com.cisco.dhruva.application.exceptions.InvalidCallTypeException;
import com.cisco.dhruva.application.filters.Filter;
import com.cisco.dsb.common.util.log.event.*;
import com.cisco.dsb.proxy.ProxyService;
import com.cisco.dsb.proxy.ProxyState;
import com.cisco.dsb.proxy.dto.ProxyAppConfig;
import com.cisco.dsb.proxy.messaging.ProxySIPRequest;
import com.cisco.wx2.util.Utilities;
import com.google.common.collect.ImmutableList;
import java.util.function.Consumer;
import javax.sip.message.Response;
import lombok.CustomLog;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

@Service
@CustomLog
public class DhruvaCallingApp {
  private ProxyService proxyService;
  private Filter filter;
  private ProxyAppConfig proxyAppConfig;
  private EventingService eventingService;

  @Autowired
  DhruvaCallingApp(ProxyService proxyService, Filter filter, EventingService eventingService) {
    this.proxyService = proxyService;
    this.filter = filter;
    this.eventingService = eventingService;
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
            .requestConsumer(getRequestConsumer())
            .build();

    ImmutableList<CallTypeEnum> interestedCallTypes =
        ImmutableList.of(
            CallTypeEnum.DIAL_IN_PSTN,
            CallTypeEnum.DIAL_IN_B2B,
            CallTypeEnum.DIAL_OUT_WXC,
            CallTypeEnum.DIAL_OUT_B2B);

    try {
      filter.register(interestedCallTypes);
    } catch (FilterTreeException e) {
      logger.error("Unable to add calltype to filter tree, exiting!!!", e);
      System.exit(-1);
    }
    proxyService.register(proxyAppConfig);

    // register events
    ImmutableList<Class<? extends DhruvaEvent>> interestedEvents =
        ImmutableList.of(LoggingEvent.class);
    eventingService.register(interestedEvents);
  }

  private Consumer<ProxySIPRequest> getRequestConsumer() {
    return proxySIPRequest -> {
      try {
        CallType callType = this.filter.filter(proxySIPRequest);
        Utilities.Checks checks = new Utilities.Checks();
        checks.add("app request process consumer", callType.toString());
        proxySIPRequest.getAppRecord().add(ProxyState.IN_PROXY_APP_RECEIVED, checks);
        callType.processRequest(proxySIPRequest);
      } catch (InvalidCallTypeException ie) {
        Utilities.Checks checks = new Utilities.Checks();
        checks.add("call type process request", ie.getMessage());
        proxySIPRequest.getAppRecord().add(ProxyState.IN_PROXY_APP_PROCESSING_FAILED, checks);
        logger.error(
            "Unable to find the calltype for request callid:{}, rejecting with 404",
            proxySIPRequest.getCallId(),
            ie);
        proxySIPRequest.reject(Response.NOT_FOUND);
      }
    };
  }
}
