package com.cisco.dsb.common.sip.tls;

import com.cisco.dsb.common.config.sip.DhruvaSIPConfigProperties;
import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import java.security.cert.CertificateException;
import java.security.cert.X509Certificate;
import javax.net.ssl.TrustManager;
import javax.net.ssl.X509TrustManager;
import lombok.CustomLog;

@CustomLog
public class DhruvaTrustManagerFactory {

  public static TrustManager getTrustManager(DhruvaSIPConfigProperties dhruvaSIPConfigProperties)
      throws Exception {
    TLSAuthenticationType tlsAuthenticationType = dhruvaSIPConfigProperties.getTlsAuthType();
    Boolean enableCertService = dhruvaSIPConfigProperties.getEnableCertService();

    if (tlsAuthenticationType == TLSAuthenticationType.MTLS && enableCertService) {

    } else if (tlsAuthenticationType == TLSAuthenticationType.NONE)
      // return a dummy trust manager which does nothing
      return new X509TrustManager() {

        @Override
        public void checkClientTrusted(X509Certificate[] x509Certificates, String s)
            throws CertificateException {
          logger.debug("Accepting a client certificate: {}", x509Certificates[0].getSubjectDN());
        }

        @Override
        public void checkServerTrusted(X509Certificate[] x509Certificates, String s)
            throws CertificateException {
          final X509Certificate serverCert = x509Certificates[0];
          logger.debug("Accepting a server certificate:{}", serverCert.getSubjectDN().getName());
        }

        @Override
        @SuppressFBWarnings(value = {"WEAK_TRUST_MANAGER"})
        public X509Certificate[] getAcceptedIssuers() {
          return new X509Certificate[0];
        }
      };
    else {
      // Default mTLS
      DsbTrustManager.initTransportProperties(dhruvaSIPConfigProperties);
      return DsbTrustManager.getSystemTrustManager();
    }
    return null;
  }
}
