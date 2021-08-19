package com.cisco.dsb.proxy.sip;

import com.cisco.dsb.common.config.SipProperties;
import com.cisco.dsb.common.dns.DnsLookup;
import com.cisco.dsb.common.sip.jain.JainSipHelper;
import com.cisco.dsb.common.sip.jain.JainStackInitializer;
import java.util.Properties;
import javax.net.ssl.KeyManager;
import javax.sip.SipStack;

/** Stack factory for creating proxy stack. */
public final class ProxyStackFactory {

  // private static Logger logger = DSBLogger.getLogger(ProxyStackFactory.class);

  private static ProxyStackFactory instance = new ProxyStackFactory();

  ProxyStackFactory() {}

  public static ProxyStackFactory getInstance() {
    return instance;
  }

  public static Properties getDefaultProxyStackProperties(String stackName) {

    Properties properties = new Properties();
    properties.setProperty("javax.sip.STACK_NAME", stackName);

    properties.setProperty("gov.nist.javax.sip.LOG_MESSAGE_CONTENT", "true");
    properties.setProperty("gov.nist.javax.sip.READ_TIMEOUT", "1000");
    properties.setProperty("gov.nist.javax.sip.PASS_INVITE_NON_2XX_ACK_TO_LISTENER", "true");

    // threading related
    properties.setProperty("gov.nist.javax.sip.REENTRANT_LISTENER", "true");
    properties.setProperty("gov.nist.javax.sip.THREAD_POOL_SIZE", "64");
    properties.setProperty("gov.nist.javax.sip.TCP_POST_PARSING_THREAD_POOL_SIZE", "30");

    // Maximum time for which the original transaction for which a forked response is received is
    // tracked.
    // This property is only relevant to Dialog Stateful applications ( User Agents or B2BUA).
    //  0 -  not the default value
    // TODO: remove if confirmed as not-applicable
    // properties.setProperty("gov.nist.javax.sip.MAX_FORK_TIME_SECONDS", "0");

    // seems useful
    properties.setProperty("gov.nist.javax.sip.MAX_MESSAGE_SIZE", "32000");
    properties.setProperty("gov.nist.javax.sip.MAX_LISTENER_RESPONSE_TIME", "120");

    // The ability to turn of dialog support is motivated by dialog free servers (such as proxy
    // servers)
    // that do not want to pay the overhead of the dialog layer
    properties.setProperty("javax.sip.AUTOMATIC_DIALOG_SUPPORT", "off");

    /**
     * ---- APPS can set the following in their app logic ---- Adding them here for reference
     * (l2sip's)
     */
    // properties.setProperty("gov.nist.javax.sip.STACK_LOGGER", DhruvaStackLogger.class.getName());

    // For Externally listening points, we need to log the entire SIP message content.
    // L2SipServerLogger will log the entire content by default.
    // properties.setProperty("gov.nist.javax.sip.SERVER_LOGGER", JainStackLogger.class.getName());

    // properties.setProperty("gov.nist.javax.sip.TLS_CLIENT_AUTH_TYPE", clientAuthType.toString());
    // properties.setProperty("gov.nist.javax.sip.NETWORK_LAYER",
    // L2SipNetworkLayer.class.getName());
    // properties.setProperty("gov.nist.javax.sip.SECURITY_MANAGER_PROVIDER",
    // L2SipSecurityManagerProvider.class.getName());
    // properties.setProperty("gov.nist.javax.sip.SIP_MESSAGE_VALVE", String.join(",", valves));

    // SIP keep alives are only needed for external SIP connections
    // properties.setProperty("gov.nist.javax.sip.MESSAGE_PROCESSOR_FACTORY",
    // L2SipJainSipMessageProcessorFactory.class.getName());

    // Check the performance, jain claims to improve performance by 50 %
    // For proxy this should be fine since we do not maintain dialog level info
    properties.setProperty("gov.nist.javax.sip.AGGRESSIVE_CLEANUP", "true");

    //     * <li><b>gov.nist.javax.sip.MIN_KEEPALIVE_TIME_SECONDS = integer</b> Minimum time between
    // keep alive
    // * pings (CRLF CRLF) from clients. If pings arrive with less than this frequency they will be
    // replied
    //            * with CRLF CRLF if greater they will be rejected. The default is -1 (i.e. do not
    // respond to CRLF CRLF).
    // * </li>
    properties.setProperty("gov.nist.javax.sip.MIN_KEEPALIVE_TIME_SECONDS", "360");

    return properties;
  }

  public SipStack createProxyStack(
      Properties properties,
      SipProperties sipProperties,
      KeyManager keyManager,
      DnsLookup dnsLookup)
      throws Exception {

    SipStack sipStack =
        JainStackInitializer.createSipStack(
            JainSipHelper.getSipFactory(), JainSipHelper.getSipFactory().getPathName(), properties);

    /**
     * Additional stack related initialization like - setting custom network layer, security manager
     * provider, ssl handshake timeout, enabled protocols Adding them here for reference (l2sip's) *
     */
    // L2SIP behaviour below : for reference
    /*NetworkLayer networkLayer = ((SIPTransactionStack) sipStack).getNetworkLayer();
    if (networkLayer instanceof L2SipNetworkLayer) {
      logger.info("initializing SSLContext in L2SipNetworkLayer");
      ((L2SipNetworkLayer) networkLayer).init(sipProperties, trustManager, keyManager, dnsLookup, true, true);
    }

    JainSipStackHelper.init((SIPTransactionStack)sipStack, sipProperties, trustManager, keyManager);

    ((SIPTransactionStack) sipStack).setSslHandshakeTimeout(sipProperties.getSslHandshakeTimeout());

    ((SipStackImpl)sipStack).setEnabledProtocols(sipProperties.getSipTlsProtocols());*/

    return sipStack;
  }
}
