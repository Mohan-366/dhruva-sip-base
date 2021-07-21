package com.cisco.dhruva.sip.proxy.handlers;

import com.cisco.dhruva.ProxyService;
import com.cisco.dsb.util.SpringApplicationContext;
import javax.sip.ResponseEvent;
import reactor.core.publisher.Mono;

public class SipResponseHandler extends ResponseEventHandler {

  public SipResponseHandler(ProxyService proxyStack, ResponseEvent responseEvent) {
    super(proxyStack, responseEvent);
  }

  @Override
  public void executeRun() {
    SpringApplicationContext.getAppContext()
        .getBean(ProxyService.class)
        .proxyResponseHandler()
        .accept(Mono.just(responseEvent));
  }
}
