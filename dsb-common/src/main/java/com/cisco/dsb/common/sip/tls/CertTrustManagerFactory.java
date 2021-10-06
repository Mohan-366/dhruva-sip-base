package com.cisco.dsb.common.sip.tls;

import com.cisco.wx2.certs.client.CertsClientFactory;
import com.cisco.wx2.certs.client.CertsX509TrustManager;
import com.cisco.wx2.certs.common.util.RevocationManager;
import com.cisco.wx2.server.organization.OrganizationCollectionCache;
import java.util.concurrent.TimeUnit;

public class CertTrustManagerFactory {

  private static CertsX509TrustManager certsX509TrustManager;

  public static CertsX509TrustManager getCertsX509TrustManager(
      CertsClientFactory certsClientFactory,
      OrganizationCollectionCache organizationCache,
      RevocationManager revocationManager,
      long revocationTimeout,
      TimeUnit revocationTimeoutUnits,
      long orgCacheSize,
      boolean loadIntegrationEnvCAs) {
    if (certsX509TrustManager != null) {
      return certsX509TrustManager;
    } else {
      return new CertsX509TrustManager(
          certsClientFactory,
          organizationCache,
          revocationManager,
          revocationTimeout,
          revocationTimeoutUnits,
          orgCacheSize,
          loadIntegrationEnvCAs);
    }
  }

  public static void setCertsX509TrustManager(CertsX509TrustManager certsX509TrustManager) {
    CertTrustManagerFactory.certsX509TrustManager = certsX509TrustManager;
  }
}
