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

  public void proxyResponse(ProxySIPResponse proxySIPResponse) throws DhruvaException {

    SIPResponse response =
        MessageConvertor.convertDhruvaResponseMessageToJainSipMessage(proxySIPResponse);
    try {
      proxySIPResponse.getProvider().sendResponse(response);

    } catch (SipException exception) {
      exception.printStackTrace();
    }
  }

  public void respond(int responseCode, ProxySIPRequest proxySIPRequest) {

    ProxySendMessage.sendResponse(
            responseCode,
            proxySIPRequest.getProvider(),
            proxySIPRequest.getServerTransaction(),
            proxySIPRequest.getRequest())
        .subscribe(
            req -> {},
            err -> {
              // Handle exception
            });
  }

  public void proxyRequest(ProxySIPRequest proxySIPRequest, Location location) throws SipException {

    // Mono.just(proxySIPRequest).subscribe();

    // ## Sending out the Request
    // process the Location object - validate Location
    // Trunk Service - getNextElement
    // Client transaction - proxy -
    // Client transaction - stack
    // post processing - Addition Via , Route  , Record-Route
    // Send the packet out using stack interface

    // Proxy Error , returns failure
    // Stack return error - (transaction processing)
    // IO Exception - Transport errors

    // Response
    // 200, 180
    // Flip record route
    // Find the right server transaction - other leg
    //

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
}
