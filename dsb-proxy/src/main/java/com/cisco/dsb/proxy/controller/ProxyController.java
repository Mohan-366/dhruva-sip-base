package com.cisco.dsb.proxy.controller;

import com.cisco.dsb.common.CommonContext;
import com.cisco.dsb.common.config.sip.DhruvaSIPConfigProperties;
import com.cisco.dsb.common.context.ExecutionContext;
import com.cisco.dsb.common.exception.DhruvaException;
import com.cisco.dsb.common.exception.DhruvaRuntimeException;
import com.cisco.dsb.common.exception.ErrorCode;
import com.cisco.dsb.common.executor.DhruvaExecutorService;
import com.cisco.dsb.common.sip.jain.JainSipHelper;
import com.cisco.dsb.common.sip.stack.dto.BindingInfo;
import com.cisco.dsb.common.sip.stack.dto.DhruvaNetwork;
import com.cisco.dsb.common.sip.stack.util.IPValidator;
import com.cisco.dsb.common.sip.util.EndPoint;
import com.cisco.dsb.common.sip.util.ReConstants;
import com.cisco.dsb.common.sip.util.SipConstants;
import com.cisco.dsb.common.sip.util.SipUtils;
import com.cisco.dsb.common.sip.util.SupportedExtensions;
import com.cisco.dsb.common.transport.Transport;
import com.cisco.dsb.proxy.ControllerInterface;
import com.cisco.dsb.proxy.controller.util.ParseProxyParamUtil;
import com.cisco.dsb.proxy.dto.ProxyAppConfig;
import com.cisco.dsb.proxy.errors.InternalProxyErrorException;
import com.cisco.dsb.proxy.messaging.MessageConvertor;
import com.cisco.dsb.proxy.messaging.ProxySIPRequest;
import com.cisco.dsb.proxy.messaging.ProxySIPResponse;
import com.cisco.dsb.proxy.sip.ProxyClientTransaction;
import com.cisco.dsb.proxy.sip.ProxyCookie;
import com.cisco.dsb.proxy.sip.ProxyCookieImpl;
import com.cisco.dsb.proxy.sip.ProxyFactory;
import com.cisco.dsb.proxy.sip.ProxyInterface;
import com.cisco.dsb.proxy.sip.ProxyParams;
import com.cisco.dsb.proxy.sip.ProxyParamsInterface;
import com.cisco.dsb.proxy.sip.ProxySendMessage;
import com.cisco.dsb.proxy.sip.ProxyServerTransaction;
import com.cisco.dsb.proxy.sip.ProxyStatelessTransaction;
import com.cisco.dsb.proxy.sip.ProxyTransaction;
import com.cisco.dsb.proxy.sip.ProxyUtils;
import com.cisco.dsb.trunk.dto.Destination;
import com.cisco.dsb.trunk.loadbalancer.LBInterface;
import com.cisco.dsb.trunk.service.TrunkService;
import gov.nist.javax.sip.address.AddressImpl;
import gov.nist.javax.sip.address.SipUri;
import gov.nist.javax.sip.header.*;
import gov.nist.javax.sip.header.ViaList;
import gov.nist.javax.sip.message.SIPRequest;
import gov.nist.javax.sip.message.SIPResponse;
import java.io.IOException;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.text.ParseException;
import java.util.*;
import java.util.function.Function;
import javax.sip.*;
import javax.sip.address.SipURI;
import javax.sip.address.URI;
import javax.sip.header.AllowHeader;
import javax.sip.header.Header;
import javax.sip.header.RouteHeader;
import javax.sip.header.ViaHeader;
import javax.sip.message.Request;
import javax.sip.message.Response;
import lombok.CustomLog;
import lombok.Getter;
import lombok.NonNull;
import lombok.Setter;
import reactor.core.publisher.Mono;

@CustomLog
public class ProxyController implements ControllerInterface, ProxyInterface {

  @Getter private ServerTransaction serverTransaction;
  private SipProvider sipProvider;
  @Getter private DhruvaSIPConfigProperties dhruvaSIPConfigProperties;
  @Getter private ProxyFactory proxyFactory;
  @Getter @Setter private ControllerConfig controllerConfig;

  @Getter @Setter private DhruvaExecutorService dhruvaExecutorService;

  @Getter @Setter private TrunkService trunkService;

  @Getter @Setter private ProxyStatelessTransaction proxyTransaction;
  /* Stores the request for this controller */
  @Getter @Setter protected ProxySIPRequest ourRequest;

  @Getter @Setter private String incomingNetwork;
  /* Stores the original request as a clone */
  @Getter @Setter protected SIPRequest originalRequest;
  /* Stores the request with pre-normalization and xcl processing applied */
  @Getter @Setter protected SIPRequest preprocessedRequest;

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

  private ProxyAppConfig proxyAppConfig;

  public ProxyController(
      ServerTransaction serverTransaction,
      @NonNull SipProvider sipProvider,
      @NonNull ProxyAppConfig proxyAppConfig,
      @NonNull DhruvaSIPConfigProperties dhruvaSIPConfigProperties,
      @NonNull ProxyFactory proxyFactory,
      @NonNull ControllerConfig controllerConfig,
      @NonNull DhruvaExecutorService dhruvaExecutorService,
      @NonNull TrunkService trunkService) {
    this.serverTransaction = serverTransaction;
    this.sipProvider = sipProvider;
    this.proxyAppConfig = proxyAppConfig;
    this.dhruvaSIPConfigProperties = dhruvaSIPConfigProperties;
    this.proxyFactory = proxyFactory;
    this.controllerConfig = controllerConfig;
    this.dhruvaExecutorService = dhruvaExecutorService;
    this.trunkService = trunkService;
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
          logger.info("Sent response:\n" + response);
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

    ProxySendMessage.sendResponseAsync(
            responseCode,
            proxySIPRequest.getProvider(),
            proxySIPRequest.getServerTransaction(),
            proxySIPRequest.getRequest())
        .subscribe(
            req -> {},
            err -> {
              logger.error("error in sending the response {}", err.getMessage());
              // Handle exception
            });
  }

  public void proxyRequest(
      @NonNull ProxySIPRequest proxySIPRequest, @NonNull Destination destination) {
    logger.debug("Entering proxyTo()");

    proxySIPRequest.setDestination(destination);
    proxyTo(destination, proxySIPRequest, timeToTry);

    logger.debug("Leaving proxyTo()");
  }

  /**
   * Check whether App is interested in mid midialog messages set the usingRouteHeader to true if
   * flag is false and viceversa If app is not interested, means route will come into play
   */
  @Override
  public void sendRequestToApp(boolean send) {
    this.sendRequestToApp = send;
  }

  public void proxyTo(Destination destination, ProxySIPRequest proxySIPRequest, long timeToTry) {

    logger.info("Entering proxyTo() with Destination: {} & Timeout: {}", destination, timeToTry);
    preprocessedRequest = (SIPRequest) proxySIPRequest.getRequest().clone();

    proxySIPRequest.setClonedRequest((SIPRequest) preprocessedRequest.clone());

    proxyForwardRequest(destination, proxySIPRequest, timeToTry)
        .subscribe(
            sentRequest -> {
              this.onProxySuccess(
                  this.proxyTransaction,
                  sentRequest.getCookie(),
                  sentRequest.getProxyClientTransaction());
            },
            err -> {
              try {
                ErrorCode errorCode;
                if (err instanceof DhruvaRuntimeException)
                  errorCode = ((DhruvaRuntimeException) err).getErrCode();
                else errorCode = ErrorCode.UNKNOWN_ERROR_REQ;
                if (err instanceof SipException && err.getCause() instanceof IOException)
                  errorCode = ErrorCode.DESTINATION_UNREACHABLE;
                logger.error(
                    "Error occurred while forwarding request with message: {}", err.getMessage());
                this.onProxyFailure(
                    this.proxyTransaction,
                    proxySIPRequest.getCookie(),
                    errorCode,
                    err.getMessage(),
                    err);
              } catch (Exception e) {
                logger.error(
                    "Unable to handle exception gracefully during proxy forward request", e);
                if (this.proxyTransaction instanceof ProxyTransaction)
                  this.onBestResponse((ProxyTransaction) this.proxyTransaction);
              }
            });
  }

  // Always access the cloned request
  public Mono<ProxySIPRequest> proxyForwardRequest(
      @NonNull Destination destination, @NonNull ProxySIPRequest proxySIPRequest, long timeToTry) {

    logger.debug("Entering proxyForwardRequest destination: " + destination);

    return Mono.just(proxySIPRequest)
        .name("proxyForwardRequest")
        .mapNotNull(proxyPostProcessor)
        .mapNotNull(fetchOutboundDestination())
        .flatMap((epMono) -> proxyToEndpoint(epMono, proxySIPRequest));
  }

  public Mono<ProxySIPRequest> proxyToEndpoint(
      Mono<EndPoint> endPointMono, ProxySIPRequest proxySIPRequest) {
    return endPointMono
        .mapNotNull(
            (ep) ->
                processOutboundDestination(proxySIPRequest, ep)
                    .orElseThrow(
                        () ->
                            new DhruvaRuntimeException(
                                ErrorCode.PROCESS_OUTBOUND_DESTINATION_ERR,
                                "Unable to process the outbound destination using endpoint received")))
        .mapNotNull(proxyTransactionProcessRequest)
        .flatMap(proxyRequest -> proxyTransaction.proxySendOutBoundRequest(proxyRequest));
  }

  private Function<ProxySIPRequest, ProxySIPRequest> proxyPostProcessor =
      proxySIPRequest -> {
        SIPRequest request = proxySIPRequest.getClonedRequest();
        Destination destination = proxySIPRequest.getDestination();
        ProxyCookieImpl cookie = new ProxyCookieImpl(destination, request);
        proxySIPRequest.setCookie(cookie);
        return proxySIPRequest;
      };

  private Optional<ProxySIPRequest> processOutboundDestination(
      ProxySIPRequest proxySIPRequest, EndPoint ep) {
    DhruvaNetwork network;
    proxySIPRequest.setDownstreamElement(ep);
    Destination destination = proxySIPRequest.getDestination();
    network = getNetwork(destination, ep);
    if (network != null) {
      if (destination.getDestinationType() != Destination.DestinationType.DEFAULT_SIP)
        try {
          addRouteFromEndpoint(proxySIPRequest.getClonedRequest(), ep);
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
            ErrorCode.PROXY_REQ_PROC_ERR, "Could not find the network to set to the request");
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

  private Function<ProxySIPRequest, Mono<EndPoint>> fetchOutboundDestination() {
    return proxySIPRequest -> {
      // Default Sip Routing
      Destination destination = proxySIPRequest.getDestination();
      if (destination.getDestinationType() == Destination.DestinationType.DEFAULT_SIP) {
        // return trunkService.getElementMono(proxySIPRequest);
        // if not ip host, call trunk
        return getDefaultSipEndpoint(destination, proxySIPRequest);
      } else if (destination.getDestinationType() == Destination.DestinationType.SERVER_GROUP
          || destination.getDestinationType() == Destination.DestinationType.A
          || destination.getDestinationType() == Destination.DestinationType.SRV) {
        return trunkService.getElementAsync(proxySIPRequest, destination);
      } else {
        return Mono.error(
            new DhruvaRuntimeException(
                ErrorCode.UNKNOWN_DESTINATION_TYPE,
                String.format("Unkown Destination type {}", destination.getDestinationType())));
      }
    };
  }

  public Mono<EndPoint> getDefaultSipEndpoint(
      Destination destination, ProxySIPRequest proxySIPRequest) {

    URI uri = getURIFromDestinationAndRequest(destination, proxySIPRequest);

    if (uri != null && uri.isSipURI()) {
      SipURI sipUrl = (SipURI) uri;
      String host = sipUrl.getMAddrParam();
      // Default transport type
      Transport routeTransport = Transport.TLS;
      // Set the default network
      DhruvaNetwork network = null;
      if (Objects.isNull(proxySIPRequest.getOutgoingNetwork())) {
        // In normal cases this should not happen, outgoing network should be set in message based
        // on route processing
        if (sipUrl.getTransportParam() != null) {
          routeTransport =
              Transport.getTypeFromString(sipUrl.getTransportParam()).orElse(Transport.TLS);
        }
        // If destination has network set
        network = destination.getNetwork();
      } else {
        Optional<DhruvaNetwork> optionalDhruvaNetwork =
            DhruvaNetwork.getNetwork(proxySIPRequest.getOutgoingNetwork());
        if (optionalDhruvaNetwork.isPresent()) {
          network = optionalDhruvaNetwork.get();
          routeTransport = ParseProxyParamUtil.getNetworkTransport(controllerConfig, network);
        }
      }
      if (host == null) {
        host = sipUrl.getHost();
      }

      if (!IPValidator.hostIsIPAddr(host)) {
        destination.setAddress(host);
        // Setting to SRV type, can be A as well
        destination.setDestinationType(Destination.DestinationType.SRV);
        logger.debug(
            "Setting destination type as : {}", Destination.DestinationType.SRV.toString());
        destination.setNetwork(network);
        return trunkService.getElementAsync(proxySIPRequest, destination);
      } else {
        // get the port from url(route or request uri), else set to default 5060
        EndPoint ep =
            new EndPoint(
                network == null ? null : network.getName(),
                host,
                sipUrl.getPort() > 0 ? sipUrl.getPort() : 5060,
                routeTransport);
        return Mono.just(ep);
      }
    }
    return Mono.error(new DhruvaRuntimeException(ErrorCode.REQUEST_PARSE_ERROR, "Not sip uri"));
  }

  private URI getURIFromDestinationAndRequest(
      Destination destination, ProxySIPRequest proxySIPRequest) {
    SIPRequest request = proxySIPRequest.getClonedRequest();

    URI uri;

    // If Route Header exists, get the uri from top route header
    // If route does not exist, get the uri set in destination object

    RouteHeader routeHeader = (RouteHeader) request.getHeader(RouteHeader.NAME);

    if (routeHeader != null) {
      uri = routeHeader.getAddress().getURI();
    } else {
      uri = destination.getUri();
    }

    return uri;
  }

  private Function<ProxySIPRequest, ProxySIPRequest> proxyTransactionProcessRequest =
      proxySIPRequest -> {
        try {
          ProxyParamsInterface pp = getProxyParams(proxySIPRequest);
          proxySIPRequest.setParams(pp);
          proxyTransaction.addProxyRecordRoute(proxySIPRequest);
          return proxyTransaction.proxyTo(proxySIPRequest);
        } catch (SipException | ParseException | DhruvaException | UnknownHostException e) {
          throw new DhruvaRuntimeException(
              ErrorCode.PROXY_REQ_PROC_ERR,
              "exception while doing proxy transaction processing",
              e);
        }
      };

  public ProxyParamsInterface getProxyParams(ProxySIPRequest proxySIPRequest)
      throws DhruvaException, ParseException, UnknownHostException {
    SIPRequest request = proxySIPRequest.getClonedRequest();

    Destination destination = proxySIPRequest.getDestination();

    Optional<DhruvaNetwork> optionalDhruvaNetwork =
        DhruvaNetwork.getNetwork(proxySIPRequest.getOutgoingNetwork());
    DhruvaNetwork outgoingNetwork;
    outgoingNetwork =
        optionalDhruvaNetwork.orElseThrow(
            () -> new DhruvaException("unable to find outgoing network"));

    SipURI uri = destination.getUri().isSipURI() ? (SipURI) destination.getUri().clone() : null;
    if (uri != null) {
      request.setRequestURI(uri);
    } else {
      request.setRequestURI(destination.getUri());
    }

    ProxyParams pp;
    pp = new ProxyParams(controllerConfig, outgoingNetwork.getName());
    ProxyParams dpp = pp;

    dpp.setProxyToAddress(proxySIPRequest.getDownstreamElement().getHost());
    request.setRemoteAddress(
        InetAddress.getByName(proxySIPRequest.getDownstreamElement().getHost()));
    dpp.setProxyToPort(proxySIPRequest.getDownstreamElement().getPort());
    request.setRemotePort(proxySIPRequest.getDownstreamElement().getPort());

    dpp.setProxyToProtocol(proxySIPRequest.getDownstreamElement().getProtocol());

    // setting the record route user portion
    pp.setRecordRouteUserParams(getRecordRouteParams(proxySIPRequest, true));

    if (pp.getProxyToAddress() == null
        && destination.getDestinationType() == Destination.DestinationType.DEFAULT_SIP)
      proxyRouteSetRemoteInfo(proxySIPRequest);

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

  public DhruvaNetwork getNetwork(Destination destination, EndPoint endPoint) {
    DhruvaNetwork network = null;

    if (endPoint.getNetwork() == null) {
      Optional<DhruvaNetwork> optionalDhruvaNetwork = getNetworkFromMyURI();
      if (optionalDhruvaNetwork.isPresent()) {
        network = optionalDhruvaNetwork.get();
      } else {
        logger.info("Dhruva getting network from location");
        network = getNetworkFromLocation(destination);
      }
      if (network == null) {
        logger.debug("Network not set on the location");
        network = destination.getDefaultNetwork();
        if (network == null) {
          // should never happen
          logger.debug("No default network specified for this request");
        }
      }
      // If still network is null, get from top most route header if available
      if (network == null) {
        Optional<DhruvaNetwork> optRouteNetwork = getNetworkFromMyRoute();
        if (optRouteNetwork.isPresent()) return optRouteNetwork.get();
      }
    } else {
      Optional<DhruvaNetwork> optionalDhruvaNetwork =
          DhruvaNetwork.getNetwork(endPoint.getNetwork());
      if (optionalDhruvaNetwork.isPresent()) network = optionalDhruvaNetwork.get();
    }

    return network;
  }

  public DhruvaNetwork getNetworkFromLocation(Destination destination) {
    // get the network from location if set
    DhruvaNetwork network = destination.getNetwork();

    if (network == null) {
      // get the network from the bindinginfo of the outgoing message if set
      // Dhruva does not have binding Info
      logger.info("network not set in for location object" + destination.toString());
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
      logger.info("Dhruva " + parsedProxyParams.toString());
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

  Function<ProxySIPRequest, ProxySIPRequest> processIncomingProxyRequestMAddr =
      proxySIPRequest -> {
        // MAddr handling of proxy
        SIPRequest request = proxySIPRequest.getRequest();
        URI uri = request.getRequestURI();
        if (uri.isSipURI()) {
          SipURI sipURI = (SipURI) uri;
          if (sipURI.getMAddrParam() != null) {
            if (controllerConfig.recognize(uri, false)) {
              sipURI.removeParameter("maddr");
              if (sipURI.getPort() >= 0) sipURI.removePort();
              if (sipURI.getTransportParam() != null) sipURI.removeParameter("transport");
            }
          }
        }
        return proxySIPRequest;
      };

  //  Function<ProxySIPRequest, ProxySIPRequest> processIncomingProxyRequestRoute =
  //      proxySIPRequest -> {
  //        SIPRequest request = proxySIPRequest.getRequest();
  //        ListIterator routes = request.getHeaders(RouteHeader.NAME);
  //        // Remove TopMost Route Header
  //        if (routes != null && routes.hasNext()) {
  //          RouteHeader routeHeader = (RouteHeader) routes.next();
  //          Address routeAddress = routeHeader.getAddress();
  //          URI routeURI = routeAddress.getURI();
  //          if (routeURI.isSipURI()) {
  //            SipURI routeSipURI = (SipURI) routeURI;
  //            String routeHost = routeSipURI.getHost();
  //            int routePort = routeSipURI.getPort();
  //            if (routePort == -1) routePort = 5060;
  //            String routeTransport = "udp";
  //            if (routeSipURI.getTransportParam() != null)
  //              routeTransport = routeSipURI.getTransportParam();
  //            boolean routeMatches =
  //                controllerConfig.recognize(
  //                    null,
  //                    routeHost,
  //                    routePort,
  //                    Transport.getTypeFromString(routeTransport).orElse(Transport.UDP));
  //            if (routeMatches) {
  //              logger.debug("removing top most route header that matches dhruva addr:",
  // routeSipURI);
  //              Optional<DhruvaNetwork> optionalDhruvaNetwork = getNetworkFromMyRoute();
  //              optionalDhruvaNetwork.ifPresent(
  //                  dhruvaNetwork -> proxySIPRequest.setOutgoingNetwork(dhruvaNetwork.getName()));
  //              request.removeFirst(RouteHeader.NAME);
  //            }
  //          } else logger.info("Route header is not sip uri");
  //        } else logger.debug("incoming request does not have route header");
  //        return proxySIPRequest;
  //      };

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
  Function<ProxySIPRequest, ProxySIPRequest> incomingProxyRequestFixLr =
      proxySIPRequest -> {
        SIPRequest request = proxySIPRequest.getRequest();
        // lrfix - user recognizes the rURI
        if (controllerConfig.recognize(request.getRequestURI(), true)) {
          RouteHeader lastRouteHeader = null;
          ListIterator<SIPHeader> routes = request.getHeaders(RouteHeader.NAME);
          if (routes != null && routes.hasNext()) {
            // Get to the last value
            do lastRouteHeader = (RouteHeader) routes.next();
            while (routes.hasNext());
          }
          if (lastRouteHeader == null) {
            throw new DhruvaRuntimeException(
                ErrorCode.PROXY_REQ_PROC_ERR, "Failed to fix the loose routing for the request");
          }

          proxySIPRequest.setLrFixUri(request.getRequestURI());
          request.setRequestURI(lastRouteHeader.getAddress().getURI());
          request.removeLast(RouteHeader.NAME);

          // Top Most Route Header
          RouteHeader topMostRouteHeader = (RouteHeader) request.getHeader(RouteHeader.NAME);

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
              logger.debug("removing top most route header that matches dhruva addr:", uri);
              Optional<DhruvaNetwork> optionalDhruvaNetwork = getNetworkFromMyRoute();
              optionalDhruvaNetwork.ifPresent(
                  dhruvaNetwork -> proxySIPRequest.setOutgoingNetwork(dhruvaNetwork.getName()));
              proxySIPRequest.setLrFixUri(uri);
              request.removeFirst(RouteHeader.NAME);
            }
          }
        } else {
          RouteHeader topRoute = (RouteHeader) request.getHeader(RouteHeader.NAME);
          if (topRoute != null) {
            URI uri = topRoute.getAddress().getURI();
            // user recognizes the top Route
            if (controllerConfig.recognize(uri, false)) {
              logger.debug("removing top most route header that matches dhruva addr:", uri);
              Optional<DhruvaNetwork> optionalDhruvaNetwork = getNetworkFromMyRoute();
              optionalDhruvaNetwork.ifPresent(
                  dhruvaNetwork -> proxySIPRequest.setOutgoingNetwork(dhruvaNetwork.getName()));

              proxySIPRequest.setLrFixUri(uri);
              request.removeFirst(RouteHeader.NAME);
            }
          }
        }
        return proxySIPRequest;
      };

  @Override
  public ProxySIPRequest onNewRequest(ProxySIPRequest proxySIPRequest) {

    SIPRequest request = proxySIPRequest.getRequest();
    ourRequest = proxySIPRequest;

    // As per RFC (Section 16.4), Route Information Preprocessing happens here
    proxySIPRequest =
        incomingProxyRequestFixLr.andThen(processIncomingProxyRequestMAddr).apply(proxySIPRequest);

    // Fetch the network from provider
    SipProvider sipProvider = proxySIPRequest.getProvider();
    Optional<String> networkFromProvider = DhruvaNetwork.getNetworkFromProvider(sipProvider);
    String network = networkFromProvider.orElseGet(() -> DhruvaNetwork.getDefault().getName());
    incomingNetwork = network;
    proxySIPRequest.setNetwork(network);

    // Save the request once all the preprocessing and validations are done.
    originalRequest = (SIPRequest) request.clone();

    // Create ProxyTransaction
    // ProxyTransaction internally creates ProxyServerTransaction
    proxyTransaction =
        createProxyTransaction(
            controllerConfig.isStateful(), request, serverTransaction, proxyFactory);

    if (proxyTransaction == null) {

      throw new DhruvaRuntimeException(
          ErrorCode.TRANSACTION_ERROR,
          "unable to create ProxyTransaction for new incoming" + request.getMethod() + " request");
    }

    proxySIPRequest.setProxyStatelessTransaction(proxyTransaction);
    proxySIPRequest.setMidCall(ProxyUtils.isMidDialogRequest(request));

    // Set the proxyTransaction in jain server transaction for future reference
    if (serverTransaction != null) {
      serverTransaction.setApplicationData(proxyTransaction);
    }

    return handleRequest().apply(proxySIPRequest);
  }

  public Function<ProxySIPRequest, ProxySIPRequest> handleRequest() {
    return proxySIPRequest -> {
      SIPRequest sipRequest = proxySIPRequest.getRequest();
      String requestType = sipRequest.getMethod();

      switch (requestType) {
        case Request.REGISTER:
          if (getDhruvaSIPConfigProperties().getSIPProxy().isProcessRegisterRequest()) {
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
              addAllowHeader(getDhruvaSIPConfigProperties().getAllowedMethods(), sipResponse);
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
            // Reply to received CANCEL request with 200 OK
            logger.info("Sending 200 (OK) response for received CANCEL");
            Response sipResponse =
                JainSipHelper.getMessageFactory().createResponse(Response.OK, sipRequest);
            ProxySendMessage.sendResponse(sipResponse, serverTransaction, sipProvider);
          } catch (Exception e) {
            throw new DhruvaRuntimeException(
                ErrorCode.SEND_RESPONSE_ERR,
                "Error sending 200 (OK) response for CANCEL request",
                e);
          }
          return proxySIPRequest;

        case Request.OPTIONS:
          try {
            logger.info("Processing OPTIONS request & responding with 200 OK");
            Response sipResponse =
                JainSipHelper.getMessageFactory().createResponse(Response.OK, sipRequest);
            addAllowHeader(getDhruvaSIPConfigProperties().getAllowedMethods(), sipResponse);
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
    System.out.println("---Response after adding supported headers: \n" + response);
  }

  private void addAcceptHeader(Response response) {
    try {
      Header acceptHeader =
          JainSipHelper.getHeaderFactory()
              .createHeader(
                  "Accept",
                  SipConstants.Content_Type_Application
                      + "/"
                      + SipConstants.ContentSubType.Sdp.toString());
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

  /** Overwrites a stateful ProxyStatelessTransaction with a ProxyTransaction(Statefull). */
  public boolean overwriteStatelessMode(SIPRequest request) {

    // Set it to null if it is stateless
    if (proxyTransaction != null && !(proxyTransaction instanceof ProxyTransaction)) {
      proxyTransaction = null;
    }

    logger.debug("Changing stateless proxy transaction to a stateful one");

    ViaList vias = request.getViaHeaders();
    if (null != vias) {
      ViaHeader topvia = (ViaHeader) vias.getFirst();
      if (controllerConfig.recognizeWithDns(
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

  private void proxyRouteSetRemoteInfo(ProxySIPRequest proxySIPRequest)
      throws UnknownHostException, ParseException {
    SIPRequest request = proxySIPRequest.getClonedRequest();

    URI routeToURI = null;
    String hostStr = "";
    int routePort = BindingInfo.REMOTE_PORT_UNSPECIFIED;
    int routeTransport = BindingInfo.BINDING_TRANSPORT_UNSPECIFIED.getValue();
    if (!mEmulate2543) {
      try {
        proxySIPRequest.lrEscape();
      } catch (ParseException e) {
        logger.error("caught exception while invoking lrEscape in proxyRouteSetRemoteInfo", e);
        throw e;
      }
    } else {
      routeToURI = request.getRequestURI();
    }

    if (routeToURI != null && routeToURI.isSipURI()) {
      SipURI url = (SipURI) routeToURI;
      if (request.getRemoteAddress() == null) {
        hostStr = (url.getMAddrParam());
        if (hostStr == null) {
          hostStr = url.getHost();
        }
        if (SipUtils.isHostIPAddr(hostStr)) {
          try {
            request.setRemoteAddress(InetAddress.getByName(hostStr));
          } catch (UnknownHostException e) {
            logger.warn("exception while setting remote address in proxy request", e);
            throw e;
          }
        }
      }

      routePort =
          request.getRemotePort() >= 0
              ? request.getRemotePort()
              : (url.getPort() >= 0 ? url.getPort() : BindingInfo.REMOTE_PORT_UNSPECIFIED);

      logger.info(
          "ProxyRoute:Setting remote address to: {} with port: {} & Remote transport: {}",
          hostStr,
          routePort,
          routeTransport);

      if (routePort != BindingInfo.REMOTE_PORT_UNSPECIFIED) {
        request.setRemotePort(routePort);
      }
    }
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

  @Override
  public void onProxySuccess(
      ProxyStatelessTransaction proxy, ProxyCookie cookie, ProxyClientTransaction trans) {
    logger.debug("Entering onProxySuccess()");
    // demarshall the cookie object
    ProxyCookieImpl cookieThing = (ProxyCookieImpl) cookie;
    // ProxyResponseInterface responseIf = cookieThing.getResponseInterface();
    Destination destination = cookieThing.getLocation();

    // See if this is a branch that should be cancelled
    int index;
    if ((index = unCancelledBranches.indexOf(destination)) != -1) {
      logger.debug("Found an uncancelled branch, cancelling it now ");
      trans.cancel();
      unCancelledBranches.remove(index);
      return;
    }

    // Store the mapping between this location and its client transaction so we
    // can easily cancel the branch if we need to
    // locToTransMap.put(location, trans);
    logger.debug("Leaving onProxySuccess()");
  }

  @Override
  public void onProxyFailure(
      ProxyStatelessTransaction proxyStatelessTransaction,
      ProxyCookie cookie,
      ErrorCode errorCode,
      String errorPhrase,
      Throwable exception) {
    logger.debug("Entering onProxyFailure():");
    SIPResponse sipResponse;
    try {
      sipResponse =
          ProxyResponseGenerator.createResponse(errorCode.getResponseCode(), getOriginalRequest());
    } catch (DhruvaException | ParseException ex) {
      throw new DhruvaRuntimeException(ErrorCode.CREATE_ERR_RESPONSE, ex.getMessage(), exception);
    }
    ProxySIPResponse proxySIPResponse;
    if (proxyStatelessTransaction instanceof ProxyTransaction) {
      ProxyTransaction proxy = (ProxyTransaction) proxyStatelessTransaction;
      ProxyClientTransaction proxyClientTransaction = proxy.getClientTransaction();
      proxySIPResponse =
          MessageConvertor.convertJainSipResponseMessageToDhruvaMessage(
              sipResponse,
              sipProvider,
              proxyClientTransaction == null ? null : proxyClientTransaction.getBranch(),
              new ExecutionContext());
      proxy.updateBestResponse(proxySIPResponse);
      if (errorCode.getAction().equals(ErrorCode.Action.SEND_ERR_RESPONSE)
          || !tryNextEndPoint(proxy, cookie, sipResponse.getStatusCode())) {
        logger.debug("onProxyFailure():sending out the best response received so far");
        filterResponse(proxy.getBestResponse());
      }
      if (proxyClientTransaction != null) {
        logger.debug("Remove Timer C from the ProxyClientTransaction due to ProxyFailure");
        proxyClientTransaction.removeTimerC();
      }
      return;
    }

    // TODO look into statelessTransaction as proxyResponse is not available for it
    if (!tryNextEndPoint(proxyStatelessTransaction, cookie, sipResponse.getStatusCode())) {
      logger.debug(
          "onProxyFailure(): Unable to send request to next endpoint, sending out the response created using errorCode received");
      proxySIPResponse =
          MessageConvertor.convertJainSipResponseMessageToDhruvaMessage(
              sipResponse, sipProvider, null, new ExecutionContext());
      filterResponse(proxySIPResponse);
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
  public void onFailureResponse(
      ProxyTransaction proxy,
      ProxyCookie cookie,
      ProxyClientTransaction trans,
      ProxySIPResponse proxySIPResponse) {
    logger.debug("Entering onFailureResponse():");
    if (!tryNextEndPoint(proxy, cookie, proxySIPResponse.getResponse().getStatusCode())) {
      logger.info("onFailureResponse(): No more endpoints left to try, sending out best response");
      filterResponse(proxy.getBestResponse());
    }
  }

  @Override
  public void onRedirectResponse(ProxySIPResponse proxySIPResponse) {
    logger.debug("In onRedirectResponse():");
    filterResponse(proxySIPResponse);
  }

  @Override
  public void onSuccessResponse(ProxyTransaction proxy, ProxySIPResponse proxySIPResponse) {
    logger.debug("Inside onSuccessResponse()");
    if (cancelBranchesAutomatically) {
      logger.info("Cancel branches automatically");
      proxy.cancel();
    }
    filterResponse(proxySIPResponse);
    logger.debug("Leaving onSuccessResponse()");
  }

  @Override
  public void onGlobalFailureResponse(ProxyTransaction proxy) {
    logger.debug("Inside onGlobalFailureResponse()");
    if (cancelBranchesAutomatically) {
      proxy.cancel();
    }
    filterResponse(proxy.getBestResponse());
    logger.debug("Leaving onGlobalFailureResponse()");
  }

  @Override
  public void onProvisionalResponse(
      ProxyTransaction proxy,
      ProxyCookie cookie,
      ProxyClientTransaction trans,
      ProxySIPResponse proxySIPResponse) {
    logger.debug("Inside onProvisionalResponse()");

    if (proxySIPResponse.getResponse().getStatusCode() == 100) return;
    filterResponse(proxySIPResponse);

    logger.debug("Leaving onProvisionalResponse()");
  }

  @Override
  public void onBestResponse(ProxyTransaction proxy) {
    logger.debug("Entering onBestResponse");
    if (cancelBranchesAutomatically) {
      logger.info("Cancel branches automatically");
      proxy.cancel();
    }
    filterResponse(proxy.getBestResponse());
  }

  @Override
  public void onRequestTimeOut(
      ProxyTransaction proxy, ProxyCookie cookie, ProxyClientTransaction trans) {

    logger.debug("Inside onRequestTimeout()");
    if (!tryNextEndPoint(proxy, cookie, Response.REQUEST_TIMEOUT)) {
      logger.info("onRequestTimeOut(): No more Endpoints left to try, sending out best response");
      filterResponse(proxy.getBestResponse());
    }
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
  public void onCancel(ProxyTransaction proxy, ProxyServerTransaction trans, SIPRequest cancel)
      throws DhruvaException {}

  @Override
  public void onResponse(ProxySIPResponse response) {
    // TODO LMA
    /*response.setNormalizationState(
    DsMessageLoggingInterface.SipMsgNormalizationState.POST_NORMALIZED);*/
  }

  /**
   * If the request was not sent to application, then all responses to that request will be sent out
   * using ProxyServerTransaction. Or if application is not interested in particular responseCode,
   * it will be sent out using ProxyServerTransaction. If not, response is sent to application using
   * the response handler provided by the application.
   *
   * @param proxySIPResponse
   * @return
   */
  private void filterResponse(@NonNull ProxySIPResponse proxySIPResponse) {
    try {
      if (sendRequestToApp && proxyAppConfig.getInterest(proxySIPResponse.getResponseClass())) {
        if (proxySIPResponse.getProxyInterface() == null) proxySIPResponse.setProxyInterface(this);
        proxyAppConfig.getResponseConsumer().accept(proxySIPResponse);
        return;
      }
      logger.debug("Sending out Response as Application was not interested in Request");
      proxyResponse(proxySIPResponse);
    } catch (Exception e) {
      ProxyTransaction trans = (ProxyTransaction) this.proxyTransaction;
      onResponseFailure(
          trans, trans.getServerTransaction(), ErrorCode.UNKNOWN_ERROR_RES, e.getMessage(), e);
    }
  }

  private boolean tryNextEndPoint(
      ProxyStatelessTransaction proxy, ProxyCookie cookie, int responseCode) {
    ProxyCookieImpl proxyCookie = (ProxyCookieImpl) cookie;
    Destination destination = proxyCookie.getLocation();
    // TODO destination is null? where to route??
    if (Objects.isNull(destination)) {
      logger.debug("location is not present in the cookie!!!");
      return false;
    }

    LBInterface lbInterface = destination.getLoadBalancer();
    if (Objects.isNull(lbInterface)) {
      logger.debug("No LB associated with the response");
      return false;
    }

    EndPoint nextEndPoint = trunkService.getNextElement(lbInterface, responseCode);
    if (Objects.isNull(nextEndPoint)) {
      logger.debug("tryNextEndPoint(): No more elements to try");
      return false;
    }

    logger.debug("tryNextEndPoint(): Sending request to next EndPoint received from Trunk");
    SIPRequest request = (SIPRequest) preprocessedRequest.clone();
    ourRequest.setClonedRequest(request);
    proxyToEndpoint(Mono.just(nextEndPoint), ourRequest)
        .subscribe(
            sentRequest -> {
              this.onProxySuccess(
                  proxy, sentRequest.getCookie(), sentRequest.getProxyClientTransaction());
            },
            err -> {
              try {
                ErrorCode errorCode = ErrorCode.UNKNOWN_ERROR_REQ;
                if (err instanceof DhruvaRuntimeException)
                  errorCode = ((DhruvaRuntimeException) err).getErrCode();
                if (err instanceof SipException && err.getCause() instanceof IOException)
                  errorCode = ErrorCode.DESTINATION_UNREACHABLE;
                logger.error(
                    "Exception occurred while sending request to next endpoint with message : ",
                    err.getMessage());
                this.onProxyFailure(
                    proxy, ourRequest.getCookie(), errorCode, err.getMessage(), err);
              } catch (Exception e) {
                logger.error(
                    "Unable to handle exception gracefully during proxy forward request", e);
                if (proxy instanceof ProxyTransaction)
                  this.onBestResponse((ProxyTransaction) proxy);
              }
            });
    return true;
  }
}
