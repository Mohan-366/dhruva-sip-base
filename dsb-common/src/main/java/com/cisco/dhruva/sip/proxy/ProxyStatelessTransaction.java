package com.cisco.dhruva.sip.proxy;

import com.cisco.dhruva.sip.controller.util.ParseProxyParamUtil;
import com.cisco.dhruva.sip.proxy.errors.InternalProxyErrorException;
import com.cisco.dsb.common.messaging.ProxySIPRequest;
import com.cisco.dsb.sip.jain.JainSipHelper;
import com.cisco.dsb.sip.proxy.SipUtils;
import com.cisco.dsb.sip.stack.dto.DhruvaNetwork;
import com.cisco.dsb.transport.Transport;
import com.cisco.dsb.util.log.DhruvaLoggerFactory;
import com.cisco.dsb.util.log.Logger;
import gov.nist.javax.sip.header.Route;
import gov.nist.javax.sip.message.SIPRequest;
import java.net.InetAddress;
import java.security.InvalidParameterException;
import java.text.ParseException;
import java.util.Optional;
import javax.sip.*;
import javax.sip.address.Address;
import javax.sip.address.SipURI;
import javax.sip.address.URI;
import javax.sip.header.RecordRouteHeader;
import javax.sip.header.RouteHeader;
import javax.sip.header.ViaHeader;
import javax.sip.message.Request;
import lombok.Setter;
import reactor.core.publisher.Mono;

/**
 * Represents a stateless proxy transaction. Stateless transaction only allows a very limited set of
 * operations to be performed on it, namely, it allows to proxy the request to a single destination
 * and nothing else, really. It will also report errors that occur synchronously with forwarding the
 * request DsProxyTransaction inherits from from StatelessTransaction and defines lots of additional
 * operations
 */
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

  /** the request received from the initial server transaction */
  private SIPRequest originalRequest;

  // private DsProxyCookieInterface cookie;

  /**
   * the controller for this ProxyStatelessTransaction In stateless mode, only used to report
   * request forwarding errors
   */
  protected ControllerInterface controller;

  /**
   * Holds default values for various proxy settings This allows for dynamic proxy reconfiguration
   */
  private ProxyParamsInterface defaultParams;

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

  private static final Logger Log = DhruvaLoggerFactory.getLogger(ProxyStatelessTransaction.class);

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
    defaultParams = config; // save the configuration

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

    Log.info("ProxyStatelessTransaction created");
  }

  /**
   * This method allows the controller to proxy to a specified URL using specified parameters the
   * code will not check to make sure the controller is not adding or removing critical headers like
   * To, From, Call-ID.
   *
   * @param proxySIPRequest request to send
   */
  public synchronized ProxySIPRequest proxyTo(ProxySIPRequest proxySIPRequest) {

    SIPRequest request = proxySIPRequest.getClonedRequest();

    ProxyCookie cookie = proxySIPRequest.getCookie();
    ProxyBranchParamsInterface params = proxySIPRequest.getParams();

    Log.debug("Entering DsProxyStatelessTransaction proxyTo()");

    if (state != PROXY_INITIAL && strayRequest == NOT_STRAY) {
      controller.onProxyFailure(
          this,
          cookie,
          ControllerInterface.INVALID_PARAM,
          "Cannot fork stateless transaction!",
          null);
      return null;
    }

    try {
      prepareRequest(proxySIPRequest);

      Log.debug("proxying request");

      state = PROXY_DONE;
      Log.debug("Leaving DsProxyStatelessTransaction  proxyTo()");
      return proxySIPRequest;

    } catch (ParseException | InvalidArgumentException | SipException e) {
      Log.error("Got DsException in proxyTo()!", e);
      controller.onProxyFailure(this, cookie, ControllerInterface.INVALID_PARAM, e.getMessage(), e);
      return null;
    }

    // controller.onProxySuccess(this, cookie, null);

  }

  /**
   * Forces the specified source IP address and port onto the request to be forwarded
   *
   * @param request request to be forwarded
   * @param sourcePort source port to force; if <=0, the source port won't be forced
   * @param sourceAddress source address to force; if null, the source address won't be forced
   */
  protected void forceRequestSource(SIPRequest request, int sourcePort, InetAddress sourceAddress) {

    // source port is allowed to take 0 . This would allow
    // to bind to random ports on fly and send the request out.
    // otherwise it is not possible to use a fixed port to
    // send it to different address...Fixed for OATS

    if (sourcePort >= 0) request.setLocalPort(sourcePort);

    if (sourceAddress != null) request.setLocalAddress(sourceAddress);
  }

  public synchronized void addProxyRecordRoute(ProxySIPRequest proxySIPRequest)
      throws SipException, ParseException {

    ProxyBranchParamsInterface params = proxySIPRequest.getParams();
    if (!params.doRecordRoute()) {
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
    Log.debug("Entering prepareRequest()");
    // Always get cloned request
    SIPRequest request = proxySIPRequest.getClonedRequest();
    RouteHeader route;

    if (params == null) params = getDefaultParams();

    // make sure that we know how to handle this URL
    route = (RouteHeader) request.getHeader(Route.NAME);

    if (route == null) {
      if (!request.getRequestURI().isSipURI()) {
        if (params.getProxyToAddress() == null
            || params.getProxyToPort() <= 0
            || params.getProxyToProtocol() == Transport.NONE)
          throw new InvalidParameterException("Cannot proxy non-SIP URL!");
      }
    }

    // determine destination based on transport params etc
    Transport destTransport = Transport.NONE;

    destTransport = setRequestDestination(request, params);

    if (processVia()) {
      // invoke branch constructor with the URL and
      // add a Via field with this branch
      // stateful and stateless beahvior is different in cloudproxy, getBranchID
      // verify this
      String branch = SipUtils.generateBranchId();

      DhruvaNetwork network;
      Optional<DhruvaNetwork> optionalDhruvaNetwork =
          DhruvaNetwork.getNetwork(proxySIPRequest.getOutgoingNetwork());
      network = optionalDhruvaNetwork.orElseGet(DhruvaNetwork::getDefault);

      // DhruvaNetwork network = (DhruvaNetwork) request.getApplicationData();
      Log.debug("branch=" + branch);

      // The following block fixes up Via protocol in case Route is used
      ViaHeader via; // !!!!!!!!!!!!!!! double check this is correct
      int viaTransport = Transport.UDP.getValue();

      if (route == null) viaTransport = destTransport.getValue();
      else {
        if (route.getAddress().getURI().isSipURI()) {
          SipURI routeURI = (SipURI) route.getAddress().getURI();
          if (routeURI.getTransportParam() != null
              && Transport.getTypeFromString(routeURI.getTransportParam()).isPresent()) {
            viaTransport =
                Transport.getTypeFromString(routeURI.getTransportParam()).get().getValue();
          } else {
            viaTransport =
                ParseProxyParamUtil.getNetworkTransport(controller.getControllerConfig(), network)
                    .getValue();
          }
        }
      }

      // get the listen interface to put into Via.
      // Note that it doesn't really matter what we put in there
      // since the Low Level will try to update it when doing SRV
      ViaListenInterface listenIf = getPreferredListenIf(viaTransport, network.getName());
      Log.debug("Got interface " + listenIf + " for transport protocol " + viaTransport);

      if (listenIf != null) viaTransport = listenIf.getProtocol().getValue();

      assert listenIf != null;

      // if 'attachExternalIP' toggle is enabled, pass hostIp
      // else pass actual Ip address
      Optional<Transport> optionalTransport = Transport.getTypeFromInt(viaTransport);
      Transport viaHeaderTransport = Transport.UDP;
      if (optionalTransport.isPresent()) {
        viaHeaderTransport = optionalTransport.get();
      }

      via =
          JainSipHelper.getHeaderFactory()
              .createViaHeader(
                  com.cisco.dsb.sip.hostPort.HostPortUtil.convertLocalIpToHostInfo(listenIf),
                  listenIf.getPort(),
                  viaHeaderTransport.name(),
                  branch);

      forceRequestSource(request, listenIf.getSourcePort(), listenIf.getSourceAddress());

      // !!!!!!!!!! The above must be changed to allow reuse of client
      // TCP connections for responses!!!!
      // !!!!!! Also, the Via will need to be updated for SRV stuff!!!

      if (via.getParameter(RPORT) != null) {
        branch = setRPORTCookie(request, branch);
      }

      via.setBranch(branch);

      //            if (request.shouldCompress()) {
      //                via.setComp(DsSipConstants.BS_SIGCOMP);
      //            } else {
      //                DsTokenSipDictionary tokDic = request.shouldEncode();
      //                if (null != tokDic) via.setParameter(DsTokenSipConstants.s_TokParamName,
      // tokDic.getName());
      //            }
      request.addFirst(via);
      Log.info("Via field added: " + via);
    }

    Log.debug("Leaving prepareRequest()");
  }

  protected ViaListenInterface getPreferredListenIf(int protocol, String direction) {
    Log.debug("Entering getPreferredListenIf()");
    int[] transports;
    Optional<Transport> transportOptional = Transport.getTypeFromInt(protocol);
    Transport transport = Transport.UDP;
    if (transportOptional.isPresent()) transport = transportOptional.get();
    switch (transport) {
      case UDP:
      case NONE:
        // defaulting to UDP
        transports = Transports_UDP;
        break;
      case TCP:
        transports = Transports_TCP;
        break;
      case TLS:
        transports = Transports_TLS;
        break;
      default:
        transports = Transports_UDP;
        Log.warn("Unknown transport requested for Via: " + protocol);
        break;
    }

    ViaListenInterface listenIf = null;
    for (int t : transports) {
      Optional<Transport> optionalTransport = Transport.getTypeFromInt(t);
      if (optionalTransport.isPresent()) transport = optionalTransport.get();
      else continue;
      listenIf = getDefaultParams().getViaInterface(transport, direction);
      if (listenIf != null) break;
    }
    Log.debug("Leaving getPreferredListenIf(), returning " + listenIf);
    return listenIf;
  }

  /**
   * Looks at the ProxyParams and Request URI and sets request's destination (through
   * setConnection*) based on them
   *
   * @param request request to be forwarded
   * @param params BranchParamsInterface object
   * @return transport protocol (UDP or TCP) to be tried
   */
  protected Transport setRequestDestination(SIPRequest request, ProxyBranchParamsInterface params) {
    Log.debug("Entering setRequestDestination()");
    Transport destTransport = Transport.NONE;
    String destAddress = null;
    int destPort = -1; // , destTransport = DsSipTransportType.NONE;
    String direction;

    if (params.getProxyToAddress() != null) {
      destAddress = params.getProxyToAddress();
    }

    if (params.getProxyToPort() > 0) {
      destPort = params.getProxyToPort();
    }

    if (params.getProxyToProtocol() != Transport.NONE) {
      destTransport = params.getProxyToProtocol();
    }

    if (destTransport == Transport.NONE) {
      destTransport = getDefaultParams().getDefaultProtocol(); // for Via!!!
    }

    Log.debug("Leaving setRequestDestination()");
    return destTransport;
  }

  /** @return the default configuration settings used by this ProxyTransaction */
  protected ProxyParamsInterface getDefaultParams() {
    return defaultParams;
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
      throws SipException, ParseException {
    Log.debug("Entering addRecordRoute()");
    SIPRequest request = proxySIPRequest.getClonedRequest();
    ProxyBranchParamsInterface params = proxySIPRequest.getParams();
    if (request.getMethod().equals(Request.INVITE)
        || request.getMethod().equals(Request.SUBSCRIBE)
        || request.getMethod().equals(Request.NOTIFY)) {

      // Verify if this has to be outgoing network or incoming network
      // DSB TODO
      String network = proxySIPRequest.getOutgoingNetwork();
      RecordRouteHeader rr = getDefaultParams().getRecordRouteInterface(network);

      if (rr != null) {
        boolean cloned = false;
        /**
         * If the Request-URI contains a SIPS URI, or the topmost Route header field value (after
         * the post processing of bullet 6) contains a SIPS URI, the URI placed into the
         * Record-Route header field MUST be a SIPS URI.
         */
        Address routeAddress = rr.getAddress();
        URI routeURI = routeAddress.getURI();
        if (_requestURI.isSipURI() && routeURI.isSipURI()) {
          SipURI url = (SipURI) _requestURI;
          if (url.isSecure()) {
            rr = (RecordRouteHeader) rr.clone();
            cloned = true;
            SipURI rrURL = (SipURI) routeURI;
            rrURL.setSecure(true);
          }
        }

        //  TODO DSB              if (request.shouldCompress()) {

        SipURI uri = (SipURI) routeURI;
        uri.setUser(params.getRecordRouteUserParams());

        // replace Record-Route localIP with externalIP for public network
        uri.setHost(
            com.cisco.dsb.sip.hostPort.HostPortUtil.convertLocalIpToHostInfo(
                getDefaultParams(), uri));

        Log.info("Adding " + rr);
        request.addFirst(rr);
      }
    }
  }

  /**
   * This is a utility methods that creates a copy of the request to make sure that forking does not
   * get broken
   *
   * <p>It is a NOOP for stateless transaction because no forking can be done if we are stateless
   */
  protected SIPRequest cloneRequest() {

    SIPRequest clone = (SIPRequest) originalRequest.clone();

    return clone;
  }

  /**
   * This is a utility methods that creates a copy of the URL to make sure that forking does not get
   * broken
   */
  protected URI cloneURI(URI url) {

    return url;
  }

  public boolean processVia() {
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

  /**
   * Extracts a propritary cookie from the branch parameter of this server's Via header. The cookie
   * is at the end of the branch and is delimited by <code>RPORT_COOKIE_START</code> and <code>
   * RPORT_COOKIE_END</code>.
   *
   * @param branch The branch parameter of a via
   * @return The rport cookie formatted as <code>&lt;ip&gt;:&lt;port&gt;</code>
   */
  public static String getRPORTCookie(String branch) {
    Log.debug("Entering getRPORTCookie(" + branch + ")");
    String rportCookie = null;
    if (branch != null) {
      if (branch.endsWith(RPORT_COOKIE_END)) {
        int i = -1;
        int lastIndex = -1;
        while ((i = branch.indexOf(RPORT_COOKIE_START, lastIndex + 1)) != -1) {
          lastIndex = i;
        }
        rportCookie =
            branch.substring(
                lastIndex + RPORT_COOKIE_START.length(),
                branch.length() - RPORT_COOKIE_END.length());
      }
    }
    Log.debug("Leaving getRPORTCookie(), returning " + rportCookie);
    return rportCookie;
  }

  /**
   * Appends the rport cookie to the given Via branch id.
   *
   * @param bindingInfo The binding info from the request. The address from <code>
   *     DsBindingInfo.getLocalAddress()</code> and the port from <code>DsBindingInfo.getLocalPort()
   *     </code> are used to create the rport cookie.
   * @param branch The current branch id of this server's Via header. This object is not modified. A
   *     copy of the <code>DsByteString</code> is created and all modifications are performed on the
   *     copy.
   * @return the modified branch id consisting of the original branch followed by <code>
   *     RPORT_COOKIE_START</code>&lt;ip&gt;:&lt;port&gt;<code>RPORT_COOKIE_END</code>
   */
  public static String setRPORTCookie(SIPRequest bindingInfo, String branch) {
    Log.debug("Entering setRPORTCookie(" + bindingInfo + ", " + branch + ")");
    StringBuilder newBranch = new StringBuilder(branch);
    String localAddress = bindingInfo.getLocalAddress().getHostAddress();
    int port = bindingInfo.getLocalPort();
    StringBuffer rportCookie = new StringBuffer(localAddress);
    rportCookie.append(colon).append(port);
    String rportCookieBS = new String(rportCookie);
    Log.debug("Adding the rport cookie " + rportCookie + " to the branch id for NAT traversal");

    newBranch.append(RPORT_COOKIE_START);
    newBranch.append(rportCookieBS);
    newBranch.append(RPORT_COOKIE_END);

    Log.debug("Leaving setRPORTCookie(), returning " + newBranch);
    return newBranch.toString();
  }
}
