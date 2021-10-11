package com.cisco.dsb.common.sip.tls;

import com.cisco.wx2.server.config.ConfigProperties;
import com.cisco.wx2.util.OrgId;
import com.google.common.base.Strings;
import java.net.URI;
import java.util.Properties;
import lombok.CustomLog;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.Environment;
import org.springframework.core.env.StandardEnvironment;

@Configuration
@Qualifier("certTrustManagerConfigProperties")
@CustomLog
public class CertTrustManagerProperties extends ConfigProperties {

  private static final String DEFAULT_DHRUVA_USER_AGENT = "WX2_DHRUVA";
  static final String REVOCATION_TIMEOUT_MILLISECONDS = "revocationTimeoutMilliseconds";
  private static final long DEFAULT_REVOCATION_TIMEOUT_MILLISECONDS = 2500L;

  private static final String REVOCATION_CACHE_EXPIRATION_HOURS = "revocationCacheExpirationHours";
  private static final int DEFAULT_REVOCATION_CACHE_EXPIRATION_HOURS = 24;

  private static final String OCSP_REVOCATION_ENABLED = "ocspEnabled";
  private static final boolean DEFAULT_OCSP_REVOCATION_ENABLED = true;

  private static final String USE_REDIS_AS_CACHE = "useRedis";

  public static final String CERTS_INTERNAL_URL_PROP = "certsApiServiceUrl";

  public static final String DEFAULT_CERTS_URL =
      "https://certs.intb1.ciscospark.com/certificate/api/v1/";

  public static final String ORG_CERT_CACHE_SIZE = "orgCertCacheSize";

  public static final String CERT_API_SERVICE_PUBLIC_URL = "certsApiServicePublicUrl";

  public static final String USE_SYSTEM_TRUSTSTORE = "useSystemTrustStore";

  public static final String IS_MACHINE_ACCOUNT_AUTH_ENABLED = "isMachineAccountAuthEnabled";

  public static final String ORG_NAME = "orgName";

  public static final String DHRUVA_ORG_ID_PROP = "dhruvaOrgId";

  public static final String DHRUVA_SERVICE_USER = "dhruvaServiceUser";
  public static final String DHRUVA_SERVICE_PASS = "dhruvaServicePasswordProp";
  private static final String PROPERTY_STRING = "callServiceConnectClientProperties";
  private static final String PROPERTY_MAP = "callServiceConnectPropertyMap";
  private static final String REVOCATION_THREAD_POOL_SIZE =
      "com.dynamicsoft.DsLibs.DsUtil.revocationManagerThreadPoolSize";
  private static final int REVOCATION_THREAD_POOL_SIZE_DEFAULT = 20;
  private static final String DHRUVA_CLIENT_ID = "dhruvaClientId";
  private static final String DHRUVA_CLIENT_SECRET = "dhruvaClientSecret";

  private Properties secrets;
  private static Environment environment = new StandardEnvironment();

  public CertTrustManagerProperties() {
    super(environment, DEFAULT_DHRUVA_USER_AGENT);
    this.secrets = new Properties(System.getProperties());
  }

  @Override
  public String getUserAgent() {
    return DEFAULT_DHRUVA_USER_AGENT;
  }

  public long getRevocationTimeoutMilliseconds() {
    return env.getProperty(
        REVOCATION_TIMEOUT_MILLISECONDS, Long.class, DEFAULT_REVOCATION_TIMEOUT_MILLISECONDS);
  }

  public int getRevocationCacheExpirationHours() {
    return env.getProperty(
        REVOCATION_CACHE_EXPIRATION_HOURS,
        Integer.class,
        DEFAULT_REVOCATION_CACHE_EXPIRATION_HOURS);
  }

  public boolean getOcspEnabled() {
    return env.getProperty(OCSP_REVOCATION_ENABLED, Boolean.class, DEFAULT_OCSP_REVOCATION_ENABLED);
  }

  public long getOrgCertCacheSize() {
    return env.getProperty(ORG_CERT_CACHE_SIZE, Long.class, 1000L);
  }

  public boolean useRedisAsCache() {
    return env.getProperty(USE_REDIS_AS_CACHE, Boolean.class, false);
  }

  public URI getCertsApiServicePublicUrl() {
    return URI.create(env.getProperty(CERT_API_SERVICE_PUBLIC_URL, DEFAULT_CERTS_URL));
  }

  public URI getCertsApiServiceUrl() {
    return URI.create(
        env.getProperty(CERTS_INTERNAL_URL_PROP, getCertsApiServicePublicUrl().toString()));
  }

  public boolean useSystemTrustStore() {
    return env.getProperty(USE_SYSTEM_TRUSTSTORE, Boolean.class, isEcpEnvironment());
  }

  public boolean isMachineAccountAuthEnabled() {
    return env.getProperty(IS_MACHINE_ACCOUNT_AUTH_ENABLED, Boolean.class, false);
  }

  public String getOrgIdString() {
    return env.getProperty(DHRUVA_ORG_ID_PROP, secrets.getProperty(DHRUVA_ORG_ID_PROP));
  }

  public String getOrgName() {
    return env.getProperty(ORG_NAME, getOrgIdString());
  }

  public String getDhruvaServiceUser() {
    return env.getProperty(DHRUVA_SERVICE_USER);
  }

  public String getDhruvaServicePassword() {
    return env.getProperty(DHRUVA_SERVICE_PASS);
  }

  public String getDhruvaClientId() {
    return env.getProperty(DHRUVA_CLIENT_ID);
  }

  public String getDhruvaClientSecret() {
    return env.getProperty(DHRUVA_CLIENT_SECRET);
  }

  public OrgId getDhruvaOrgId() {
    String orgId = getOrgIdString();
    return Strings.isNullOrEmpty(orgId) ? null : OrgId.fromString(orgId);
  }

  public int getRevocationManagerThreadPoolSize() {
    return env.getProperty(
        REVOCATION_THREAD_POOL_SIZE, Integer.class, REVOCATION_THREAD_POOL_SIZE_DEFAULT);
  }
}
