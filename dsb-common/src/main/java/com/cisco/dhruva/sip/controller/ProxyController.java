package com.cisco.dhruva.sip.controller;

import com.cisco.dhruva.sip.controller.util.ParseProxyParamUtil;
import com.cisco.dhruva.sip.proxy.*;
import com.cisco.dhruva.sip.proxy.errors.InternalProxyErrorException;
import com.cisco.dsb.common.CommonContext;
import com.cisco.dsb.common.messaging.ProxySIPRequest;
import com.cisco.dsb.common.messaging.ProxySIPResponse;
import com.cisco.dsb.config.sip.DhruvaSIPConfigProperties;
import com.cisco.dsb.exception.DhruvaException;
import com.cisco.dsb.sip.stack.dto.DhruvaNetwork;
import com.cisco.dsb.sip.util.ReConstants;
import com.cisco.dsb.transport.Transport;
import com.cisco.dsb.util.log.DhruvaLoggerFactory;
import com.cisco.dsb.util.log.Logger;
import gov.nist.javax.sip.header.Route;
import gov.nist.javax.sip.header.RouteList;
import gov.nist.javax.sip.header.ViaList;
import gov.nist.javax.sip.message.SIPRequest;
import gov.nist.javax.sip.message.SIPResponse;
import java.text.ParseException;
import java.util.*;
import java.util.HashMap;
import javax.sip.*;
import javax.sip.address.SipURI;
import javax.sip.address.URI;
import javax.sip.header.ViaHeader;
import javax.sip.message.Request;
import javax.sip.message.Response;
import lombok.Getter;
import lombok.NonNull;
import lombok.Setter;
import reactor.core.publisher.Mono;

public class ProxyController implements ControllerInterface, ProxyInterface {

  private ServerTransaction serverTransaction;
  private SipProvider sipProvider;
  private DhruvaSIPConfigProperties dhruvaSIPConfigProperties;
  private ProxyFactory proxyFactory;

  @Getter @Setter private ControllerConfig controllerConfig;
  @Getter @Setter private ProxyStatelessTransaction proxyTransaction;
  /* Stores the request for this controller */
  @Getter @Setter protected SIPRequest ourRequest;

  @Getter @Setter private String incomingNetwork;
  /* Stores the original request as a clone */
  @Getter @Setter protected SIPRequest originalRequest;
  /* Stores the request with pre-normalization and xcl processing applied */
  @Getter @Setter protected SIPRequest preprocessedRequest;

  @Getter @Setter protected int timeToTry = 32;
  /** If true, will cancel all branches on CANCEL, 2xx and 6xx respnses */
  @Getter @Setter protected boolean cancelBranchesAutomatically = false;

  protected ArrayList unCancelledBranches = new ArrayList(3);

  public HashMap<Integer, Map<String, String>> parsedProxyParamsByType = null;

  // Order in which the transport is selected.
  private static final Transport[] Transports = {Transport.TLS, Transport.TCP, Transport.UDP};

  Logger logger = DhruvaLoggerFactory.getLogger(ProxyController.class);
  /* A mapping of Locations to client transactions used when cancelling */
  protected HashMap locToTransMap = new HashMap(11);

  /* Stores if we are in stateful or stateless mode */
  protected byte stateMode = -1;

  public ProxyController(
      ServerTransaction serverTransaction,
      @NonNull SipProvider sipProvider,
      @NonNull DhruvaSIPConfigProperties dhruvaSIPConfigProperties,
      @NonNull ProxyFactory proxyFactory,
      @NonNull ControllerConfig controllerConfig) {
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

    // Optional<String> networkFromProvider = DhruvaNetwork.getNetworkFromProvider(sipProvider);
    // String network;

    // network = networkFromProvider.orElseGet(() -> DhruvaNetwork.getDefault().getName());

    // request.getRequest().setApplicationData(DhruvaNetwork.getNetwork(network));
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
    if (ourRequest == null) {
      SIPRequest req = ourRequest;
      if ((!req.getMethod().equals(Request.ACK)) && (!req.getMethod().equals(Request.CANCEL))) {
        // Change to statefull if we are stateless
        if (stateMode != ControllerConfig.STATEFUL) {
          overwriteStatelessMode(req);
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
        ProxyResponseGenerator.sendResponse(
            proxySIPResponse.getResponse(), (ProxyTransaction) proxyTransaction);
      } else {
        logger.warn("in respond() - not forwarding response because request method was ACK");
      }
    } else {
      logger.error(
          "Request is null for response, this should have never come here, as there is"
              + " transaction check before sending to application!!!");
    }
  }

  public void respond(@NonNull int responseCode, @NonNull ProxySIPRequest proxySIPRequest) {

    ProxySendMessage.sendResponseAsync(
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

  public void proxyRequest(@NonNull ProxySIPRequest proxySIPRequest, @NonNull Location location)
      throws SipException {
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
    // SIPRequest request = (SIPRequest) proxySIPRequest.getRequest().clone();

    proxySIPRequest.setLocation(location);
    proxyTo(location, proxySIPRequest, timeToTry);

    //    SIPRequest request =
    //        MessageConvertor.convertDhruvaRequestMessageToJainSipMessage(proxySIPRequest);
    //    if (!((SIPRequest) proxySIPRequest.getSIPMessage()).getMethod().equals(Request.ACK)) {
    //      ClientTransaction clientTransaction =
    //          (proxySIPRequest).getProvider().getNewClientTransaction((Request) request.clone());
    //      clientTransaction.setApplicationData(
    //          proxySIPRequest.getContext().get(CommonContext.PROXY_CONTROLLER));
    //      clientTransaction.sendRequest();
    //    } else {
    //      (proxySIPRequest).getProvider().sendRequest(request);
    //    }

    // proxyTransaction.proxyTo();
  }

  public void proxyTo(Location location, ProxySIPRequest proxySIPRequest, long timeToTry) {

    preprocessedRequest = (SIPRequest) proxySIPRequest.getRequest().clone();
    proxySIPRequest.setClonedRequest((SIPRequest) preprocessedRequest.clone());

    // processLocation.apply(proxySIPRequest).subscribe();
    proxyForwardRequest(location, proxySIPRequest, timeToTry);
  }

  // Always access the cloned request
  private void proxyForwardRequest(
      Location location, ProxySIPRequest proxySIPRequest, long timeToTry) {
    SIPRequest request = proxySIPRequest.getClonedRequest();

    logger.debug("Entering proxyTo Location: " + location);
    logger.debug("timeout for proxyTo() is: " + timeToTry);

    DhruvaNetwork network;
    RouteList routeHeaders = location.getRouteHeaders();
    if (routeHeaders != null && location.getLoadBalancer() == null) {
      List<Route> routeList = routeHeaders.getHeaderList();
      for (Route r : routeList) {
        try {
          request.addLast(r);
        } catch (SipException e) {
          logger.error("exception while adding route header from location to request");
        }
      }
    }

    ProxyCookieImpl cookie = new ProxyCookieImpl(location, request);

    // Get Static Server Group
    // If static is null get Dynamic server

    // retrieve network information based on location and servergroup
    String serverGroup = null;
    network = getNetwork(location, serverGroup); // TODO DSB, SG integration

    if (network != null) {
      proxySIPRequest.setOutgoingNetwork(network.getName());

      //    SIPRequest request =
      //        MessageConvertor.convertDhruvaRequestMessageToJainSipMessage(proxySIPRequest);
      //    if (!((SIPRequest) proxySIPRequest.getSIPMessage()).getMethod().equals(Request.ACK)) {
      //      ClientTransaction clientTransaction =
      //          (proxySIPRequest).getProvider().getNewClientTransaction((Request)
      // request.clone());
      //      clientTransaction.setApplicationData(proxySIPRequest.getProxyTransaction());
      //      clientTransaction.sendRequest();

    } else {
      logger.warn("Could not find the network to set to the request");
      // until then we need to fail the call.
      sendFailureResponse(request, Response.SERVER_INTERNAL_ERROR);
      return;
    }

    if (serverGroup != null) {
      // proxyToServerGroup
      logger.info("server group is null");
    } else {
      proxyTransactionProcessRequest(proxySIPRequest, cookie);
    }
  }

  // Get static server group
  public Mono<String> getStaticServerGroup(ProxySIPRequest proxySIPRequest) {
    boolean found = false;
    if (found) {
      return Mono.just("10.1.1.1");
    } else return Mono.empty();
  }

  // Get dynamic server group

  public Mono<String> getDynamicServerGroup(ProxySIPRequest proxySIPRequest) {
    // return Trunk Service output
    return Mono.empty();
  }

  public void proxyTransactionProcessRequest(
      ProxySIPRequest proxySIPRequest, ProxyCookieImpl cookieThing) {
    ProxyParamsInterface pp = getProxyParams(proxySIPRequest);
    try {
      proxyTransaction.addProxyRecordRoute(proxySIPRequest, pp);
      proxyTransaction.proxyTo(proxySIPRequest, cookieThing, pp);
    } catch (SipException | ParseException e) {
      logger.error("exception while adding record route" + e.getMessage());
    }
  }

  public ProxyParamsInterface getProxyParams(ProxySIPRequest proxySIPRequest) {
    SIPRequest request = proxySIPRequest.getClonedRequest();

    Location location = proxySIPRequest.getLocation();

    String networkStr = proxySIPRequest.getOutgoingNetwork();
    DhruvaNetwork network = DhruvaNetwork.getNetwork(networkStr).get();

    SipURI uri = location.getUri().isSipURI() ? (SipURI) location.getUri().clone() : null;
    if (uri != null) {
      request.setRequestURI(uri);
    } else {
      request.setRequestURI(location.getUri());
    }

    ProxyParamsInterface pp = controllerConfig;
    RouteList rlist = request.getRouteHeaders();

    // proper way to check if (routes != null && routes.getFirst () != null) {

    if (timeToTry > 0 || (!location.processRoute()) || rlist == null) {
      // create a new DsProxyParms object
      pp = new ProxyParams(controllerConfig, network.getName());

      ProxyParams dpp = (ProxyParams) pp;

      // If the timeToTry has been changed (can happen in a sequential search) or
      // we want to override the route header in a message (processRoute
      // = false), then we have to create a new ProxyParams object and pass
      // that to the proxyToLogical method
      if (timeToTry > 0) dpp.setRequestTimeout(timeToTry);

      if (!location.processRoute() || rlist == null) {
        logger.debug("processRoute was not set, setting binding info for location");

        URI locUri = location.getUri();
        if (null != locUri && locUri.isSipURI()) {
          SipURI url = (SipURI) locUri;
          dpp.setProxyToAddress(url.getMAddrParam() != null ? url.getMAddrParam() : url.getHost());
          //          String host = url.getMAddrParam() != null ? url.getMAddrParam() :
          // url.getHost();
          //          if (SipUtils.isHostIPAddr(host)) {
          //            try {
          //              bInfo.setRemoteAddress(host);
          //              logger.info("Set Binding Info remote address to " + host);
          //            } catch (Exception e) {
          //              logger.error("Cannot set destination address in bindingInfo!", e);
          //            }
          //          }

          // added the if check for correct DNS SRV
          if (url.getPort() >= 0) {
            dpp.setProxyToPort(url.getPort());
          }

          if (url.getTransportParam() != null) {
            dpp.setProxyToProtocol(Transport.valueOf(url.getTransportParam()));
          } else {
            /*
             * if the location doesn't have a transport set (in
             * case of deafult_sip select the transport based on
             * network listenpoint in the order of TLS,TCP and
             * UDP
             */
            boolean interfaceSet = false;
            for (Transport transport : Transports) {
              if (controllerConfig.getInterface(
                      transport,
                      DhruvaNetwork.getNetwork(proxySIPRequest.getOutgoingNetwork()).get())
                  != null) {
                dpp.setProxyToProtocol(transport);
                interfaceSet = true;
                break;
              }
            }

            if (!interfaceSet) {
              dpp.setProxyToProtocol(Transport.UDP);
            }
          }
          logger.debug(
              "Set proxy-params to: "
                  + pp.getProxyToAddress()
                  + ':'
                  + pp.getProxyToPort()
                  + ':'
                  + pp.getProxyToProtocol());
        }
      }
    }

    // REDDY setting the record route user portion
    if (pp instanceof ProxyParams) {
      ((ProxyParams) pp).setRecordRouteUserParams(getRecordRouteParams(proxySIPRequest, true));
    } else {
      // creating a new DsProxyParams from the ppIface and set recordrouting params to it.
      pp = new ProxyParams(controllerConfig, network.getName());
      ((ProxyParams) pp).setRecordRouteUserParams(getRecordRouteParams(proxySIPRequest, true));
      logger.debug(
          "DsProxyParamsInterface is not of type DsProxyParams so not setting the record-route user params");
    }
    return pp;
  }

  public String getRecordRouteParams(ProxySIPRequest proxySIPRequest, boolean escape) {
    logger.debug("Entering getRecordRouteParams()");

    String rrUser = "";

    rrUser =
        rrUser
            + ReConstants.BS_RR_TOKEN
            + ReConstants.BS_NETWORK_TOKEN
            + proxySIPRequest.getNetwork();

    //    if (escape) {
    //      rrUser = SipURI.getEscapedString(rrUser, SipURI.USER_ESCAPE_BYTES);
    //    }

    logger.debug("Leaving getRecordRouteParams(), returning " + rrUser + '"');
    return rrUser;
  }

  public DhruvaNetwork getNetwork(Location location, String serverGroup) {
    DhruvaNetwork network = null;

    if (serverGroup == null) {
      Optional<DhruvaNetwork> optionalDhruvaNetwork = getNetworkFromMyURI();
      if (optionalDhruvaNetwork.isPresent()) {
        network = optionalDhruvaNetwork.get();
      } else {
        logger.info("Dhruva getting network from location");
        network = getNetworkFromLocation(location);
      }

      if (network == null) {
        logger.debug("Network not set on the location");
        network = location.getDefaultNetwork();
        if (network == null) {
          // should never happen
          logger.debug("No default network specified for this request");
        }
      }
    }

    return network;
  }

  public DhruvaNetwork getNetworkFromLocation(Location location) {
    // get the network from location if set
    DhruvaNetwork network = location.getNetwork();

    if (network == null) {
      // get the network from the bindinginfo of the outgoing message if set
      network = location.getNetwork();
    }

    return network;
  }

  private Optional<DhruvaNetwork> getNetworkFromMyURI() {

    // get the network from my-uri(record routing case)
    Map<String, String> parsedProxyParams = null;
    try {
      parsedProxyParams = getParsedProxyParams(ReConstants.MY_URI, false);
    } catch (DhruvaException e) {
      logger.error("Error in parsing the my uri for app params", e);
    }

    if (parsedProxyParams != null) {
      logger.info("Dhruva " + parsedProxyParams.toString());
      logger.info("Dhruva " + parsedProxyParams.get(ReConstants.N));
      return DhruvaNetwork.getNetwork(parsedProxyParams.get(ReConstants.N));
    }

    return Optional.empty();
  }

  public Map<String, String> getParsedProxyParams(int type, boolean decompress)
      throws DhruvaException {
    return getParsedProxyParams(type, decompress, ReConstants.DELIMITER_STR);
  }

  public Map<String, String> getParsedProxyParams(int type, boolean decompress, String delimiter)
      throws DhruvaException {
    Map<String, String> proxyParams = null;
    if (parsedProxyParamsByType == null) {
      parsedProxyParamsByType = new HashMap<Integer, Map<String, String>>();
    } else {
      proxyParams = parsedProxyParamsByType.get(type);
    }

    if (proxyParams == null) {
      proxyParams =
          ParseProxyParamUtil.getParsedProxyParams(ourRequest, type, decompress, delimiter);
    }

    if (proxyParams != null) {
      parsedProxyParamsByType.put(type, proxyParams);
    }

    return proxyParams;
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
    ourRequest = request;
    originalRequest = (SIPRequest) request.clone();

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
    incomingNetwork = network;
    proxySIPRequest.setProxyStatelessTransaction(proxyTransaction);
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
                  proxyFactory
                      .proxyTransaction()
                      .apply(this, controllerConfig, serverTrans, request);
        } catch (InternalProxyErrorException ex) {
          logger.error("exception while creating proxy transaction" + ex.getMessage());
          return null;
        }
      } else {
        try {
          proxyTransaction = new ProxyStatelessTransaction(this, controllerConfig, request);
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
      ProxyStatelessTransaction proxy, ProxyCookie cookie, ProxyClientTransaction trans) {
    logger.debug("Entering onProxySuccess() ");
    // demarshall the cookie object
    ProxyCookieImpl cookieThing = (ProxyCookieImpl) cookie;
    // ProxyResponseInterface responseIf = cookieThing.getResponseInterface();
    Location location = cookieThing.getLocation();

    // See if this is a branch that should be cancelled
    int index;
    if ((index = unCancelledBranches.indexOf(location)) != -1) {

      logger.debug("Found an uncancelled branch, cancelling it now ");

      trans.cancel();
      unCancelledBranches.remove(index);
      return;
    }

    // Store the mapping between this location and its client transaction so we
    // can easily cancel the branch if we need to
    locToTransMap.put(location, trans);
  }

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

  //  @Override
  //  public ControllerConfig getControllerConfig() {
  //    return this.controllerConfig;
  //  }
}
