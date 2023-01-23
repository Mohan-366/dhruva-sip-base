package com.cisco.dsb.common.sip.tls;

import com.cisco.dsb.common.config.CertConfigurationProperties;
import com.cisco.dsb.common.config.TruststoreConfigurationProperties;
import com.cisco.dsb.common.config.sip.CommonConfigurationProperties;
import com.cisco.wx2.certs.client.CertsClientFactory;
import com.cisco.wx2.certs.client.CertsX509TrustManager;
import com.cisco.wx2.certs.common.util.CRLRevocationCache;
import com.cisco.wx2.certs.common.util.OCSPRevocationCache;
import com.cisco.wx2.certs.common.util.RevocationManager;
import com.cisco.wx2.client.HttpUtil;
import com.cisco.wx2.client.commonidentity.BearerAuthorizationProvider;
import com.cisco.wx2.client.commonidentity.CommonIdentityClientFactory;
import com.cisco.wx2.client.commonidentity.CommonIdentityScimClient;
import com.cisco.wx2.client.commonidentity.CommonIdentityScimClientFactory;
import com.cisco.wx2.client.discovery.DiscoveryService;
import com.cisco.wx2.server.auth.ng.Scope;
import com.cisco.wx2.server.organization.CommonIdentityOrganizationCollectionCache;
import com.cisco.wx2.server.organization.CommonIdentityOrganizationLoader;
import com.cisco.wx2.server.organization.OrganizationCollectionCache;
import com.cisco.wx2.server.organization.OrganizationLoader;
import com.cisco.wx2.util.OrgId;
import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import java.io.*;
import java.net.URI;
import java.security.*;
import java.security.cert.CertPathBuilder;
import java.security.cert.PKIXBuilderParameters;
import java.security.cert.PKIXRevocationChecker;
import java.security.cert.X509CertSelector;
import java.util.EnumSet;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import javax.net.ssl.*;
import lombok.CustomLog;
import lombok.NonNull;
import org.apache.http.impl.conn.PoolingHttpClientConnectionManager;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

@CustomLog
@Component
public class DsbTrustManagerFactory {

  private CommonConfigurationProperties commonConfigurationProperties;
  private CertServiceTrustManagerProperties certServiceTrustManagerProperties;
  private static final String javaHome = System.getProperty("java.home");

  @Autowired
  public void setCommonConfigurationProperties(
      CommonConfigurationProperties commonConfigurationProperties) {
    this.commonConfigurationProperties = commonConfigurationProperties;
  }

  @Autowired
  public void setCertTrustManagerProperties(
      CertServiceTrustManagerProperties certServiceTrustManagerProperties) {
    this.certServiceTrustManagerProperties = certServiceTrustManagerProperties;
  }

  public DsbTrustManager getDsbTrustManager() throws Exception {
    return getDsbTrustManager(new CertConfigurationProperties());
  }

  public DsbTrustManager getDsbTrustManager(
      @NonNull CertConfigurationProperties certConfigurationProperties) throws Exception {
    DsbTrustManager trustManager;
    TruststoreConfigurationProperties truststoreConfigurationProperties =
        commonConfigurationProperties.getTruststoreConfig();
    if (certConfigurationProperties.isTrustAllCerts()) {
      logger.warn(
          "Using PermissiveInstance for TrustManager. No certificate validation will be performed.");
      trustManager = new DsbTrustManager(null);
    }
    // TODO: Move certService config under CertConfigurationProperties
    else if (truststoreConfigurationProperties.isEnableCertService()) {

      logger.info(
          "Certs Service URL = {}", certServiceTrustManagerProperties.getCertsApiServiceUrl());

      ExecutorService executorService =
          Executors.newFixedThreadPool(
              certServiceTrustManagerProperties.getRevocationManagerThreadPoolSize());

      RevocationManager revocationManager =
          new RevocationManager(
              OCSPRevocationCache.memoryBackedOcspCache(
                  certServiceTrustManagerProperties.getRevocationCacheExpirationHours()),
              CRLRevocationCache.memoryBackedCRLCache(
                  certServiceTrustManagerProperties.getRevocationCacheExpirationHours(),
                  certServiceTrustManagerProperties.getHttpConnectTimeout(),
                  certServiceTrustManagerProperties.getHttpReadTimeout()),
              executorService);
      revocationManager.setOcspEnabled(certConfigurationProperties.isOcsp());
      // There is no option to create CertsX509TrustManager(csb) without RevocationManager
      boolean hardfail =
          certConfigurationProperties.isRevocationCheck()
              && !certConfigurationProperties.isRevocationSoftfail();
      revocationManager.setHardFail(hardfail);
      trustManager =
          new DsbTrustManager(
              new CertsX509TrustManager(
                  certsClientFactory(),
                  orgsCache(),
                  revocationManager,
                  certServiceTrustManagerProperties.getRevocationTimeoutMilliseconds(),
                  TimeUnit.MILLISECONDS,
                  certServiceTrustManagerProperties.getOrgCertCacheSize(),
                  false));
    } else {
      logger.info("System trust store will be used as source of trust.");
      trustManager =
          getSystemTrustManager(certConfigurationProperties, truststoreConfigurationProperties);
    }

    // setting to default for now which means this feature is disabled and we are not rejecting any
    // source.
    trustManager.setRequireTrustedSipSources(
        certConfigurationProperties.isRequiredTrustedSipSources());
    trustManager.setTrustedSipSources(certConfigurationProperties.getTrustedSipSources());
    trustManager.setAcceptedIssuersEnabled(certConfigurationProperties.isAcceptedIssuersEnabled());
    return trustManager;
  }

  private DsbTrustManager getSystemTrustManager(
      CertConfigurationProperties certConfigurationProperties,
      TruststoreConfigurationProperties truststoreConfigurationProperties)
      throws Exception {

    KeyStore trustStore = loadTrustStore(truststoreConfigurationProperties);
    PKIXBuilderParameters pkixParams =
        new PKIXBuilderParameters(trustStore, new X509CertSelector());
    pkixParams.setRevocationEnabled(false);

    if (certConfigurationProperties.isRevocationCheck()) {
      CertPathBuilder cpb = CertPathBuilder.getInstance("PKIX");
      PKIXRevocationChecker rc = (PKIXRevocationChecker) cpb.getRevocationChecker();
      rc.setOptions(getRevocationOptions(certConfigurationProperties));
      pkixParams.addCertPathChecker(rc);
    }
    TrustManagerFactory tmf =
        TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm());
    tmf.init(new CertPathTrustManagerParameters(pkixParams));
    TrustManager[] tms = tmf.getTrustManagers();

    for (TrustManager tm : tms) {
      if (tm instanceof X509TrustManager) {
        logger.info(
            "Initializing trust manager with {} certs as trust anchors",
            ((X509TrustManager) tm).getAcceptedIssuers().length);
        return new DsbTrustManager((X509TrustManager) tm);
      }
    }
    throw new IllegalArgumentException("Unable to find system trust manager");
  }

  /**
   * End Entity i.e most-trusted CA will only be checked for revocation status By default OCSP is
   * disabled, hence only CRL check is done. If OCSP is enabled then first OCSP check is done first
   * and if soft fail is enabled it will fall back to CRL is there were any network issue with OCSP.
   * When soft fail is enabled, any network errors(only) while determining revocation status is
   * ignored.
   */
  private Set<PKIXRevocationChecker.Option> getRevocationOptions(
      CertConfigurationProperties certConfigurationProperties) {
    EnumSet<PKIXRevocationChecker.Option> options =
        EnumSet.noneOf(PKIXRevocationChecker.Option.class);
    if (certConfigurationProperties.isRevocationSoftfail()) {
      options.add(PKIXRevocationChecker.Option.SOFT_FAIL);
    }
    if (!certConfigurationProperties.isOcsp()) {
      options.add(PKIXRevocationChecker.Option.PREFER_CRLS);
      options.add(PKIXRevocationChecker.Option.NO_FALLBACK);
    }
    return options;
  }

  @SuppressFBWarnings(
      value = {"HARD_CODE_PASSWORD", "PATH_TRAVERSAL_IN"},
      justification = "baseline suppression")
  private KeyStore loadTrustStore(
      TruststoreConfigurationProperties truststoreConfigurationProperties) throws Exception {
    File storeFile;
    FileInputStream fis = null;
    final String sep = File.separator;
    KeyStore ks;
    try {
      String trustStoreFile = truststoreConfigurationProperties.getTrustStoreFilePath();
      String trustStorePassword = truststoreConfigurationProperties.getTrustStorePassword();
      if (trustStoreFile != null) {
        storeFile = new File(trustStoreFile);
        fis = getFileInputStream(storeFile);
      } else {
        storeFile = new File(javaHome + sep + "lib" + sep + "security" + sep + "jssecacerts");
        if ((fis = getFileInputStream(storeFile)) == null) {
          storeFile = new File(javaHome + sep + "lib" + sep + "security" + sep + "cacerts");
          fis = getFileInputStream(storeFile);
        }
      }

      ks = KeyStore.getInstance(truststoreConfigurationProperties.getTrustStoreType());

      char[] trustStorePass = null;
      if (trustStorePassword.length() != 0) {
        trustStorePass = trustStorePassword.toCharArray();
      }

      // if trustStoreFile is NONE, fis will be null
      ks.load(fis, trustStorePass);
    } finally {
      if (fis != null) {
        fis.close();
      }
    }

    return ks;
  }

  private FileInputStream getFileInputStream(File file) throws Exception {
    return AccessController.doPrivileged(
        (PrivilegedExceptionAction<FileInputStream>) () -> new FileInputStream(file));
  }

  private OrganizationCollectionCache orgsCache() {
    return CommonIdentityOrganizationCollectionCache.memoryBackedCache(
        orgLoader(),
        certServiceTrustManagerProperties.getOrgCacheExpirationMinutes(),
        TimeUnit.MINUTES,
        true);
  }

  public OrganizationLoader orgLoader() {
    return new CommonIdentityOrganizationLoader(commonIdentityScimClientFactory());
  }

  public CommonIdentityScimClientFactory commonIdentityScimClientFactory() {
    logger.info(
        "Common Identity SCIM URL = {}", certServiceTrustManagerProperties.getScimEndpointUrl());
    return CommonIdentityScimClientFactory.builder(certServiceTrustManagerProperties)
        .connectionManager(scimConnectionManager())
        .baseUrl(certServiceTrustManagerProperties.getScimEndpointUrl())
        .authorizationProvider(scimBearerAuthorizationProvider())
        .maxQuery(certServiceTrustManagerProperties.getMaxCiQuerySize())
        .bulkSize(certServiceTrustManagerProperties.getMaxUsersFromCiMultiget())
        .federationIgnored(true)
        .build();
  }

  public PoolingHttpClientConnectionManager scimConnectionManager() {
    return HttpUtil.newPoolingClientConnectionManager(
        certServiceTrustManagerProperties.disableSslChecks(),
        certServiceTrustManagerProperties.getHttpMaxConnections(),
        certServiceTrustManagerProperties.getHttpMaxConnectionsPerRoute(),
        certServiceTrustManagerProperties.getDnsResolver());
  }

  public CertsClientFactory certsClientFactory() {

    return CertsClientFactory.builder(
            certServiceTrustManagerProperties,
            certServiceTrustManagerProperties.getCertsApiServiceUrl())
        .authorizationProvider(bearerAuthorizationProvider())
        .discoveryService(discoveryService())
        .serviceAuth(true)
        .build();
  }

  public Map<URI, URI> getLocalDiscoveryURIMap() {
    return null;
  }

  public DiscoveryService discoveryService() {
    Map<URI, URI> localDiscoveryURIMap = getLocalDiscoveryURIMap();
    logger.info("The localDiscoveryURIMap is {}", localDiscoveryURIMap);
    return new DiscoveryService(localDiscoveryURIMap);
  }

  public BearerAuthorizationProvider bearerAuthorizationProvider() {

    BearerAuthorizationProvider.Builder builder =
        certServiceTrustManagerProperties.isMachineAccountAuthEnabled()
            ? BearerAuthorizationProvider.builder()
            : BearerAuthorizationProvider.builder(
                certServiceTrustManagerProperties.getAuthorizationConfig("dsb".toLowerCase()));

    return builder
        .commonIdentityClientFactory(commonIdentityClientFactory())
        .orgId(certServiceTrustManagerProperties.getDhruvaOrgId())
        .userId(certServiceTrustManagerProperties.getDhruvaServiceUser())
        .password(certServiceTrustManagerProperties.getDhruvaServicePassword())
        .scope(com.cisco.wx2.server.auth.ng.Scope.of(Scope.Identity.SCIM))
        .clientId(certServiceTrustManagerProperties.getDhruvaClientId())
        .clientSecret(certServiceTrustManagerProperties.getDhruvaClientSecret())
        .build();
  }

  public BearerAuthorizationProvider scimBearerAuthorizationProvider() {
    OrgId commonIdentityOrgId = null;
    String commonIdentityOrgIdStr = certServiceTrustManagerProperties.getOrgName();
    if (null != commonIdentityOrgIdStr && !commonIdentityOrgIdStr.isEmpty()) {
      commonIdentityOrgId = OrgId.fromString(commonIdentityOrgIdStr);
    }
    return BearerAuthorizationProvider.builder(
            certServiceTrustManagerProperties.getCommonIdentityAuthorizationConfig())
        .commonIdentityClientFactory(commonIdentityClientFactory())
        .orgId(commonIdentityOrgId)
        .userId(certServiceTrustManagerProperties.getDhruvaServiceUser())
        .password(certServiceTrustManagerProperties.getDhruvaServicePassword())
        .scope(CommonIdentityScimClient.CI_SCIM_SCOPE)
        .clientId(certServiceTrustManagerProperties.getDhruvaClientId())
        .clientSecret(certServiceTrustManagerProperties.getDhruvaClientSecret())
        .build();
  }

  public CommonIdentityClientFactory commonIdentityClientFactory() {
    logger.info(
        "Common Identity OAuth Service URL = {}",
        certServiceTrustManagerProperties.getOAuthEndpointUrl());
    return CommonIdentityClientFactory.builder(certServiceTrustManagerProperties)
        .baseUrl(certServiceTrustManagerProperties.getOAuthEndpointUrl())
        .federationIgnored(true)
        .build();
  }

  public KeyManager getKeyManager() throws Exception {
    TruststoreConfigurationProperties truststoreConfigurationProperties =
        commonConfigurationProperties.getTruststoreConfig();
    KeyStore keyStore = loadKeyStore(truststoreConfigurationProperties);
    KeyManagerFactory keyManagerFactory;
    keyManagerFactory = KeyManagerFactory.getInstance(KeyManagerFactory.getDefaultAlgorithm());
    keyManagerFactory.init(
        keyStore, truststoreConfigurationProperties.getKeyStorePassword().toCharArray());
    logger.info("Key manager factory initialized. SIP TLS settings OK.");

    KeyManager[] keyManager = keyManagerFactory.getKeyManagers();
    if (keyManager == null || keyManager.length == 0) {
      throw new IllegalStateException("Unable to create SIP TLS key manager");
    }

    if (keyManager.length > 1) {
      logger.warn("More than one possible SIP key. Choosing the first one.");
    }
    return keyManager[0];
  }

  @SuppressFBWarnings(
      value = {"HARD_CODE_PASSWORD", "PATH_TRAVERSAL_IN"},
      justification = "baseline suppression")
  private KeyStore loadKeyStore(TruststoreConfigurationProperties props) throws Exception {
    // Key store loaded from key store file (or base 64 encoded file loaded in environment variable)
    // Production deployment should use key store file (PKCS12 or JKS), base 64 encoded
    final String keyStoreFilename = props.getKeyStoreFilePath();
    final String keyStorePassword = props.getKeyStorePassword();
    final String keyStoreType = props.getKeyStoreType();

    KeyStore keyStore;
    File ksFile;
    FileInputStream fis = null;
    try {
      ksFile = new File(keyStoreFilename);
      fis = getFileInputStream(ksFile);
      keyStore = KeyStore.getInstance(keyStoreType);
      keyStore.load(fis, keyStorePassword.toCharArray());
    } finally {
      if (fis != null) fis.close();
    }

    logger.info(
        "Loaded key store of type {} from file {}. Entries: {}",
        keyStoreType,
        keyStoreFilename,
        keyStore.size());
    return keyStore;
  }
}
