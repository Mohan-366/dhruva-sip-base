package com.cisco.dsb.proxy.handlers;

import com.cisco.dsb.common.sip.jain.JainSipHelper;
import com.cisco.dsb.proxy.ProxyService;
import gov.nist.javax.sip.message.SIPRequest;
import javax.sip.RequestEvent;
import javax.sip.SipProvider;

public abstract class RequestEventHandler extends ProxyEventHandler {

  /** Request that was received. */
  protected final SIPRequest receivedRequest;

  /** Request event that delivered the request. */
  protected final RequestEvent requestEvent;

  public RequestEventHandler(ProxyService proxyStack, RequestEvent requestEvent) {
    super(
        proxyStack,
        JainSipHelper.getCallId(requestEvent.getRequest()),
        (SipProvider) requestEvent.getSource());
    this.receivedRequest = (SIPRequest) requestEvent.getRequest();
    this.requestEvent = requestEvent;
  }
}
