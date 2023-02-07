package com.cisco.dsb.proxy.bootstrap.proxyserver;

import com.cisco.dsb.common.config.CertConfigurationProperties;
import com.cisco.dsb.common.config.TruststoreConfigurationProperties;
import com.cisco.dsb.common.config.sip.CommonConfigurationProperties;
import com.cisco.dsb.common.executor.DhruvaExecutorService;
import com.cisco.dsb.common.ratelimiter.DsbRateLimiter;
import com.cisco.dsb.common.ratelimiter.DsbRateLimiterValve;
import com.cisco.dsb.common.service.MetricService;
import com.cisco.dsb.common.sip.bean.SIPListenPoint;
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
import java.io.FileNotFoundException;
import java.io.IOException;
import java.util.Properties;
import java.util.concurrent.CompletableFuture;
import javax.net.ssl.KeyManager;
import javax.net.ssl.TrustManager;
import javax.sip.SipFactory;
import javax.sip.SipListener;
import javax.sip.SipStack;
import lombok.CustomLog;
import org.springframework.util.ResourceUtils;

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
      SIPListenPoint listenPoint,
      DsbRateLimiter dsbRateLimiter,
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
                getStackProperties(listenPoint),
                listenPoint,
                handler,
                executorService,
                trustManager,
                keyManager,
                this.metricService);
        if (sipStack instanceof SipStackImpl) {
          SipStackImpl sipStackImpl = (SipStackImpl) sipStack;
          ((DsbJainSipMessageProcessorFactory) sipStackImpl.messageProcessorFactory)
              .initFromApplication(commonConfigurationProperties, executorService, metricService);
          sipStackImpl.sipMessageValves.forEach(
              sipMessageValve -> {
                if (sipMessageValve instanceof DsbRateLimiterValve) {
                  ((DsbRateLimiterValve) sipMessageValve).initFromApplication(dsbRateLimiter);
                }
              });
        }
        serverStartFuture.complete(sipStack);
        break;
      } catch (Exception e) {
        logger.error("Unable to start listenPoint", e);
        if (retryCount == 0 || !(e.getCause() instanceof IOException)) {
          serverStartFuture.completeExceptionally(e);
          break;
        }
        retryCount--;
        logger.info(
            "Retrying to bind on {}:{} after {} seconds. Retries left:{}",
            listenPoint.getHostIPAddress(),
            listenPoint.getPort(),
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

  private Properties getStackProperties(SIPListenPoint listenPoint) throws FileNotFoundException {

    CertConfigurationProperties certConfigurationProperties = listenPoint.getCertPolicy();
    TruststoreConfigurationProperties truststoreConfigurationProperties =
        commonConfigurationProperties.getTruststoreConfig();

    Properties stackProps = ProxyStackFactory.getDefaultProxyStackProperties(listenPoint.getName());
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
    if (listenPoint.getTransport() == Transport.TLS) {
      stackProps.setProperty(
          "gov.nist.javax.sip.TLS_CLIENT_AUTH_TYPE",
          certConfigurationProperties.getClientAuthType().toString());
      stackProps.setProperty(
          "gov.nist.javax.sip.TLS_CLIENT_PROTOCOLS",
          String.join(",", truststoreConfigurationProperties.getTlsProtocols()));
      stackProps.setProperty(
          "gov.nist.javax.sip.ENABLED_CIPHER_SUITES",
          String.join(",", truststoreConfigurationProperties.getCiphers()));
      String keyAbsPath = getAbsFilePath(truststoreConfigurationProperties.getKeyStoreFilePath());

      stackProps.setProperty("javax.net.ssl.keyStore", keyAbsPath);
      stackProps.setProperty(
          "javax.net.ssl.keyStorePassword",
          truststoreConfigurationProperties.getKeyStorePassword());
      stackProps.setProperty(
          "javax.net.ssl.keyStoreType", truststoreConfigurationProperties.getKeyStoreType());

      String trustAbsPath =
          getAbsFilePath(truststoreConfigurationProperties.getTrustStoreFilePath());

      stackProps.setProperty("javax.net.ssl.trustStore", trustAbsPath);
      stackProps.setProperty(
          "javax.net.ssl.trustStorePassword",
          truststoreConfigurationProperties.getTrustStorePassword());
      stackProps.setProperty(
          "javax.net.ssl.trustStoreType", truststoreConfigurationProperties.getTrustStoreType());
    }

    stackProps.setProperty("gov.nist.javax.sip.NETWORK_LAYER", DsbNetworkLayer.class.getName());

    stackProps.setProperty(
        "gov.nist.javax.sip.RELIABLE_CONNECTION_KEEP_ALIVE_TIMEOUT",
        commonConfigurationProperties.getReliableKeepAlivePeriod());
    stackProps.setProperty(
        "gov.nist.javax.sip.MIN_KEEPALIVE_TIME_SECONDS",
        commonConfigurationProperties.getMinKeepAliveTimeSeconds());

    stackProps.setProperty(
        "gov.nist.javax.sip.NEVER_ADD_RECEIVED_RPORT",
        String.valueOf(!listenPoint.isEnableRport()));
    stackProps.setProperty(
        "gov.nist.javax.sip.ALWAYS_ADD_RPORT", String.valueOf(listenPoint.isEnableRport()));

    // Is rate Limit enabled for the corresponding listen point
    if (listenPoint.isEnableRateLimiter()) {
      String valve = DsbRateLimiterValve.class.getCanonicalName();
      stackProps.setProperty("gov.nist.javax.sip.SIP_MESSAGE_VALVE", valve);
    }
    return stackProps;
  }

  private String getAbsFilePath(String filePath) throws FileNotFoundException {
    String absolutePath = ResourceUtils.getFile(filePath).getAbsolutePath();
    return absolutePath;
  }
}
