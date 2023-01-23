package com.cisco.dsb.common.sip.tls;

import com.cisco.dsb.common.dto.TrustedSipSources;
import java.security.cert.CertificateException;
import java.security.cert.X509Certificate;
import java.util.ArrayList;
import javax.net.ssl.X509TrustManager;
import lombok.CustomLog;
import lombok.Setter;

@CustomLog
/**
 * This is implementation of {@link X509TrustManager} which uses any X509TrustManager Impl classes
 * to carry out TLS cert Validation. Additional to validating TLS certs, sip trusted source hostname
 * validation is also done.
 */
public class DsbTrustManager implements X509TrustManager {

  protected final X509TrustManager trustManager;
  private boolean requireTrustedSipSources;
  private TrustedSipSources trustedSipSources;
  @Setter private boolean acceptedIssuersEnabled = false;

  /**
   * If passed arg is null, then all permissive trustManager is returned.
   *
   * @param trustManager X509TrustManager implementation class
   */
  public DsbTrustManager(X509TrustManager trustManager) {
    this.trustManager = trustManager;
  }

  @Override
  public void checkClientTrusted(X509Certificate[] chain, String authType)
      throws CertificateException {
    if (logger.isDebugEnabled()) {
      for (X509Certificate certificate : chain) {
        logger.debug(
            "SubjectDN: {}, IssuerDN: {}", certificate.getSubjectDN(), certificate.getIssuerDN());
      }
    }

    if (trustManager == null) {
      return;
    }

    validateTrustedSipSources(chain);

    trustManager.checkClientTrusted(chain, authType);
  }

  @Override
  public void checkServerTrusted(X509Certificate[] chain, String authType)
      throws CertificateException {
    if (logger.isDebugEnabled()) {
      for (X509Certificate certificate : chain) {
        logger.debug(
            "SubjectDN: {}, IssuerDN: {}", certificate.getSubjectDN(), certificate.getIssuerDN());
      }
    }

    if (trustManager == null) {
      return;
    }

    validateTrustedSipSources(chain);

    trustManager.checkServerTrusted(chain, authType);
  }

  @Override
  public X509Certificate[] getAcceptedIssuers() {
    if (acceptedIssuersEnabled) {
      return trustManager.getAcceptedIssuers();
    } else {
      return new X509Certificate[0];
    }
  }

  /**
   * Should be set according to "requireTrustedSipSources" config parameter.
   *
   * @param required set true if an empty trust list should mean that NO sip sources are trusted.
   *     set false if an empty trust list should mean that ALL sip sources are trusted.
   */
  public void setRequireTrustedSipSources(boolean required) {
    if (!requireTrustedSipSources && required && trustedSipSources.isEmpty()) {
      logger.warn(
          "trustedSipSources is now required but empty.  No TLS connections will be allowed.");
    }
    this.requireTrustedSipSources = required;
  }

  public void setTrustedSipSources(TrustedSipSources newSipSources) {
    this.trustedSipSources = newSipSources == null ? new TrustedSipSources() : newSipSources;
    if (requireTrustedSipSources && trustedSipSources.isEmpty()) {
      logger.warn("trustedSipSources is empty but required.  No TLS connections will be allowed.");
    } else {
      logger.info("Setting trustedSipSources: {}", trustedSipSources);
    }
  }

  private void validateTrustedSipSources(X509Certificate[] chain) throws CertificateException {
    // Trust everything if no whitelist is provided and "requireTrustedSipSources" config is FALSE
    if (!requireTrustedSipSources && (trustedSipSources == null || trustedSipSources.isEmpty())) {
      return;
    }

    validateChain(chain);
    CertificateInfo certificateInfo = CertificateInfo.createFromX509Certificate(chain[0]);

    if (!HostNameValidationHelper.match(
        new ArrayList<>(trustedSipSources.getTrustedSipSources()), chain[0])) {
      logger.warn(
          "Rejected connection from untrusted source.  trustedSipSources: {}, certificate: {}",
          trustedSipSources,
          certificateInfo);

      throw new CertificateException(
          "Hostname validation failed for certificate " + certificateInfo);
    }
    logger.debug(
        "Trusting connection with  trustedSipSources: {}, certificate: {}",
        trustedSipSources,
        certificateInfo);
  }

  private void validateChain(X509Certificate[] chain) throws CertificateException {
    if (chain == null || chain.length == 0) {
      logger.warn("Empty certificate chain.");
      throw new CertificateException("Empty certificate chain.");
    }
  }
}
