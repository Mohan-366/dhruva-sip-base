package com.cisco.dsb.common.config.sip;

import com.cisco.dsb.common.sip.bean.SIPListenPoint;
import com.cisco.dsb.common.sip.bean.SIPProxy;
import com.cisco.dsb.common.transport.TLSAuthenticationType;
import com.cisco.dsb.common.transport.Transport;
import com.cisco.dsb.common.util.JsonUtilFactory;
import com.cisco.wx2.dto.BuildInfo;
import java.security.KeyStore;
import java.util.*;
import java.util.concurrent.TimeUnit;
import javax.sip.message.Request;
import lombok.CustomLog;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Bean;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.Environment;

@Configuration
@ConfigurationProperties(prefix = "bean")
@Qualifier("dhruvaSIPConfigProperties")
@CustomLog
public class DhruvaSIPConfigProperties {

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

  public static final long DEFAULT_TIMER_C_DURATION_MILLISEC = 45000;

  public static final boolean DEFAULT_ATTACH_EXTERNAL_IP = false;

  private static final String USE_REDIS_AS_CACHE = "useRedis";

  public static final TLSAuthenticationType DEFAULT_TRANSPORT_AUTH = TLSAuthenticationType.MTLS;

  public static final boolean DEFAULT_ENABLE_CERT_SERVICE = false;

  private static final String SIP_CERTIFICATE = "sipCertificate";

  private static final String SIP_PRIVATE_KEY = "sipPrivateKey";

  private static final String UDP_EVENTLOOP_THREAD_COUNT = "dhruva.network.udpEventloopThreadCount";

  private static final Integer DEFAULT_UDP_EVENTLOOP_THREAD_COUNT = 1;

  private static final String TLS_EVENTLOOP_THREAD_COUNT = "dhruva.network.tlsEventloopThreadCount";

  private static final Integer DEFAULT_TLS_EVENTLOOP_THREAD_COUNT = 20;

  private static final String CONNECTION_CACHE_CONNECTION_IDLE_TIMEOUT_SECONDS =
      "dhruva.network.connectionCache.connectionIdleTimeout";

  private static final Integer DEFAULT_CONNECTION_CACHE_CONNECTION_IDLE_TIMEOUT_MINUTES = 14400;

  private static final String TLS_CIPHERS = "dhruva.sipTlsCipherSuites";

  private static final String HOST_PORT_ENABLED = "hostPortEnabled";

  private static final Boolean DEFAULT_HOST_PORT_ENABLED = false;

  private static final String HOST_IP_OR_FQDN = "hostIpOrFqdn";

  private static final String TLS_HANDSHAKE_TIMEOUT_MILLISECONDS =
      "dhruva.tlsHandShakeTimeOutMilliSeconds";
  private static final Integer DEFAULT_TLS_HANDSHAKE_TIMEOUT_MILLISECONDS = 5000;
  private static final String TLS_CA_LIST_IN_SERVER_HELLO_ENABLED =
      "dhruva.tlsCaListInServerHelloEnabled";
  private static final Boolean DEFAULT_TLS_CA_LIST_IN_SERVER_HELLO_ENABLED = false;
  private static final String CONNECTION_WRITE_TIMEOUT_IN_MILLIS =
      "dhruva.connectionWriteTimeoutInMllis";
  private static final long DEFAULT_CONNECTION_WRITE_TIMEOUT_IN_MILLIS = 60000;
  private static final String TLS_OCSP_RESPONSE_TIMEOUT_SECONDS =
      "dhruva.tlsOcspResponseTimeoutIn" + "Seconds";
  private static final int DEFAULT_TLS_OCSP_RESPONSE_TIMEOUT_SECONDS = 5;
  private static final String TLS_TRUST_STORE_FILE_PATH = "dhruva.tlsTrustStoreFilePath";
  private static final String TLS_TRUST_STORE_TYPE = "dhruva.tlsTrustStoreType";
  private static final String DEFAULT_TLS_TRUST_STORE_TYPE = KeyStore.getDefaultType();
  private static final String TLS_TRUST_STORE_PASSWORD = "dhruva.tlsTrustStorePassword";
  private static final String TLS_KEY_STORE_FILE_PATH = "dhruva.tlsKeyStoreFilePath";
  private static final String TLS_KEY_STORE_TYPE = "dhruva.tlsKeyStoreType";
  private static final String DEFAULT_TLS_KEY_STORE_TYPE = KeyStore.getDefaultType();
  private static final String TLS_KEY_STORE_PASSWORD = "dhruva.tlsKeyStorePassword";
  private static final String TLS_CERT_REVOCATION_SOFTFAIL_ENABLED =
      "dhruva.tlsCertRevocationEnable" + "SoftFail";
  private static final Boolean DEFAULT_TLS_CERT_REVOCATION_SOFTFAIL_ENABLED = Boolean.TRUE;
  private static final String TLS_CERT_OCSP_ENABLED = "dhruva.tlsCertEnableOcsp";
  private static final Boolean DEFAULT_TLS_CERT_OCSP_ENABLED = true;
  private static final String CLIENT_AUTH_TYPE = "dhruva.clientAuthType";
  private static final String DEFAULT_CLIENT_AUTH_TYPE = "Disabled";

  private static final String NIO_ENABLED = "dhruva.nioEnabled";
  private static final Boolean DEFAULT_NIO_ENABLED = false;
  public static int DEFAULT_PORT = 5060;

  private static final String KEEP_ALIVE_PERIOD = "keepAlivePeriod";
  private static final Long DEFAULT_KEEP_ALIVE_PERIOD =
      TimeUnit.MILLISECONDS.convert(20, TimeUnit.SECONDS);

  @Autowired private Environment env;

  private static BuildInfo buildInfo;

  public static final String DEFAULT_DHRUVA_USER_AGENT = "WX2_Dhruva";

  private String[] tlsProtocols = new String[] {"TLSv1.2"};

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

    String configuredListeningPoints = env.getProperty(SIP_LISTEN_POINTS);

    List<SIPListenPoint> listenPoints;

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

    String configuredSipProxy = env.getProperty(SIP_PROXY);

    SIPProxy proxy;

    if (configuredSipProxy != null) {
      try {
        proxy =
            JsonUtilFactory.getInstance(JsonUtilFactory.JsonUtilType.LOCAL)
                .toObject(configuredSipProxy, SIPProxy.class);
      } catch (Exception e) {
        logger.error(
            "Error converting JSON sipProxy configuration provided in the environment , default sipProxy will be choosen ",
            e);
        proxy = getDefaultSIPProxy();
      }

    } else {
      proxy = getDefaultSIPProxy();
    }

    logger.info("sip proxy config from the {} configuration {}", SIP_PROXY, proxy);

    return proxy;
  }

  private SIPProxy getDefaultSIPProxy() {
    return new SIPProxy.SIPProxyBuilder().build();
  }

  public boolean useRedisAsCache() {
    return env.getProperty(USE_REDIS_AS_CACHE, Boolean.class, true);
  }

  public int getDhruvaDnsCacheMaxSize() {
    int defaultCacheSize = 1_000;
    int cacheSize = env.getProperty("DhruvaDnsCacheMaxSize", Integer.class, defaultCacheSize);
    return cacheSize > 0 ? cacheSize : defaultCacheSize;
  }

  public long dnsCacheRetentionTimeMillis() {
    long defaultTime = 32000L;
    long retTime = env.getProperty("DhruvaDnsRetentionTimeMillis", Long.class, defaultTime);
    return retTime > 0L ? retTime : defaultTime;
  }

  public long dnsLookupTimeoutMillis() {
    long defaultTime = 10000L;
    long retTime = env.getProperty("DhruvaDnsTimeoutTimeMillis", Long.class, defaultTime);
    return retTime > 0L ? retTime : defaultTime;
  }

  public String getSipCertificate() {
    return env.getProperty(SIP_CERTIFICATE);
  }

  public String getSipPrivateKey() {
    return env.getProperty(SIP_PRIVATE_KEY);
  }

  public int getUdpEventPoolThreadCount() {
    return env.getProperty(
        UDP_EVENTLOOP_THREAD_COUNT, Integer.class, DEFAULT_UDP_EVENTLOOP_THREAD_COUNT);
  }

  public int getTlsEventPoolThreadCount() {
    return env.getProperty(
        TLS_EVENTLOOP_THREAD_COUNT, Integer.class, DEFAULT_TLS_EVENTLOOP_THREAD_COUNT);
  }

  public int getConnectionCacheConnectionIdleTimeout() {
    return env.getProperty(
        CONNECTION_CACHE_CONNECTION_IDLE_TIMEOUT_SECONDS,
        Integer.class,
        DEFAULT_CONNECTION_CACHE_CONNECTION_IDLE_TIMEOUT_MINUTES);
  }

  public List<String> getCiphers() {

    String ciphers = env.getProperty(TLS_CIPHERS, String.class);
    if (ciphers == null || ciphers.isEmpty()) {
      return CipherSuites.allowedCiphers;
    } else {
      return Collections.unmodifiableList(
          CipherSuites.getAllowedCiphers(Arrays.asList(ciphers.split(","))));
    }
  }

  public String getHostInfo() {
    return env.getProperty(HOST_IP_OR_FQDN);
  }

  public boolean isHostPortEnabled() {
    return env.getProperty(HOST_PORT_ENABLED, Boolean.class, DEFAULT_HOST_PORT_ENABLED);
  }

  public String[] getTlsProtocols() {
    return tlsProtocols;
  }

  public int getTlsHandshakeTimeoutMilliSeconds() {
    return env.getProperty(
        TLS_HANDSHAKE_TIMEOUT_MILLISECONDS,
        Integer.class,
        DEFAULT_TLS_HANDSHAKE_TIMEOUT_MILLISECONDS);
  }

  public boolean getIsAcceptedIssuersEnabled() {
    return env.getProperty(
        TLS_CA_LIST_IN_SERVER_HELLO_ENABLED,
        Boolean.class,
        DEFAULT_TLS_CA_LIST_IN_SERVER_HELLO_ENABLED);
  }

  public long getConnectionWriteTimeoutInMilliSeconds() {
    return env.getProperty(
        CONNECTION_WRITE_TIMEOUT_IN_MILLIS, Long.class, DEFAULT_CONNECTION_WRITE_TIMEOUT_IN_MILLIS);
  }

  public int getOcspResponseTimeoutSeconds() {
    return env.getProperty(
        TLS_OCSP_RESPONSE_TIMEOUT_SECONDS,
        Integer.class,
        DEFAULT_TLS_OCSP_RESPONSE_TIMEOUT_SECONDS);
  }

  public String getTrustStoreFilePath() {
    return env.getProperty(TLS_TRUST_STORE_FILE_PATH, String.class);
  }

  public String getTrustStoreType() {
    return env.getProperty(TLS_TRUST_STORE_TYPE, String.class, DEFAULT_TLS_TRUST_STORE_TYPE);
  }

  public String getTrustStorePassword() {
    return env.getProperty(TLS_TRUST_STORE_PASSWORD, String.class);
  }

  public String getKeyStoreFilePath() {
    return env.getProperty(TLS_KEY_STORE_FILE_PATH);
  }

  public String getKeyStoreType() {
    return env.getProperty(TLS_KEY_STORE_TYPE, String.class, DEFAULT_TLS_KEY_STORE_TYPE);
  }

  public String getKeyStorePassword() {
    return env.getProperty(TLS_KEY_STORE_PASSWORD, String.class);
  }

  public Boolean isTlsCertRevocationSoftFailEnabled() {
    return env.getProperty(
        TLS_CERT_REVOCATION_SOFTFAIL_ENABLED,
        Boolean.class,
        DEFAULT_TLS_CERT_REVOCATION_SOFTFAIL_ENABLED);
  }

  public boolean isTlsOcspEnabled() {
    return env.getProperty(TLS_CERT_OCSP_ENABLED, Boolean.class, DEFAULT_TLS_CERT_OCSP_ENABLED);
  }

  public String getClientAuthType() {
    return env.getProperty(CLIENT_AUTH_TYPE, String.class, DEFAULT_CLIENT_AUTH_TYPE);
  }

  public boolean isNioEnabled() {
    return env.getProperty(NIO_ENABLED, Boolean.class, DEFAULT_NIO_ENABLED);
  }

  public boolean isLogKeepAlivesEnabled() {
    return env.getProperty("logKeepAlivesEnabled", Boolean.class, true);
  }

  public long getKeepAlivePeriod() {
    return env.getProperty(KEEP_ALIVE_PERIOD, Long.class, DEFAULT_KEEP_ALIVE_PERIOD);
  }
}
