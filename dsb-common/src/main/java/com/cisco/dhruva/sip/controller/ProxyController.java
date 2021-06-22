package com.cisco.dhruva.sip.controller;

import com.cisco.dsb.common.CommonContext;
import com.cisco.dsb.common.messaging.DSIPMessage;
import com.cisco.dsb.common.messaging.DSIPRequestMessage;
import com.cisco.dsb.common.messaging.DSIPResponseMessage;
import com.cisco.dsb.config.sip.DhruvaSIPConfigProperties;
import com.cisco.dsb.util.log.DhruvaLoggerFactory;
import com.cisco.dsb.util.log.Logger;
import javax.sip.ServerTransaction;
import javax.sip.SipProvider;

public class ProxyController {

  private ServerTransaction serverTransaction;
  private SipProvider sipProvider;
  private DhruvaSIPConfigProperties dhruvaSIPConfigProperties;
  private AppAdaptorInterface proxyAppAdaptor;
  Logger logger = DhruvaLoggerFactory.getLogger(ProxyController.class);

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
    dsipRequestMessage.getContext().set(CommonContext.PROXY_CONTROLLER, this);
    dsipRequestMessage
        .getContext()
        .set(
            CommonContext.APP_MESSAGE_HANDLER,
            new AppMessageListener() {
              @Override
              public void onMessage(DSIPMessage message) {
                // Handle the message from App
              }
            });
    proxyAppAdaptor.handleRequest(dsipRequestMessage);
  }

  public void onResponse(DSIPResponseMessage dsipResponseMessage){
      proxyAppAdaptor.handleResponse(dsipResponseMessage);
  }
}
