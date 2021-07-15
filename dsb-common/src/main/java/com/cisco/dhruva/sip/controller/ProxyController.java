package com.cisco.dhruva.sip.controller;

import com.cisco.dhruva.sip.proxy.*;
import com.cisco.dhruva.sip.proxy.errors.InternalProxyErrorException;
import com.cisco.dsb.common.CommonContext;
import com.cisco.dsb.common.messaging.MessageConvertor;
import com.cisco.dsb.common.messaging.ProxySIPRequest;
import com.cisco.dsb.common.messaging.ProxySIPResponse;
import com.cisco.dsb.config.sip.DhruvaSIPConfigProperties;
import com.cisco.dsb.exception.DhruvaException;
import com.cisco.dsb.sip.stack.dto.DhruvaNetwork;
import com.cisco.dsb.util.log.DhruvaLoggerFactory;
import com.cisco.dsb.util.log.Logger;
import gov.nist.javax.sip.message.SIPRequest;
import gov.nist.javax.sip.message.SIPResponse;
import java.util.HashMap;
import java.util.Optional;
import javax.sip.*;
import javax.sip.message.Request;

public class ProxyController implements ControllerInterface, ProxyInterface {

  private ServerTransaction serverTransaction;
  private SipProvider sipProvider;
  private DhruvaSIPConfigProperties dhruvaSIPConfigProperties;
  private AppAdaptorInterface proxyAppAdaptor;
  private ControllerConfig controllerConfig;
  private ProxyTransaction proxyTransaction;
  /** If true, will cancel all branches on CANCEL, 2xx and 6xx responses */
  private boolean cancelBranchesAutomatically = false;

  Logger logger = DhruvaLoggerFactory.getLogger(ProxyController.class);
  /* A mapping of Locations to client transactions used when cancelling */
  protected HashMap locToTransMap = new HashMap(11);
  private ProxySIPRequest ourRequest;
  /* Stores if we are in stateful or stateless mode */
  protected byte stateMode = -1;

  public ProxyController(
      ServerTransaction serverTransaction,
      SipProvider sipProvider,
      DhruvaSIPConfigProperties dhruvaSIPConfigProperties,
      AppAdaptorInterface appAdaptorInterface,
      ControllerConfig controllerConfig) {
    this.serverTransaction = serverTransaction;
    this.sipProvider = sipProvider;
    this.dhruvaSIPConfigProperties = dhruvaSIPConfigProperties;
    this.proxyAppAdaptor = appAdaptorInterface;
    this.controllerConfig = controllerConfig;
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

  public void setController(ProxySIPRequest request) {

    Optional<String> networkFromProvider = DhruvaNetwork.getNetworkFromProvider(sipProvider);
    String network;

    network = networkFromProvider.orElseGet(() -> DhruvaNetwork.getDefault().getName());

    request.getRequest().setApplicationData(DhruvaNetwork.getNetwork(network));
    request.getContext().set(CommonContext.PROXY_CONTROLLER, this);
  }

  public void proxyResponse(ProxySIPResponse proxySIPResponse) {

    /*SIPResponse response =
        MessageConvertor.convertDhruvaResponseMessageToJainSipMessage(proxySIPResponse);
    try {
      proxySIPResponse.getProvider().sendResponse(response);

    } catch (SipException exception) {
      exception.printStackTrace();
    }*/

    Optional<SIPRequest> request = Optional.ofNullable(this.ourRequest.getRequest());
    if (request.isPresent()) {
      SIPRequest req = request.get();
      if ((!req.getMethod().equals(Request.ACK)) && (!req.getMethod().equals(Request.CANCEL))) {
        // Change to statefull if we are stateless
        if (stateMode != ControllerConfig.STATEFUL) {
          overwriteStatelessMode();
        }
        // MEETPASS TODO
        //        if (DsMappedResponseCreator.getInstance() != null) {
        //          response =
        //              DsMappedResponseCreator.getInstance()
        //                  .createresponse(
        //                      incomingNetwork.toString(),
        //                      proxyErrorAggregator.getProxyErrorList(),
        //                      response);
        //        }
        // TODO can be sent using Mono
        ProxyResponseGenerator.sendResponse(proxySIPResponse.getResponse(), proxyTransaction);
      } else {
        logger.warn("in respond() - not forwarding response because request method was ACK");
      }
    } else {
      logger.error(
          "Request is null for response, this should have never come here, as there is"
              + " transaction check before sending to application!!!");
    }
  }

  public boolean overwriteStatelessMode() {
    // TODO check this logic from Dhruva
    return false;
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
      clientTransaction.setApplicationData(proxySIPRequest.getProxyTransaction());
      clientTransaction.sendRequest();
    } else {
      (proxySIPRequest).getProvider().sendRequest(request);
    }
    // proxyTransaction.proxyTo();
  }

  @Override
  public ProxyStatelessTransaction onNewRequest(ServerTransaction server, SIPRequest request) {

    // Create ServerTransaction if not available from Jain.server could be null
    DhruvaNetwork network = (DhruvaNetwork) request.getApplicationData();
    Optional<SipProvider> optionalSipProvider =
        DhruvaNetwork.getProviderFromNetwork(network.getName());
    SipProvider sipProvider;
    if (optionalSipProvider.isPresent()) sipProvider = optionalSipProvider.get();
    else {
      logger.error("provider is not set in request");
      return null;
    }
    if (server == null) {
      try {
        serverTransaction = sipProvider.getNewServerTransaction(request);
      } catch (TransactionAlreadyExistsException | TransactionUnavailableException ex1) {
        logger.error("exception while creating new server transaction in jain" + ex1.getMessage());
        return null;
      }
    }

    // Create proxy transaction.TODO
    ProxyFactoryInterface proxyFactoryInterface = new ProxyFactory();
    try {
      proxyTransaction =
          (ProxyTransaction)
              proxyFactoryInterface.createProxyTransaction(this, null, serverTransaction, request);
    } catch (InternalProxyErrorException ex) {
      logger.error("exception while creating proxy transaction" + ex.getMessage());
      return null;
    }

    serverTransaction.setApplicationData(proxyTransaction);

    return null;
  }

  @Override
  public void onProxySuccess(
      ProxyStatelessTransaction proxy, ProxyCookie cookie, ProxyClientTransaction trans) {}

  @Override
  public void onProxyFailure(
      ProxyStatelessTransaction proxy,
      ProxyCookie cookie,
      int errorCode,
      String errorPhrase,
      Throwable exception) {}

  @Override
  public void onResponseSuccess(ProxyTransaction proxy, ProxyServerTransaction trans) {}

  @Override
  public void onResponseFailure(
      ProxyTransaction proxy,
      ProxyServerTransaction trans,
      int errorCode,
      String errorPhrase,
      Throwable exception) {
    logger.warn(
        "onResponseFailure()- Could not send response , exception" + exception.getMessage());
    /*
    //TODO what action should be taken???
    if (proxyErrorAggregator != null) {
      DsSipResponse response = proxy.getBestResponse();
      proxyErrorAggregator.onResponseFailure(exception, response, errorCode);
    }*/
  }

  @Override
  public void onFailureResponse(
      ProxyTransaction proxy,
      ProxyCookie cookie,
      ProxyClientTransaction trans,
      ProxySIPResponse proxySIPResponse) {
    logger.info("Entering onFailureResponse():");
    // not copying proxyError Aggregator Code, how is this functionality covered???
    // TODO add retry logic once loadbalancer is ready
    // as of now we are sending all response to Application layer
    proxySIPResponse.setToApplication(true);
    logger.info("Leaving onFailureResponse");
  }

  @Override
  public void onRedirectResponse(
      ProxyTransaction proxy,
      ProxyCookie cookie,
      ProxyClientTransaction trans,
      ProxySIPResponse proxySIPResponse) {
    logger.info("Entering onRedirectResponse():");

    proxySIPResponse.setToApplication(true);

    logger.info("Leaving onRedirectResponse()");
  }

  @Override
  public void onSuccessResponse(
      ProxyTransaction proxy,
      ProxyCookie cookie,
      ProxyClientTransaction trans,
      ProxySIPResponse proxySIPResponse) {
    ProxyCookieImpl cookieImpl = (ProxyCookieImpl) cookie;
    Location location = cookieImpl.getLocation();

    /*if (location.getLoadBalancer() != null)
      location.getLoadBalancer().getLastServerTried().onSuccess();
    */
    // Remove the mapping to this location since it is no longer cancellable
    locToTransMap.remove(location);

    // Cancel all outstanding branches if we are supposed to
    if (cancelBranchesAutomatically) {
      proxy.cancel();
      locToTransMap.clear();
    }
    logger.info("Entering onSuccessResponse():");

    proxySIPResponse.setToApplication(true);

    logger.info("Leaving onSuccessResponse():");
  }

  @Override
  public void onGlobalFailureResponse(
      ProxyTransaction proxy,
      ProxyCookie cookie,
      ProxyClientTransaction trans,
      ProxySIPResponse proxySIPResponse) {
    logger.info("Entering onGlobalFailureResponse():");
    Location location = ((ProxyCookieImpl) cookie).getLocation();
    /*
    if (location.getLoadBalancer() != null)
      location.getLoadBalancer().getLastServerTried().onSuccess();
      */
    locToTransMap.remove(location);
    if (cancelBranchesAutomatically) {
      proxy.cancel();
      locToTransMap.clear();
    }
    proxySIPResponse.setToApplication(true);

    logger.info("Leaving onGlobalFailureResponse():");
  }

  @Override
  public void onProvisionalResponse(
      ProxyTransaction proxy,
      ProxyCookie cookie,
      ProxyClientTransaction trans,
      ProxySIPResponse proxySIPResponse) {
    SIPResponse response = proxySIPResponse.getResponse();
    logger.debug("Entering onProvisionalResponse()");

    /*ProxyCookieThing cookieThing = (ProxyCookieThing) cookie;
    AppAdaptorInterface responseIf = cookieThing.getResponseInterface();
    Location location = cookieThing.getLocation();
    //TODO uncomment after loadbalancer impl
    if (location.getLoadBalancer() != null)
      location.getLoadBalancer().getLastServerTried().onSuccess();

    int responseCode = response.getStatusCode();
    if (responseCode != 100) {
      // proxy.respond(response);
      Log.debug("sent " + responseCode + " response ");
    }

    // pass the provisional response back
    if (responseIf != null)
      responseIf.handleResponse(location, Optional.of(response), responseCode);*/
    logger.info("Entering onProvisionalResponse():");

    proxySIPResponse.setToApplication(true);

    logger.info("Leaving onProvisionalResponse()");
  }

  @Override
  public void onBestResponse(ProxyTransaction proxy, ProxySIPResponse proxySIPResponse) {}

  @Override
  public void onRequestTimeOut(
      ProxyTransaction proxy, ProxyCookie cookie, ProxyClientTransaction trans) {}

  @Override
  public void onResponseTimeOut(ProxyTransaction proxy, ProxyServerTransaction trans) {}

  @Override
  public void onICMPError(ProxyTransaction proxy, ProxyServerTransaction trans) {}

  @Override
  public void onICMPError(
      ProxyTransaction proxy, ProxyCookie cookie, ProxyClientTransaction trans) {}

  @Override
  public void onAck(ProxyTransaction proxy, ProxyServerTransaction transaction, SIPRequest ack) {}

  @Override
  public void onCancel(ProxyTransaction proxy, ProxyServerTransaction trans, SIPRequest cancel)
      throws DhruvaException {}

  @Override
  public void onResponse(ProxySIPResponse response) {
    // TODO what to do here???
    /*response.setNormalizationState(
    DsMessageLoggingInterface.SipMsgNormalizationState.POST_NORMALIZED);*/
  }

  @Override
  public ControllerConfig getControllerConfig() {
    return this.controllerConfig;
  }
}
