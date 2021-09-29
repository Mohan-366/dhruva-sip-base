package com.cisco.dsb.common.config.sip;

import com.cisco.dsb.common.sip.bean.SIPListenPoint;
import com.cisco.dsb.common.sip.bean.SIPProxy;
import com.cisco.dsb.common.transport.TLSAuthenticationType;
import com.cisco.dsb.common.transport.Transport;
import com.cisco.dsb.common.util.JsonUtilFactory;
import com.cisco.wx2.dto.BuildInfo;
import java.security.KeyStore;
import java.util.*;
import javax.sip.message.Request;
import lombok.CustomLog;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.Environment;

@Configuration
@ConfigurationProperties(prefix = "bean")
@Qualifier("dsbSIPConfigProperties")
@CustomLog
public class DhruvaSIPConfigProperties {
  private Environment env;

  public static final String SIP_LISTEN_POINTS = "sipListenPoints";

  public static final String SIP_PROXY = "sipProxy";

  public static final Transport DEFAULT_TRANSPORT = Transport.TCP;

  // MeetPass TODO
  // Env is not read properly, hence setting it here to true
  public static final boolean DEFAULT_RECORD_ROUTE_ENABLED = true;

  public static final boolean DEFAULT_PROXY_ERROR_AGGREGATOR_ENABLED = false;

  public static final boolean DEFAULT_PROXY_CREATE_DNSSERVERGROUP_ENABLED = false;

  public static final boolean DEFAULT_PROXY_PROCESS_ROUTE_HEADER_ENABLED = false;

  public static final boolean DEFAULT_PROXY_PROCESS_REGISTER_REQUEST = false;

  // timer C = 3 mins
  public static final long DEFAULT_TIMER_C_DURATION_MILLISEC = 180000;

  public static final boolean DEFAULT_ATTACH_EXTERNAL_IP = false;

  private static final String USE_REDIS_AS_CACHE = "useRedis";

  public static final TLSAuthenticationType DEFAULT_TRANSPORT_AUTH = TLSAuthenticationType.MTLS;

  public static final boolean DEFAULT_ENABLE_CERT_SERVICE = false;

  private static final String SIP_CERTIFICATE = "sipCertificate";

  private static final String SIP_PRIVATE_KEY = "sipPrivateKey";

  private static final String UDP_EVENTLOOP_THREAD_COUNT = "dsb.network.udpEventloopThreadCount";

  private static final Integer DEFAULT_UDP_EVENTLOOP_THREAD_COUNT = 1;

  private static final String TLS_EVENTLOOP_THREAD_COUNT = "dsb.network.tlsEventloopThreadCount";

  private static final Integer DEFAULT_TLS_EVENTLOOP_THREAD_COUNT = 20;

  private static final String CONNECTION_CACHE_CONNECTION_IDLE_TIMEOUT_SECONDS =
      "dsb.network.connectionCache.connectionIdleTimeout";

  private static final Integer DEFAULT_CONNECTION_CACHE_CONNECTION_IDLE_TIMEOUT_MINUTES = 14400;

  private static final String TLS_CIPHERS = "dsb.sipTlsCipherSuites";

  private static final String HOST_PORT_ENABLED = "hostPortEnabled";

  private static final Boolean DEFAULT_HOST_PORT_ENABLED = false;

  private static final String HOST_IP_OR_FQDN = "hostIpOrFqdn";

  private static final String TLS_HANDSHAKE_TIMEOUT_MILLISECONDS =
      "dsb.tlsHandShakeTimeOutMilliSeconds";
  private static final Integer DEFAULT_TLS_HANDSHAKE_TIMEOUT_MILLISECONDS = 5000;
  private static final String TLS_CA_LIST_IN_SERVER_HELLO_ENABLED =
      "dsb.tlsCaListInServerHelloEnabled";
  private static final Boolean DEFAULT_TLS_CA_LIST_IN_SERVER_HELLO_ENABLED = false;
  private static final String CONNECTION_WRITE_TIMEOUT_IN_MILLIS =
      "dsb.connectionWriteTimeoutInMllis";
  private static final long DEFAULT_CONNECTION_WRITE_TIMEOUT_IN_MILLIS = 60000;
  private static final String TLS_OCSP_RESPONSE_TIMEOUT_SECONDS =
      "dsb.tlsOcspResponseTimeoutIn" + "Seconds";
  private static final int DEFAULT_TLS_OCSP_RESPONSE_TIMEOUT_SECONDS = 5;
  private static final String TLS_TRUST_STORE_FILE_PATH = "dsb.tlsTrustStoreFilePath";
  private static final String DEFAULT_TRUST_STORE_FILE_PATH =
      System.getProperty("javax.net.ssl.trustStore");
  private static final String TLS_TRUST_STORE_TYPE = "dsb.tlsTrustStoreType";
  private static final String DEFAULT_TLS_TRUST_STORE_TYPE = KeyStore.getDefaultType();
  private static final String TLS_TRUST_STORE_PASSWORD = "dsb.tlsTrustStorePassword";
  private static final String TLS_KEY_STORE_FILE_PATH = "dsb.tlsKeyStoreFilePath";
  private static final String TLS_KEY_STORE_TYPE = "dsb.tlsKeyStoreType";
  private static final String DEFAULT_TLS_KEY_STORE_TYPE = KeyStore.getDefaultType();
  private static final String TLS_KEY_STORE_PASSWORD = "dsb.tlsKeyStorePassword";
  private static final String DEFAULT_TLS_TRUST_STORE_PASSWORD =
      System.getProperty("javax.net.ssl.trustStorePassword", "");
  private static final String TLS_CERT_REVOCATION_SOFTFAIL_ENABLED =
      "dsb.tlsCertRevocationEnable" + "SoftFail";
  private static final Boolean DEFAULT_TLS_CERT_REVOCATION_SOFTFAIL_ENABLED = Boolean.TRUE;
  private static final String TLS_CERT_OCSP_ENABLED = "dsb.tlsCertEnableOcsp";
  private static final Boolean DEFAULT_TLS_CERT_OCSP_ENABLED = true;
  private static final String CLIENT_AUTH_TYPE = "dsb.clientAuthType";
  private static final String DEFAULT_CLIENT_AUTH_TYPE = "Disabled";

  private static final String NIO_ENABLED = "dsb.nioEnabled";
  private static final Boolean DEFAULT_NIO_ENABLED = false;
  public static int DEFAULT_PORT = 5060;

  private static BuildInfo buildInfo;
  // All Keep Alive time related values in seconds
  private static final String KEEP_ALIVE_PERIOD = "keepAlivePeriod";
  private static final Long DEFAULT_KEEP_ALIVE_PERIOD = Long.valueOf(20);
  private static final String RELIABLE_CONNECTION_KEEP_ALIVE_TIMEOUT =
      "dsb.reliableKeepAlivePeriod";
  private static final String DEFAULT_RELIABLE_CONNECTION_KEEP_ALIVE_TIMEOUT = "25";
  private static final String MIN_KEEP_ALIVE_TIME_SECONDS = "dsb.minKeepAliveTimeSeconds";
  private static final String DEFAULT_MIN_KEEP_ALIVE_TIME_SECONDS = "20";
  private static final String LOG_KEEP_ALIVES_ENABLED = "logKeepAlivesEnabled";
  private static final Boolean DEFAULT_LOG_KEEP_ALIVES_ENABLED = false;

  public static final String DEFAULT_DHRUVA_USER_AGENT = "WX2_Dhruva";

  private String[] tlsProtocols = new String[] {"TLSv1.2"};
  private String configuredSipProxy;
  private String HOST_IP_OR_FQDN_VALUE;
  private Boolean USE_REDIS_AS_CACHE_VALUE;
  private String configuredListeningPoints;
  private List<SIPListenPoint> listenPoints;
  private SIPProxy proxy;
  private static final int defaultCacheSize = 1_000;
  private int cacheSize;

  private static final long defaultTimeOutCache = 32000L;
  private long timeOutCache;

  private static final long defaultTimeOutDns = 10000L;
  private long timeOutDns;

  private String sipCertificate;
  private String sipPrivateKey;
  private int tlsHandshakeTimeOut;
  private boolean isAcceptedIssuersEnabled;
  private int tlsOcspResponseTimeout;
  private String trustStoreFilePath;
  private String trustStoreType;
  private String trustStorePassword;
  private boolean tlsCertRevocationSoftfailEnabled;
  private boolean tlsOcspEnabled;
  private boolean nioEnabled;
  private String keyStoreFilePath;
  private String keyStoreType;
  private String keyStorePassword;
  private String clientAuthType;
  private boolean logKeepAlivesEnabled;
  private long keepAlivePeriod;
  private String reliableConnectionKeepAliveTimeout;
  private String minKeepAliveTimeSeconds;

  private int udpEventPoolThreadCount;
  private int tlsEventPoolThreadCount;

  private int connectionCacheConnectionIdleTimeout;
  private long connectionWriteTimeout;

  private List<String> allowedCiphers;
  private boolean isHostPortEnabled;

  @Autowired
  public DhruvaSIPConfigProperties(Environment env) {
    this.env = env;
    init();
  }

  private void init() {
    this.configuredSipProxy = env.getProperty(SIP_PROXY);
    this.HOST_IP_OR_FQDN_VALUE = env.getProperty(HOST_IP_OR_FQDN);
    this.USE_REDIS_AS_CACHE_VALUE = env.getProperty(USE_REDIS_AS_CACHE, Boolean.class, true);
    this.configuredListeningPoints = env.getProperty(SIP_LISTEN_POINTS);
    this.cacheSize =
        (this.cacheSize = env.getProperty("DhruvaDnsCacheMaxSize", Integer.class, defaultCacheSize))
                > 0
            ? cacheSize
            : defaultCacheSize;
    this.timeOutCache =
        (this.timeOutCache =
                    env.getProperty(
                        "DhruvaDnsRetentionTimeMillis", Long.class, defaultTimeOutCache))
                > 0
            ? timeOutCache
            : defaultTimeOutCache;
    this.timeOutDns =
        (this.timeOutDns =
                    env.getProperty("DhruvaDnsTimeoutTimeMillis", Long.class, defaultTimeOutDns))
                > 0
            ? timeOutDns
            : defaultTimeOutDns;
    this.sipCertificate = env.getProperty(SIP_CERTIFICATE);
    this.sipPrivateKey = env.getProperty(SIP_PRIVATE_KEY);
    this.udpEventPoolThreadCount =
        env.getProperty(
            UDP_EVENTLOOP_THREAD_COUNT, Integer.class, DEFAULT_UDP_EVENTLOOP_THREAD_COUNT);
    this.tlsEventPoolThreadCount =
        env.getProperty(
            TLS_EVENTLOOP_THREAD_COUNT, Integer.class, DEFAULT_TLS_EVENTLOOP_THREAD_COUNT);
    this.connectionCacheConnectionIdleTimeout =
        env.getProperty(
            CONNECTION_CACHE_CONNECTION_IDLE_TIMEOUT_SECONDS,
            Integer.class,
            DEFAULT_CONNECTION_CACHE_CONNECTION_IDLE_TIMEOUT_MINUTES);
    this.isHostPortEnabled =
        env.getProperty(HOST_PORT_ENABLED, Boolean.class, DEFAULT_HOST_PORT_ENABLED);
    this.tlsHandshakeTimeOut =
        env.getProperty(
            TLS_HANDSHAKE_TIMEOUT_MILLISECONDS,
            Integer.class,
            DEFAULT_TLS_HANDSHAKE_TIMEOUT_MILLISECONDS);
    this.isAcceptedIssuersEnabled =
        env.getProperty(
            TLS_CA_LIST_IN_SERVER_HELLO_ENABLED,
            Boolean.class,
            DEFAULT_TLS_CA_LIST_IN_SERVER_HELLO_ENABLED);
    this.connectionWriteTimeout =
        env.getProperty(
            CONNECTION_WRITE_TIMEOUT_IN_MILLIS,
            Long.class,
            DEFAULT_CONNECTION_WRITE_TIMEOUT_IN_MILLIS);
    this.tlsOcspResponseTimeout =
        env.getProperty(
            TLS_OCSP_RESPONSE_TIMEOUT_SECONDS,
            Integer.class,
            DEFAULT_TLS_OCSP_RESPONSE_TIMEOUT_SECONDS);
    this.trustStoreFilePath =
        env.getProperty(TLS_TRUST_STORE_FILE_PATH, String.class, DEFAULT_TRUST_STORE_FILE_PATH);
    this.trustStoreType =
        env.getProperty(TLS_TRUST_STORE_TYPE, String.class, DEFAULT_TLS_TRUST_STORE_TYPE);
    this.tlsCertRevocationSoftfailEnabled =
        env.getProperty(
            TLS_CERT_REVOCATION_SOFTFAIL_ENABLED,
            Boolean.class,
            DEFAULT_TLS_CERT_REVOCATION_SOFTFAIL_ENABLED);
    this.tlsOcspEnabled =
        env.getProperty(TLS_CERT_OCSP_ENABLED, Boolean.class, DEFAULT_TLS_CERT_OCSP_ENABLED);
    this.nioEnabled = env.getProperty(NIO_ENABLED, Boolean.class, DEFAULT_NIO_ENABLED);
    this.trustStorePassword =
        env.getProperty(TLS_TRUST_STORE_PASSWORD, String.class, DEFAULT_TLS_TRUST_STORE_PASSWORD);
    this.keyStoreFilePath = env.getProperty(TLS_KEY_STORE_FILE_PATH);
    this.keyStoreType =
        env.getProperty(TLS_KEY_STORE_TYPE, String.class, DEFAULT_TLS_KEY_STORE_TYPE);
    this.keyStorePassword = env.getProperty(TLS_KEY_STORE_PASSWORD, String.class);
    this.clientAuthType = env.getProperty(CLIENT_AUTH_TYPE, String.class, DEFAULT_CLIENT_AUTH_TYPE);
    this.logKeepAlivesEnabled =
        env.getProperty(LOG_KEEP_ALIVES_ENABLED, Boolean.class, DEFAULT_LOG_KEEP_ALIVES_ENABLED);
    this.keepAlivePeriod =
        env.getProperty(KEEP_ALIVE_PERIOD, Long.class, DEFAULT_KEEP_ALIVE_PERIOD);
    this.reliableConnectionKeepAliveTimeout =
        env.getProperty(
            RELIABLE_CONNECTION_KEEP_ALIVE_TIMEOUT,
            String.class,
            DEFAULT_RELIABLE_CONNECTION_KEEP_ALIVE_TIMEOUT);
    this.minKeepAliveTimeSeconds =
        env.getProperty(
            MIN_KEEP_ALIVE_TIME_SECONDS, String.class, DEFAULT_MIN_KEEP_ALIVE_TIME_SECONDS);
  }

  public String getAllowedMethods() {
    // TODO:  can this be a configuration ?
    return getDefaultAllowedMethods();
  }

  private String getDefaultAllowedMethods() {

    String allow =
        Request.INVITE
            .concat(",")
            .concat(Request.ACK)
            .concat(",")
            .concat(Request.BYE)
            .concat(",")
            .concat(Request.CANCEL)
            .concat(",")
            .concat(Request.OPTIONS)
            .concat(",")
            .concat(Request.INFO)
            .concat(",")
            .concat(Request.SUBSCRIBE)
            .concat(",")
            .concat(Request.REFER);
    if (getSIPProxy().isProcessRegisterRequest()) {
      allow.concat(",").concat(Request.REGISTER);
    }
    return allow;
  }

  @Bean(name = "listenPoints")
  public List<SIPListenPoint> getListeningPoints() {
    if (listenPoints != null) return listenPoints;

    if (configuredListeningPoints != null) {
      try {
        listenPoints =
            Arrays.asList(
                JsonUtilFactory.getInstance(JsonUtilFactory.JsonUtilType.LOCAL)
                    .toObject(configuredListeningPoints, SIPListenPoint[].class));
      } catch (Exception e) {
        // TODO should we generate an Alarm
        logger.error(
            "Error converting JSON ListenPoint configuration provided in the environment , default listen point will be chosen ",
            e);
        listenPoints = getDefaultListenPoints();
      }

    } else {
      listenPoints = getDefaultListenPoints();
    }

    logger.info("Listen points from the {} configuration {}", SIP_LISTEN_POINTS, listenPoints);

    return listenPoints;
  }

  private List<SIPListenPoint> getDefaultListenPoints() {

    List<SIPListenPoint> listenPoints = new ArrayList<>();

    SIPListenPoint udpListenPoint = new SIPListenPoint.SIPListenPointBuilder().build();

    listenPoints.add(udpListenPoint);

    return listenPoints;
  }

  public SIPProxy getSIPProxy() {

    if (this.proxy != null) return this.proxy;

    if (configuredSipProxy != null) {
      try {
        this.proxy =
            JsonUtilFactory.getInstance(JsonUtilFactory.JsonUtilType.LOCAL)
                .toObject(configuredSipProxy, SIPProxy.class);
      } catch (Exception e) {
        logger.error(
            "Error converting JSON sipProxy configuration provided in the environment , default sipProxy will be choosen ",
            e);
        this.proxy = getDefaultSIPProxy();
      }

    } else {
      this.proxy = getDefaultSIPProxy();
    }

    logger.info("sip proxy config from the {} configuration {}", SIP_PROXY, proxy);

    return this.proxy;
  }

  private SIPProxy getDefaultSIPProxy() {
    return new SIPProxy.SIPProxyBuilder().build();
  }

  public boolean useRedisAsCache() {
    return this.USE_REDIS_AS_CACHE_VALUE;
  }

  public int getDhruvaDnsCacheMaxSize() {
    return this.cacheSize;
  }

  public long dnsCacheRetentionTimeMillis() {
    return this.timeOutCache;
  }

  public long dnsLookupTimeoutMillis() {
    long defaultTime = 10000L;
    long retTime = env.getProperty("DhruvaDnsTimeoutTimeMillis", Long.class, defaultTime);
    return retTime > 0L ? retTime : defaultTime;
  }

  public String getSipCertificate() {
    return this.sipCertificate;
  }

  public String getSipPrivateKey() {
    return this.sipPrivateKey;
  }

  public int getUdpEventPoolThreadCount() {
    return this.udpEventPoolThreadCount;
  }

  public int getTlsEventPoolThreadCount() {
    return this.tlsEventPoolThreadCount;
  }

  public int getConnectionCacheConnectionIdleTimeout() {
    return this.connectionCacheConnectionIdleTimeout;
  }

  public List<String> getCiphers() {
    if (this.allowedCiphers != null) return this.allowedCiphers;

    String ciphers = env.getProperty(TLS_CIPHERS, String.class);
    if (ciphers == null || ciphers.isEmpty()) {
      return (this.allowedCiphers = CipherSuites.allowedCiphers);
    } else {
      return this.allowedCiphers =
          Collections.unmodifiableList(
              CipherSuites.getAllowedCiphers(Arrays.asList(ciphers.split(","))));
    }
  }

  public String getHostInfo() {
    return HOST_IP_OR_FQDN_VALUE;
  }

  public boolean isHostPortEnabled() {
    return this.isHostPortEnabled;
  }

  public String[] getTlsProtocols() {
    return tlsProtocols;
  }

  public int getTlsHandshakeTimeoutMilliSeconds() {
    return this.tlsHandshakeTimeOut;
  }

  public boolean getIsAcceptedIssuersEnabled() {
    return this.isAcceptedIssuersEnabled;
  }

  public long getConnectionWriteTimeoutInMilliSeconds() {
    return this.connectionWriteTimeout;
  }

  public int getOcspResponseTimeoutSeconds() {
    return this.tlsOcspResponseTimeout;
  }

  public String getTrustStoreFilePath() {
    return this.trustStoreFilePath;
  }

  public String getTrustStoreType() {
    return this.trustStoreType;
  }

  public String getTrustStorePassword() {
    return this.trustStorePassword;
  }

  public String getKeyStoreFilePath() {
    return this.keyStoreFilePath;
  }

  public String getKeyStoreType() {
    return this.keyStoreType;
  }

  public String getKeyStorePassword() {
    return this.keyStorePassword;
  }

  public Boolean isTlsCertRevocationSoftFailEnabled() {
    return this.tlsCertRevocationSoftfailEnabled;
  }

  public boolean isTlsOcspEnabled() {
    return this.tlsOcspEnabled;
  }

  public String getClientAuthType() {
    return this.clientAuthType;
  }

  public boolean isNioEnabled() {
    return this.nioEnabled;
  }

  public boolean isLogKeepAlivesEnabled() {
    return this.logKeepAlivesEnabled;
  }

  public long getKeepAlivePeriod() {
    return this.keepAlivePeriod;
  }

  public String getReliableConnectionKeepAliveTimeout() {
    return this.reliableConnectionKeepAliveTimeout;
  }

  public String getMinKeepAliveTimeSeconds() {
    return this.minKeepAliveTimeSeconds;
  }
}
