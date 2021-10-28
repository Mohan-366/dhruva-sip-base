package com.cisco.dsb.common.config.sip;

import com.cisco.dsb.common.dto.TrustedSipSources;
import com.cisco.dsb.common.sip.bean.SIPListenPoint;
import com.cisco.dsb.common.sip.tls.TLSAuthenticationType;
import com.cisco.dsb.common.transport.Transport;
import com.cisco.wx2.dto.ErrorInfo;
import com.cisco.wx2.dto.ErrorList;
import java.security.KeyStore;
import java.util.*;
import lombok.CustomLog;
import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.stereotype.Component;

@Component
@EnableConfigurationProperties
@ConfigurationProperties(prefix = "common")
@CustomLog
public class CommonConfigurationProperties {
  // Defaults for listenPoint builder and sipProxy builder
  public static final String DEFAULT_NETWORK_NAME = "TCPNetwork";
  public static final String DEFAULT_HOST_IP = "127.0.0.1";
  public static final Transport DEFAULT_TRANSPORT = Transport.TCP;
  public static int DEFAULT_PORT = 5060;
  public static final boolean DEFAULT_RECORD_ROUTE_ENABLED = true;
  public static final TLSAuthenticationType DEFAULT_TLS_AUTH_TYPE = TLSAuthenticationType.SERVER;
  public static final Boolean DEFAULT_ENABLE_CERT_SERVICE = false;
  public static final long DEFAULT_TIMER_C_DURATION_MILLISEC = 180000;
  public static final boolean DEFAULT_ATTACH_EXTERNAL_IP = false;

  @Getter @Setter private boolean enableCertService = false;
  @Getter @Setter private boolean useRedisAsCache = false;
  @Getter @Setter private TLSAuthenticationType tlsAuthType = TLSAuthenticationType.SERVER;

  @Getter @Setter private String sipCertificate;
  @Getter @Setter private String sipPrivateKey;

  @Getter @Setter private int udpEventloopThreadCount = 1;
  @Getter @Setter private int tlsEventloopThreadCount = 20;
  @Getter @Setter private int connectionIdleTimeout = 14400;
  @Getter @Setter private List<String> tlsCiphers = CipherSuites.allowedCiphers;

  @Getter @Setter private boolean hostPortEnabled = false;
  @Getter @Setter private String hostInfo;

  @Getter @Setter private boolean acceptedIssuersEnabled = false;
  @Getter @Setter private int tlsHandShakeTimeOutMilliSeconds = 5000;
  @Getter @Setter private long connectionWriteTimeoutInMllis = 60000;
  @Getter @Setter private int tlsOcspResponseTimeoutInSeconds = 5;

  @Getter @Setter
  private String tlsTrustStoreFilePath = System.getProperty("javax.net.ssl.trustStore");

  @Getter @Setter private String tlsTrustStoreType = KeyStore.getDefaultType();

  @Getter @Setter
  private String tlsTrustStorePassword = System.getProperty("javax.net.ssl.trustStorePassword", "");

  @Getter @Setter private String tlsKeyStoreFilePath = System.getProperty("javax.net.ssl.keyStore");
  @Getter @Setter private String tlsKeyStoreType = KeyStore.getDefaultType();

  @Getter @Setter
  private String tlsKeyStorePassword = System.getProperty("javax.net.ssl.keyStorePassword", "");

  @Getter @Setter private boolean tlsCertRevocationEnableSoftFail = false;
  @Getter @Setter private boolean tlsCertEnableOcsp = false;
  @Getter @Setter private String clientAuthType = "Disabled";
  @Getter @Setter private boolean nioEnabled = false;

  // All Keep Alive time related values in seconds
  @Getter @Setter private long keepAlivePeriod = 20L;
  @Getter @Setter private String reliableKeepAlivePeriod = "25";
  @Getter @Setter private String minKeepAliveTimeSeconds = "20";
  @Getter @Setter private boolean logKeepAlivesEnabled = false;
  @Getter @Setter private String trustedSipSources = "";
  @Getter @Setter private boolean requiredTrustedSipSources = false;

  // DSBNetworkLayer is using this as static variable
  @Getter private static int socketConnectionTimeout = 8000;
  // TODO: this is hack, inject spring object everywhere instead of static field
  public void setSocketConnectionTimeout(int timeout) {
    socketConnectionTimeout = timeout;
  }

  @Getter @Setter private String dhruvaUserAgent = "WX2Dhruva";
  @Getter @Setter private List<String> tlsProtocols = Collections.singletonList("TLSv1.2");

  @Getter @Setter
  private List<SIPListenPoint> listenPoints =
      Collections.singletonList(SIPListenPoint.SIPListenPointBuilder().build());

  @Getter private int dnsCacheSize = 1000;
  @Getter private long timeOutDnsCache = 32_000L;
  @Getter private long timeOutDns = 10_000L;
  @Getter @Setter private long dnsLookupTimeoutMillis = 10_000L;

  public void setDnsCacheSize(int size) {
    if (size > 0) this.dnsCacheSize = size;
  }

  public void setTimeOutDnsCache(int timeOutCache) {
    if (timeOutCache > 0) this.timeOutDnsCache = timeOutCache;
  }

  public void setTimeOutDns(int timeOutDns) {
    if (timeOutDns > 0) this.timeOutDns = timeOutDns;
  }

  public TrustedSipSources getTrustedSipSources() {
    TrustedSipSources trustedSipSources = new TrustedSipSources(this.trustedSipSources);
    // Log and purge any values that are not valid
    ErrorList errorList = trustedSipSources.validateSources();
    for (ErrorInfo errorInfo : errorList) {
      logger.warn("trustedSipSources included invalid entry: {}", errorInfo.getDescription());
      trustedSipSources.remove(errorInfo.getDescription());
    }
    return trustedSipSources;
  }
}
