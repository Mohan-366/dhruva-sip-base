package com.cisco.dsb.proxy.handlers;

import com.cisco.dsb.proxy.ProxyService;
import javax.sip.SipProvider;
import javax.sip.TimeoutEvent;

public abstract class TimeOutEventHandler extends ProxyEventHandler {

  /** time out event that delivered the request. */
  protected final TimeoutEvent timeoutEvent;

  public TimeOutEventHandler(ProxyService proxyStack, TimeoutEvent timeoutEvent) {
    super(proxyStack, null, (SipProvider) timeoutEvent.getSource());
    this.timeoutEvent = timeoutEvent;
  }
}
