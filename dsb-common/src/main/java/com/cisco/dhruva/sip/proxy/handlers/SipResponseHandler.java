package com.cisco.dhruva.sip.proxy.handlers;

import com.cisco.dhruva.ProxyService;
import javax.sip.ResponseEvent;

public class SipResponseHandler extends ResponseEventHandler {
  public SipResponseHandler(ProxyService proxyStack, ResponseEvent responseEvent) {
    super(proxyStack, responseEvent);
  }

  @Override
  public void executeRun() throws Exception {}
}
