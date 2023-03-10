package com.cisco.dsb.common.config;

import com.cisco.dsb.common.config.sip.CommonConfigurationProperties;
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
import com.cisco.dsb.common.sip.tls.DsbTrustManagerFactory;
import com.cisco.dsb.common.util.log.ConnectionAspect;
import com.cisco.dsb.common.util.log.event.EventingService;
import com.cisco.wx2.dto.IdentityMachineAccount;
import com.cisco.wx2.server.config.ConfigProperties;
import com.cisco.wx2.server.config.Wx2Properties;
import com.cisco.wx2.server.exception.CsbHandlerExceptionResolver;
import com.cisco.wx2.util.stripedexecutor.StripedExecutorService;
import com.ciscospark.server.Wx2ConfigAdapter;
import java.util.concurrent.TimeUnit;
import javax.annotation.PostConstruct;
import javax.annotation.PreDestroy;
import lombok.CustomLog;
import org.aspectj.lang.Aspects;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.context.annotation.*;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.core.env.Environment;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.annotation.EnableScheduling;
import reactor.core.scheduler.Schedulers;

@Configuration
@ConditionalOnWebApplication
@EnableAsync
@EnableScheduling
@CustomLog
public class DhruvaConfig extends Wx2ConfigAdapter {

  @Autowired CommonConfigurationProperties commonConfigurationProperties;
  @Autowired DsbTrustManagerFactory dsbTrustManagerFactory;
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

  @PostConstruct
  public void initialize() {
    if (commonConfigurationProperties.isReactorSchedulerMetricsEnabled()) {
      Schedulers.enableMetrics();
    }
  }

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
  public EventingService eventingService() {
    return new EventingService(commonConfigurationProperties.isEmitUnmaskedEvents());
  }

  @Bean
  @Order(Ordered.HIGHEST_PRECEDENCE)
  public CsbHandlerExceptionResolver dhruvaExceptionResolver() {
    return new DhruvaExceptionResolver();
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
          "dsb",
          env,
          executorMetricRegistry(),
          wx2Properties().getApplicationInstanceIndex(),
          wx2Properties().isMonitoredExecutorInfluxMetricsEnabled());
    } else {
      return new DhruvaExecutorService(
          "dsb",
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
        commonConfigurationProperties,
        getApplicationContext().getBean(DhruvaExecutorService.class));
  }

  @Bean
  public DnsReporter dnsMetricsReporter() {
    return new DnsMetricsReporter();
  }

  @Bean
  public DnsLookup dnsLookup() {
    return DnsResolvers.newBuilder()
        .cacheSize(commonConfigurationProperties.getDnsCacheSize())
        .dnsLookupTimeoutMillis(commonConfigurationProperties.getDnsLookupTimeoutMillis())
        .retentionDurationMillis(commonConfigurationProperties.getTimeOutDnsCache())
        .metered(dnsMetricsReporter())
        .build();
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
  @Primary
  public DhruvaProperties props() {
    return new DhruvaProperties(env);
  }

  /*
  To inject ConnectionAspect, used for LMA of connection related information
  */
  @Bean
  public ConnectionAspect connectionAspectBean() {
    return Aspects.aspectOf(ConnectionAspect.class);
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
