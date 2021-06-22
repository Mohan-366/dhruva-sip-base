package com.cisco.dhruva.sip.proxy.handlers;

import com.cisco.dhruva.ProxyService;
import com.cisco.dsb.common.executor.BaseHandler;
import com.cisco.dsb.util.log.DhruvaLoggerFactory;
import com.cisco.dsb.util.log.Logger;
import javax.sip.ListeningPoint;
import javax.sip.SipProvider;

public abstract class ProxyEventHandler extends BaseHandler {
  private static Logger logger = DhruvaLoggerFactory.getLogger(ProxyEventHandler.class);

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

  public String getCallId() {
    return callId;
  }
}
