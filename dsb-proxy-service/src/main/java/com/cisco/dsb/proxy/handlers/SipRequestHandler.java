package com.cisco.dsb.proxy.handlers;

import com.cisco.dsb.proxy.service.ProxyService;
import com.cisco.dsb.util.SpringApplicationContext;
import javax.sip.RequestEvent;
import reactor.core.publisher.Mono;

public class SipRequestHandler extends RequestEventHandler {

  public SipRequestHandler(ProxyService proxyStack, RequestEvent requestEvent) {
    super(proxyStack, requestEvent);
  }

  @Override
  public void executeRun() {

    SpringApplicationContext.getAppContext()
        .getBean(ProxyService.class)
        .proxyRequestHandler()
        .accept(Mono.just(requestEvent));
  }
}
