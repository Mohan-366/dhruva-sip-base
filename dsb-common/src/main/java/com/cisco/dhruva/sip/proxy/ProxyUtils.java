package com.cisco.dhruva.sip.proxy;

import com.cisco.dsb.sip.jain.JainSipHelper;
import com.cisco.dsb.sip.proxy.SipUtils;
import com.cisco.dsb.util.log.DhruvaLoggerFactory;
import com.cisco.dsb.util.log.Logger;
import com.google.common.base.Preconditions;
import gov.nist.javax.sip.message.SIPMessage;
import gov.nist.javax.sip.message.SIPRequest;
import gov.nist.javax.sip.stack.MessageChannel;
import gov.nist.javax.sip.stack.SIPClientTransaction;

import java.security.cert.X509Certificate;
import java.text.ParseException;
import java.util.concurrent.CompletableFuture;
import javax.net.ssl.SSLException;
import javax.net.ssl.SSLPeerUnverifiedException;
import javax.net.ssl.SSLSession;
import javax.sip.InvalidArgumentException;
import javax.sip.ServerTransaction;
import javax.sip.SipException;
import javax.sip.SipProvider;
import javax.sip.address.Address;
import javax.sip.address.SipURI;
import javax.sip.header.CSeqHeader;
import javax.sip.header.ContactHeader;
import javax.sip.message.Response;

public class ProxyUtils {
  private static Logger logger = DhruvaLoggerFactory.getLogger(ProxyUtils.class);

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
}
