package com.cisco.dsb.proxy.bootstrap.proxyserver;

import com.cisco.dsb.common.config.sip.CommonConfigurationProperties;
import com.cisco.dsb.common.executor.DhruvaExecutorService;
import com.cisco.dsb.common.service.MetricService;
import com.cisco.dsb.common.sip.jain.JainSipHelper;
import com.cisco.dsb.common.sip.jain.JainStackInitializer;
import com.cisco.dsb.common.sip.jain.channelCache.DsbJainSipMessageProcessorFactory;
import com.cisco.dsb.common.sip.stack.dto.DhruvaNetwork;
import com.cisco.dsb.common.sip.tls.DsbNetworkLayer;
import com.cisco.dsb.common.sip.tls.DsbTrustManager;
import com.cisco.dsb.common.transport.Transport;
import com.cisco.dsb.common.util.log.DhruvaServerLogger;
import com.cisco.dsb.common.util.log.DhruvaStackLogger;
import com.cisco.dsb.proxy.bootstrap.Server;
import com.cisco.dsb.proxy.sip.ProxyStackFactory;
import gov.nist.javax.sip.SipStackImpl;
import java.io.IOException;
import java.net.InetAddress;
import java.util.Properties;
import java.util.concurrent.CompletableFuture;
import javax.net.ssl.KeyManager;
import javax.net.ssl.TrustManager;
import javax.sip.SipFactory;
import javax.sip.SipListener;
import javax.sip.SipStack;
import lombok.CustomLog;
import org.apache.commons.lang3.RandomStringUtils;

@CustomLog
public class SipServer implements Server {

  private final Transport transport;
  private SipListener sipListener;
  private DhruvaNetwork networkConfig;
  private MetricService metricService;
  private DhruvaExecutorService executorService;
  private CommonConfigurationProperties commonConfigurationProperties;
  private TrustManager trustManager;
  private KeyManager keyManager;

  public SipServer(
      Transport transport,
      SipListener handler,
      DhruvaExecutorService executorService,
      MetricService metricService,
      CommonConfigurationProperties commonConfigurationProperties) {
    this.transport = transport;
    this.metricService = metricService;
    this.sipListener = handler;
    this.executorService = executorService;
    this.commonConfigurationProperties = commonConfigurationProperties;
  }

  @Override
  public void startListening(
      InetAddress address,
      int port,
      SipListener handler,
      DsbTrustManager trustManager,
      KeyManager keyManager,
      CompletableFuture<SipStack> serverStartFuture) {

    SipFactory sipFactory = JainSipHelper.getSipFactory();
    int retryCount = this.commonConfigurationProperties.getListenPointRetryCount();
    int retryDelay = this.commonConfigurationProperties.getListenPointRetryDelay();
    while (retryCount >= 0) {
      try {
        SipStack sipStack =
            JainStackInitializer.getSimpleStack(
                this.commonConfigurationProperties,
                sipFactory,
                sipFactory.getPathName(),
                getStackProperties(),
                address.getHostAddress(),
                port,
                transport.toString(),
                handler,
                executorService,
                trustManager,
                keyManager,
                this.metricService);
        if (sipStack instanceof SipStackImpl) {
          SipStackImpl sipStackImpl = (SipStackImpl) sipStack;
          ((DsbJainSipMessageProcessorFactory) sipStackImpl.messageProcessorFactory)
              .initFromApplication(commonConfigurationProperties, executorService, metricService);
        }
        serverStartFuture.complete(sipStack);
        break;
      } catch (Exception e) {
        logger.warn("Unable to start listenPoint", e);
        if (retryCount == 0 || !(e.getCause() instanceof IOException)) {
          serverStartFuture.completeExceptionally(e);
          break;
        }
        retryCount--;
        logger.info(
            "Retrying to bind on {}:{} after {} seconds. Retries left:{}",
            address.getHostAddress(),
            port,
            retryDelay,
            retryCount);

        try {
          Thread.sleep(retryDelay * 1000L);
        } catch (InterruptedException ex) {
          logger.error("Interrupted while waiting to retry creation on listenPoint", ex);
        }
      }
    }
  }

  private Properties getStackProperties() {

    Properties stackProps =
        ProxyStackFactory.getDefaultProxyStackProperties(RandomStringUtils.randomAlphanumeric(5));
    stackProps.setProperty("gov.nist.javax.sip.STACK_LOGGER", DhruvaStackLogger.class.getName());
    stackProps.setProperty("gov.nist.javax.sip.SERVER_LOGGER", DhruvaServerLogger.class.getName());
    stackProps.setProperty("gov.nist.javax.sip.LOG_MESSAGE_CONTENT", "true");
    stackProps.setProperty("gov.nist.javax.sip.READ_TIMEOUT", "1000");
    stackProps.setProperty("gov.nist.javax.sip.PASS_INVITE_NON_2XX_ACK_TO_LISTENER", "true");

    // threading related
    stackProps.setProperty("gov.nist.javax.sip.REENTRANT_LISTENER", "true");
    stackProps.setProperty("gov.nist.javax.sip.THREAD_POOL_SIZE", "64");
    stackProps.setProperty("gov.nist.javax.sip.TCP_POST_PARSING_THREAD_POOL_SIZE", "30");

    // this is the default
    stackProps.setProperty("gov.nist.javax.sip.MAX_FORK_TIME_SECONDS", "0");

    // seems useful
    stackProps.setProperty("gov.nist.javax.sip.MAX_MESSAGE_SIZE", "32000");
    stackProps.setProperty("gov.nist.javax.sip.MAX_LISTENER_RESPONSE_TIME", "120");

    // Disable dialog support for proxy
    stackProps.setProperty("javax.sip.AUTOMATIC_DIALOG_SUPPORT", "off");

    stackProps.setProperty(
        "gov.nist.javax.sip.MESSAGE_PROCESSOR_FACTORY",
        DsbJainSipMessageProcessorFactory.class.getName());
    //    stackProps.setProperty("gov.nist.javax.sip.DEBUG_LOG", JainStackLogger.class.getName());
    stackProps.setProperty(
        "gov.nist.javax.sip.TLS_CLIENT_AUTH_TYPE",
        commonConfigurationProperties.getClientAuthType());
    stackProps.setProperty("gov.nist.javax.sip.NETWORK_LAYER", DsbNetworkLayer.class.getName());
    stackProps.setProperty("gov.nist.javax.sip.TLS_CLIENT_PROTOCOLS", "TLSv1.2");
    stackProps.setProperty(
        "gov.nist.javax.sip.RELIABLE_CONNECTION_KEEP_ALIVE_TIMEOUT",
        commonConfigurationProperties.getReliableKeepAlivePeriod());
    stackProps.setProperty(
        "gov.nist.javax.sip.MIN_KEEPALIVE_TIME_SECONDS",
        commonConfigurationProperties.getMinKeepAliveTimeSeconds());

    stackProps.setProperty("gov.nist.javax.sip.ALWAYS_ADD_RPORT", "true");

    return stackProps;
  }
}
