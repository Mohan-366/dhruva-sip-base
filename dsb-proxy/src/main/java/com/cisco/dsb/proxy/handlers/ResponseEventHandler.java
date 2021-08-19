package com.cisco.dsb.proxy.handlers;

import com.cisco.dsb.common.sip.jain.JainSipHelper;
import com.cisco.dsb.proxy.ProxyService;
import com.cisco.dsb.proxy.sip.ProxyUtils;
import gov.nist.javax.sip.message.SIPResponse;
import javax.sip.ResponseEvent;
import javax.sip.SipProvider;

public abstract class ResponseEventHandler extends ProxyEventHandler {

  // SIP response
  protected final SIPResponse response;

  protected final String cSeq;

  protected ResponseEvent responseEvent;

  public ResponseEventHandler(ProxyService proxyStack, ResponseEvent responseEvent) {
    super(
        proxyStack,
        JainSipHelper.getCallId(responseEvent.getResponse()),
        (SipProvider) responseEvent.getSource());
    this.responseEvent = responseEvent;
    this.response = (SIPResponse) responseEvent.getResponse();
    cSeq = ProxyUtils.getCseqNumber(response);
  }
}
