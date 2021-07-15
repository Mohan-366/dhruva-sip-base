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
import com.cisco.dsb.transport.Transport;
import com.cisco.dsb.util.log.DhruvaLoggerFactory;
import com.cisco.dsb.util.log.Logger;
import gov.nist.javax.sip.header.ViaList;
import gov.nist.javax.sip.message.SIPRequest;
import gov.nist.javax.sip.message.SIPResponse;
import java.util.HashMap;
import java.text.ParseException;
import java.util.Objects;
import java.util.Optional;
import javax.sip.*;
import javax.sip.header.ViaHeader;
import javax.sip.message.Request;
import javax.sip.message.Response;

public class ProxyController implements ControllerInterface, ProxyInterface {

  private ServerTransaction serverTransaction;
  private SipProvider sipProvider;
  private DhruvaSIPConfigProperties dhruvaSIPConfigProperties;
  private ProxyFactory proxyFactory;
  private ControllerConfig controllerConfig;
  /** If true, will cancel all branches on CANCEL, 2xx and 6xx responses */
  private boolean cancelBranchesAutomatically = false;
  private ProxyStatelessTransaction proxyTransaction;
  /* Stores the request for this controller */
  private ProxySIPRequest ourRequest;
  /* Stores the original request as a clone */
  protected SIPRequest originalRequest;
  /* Stores the request with pre-normalization and xcl processing applied */
  protected SIPRequest preprocessedRequest;

  Logger logger = DhruvaLoggerFactory.getLogger(ProxyController.class);
  /* A mapping of Locations to client transactions used when cancelling */
  protected HashMap locToTransMap = new HashMap(11);

  /* Stores if we are in stateful or stateless mode */
  protected byte stateMode = -1;

  public ProxyController(
      ServerTransaction serverTransaction,
      SipProvider sipProvider,
      DhruvaSIPConfigProperties dhruvaSIPConfigProperties,
      ProxyFactory proxyFactory,
      ControllerConfig controllerConfig) {
    this.serverTransaction = serverTransaction;
    this.sipProvider = sipProvider;
    this.dhruvaSIPConfigProperties = dhruvaSIPConfigProperties;
    this.proxyFactory = proxyFactory;
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
        ProxyResponseGenerator.sendResponse(proxySIPResponse.getResponse(), (ProxyTransaction) proxyTransaction);
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
    // convert proxyStatelessTransaction to proxyTransaction
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

  //
  //  Consumer<Mono<ProxySIPRequest>> proyto = (proxySIPRequestMono) -> {
  //      proxySIPRequestMono.
  //  }

  //  Function<ProxySIPRequest, Mono<ProxySIPRequest>> proxyTo = (proxySIPRequest) -> {
  //    return Mono.just(proxySIPRequest).;
  //  };

  public void proxyRequest(ProxySIPRequest proxySIPRequest, Location location) throws SipException {
    // StepVerifier.create(proxyTo.apply(proxySIPRequest)).ex
    // proxyTo.apply(proxySIPRequest).subscribe((val) -> {},(err) -> {}
    // );
    // ConcatMap
    // Mono.just(proxySIPRequest).subscribe();
    // ## Sending out the Request
    // Clone the request
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

    // Mono.just(proxySIPRequest).mapNotNull(processlocation).flatMap(TrunkService::process).map(location1 -> proxyTransaction.proxyTo(proxySIPRequest, location)).subscribe(()-<{}, ()=>{})

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
  public ProxySIPRequest onNewRequest(ProxySIPRequest proxySIPRequest) {

    SIPRequest request = proxySIPRequest.getRequest();
    // Create ServerTransaction if not available from Jain.server could be null
    //    DhruvaNetwork network = (DhruvaNetwork) request.getApplicationData();
    //    Optional<SipProvider> optionalSipProvider =
    //        DhruvaNetwork.getProviderFromNetwork(network.getName());
    //    SipProvider sipProvider;
    //    if (optionalSipProvider.isPresent()) sipProvider = optionalSipProvider.get();
    //    else {
    //      logger.error("provider is not set in request");
    //      return null;
    //    }

    Optional<ServerTransaction> optionalServerTransaction =
        checkServerTransaction(sipProvider, request, serverTransaction);
    if (optionalServerTransaction.isPresent()) {
      serverTransaction = optionalServerTransaction.get();
    } else logger.info("server transaction not created for request" + request.getMethod());

    // Create ProxyTransaction
    // ProxyTransaction internally creates ProxyServerTransaction
    proxyTransaction =
        createProxyTransaction(
            controllerConfig.isStateful(), request, serverTransaction, proxyFactory);
    // Fetch the network from provider
    SipProvider sipProvider = proxySIPRequest.getProvider();
    Optional<String> networkFromProvider = DhruvaNetwork.getNetworkFromProvider(sipProvider);
    String network;
    network = networkFromProvider.orElseGet(() -> DhruvaNetwork.getDefault().getName());

    proxySIPRequest.setProxyController(this);
    proxySIPRequest.setNetwork(network);
    proxySIPRequest.setMidCall(ProxyUtils.isMidDialogRequest(request));
    // Set the proxyTransaction in jain server transaction for future reference
    serverTransaction.setApplicationData(proxyTransaction);
    return proxySIPRequest;
  }

  /**
   * Creates a <CODE>DsProxyStatelessTransaction</CODE> object if the proxy is configured to be
   * stateless. Otherwise if either the proxy is configured to be stateful or if the controller
   * decides that the current transaction should be stateful , it creates the <CODE>
   * DsProxyTransaction</CODE> object. This method can only be used to create a transaction if one
   * has not been created yet.
   *
   * @param setStateful Indicates that the current transaction be stateful,irrespective of the
   *     controller configuration.
   * @param request The request that will be used to create the transaction
   */
  public ProxyStatelessTransaction createProxyTransaction(
      boolean setStateful,
      SIPRequest request,
      ServerTransaction serverTrans,
      ProxyFactory proxyFactory) {
    Objects.requireNonNull(request);
    Objects.requireNonNull(proxyFactory);

    if (proxyTransaction == null) {
      DhruvaNetwork network = (DhruvaNetwork) request.getApplicationData();

      if (setStateful || (network.getTransport() == Transport.TCP)) {
        try {
          proxyTransaction =
              (ProxyTransaction)
                  proxyFactory.proxyTransaction().apply(this, null, serverTrans, request);
        } catch (InternalProxyErrorException ex) {
          logger.error("exception while creating proxy transaction" + ex.getMessage());
          return null;
        }
      } else {
        try {
          proxyTransaction = new ProxyStatelessTransaction(this, null, request);
        } catch (InternalProxyErrorException dse) {
          sendFailureResponse(request, Response.SERVER_INTERNAL_ERROR);
          return null;
        }
        logger.debug("Created stateless proxy transaction ");
      }
    }
    return proxyTransaction;
  }

  /**
   * Creates a new ServerTransaction object that will handle the request if necessary and if request
   * type is to be handled by transactions.
   *
   * @param sipProvider SipProvider object
   * @param request Incoming request
   * @param st ServerTransaction that was retrieved from RequestEvent
   * @return
   */
  private Optional<ServerTransaction> checkServerTransaction(
      SipProvider sipProvider, Request request, ServerTransaction st) {
    ServerTransaction serverTransaction = st;

    // ACKs are not handled by transactions
    if (controllerConfig.isStateful()
        && serverTransaction == null
        && !request.getMethod().equals(Request.ACK)) {
      try {
        serverTransaction = sipProvider.getNewServerTransaction(request);
      } catch (TransactionAlreadyExistsException | TransactionUnavailableException ex) {
        logger.warn("exception while creating new server transaction in jain" + ex.getMessage());
        return Optional.empty();
      }
    }

    return Optional.ofNullable(serverTransaction);
  }

  /*
   * Overwrites a stateful DsProxyTransaction with a DsStatelessProxy transaction.
   */
  public boolean overwriteStatelessMode(SIPRequest request) {

    // Set it to null if it is stateless
    if (proxyTransaction != null && !(proxyTransaction instanceof ProxyTransaction)) {
      proxyTransaction = null;
    }

    logger.debug("Changing stateless proxy transaction to a stateful one");

    ViaList vias = request.getViaHeaders();
    if (null != vias) {
      ViaHeader topvia = (ViaHeader) vias.getFirst();
      if (controllerConfig.recognize(
          null, topvia.getHost(), topvia.getPort(), Transport.valueOf(topvia.getTransport()))) {
        logger.debug(
            "Removing the top via since its our own and we are trying to respond in stateless mode");
        vias.removeFirst();
      }
    }

    // Create a stateful proxy
    createProxyTransaction(true, request, serverTransaction);

    return !(proxyTransaction == null);
  }

  /**
   * Creates a <CODE>DsProxyStatelessTransaction</CODE> object if the proxy is configured to be
   * stateless. Otherwise if either the proxy is configured to be stateful or if the controller
   * decides that the current transaction should be stateful , it creates the <CODE>
   * DsProxyTransaction</CODE> object. This method can only be used to create a transaction if one
   * has not been created yet.
   *
   * @param setStateful Indicates that the current transaction be stateful,irrespective of the
   *     controller configuration.
   */
  protected void createProxyTransaction(
      boolean setStateful, SIPRequest request, ServerTransaction serverTrans) {

    createProxyTransaction(setStateful, request, serverTrans, proxyFactory);
  }

  /* Attempts to change to stateful mode to send are response with the given response
   * code.
   * @param responseCode The response code of the response to send upstream.
   * @returns True if it could change to stateful mode, false if we couldn't
   */
  protected boolean changeToStatefulForResponse(SIPRequest request, int responseCode) {
    // Make sure we are stateful before sending the response
    boolean success = overwriteStatelessMode(request);
    if (!success) {
      // Just drop it, and log the event
      logger.warn("Unable to change state to send " + responseCode + ", dropping the response");
    }

    return success;
  }

  /*
   * Sends a 404 or 500 response.
   */
  protected void sendFailureResponse(SIPRequest request, int errorResponseCode) {

    if (errorResponseCode == Response.SERVER_INTERNAL_ERROR) {
      if (changeToStatefulForResponse(request, Response.SERVER_INTERNAL_ERROR)) {
        try {
          ProxyResponseGenerator.sendServerInternalErrorResponse(
              request, (ProxyTransaction) proxyTransaction);
        } catch (DhruvaException | ParseException e) {
          logger.error("Error encountered while sending internal error response", e);
        }
        // failureResponseSent = true;
      }
    } else if (errorResponseCode == Response.NOT_FOUND) {
      if (changeToStatefulForResponse(request, Response.NOT_FOUND)) {
        try {
          ProxyResponseGenerator.sendNotFoundResponse(request, (ProxyTransaction) proxyTransaction);
        } catch (DhruvaException | ParseException e) {
          // Warn Logging
          logger.error("Unable to create not found response", e);
        }
        // failureResponseSent = true;
      }
    }
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
