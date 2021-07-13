package com.cisco.dsb.transport;

import com.cisco.wx2.certs.client.CertsClientFactory;
import com.cisco.wx2.certs.client.CertsX509TrustManager;
import com.cisco.wx2.certs.common.util.RevocationManager;
import com.cisco.wx2.server.organization.OrganizationCollectionCache;
import com.cisco.wx2.util.SslUtil;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.TimeUnit;
import javax.annotation.Nonnull;
import javax.net.ssl.TrustManager;
import javax.net.ssl.TrustManagerFactory;
import javax.net.ssl.X509TrustManager;

public class TrustManagerProvider {

  /**
   * Given a TrustManagerFactory, list of trust managers(if present) are fetched
   *
   * @param tmf - should be created & initialised(using tmf.init()) by the implementing apps before
   *     invoking this method. If it is not initialised, then tmf.getTrustManagers() will throw an
   *     'IllegalStateException: TrustManagerFactoryImpl is not initialized'
   * @return all/no TrustManagers wrapped in an Optional
   */
  public static Optional<TrustManager[]> getAllTrustManagers(@Nonnull TrustManagerFactory tmf) {
    Objects.requireNonNull(tmf, "Input TrustManagerFactory is null");
    return Optional.of(tmf.getTrustManagers());
  }

  /**
   * Given a TrustManagerFactory, default system trust manager of type X509TrustManager is returned
   *
   * @param tmf - should be created & initialised(using tmf.init()) by the implementing apps before
   *     invoking this method. If it is not initialised, then tmf.getTrustManagers() will throw an
   *     'IllegalStateException: TrustManagerFactoryImpl is not initialized'
   * @return X509TrustManager wrapped in an Optional
   */
  public static Optional<X509TrustManager> getSystemTrustManager(@Nonnull TrustManagerFactory tmf) {
    Objects.requireNonNull(tmf, "Input TrustManagerFactory is null");

    Optional<TrustManager[]> tms = TrustManagerProvider.getAllTrustManagers(tmf);
    if (!tms.isPresent() || tms.get().length == 0) {
      return Optional.empty();
    }
    for (TrustManager tm : tms.get()) {
      if (tm instanceof X509TrustManager) {
        return Optional.of((X509TrustManager) tm);
      }
    }
    return Optional.empty();
  }

  /**
   * Provides a CertsX509TrustManager (which delegates trust and certificate validation to the Certs
   * client and service) given the required parameters to create one
   *
   * @param certsClientFactory CertsClientFactory instance
   * @param orgCollectionCache a cache ot type OrganizationCollectionCache created with its
   *     associated properties
   * @param revocationManager RevocationManager instance created with its associated properties
   * @param revocationTimeout timeout duration
   * @param revocationTimeoutUnit time unit in which duration is provided, eg: Seconds,
   *     milliseconds, etc
   * @param orgCacheSize cache size
   * @param loadIntegrationEnvCAs to load int env CAs or not
   * @return CertsX509TrustManager
   */
  public static CertsX509TrustManager getCertsX509TrustManager(
      CertsClientFactory certsClientFactory,
      OrganizationCollectionCache orgCollectionCache,
      RevocationManager revocationManager,
      long revocationTimeout,
      TimeUnit revocationTimeoutUnit,
      long orgCacheSize,
      boolean loadIntegrationEnvCAs) {
    return new CertsX509TrustManager(
        certsClientFactory,
        orgCollectionCache,
        revocationManager,
        revocationTimeout,
        revocationTimeoutUnit,
        orgCacheSize,
        loadIntegrationEnvCAs);
  }

  /**
   * Provides a PermissiveTrustManager (which performs no certificate validation and trusts all
   * certificates)
   *
   * @return PermissiveTrustManager
   */
  public static SslUtil.PermissiveTrustManager getSslUtilPermissiveTrustManager() {
    return new SslUtil.PermissiveTrustManager();
  }
}
