package com.cisco.dsb.common.ua;

import gov.nist.javax.sip.stack.ClientAuthType;
import java.util.Properties;

public class UAStackFactory {

  // private static Logger logger = DSBLogger.getLogger(UAStackFactory.class);

  private static UAStackFactory instance = new UAStackFactory();

  UAStackFactory() {}

  public static UAStackFactory getInstance() {
    return instance;
  }

  public static Properties getDefaultUAStackProperties(String stackName) {

    Properties properties = new Properties();
    properties.setProperty("javax.sip.STACK_NAME", stackName);

    properties.setProperty("gov.nist.javax.sip.LOG_MESSAGE_CONTENT", "true");
    properties.setProperty("gov.nist.javax.sip.READ_TIMEOUT", "1000");
    properties.setProperty("gov.nist.javax.sip.PASS_INVITE_NON_2XX_ACK_TO_LISTENER", "true");
    properties.setProperty(
        "gov.nist.javax.sip.TLS_CLIENT_AUTH_TYPE", ClientAuthType.DisabledAll.toString());

    // threading related
    properties.setProperty("gov.nist.javax.sip.REENTRANT_LISTENER", "true");
    properties.setProperty("gov.nist.javax.sip.THREAD_POOL_SIZE", "64");
    properties.setProperty("gov.nist.javax.sip.TCP_POST_PARSING_THREAD_POOL_SIZE", "30");

    // this is the default
    properties.setProperty("gov.nist.javax.sip.MAX_FORK_TIME_SECONDS", "0");

    // seems useful
    properties.setProperty("gov.nist.javax.sip.MAX_LISTENER_RESPONSE_TIME", "120");

    /**
     * ---- APPS can set the following in their app logic ---- Adding them here for reference
     * (l2sip's)
     */
    // properties.setProperty("gov.nist.javax.sip.STACK_LOGGER", L2SipStackLogger.class.getName());
    // properties.setProperty("gov.nist.javax.sip.SERVER_LOGGER",
    // L2SipHeaderLogger.class.getName());
    // properties.setProperty("gov.nist.javax.sip.NETWORK_LAYER",
    // L2SipNetworkLayer.class.getName());

    // use NIO
    // properties.setProperty("gov.nist.javax.sip.MESSAGE_PROCESSOR_FACTORY",
    // "gov.nist.javax.sip.stack.NioMessageProcessorFactory");
    // properties.setProperty("gov.nist.javax.sip.MAX_TX_LIFETIME_INVITE",
    // String.valueOf(sipProperties.getInviteTxTimeoutSecs()));
    // properties.setProperty("gov.nist.javax.sip.MAX_TX_LIFETIME_NON_INVITE",
    // String.valueOf(sipProperties.getNonInviteTxTimeoutSecs()));

    return properties;
  }
}
