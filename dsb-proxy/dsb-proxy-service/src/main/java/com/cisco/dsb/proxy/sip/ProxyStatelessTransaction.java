package com.cisco.dsb.proxy.sip;

import com.cisco.dsb.common.config.sip.CommonConfigurationProperties;
import com.cisco.dsb.common.exception.DhruvaRuntimeException;
import com.cisco.dsb.common.exception.ErrorCode;
import com.cisco.dsb.common.sip.util.SipUtils;
import com.cisco.dsb.common.transport.Transport;
import com.cisco.dsb.proxy.ControllerInterface;
import com.cisco.dsb.proxy.ProxyState;
import com.cisco.dsb.proxy.errors.InternalProxyErrorException;
import com.cisco.dsb.proxy.messaging.ProxySIPRequest;
import com.cisco.wx2.util.Utilities;
import gov.nist.javax.sip.header.Route;
import gov.nist.javax.sip.message.SIPRequest;
import gov.nist.javax.sip.message.SIPResponse;
import java.security.InvalidParameterException;
import java.text.ParseException;
import javax.sip.InvalidArgumentException;
import javax.sip.SipException;
import javax.sip.address.Address;
import javax.sip.address.SipURI;
import javax.sip.address.URI;
import javax.sip.header.RecordRouteHeader;
import javax.sip.header.RouteHeader;
import javax.sip.header.ViaHeader;
import javax.sip.message.Request;
import lombok.CustomLog;
import lombok.Getter;
import lombok.Setter;
import reactor.core.publisher.Mono;

/**
 * Represents a stateless proxy transaction. Stateless transaction only allows a very limited set of
 * operations to be performed on it, namely, it allows to proxy the request to a single destination
 * and nothing else, really. It will also report errors that occur synchronously with forwarding the
 * request DsProxyTransaction inherits from from StatelessTransaction and defines lots of additional
 * operations
 */
@CustomLog
public class ProxyStatelessTransaction implements ProxyTransactionInterface {

  // internal state constants
  /** The transaction is in the initial state when created */
  private static final int PROXY_INITIAL = 0;
  /** Once request is proxied, the transaction is in DONE state */
  private static final int PROXY_DONE = 1;

  // for NAT Traversal
  public static final String RPORT = "rport";
  public static final String RPORT_COOKIE_START = "0000";
  public static final String RPORT_COOKIE_END = "1111";

  /** is this transaction handles a stray ACK or CANCEL? */
  protected static final int NOT_STRAY = 0;

  protected static final int STRAY_ACK = 1;
  protected static final int STRAY_CANCEL = 2;

  @Setter private int strayRequest = NOT_STRAY;

  @Getter private boolean processVia = true;
  /** the request received from the initial server transaction */
  @Setter private SIPRequest originalRequest;

  // private DsProxyCookieInterface cookie;

  /**
   * the controller for this ProxyStatelessTransaction In stateless mode, only used to report
   * request forwarding errors
   */
  protected ControllerInterface controller;

  /**
   * Holds default values for various proxy settings This allows for dynamic proxy reconfiguration
   */
  private ProxyParamsInterface controllerConfig;

  private int state = PROXY_INITIAL;

  private static final int[] Transports_UDP = {
    Transport.UDP.getValue(), Transport.TCP.getValue(), Transport.TLS.getValue()
  };

  private static final int[] Transports_TCP = {
    Transport.TCP.getValue(), Transport.UDP.getValue(), Transport.TLS.getValue()
  };

  private static final int[] Transports_TLS = {
    Transport.TLS.getValue(), Transport.UDP.getValue(), Transport.TCP.getValue()
  };

  /**
   * the total number of branches forked up to now. This is different from branchesOutstanding and
   * is used to compute branch IDs of the branches; this variable is more important for
   * DsProxyTransaction
   */
  protected int n_branch = 0;

  //  private static final boolean m_simpleResolver =
  // DsConfigManager.getProperty(DsConfigManager.PROP_SIMPLE_RESOLVER,
  //                DsConfigManager.PROP_SIMPLE_RESOLVER_DEFAULT);

  // some private strings
  private static final String colon = ":";

  /** To be used with object pooling only */
  public ProxyStatelessTransaction() {}

  public ProxyStatelessTransaction(
      ControllerInterface controller, ProxyParamsInterface config, SIPRequest request)
      throws InternalProxyErrorException {
    init(controller, config, request);
  }

  /** Used for object pooling */
  public synchronized void init(
      ControllerInterface controller, ProxyParamsInterface config, SIPRequest request) {

    this.controller = controller;
    originalRequest = request;

    state = PROXY_INITIAL;
    controllerConfig = config; // save the configuration

    switch (request.getMethod()) {
      case Request.ACK:
        strayRequest = STRAY_ACK;
        break;
      case Request.CANCEL:
        strayRequest = STRAY_CANCEL;
        break;
      default:
        strayRequest = NOT_STRAY;
        break;
    }
    n_branch = 0;
    logger.info("New ProxyStatelessTransaction created");
  }

  /**
   * This method allows the controller to proxy to a specified URL using specified parameters the
   * code will not check to make sure the controller is not adding or removing critical headers like
   * To, From, Call-ID.
   *
   * @param proxySIPRequest request to send
   */
  public synchronized ProxySIPRequest proxyPostProcess(ProxySIPRequest proxySIPRequest) {

    SIPRequest request = proxySIPRequest.getRequest();

    ProxyCookie cookie = proxySIPRequest.getCookie();
    ProxyBranchParamsInterface params = proxySIPRequest.getParams();

    logger.debug("Entering proxyTo()");

    if (state != PROXY_INITIAL && strayRequest == NOT_STRAY) {
      throw new DhruvaRuntimeException(
          ErrorCode.INVALID_PARAM, "Cannot fork stateless transaction!");
    }

    try {
      prepareRequest(proxySIPRequest);
      state = PROXY_DONE;
      logger.debug("Leaving ProxyStatelessTransaction  proxyTo()");
      return proxySIPRequest;

    } catch (ParseException | InvalidArgumentException | SipException e) {
      logger.error("Got DsException in proxyTo()!", e);
      throw new DhruvaRuntimeException(ErrorCode.INVALID_PARAM, e.getMessage(), e);
    }
  }

  public synchronized void addProxyRecordRoute(ProxySIPRequest proxySIPRequest)
      throws SipException, ParseException {

    ProxyBranchParamsInterface params = proxySIPRequest.getParams();
    if (!params.doRecordRoute()) {
      logger.info("Proxy not adding self in Record-route");
      return;
    }

    // addRecordRoute(proxySIPRequest, getOriginalRequest().getRequestURI(), params);
    addRecordRoute(proxySIPRequest, getOriginalRequest().getRequestURI());
  }

  @Override
  public synchronized Mono<ProxySIPRequest> proxySendOutBoundRequest(
      ProxySIPRequest proxySIPRequest) {
    return Mono.empty();
  }

  /**
   * This is an internal implementation of proxying code. It's necessary to allow easy subclassing
   * in DsProxyTransaction, which will need to create DsProxyClientTransaction etc.
   *
   * @param proxySIPRequest request to send
   */
  protected synchronized void prepareRequest(ProxySIPRequest proxySIPRequest)
      throws InvalidParameterException, ParseException, SipException, InvalidArgumentException {
    ProxyBranchParamsInterface params = proxySIPRequest.getParams();
    logger.debug("Entering prepareRequest()");
    // Always get cloned request
    SIPRequest request = proxySIPRequest.getRequest();
    RouteHeader route;

    // make sure that we know how to handle this URL
    route = (RouteHeader) request.getHeader(Route.NAME);

    if (route == null) {
      if (!request.getRequestURI().isSipURI()) {

        throw new InvalidParameterException("Cannot proxy non-SIP URL!");
      }
    }

    if (processVia) {
      String branch = SipUtils.generateBranchId(request, true);
      ViaHeader via =
          controllerConfig.getViaHeader(
              proxySIPRequest.getOutgoingNetwork(), params.getHostNameType(ViaHeader.NAME), branch);
      request.addFirst(via);
      logger.info("Via field added: {}", via);
    }
    logger.debug("Leaving prepareRequest()");
  }

  /**
   * @return STRAY_ACK if the request is a stray ACK, STRAY_CANCEL if the request is a stray CANCEL
   *     NOT_STRAY - otherwise
   */
  protected int getStrayStatus() {
    return strayRequest;
  }

  /**
   * @return the original request that caused the creation of this transaction This method is not
   *     strictly necessary but it makes application's life somewhat easier as the application is
   *     not required to save the request for later reference NOTE: modifying this request will have
   *     unpredictable consequences on further operation of this transaction
   */
  public SIPRequest getOriginalRequest() {
    return originalRequest;
  }
  /*
    protected void addRecordRoute(DsSipRequest request, DsURI _requestURI, int protocol) {
      addRecordRoute(request, _requestURI, protocol, DsProxyParamsInterface.LISTEN_INTERNAL);
    }
  */
  // REDDY_RR_CHANGE
  // Insert itself in Record-Route if required
  protected void addRecordRoute(ProxySIPRequest proxySIPRequest, URI _requestURI)
      throws SipException {
    logger.debug("Entering addRecordRoute()");
    SIPRequest request = proxySIPRequest.getRequest();
    ProxyBranchParamsInterface params = proxySIPRequest.getParams();
    if (request.getMethod().equals(Request.INVITE)
        || request.getMethod().equals(Request.SUBSCRIBE)
        || request.getMethod().equals(Request.NOTIFY)) {

      String network = proxySIPRequest.getOutgoingNetwork();
      RecordRouteHeader rr =
          controllerConfig.getRecordRoute(
              params.getRecordRouteUserParams(),
              network,
              params.getHostNameType(RecordRouteHeader.NAME));
      if (rr != null) {
        /*
         * If the Request-URI contains a SIPS URI, or the topmost Route header field value (after
         * the post processing of bullet 6) contains a SIPS URI, the URI placed into the
         * Record-Route header field MUST be a SIPS URI.
         */
        Address routeAddress = rr.getAddress();
        URI routeURI = routeAddress.getURI();
        if (_requestURI.isSipURI() && routeURI.isSipURI()) {
          SipURI url = (SipURI) _requestURI;
          if (url.isSecure()) {
            SipURI rrURL = (SipURI) routeURI;
            rrURL.setSecure(true);
          }
        }

        logger.info("Adding record route" + rr);
        Utilities.Checks checks = new Utilities.Checks();
        checks.add("record route", rr);
        proxySIPRequest.getAppRecord().add(ProxyState.OUT_PROXY_RECORD_ROUTE_ADDED, checks);
        request.addFirst(rr);
      }
    }
    logger.debug("Leaving addRecordRoute()");
  }

  /**
   * This is a utility methods that creates a copy of the URL to make sure that forking does not get
   * broken
   */
  protected URI cloneURI(URI url) {

    return url;
  }

  /**
   * If <CODE>{@link CommonConfigurationProperties}</CODE> has processVia set, remove the top via
   * header, if no more Via is found return false, else return true.
   */
  public boolean processVia(SIPResponse response) {
    if (processVia) {
      response.removeFirst(ViaHeader.NAME);
      return response.getViaHeaders() != null && response.getViaHeaders().size() != 0;
    }
    return true;
  }

  /**
   * Returns the DsControllerInterface used for callbacks
   *
   * @return controller Controller to notify of proxy events.
   */
  public ControllerInterface getController() {
    return controller;
  }
}
