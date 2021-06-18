package com.cisco.dsb.util;

import com.cisco.dsb.sip.stack.dto.BindingInfo;
import com.cisco.dsb.transport.Transport;
import gov.nist.javax.sip.header.SIPHeaderList;
import gov.nist.javax.sip.message.SIPRequest;
import gov.nist.javax.sip.message.SIPResponse;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.text.ParseException;
import java.util.Arrays;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.concurrent.atomic.AtomicReference;
import javax.sip.PeerUnavailableException;
import javax.sip.SipFactory;
import javax.sip.message.MessageFactory;

public class SIPRequestBuilder {

  private static SipFactory sipFactory;
  private static MessageFactory messageFactory;

  static {
    sipFactory = SipFactory.getInstance();
    SIPHeaderList.setPrettyEncode(true);
    try {
      messageFactory = sipFactory.createMessageFactory();
    } catch (PeerUnavailableException e) {
      // addressFactory will be null and we'll get NPE later in the code.
    }
  }

  private SDPType sdpType;
  private int contentLength = 0;
  private String[] optionalRequestURIValueList;
  private RequestMethod method = RequestMethod.INVITE;
  LinkedList<RequestHeader> headerPosition = new LinkedList();

  HashMap<RequestHeader, String> headers = new HashMap();
  private String requestUriHeader = "UserB@there.com";

  public SIPRequestBuilder() {
    headerPosition.add(RequestHeader.RequestUri);
    headerPosition.add(RequestHeader.Via);
    headerPosition.add(RequestHeader.Route);
    headerPosition.add(RequestHeader.MaxForwards);
    headerPosition.add(RequestHeader.To);
    headerPosition.add(RequestHeader.From);
    headerPosition.add(RequestHeader.CallId);
    headerPosition.add(RequestHeader.CSeq);
    headerPosition.add(RequestHeader.ContentType);
    headerPosition.add(RequestHeader.ContentLength);
    headerPosition.add(RequestHeader.Contact);
  }

  public enum LineSeparator {
    NEWLINE("\n"),
    CRLF("\r\n");

    public String getLineSeparator() {
      return lineSeparator;
    }

    private final String lineSeparator;

    LineSeparator(String separator) {
      this.lineSeparator = separator;
    }
  }

  public enum Position {
    Beginning,
    Middle,
    End
  }

  public enum RequestMethod {
    INVITE,
    ACK,
    OPTIONS,
    BYE,
    CANCEL,
    PRACK,
    NOTIFY
  }

  public enum RequestHeader {
    Via("Via", "v"),
    MaxForwards("Max-Forwards", "Max-Forwards"),
    RequestUri("", ""),
    CallId("Call-ID", "i"),
    From("From", "f"),
    To("To", "t"),
    ContentLength("Content-Length", "l"),
    Contact("Contact", "m"),
    ContentType("Content-Type", "c"),
    Route("Route", "Route"),
    CSeq("CSeq", "CSeq");

    private final String name;
    private final String shortForm;
    private boolean shortFormEnabled;

    RequestHeader(String name, String shortForm) {
      this.name = name;
      this.shortForm = shortForm;
      shortFormEnabled = false;
    }

    String getName() {
      return name;
    }

    public String getShortForm() {
      return shortForm;
    }

    public boolean isShortFormEnabled() {
      return shortFormEnabled;
    }
  }

  public enum SDPType {
    small,
    large;

    String getSdp(SIPRequestBuilder builder) throws Exception {
      switch (this) {
        case small:
          return builder.smallSdp;
        case large:
          return builder.largeSdp;
        default:
          throw new Exception("SDPType doesn't exist");
      }
    }
  }

  public String lineSeparator = LineSeparator.CRLF.getLineSeparator();

  private String toTag = null;

  private String to = " LittleGuy <sip:UserB@there.com>";

  private String callId = "1-4955@192.168.65.141";

  private String sdp;

  public void setToTag(String toTag) {
    this.toTag = toTag;
  }

  private String smallSdp =
      "      v=0"
          + lineSeparator
          + "      o=user1 53655765 2353687637 IN IP[local_ip_type] [local_ip]"
          + lineSeparator
          + "      s=-"
          + lineSeparator
          + "      c=IN IP[media_ip_type] [media_ip]"
          + lineSeparator
          + "      t=0 0"
          + lineSeparator
          + "      m=audio [media_port] RTP/AVP 0"
          + lineSeparator
          + "      a=rtpmap:0 PCMU/8000"
          + lineSeparator;

  private String largeSdp =
      "      "
          + lineSeparator
          + "      v=0 "
          + lineSeparator
          + "      o=tandberg 0 1 IN IP4 128.107.82.105 "
          + lineSeparator
          + "      s=- "
          + lineSeparator
          + "      c=IN IP4 128.107.82.105 "
          + lineSeparator
          + "      b=AS:384 "
          + lineSeparator
          + "      t=0 0 "
          + lineSeparator
          + "      m=audio 59114 RTP/SAVP 108 101 "
          + lineSeparator
          + "      a=rtpmap:108 MP4A-LATM/90000 "
          + lineSeparator
          + "      a=fmtp:108 profile-level-id=24;object=23;bitrate=64000 "
          + lineSeparator
          + "      a=rtpmap:101 telephone-event/8000 "
          + lineSeparator
          + "      a=fmtp:101 0-15 "
          + lineSeparator
          + "      a=crypto:1 AES_CM_128_HMAC_SHA1_80 inline:46gffnjfgdkjkfkfklenb3 "
          + lineSeparator
          + "      a=sendrecv "
          + lineSeparator
          + "      a=content:main "
          + lineSeparator
          + "      a=rtcp:59115 IN IP4 128.107.82.105 "
          + lineSeparator
          + "      m=video 59104 RTP/SAVP 126 "
          + lineSeparator
          + "      b=TIAS:320000 "
          + lineSeparator
          + "      a=rtpmap:126 H264/90000 "
          + lineSeparator
          + "      a=fmtp:126 profile-level-id=428016;max-mbps=490000;max-fs=8160;max-cpb=200;max-br=5000;max-smbps=490000;max-fps=6000;packetization-mode=1;max-rcmd-nalu-size=3133440;sar-supported=16 "
          + lineSeparator
          + "      a=rtcp-fb:* ccm fir "
          + lineSeparator
          + "      a=rtcp-fb:* ccm tmmbr "
          + lineSeparator
          + "      a=rtcp-fb:* nack pli "
          + lineSeparator
          + "      a=crypto:1 AES_CM_128_HMAC_SHA1_80 inline:749dhbb4t4993bdbcnj3i90 "
          + lineSeparator
          + "      a=sendrecv "
          + lineSeparator
          + "      a=content:main "
          + lineSeparator
          + "      a=label:11 "
          + lineSeparator
          + "      a=rtcp:59105 IN IP4 128.107.82.105 "
          + lineSeparator
          + "      m=video 59090 RTP/SAVP 126 "
          + lineSeparator
          + "      b=TIAS:320000 "
          + lineSeparator
          + "      a=rtpmap:126 H264/90000 "
          + lineSeparator
          + "      a=fmtp:126 profile-level-id=428014;max-mbps=245000;max-fs=8160;max-cpb=100;max-br=2500;max-smbps=245000;max-fps=6000;packetization-mode=1;max-rcmd-nalu-size=3133440;sar-supported=16 "
          + lineSeparator
          + "      a=rtcp-fb:* ccm fir "
          + lineSeparator
          + "      a=rtcp-fb:* ccm tmmbr "
          + lineSeparator
          + "      a=rtcp-fb:* nack pli "
          + lineSeparator
          + "      a=crypto:1 AES_CM_128_HMAC_SHA1_80 inline:gfgue68486589030047982"
          + lineSeparator
          + "      a=sendrecv "
          + lineSeparator
          + "      a=content:slides "
          + lineSeparator
          + "      a=label:12 "
          + lineSeparator
          + "      a=rtcp:59091 IN IP4 128.107.82.105 "
          + lineSeparator
          + "      m=application 59088 UDP/BFCP * "
          + lineSeparator
          + "      a=floorctrl:c-only "
          + lineSeparator
          + "      m=application 0 UDP/DTLS/UDT/IX * "
          + lineSeparator;

  public String getCallId() {
    return callId;
  }

  public SIPRequestBuilder withLineSeparator(LineSeparator lineSeparator) {
    this.lineSeparator = lineSeparator.getLineSeparator();
    return this;
  }

  public SIPRequestBuilder withSdpType(SDPType sdpType) {
    this.sdpType = sdpType;
    return this;
  }

  public SIPRequestBuilder withContentLength(int contentLength) {
    this.contentLength = contentLength;
    return this;
  }

  public SIPRequestBuilder withOptionalRequestURIValueList(String[] optionalRequestURIValueList) {
    this.optionalRequestURIValueList = optionalRequestURIValueList;
    return this;
  }

  public SIPRequestBuilder withMethod(RequestMethod method) {
    this.method = method;
    return this;
  }

  public SIPRequestBuilder withHeaderPosition(RequestHeader header, Position position)
      throws Exception {

    switch (position) {
      case Beginning:
        return withHeaderPosition(header, 1);
      case Middle:
        return withHeaderPosition(header, headerPosition.size() / 2);
      case End:
        return withHeaderPosition(header, headerPosition.size() - 1);

      default:
        throw new Exception("Position " + position + " is not supported");
    }
  }

  public SIPRequestBuilder withShortForm(RequestHeader requestHeader) {
    requestHeader.shortFormEnabled = true;
    return this;
  }

  public SIPRequestBuilder withHeaderPosition(RequestHeader header, int position) {
    headerPosition.remove(header);
    headerPosition.add(position, header);
    return this;
  }

  public SIPRequestBuilder withHeader(RequestHeader header, String value) {
    headers.put(header, value);
    return this;
  }

  /* Response code and their message */
  public static HashMap<Integer, String> ResponseCodeValue =
      new HashMap<Integer, String>() {
        {
          put(100, "Trying");
          put(180, "Ringing");
          put(200, "OK");
          put(202, "ACCEPTED");
          put(300, "MULTIPLE CHOICES");
          put(301, "MOVED PERMANENTLY");
          put(302, "MOVED TEMPORARILY");
          put(305, "USE PROXY");
          put(380, "ALTERNATIVE SERVICE");
          put(400, "BAD REQUEST");
          put(401, "UNAUTHORIZED");
          put(403, "FORBIDDEN");
          put(404, "NOT FOUND");
          put(500, "SERVER INTERNAL ERROR");
          put(502, "BAD GATEWAY");
          put(504, "GATEWAY TIMEOUT");
          put(600, "BUSY EVERYWHERE");
          put(603, "DECLINE");
        }
      };

  public static SIPRequest createRequest(String request) throws ParseException {
    SIPRequest msg;
    msg = (SIPRequest) messageFactory.createRequest(request);
    return msg;
  }

  public static SIPResponse createResponse(byte[] response) throws ParseException {
    return (SIPResponse) messageFactory.createResponse(Arrays.toString(response));
  }

  public String getRequestAsString(RequestMethod method, String... optionalRequestURIValueList)
      throws Exception {
    return withMethod(method).withOptionalRequestURIValueList(optionalRequestURIValueList).build();
  }

  public String getRequestAsString(RequestMethod method, boolean random) throws Exception {

    if (toTag != null) {
      to += ";" + toTag;
    }
    if (!random) {
      return getRequestAsString(method);
    }
    String randomString = randomAlphaNumeric(20);
    callId = randomString;
    String requestUri = " sip:" + randomString + "@cisco.com SIP/2.0";
    String toUri = "To: <sip:" + randomString + "@cisco.com>";
    String requestString =
        method.name()
            + requestUri
            + lineSeparator
            + "Via: SIP/2.0/UDP ss1.wcom.com:5060;branch=2d4790.1"
            + lineSeparator
            + "Via: SIP/2.0/UDP here.com:5060"
            + lineSeparator
            + "Max-Forwards: 70"
            + lineSeparator
            + "Route: <sip:UserE@xxx.yyy.com;maddr=ss1.wcom.com>"
            + lineSeparator
            + "Route: <sip:TinkyWinky@tellytubbyland.com;maddr=ss1.wcom.com>"
            + lineSeparator
            + toUri
            + lineSeparator
            + "From: BigGuy <sip:UserA@here.com>"
            + lineSeparator
            + "Call-ID: "
            + callId
            + lineSeparator
            + "CSeq: 1 "
            + method.name()
            + lineSeparator
            + "Content-Length: "
            + contentLength
            + lineSeparator
            + "Contact: <sip:UserA@100.101.102.103>"
            + lineSeparator
            + "Content-Type: application/sdp"
            + lineSeparator;

    if (method == RequestMethod.PRACK) {
      requestString = requestString + "RAck: 1 1 INVITE" + lineSeparator;
    }

    return requestString;
  }

  public String build() throws Exception {
    if (optionalRequestURIValueList != null && optionalRequestURIValueList.length != 0) {
      requestUriHeader = optionalRequestURIValueList[0];
    }

    if (toTag != null) {
      to += ";" + toTag;
    }

    if (sdpType != null) {
      sdp = sdpType.getSdp(this);
      if (contentLength == 0) {
        this.contentLength = sdp.length();
      }
    }

    populateHeaders();

    return buildHeaders();
  }

  private String buildHeaders() {

    AtomicReference<String> constructedHeader = new AtomicReference<>("");
    headerPosition.forEach(
        requestHeader ->
            constructedHeader.set(
                constructedHeader.get() + headers.get(requestHeader) + lineSeparator));

    String constructedHeaderString = constructedHeader.get();
    if (method == RequestMethod.PRACK) {
      constructedHeaderString = constructedHeaderString + "RAck: 1 1 INVITE" + lineSeparator;
    }

    if (sdp != null) {
      constructedHeaderString = constructedHeaderString + lineSeparator + sdp;
    } else {
      constructedHeaderString = constructedHeaderString + lineSeparator;
    }
    return constructedHeaderString;
  }

  private void populateHeaders() {

    headers.put(RequestHeader.RequestUri, method + " sip:" + requestUriHeader + " SIP/2.0");
    headers.put(
        RequestHeader.Via,
        constructHeader(
            RequestHeader.Via,
            " SIP/2.0/UDP 127.0.0.1:5070;branch=2d4790.1;rport",
            " SIP/2.0/UDP here.com:5060"));
    headers.put(RequestHeader.MaxForwards, constructHeader(RequestHeader.MaxForwards, " 70"));
    headers.put(
        RequestHeader.Route,
        constructHeader(
            RequestHeader.Route,
            " <sip:UserE@xxx.yyy.com;maddr=ss1.wcom.com>",
            " <sip:TinkyWinky@tellytubbyland.com;maddr=ss1.wcom.com>"));
    headers.put(RequestHeader.To, constructHeader(RequestHeader.To, to));
    headers.put(
        RequestHeader.From, constructHeader(RequestHeader.From, " BigGuy <sip:UserA@here.com>"));
    headers.put(RequestHeader.CallId, constructHeader(RequestHeader.CallId, callId));
    headers.put(RequestHeader.CSeq, constructHeader(RequestHeader.CSeq, " 1 " + method.name()));
    headers.put(
        RequestHeader.ContentLength,
        constructHeader(RequestHeader.ContentLength, String.valueOf(contentLength)));
    headers.put(
        RequestHeader.Contact,
        constructHeader(RequestHeader.Contact, " <sip:UserA@100.101.102.103>"));
    headers.put(
        RequestHeader.ContentType, constructHeader(RequestHeader.ContentType, " application/sdp"));
  }

  private String constructHeader(RequestHeader requestHeader, String... values) {

    if (headers.get(requestHeader) == null) {
      StringBuilder header = new StringBuilder();
      for (int i = 0; i < values.length; i++) {

        String headerName =
            requestHeader.shortFormEnabled ? requestHeader.getShortForm() : requestHeader.getName();
        header = header.append(headerName + ":" + values[i]);

        if (i != (values.length - 1)) {
          header.append(lineSeparator);
        }
      }
      return header.toString();
    } else {
      return headers.get(requestHeader);
    }
  }

  public String getRequestAsString(RequestMethod method, int maxForwardValue) {

    if (toTag != null) {
      to += ";" + toTag;
    }
    String randomString = randomAlphaNumeric(20);
    String callId = randomString;
    String requestUri = " sip:" + randomString + "@cisco.com SIP/2.0";
    String toUri = "To: <sip:" + randomString + "@cisco.com>";

    return method.name()
        + requestUri
        + lineSeparator
        + "Via: SIP/2.0/UDP ss1.wcom.com:5060;branch=2d4790.1"
        + lineSeparator
        + "Via: SIP/2.0/UDP here.com:5060"
        + lineSeparator
        + "Max-Forwards:"
        + maxForwardValue
        + lineSeparator
        + "Route: <sip:UserE@xxx.yyy.com;maddr=ss1.wcom.com>"
        + lineSeparator
        + "Route: <sip:TinkyWinky@tellytubbyland.com;maddr=ss1.wcom.com>"
        + lineSeparator
        + toUri
        + lineSeparator
        + "From: BigGuy <sip:UserA@here.com>"
        + lineSeparator
        + "Call-ID: "
        + callId
        + lineSeparator
        + "CSeq: 1 "
        + method.name()
        + lineSeparator
        + "Content-Length: "
        + contentLength
        + lineSeparator
        + "Contact: <sip:UserA@100.101.102.103>"
        + lineSeparator
        + "Content-Type: application/sdp"
        + lineSeparator
        + sdp;
  }

  public void setBindingInfo(SIPRequest request, Transport transport) throws UnknownHostException {
    BindingInfo bindingInfo;
    InetAddress localAddr;
    InetAddress remoteAddr;
    byte[] localIpAddr = new byte[] {127, 0, 0, 1};
    byte[] remoteIpAddr = new byte[] {127, 0, 0, 1};
    localAddr = InetAddress.getByAddress(localIpAddr);
    remoteAddr = InetAddress.getByAddress(remoteIpAddr);
    bindingInfo = new BindingInfo();
    bindingInfo.setRemoteAddress(remoteAddr);
    bindingInfo.setRemotePort(5060);
    bindingInfo.setLocalAddress(localAddr);
    bindingInfo.setLocalPort(5060);
    bindingInfo.setTransport(transport);
  }

  public static String randomAlphaNumeric(int count) {
    StringBuilder builder = new StringBuilder();
    final String ALPHA_NUMERIC_STRING =
        "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmopqrstuvwxyz0123456789";
    while (count-- != 0) {
      int character = (int) (Math.random() * ALPHA_NUMERIC_STRING.length());
      builder.append(character);
    }
    return builder.toString();
  }

  public SIPRequest getHybridCascadeRequest() throws Exception {
    String sipMessage =
        "INVITE sip:73sVgblHnSQtmLz08VXs8dag@192.168.65.141:5060;call-type=hybrid-cascade;x-cisco-svc-type=spark-mm SIP/2.0\n"
            + "Via: SIP/2.0/TCP 192.168.65.141:7002;branch=z9hG4bK-4955-1-0\n"
            + "Max-Forwards: 69\n"
            + "To: sut <sip:73sVgblHnSQtmLz08VXs8dag@192.168.65.141:5060>\n"
            + "From: 123 <sip:123@192.168.65.141:7002>;tag=4955SIPpTag001\n"
            + "Contact: sip:123@192.168.65.141:7002;ifocus\n"
            + "Call-ID: "
            + callId
            + "\n"
            + "CSeq: 1 INVITE\n"
            + "Content-Length: 0\n"
            + "Subject: Performance Test\n"
            + "Allow: UPDATE\n"
            + "Supported: timer,resource-priority,replaces\n"
            + "Content-Type: application/sdp\n"
            + "Session-ID: 6d4da3adee523660ab1654a7ba94cf83;remote=00000000000000000000000000000000\n";
    return (SIPRequest) messageFactory.createRequest(sipMessage);
  }

  public SIPRequest getReInviteRequest(String optionalRequestURIValue) throws Exception {
    if (optionalRequestURIValue == null || optionalRequestURIValue.equals("")) {
      optionalRequestURIValue =
          "73sVgblHnSQtmLz08VXs8dag@192.168.65.141:5060;call-type=hybrid-cascade;x-cisco-svc-type=spark-mm";
    }

    String sipMessage =
        "INVITE sip:"
            + optionalRequestURIValue
            + " SIP/2.0\n"
            + "Via: SIP/2.0/TCP 192.168.65.141:7002;branch=z9hG4bK-4955-1-0\n"
            + "Max-Forwards: 69\n"
            + "To: sut <sip:73sVgblHnSQtmLz08VXs8dag@192.168.65.141:5060>;tag=8079SIPpTag011\n"
            + "From: 123 <sip:123@192.168.65.141:7002>;tag=4955SIPpTag001\n"
            + "Contact: sip:123@192.168.65.141:7002;ifocus\n"
            + "Call-ID: "
            + callId
            + "\n"
            + "CSeq: 1 INVITE\n"
            + "Content-Length: 0\n"
            + "Subject: Performance Test\n"
            + "Allow: UPDATE\n"
            + "Supported: timer,resource-priority,replaces\n"
            + "Content-Type: application/sdp\n"
            + "Session-ID: 6d4da3adee523660ab1654a7ba94cf83;remote=00000000000000000000000000000000\n";
    return (SIPRequest) messageFactory.createRequest(sipMessage);
  }

  public SIPResponse get200Response() throws ParseException {
    String sipMessage =
        "SIP/2.0 200 OK\n"
            + "Via: SIP/2.0/TCP 192.168.65.141:5066;branch=z9hG4bKUsaQangfWbsEVmsoPdTNBA~~0\n"
            + "Via: SIP/2.0/TCP 192.168.65.141:7002;branch=z9hG4bK-8090-1-0\n"
            + "Record-Route: <sip:192.168.65.141:5080;transport=tcp;lr;call-type=sip>\n"
            + "Record-Route: <sip:rr,n=net_sp_@192.168.65.141:5066;transport=tcp;lr;x-cisco-call-type=hybrid-cascade>\n"
            + "To: sut <sip:73sVgblHnSQtmLz08VXs8dag@192.168.65.141:5060>;tag=8079SIPpTag011\n"
            + "From: 123 <sip:123@192.168.65.141:7002>;tag=8090SIPpTag001\n"
            + "Contact: <sip:service@192.168.65.141:5080;transport=TCP>;sip.cisco.multistream;\n"
            + "Call-ID: "
            + callId
            + "\n"
            + "CSeq: 1 INVITE\n"
            + "Content-Length: 0\n"
            + "Allow: UPDATE\n"
            + "Content-Type: application/sdp\n"
            + "Session-ID: bc6d01a4a51c3886a6bf32cd9155dd56;remote=50aa12ba7817323b8342191bcd537513\n";
    return (SIPResponse) messageFactory.createResponse(sipMessage);
  }

  /* This method returns a response for the responseCode passed to it */
  public SIPResponse getResponse(int code) throws ParseException {
    String result = "200 OK";
    if (ResponseCodeValue.containsKey(code)) {
      result = code + " " + ResponseCodeValue.get(code);
    }

    String sipMessage =
        "SIP/2.0 "
            + result
            + "\n"
            + "Via: SIP/2.0/TCP 192.168.65.141:5066;branch=z9hG4bKUsaQangfWbsEVmsoPdTNBA~~0\n"
            + "Via: SIP/2.0/TCP 192.168.65.141:7002;branch=z9hG4bK-8090-1-0\n"
            + "Record-Route: <sip:192.168.65.141:5080;transport=tcp;lr;call-type=sip>\n"
            + "Record-Route: <sip:rr,n=net_sp_@192.168.65.141:5066;transport=tcp;lr;x-cisco-call-type=hybrid-cascade>\n"
            + "To: sut <sip:73sVgblHnSQtmLz08VXs8dag@192.168.65.141:5060>;tag=8079SIPpTag011\n"
            + "From: 123 <sip:123@192.168.65.141:7002>;tag=8090SIPpTag001\n"
            + "Contact: <sip:service@192.168.65.141:5080;transport=TCP>;sip.cisco.multistream;\n"
            + "Call-ID: "
            + callId
            + "\n"
            + "CSeq: 1 INVITE\n"
            + "Content-Length: 0\n"
            + "Allow: UPDATE\n"
            + "Content-Type: application/sdp\n"
            + "Session-ID: bc6d01a4a51c3886a6bf32cd9155dd56;remote=50aa12ba7817323b8342191bcd537513\n";
    return (SIPResponse) messageFactory.createResponse(sipMessage);
  }
}
