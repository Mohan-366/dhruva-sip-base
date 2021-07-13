package com.cisco.dhruva.sip.proxy;

import com.cisco.dsb.exception.DhruvaException;
import com.cisco.dsb.sip.jain.JainSipHelper;
import com.cisco.dsb.sip.proxy.SipUtils;
import com.cisco.dsb.sip.stack.util.SipTag;
import com.cisco.dsb.transport.Transport;
import com.cisco.dsb.util.log.DhruvaLoggerFactory;
import com.cisco.dsb.util.log.Logger;
import com.google.common.base.Preconditions;
import gov.nist.javax.sip.message.SIPMessage;
import gov.nist.javax.sip.message.SIPRequest;
import gov.nist.javax.sip.message.SIPResponse;
import gov.nist.javax.sip.stack.MessageChannel;

import java.io.IOException;
import java.security.NoSuchAlgorithmException;
import java.security.Provider;
import java.security.Security;
import java.security.cert.X509Certificate;
import java.text.ParseException;
import javax.crypto.KeyGenerator;
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

public class ProxyUtils {
  private static Logger logger = DhruvaLoggerFactory.getLogger(ProxyUtils.class);

  private static final String nullString = "null";

  private static final Logger Log = DhruvaLoggerFactory.getLogger(ProxyUtils.class);

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




  public static void fixRequestForForking(
          SIPRequest request, boolean stripVia, boolean stripRecordRoute) {
    request.setFinalised(false);

    if (stripVia) {
      removeTopVia(request);
    }

    if (stripRecordRoute) {
      removeTopRecordRoute(request);
    }

    try {
      request.lrUnescape();
    } catch (Exception e) {
      Log.warn("Exception encountered while trying to unescape request", e);
    }

    request.setBindingInfo(new DsBindingInfo());
  }

  public static DsSipRequest cloneRequestForForking(
          DsSipRequest originalRequest, boolean stripVia, boolean stripRecordRoute) {
    DsSipRequest clone = (DsSipRequest) originalRequest.clone();
    fixRequestForForking(clone, stripVia, stripRecordRoute);
    return clone;
  }

  public static KeyGenerator getAESKeyGenerator() throws NoSuchAlgorithmException {
    // , NoSuchPaddingException, InvalidKeyException{
    KeyGenerator keyGen;

    try {
      // first try to use preconfigured crypto provider
      keyGen = KeyGenerator.getInstance("AES");
      Log.info("Created default AES KeyGen");

    } catch (NoSuchAlgorithmException e) {
      Log.warn("Error getting default KeyGen for AES", e);
      try {

        // if none configured, try to use the standard Sun provider
        // (still must be installed)
        Class sunJceClass = Class.forName(SUN_JCE_PROVIDER);
        Provider sunJCE = (Provider) sunJceClass.newInstance();
        Security.addProvider(sunJCE);

        keyGen = KeyGenerator.getInstance("AES");
      } catch (Throwable e1) {
        Log.warn("Error getting Sun's KeyGen for AES", e1);
        try {
          // if none configured, try to use the Open JCE provider
          // (still must be installed)
          Class sunJceClass = Class.forName(OPEN_JCE_PROVIDER);
          Provider openJCE = (Provider) sunJceClass.newInstance();
          Security.addProvider(openJCE);

          keyGen = KeyGenerator.getInstance("AES");
          Log.debug("Created Open JCE KeyGen");
        } catch (Throwable e2) {
          Log.error("Cannot create a AES KeyGen", e2);
          throw new NoSuchAlgorithmException("Cannot create AES KeyGen:" + e2.getMessage());
        }
      }
    }
    return keyGen;
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
          ServerTransaction server, SIPResponse response) throws DhruvaException, IOException, ParseException, SipException, InvalidArgumentException {
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

  public static boolean recognize(
          String host, int port, String transport, SipURI myURL) {
    Log.debug("Entering recognize(" + host + ", " + port + ", " + transport + ", " + myURL + ")");
    boolean b =
            (host.equals(myURL.getHost())
                    && port == myURL.getPort()
                    && transport.toString() == myURL.getTransportParam());
    Log.debug("Leaving recognize(), returning " + b);
    return b;
  }
}
