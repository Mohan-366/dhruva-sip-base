package com.cisco.dsb.common.sip.tls;

import com.cisco.dsb.common.config.sip.DhruvaSIPConfigProperties;
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
import lombok.CustomLog;
import org.apache.http.impl.conn.PoolingHttpClientConnectionManager;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

@CustomLog
@Component
public class DsbTrustManagerFactory {

  @Autowired DhruvaSIPConfigProperties dhruvaSIPConfigProperties;
  @Autowired CertTrustManagerProperties certTrustManagerProperties;

  public DsbTrustManager getDsbTrsutManager() throws Exception {
    return getDsbTrsutManager(null);
  }

  public DsbTrustManager getDsbTrsutManager(TLSAuthenticationType tlsAuthenticationType)
      throws Exception {
    DsbTrustManager trustManager;
    TLSAuthenticationType authenticationType;
    if (tlsAuthenticationType == null) {
      authenticationType = dhruvaSIPConfigProperties.getTlsAuthType();
    } else {
      authenticationType = tlsAuthenticationType;
    }
    if (authenticationType == TLSAuthenticationType.NONE) {
      logger.warn(
          "Using PermissiveInstance for TrustManager. No certificate validation will be performed.");
      trustManager = DsbTrustManager.getTrustAllCertsInstance();
    } else if (authenticationType == TLSAuthenticationType.MTLS
        && dhruvaSIPConfigProperties.getEnableCertService()) {

      logger.info("Certs Service URL = {}", certTrustManagerProperties.getCertsApiServiceUrl());

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

      trustManager =
          DsbTrustManager.createInstance(
              certsClientFactory(),
              orgsCache(),
              revocationManager,
              certTrustManagerProperties.getRevocationTimeoutMilliseconds(),
              TimeUnit.MILLISECONDS,
              certTrustManagerProperties.getOrgCertCacheSize());
    } else {
      logger.info("System trust store will be used as source of trust.");
      trustManager = DsbTrustManager.getSystemTrustManager(dhruvaSIPConfigProperties);
    }

    // setting to default for now which means this feature is disabled and we are not rejecting any
    // source.
    trustManager.setRequireTrustedSipSources(
        dhruvaSIPConfigProperties.getRequiredTrustedSipSources());
    trustManager.setTrustedSipSources(dhruvaSIPConfigProperties.getTrustedSipSources());
    return trustManager;
  }

  private OrganizationCollectionCache orgsCache() {
    return CommonIdentityOrganizationCollectionCache.memoryBackedCache(
        orgLoader(),
        certTrustManagerProperties.getOrgCacheExpirationMinutes(),
        TimeUnit.MINUTES,
        true);
  }

  public OrganizationLoader orgLoader() {
    return new CommonIdentityOrganizationLoader(commonIdentityScimClientFactory());
  }

  public CommonIdentityScimClientFactory commonIdentityScimClientFactory() {
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

  public PoolingHttpClientConnectionManager scimConnectionManager() {
    return HttpUtil.newPoolingClientConnectionManager(
        certTrustManagerProperties.disableSslChecks(),
        certTrustManagerProperties.getHttpMaxConnections(),
        certTrustManagerProperties.getHttpMaxConnectionsPerRoute(),
        certTrustManagerProperties.getDnsResolver());
  }

  public CertsClientFactory certsClientFactory() {

    return CertsClientFactory.builder(
            certTrustManagerProperties, certTrustManagerProperties.getCertsApiServiceUrl())
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
        certTrustManagerProperties.isMachineAccountAuthEnabled()
            ? BearerAuthorizationProvider.builder()
            : BearerAuthorizationProvider.builder(
                certTrustManagerProperties.getAuthorizationConfig("dsb".toLowerCase()));

    return builder
        .commonIdentityClientFactory(commonIdentityClientFactory())
        .orgId(certTrustManagerProperties.getDhruvaOrgId())
        .userId(certTrustManagerProperties.getDhruvaServiceUser())
        .password(certTrustManagerProperties.getDhruvaServicePassword())
        .scope(com.cisco.wx2.server.auth.ng.Scope.of(Scope.Identity.SCIM))
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
