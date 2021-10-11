package com.cisco.dsb.common.sip.tls;

import com.cisco.dsb.common.util.log.DhruvaLoggerFactory;
import com.cisco.dsb.common.util.log.Logger;
import com.cisco.wx2.certs.client.CertsClientFactory;
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
import java.net.URI;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import javax.net.ssl.X509TrustManager;
import org.apache.http.impl.conn.PoolingHttpClientConnectionManager;

public class CertTrustManager {
  private static final Logger logger = DhruvaLoggerFactory.getLogger(CertTrustManager.class);
  private static CertTrustManagerProperties certTrustManagerProperties;

  public static final String SERVICE_NAME = "Cloudproxy";

  private static X509TrustManager trustManager;

  private CertTrustManager() {
    trustManager =
        CertTrustManagerFactory.getCertsX509TrustManager(
            certsClientFactory(),
            orgsCache(),
            revocationManager(),
            certTrustManagerProperties.getRevocationTimeoutMilliseconds(),
            TimeUnit.MILLISECONDS,
            certTrustManagerProperties.getOrgCertCacheSize(),
            false);
  }

  private RevocationManager revocationManager() {
    ExecutorService executorService =
        Executors.newFixedThreadPool(
            certTrustManagerProperties.getRevocationManagerThreadPoolSize());
    RevocationManager revocationManager =
        new RevocationManager(
            OCSPRevocationCache.memoryBackedOcspCache(
                certTrustManagerProperties.getRevocationCacheExpirationHours()),
            CRLRevocationCache.memoryBackedCRLCache(
                certTrustManagerProperties.getRevocationCacheExpirationHours(),
                certTrustManagerProperties.getHttpConnectTimeout(),
                certTrustManagerProperties.getHttpReadTimeout()),
            executorService);
    revocationManager.setOcspEnabled(certTrustManagerProperties.getOcspEnabled());
    return revocationManager;
  }

  private OrganizationCollectionCache orgsCache() {
    return CommonIdentityOrganizationCollectionCache.memoryBackedCache(
        orgLoader(),
        certTrustManagerProperties.getOrgCacheExpirationMinutes(),
        TimeUnit.MINUTES,
        true);
  }

  private OrganizationLoader orgLoader() {
    return new CommonIdentityOrganizationLoader(commonIdentityScimClientFactory());
  }

  private CommonIdentityScimClientFactory commonIdentityScimClientFactory() {
    logger.info("Common Identity SCIM URL = {}", certTrustManagerProperties.getScimEndpointUrl());
    return CommonIdentityScimClientFactory.builder(certTrustManagerProperties)
        .connectionManager(scimConnectionManager())
        .baseUrl(certTrustManagerProperties.getScimEndpointUrl())
        .authorizationProvider(scimBearerAuthorizationProvider())
        .maxQuery(certTrustManagerProperties.getMaxCiQuerySize())
        .bulkSize(certTrustManagerProperties.getMaxUsersFromCiMultiget())
        .federationIgnored(true)
        .build();
  }

  private PoolingHttpClientConnectionManager scimConnectionManager() {
    return HttpUtil.newPoolingClientConnectionManager(
        certTrustManagerProperties.disableSslChecks(),
        certTrustManagerProperties.getHttpMaxConnections(),
        certTrustManagerProperties.getHttpMaxConnectionsPerRoute(),
        certTrustManagerProperties.getDnsResolver());
  }

  private CertsClientFactory certsClientFactory() {

    return CertsClientFactory.builder(
            certTrustManagerProperties, certTrustManagerProperties.getCertsApiServiceUrl())
        .authorizationProvider(bearerAuthorizationProvider())
        .discoveryService(discoveryService())
        .serviceAuth(true)
        .build();
  }

  private Map<URI, URI> getLocalDiscoveryURIMap() {
    return null;
  }

  public DiscoveryService discoveryService() {
    Map<URI, URI> localDiscoveryURIMap = getLocalDiscoveryURIMap();
    logger.info("The localDiscoveryURIMap is {}", localDiscoveryURIMap);
    return new DiscoveryService(localDiscoveryURIMap);
  }

  public BearerAuthorizationProvider bearerAuthorizationProvider() {

    BearerAuthorizationProvider.Builder builder =
        certTrustManagerProperties.isMachineAccountAuthEnabled()
            ? BearerAuthorizationProvider.builder()
            : BearerAuthorizationProvider.builder(
                certTrustManagerProperties.getAuthorizationConfig(SERVICE_NAME.toLowerCase()));

    return builder
        .commonIdentityClientFactory(commonIdentityClientFactory())
        .orgId(certTrustManagerProperties.getDhruvaOrgId())
        .userId(certTrustManagerProperties.getDhruvaServiceUser())
        .password(certTrustManagerProperties.getDhruvaServicePassword())
        .scope(Scope.of(Scope.Identity.SCIM))
        .clientId(certTrustManagerProperties.getDhruvaClientId())
        .clientSecret(certTrustManagerProperties.getDhruvaClientSecret())
        .build();
  }

  public BearerAuthorizationProvider scimBearerAuthorizationProvider() {
    OrgId commonIdentityOrgId = null;
    String commonIdentityOrgIdStr = certTrustManagerProperties.getOrgName();
    if (null != commonIdentityOrgIdStr && !commonIdentityOrgIdStr.isEmpty()) {
      commonIdentityOrgId = OrgId.fromString(commonIdentityOrgIdStr);
    }
    return BearerAuthorizationProvider.builder(
            certTrustManagerProperties.getCommonIdentityAuthorizationConfig())
        .commonIdentityClientFactory(commonIdentityClientFactory())
        .orgId(commonIdentityOrgId)
        .userId(certTrustManagerProperties.getDhruvaServiceUser())
        .password(certTrustManagerProperties.getDhruvaServicePassword())
        .scope(CommonIdentityScimClient.CI_SCIM_SCOPE)
        .clientId(certTrustManagerProperties.getDhruvaClientId())
        .clientSecret(certTrustManagerProperties.getDhruvaClientSecret())
        .build();
  }

  public CommonIdentityClientFactory commonIdentityClientFactory() {
    logger.info(
        "Common Identity OAuth Service URL = {}", certTrustManagerProperties.getOAuthEndpointUrl());
    return CommonIdentityClientFactory.builder(certTrustManagerProperties)
        .baseUrl(certTrustManagerProperties.getOAuthEndpointUrl())
        .federationIgnored(true)
        .build();
  }
}
