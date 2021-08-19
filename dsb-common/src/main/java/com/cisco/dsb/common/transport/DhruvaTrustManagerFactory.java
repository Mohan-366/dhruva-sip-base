package com.cisco.dsb.common.transport;

import com.cisco.dsb.common.util.log.DhruvaLoggerFactory;
import com.cisco.dsb.common.util.log.Logger;
import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import java.security.cert.CertificateException;
import java.security.cert.X509Certificate;
import javax.net.ssl.TrustManager;
import javax.net.ssl.X509TrustManager;

public class DhruvaTrustManagerFactory {
  private static final Logger logger =
      DhruvaLoggerFactory.getLogger(DhruvaTrustManagerFactory.class);

  public static TrustManager getTrustManager(
      String deploymentName, TLSAuthenticationType tlsAuthenticationType, boolean enableCertService)
      throws Exception {
    if (tlsAuthenticationType == TLSAuthenticationType.MTLS && enableCertService) {
      CertTrustManager certTrustManager = CertTrustManager.createCertTrustManager();

      return certTrustManager.getTrustManager();

    } else if (tlsAuthenticationType == TLSAuthenticationType.NONE)
      // return a dummy trust manager which does nothing
      return new X509TrustManager() {

        @Override
        public void checkClientTrusted(X509Certificate[] x509Certificates, String s)
            throws CertificateException {
          logger.debug(
              "Accepting a client certificate: {} for deployment {}",
              x509Certificates[0].getSubjectDN(),
              deploymentName);
        }

        @Override
        public void checkServerTrusted(X509Certificate[] x509Certificates, String s)
            throws CertificateException {
          final X509Certificate serverCert = x509Certificates[0];
          logger.debug(
              "Accepting a server certificate:{} for deployment {}",
              serverCert.getSubjectDN().getName(),
              deploymentName);
        }

        @Override
        @SuppressFBWarnings(value = {"WEAK_TRUST_MANAGER"})
        public X509Certificate[] getAcceptedIssuers() {
          return new X509Certificate[0];
        }
      };
    else {
      // Default mTLS
      return DhruvaTrustManager.getSystemTrustManager();
    }
  }
}
