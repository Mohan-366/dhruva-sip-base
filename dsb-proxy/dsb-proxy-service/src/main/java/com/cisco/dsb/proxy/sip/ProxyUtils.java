package com.cisco.dsb.proxy.sip;

import com.cisco.dsb.common.exception.DhruvaException;
import com.cisco.dsb.common.sip.stack.util.SipTag;
import com.cisco.dsb.common.sip.util.SipUtils;
import com.google.common.base.Preconditions;
import gov.nist.javax.sip.message.SIPMessage;
import gov.nist.javax.sip.message.SIPRequest;
import gov.nist.javax.sip.message.SIPResponse;
import gov.nist.javax.sip.stack.MessageChannel;
import java.io.IOException;
import java.security.cert.X509Certificate;
import java.text.ParseException;
import javax.net.ssl.SSLException;
import javax.net.ssl.SSLPeerUnverifiedException;
import javax.net.ssl.SSLSession;
import javax.sip.InvalidArgumentException;
import javax.sip.ServerTransaction;
import javax.sip.SipException;
import javax.sip.address.Address;
import javax.sip.address.SipURI;
import javax.sip.address.URI;
import javax.sip.header.CSeqHeader;
import javax.sip.header.ContactHeader;
import javax.sip.header.ToHeader;
import lombok.CustomLog;

@CustomLog
public class ProxyUtils {

  private static final String nullString = "null";

  public static void updateContactHeader(
      final SIPMessage sipMessage,
      final ContactHeader originalContactHeader,
      final String contactAddress,
      final String transport,
      final int contactPort)
      throws ParseException {

    if (originalContactHeader != null) {
      Preconditions.checkNotNull(sipMessage);

      ContactHeader contactHeader = (ContactHeader) originalContactHeader.clone();
      Address address = contactHeader.getAddress();
      if (address.getURI().isSipURI()) {
        SipURI sipURI = (SipURI) address.getURI();
        sipURI.setHost(contactAddress);
        sipURI.setPort(contactPort);
        sipURI.setTransportParam(transport);
      }
      sipMessage.setHeader(contactHeader);
    }
  }

  public static String getCseqNumber(SIPMessage sipMessage) {
    CSeqHeader cSeqHeader = sipMessage.getCSeqHeader();
    return cSeqHeader != null ? Long.toString(cSeqHeader.getSeqNumber()) : nullString;
  }

  public static String getCseqMethod(SIPMessage sipMessage) {
    CSeqHeader cSeqHeader = sipMessage.getCSeqHeader();
    return cSeqHeader != null ? cSeqHeader.getMethod() : nullString;
  }

  public static X509Certificate getPeerCertificate(SIPRequest request) throws SSLException {
    if (request == null) {
      return null;
    }

    MessageChannel messageChannel = (MessageChannel) request.getMessageChannel();

    return getPeerCertificate(messageChannel);
  }

  public static X509Certificate getPeerCertificate(MessageChannel messageChannel)
      throws SSLPeerUnverifiedException {
    if (messageChannel == null || !messageChannel.isSecure()) {
      return null;
    }
    logger.debug(
        "Get certificate from SSL session messageChannel=[local=[{}] peer=[{}] hashCode={}]",
        messageChannel.getHostPort(),
        messageChannel.getPeerHostPort(),
        messageChannel.hashCode());
    SSLSession sslSession = SipUtils.getSslSession(messageChannel);

    return (X509Certificate) sslSession.getPeerCertificates()[0];
  }

  public static int getResponseClass(SIPResponse response) {
    return response.getStatusCode() / 100;
  }

  /**
   * Sends a final response without creating ProxyTransaction This is used to send error responses
   * to requests that fail sanity checks performed in DsProxyUtils.validateRequest().
   *
   * @param server request in question
   * @param response to send
   * @return DsSipServerTransaction transaction to return to Low Level
   */
  protected static ServerTransaction sendErrorResponse(
      ServerTransaction server, SIPResponse response)
      throws DhruvaException, IOException, ParseException, SipException, InvalidArgumentException {
    ToHeader to = response.getToHeader();

    // To header is not null if we got to here
    if (to.getTag() == null) {
      to.setTag(SipTag.generateTag());
    }

    server.sendResponse(response);

    return server;
  }

  public static boolean recognize(URI uri, SipURI myURL) {
    boolean b = false;

    if (uri.isSipURI()) {
      SipURI url = (SipURI) uri;
      b = recognize(url, myURL);
    }
    return b;
  }

  public static boolean recognize(SipURI url, SipURI myURL) {
    boolean b = false;

    if (url.getMAddrParam() != null) {
      b = recognize(url.getMAddrParam(), url.getPort(), url.getTransportParam(), myURL);
    } else {
      b = recognize(url.getHost(), url.getPort(), url.getTransportParam(), myURL);
    }
    return b;
  }

  public static boolean recognize(String host, int port, String transport, SipURI myURL) {
    logger.debug(
        "Entering recognize(" + host + ", " + port + ", " + transport + ", " + myURL + ")");
    boolean b =
        (host.equals(myURL.getHost())
            && port == myURL.getPort()
            && transport.equalsIgnoreCase(myURL.getTransportParam()));
    logger.debug("Leaving recognize(), returning " + b);
    return b;
  }

  public static boolean checkSipUriMatches(SipURI uri1, SipURI uri2) {
    return uri1.getHost().equalsIgnoreCase(uri2.getHost())
        && uri1.getPort() == uri2.getPort()
        && uri1.getUser().equalsIgnoreCase(uri2.getUser());
  }
}
