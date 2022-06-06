package com.cisco.dsb.proxy.controller;

import com.cisco.dsb.common.context.CommonContext;
import com.cisco.dsb.common.context.ExecutionContext;
import com.cisco.dsb.common.exception.DhruvaException;
import com.cisco.dsb.common.exception.DhruvaRuntimeException;
import com.cisco.dsb.common.exception.ErrorCode;
import com.cisco.dsb.common.executor.DhruvaExecutorService;
import com.cisco.dsb.common.metric.SipMetricsContext;
import com.cisco.dsb.common.service.MetricService;
import com.cisco.dsb.common.sip.jain.JainSipHelper;
import com.cisco.dsb.common.sip.stack.dto.DhruvaNetwork;
import com.cisco.dsb.common.sip.util.*;
import com.cisco.dsb.common.transport.Transport;
import com.cisco.dsb.common.util.SpringApplicationContext;
import com.cisco.dsb.proxy.ControllerInterface;
import com.cisco.dsb.proxy.ProxyConfigurationProperties;
import com.cisco.dsb.proxy.ProxyState;
import com.cisco.dsb.proxy.controller.util.ParseProxyParamUtil;
import com.cisco.dsb.proxy.dto.ProxyAppConfig;
import com.cisco.dsb.proxy.errors.InternalProxyErrorException;
import com.cisco.dsb.proxy.messaging.MessageConvertor;
import com.cisco.dsb.proxy.messaging.ProxySIPRequest;
import com.cisco.dsb.proxy.messaging.ProxySIPResponse;
import com.cisco.dsb.proxy.sip.*;
import gov.nist.core.HostPort;
import gov.nist.javax.sip.SipStackImpl;
import gov.nist.javax.sip.address.AddressImpl;
import gov.nist.javax.sip.address.SipUri;
import gov.nist.javax.sip.header.Allow;
import gov.nist.javax.sip.header.Route;
import gov.nist.javax.sip.header.RouteList;
import gov.nist.javax.sip.header.Supported;
import gov.nist.javax.sip.message.SIPRequest;
import gov.nist.javax.sip.message.SIPResponse;
import gov.nist.javax.sip.stack.SIPTransaction;
import java.io.IOException;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.text.ParseException;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.function.Function;
import javax.sip.ServerTransaction;
import javax.sip.SipException;
import javax.sip.SipProvider;
import javax.sip.address.SipURI;
import javax.sip.address.URI;
import javax.sip.header.AllowHeader;
import javax.sip.header.Header;
import javax.sip.header.RouteHeader;
import javax.sip.message.Request;
import javax.sip.message.Response;
import lombok.CustomLog;
import lombok.Getter;
import lombok.NonNull;
import lombok.Setter;
import reactor.core.publisher.Mono;

@CustomLog
public class ProxyController implements ControllerInterface, ProxyInterface {

  @Getter private final ServerTransaction serverTransaction;
  private final SipProvider sipProvider;
  @Getter private final ProxyConfigurationProperties proxyConfigurationProperties;
  @Getter private final ProxyFactory proxyFactory;
  @Getter @Setter private ControllerConfig controllerConfig;

  @Getter @Setter private DhruvaExecutorService dhruvaExecutorService;

  @Getter @Setter private ProxyStatelessTransaction proxyTransaction;
  /* Stores the request for this controller. Do not work on this directly, always work on the clone. */
  @Getter @Setter protected ProxySIPRequest ourRequest;

  @Getter @Setter private String incomingNetwork;
  @Getter @Setter private String outgoingNetwork;

  @Getter @Setter protected int timeToTry = 32;

  /** If true, will cancel all branches on CANCEL, 2xx and 6xx responses */
  @Getter @Setter protected boolean cancelBranchesAutomatically = false;

  @Getter static final boolean mEmulate2543 = false;

  @Getter protected boolean usingRouteHeader = false;

  protected ArrayList unCancelledBranches = new ArrayList(3);

  public HashMap<Integer, Map<String, String>> parsedProxyParamsByType = null;

  /* Stores if we are in stateful or stateless mode */
  @Setter protected byte stateMode = -1;
  /** reference if request was sent to application. Default is true. */
  private boolean sendRequestToApp = true;

  public static final String CALLTYPE = "calltype";

  private ProxyAppConfig proxyAppConfig;

  private static MetricService metricService =
      SpringApplicationContext.getAppContext() == null
          ? null
          : SpringApplicationContext.getAppContext().getBean(MetricService.class);

  public ProxyController(
      ServerTransaction serverTransaction,
      @NonNull SipProvider sipProvider,
      @NonNull ProxyAppConfig proxyAppConfig,
      @NonNull ProxyConfigurationProperties proxyConfigurationProperties,
      @NonNull ProxyFactory proxyFactory,
      @NonNull ControllerConfig controllerConfig,
      @NonNull DhruvaExecutorService dhruvaExecutorService) {
    this.serverTransaction = serverTransaction;
    this.sipProvider = sipProvider;
    this.proxyAppConfig = proxyAppConfig;
    this.proxyConfigurationProperties = proxyConfigurationProperties;
    this.proxyFactory = proxyFactory;
    this.controllerConfig = controllerConfig;
    this.dhruvaExecutorService = dhruvaExecutorService;
  }

  public void setController(@NonNull ProxySIPRequest request) {

    request.getContext().set(CommonContext.PROXY_CONTROLLER, this);
  }

  public void proxyResponse(@NonNull ProxySIPResponse proxySIPResponse) {
    if (ourRequest != null) {
      SIPRequest req = ourRequest.getRequest();
      if ((!req.getMethod().equals(Request.ACK)) && (!req.getMethod().equals(Request.CANCEL))) {
        // Change to statefull if we are stateless
        /*if (stateMode != ControllerConfig.STATEFUL) {
          overwriteStatelessMode(req);
        }*/
        if (proxyTransaction != null) {
          SIPResponse response = proxySIPResponse.getResponse();
          ((ProxyTransaction) proxyTransaction).respond(response);
          logger.info("Sent response: {}", response.getStatusCode());
        } else {
          logger.error("ProxyTransaction was null!");
        }
      } else {
        logger.warn("in respond() - not forwarding response because request method was ACK");
      }
    } else {
      logger.error(
          "Request is null for response, this should have never come here, as there is"
              + " transaction check before sending to application!!!");
    }
  }

  public void respond(int responseCode, @NonNull ProxySIPRequest proxySIPRequest) {

    Response response;
    try {
      response =
          JainSipHelper.getMessageFactory()
              .createResponse(responseCode, proxySIPRequest.getOriginalRequest());
    } catch (ParseException e) {
      logger.error("Unable to create SipResponse for responseCode {}", responseCode, e);
      return;
    }

    Optional.ofNullable(((ProxyTransaction) proxyTransaction).getServerTransaction())
        .ifPresent(proxySrvTxn -> proxySrvTxn.setInternallyGeneratedResponse(true));

    ((ProxyTransaction) proxyTransaction).respond((SIPResponse) response);
  }

  @Override
  public CompletableFuture<ProxySIPResponse> proxyRequest(
      @NonNull ProxySIPRequest proxySIPRequest, EndPoint endPoint) {
    logger.info(
        "proxying to endpoint {} over transport {}", endPoint.getHost(), endPoint.getProtocol());
    proxySIPRequest.getAppRecord().add(ProxyState.IN_PROXY_PROCESS_ENDPOINT, null);
    // already has EndPoint
    processOutboundDestination(proxySIPRequest, endPoint); // add Route header
    return proxyRequest(proxySIPRequest);
  }

  @Override
  public CompletableFuture<ProxySIPResponse> proxyRequest(ProxySIPRequest proxySIPRequest) {
    CompletableFuture<ProxySIPResponse> responseCF = new CompletableFuture<>();
    ((ProxyCookieImpl) proxySIPRequest.getCookie()).setResponseCF(responseCF);
    try {
      proxyTransactionProcessRequest.apply(proxySIPRequest);
    } // adds some proxyParams and RR
    catch (DhruvaRuntimeException e) {
      responseCF.completeExceptionally(e);
      return responseCF;
    }
    proxyTransaction
        .proxySendOutBoundRequest(proxySIPRequest)
        .subscribe(
            this::onProxySuccess,
            err -> {
              try {
                this.onProxyFailure(
                    proxySIPRequest.getProxyClientTransaction(), proxySIPRequest.getCookie(), err);
              } catch (Exception e) {
                logger.error(
                    "Unable to handle exception gracefully during proxy forward request", e);
                responseCF.completeExceptionally(e);
              }
            });
    return responseCF;
  }

  /**
   * Check whether App is interested in mid midialog messages set the usingRouteHeader to true if
   * flag is false and viceversa If app is not interested, means route will come into play
   */
  @Override
  public void sendRequestToApp(boolean send) {
    this.sendRequestToApp = send;
  }

  private Optional<ProxySIPRequest> processOutboundDestination(
      ProxySIPRequest proxySIPRequest, EndPoint ep) {
    DhruvaNetwork network;
    proxySIPRequest.setDownstreamElement(ep);
    network = getNetwork(ep);
    if (network != null) {
      try {
        addRouteFromEndpoint(proxySIPRequest.getRequest(), ep);
      } catch (ParseException pe) {
        throw new DhruvaRuntimeException(
            ErrorCode.PROXY_REQ_PROC_ERR, "Unable to add Endpoint as route to request", pe);
      }
      proxySIPRequest.setOutgoingNetwork(network.getName());
      logger.info("setting outgoing network to ", network.getName());
    } else {
      if (proxySIPRequest.getOutgoingNetwork() == null) {
        logger.warn("Could not find the network to set to the request");
        // until then we need to fail the call.
        throw new DhruvaRuntimeException(
            ErrorCode.NO_OUTGOING_NETWORK, "Could not find the network to set to the request");
      }
    }
    return Optional.of(proxySIPRequest);
  }

  private void addRouteFromEndpoint(SIPRequest clonedRequest, EndPoint ep) throws ParseException {
    RouteList routes = clonedRequest.getRouteHeaders();
    Route routeFromEndPoint = new Route();
    AddressImpl address = new AddressImpl();
    SipUri sipUri = new SipUri();
    sipUri.setPort(ep.getPort());
    sipUri.setHost(ep.getHost());
    sipUri.setTransportParam(ep.getProtocol().name().toLowerCase(Locale.ROOT));
    sipUri.setParameter("lr", null);
    address.setURI(sipUri);
    // address, transport, any other params
    routeFromEndPoint.setAddress(address);
    if (routes == null) {
      clonedRequest.setHeader(routeFromEndPoint);
      return;
    }
    routes.addFirst(routeFromEndPoint);
  }

  private Function<ProxySIPRequest, ProxySIPRequest> proxyTransactionProcessRequest =
      proxySIPRequest -> {
        try {
          if (proxySIPRequest.getOutgoingNetwork() == null)
            throw new DhruvaRuntimeException("No outgoing network set, unable to route the call");
          if (proxySIPRequest.getDownstreamElement() == null) {
            Optional<DhruvaNetwork> outGoingNetwork =
                DhruvaNetwork.getNetwork(proxySIPRequest.getOutgoingNetwork());
            HostPort hostPort = getHostPortFromRequest(proxySIPRequest.getRequest());
            EndPoint endPoint =
                outGoingNetwork
                    .map(
                        dhruvaNetwork ->
                            new EndPoint(
                                dhruvaNetwork.getName(),
                                hostPort.getHost().getIpAddress(),
                                hostPort.getPort() <= 0
                                    ? dhruvaNetwork.getTransport().getDefaultPort()
                                    : hostPort.getPort(),
                                dhruvaNetwork.getTransport()))
                    .orElseThrow(() -> new DhruvaException("unable to find outgoing network"));
            logger.debug("Created Endpoint from request, Endpoint:{}", endPoint);
            proxySIPRequest.setDownstreamElement(endPoint);
          }
          ProxyParamsInterface pp = getProxyParams(proxySIPRequest);
          proxySIPRequest.setParams(pp);
          proxyTransaction.addProxyRecordRoute(proxySIPRequest);
          return proxyTransaction.proxyPostProcess(proxySIPRequest);
        } catch (SipException | ParseException | DhruvaException | UnknownHostException e) {
          throw new DhruvaRuntimeException(
              ErrorCode.PROXY_REQ_PROC_ERR,
              "exception while doing proxy transaction processing",
              e);
        }
      };

  private HostPort getHostPortFromRequest(SIPRequest request) {
    SipUri uri;
    if (request.getHeader(RouteHeader.NAME) != null) {
      uri = ((SipUri) ((Route) request.getRouteHeaders().getFirst()).getAddress().getURI());
    } else {
      uri = ((SipUri) request.getRequestURI());
    }
    return uri.getHostPort();
  }

  public ProxyParamsInterface getProxyParams(ProxySIPRequest proxySIPRequest)
      throws DhruvaException, ParseException, UnknownHostException {
    SIPRequest request = proxySIPRequest.getRequest();

    Optional<DhruvaNetwork> optionalDhruvaNetwork =
        DhruvaNetwork.getNetwork(proxySIPRequest.getOutgoingNetwork());
    DhruvaNetwork outgoingNetwork;
    outgoingNetwork =
        optionalDhruvaNetwork.orElseThrow(
            () -> new DhruvaException("unable to find outgoing network"));

    ProxyParams pp;
    pp = new ProxyParams(controllerConfig, outgoingNetwork.getName());
    ProxyParams dpp = pp;
    dpp.setProxyToAddress(proxySIPRequest.getDownstreamElement().getHost());
    if (!mEmulate2543) {
      try {
        proxySIPRequest.lrEscape();
      } catch (ParseException e) {
        logger.error("caught exception while invoking lrEscape in proxyRouteSetRemoteInfo", e);
        throw e;
      }
    }
    request.setRemoteAddress(
        InetAddress.getByName(proxySIPRequest.getDownstreamElement().getHost()));
    dpp.setProxyToPort(proxySIPRequest.getDownstreamElement().getPort());
    request.setRemotePort(proxySIPRequest.getDownstreamElement().getPort());

    dpp.setProxyToProtocol(proxySIPRequest.getDownstreamElement().getProtocol());

    // setting the record route user portion
    pp.setRecordRouteUserParams(getRecordRouteParams(proxySIPRequest, true));

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

    // TODO DSB Not sure what does this fn do?
    //    if (escape) {
    //      rrUser = SipURI.getEscapedString(rrUser, SipURI.USER_ESCAPE_BYTES);
    //    }

    logger.debug("Leaving getRecordRouteParams(), returning {}", rrUser);
    return rrUser;
  }

  public DhruvaNetwork getNetwork(EndPoint endPoint) {
    DhruvaNetwork network = null;

    Optional<DhruvaNetwork> optionalDhruvaNetwork;
    if (endPoint.getNetwork() == null) {
      optionalDhruvaNetwork = getNetworkFromMyURI();

    } else {
      optionalDhruvaNetwork = DhruvaNetwork.getNetwork(endPoint.getNetwork());
    }
    if (optionalDhruvaNetwork.isPresent()) {
      network = optionalDhruvaNetwork.get();
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
      // logger.info("Dhruva " + parsedProxyParams.toString());
      logger.debug(
          "Dhruva- network name found in proxy param: " + parsedProxyParams.get(ReConstants.N));
      if (parsedProxyParams.get(ReConstants.N) != null)
        return DhruvaNetwork.getNetwork(parsedProxyParams.get(ReConstants.N));
    }

    return Optional.empty();
  }

  private Optional<DhruvaNetwork> getNetworkFromMyRoute() {

    // get the network from my-uri(record routing case)
    Map<String, String> parsedProxyParams = null;
    try {
      parsedProxyParams = getParsedProxyParams(ReConstants.ROUTE, false);
    } catch (DhruvaException e) {
      logger.error("Error in parsing the route for app params", e);
    }

    if (parsedProxyParams != null) {
      logger.info("Dhruva " + parsedProxyParams);
      logger.info("Dhruva " + parsedProxyParams.get(ReConstants.N));
      if (parsedProxyParams.get(ReConstants.N) != null)
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
      parsedProxyParamsByType = new HashMap<>();
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

  Function<ProxySIPRequest, Mono<ProxySIPRequest>> processIncomingProxyRequestMAddr =
      proxySIPRequest -> {
        // MAddr handling of proxy
        SIPRequest request = proxySIPRequest.getRequest();
        URI uri = request.getRequestURI();
        if (uri.isSipURI()) {
          SipURI sipURI = (SipURI) uri;
          if (sipURI.getMAddrParam() != null) {

            return controllerConfig
                .recognize(uri, false)
                .handle(
                    (response, sink) -> {
                      if (response) {
                        sipURI.removeParameter("maddr");
                        if (sipURI.getPort() >= 0) sipURI.removePort();
                        if (sipURI.getTransportParam() != null) sipURI.removeParameter("transport");
                      }
                      sink.next(proxySIPRequest);
                    });
          }
        }
        return Mono.just(proxySIPRequest);
      };

  /**
   * Apply the loose routing fix operation. The supplied interface is first asked to recognize the
   * request URI.
   *
   * <p>CASE 1: If the request URI is recognized, it is saved internally as the LRFIX URI and
   * replaced by the URI of the bottom Route header. If the this is the only header, and the route
   * headers's URI is recoginized, it is saved internally as LRFIX URI. If there are more headers,
   * the supplied interface is asked to recognize the URI of the top Route header. If the top Route
   * header's URI is recognized, it is removed and saved internally as the LRFIX URI.
   *
   * <p>CASE 2: If the request URI is not recognized, the supplied interface is asked to recognize
   * the URI of the top Route header. If the top Route header's URI is recognized, it is removed and
   * saved internally as the LRFIX URI.
   *
   * <p>CASE 3: If neither is recognized, the FIX URI is set to null. Once this method is called, it
   * will always return the value returned on its first invocation.
   *
   * <p>Saves the Route Header Parameters, if any, locally. The application needs to call
   * getRouteParameters() to get the value. As per RFC 2543 (strict routing), the route header does
   * not support header parameters in route header. So, if the previous element is a strict router,
   * the header parameters object remains null.
   *
   * @param proxySipRequest
   * @return the proxySipRequst with FIX URI as described above
   * @throws RuntimeException if fix did not succeed.
   */
  Function<ProxySIPRequest, Mono<ProxySIPRequest>> incomingProxyRequestFixLr =
      proxySIPRequest -> {
        SIPRequest request = proxySIPRequest.getRequest();
        // lrfix - user recognizes the rURI
        URI uriAsync = null;
        RouteHeader topRoute = (RouteHeader) request.getHeader(RouteHeader.NAME);
        if (topRoute != null) uriAsync = topRoute.getAddress().getURI();
        URI finalUriAsync = uriAsync;
        return controllerConfig
            .recognize(request.getRequestURI(), true)
            .handle(
                (response, sink) -> {
                  if (response) {
                    RouteHeader lastRouteHeader = null;
                    RouteList routes = request.getRouteHeaders();
                    if (routes != null) {
                      lastRouteHeader = (RouteHeader) routes.getLast();
                    }
                    if (lastRouteHeader == null) {
                      sink.error(
                          new DhruvaRuntimeException(
                              ErrorCode.PROXY_REQ_PROC_ERR,
                              "Failed to fix the loose routing for the request"));
                      return;
                    }
                    proxySIPRequest.setLrFixUri(request.getRequestURI());
                    request.setRequestURI(lastRouteHeader.getAddress().getURI());
                    request.removeLast(RouteHeader.NAME);

                    // Top Most Route Header
                    RouteHeader topMostRouteHeader =
                        (RouteHeader) request.getHeader(RouteHeader.NAME);

                    if (topMostRouteHeader == null) {

                      URI lrfixUri = proxySIPRequest.getLrFixUri();
                      URI lastRouteUri = lastRouteHeader.getAddress().getURI();

                      SipURI lrfixSipUri = (SipURI) lrfixUri;
                      SipURI lastRouteSipUri = (SipURI) lastRouteUri;
                      if (ProxyUtils.checkSipUriMatches(lrfixSipUri, lastRouteSipUri)) {
                        proxySIPRequest.setLrFixUri(lastRouteUri);
                      }
                    } else {
                      URI uri = topMostRouteHeader.getAddress().getURI();
                      URI lrfixUri = proxySIPRequest.getLrFixUri();

                      SipURI sipUri = (SipURI) uri;
                      SipURI sipLrFixUri = (SipURI) lrfixUri;
                      if (ProxyUtils.checkSipUriMatches(sipUri, sipLrFixUri)) {
                        logger.debug(
                            "removing top most route header that matches dhruva addr:", uri);
                        Optional<DhruvaNetwork> optionalDhruvaNetwork = getNetworkFromMyRoute();
                        optionalDhruvaNetwork.ifPresent(
                            dhruvaNetwork ->
                                proxySIPRequest.setOutgoingNetwork(dhruvaNetwork.getName()));
                        proxySIPRequest.setLrFixUri(uri);
                        request.removeFirst(RouteHeader.NAME);
                      }
                    }
                    sink.next(proxySIPRequest);
                  }
                })
            .switchIfEmpty(
                controllerConfig
                    .recognize(finalUriAsync, false)
                    .handle(
                        (response, sink) -> {
                          if (response) {

                            Optional<DhruvaNetwork> optionalDhruvaNetwork = getNetworkFromMyRoute();
                            optionalDhruvaNetwork.ifPresent(
                                dhruvaNetwork ->
                                    proxySIPRequest.setOutgoingNetwork(dhruvaNetwork.getName()));

                            proxySIPRequest.setLrFixUri(finalUriAsync);
                            request.removeFirst(RouteHeader.NAME);
                            logger.debug(
                                "removing top most route header that matches dhruva addr:",
                                finalUriAsync);
                          }
                          sink.next(proxySIPRequest);
                        }))
            .cast(ProxySIPRequest.class);
      };

  @Override
  public Mono<ProxySIPRequest> onNewRequest(ProxySIPRequest proxySIPRequest) {

    SIPRequest request = proxySIPRequest.getRequest();
    ourRequest = proxySIPRequest;

    // As per RFC (Section 16.4), Route Information Preprocessing happens here
    return incomingProxyRequestFixLr
        .apply(proxySIPRequest)
        .flatMap(processIncomingProxyRequestMAddr)
        .handle(
            (proxySIPReq, sink) -> {

              // Fetch the network from provider
              SipProvider sipProvider = proxySIPRequest.getProvider();
              Optional<String> networkFromProvider =
                  DhruvaNetwork.getNetworkFromProvider(sipProvider);

              if (!networkFromProvider.isPresent()) {
                logger.error("Unable to find network from provider");
                sink.error(
                    new DhruvaRuntimeException(
                        ErrorCode.NO_INCOMING_NETWORK, "Unable to find network from provider"));
                return;
              } else incomingNetwork = networkFromProvider.get();
              proxySIPRequest.setNetwork(incomingNetwork);

              // Create ProxyTransaction
              // ProxyTransaction internally creates ProxyServerTransaction
              proxyTransaction =
                  createProxyTransaction(
                      controllerConfig.isStateful(), request, serverTransaction, proxyFactory);

              if (proxyTransaction == null) {
                sink.error(
                    new DhruvaRuntimeException(
                        ErrorCode.TRANSACTION_ERROR,
                        "unable to create ProxyTransaction for new incoming"
                            + request.getMethod()
                            + " request"));
                return;
              }

              proxySIPRequest.setProxyStatelessTransaction(proxyTransaction);
              proxySIPRequest.setMidCall(SipUtils.isMidDialogRequest(request));

              // Set the proxyTransaction in jain server transaction for future reference
              if (serverTransaction != null) {
                serverTransaction.setApplicationData(proxyTransaction);
              }
              ProxySIPRequest handledRequest = handleRequest().apply(proxySIPRequest);
              if (handledRequest != null) sink.next(handledRequest);
            });
  }

  public Function<ProxySIPRequest, ProxySIPRequest> handleRequest() {
    return proxySIPRequest -> {
      SIPRequest sipRequest = proxySIPRequest.getRequest();
      String requestType = sipRequest.getMethod();

      switch (requestType) {
        case Request.REGISTER:
          if (getProxyConfigurationProperties().getSipProxy().isProcessRegisterRequest()) {
            // proxy behaviour on processing REGISTER - nothing at this point
            logger.debug("Proxy processing incoming REGISTER");
            return proxySIPRequest;
          } else {
            try {
              logger.info(
                  "Received REGISTER is not supported by Proxy. Sending 405 error response");
              Response sipResponse =
                  JainSipHelper.getMessageFactory()
                      .createResponse(Response.METHOD_NOT_ALLOWED, sipRequest);
              addAllowHeader(getProxyConfigurationProperties().getAllowedMethods(), sipResponse);
              ProxySendMessage.sendResponse(sipResponse, serverTransaction, sipProvider);
            } catch (Exception e) {
              throw new DhruvaRuntimeException(
                  ErrorCode.SEND_RESPONSE_ERR,
                  "Error sending 405 (METHOD NOT ALLOWED) response for REGISTER request",
                  e);
            }
            return null;
          }

        case Request.CANCEL:
          try {
            // Reply to received CANCEL request with 200 OK --> to Client
            logger.info("Sending 200 (OK) response for received CANCEL");
            Response sipResponse =
                JainSipHelper.getMessageFactory().createResponse(Response.OK, sipRequest);
            ProxySendMessage.sendResponse(sipResponse, serverTransaction, sipProvider);

            // Sending CANCEL to server
            ServerTransaction serverTransaction = proxySIPRequest.getServerTransaction();
            if (serverTransaction != null) {

              // Find relevant initial transaction for CANCEL and map it with client Transaction
              SIPTransaction sipTransaction =
                  ((SipStackImpl) proxySIPRequest.getProvider().getSipStack())
                      .findCancelTransaction(sipRequest, true);
              if (sipTransaction == null) {
                logger.error(
                    "Initial Transaction not found for CANCEL {} , dropping it", proxySIPRequest);
                return null;
              }
              ProxyTransaction proxyTransaction =
                  (ProxyTransaction) sipTransaction.getApplicationData();
              // sending over to client
              proxyTransaction.cancel();
            }
          } catch (Exception e) {
            throw new DhruvaRuntimeException(
                ErrorCode.SEND_RESPONSE_ERR,
                "Error sending 200 (OK) response for CANCEL request",
                e);
          }
          return null;

        case Request.OPTIONS:
          try {
            logger.info("Processing OPTIONS request & responding with 200 OK");
            Response sipResponse =
                JainSipHelper.getMessageFactory().createResponse(Response.OK, sipRequest);
            addAllowHeader(getProxyConfigurationProperties().getAllowedMethods(), sipResponse);
            addSupportedHeader(SupportedExtensions.getExtensions(), sipResponse);
            addAcceptHeader(sipResponse);
            // TODO: if we know the data for 'Accept-Language' & 'AcceptEncoding' headers, add them
            // to this OPTIONS 200 OK response (as per rfc)
            ProxySendMessage.sendResponse(sipResponse, serverTransaction, sipProvider);
          } catch (Exception e) {
            throw new DhruvaRuntimeException(
                ErrorCode.SEND_RESPONSE_ERR,
                "Error sending 200 (OK) response for OPTIONS request",
                e);
          }
          return null;

        default:
          // strayRequest = NOT_STRAY;
          return proxySIPRequest;
      }
    };
  }

  /** Add the proxy allowed methods to 'Allow' header * */
  private void addAllowHeader(String allowedMethods, Response response) {
    AllowHeader allowHeader = new Allow(allowedMethods);
    response.addHeader(allowHeader);
  }

  private void addSupportedHeader(List<String> supportedFeatures, Response response) {
    List<Supported> supportedHeaders = new ArrayList<>();
    supportedFeatures.forEach(
        val -> {
          Supported header = new Supported();
          try {
            header.setOptionTag(val);
            supportedHeaders.add(header);
          } catch (ParseException e) {
            e.printStackTrace();
          }
        });
    supportedHeaders.forEach(response::addHeader);
  }

  private void addAcceptHeader(Response response) {
    try {
      Header acceptHeader =
          JainSipHelper.getHeaderFactory()
              .createHeader(
                  "Accept",
                  SipConstants.Content_Type_Application + "/" + SipConstants.ContentSubType.Sdp);
      response.addHeader(acceptHeader);
    } catch (Exception e) {
      logger.warn("Exception adding 'Accept' header");
    }
  }
  /**
   * Creates a <CODE>ProxyStatelessTransaction</CODE> object if the proxy is configured to be
   * stateless. Otherwise if either the proxy is configured to be stateful or if the controller
   * decides that the current transaction should be stateful , it creates the <CODE>
   * ProxyTransaction</CODE> object. This method can only be used to create a transaction if one has
   * not been created yet.
   *
   * @param setStateful Indicates that the current transaction be stateful,irrespective of the
   *     controller configuration.
   * @param request The request that will be used to create the transaction
   */
  public ProxyStatelessTransaction createProxyTransaction(
      boolean setStateful,
      @NonNull SIPRequest request,
      ServerTransaction serverTrans,
      @NonNull ProxyFactory proxyFactory) {

    if (proxyTransaction == null) {
      Transport transport = Transport.NONE;
      if (incomingNetwork != null)
        transport =
            DhruvaNetwork.getNetwork(incomingNetwork)
                .orElseGet(DhruvaNetwork::getDefault)
                .getTransport();

      if (setStateful || (transport == Transport.TCP)) {
        try {
          proxyTransaction =
              proxyFactory.proxyTransaction().apply(this, controllerConfig, serverTrans, request);
        } catch (InternalProxyErrorException ex) {
          logger.error("exception while creating proxy transaction" + ex.getMessage());
          return null;
        }
      } else {
        try {
          proxyTransaction = new ProxyStatelessTransaction(this, controllerConfig, request);
        } catch (InternalProxyErrorException dse) {
          logger.error("exception while creating proxy stateless transaction" + dse.getMessage());
          // Do not invoke sendFailureResponse, there is no proxy transaction is created
          return null;
        }
      }
    }
    return proxyTransaction;
  }

  @Override
  public void onProxySuccess(ProxySIPRequest proxySIPRequest) {
    logger.debug("sent out the request successfully");
    proxySIPRequest.handleProxyEvent(
        metricService, SipMetricsContext.State.proxyNewRequestSendSuccess);
  }

  @Override
  public void onProxyFailure(
      ProxyClientTransaction proxyClientTransaction, ProxyCookie cookie, Throwable err) {
    logger.debug("Entering onProxyFailure():");
    if (proxyClientTransaction != null) {
      ProxySIPRequest proxySIPRequest = proxyClientTransaction.getProxySIPRequest();
      proxySIPRequest.handleProxyEvent(
          metricService, SipMetricsContext.State.proxyNewRequestSendFailure);
    }

    ErrorCode errorCode;
    if (err instanceof DhruvaRuntimeException)
      errorCode = ((DhruvaRuntimeException) err).getErrCode();
    else errorCode = ErrorCode.UNKNOWN_ERROR_REQ;
    if (err instanceof SipException && err.getCause() instanceof IOException)
      errorCode = ErrorCode.DESTINATION_UNREACHABLE;
    logger.error("Error occurred while forwarding request with message:", err);
    SIPResponse sipResponse;
    try {
      sipResponse =
          ProxyResponseGenerator.createResponse(
              errorCode.getResponseCode(), ourRequest.getOriginalRequest());
    } catch (DhruvaException | ParseException ex) {
      throw new DhruvaRuntimeException(ErrorCode.CREATE_ERR_RESPONSE, ex.getMessage(), err);
    }

    if (proxyTransaction instanceof ProxyTransaction) {
      proxyClientTransaction =
          proxyClientTransaction != null
              ? proxyClientTransaction
              : ((ProxyTransaction) proxyTransaction).getClientTransaction();
      ProxySIPResponse proxySIPResponse =
          MessageConvertor.convertJainSipResponseMessageToDhruvaMessage(
              sipResponse,
              sipProvider,
              proxyClientTransaction == null ? null : proxyClientTransaction.getBranch(),
              new ExecutionContext());

      Optional.ofNullable(((ProxyTransaction) this.proxyTransaction).getServerTransaction())
          .ifPresent(proxySrvTxn -> proxySrvTxn.setInternallyGeneratedResponse(true));

      if (proxyClientTransaction != null) {
        logger.debug("Remove Timer C from the ProxyClientTransaction due to ProxyFailure");
        proxyClientTransaction.removeTimerC();
      }
      proxySIPResponse.setProxyInterface(this);
      ((ProxyCookieImpl) cookie).getResponseCF().complete(proxySIPResponse);
    }
  }

  @Override
  public void onResponseSuccess(ProxyTransaction proxy, ProxyServerTransaction trans) {
    logger.debug("Response sent successfully");
    // TODO add metrics code block
  }

  @Override
  public void onResponseFailure(
      ProxyTransaction proxy,
      ProxyServerTransaction trans,
      ErrorCode errorCode,
      String errorPhrase,
      Throwable exception) {
    if (Objects.isNull(exception)) exception = new DhruvaException(errorPhrase);
    logger.warn(
        "onResponseFailure()- Could not send response , exception" + exception.getMessage());
    // TODO add metrics and alarms
  }

  @Override
  public void onFinalResponse(ProxyCookie cookie, ProxySIPResponse proxySIPResponse) {
    logger.debug("Entering onFinalResponse:");
    if (proxySIPResponse.getResponseClass() == 2 || proxySIPResponse.getResponseClass() == 6) {
      if (cancelBranchesAutomatically && proxyTransaction instanceof ProxyTransaction) {
        logger.info("Cancel branches automatically");
        ((ProxyTransaction) proxyTransaction).cancel();
      }
    }
    ((ProxyCookieImpl) cookie).getResponseCF().complete(proxySIPResponse);
    logger.debug("Leaving onFinalResponse, notified the listener");
  }

  @Override
  public void onProvisionalResponse(ProxyCookie cookie, ProxySIPResponse proxySIPResponse) {
    logger.debug("Inside onProvisionalResponse()");

    if (proxySIPResponse.getResponse().getStatusCode() == 100) return;
    // sending out all the provisional response except 100 Trying
    proxyResponse(proxySIPResponse);

    logger.debug("Leaving onProvisionalResponse()");
  }

  @Override
  public void onResponseTimeOut(ProxyTransaction proxy, ProxyServerTransaction trans) {
    logger.debug("Entering onResponseTimeout");
    // TODO add metrics and alarm
  }

  @Override
  public void onICMPError(ProxyTransaction proxy, ProxyServerTransaction trans) {}

  @Override
  public void onICMPError(
      ProxyTransaction proxy, ProxyCookie cookie, ProxyClientTransaction trans) {}

  @Override
  public void onAck(ProxyTransaction proxy) {
    proxy.ack();
  }

  @Override
  public void onCancel(ProxyTransaction proxy) {

    proxy.cancel();
  }

  @Override
  public void onResponse(ProxySIPResponse response) {
    Optional<String> network = DhruvaNetwork.getNetworkFromProvider(response.getProvider());
    if (network.isPresent()) response.setNetwork(network.get());
    else logger.warn("Unable to set incoming network from SipProvider");
  }
}
