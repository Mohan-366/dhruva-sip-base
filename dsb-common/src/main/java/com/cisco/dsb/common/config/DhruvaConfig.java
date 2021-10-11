package com.cisco.dsb.common.config;

import com.cisco.dsb.common.config.sip.DhruvaSIPConfigProperties;
import com.cisco.dsb.common.dns.DnsInjectionService;
import com.cisco.dsb.common.dns.DnsLookup;
import com.cisco.dsb.common.dns.DnsMetricsReporter;
import com.cisco.dsb.common.dns.DnsResolvers;
import com.cisco.dsb.common.dns.metrics.DnsReporter;
import com.cisco.dsb.common.executor.DhruvaExecutorService;
import com.cisco.dsb.common.executor.ExecutorType;
import com.cisco.dsb.common.metric.InfluxClient;
import com.cisco.dsb.common.metric.MetricClient;
import com.cisco.dsb.common.service.SipServerLocatorService;
import com.cisco.dsb.common.sip.tls.CertTrustManagerProperties;
import com.cisco.dsb.common.sip.tls.DsbNetworkLayer;
import com.cisco.dsb.common.sip.tls.DsbTrustManager;
import com.cisco.dsb.common.sip.tls.TLSAuthenticationType;
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
import com.cisco.wx2.dto.IdentityMachineAccount;
import com.cisco.wx2.redis.RedisDataSource;
import com.cisco.wx2.redis.RedisDataSourceManager;
import com.cisco.wx2.server.auth.ng.Scope;
import com.cisco.wx2.server.config.ConfigProperties;
import com.cisco.wx2.server.config.Wx2Properties;
import com.cisco.wx2.server.organization.CommonIdentityOrganizationCollectionCache;
import com.cisco.wx2.server.organization.CommonIdentityOrganizationLoader;
import com.cisco.wx2.server.organization.OrganizationCollectionCache;
import com.cisco.wx2.server.organization.OrganizationLoader;
import com.cisco.wx2.util.OrgId;
import com.cisco.wx2.util.stripedexecutor.StripedExecutorService;
import com.ciscospark.server.Wx2ConfigAdapter;
import java.net.URI;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.TimeUnit;
import javax.annotation.PreDestroy;
import javax.net.ssl.KeyManager;
import lombok.CustomLog;
import org.apache.http.impl.conn.PoolingHttpClientConnectionManager;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.context.annotation.*;
import org.springframework.core.env.Environment;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.annotation.EnableScheduling;

@Configuration
@ConditionalOnWebApplication
@EnableAsync
@EnableScheduling
@DependsOn("dhruvaSIPConfigProperties")
@CustomLog
public class DhruvaConfig extends Wx2ConfigAdapter {

  @Autowired DhruvaSIPConfigProperties dhruvaSIPConfigProperties;
  @Autowired CertTrustManagerProperties certTrustManagerProperties;

  @Autowired private Environment env;

  private static final long DEFAULT_CACHE_TIMEOUT = 10;

  @Override
  public String getServiceName() {
    return "Dhruva";
  }

  @Override
  public String getMetricsNamespace() {
    return "dhruva";
  }

  @Bean
  public MetricClient getMetricClient() {
    return new InfluxClient();
  }

  private IdentityMachineAccount machineAccount = null;

  @PreDestroy
  public void destroy() {
    try {
      if (machineAccount != null) {
        commonIdentityScimClientFactory()
            .newClient()
            .deleteMachineAccount(machineAccount.getOrgId(), machineAccount.getId());
      }
    } catch (RuntimeException e) {
      log.warn("Unable to clean up machine account", e);
    }
  }

  @Bean
  @DependsOn("dhruvaExecutorService")
  public StripedExecutorService stripedExecutor() {
    dhruvaExecutorService().startStripedExecutorService(ExecutorType.PROXY_PROCESSOR);
    return (StripedExecutorService)
        dhruvaExecutorService().getExecutorThreadPool(ExecutorType.PROXY_PROCESSOR);
  }

  @Bean
  public DhruvaExecutorService dhruvaExecutorService() {
    if (wx2Properties().isMonitoredExecutorInfluxMetricsEnabled()) {
      return new DhruvaExecutorService(
          "DhruvaSipServer",
          env,
          executorMetricRegistry(),
          wx2Properties().getApplicationInstanceIndex(),
          wx2Properties().isMonitoredExecutorInfluxMetricsEnabled());
    } else {
      return new DhruvaExecutorService(
          "DhruvaSipServer",
          env,
          metricRegistry(),
          wx2Properties().getApplicationInstanceIndex(),
          wx2Properties().isMonitoredExecutorInfluxMetricsEnabled());
    }
  }

  @Bean
  public Integer defaultCacheTimeout() {
    return (int) TimeUnit.MINUTES.toSeconds(DEFAULT_CACHE_TIMEOUT);
  }

  @Bean
  @Lazy
  public DnsInjectionService dnsInjectionService() {
    // TODO check for redis
    return DnsInjectionService.memoryBackedCache();
  }

  @Bean
  public DnsReporter dnsReporter() {
    return new DnsMetricsReporter();
  }

  @Bean
  public SipServerLocatorService sipServerLocatorService() {
    return new SipServerLocatorService(
        dhruvaSIPConfigProperties, getApplicationContext().getBean(DhruvaExecutorService.class));
  }

  @Bean
  public DnsReporter dnsMetricsReporter() {
    return new DnsMetricsReporter();
  }

  @Bean
  public DnsLookup dnsLookup() {
    return DnsResolvers.newBuilder()
        .cacheSize(dhruvaSIPConfigProperties.getDhruvaDnsCacheMaxSize())
        .dnsLookupTimeoutMillis(dhruvaSIPConfigProperties.dnsLookupTimeoutMillis())
        .retentionDurationMillis(dhruvaSIPConfigProperties.dnsCacheRetentionTimeMillis())
        .metered(dnsMetricsReporter())
        .build();
  }

  @Bean
  public DsbTrustManager dsbTrustManager() throws Exception {
    DsbTrustManager trustManager;
    if (dhruvaSIPConfigProperties.getTlsAuthType() == TLSAuthenticationType.NONE) {
      logger.warn(
          "Using PermissiveInstance for TrustManager. No certificate validation will be performed.");
      trustManager = DsbTrustManager.createPermissiveInstance();
    } else if (dhruvaSIPConfigProperties.getEnableCertService()) {

      logger.info("Certs Service URL = {}", certTrustManagerProperties.getCertsApiServiceUrl());

      ExecutorService executorService =
          monitoredTrackingPreservedExecutorProvider()
              .newManagedExecutorService("revocation-manager-executor");

      RevocationManager revocationManager = null;

      if (certTrustManagerProperties.useRedisAsCache()) {
        revocationManager =
            new RevocationManager(
                OCSPRevocationCache.redisBackedOcspCache(
                    redisDataSource(),
                    certTrustManagerProperties.getRedisPrefix(),
                    certTrustManagerProperties.getRevocationCacheExpirationHours(),
                    metricRegistry()),
                CRLRevocationCache.redisBackedCRLCache(
                    redisDataSource(),
                    certTrustManagerProperties.getRedisPrefix(),
                    certTrustManagerProperties.getRevocationCacheExpirationHours(),
                    certTrustManagerProperties.getHttpConnectTimeout(),
                    certTrustManagerProperties.getHttpReadTimeout(),
                    metricRegistry()),
                executorService);
      } else {
        revocationManager =
            new RevocationManager(
                OCSPRevocationCache.memoryBackedOcspCache(
                    certTrustManagerProperties.getRevocationCacheExpirationHours()),
                CRLRevocationCache.memoryBackedCRLCache(
                    certTrustManagerProperties.getRevocationCacheExpirationHours(),
                    certTrustManagerProperties.getHttpConnectTimeout(),
                    certTrustManagerProperties.getHttpReadTimeout()),
                executorService);
      }

      revocationManager.setOcspEnabled(certTrustManagerProperties.getOcspEnabled());
      trustManager =
          DsbTrustManager.createInstance(
              certsClientFactory(),
              orgCollectionCache(),
              revocationManager,
              certTrustManagerProperties.getRevocationTimeoutMilliseconds(),
              TimeUnit.MILLISECONDS,
              certTrustManagerProperties.getOrgCertCacheSize());
    } else {
      logger.info("System trust store will be used as source of trust.");
      DsbTrustManager.initTransportProperties(dhruvaSIPConfigProperties);
      trustManager = DsbTrustManager.getSystemTrustManager();
    }

    //    trustManager.setRequireTrustedSipSources(props().requireTrustedSipSources());
    //    trustManager.setTrustedSipSources(props().getTrustedSipSourcesProp());
    return trustManager;
  }

  @Bean
  public KeyManager keyManager(DhruvaSIPConfigProperties sipProperties) {
    return DsbNetworkLayer.createKeyManager(sipProperties);
  }

  public RedisDataSource redisDataSource() {
    return redisDataSourceManager().getRedisDataSource("dsbRedisDataSource");
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

  @Bean
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

  public OrganizationCollectionCache orgCollectionCache() {
    if (certTrustManagerProperties.useRedisAsCache()) {
      return CommonIdentityOrganizationCollectionCache.redisDatasourceBackedCache(
          orgLoader(),
          certTrustManagerProperties,
          redisDataSourceManager()
              .getRedisDataSource(RedisDataSourceManager.COMMON_REDIS_SOURCE_NAME.ORGCACHE),
          metricRegistry());
    }
    return CommonIdentityOrganizationCollectionCache.memoryBackedCache(
        orgLoader(),
        certTrustManagerProperties.getOrgCacheExpirationMinutes(),
        TimeUnit.MINUTES,
        true);
  }
  // TODO DSB
  @Bean
  @Profile("disabled")
  @Override
  public ConfigProperties configProperties() {
    return null;
  }

  @Override
  public Wx2Properties wx2Properties() {
    return props();
  }

  @Bean(name = "configProperties")
  public DhruvaProperties props() {
    return new DhruvaProperties(env);
  }

  // this will be needed in future for http requests
  /*
  @Bean
  public DhruvaDiagnosticsFilter dhruvaDiagnosticsFilter() {
    return new DhruvaDiagnosticsFilter();
  }

  @Bean
  @Override
  public LinkedHashMap<String, FilterInfo> servletFilters() {
    LinkedHashMap<String, FilterInfo> filters = super.servletFilters();
    filters.put(getClass().getName() + ".diagnostics", new FilterInfo(dhruvaDiagnosticsFilter()));
    return filters;
  }
  */

  // DSB TODO
  //  @Bean
  //  public AsyncDiagnosticsClient asyncDiagnosticsClient() {
  //    return new AsyncDiagnosticsClient();
  //  }

  //  @Bean
  //  public CommonIdentityClientFactory commonIdentityClientFactory() {
  //    return CommonIdentityClientFactory.builder(props())
  //        .baseUrl(props().getOAuthEndpointUrl())
  //        .maxConnections(props().getHttpMaxConnections())
  //        .maxConnectionsPerRoute(props().getHttpMaxConnectionsPerRoute())
  //        .federationIgnored(true)
  //        .build();
  //  }

  //  @Bean
  //  public AuthorizationProvider diagnosticsServiceAuthorizationProvider() {
  //
  //    BearerAuthorizationProvider.Builder builder =
  //        BearerAuthorizationProvider.builder(props().getAuthorizationConfig("dhruva"));
  //    return builder
  //        .commonIdentityClientFactory(commonIdentityClientFactory())
  //        .orgId(OrgId.fromString(props().getDhruvaOrgId()))
  //        .userId(props().getDhruvaMachineAccountUser())
  //        .password(props().getDhruvaMachineAccountPassword())
  //        .scope(DiagnosticsClient.CI_SCIM_CALL_DIAGNOSTICS_SCOPE)
  //        .clientId(props().getDhruvaClientId())
  //        .clientSecret(props().getDhruvaClientSecret())
  //        .build();
  //  }

  //  @Bean
  //  public DiagnosticsClientFactory diagnosticsClientFactory() {
  //    return DiagnosticsClientFactory.builder(props())
  //        .baseUrl(props().getDiagnosticsUrl())
  //        .userAgent(props().getUserAgent())
  //        .authorizationProvider(diagnosticsServiceAuthorizationProvider())
  //        .build();
  //  }

  //  @Bean
  //  public BearerAuthorizationProvider scimBearerAuthorizationProvider() {
  //
  //    BearerAuthorizationProvider.Builder builder =
  //        BearerAuthorizationProvider.builder(props().getCommonIdentityAuthorizationConfig());
  //    CommonIdentityClientFactory clientFactory =
  //        CommonIdentityClientFactory.builder(props())
  //            .baseUrl(props().getOAuthEndpointUrl())
  //            .maxConnections(20)
  //            .maxConnectionsPerRoute(20)
  //            .disableSSLChecks(true)
  //            .federationIgnored(true)
  //            .build();
  //    return builder.commonIdentityClientFactory(clientFactory).build();
  //  }

  //  @Bean
  //  public CommonIdentityScimClientFactory scimClientFactory() {
  //    return CommonIdentityScimClientFactory.builder(props())
  //        .baseUrl(props().getScimEndpointUrl())
  //        .authorizationProvider(scimBearerAuthorizationProvider())
  //        .maxConnections(20)
  //        .maxConnectionsPerRoute(20)
  //        .disableSSLChecks(true)
  //        .federationIgnored(true)
  //        .build();
  //  }

  //  public MeetingRegistryClientFactory meetingRegistryClientFactory() {
  //    return MeetingRegistryClientFactory.builder(configProperties())
  //        .authorizationProvider(meetingRegistryAuthorizationProvider())
  //        .baseUrl(configProperties().getMeetingRegistryServicePublicUrl())
  //        .timeoutPolicy(timeoutPolicy())
  //        .build();
  //  }

  //  public AuthorizationProvider meetingRegistryAuthorizationProvider() {
  //    return createDhruvaClientAuthorizationProvider(MeetingRegistryClient.OAUTH_SCOPE_READ);
  //  }
  //
  //  private AuthorizationProvider createDhruvaClientAuthorizationProvider(String... scopes) {
  //    AuthorizationProvider authProvider =
  //        BearerAuthorizationProvider.builder()
  //            .commonIdentityClientFactory(commonIdentityClientFactory())
  //            .orgId(OrgId.fromString(props().getDhruvaOrgId()))
  //            .userId(props().getDhruvaMachineAccountUser())
  //            .password(props().getDhruvaMachineAccountPassword())
  //            .scope(Utilities.setAsString(scopes))
  //            .clientId(props().getDhruvaClientId())
  //            .clientSecret(props().getDhruvaClientSecret())
  //            .build();
  //
  //    try {
  //      String auth = authProvider.getAuthorization();
  //      if (auth != null && auth.length() < 512) {
  //        log.warn(
  //            "Check that machine account is using a self-contained token, length = {}, scopes =
  // {}",
  //            auth.length(),
  //            scopes);
  //      }
  //    } catch (Exception ignore) {
  //      log.info("Unable to get machine account authorization, scopes = {}", (Object[]) scopes);
  //    }
  //
  //    return authProvider;
  //  }
}
