package com.cisco.dhruva.sip.proxy.handlers;

import com.cisco.dhruva.ProxyService;
import com.cisco.dsb.sip.jain.JainSipHelper;
import com.cisco.dsb.util.log.DhruvaLoggerFactory;
import com.cisco.dsb.util.log.Logger;
import gov.nist.javax.sip.message.SIPRequest;
import javax.sip.RequestEvent;
import javax.sip.SipProvider;

public abstract class RequestEventHandler extends ProxyEventHandler {

  private static Logger logger = DhruvaLoggerFactory.getLogger(RequestEventHandler.class);

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
