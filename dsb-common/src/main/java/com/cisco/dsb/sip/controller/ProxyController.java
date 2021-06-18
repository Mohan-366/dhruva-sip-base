package com.cisco.dsb.sip.controller;

import com.cisco.dsb.config.sip.DhruvaSIPConfigProperties;
import javax.sip.ServerTransaction;
import javax.sip.SipProvider;

public class ProxyController {

  private ServerTransaction serverTransaction;
  private SipProvider sipProvider;
  private DhruvaSIPConfigProperties dhruvaSIPConfigProperties;

  public ProxyController(
      ServerTransaction serverTransaction,
      SipProvider sipProvider,
      DhruvaSIPConfigProperties dhruvaSIPConfigProperties) {
    this.serverTransaction = serverTransaction;
    this.sipProvider = sipProvider;
    this.dhruvaSIPConfigProperties = dhruvaSIPConfigProperties;
  }
}