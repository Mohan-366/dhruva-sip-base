package com.cisco.dhruva.sip.controller;

import com.cisco.dhruva.sip.proxy.Location;
import com.cisco.dhruva.sip.proxy.ProxySendMessage;
import com.cisco.dsb.common.CommonContext;
import com.cisco.dsb.common.messaging.MessageConvertor;
import com.cisco.dsb.common.messaging.ProxySIPRequest;
import com.cisco.dsb.common.messaging.ProxySIPResponse;
import com.cisco.dsb.config.sip.DhruvaSIPConfigProperties;
import com.cisco.dsb.exception.DhruvaException;
import com.cisco.dsb.util.log.DhruvaLoggerFactory;
import com.cisco.dsb.util.log.Logger;
import gov.nist.javax.sip.message.SIPRequest;
import gov.nist.javax.sip.message.SIPResponse;
import java.util.concurrent.CompletableFuture;
import javax.sip.*;
import javax.sip.message.Request;

public class ProxyController implements ProxyInterface {

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

  public void setController(ProxySIPRequest request) {
    request.getContext().set(CommonContext.PROXY_CONTROLLER, this);
  }

  public void onNewRequest(ProxySIPRequest request) {
    // Create proxu transaction
    // handle request params
    request.getContext().set(CommonContext.PROXY_CONTROLLER, this);
    //    dsipRequestMessage
    //        .getContext()
    //        .set(
    //            CommonContext.APP_MESSAGE_HANDLER,
    //            new AppMessageListener() {
    //              @Override
    //              public void onMessage(DSIPMessage message) {
    //                // Handle the message from App
    //              }
    //            });
    proxyAppAdaptor.handleRequest(request);
  }

  public void respond(ProxySIPResponse proxySIPResponse) throws DhruvaException {

    SIPResponse response =
        MessageConvertor.convertDhruvaResponseMessageToJainSipMessage(proxySIPResponse);
    try {
      (proxySIPResponse).getProvider().sendResponse(response);

    } catch (SipException exception) {
      exception.printStackTrace();
    }
  }

  public void respond(int responseCode, ProxySIPRequest proxySIPRequest) {
    try {
      CompletableFuture<Void> responseResult =
          ProxySendMessage.sendResponse(
              responseCode,
              proxySIPRequest.getProvider(),
              proxySIPRequest.getServerTransaction(),
              proxySIPRequest.getRequest());
    } catch (Exception e) {
      System.out.println(e.getMessage());
    }
  }

  public void proxyTo(ProxySIPRequest proxySIPRequest, Location location) throws SipException {
    SIPRequest request =
        MessageConvertor.convertDhruvaRequestMessageToJainSipMessage(proxySIPRequest);
    if (!((SIPRequest) proxySIPRequest.getSIPMessage()).getMethod().equals(Request.ACK)) {
      ClientTransaction clientTransaction =
          (proxySIPRequest).getProvider().getNewClientTransaction((Request) request.clone());
      clientTransaction.setApplicationData(
          proxySIPRequest.getContext().get(CommonContext.PROXY_CONTROLLER));
      clientTransaction.sendRequest();
    } else {
      (proxySIPRequest).getProvider().sendRequest(request);
    }
  }

  public void onResponse(ProxySIPResponse proxySIPResponse) {
    proxyAppAdaptor.handleResponse(proxySIPResponse);
  }
}
