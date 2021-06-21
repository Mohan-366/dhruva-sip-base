package com.cisco.dsb.sip.jain;

import com.cisco.dsb.sip.util.SipConstants;
import com.cisco.dsb.sip.util.SipPatterns;
import com.cisco.dsb.sip.util.SipTokens;
import com.google.common.base.Preconditions;
import com.google.common.base.Strings;
import gov.nist.core.NameValueList;
import gov.nist.javax.sip.address.SipUri;
import gov.nist.javax.sip.header.Contact;
import gov.nist.javax.sip.header.RecordRoute;
import gov.nist.javax.sip.message.SIPMessage;
import gov.nist.javax.sip.message.SIPRequest;
import gov.nist.javax.sip.message.SIPResponse;
import java.text.ParseException;
import java.util.Collections;
import java.util.LinkedList;
import java.util.List;
import java.util.ListIterator;
import javax.sip.*;
import javax.sip.address.Address;
import javax.sip.address.AddressFactory;
import javax.sip.address.SipURI;
import javax.sip.address.URI;
import javax.sip.header.*;
import javax.sip.message.Message;
import javax.sip.message.MessageFactory;
import javax.sip.message.Request;

/**
 * TODO: behaviours are ported from L2sip (JainSipUtils & SipHelper). Should have tests to cover
 * all.
 *
 * <p>Helper class containing methods that fetch, create, validate, add and remove data
 * corresponding to jain sip msg
 */
public class JainSipHelper {

  private static SipFactory sipFactory;
  private static AddressFactory addressFactory;
  private static HeaderFactory headerFactory;
  private static MessageFactory messageFactory;

  static {
    sipFactory = SipFactory.getInstance();
    sipFactory.setPathName("gov.nist");
    // apps should set this based on the stack impl they want -> sipFactory.setPathName("gov.nist");
    // RemotePartyIDParser.init();
    try {
      addressFactory = sipFactory.createAddressFactory();
      headerFactory = sipFactory.createHeaderFactory();
      messageFactory = sipFactory.createMessageFactory();
    } catch (PeerUnavailableException e) {
      // logger.error("Error creating any of the above factories", e);
    }
  }

  // --------- Methods that FETCH DATA ----------//

  public static HeaderFactory getHeaderFactory() {
    return headerFactory;
  }

  public static AddressFactory getAddressFactory() {
    return addressFactory;
  }

  public static MessageFactory getMessageFactory() {
    return messageFactory;
  }

  public static SipFactory getSipFactory() {
    return sipFactory;
  }

  public static String getCallId(Message message) {
    return ((CallIdHeader) message.getHeader(CallIdHeader.NAME)).getCallId();
  }

  public static String getCSeqString(Message message) {
    return ((CSeqHeader) message.getHeader(CSeqHeader.NAME)).getMethod();
  }

  public static long getCSeq(Message message) {
    return ((CSeqHeader) message.getHeader(CSeqHeader.NAME)).getSeqNumber();
  }

  public static String getToTag(Message message) {
    return ((ToHeader) message.getHeader(ToHeader.NAME)).getTag();
  }

  public static String getMethod(Message message) {
    CSeqHeader header = (CSeqHeader) message.getHeader(CSeqHeader.NAME);
    return header != null ? header.getMethod() : null;
  }

  public static int getContentLength(Message msg) {
    return ((ContentLengthHeader) msg.getHeader(ContentLengthHeader.NAME)).getContentLength();
  }

  public static boolean contentTypeMatches(Message msg, String contentType, String subType) {
    ContentTypeHeader header = (ContentTypeHeader) msg.getHeader(ContentTypeHeader.NAME);
    return header.getContentType().equalsIgnoreCase(contentType)
        && header.getContentSubType().equalsIgnoreCase(subType);
  }

  public static HeaderAddress getFromHeader(Request req) {
    return (FromHeader) req.getHeader(FromHeader.NAME);
  }

  public static HeaderAddress getToHeader(Request req) {
    return (ToHeader) req.getHeader(ToHeader.NAME);
  }

  public static String getDomain(URI uri) {
    if (uri instanceof SipUri) {
      return ((SipUri) uri).getHost();
    }
    return null;
  }

  public static String getToDomain(Message msg) {
    return getDomain(((ToHeader) msg.getHeader(ToHeader.NAME)).getAddress().getURI());
  }

  /**
   * Returns the user@host portion of the SIP URI, if user is not null. If user is null, only host
   * is returned.
   */
  public static String getUserAtHost(SipURI sipUri) {
    return ((SipUri) sipUri).getUserAtHost();
  }

  public static String getMethodParamFromRequest(Request msg, String paramName) {
    if (msg.getRequestURI() instanceof SipUri) {
      SipUri sipUri = (SipUri) msg.getRequestURI();
      return sipUri.getParameter(paramName);
    }
    return null;
  }

  public static String getParameters(Header header) {
    if (header != null) {
      String headerStr = header.toString();
      if (headerStr.contains(">;")) {
        return headerStr.substring(headerStr.indexOf(">;") + 2);
      }
    }
    return null;
  }

  // ---------------------------------------------//

  // --------- Methods that CREATE DATA ----------//
  /**
   * Converts a SIP URI or user@host (no sip: scheme) string into a SipURI. This method can be used
   * for: 1. CI SipAddress (user@host format - no sip: scheme stored typically) 2.
   * com.cisco.wx2.util.DialableKey (valid SIP URI stored as String) 3. LocusSupplementaryUserInfo
   * sipUrl (valid SIP URI stored as a String) 4. LocusParticipantInfo sipUrl (valid SIP URI stored
   * as a String)
   *
   * @param sipUriOrUserAtHost - a SIP address, with or without a sip: or sips: scheme
   * @return a SIP URI
   */
  public static SipURI createSipURI(String sipUriOrUserAtHost) throws ParseException {
    Preconditions.checkArgument(!Strings.isNullOrEmpty(sipUriOrUserAtHost));

    String targetSipAddress = addSipSchemeIfNeeded(sipUriOrUserAtHost);

    URI uri = addressFactory.createURI(targetSipAddress);
    if (!(uri instanceof SipURI)) {
      throw new ParseException(
          "Unable to convert "
              +
              // LogUtils.obfuscate(sipUriOrUserAtHost) +
              " to SIP URI",
          0);
    }
    return (SipURI) uri;
  }

  /**
   * Converts a SIP URI or user@host (no sip: scheme) string into a sanitized/normalized
   * (sip|sips):user@host format SipURI. This method can be used for: 1. CI SipAddress (user@host
   * format - no sip: scheme stored typically) 2. DialableKey (valid SIP URI stored as String) 3.
   * LocusSupplementaryUserInfo sipUrl (valid SIP URI stored as a String) 4. LocusParticipantInfo
   * sipUrl (valid SIP URI stored as a String)
   *
   * @param sipAddress - a SIP address, with or without a sip: or sips: scheme
   * @return a sanitized/normalized (sip|sips):user@host format SIP URI
   * @throws ParseException on invalid URI
   */
  public static SipURI createSanitizedSipUri(String sipAddress) throws ParseException {
    SipURI sipURI = createSipURI(sipAddress);
    return sanitizeSipURI(sipURI, true);
  }

  /**
   * Returns the SIP URI as {@code scheme:[user@]host}. Removes URI parameters, password, and port.
   * Scheme optionally forced from sips: to sip: scheme.
   */
  public static SipURI sanitizeSipURI(SipURI uri, boolean forceSipScheme) {
    if (uri == null) {
      return null;
    }

    // Sanitize the URI by only using the username and host portions
    SipURI sanitizedUri;
    try {
      sanitizedUri = JainSipHelper.getAddressFactory().createSipURI(uri.getUser(), uri.getHost());
    } catch (ParseException e) {
      // This would be really unexpected since the URI was already parsed
      // logger.warn("Unable to create SIP URI from user/host", e);
      return null;
    }
    if (!forceSipScheme) {
      // Use sips: scheme if input URI had it
      sanitizedUri.setSecure(uri.isSecure());
    }
    return sanitizedUri;
  }

  /**
   * Util to convert one header to other. In cases, where TO has to be converted to From, this util
   * can be used.
   *
   * @param header
   * @param headerName
   * @return
   * @throws ParseException
   */
  public Header createHeader(Header header, String headerName) throws ParseException {
    String headerLine = header.toString().substring(header.toString().indexOf(' ') + 1);
    return headerFactory.createHeader(headerName, headerLine);
  }

  public ContactHeader createContactHeader(Address address) {
    return headerFactory.createContactHeader(address);
  }

  public static RouteHeader createRouteHeader(String host, int port, String transport)
      throws ParseException {
    SipURI hopUri = addressFactory.createSipURI("l2sip", host);
    hopUri.setPort(port);
    hopUri.setTransportParam(transport);
    hopUri.setLrParam();
    return headerFactory.createRouteHeader(addressFactory.createAddress(hopUri));
  }

  /**
   * Create route headers list from record route headers list.
   *
   * @param recordRouteHeaders - list of record route header's from request / response. @Param
   *     isResponse - if record route header is from response, then reverse it to form route set.
   * @return
   * @throws ParseException
   */
  public static List<RouteHeader> createRouteListFromRecordRouteList(
      ListIterator recordRouteHeaders, boolean isResponse) throws ParseException {
    List<RouteHeader> routeList = new LinkedList<>();
    if (recordRouteHeaders != null) {
      while (recordRouteHeaders.hasNext()) {
        RecordRoute recordRoute = (RecordRoute) recordRouteHeaders.next();
        RouteHeader routeHeader =
            (RouteHeader) headerFactory.createHeader(RouteHeader.NAME, recordRoute.getValue());
        routeList.add(routeHeader);
      }
    }

    // if route list is generated from a response then reverse the route list.
    if (isResponse) {
      Collections.reverse(routeList);
    }
    return routeList;
  }

  /**
   * Creates a Remote Party ID header per draft RFC
   * https://tools.ietf.org/html/draft-ietf-sip-privacy-00
   *
   * @param displayName
   * @param rpidUri
   * @param orgId
   * @param isCallingParty
   * @return
   * @throws ParseException
   */
  /*public static Header createRpidHeader(
      String displayName, SipURI rpidUri, OrgId orgId, boolean isCallingParty)
      throws ParseException {
    StringBuffer rpidHeaderBuffer = new StringBuffer(256);

    // spark call wants a display name as part of the RPID header
    if (Strings.isNullOrEmpty(displayName)) {
      rpidHeaderBuffer.append(rpidUri);
    } else {
      rpidHeaderBuffer.append(
          JainSipUtils.getAddressFactory().createAddress(displayName, rpidUri).toString());
    }

    rpidHeaderBuffer
        .append(";")
        .append(RemotePartyIDHeader.PARTY_PARAM_NAME)
        .append("=")
        .append(
            isCallingParty
                ? RemotePartyIDHeader.PARTY_PARAM_CALLING
                : RemotePartyIDHeader.PARTY_PARAM_CALLED);
    rpidHeaderBuffer
        .append(";")
        .append(RemotePartyIDHeader.SCREEN_PARAM_NAME)
        .append("=")
        .append(RemotePartyIDHeader.SCREEN_PARAM_YES);
    rpidHeaderBuffer
        .append(";")
        .append(RemotePartyIDHeader.PRIVACY_PARAM_NAME)
        .append("=")
        .append(RemotePartyIDHeader.PRIVACY_PARAM_OFF);
    if (orgId != null && !orgId.toString().isEmpty()) {
      rpidHeaderBuffer
          .append(";")
          .append(SipConstants.X_Cisco_Tenant)
          .append("=")
          .append(orgId.toString());
    }
    return JainSipUtils.getHeaderFactory()
        .createHeader(SipConstants.Rpid_Header_Name, rpidHeaderBuffer.toString());
  }*/

  // ---------------------------------------------//

  // --------- Methods that VALIDATE DATA ----------//
  /**
   * Check if a uri is a sip uri with user type of phone.
   *
   * @param uri
   * @return true if uri is SipUri with user=phone parameter
   */
  public static boolean isPhoneType(URI uri) {
    if (uri instanceof SipUri) {
      return ((SipUri) uri).getUserParam() != null && ((SipUri) uri).getUserParam().equals("phone");
    }
    return false;
  }

  public static boolean isSuccessCode(int statusCode) {
    return statusCode >= 200 && statusCode < 300;
  }

  /**
   * Checks whether trimmed string has a leading sip: or sips: scheme.
   *
   * @param str
   * @return
   */
  public static boolean hasSipScheme(String str) {
    if (str == null) {
      return false;
    }
    return SipPatterns.sipSchemePattern.matcher(str).find();
  }

  public static boolean hasCallIdParameter(Header header) {
    String parameters = getParameters(header);
    return parameters != null && parameters.toLowerCase().contains(SipConstants.CallId_Parameter);
  }

  // ---------------------------------------------//

  // --------- Methods that ADD DATA ----------//
  /**
   * Trims a string and adds a leading sip: scheme if the string is: 1. non-empty 2. has neither a
   * leading sip: or sips: scheme already
   *
   * @param str
   * @return
   */
  public static String addSipSchemeIfNeeded(String str) {
    if (str == null) {
      return null;
    }
    // Trims a string of whitespace first to normalize
    // Also avoids putting whitespace in the user info if sip: is added
    String trimmed = str.trim();
    if (hasSipScheme(trimmed) || trimmed.isEmpty()) {
      return trimmed;
    }
    return SipTokens.SipColon + trimmed;
  }

  // ---------------------------------------------//

  // --------- Methods that REMOVE DATA ----------//
  /**
   * Removes leading sip: or sips: scheme if it exists.
   *
   * @param str
   * @return
   */
  public static String removeSipScheme(String str) {
    if (str == null) {
      return null;
    }
    return SipPatterns.sipSchemePattern.matcher(str.trim()).replaceFirst("");
  }

  public static void removeParameterFromContactHeader(Header contactHeader, String paramName) {
    if (contactHeader == null) {
      return;
    }
    Preconditions.checkArgument(contactHeader instanceof Contact);
    Contact contact = (Contact) contactHeader;
    NameValueList nvPairs = contact.getContactParms();
    if (nvPairs != null) {
      nvPairs.remove(paramName);
    }
  }

  // ---------------------------------------------//

  // --------- Methods that PRINT DATA ----------//

  public static String toTraceString(Dialog dialog) {
    if (dialog == null) {
      return "null";
    }
    DialogState state = dialog.getState();
    CallIdHeader callIdHeader = dialog.getCallId();
    return String.format(
        "DialogId=%s state=[%s] callId=%s",
        dialog.getDialogId(),
        state == null ? "null" : state.toString(),
        callIdHeader == null ? "null" : callIdHeader.getCallId());
  }

  public static String toTraceString(ServerTransaction t) {
    if (t == null) {
      return "null";
    }
    return String.format(
        "state=[%s] dialog=[%s]",
        t.getState() == null ? "null" : t.getState().toString(),
        t.getDialog() == null ? "null" : toTraceString(t.getDialog()));
  }

  public static String toTraceString(Request r) {
    if (r == null) {
      return "null";
    }
    return String.format("method=%s", r.getMethod() == null ? "null" : r.getMethod());
  }

  public static String toTraceString(SIPMessage sipMessage) {
    String callId = sipMessage.getCallIdHeader().getCallId();
    String cseq =
        sipMessage.getCSeqHeader().getSeqNumber() + " " + sipMessage.getCSeqHeader().getMethod();
    if (sipMessage instanceof SIPRequest) {
      SIPRequest req = (SIPRequest) sipMessage;
      return String.format("Request [%s] call-Id=[%s] cseq=[%s]", req.getMethod(), callId, cseq);
    } else if (sipMessage instanceof SIPResponse) {
      SIPResponse res = (SIPResponse) sipMessage;
      String status =
          String.valueOf(res.getStatusLine().getStatusCode())
              + " "
              + res.getStatusLine().getReasonPhrase();
      return String.format("Response [%s] call-Id=[%s] cseq=[%s]", status, callId, cseq);
    } else {
      return String.format(
          "%s call-Id=[%s] cseq=[%s]", sipMessage.getClass().getSimpleName(), callId, cseq);
    }
  }
}
