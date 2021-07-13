package com.cisco.dhruva.sip.controller;

import com.cisco.dhruva.sip.proxy.*;
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

import javax.sip.ClientTransaction;
import javax.sip.ServerTransaction;
import javax.sip.SipException;
import javax.sip.SipProvider;
import javax.sip.message.Request;

public class ProxyController implements ControllerInterface , ProxyInterface {

  private ServerTransaction serverTransaction;
  private SipProvider sipProvider;
  private DhruvaSIPConfigProperties dhruvaSIPConfigProperties;
  private AppAdaptorInterface proxyAppAdaptor;
  private ControllerConfig controllerConfig;
  Logger logger = DhruvaLoggerFactory.getLogger(ProxyController.class);

  public ProxyController(
      ServerTransaction serverTransaction,
      SipProvider sipProvider,
      DhruvaSIPConfigProperties dhruvaSIPConfigProperties,
      AppAdaptorInterface appAdaptorInterface, ControllerConfig controllerConfig) {
    this.serverTransaction = serverTransaction;
    this.sipProvider = sipProvider;
    this.dhruvaSIPConfigProperties = dhruvaSIPConfigProperties;
    this.proxyAppAdaptor = appAdaptorInterface;
    this.controllerConfig = controllerConfig;
  }

  public void setController(ProxySIPRequest request) {
    request.getContext().set(CommonContext.PROXY_CONTROLLER, this);
  }
//
//  public void onNewRequest(ProxySIPRequest request) throws DhruvaException {
//    // Create proxy transaction
//    // handle request params
//    // Return ProxySipRequest
//    request.getContext().set(CommonContext.PROXY_CONTROLLER, this);
//    //    dsipRequestMessage
//    //        .getContext()
//    //        .set(
//    //            CommonContext.APP_MESSAGE_HANDLER,
//    //            new AppMessageListener() {
//    //              @Override
//    //              public void onMessage(DSIPMessage message) {
//    //                // Handle the message from App
//    //              }
//    //            });
//    proxyAppAdaptor.handleRequest(request);
//  }


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

  public void onResponse(ProxySIPResponse proxySIPResponse) throws DhruvaException {
    proxyAppAdaptor.handleResponse(proxySIPResponse);
  }


  @Override
  public ProxyStatelessTransaction onNewRequest(ServerTransaction server, SIPRequest request) {
    return null;
  }

  @Override
  public void onProxySuccess(ProxyStatelessTransaction proxy, ProxyCookieInterface cookie, ProxyClientTransaction trans) {

  }

  @Override
  public void onProxyFailure(ProxyStatelessTransaction proxy, ProxyCookieInterface cookie, int errorCode, String errorPhrase, Throwable exception) {

  }

  @Override
  public void onResponseSuccess(ProxyTransaction proxy, ProxyServerTransaction trans) {

  }

  @Override
  public void onResponseFailure(ProxyTransaction proxy, ProxyServerTransaction trans, int errorCode, String errorPhrase, Throwable exception) {

  }

  @Override
  public void onFailureResponse(ProxyTransaction proxy, ProxyCookieInterface cookie, ProxyClientTransaction trans, SIPResponse response) {

  }

  @Override
  public void onRedirectResponse(ProxyTransaction proxy, ProxyCookieInterface cookie, ProxyClientTransaction trans, SIPResponse response) {

  }

  @Override
  public void onSuccessResponse(ProxyTransaction proxy, ProxyCookieInterface cookie, ProxyClientTransaction trans, SIPResponse response) {

  }

  @Override
  public void onGlobalFailureResponse(ProxyTransaction proxy, ProxyCookieInterface cookie, ProxyClientTransaction trans, SIPResponse response) {

  }

  @Override
  public void onProvisionalResponse(ProxyTransaction proxy, ProxyCookieInterface cookie, ProxyClientTransaction trans, SIPResponse response) {

  }

  @Override
  public void onBestResponse(ProxyTransaction proxy, SIPResponse response) {

  }

  @Override
  public void onRequestTimeOut(ProxyTransaction proxy, ProxyCookieInterface cookie, ProxyClientTransaction trans) {

  }

  @Override
  public void onResponseTimeOut(ProxyTransaction proxy, ProxyServerTransaction trans) {

  }

  @Override
  public void onICMPError(ProxyTransaction proxy, ProxyServerTransaction trans) {

  }

  @Override
  public void onICMPError(ProxyTransaction proxy, ProxyCookieInterface cookie, ProxyClientTransaction trans) {

  }

  @Override
  public void onAck(ProxyTransaction proxy, ProxyServerTransaction transaction, SIPRequest ack) {

  }

  @Override
  public void onCancel(ProxyTransaction proxy, ProxyServerTransaction trans, SIPRequest cancel) throws DhruvaException {

  }

  @Override
  public void onResponse(SIPResponse response) {

  }

  @Override
  public ControllerConfig getControllerConfig() {
    return this.controllerConfig;
  }
}
