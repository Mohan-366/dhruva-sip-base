package com.cisco.dsb.sip.proxy;

import com.cisco.dsb.util.log.DhruvaLoggerFactory;
import com.cisco.dsb.util.log.Logger;
import com.cisco.wx2.util.Token;
import com.google.common.base.Preconditions;
import com.google.common.net.InetAddresses;
import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import gov.nist.javax.sip.stack.HandshakeCompletedListenerImpl;
import gov.nist.javax.sip.stack.MessageChannel;
import gov.nist.javax.sip.stack.NioTlsMessageChannel;
import gov.nist.javax.sip.stack.TLSMessageChannel;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Random;
import javax.net.ssl.HandshakeCompletedEvent;
import javax.net.ssl.SSLPeerUnverifiedException;
import javax.net.ssl.SSLSession;
import javax.sip.header.ViaHeader;

public final class SipUtils {
  private static Logger logger = DhruvaLoggerFactory.getLogger(SipUtils.class);

  public static final String BRANCH_MAGIC_COOKIE = "z9hG4bK";

  /** toHex */
  private static final char[] toHex = {
    '0', '1', '2', '3', '4', '5', '6', '7', '8', '9', 'a', 'b', 'c', 'd', 'e', 'f'
  };

  private SipUtils() {}

  /** @return Branch id value */
  @SuppressFBWarnings(
      value = {"PREDICTABLE_RANDOM", "WEAK_MESSAGE_DIGEST_MD5"},
      justification = "baseline suppression")
  public static String generateBranchId() {
    StringBuffer ret = new StringBuffer();
    StringBuffer b = new StringBuffer();
    String hex;

    b.append((new Random()).nextInt(10000));
    b.append(System.currentTimeMillis());

    try {
      MessageDigest messageDigest = MessageDigest.getInstance("MD5");
      byte[] bytes = messageDigest.digest(b.toString().getBytes());
      hex = toHexString(bytes);
    } catch (NoSuchAlgorithmException ex) {
      hex = "NoSuchAlgorithmExceptionMD5";
    }

    ret.append(BRANCH_MAGIC_COOKIE + hex);

    return ret.toString();
  }

  /**
   * @param b Array of bytes
   * @return A hexadecimal string
   */
  public static String toHexString(byte[] b) {
    int pos = 0;
    char[] c = new char[b.length * 2];

    for (int i = 0; i < b.length; i++) {
      c[pos++] = toHex[(b[i] >> 4) & 0x0F];
      c[pos++] = toHex[b[i] & 0x0f];
    }

    return new String(c);
  }

  public static SSLSession getSslSession(MessageChannel messageChannel)
      throws SSLPeerUnverifiedException {
    if (messageChannel == null || !messageChannel.isSecure()) {
      return null;
    }

    Preconditions.checkArgument(
        messageChannel instanceof TLSMessageChannel
            || messageChannel instanceof NioTlsMessageChannel,
        "Not a TLS message channel");

    HandshakeCompletedListenerImpl handshakeCompletedListener = null;

    logger.debug(
        "getSslSession from messageChannel: local=[{}] peer=[{}] hashCode={}",
        messageChannel.getHostPort(),
        messageChannel.getPeerHostPort(),
        messageChannel.hashCode());

    if (messageChannel instanceof TLSMessageChannel) {
      handshakeCompletedListener =
          ((TLSMessageChannel) messageChannel).getHandshakeCompletedListener();
    } else if (messageChannel instanceof NioTlsMessageChannel) {
      handshakeCompletedListener =
          ((NioTlsMessageChannel) messageChannel).getHandshakeCompletedListener();
    }

    if (handshakeCompletedListener == null) {
      throw new SSLPeerUnverifiedException("Null handshake completed listener");
    }

    SSLSession sslSession = null;

    // For debugging https://jira-eng-gpk2.cisco.com/jira/browse/SPARK-100870 in govcloud

    if (messageChannel instanceof TLSMessageChannel) {
      logger.debug(
          "begin getHandshakeCompletedEvent messageChannel.hashCode={} handshakeCompletedListener.hashCode={}",
          messageChannel.hashCode(),
          handshakeCompletedListener.hashCode());
      HandshakeCompletedEvent handshakeCompletedEvent =
          handshakeCompletedListener.getHandshakeCompletedEvent();

      if (handshakeCompletedEvent == null) {
        throw new SSLPeerUnverifiedException("Null handshake completed event");
      }

      sslSession = handshakeCompletedEvent.getSession();
    }
    // TODO DSB
    //        } else if (messageChannel instanceof L2SipNioTlsMessageChannel) {
    //            L2SipSSLStateMachine sslStateMachine = ((L2SipNioTlsMessageChannel)
    // messageChannel).getSslStateMachine();
    //            sslSession = sslStateMachine.getSslEngine().getSession();
    //        }

    if (sslSession == null) {
      throw new SSLPeerUnverifiedException("Null SSL session available");
    }

    return sslSession;
  }

  public static String getConnectionProtocol(MessageChannel messageChannel)
      throws SSLPeerUnverifiedException {
    if (messageChannel == null) {
      return null;
    }

    if (!messageChannel.isSecure()) {
      return "TCP";
    }

    SSLSession sslSession = getSslSession(messageChannel);
    return sslSession.getProtocol();
  }

  public static String getConnectionId(
      String direction, String protocol, MessageChannel messageChannel) {
    if (messageChannel == null) {
      return null;
    }

    return direction
        + "-"
        + protocol
        + "-"
        + messageChannel.getHostPort()
        + "-"
        + messageChannel.getPeerHostPort();
  }

  /**
   * Parses VIA header, example: Via: SIP/2.0/TLS
   * 207.182.174.140:5061;branch=z9hG4bKbO1TP113br49InE+ig+w2A~~509122;received=10.240.212.35;rport=13072
   * And returns the sent-by (RFC spec term) address (in example above returns 207.182.174.140)
   *
   * @param viaHeader The ViaHeader to parse
   * @return Returns null if sent-by address doesn't exist (can be IP or hostname)
   */
  public static String getViaHeaderSentByAddress(ViaHeader viaHeader) {
    return splitStringColonReturnFirstLeftHandOrNull(viaHeader.getHost());
  }

  /**
   * Parses VIA header, returns the received (RFC spec term) address. From {@link
   * #getViaHeaderSentByAddress(ViaHeader)} example returns 10.240.212.35.
   *
   * @param viaHeader The ViaHeader to parse
   * @return Returns null if received address doesn't exist (very likely an IP, but could be
   *     hostname)
   */
  public static String getViaHeaderReceivedAddress(ViaHeader viaHeader) {
    return splitStringColonReturnFirstLeftHandOrNull(viaHeader.getReceived());
  }

  private static String splitStringColonReturnFirstLeftHandOrNull(String value) {
    return value != null ? value.split(Token.Colon)[0] : null;
  }

  /**
   * Determines whether passed in String value is IP string literal.
   *
   * @param value Value to attempt parsing as IP string
   * @return true if the supplied string is a valid IP string literal, false otherwise
   */
  public static boolean isInetAddress(String value) {
    return InetAddresses.isInetAddress(value);
  }
}
