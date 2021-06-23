package com.cisco.dhruva.sip.proxy.handlers;

import com.cisco.dhruva.ProxyService;
import com.cisco.dhruva.sip.proxy.ProxyUtils;
import com.cisco.dsb.sip.jain.JainSipHelper;
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
