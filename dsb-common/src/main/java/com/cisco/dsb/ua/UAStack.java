package com.cisco.dsb.ua;

import com.cisco.dsb.SipProperties;
import com.cisco.dsb.common.dns.DnsLookup;
import com.cisco.dsb.sip.jain.JainSipHelper;
import com.cisco.dsb.sip.jain.JainStackInitializer;
import com.cisco.dsb.sip.util.SipConstants;
import com.cisco.dsb.sip.util.SipExtraHeaderGenerator;
import com.cisco.dsb.sip.util.SipTokens;
import com.cisco.dsb.util.log.LogUtils;
import gov.nist.core.net.NetworkLayer;
import gov.nist.javax.sip.SipStackExt;
import gov.nist.javax.sip.header.Server;
import gov.nist.javax.sip.message.SIPRequest;
import gov.nist.javax.sip.stack.SIPStackTimerTask;
import gov.nist.javax.sip.stack.SIPTransactionStack;
import java.text.ParseException;
import java.util.*;
import javax.net.ssl.KeyManager;
import javax.sip.*;
import javax.sip.address.Address;
import javax.sip.address.SipURI;
import javax.sip.address.URI;
import javax.sip.header.*;
import javax.sip.message.Message;
import javax.sip.message.Request;
import javax.sip.message.Response;
import org.apache.commons.lang3.tuple.Pair;

// This class is not loaded by Spring so autowire does not work.
// TODO: Implement SipStackWrapper and uncomment the commented methods.
//  Since lots of l2sip code are coming in as part of this, have commented them

public class UAStack /*implements SipStackWrapper*/ {

  // private static Logger logger =
  // L2SipLogger.getLogger(com.cisco.wx2.sip.sipstack.sip.jain.UAStack.class);

  private SipStackExt sipStack;

  private SipProvider sipTransportProvider;

  private ListeningPoint listeningPoint;

  private final MaxForwardsHeader maxForwardsHeader;
  private final Header userAgentHeader;
  private final Header acceptHeader;
  private final AllowEventsHeader allowEventsHeader;
  private final Header callInfoHeader;
  private final SipURI contactURI;
  private final ContactHeader inBoundCallContactHeader;
  private final ContactHeader outBoundCallContactHeader;
  private final ContactHeader outBoundTestCallContactHeader;

  private final SupportedHeader supportedHeader;
  private final SupportedHeader supportedHeaderWithTimer;

  // For diagnostics service: Contains our
  // public IP and port that we listen on
  // private final NodeInfo localNodeInfo;

  private String contactName = "l2sip-UA";

  private final Server l2SipInstance;
  private final SIPRouteHeaderProvider sipRouteHeaderProvider;

  private int minSE;

  // White list of sites to which the allow header will advertise `update`.
  private final Set<String> sitesToAdvertiseUpdates;

  // Flag to enable allow header for all the calls.
  private final boolean advertiseUpdatesForAllCalls;

  private final Set<String> sitesToAdvertiseCallInfo;

  // Parser for extracting HostKey from the SIPUri
  // private final MeetingIdHostKeyParser meetingIdHostKeyParser;

  protected UAStack(
      SipProperties sipProperties,
      SipListener sipListener,
      String stackName,
      SIPRouteHeaderProvider sipRouteHeaderProvider,
      KeyManager keyManager,
      DnsLookup dnsLookup)
      throws Exception {

    this.l2SipInstance = sipProperties.getSipInstance();
    this.sipRouteHeaderProvider = sipRouteHeaderProvider;
    this.minSE = sipProperties.getMinSE();

    Properties properties = UAStackFactory.getDefaultUAStackProperties(stackName);
    sipStack =
        (SipStackExt)
            JainStackInitializer.createSipStack(
                JainSipHelper.getSipFactory(),
                JainSipHelper.getSipFactory().getPathName(),
                properties);
    ;

    String sipStackAddress = sipProperties.getLocalUAAddress();
    int sipStackPort = sipProperties.getLocalUAPort();

    NetworkLayer networkLayer = ((SIPTransactionStack) sipStack).getNetworkLayer();
    /*if (networkLayer instanceof L2SipNetworkLayer) {
      logger.info("initializing SSLContext in L2SipNetworkLayer");
      ((L2SipNetworkLayer) networkLayer).init(sipProperties, L2SipTrustManager.getTrustAllCertsInstance(), keyManager,
        dnsLookup, false, false);
    }*/

    listeningPoint =
        JainStackInitializer.createListeningPointForSipStack(
            sipStack,
            sipStackAddress,
            sipStackPort,
            sipProperties.getUAProxyInternalTransport().toString());
    // localNodeInfo = DiagUtils.nodeInfo(sipStackAddress, listeningPoint.getPort());

    sipTransportProvider =
        JainStackInitializer.createSipProviderForListenPoint(sipStack, listeningPoint);
    sipTransportProvider.addSipListener(sipListener);

    /*logger.info(" Sip stack with name  \"{}\" is initialized and listening at {}:{}/{}",
          stackName, sipStackAddress, listeningPoint.getPort(), listeningPoint.getTransport());
    */
    // initalizing local final variable
    maxForwardsHeader = JainSipHelper.getHeaderFactory().createMaxForwardsHeader(70);
    userAgentHeader = JainSipHelper.getHeaderFactory().createHeader("User-Agent", "Cisco-L2SIP");
    acceptHeader =
        JainSipHelper.getHeaderFactory()
            .createHeader(
                "Accept",
                SipConstants.Content_Type_Application
                    + "/"
                    + SipConstants.ContentSubType.Sdp.toString());
    allowEventsHeader =
        JainSipHelper.getHeaderFactory().createAllowEventsHeader(SipConstants.Kpml_Event_Type);

    String optionTags = "replaces";
    String optionTagsWithTimer = optionTags + SipTokens.Comma + SipConstants.Timer_Option_Tag;

    supportedHeaderWithTimer =
        JainSipHelper.getHeaderFactory().createSupportedHeader(optionTagsWithTimer);
    supportedHeader = JainSipHelper.getHeaderFactory().createSupportedHeader(optionTags);

    // Create the contact uri.
    contactURI = JainSipHelper.getAddressFactory().createSipURI(contactName, sipStackAddress);
    contactURI.setTransportParam(listeningPoint.getTransport());
    contactURI.setPort(listeningPoint.getPort());

    // Create contact header.
    Address contactAddress = JainSipHelper.getAddressFactory().createAddress(contactURI);
    // Inbound call contact header
    inBoundCallContactHeader = JainSipHelper.getHeaderFactory().createContactHeader(contactAddress);
    // outbound call contact header
    outBoundCallContactHeader =
        JainSipHelper.getHeaderFactory().createContactHeader(contactAddress);
    // add call-type parameter for outbound call call contact for avoiding loopback.
    outBoundCallContactHeader.setParameter(
        SipConstants.Loopback_Parameter, SipConstants.Loopback_Parameter_Value);
    // Deprecated parameter for avoiding loopbacks.
    if (sipProperties.includeOldLoopbackParam()) {
      outBoundCallContactHeader.setParameter(
          SipConstants.Old_Loopback_Parameter, SipConstants.Old_Loopback_Parameter_Value);
    }

    outBoundTestCallContactHeader =
        JainSipHelper.getHeaderFactory().createContactHeader(contactAddress);
    // add call-type parameter for outbound call call contact for avoiding loopback.
    outBoundTestCallContactHeader.setParameter(
        SipConstants.Loopback_Parameter, SipConstants.Loopback_Parameter_Test_Value);
    // Deprecated parameter for avoiding loopbacks
    if (sipProperties.includeOldLoopbackParam()) {
      outBoundTestCallContactHeader.setParameter(
          SipConstants.Old_Loopback_Parameter, SipConstants.Old_Loopback_Parameter_Test_Value);
    }

    sitesToAdvertiseUpdates = sipProperties.getSitesToAdvertiseUpdates();
    advertiseUpdatesForAllCalls = sipProperties.advertiseUpdateForAllCalls();
    sitesToAdvertiseCallInfo = sipProperties.getSitesToAdvertiseCallInfo();

    callInfoHeader =
        JainSipHelper.getHeaderFactory()
            .createHeader(
                SipConstants.CALL_INFO_HEADER_NAME,
                "<urn:x-cisco-remotecc:callinfo>;"
                    + "x-cisco-video-traffic-class="
                    + sipProperties.getCiscoVideoTrafficClass());

    // Using the properties, Create the parser when the application starts.
    // meetingIdHostKeyParser = sipProperties.meetingIdHostKeyParsingSettings().getParser();
  }

  /**
   * This method initiates the shutdown of the stack. The stack will terminate all ongoing
   * transactions, without providing notification to the listener, close all listening points and
   * release all resources associated with this stack.
   */
  public void stop() {
    // logger.info("Stopping sipstack");
    sipStack.stop();
  }

  @SuppressWarnings("checkstyle:parameterassignment")
  public void sendResponse(
      ServerTransaction st, Request req, SessionId sipSessionId, int statusCode) {
    Response response;
    try {
      // reply with statuscode
      response = JainSipHelper.getMessageFactory().createResponse(statusCode, req);

      addAdditionalCallHeaders(response, null, sipSessionId, null);

      // Add Allow: header for INVITE and OPTIONS only
      if (req.getMethod().equals(SIPRequest.INVITE) || req.getMethod().equals(SIPRequest.OPTIONS)) {
        // JainSipHelper.addAllowHeader(response, (ToHeader)req.getHeader(ToHeader.NAME),
        // sitesToAdvertiseUpdates,advertiseUpdatesForAllCalls);
      }

      if (req.getMethod().equals(SIPRequest.INVITE)) {
        if (JainSipHelper.isWhiteListedHost(
            (ToHeader) req.getHeader(ToHeader.NAME),
            sitesToAdvertiseCallInfo,
            "sitesToAdvertiseCallInfo")) {
          response.addHeader(callInfoHeader);
        }
      }

      // Get a new server transaction if needed
      if (st == null) {
        st = sipTransportProvider.getNewServerTransaction(req);
      }

      st.sendResponse(response);
    } catch (Throwable e) {
      // logger.info("Can't send response.", e);
    }
  }

  /*public void sendResponse(ServerTransaction st, Request req, int statusCode, SipCall sipCall) {
    sendResponse(st, req, statusCode, sipCall, null, false);
  }*/

  /*
  @SuppressWarnings("checkstyle:parameterassignment")
  public void sendResponse(ServerTransaction st, Request req, int statusCode, SipCall sipCall, SipReason reason, boolean retry) {
    Response response;
    try {
      //reply with statuscode
      response = JainSipHelper.getMessageFactory().createResponse(statusCode, req);

      if (sipCall != null) {
        //update TO header - sipcall will have the new tag required to be sent in response for the request.
        response.setHeader(getHeader(sipCall.getL2sipParty(), ToHeader.NAME));
        addAdditionalCallHeaders(response, sipCall.getHeaders(), sipCall.getSipSessionId(), sipCall.getApplicationData());
      } else if(req.getHeader(SessionId.HEADER_NAME) != null) {
        // TODO SESSIONID We should not blindly accept the incoming Session-Id. What scenario do we hit this
        addAdditionalCallHeaders(response, null, SessionId.extractFromSipEventAndFlip(req), null);
      }

      //If response requires to add retry-after header, add retry-after header with 2 seconds.
      if(retry) {
        Header header = JainSipHelper.getHeaderFactory().createRetryAfterHeader(2);
        response.addHeader(header);
      }

      // JainSip doesn't know about 422 so we need to set the reason phrase here
      if (statusCode == 422) {
        response.setReasonPhrase("Session Interval Too Small");

        if (sipCall.getSessionRefreshData() == null) {
          // we should never get here
          logger.warn("Sending a 422 response without MinSE value is bad.");
        } else {
          int minSE = sipCall.getSessionRefreshData().getMinSE();
          MinSE minSeHeader = new MinSE();

          try {
            minSeHeader.setExpires(minSE);
            response.addHeader(minSeHeader);
          } catch (InvalidArgumentException e) {
            logger.error("failed creating headers. minSE: {}", minSE, e);
          }
        }
      } else if (JainSipHelper.isSuccessCode(statusCode) && Request.UPDATE.equals(req.getMethod())) {
        // WEBEX-105471 2xx response to UPDATE needs contact header
        addSipStackDefaultHeaders(sipCall, response, req);
      }

      JainSipHelper.addReasonIfNecessary(response, reason);

      // Add Expires: header for SUBSCRIBE only
      if (req.getMethod().equals(SIPRequest.SUBSCRIBE)) {
        response.setExpires(req.getExpires());
      }

      try {
        //Get a new server transaction if needed
        if (st == null) {
          st = sipTransportProvider.getNewServerTransaction(req);
        }
        st.sendResponse(response);
      } catch (Exception e) {
        logger.info("Exception while sending a response  using the server transaction." + "Trying to send a response from the sip stack provider", e);
        sipTransportProvider.sendResponse(response);
      }
    } catch (Throwable e) {
       logger.info("Can't send reply", e);
    }
  }*/

  /*@SuppressWarnings("checkstyle:parameterassignment")
  public void sendInviteResponse(ServerTransaction st, Request request, int statusCode, SipCall sipCall, OutboundCallParams outboundCallParams) {
    Response response;
    try {

      //reply with status code
      response = JainSipHelper.getMessageFactory().createResponse(statusCode, request);

      if (sipCall != null) {
        //update TO header - sipcall will have the new tag required to be sent in response for the request.
        response.setHeader(getHeader(sipCall.getL2sipParty(), ToHeader.NAME));

        addAdditionalCallHeaders(response, sipCall.getHeaders(), sipCall.getSipSessionId(), sipCall
          .getApplicationData());

        addSipStackDefaultHeaders(sipCall, response, outboundCallParams);
        addSessionRefreshHeaders(sipCall, response);

        // Add Allow: header for INVITE and OPTIONS only
        JainSipHelper.addAllowHeader(response, (ToHeader)request.getHeader(ToHeader.NAME), sitesToAdvertiseUpdates,
          advertiseUpdatesForAllCalls);

        if( request.getMethod().equals(SIPRequest.INVITE)) {
          if ( JainSipHelper.isWhiteListedHost((ToHeader)request.getHeader(ToHeader.NAME), sitesToAdvertiseCallInfo,
            "sitesToAdvertiseCallInfo")) {
            response.addHeader(callInfoHeader);
          }
        }

        // Advertise support of kmpl event subscriptions
        response.addHeader(allowEventsHeader);

        addSupportedHeader(sipCall, response);

        addContent(response, sipCall.getL2sipParty().getSdp(), SipConstants.Content_Type_Application,
          SipConstants.ContentSubType.Sdp.toString());
      }

      if (st == null) {
        st = sipTransportProvider.getNewServerTransaction(request);
      }
      st.sendResponse(response);
    } catch (Throwable e) {
      logger.info("Can't send response", e);
      DiagnosticsUtil.renderNote(sipCall.getCallId(), String.format("Can't send reply %s", e));
    }
  }*/

  /** Function to set session refresh headers based on sipCall's SessionRefreshData */
  /*private void addSessionRefreshHeaders(SipCall sipCall, Message message) {
    if (sipCall.getSessionRefreshEnabled().equals(SipCall.SessionRefreshEnabled.DISABLED)) {
      logger.debug("session refresh is not enabled for this call");
      return;
    }

    SessionRefreshData refreshData = sipCall.getSessionRefreshData();

    logger.info("Adding session refresh headers for call with refresh data: {}", refreshData);

    SessionExpires sessionExpiresHeader = null;
    MinSE minSeHeader = null;

    try {
      // If refreshData is null (session expires isn't requested for this call yet), we still want to add the
      // Min-SE header to requests. If we do not, then a response could include a SessionExpires header with a
      // session expiration interval that is smaller than we want to support, and we would have no chance to send
      // a 422 back. By including the Min-SE header, we ensure that
      if (refreshData == null) {
        if (message instanceof Request) {
          minSeHeader = new MinSE();
          minSeHeader.setExpires(minSE);
        }
      } else {
        sessionExpiresHeader = new SessionExpires();
        sessionExpiresHeader.setExpires(refreshData.getSessionInterval());
        String refresher = refreshData.isRemoteRefresher() != message instanceof Request ? SipConstants.UAC : SipConstants.UAS;
        sessionExpiresHeader.setRefresher(refresher);
        minSeHeader = new MinSE();
        minSeHeader.setExpires(refreshData.getMinSE());
      }
    } catch (InvalidArgumentException e) {
      logger.error("failed creating headers for session expires data: {}", refreshData, e);
      return;
    }

    if (sessionExpiresHeader != null) {
      message.addHeader(sessionExpiresHeader);
    }
    if (minSeHeader != null) {
      message.addHeader(minSeHeader);
    }
  }*/

  /*@Override
  public InboundCallBuilder getInboundCallBuilder() {
    //pass the parser to the call builder for parsing the URI when building the new inbound call.
    return new InboundCallBuilder(l2SipInstance, meetingIdHostKeyParser);
  }*/

  private void addAdditionalCallHeaders(
      Message message, Map<String, Header> headers, SessionId sessionId, Object applicationData)
      throws ParseException {
    if (sessionId != null) {
      Header sessionIdHeader =
          JainSipHelper.getHeaderFactory()
              .createHeader(SessionId.HEADER_NAME, sessionId.getHeaderValue());
      message.addHeader(sessionIdHeader);
    }
    if (applicationData != null) {
      if (applicationData instanceof SipExtraHeaderGenerator) {
        SipExtraHeaderGenerator headerGenerator = (SipExtraHeaderGenerator) applicationData;
        List<Pair<String, String>> applicationHeaders = headerGenerator.getExtraHeaders();
        for (Pair<String, String> applicationHeader : applicationHeaders) {
          Header header =
              JainSipHelper.getHeaderFactory()
                  .createHeader(applicationHeader.getKey(), applicationHeader.getValue());
          message.addHeader(header);
        }
      }
    }
    if (headers != null && !headers.isEmpty()) {
      Set<String> headersKeySet = headers.keySet();
      Iterator<String> keysIterator = headersKeySet.iterator();
      while (keysIterator.hasNext()) {
        Header header = headers.get(keysIterator.next());
        if (message.getHeader(header.getName()) == null) {
          message.addHeader(header);
        }
      }
    }
  }

  /*private void addSipStackDefaultHeaders(SipCall sipCall, Response response, Request request) {
    OutboundCallParams outboundCallParams = new OutboundCallParams();
    if (JainSipHelper.hasCallIdParameter(request.getHeader(ContactHeader.NAME))) {
      outboundCallParams.setCallIdOnContact(true);
    }

    addSipStackDefaultHeaders(sipCall, response, outboundCallParams);
  }*/

  /*private void addSipStackDefaultHeaders(SipCall sipCall, Message message, OutboundCallParams outboundCallParams) {
    //Add L2SIP headers
    message.addHeader(userAgentHeader);
    message.addHeader(acceptHeader);

    if (sipCall != null && sipCall.getHeader(ContactHeader.NAME) == null) {
      message.addHeader(getContactHeader(sipCall.getCallDirection(), sipCall.getCallId(), outboundCallParams));
    }
  }*/

  /*private Header getHeader(Party party, String headerName) throws ParseException {
    return getHeader(party.getHeader(), headerName);
  }*/

  /**
   * Util to convert one header to other. In cases, where TO has to be converted to From, this util
   * can be used.
   *
   * @param header
   * @param headerName
   * @return
   * @throws ParseException
   */
  private Header getHeader(Header header, String headerName) throws ParseException {
    String headerLine = header.toString().substring(header.toString().indexOf(' ') + 1);
    return JainSipHelper.getHeaderFactory().createHeader(headerName, headerLine);
  }

  /*private SIPRequest getRequest(SipCall sipCall, String method, String content, String contentType,
                              String subContentType, OutboundCallParams outboundCallParams)
  throws ParseException, InvalidArgumentException, SipException {

  final Request request;

  //From header for request
  FromHeader fromHeader = (FromHeader) getHeader(sipCall.getL2sipParty(), FromHeader.NAME);

  //To header for request
  ToHeader toHeader = (ToHeader) getHeader(sipCall.getSipParty(), ToHeader.NAME);

  */
  /**
   * Create request URI and route headers by following the rules in
   * https://tools.ietf.org/html/rfc3261#section-12.2
   *
   * <p>The UAC uses the remote target and route set to build the Request-URI and Route header field
   * of the request.
   *
   * <p>If the route set is empty, the UAC MUST place the remote target URI into the Request-URI.
   * The UAC MUST NOT add a Route header field to the request.
   *
   * <p>If the route set is not empty, and the first URI in the route set contains the lr parameter
   * (see Section 19.1.1), the UAC MUST place the remote target URI into the Request-URI and MUST
   * include a Route header field containing the route set values in order, including all
   * parameters.
   *
   * <p>If the route set is not empty, and its first URI does not contain the lr parameter, the UAC
   * MUST place the first URI from the route set into the Request-URI, stripping any parameters that
   * are not allowed in a Request-URI. The UAC MUST add a Route header field containing the
   * remainder of the route set values in order, including all parameters. The UAC MUST then place
   * the remote target URI into the Route header field as the last value.
   */
  /*
    //get sip side contact uri to be used as request uri
    SipURI requestUri = null;
    List<RouteHeader> routeHeaders = getRouteHeaders(sipCall.getSipServiceType(), sipCall.getRouteHeaders(), sipCall.getSipParty(), toHeader);

    boolean setLoopbackParameter = (outboundCallParams == null) ? false : outboundCallParams.isLoopbackParamOnRequestUri();
    String xCiscoCallType = (outboundCallParams == null) || outboundCallParams.getRequestUriXCiscoCallType() == null
      ? null : outboundCallParams.getRequestUriXCiscoCallType().getUriParamValue();

    requestUri = getRequestUri(sipCall.getSipParty(), toHeader, setLoopbackParameter, xCiscoCallType);

    //Create call id header
    CallIdHeader callIdHeader = JainSipHelper.getHeaderFactory().createCallIdHeader(sipCall.getCallId());

    //create via header list.
    List<ViaHeader> viaList = new ArrayList<>();

    //create cseq header
    CSeqHeader cSeqHeader = JainSipHelper.getHeaderFactory().createCSeqHeader(sipCall.getL2sipParty().getNewCseq(), method);

    //create request
    request = JainSipHelper.getMessageFactory().createRequest(requestUri, cSeqHeader.getMethod(), callIdHeader,
      cSeqHeader, fromHeader, toHeader, viaList, maxForwardsHeader);

    routeHeaders.forEach(request::addHeader);

    // TODO: is this redundant with the addAdditionalCallHeaders called below???
    if (sipCall.getHeaders() != null && !sipCall.getHeaders().isEmpty()) {
      Iterator<String> headerItr = sipCall.getHeaders().keySet().iterator();
      while (headerItr.hasNext()) {
        request.addHeader(sipCall.getHeader(headerItr.next()));
      }
    }

    if (method.equals(SIPRequest.INVITE) &&
      sipCall.getSipServiceType() == SipCall.SipServiceType.Enterprise &&
      sipCall.getCallDirection() == CallDirection.OUTBOUND) {
      request.addHeader(new PAssertedIdentity((AddressImpl)fromHeader.getAddress()));
    }

    if (method.equals(SIPRequest.INVITE) && outboundCallParams != null && outboundCallParams.isSeparateTlsConnectionPerCall()) {
      request.addHeader(JainSipHelper.getHeaderFactory().createHeader(SipConstants.SEPARATE_TLS_CONNECTION_PER_CALL, "true"));
    }

    //Add sip stack headers.
    addSipStackDefaultHeaders(sipCall, request, outboundCallParams);

    //Add additional call headers based on the sipCall values.
    addAdditionalCallHeaders(request, sipCall.getHeaders(), sipCall.getSipSessionId(), sipCall
      .getApplicationData());

    // Add Allow: header for INVITE and OPTIONS only
    if (method.equals(SIPRequest.INVITE) || method.equals(SIPRequest.OPTIONS)) {

      JainSipHelper.addAllowHeader(request, fromHeader, sitesToAdvertiseUpdates, advertiseUpdatesForAllCalls);

      // Advertise support of kmpl event subscriptions
      request.addHeader(allowEventsHeader);

      addSupportedHeader(sipCall, request);
    }

    if( method.equals(SIPRequest.INVITE)) {
      if ( JainSipHelper.isWhiteListedHost(fromHeader, sitesToAdvertiseCallInfo, "sitesToAdvertiseCallInfo")) {
        request.addHeader(callInfoHeader);
      }
    }

    // Add session refresh headers for INVITE and UPDATE only
    if (method.equals(SIPRequest.INVITE) || method.equals(SIPRequest.UPDATE)) {
      addSessionRefreshHeaders(sipCall, request);
    }

    //add content for this request
    addContent(request, content, contentType, subContentType);

    return (SIPRequest) request;
  }*/

  /*private List<RouteHeader> getRouteHeaders(SipServiceType callServiceType, List<RouteHeader> routeHeaders,
                                            Party sipParty, ToHeader toHeader) throws SipException, ParseException {
    final LinkedList<RouteHeader> returnHeaders = new LinkedList<>();

    SipURI proxyServerRouteFinder;
    if (routeHeaders != null && !routeHeaders.isEmpty()) {
      returnHeaders.addAll(routeHeaders);
      proxyServerRouteFinder = (SipURI) routeHeaders.get(0).getAddress().getURI();
      if (!proxyServerRouteFinder.hasLrParam()) {
        returnHeaders.add(JainSipHelper.getHeaderFactory().createRouteHeader(sipParty.getContactAddress()));
      }
    } else {
      proxyServerRouteFinder = getRequestUri(sipParty, toHeader, false, null);
    }

    // Per RFC 3261 (https://tools.ietf.org/html/rfc3261#page-239) when we get something like:
    //    sips:alice@atlanta.com;transport=TCP
    // We should NOT use transport TCP but instead honor the SIPS protocol so use TLS instead.
    // The exception is if transport is sctp, then don't use TLS.

    boolean isTransportParamNull = proxyServerRouteFinder.getTransportParam() == null;
    boolean hasTcpTransport = !isTransportParamNull &&
      proxyServerRouteFinder.getTransportParam().toUpperCase().contains(Transport.TCP.name());
    boolean hasSipsScheme = proxyServerRouteFinder.isSecure();

    Transport transport = null;
    if (isTransportParamNull || (hasSipsScheme && hasTcpTransport)) {
      transport = Transport.TLS;
    } else {
      transport = Transport.getTypeFromString(proxyServerRouteFinder.getTransportParam()).orElse(Transport.NONE);
    }
    List<RouteHeader> proxyRouteList = sipRouteHeaderProvider.getProxyServerRoute(callServiceType, transport);
    proxyRouteList.forEach(returnHeaders::addFirst);

    return returnHeaders;
  }*/

  /*public void sendRequest(SipCall sipCall, String method) throws Exception {
    sendRequest(sipCall, method, null, null, null, null);
  }*/

  /*public ClientTransaction sendRequest(SipCall sipCall, String method, String content, String contentType,
                                       String subContentType, OutboundCallParams outboundCallParams)
    throws SipException, ParseException, InvalidArgumentException {

    ClientTransaction clientTransaction;

    try {

      SIPRequest request = getRequest(sipCall, method, content, contentType, subContentType, outboundCallParams);

      // Create the client transaction and send message
      clientTransaction = sipTransportProvider.getNewClientTransaction(request);

      //For Bye request, if dialog is present in jainsip stack, then it does not allow
      // to send bye outside of dialog. So, get dialog from transaction and send bye request.
      if (Request.BYE.equals(method) || Request.NOTIFY.equals(method)) {
        Dialog dialog = clientTransaction.getDialog();
        if (dialog != null) {
          dialog.sendRequest(clientTransaction);
        } else {
          clientTransaction.sendRequest();
        }
      } else {
        clientTransaction.sendRequest();
      }
    } catch (Exception ex) {
      logger.error("Exception while sending a request for call id : " + sipCall.getCallId(), ex);
      throw ex;
    }

    return clientTransaction;
  }*/

  public void scheduleTask(SIPStackTimerTask timerTask, long delayMillis) {
    if (delayMillis < 0 || timerTask == null) {
      return;
    }

    ((SIPTransactionStack) sipStack).getTimer().schedule(timerTask, delayMillis);
  }

  /**
   * @param //party - Use contact address as the URI if possible
   * @param //toHeader - otherwise use this
   * @param //setLoopbackParameter - whether to add call loopback param
   * @param //xCiscoCallType - If not null, add this parameter value
   * @return
   * @throws ParseException
   */
  /*private SipURI getRequestUri(Party party, ToHeader toHeader, boolean setLoopbackParameter,
                               @Nullable String xCiscoCallType) throws ParseException {
    SipURI sipURI = null;
    if (party.getContactAddress() != null) {
      sipURI = (SipURI) party.getContactAddress().getURI();
    }
    if (sipURI == null ) {
      sipURI = (SipURI) toHeader.getAddress().getURI();
    }

    sipURI = (SipURI)sipURI.clone();

    if (setLoopbackParameter) {
      sipURI.setParameter(SipConstants.Loopback_Parameter, SipConstants.Loopback_Parameter_Value);
    }
    if (!Strings.isNullOrEmpty(xCiscoCallType)) {
      sipURI.setParameter(SipConstants.X_Cisco_Call_Type, xCiscoCallType);
    }
    return sipURI;
  }*/

  public ServerTransaction createServerTransaction(Request req) throws Exception {
    return sipTransportProvider.getNewServerTransaction(req);
  }

  /**
   * Ack request generated using the dialog might not contain all the route headers that were added
   * while sending a re-invite. Hence, Ack request requires a special handling for route headers.
   *
   * <p>1. Generate a ACK request from dialog. 2. If SipCall is not null, then remove all the route
   * headers from generated ACK and add the new route headers from SipCall.
   *
   * @param event
   * @param sipCall
   * @throws Exception
   */
  /*public void sendAck(ResponseEvent event, SipCall sipCall) throws Exception {
  try {
    CSeqHeader cseq = (CSeqHeader) event.getResponse().getHeader(CSeqHeader.NAME);
    Dialog dialog = null;

    //Get dialog from Client transaction.
    //If Client transaction is not available, get dialog directly from the event.
    if (event.getClientTransaction() != null ) {
      dialog = event.getClientTransaction().getDialog();
    } else {
      dialog = event.getDialog();
    }
    if (dialog != null) {
      Request ackRequest = dialog.createAck(cseq.getSeqNumber());

      if (sipCall != null) {
        //remove the route headers in the generated ACK.
        ackRequest.removeHeader(RouteHeader.NAME);
        */
  /**
   * Add route headers for ACK request. NOTE: Route headers in ACK has to be same as in INVITE.
   * Since the INVITE ( see #sendRequest(com.cisco.wx2.sip.sipstack.sip.interfaces.SipCall,
   * java.lang.String, java.lang.String, java.lang.String, java.lang.String,
   * com.cisco.wx2.sip.util.OutboundCallParams) } is not generated from dialog and custom route
   * headers are added in the INVITE. Hence, we have to add custom route headers for ACK too. i.e,
   * generate a ACK request from dialog ( which ensures all the properties are carried from the
   * ResponseEvent ) and re-build the Route header similar to how it was built for INVITE request.
   */
  /*
  //To header for request
  ToHeader toHeader = (ToHeader) getHeader(sipCall.getSipParty(), ToHeader.NAME);
  List<RouteHeader> routes = getRouteHeaders(sipCall.getSipServiceType(), sipCall.getRouteHeaders(), sipCall.getSipParty(), toHeader);
  routes.stream().forEach(route -> {
    try {
      ackRequest.addLast(route);
    } catch (SipException e) {
      logger.info("Error while adding route to ack request.", e);
    }
  });
  */
  /** Add additional application headers to the request. */
  /*
          addAdditionalCallHeaders(ackRequest, sipCall.getHeaders(), sipCall.getSipSessionId(), sipCall.getApplicationData());
        }

        //Send the request using the dialog.
        dialog.sendAck(ackRequest);
      } else {
        throw new Exception("Ack not sent. Dialog is null in transaction.");
      }
    } catch (Exception e) {
      if (sipCall != null && !Strings.isNullOrEmpty(sipCall.getCallId())) {
        DiagnosticsUtil.renderNote(sipCall.getCallId(), String.format("Failed sending ACK. %s", e));
      }
      throw e;
    }
  }*/

  @SuppressWarnings("checkstyle:parameterassignment")
  public SipURI createSipURI(String alias, String domain) throws Exception {
    // alias can be NULL
    if (alias != null && alias.contains("@")) {
      String[] parts = alias.split("@", 2);
      if (parts.length == 2) {
        alias = parts[0];
        domain = parts[1];
      }
    }

    SipURI sipUri;
    try {
      sipUri = JainSipHelper.getAddressFactory().createSipURI(alias, domain);
    } catch (ParseException e) {
      // logger.error(String.format("Exception creating sip Uri for alias: %s; domain: %s",
      // LogUtils.obfuscate(alias), LogUtils.obfuscate(domain)), e);
      throw new Exception(
          String.format(
              "Exception creating sip Uri for alias: %s; domain: %s",
              LogUtils.obfuscate(alias), LogUtils.obfuscate(domain)),
          e);
    }

    return sipUri;
  }

  public String getNewCallId() {
    return sipTransportProvider.getNewCallId().getCallId();
  }

  public URI getContactUri() {
    return contactURI;
  }

  protected void addContent(
      Message message, String content, String contentType, String subContentType)
      throws ParseException {
    ContentTypeHeader contentTypeHeader = null;
    if ((contentType != null) && (subContentType != null) && (content != null)) {
      // Create ContentTypeHeader
      contentTypeHeader =
          JainSipHelper.getHeaderFactory().createContentTypeHeader(contentType, subContentType);
    }

    if ((content != null) && (contentTypeHeader != null)) {
      message.setContent(content, contentTypeHeader);
    }
  }

  public void sendCancel(ClientTransaction clientTransaction) throws SipException {
    if (clientTransaction != null) {
      Request cancelRequest = clientTransaction.createCancel();
      ClientTransaction cancelTransaction =
          sipTransportProvider.getNewClientTransaction(cancelRequest);
      cancelTransaction.sendRequest();
    }
  }

  /*@Override
  public OutBoundCallBuilder getOutboundCallBuilder(String callId,
                                                    SessionId sipSessionId,
                                                    java.net.URI locusDialOutUrl,
                                                    String correlationId) {
    return new OutBoundCallBuilder(l2SipInstance, callId, sipSessionId, locusDialOutUrl, correlationId);
  }

  public ContactHeader getContactHeader(CallDirection callDirection, String callId, OutboundCallParams outboundCallParams) {
    boolean callIdOnContact = (outboundCallParams == null) ? false: outboundCallParams.isCallIdOnContact();
    boolean test = (outboundCallParams == null) ? false: outboundCallParams.isTest();

    ContactHeader header = CallDirection.INBOUND.equals(callDirection) ?
      inBoundCallContactHeader :
      !test ? outBoundCallContactHeader : outBoundTestCallContactHeader;

    if(callIdOnContact) {
      header = (ContactHeader) header.clone();
      try {
        header.setParameter(SipConstants.CallId_Parameter,
          URLEncoder.encode(callId, StandardCharsets.UTF_8.name()));
      } catch (ParseException | UnsupportedEncodingException e) {
        logger.info("Exception adding callid to contact header", e);
      }
    }
    return header;
  }*/

  /*private void addSupportedHeader(SipCall sipCall, Message message) {
    // Don't add "Supported: replaces" header for Huron calls.
    // Huron uses this header for HA, but l2sip doesn't support yet.
    // see https://sqbu-github.cisco.com/WebExSquared/sip-apps/issues/1608
    // TODO: (SE) for now we assume Huron doesn't need `timer` support either
    if (sipCall.getSipServiceType().isHuronSipServiceType()) {
      return;
    }

    if (sipCall.getSessionRefreshEnabled().equals(SipCall.SessionRefreshEnabled.ENABLED)) {
      message.addHeader(supportedHeaderWithTimer);
    } else {
      message.addHeader(supportedHeader);
    }

    addSrtpFallBackParamToSupportedHeader(sipCall, message);
  }*/

  /*private void addSrtpFallBackParamToSupportedHeader(SipCall sipCall, Message message) {
  if(sipCall.isCUCMStyleSrtpFallbackSupported()) {
    SupportedHeader header = (SupportedHeader) message.getHeader(SIPHeader.SUPPORTED);
    String optionsTag = header.getOptionTag();
    optionsTag = optionsTag + SipTokens.Comma + SipConstants.X_Cisco_Srtp_Fallback;
    try {
      message
        .setHeader(JainSipHelper.getHeaderFactory().createSupportedHeader(optionsTag));
    } catch (ParseException e) {
      */
  /*logger.error("ParseException while trying to add "
  +SipConstants.X_Cisco_Srtp_Fallback+" param to Supported header, "
  + "Message will not have this parameter");*/
  /*
      }
    }
  }*/

  // Passed to L2SipJainSipMessageHandler for processing
  // diagnostics service render methods.
  /*NodeInfo getNodeInfo() {
    return localNodeInfo;
  }*/

  /*
  only used for unit test
  */
  void setSipTransportProvider(SipProvider sipTransportProvider) {
    this.sipTransportProvider = sipTransportProvider;
  }
}
