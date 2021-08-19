package com.cisco.dsb.proxy.handlers;

import com.cisco.dsb.common.service.ProxyService;
import com.cisco.dsb.common.util.SpringApplicationContext;
import javax.sip.TimeoutEvent;
import reactor.core.publisher.Mono;

public class SipTimeOutHandler extends TimeOutEventHandler {

  public SipTimeOutHandler(ProxyService proxyStack, TimeoutEvent timeoutEvent) {
    super(proxyStack, timeoutEvent);
  }

  @Override
  public void executeRun() {
    SpringApplicationContext.getAppContext()
        .getBean(ProxyService.class)
        .proxyTimeOutHandler()
        .accept(Mono.just(timeoutEvent));
  }
}
