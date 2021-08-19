package com.cisco.dsb.proxy.handlers;

import com.cisco.dsb.common.executor.BaseHandler;
import com.cisco.dsb.common.service.ProxyService;
import javax.sip.ListeningPoint;
import javax.sip.SipProvider;
import org.springframework.lang.Nullable;

public abstract class ProxyEventHandler extends BaseHandler {

  protected final String callId;

  /** Source listening point in which this request/response was received. */
  protected final ListeningPoint sourceListeningPoint;

  /** Event source in which this request/response was received. */
  protected final SipProvider eventSource;

  protected final ProxyService proxyStack;

  public ProxyEventHandler(ProxyService proxyStack, String callId, SipProvider eventSource) {
    this.proxyStack = proxyStack;
    this.callId = callId;
    this.eventSource = eventSource;
    this.sourceListeningPoint = eventSource.getListeningPoints()[0];
  }

  @Nullable
  public String getCallId() {
    return callId;
  }
}
