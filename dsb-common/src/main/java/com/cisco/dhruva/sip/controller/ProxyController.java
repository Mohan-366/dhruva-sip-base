package com.cisco.dhruva.sip.controller;

import com.cisco.dsb.common.messaging.DSIPRequestMessage;
import com.cisco.dsb.config.sip.DhruvaSIPConfigProperties;
import javax.sip.ServerTransaction;
import javax.sip.SipProvider;

public class ProxyController {

  private ServerTransaction serverTransaction;
  private SipProvider sipProvider;
  private DhruvaSIPConfigProperties dhruvaSIPConfigProperties;
  private AppAdaptorInterface proxyAppAdaptor;

  public ProxyController(
      ServerTransaction serverTransaction,
      SipProvider sipProvider,
      DhruvaSIPConfigProperties dhruvaSIPConfigProperties,
      AppAdaptorInterface appAdaptorInterface) {
    this.serverTransaction = serverTransaction;
    this.sipProvider = sipProvider;
    this.dhruvaSIPConfigProperties = dhruvaSIPConfigProperties;
    this.proxyAppAdaptor = appAdaptorInterface;
  }

  public void onNewRequest(DSIPRequestMessage dsipRequestMessage) {
    proxyAppAdaptor.handleRequest(dsipRequestMessage);
  }
}
