package com.cisco.dsb.common.transport;

import java.io.ByteArrayInputStream;
import java.security.cert.CertificateEncodingException;
import java.security.cert.CertificateException;
import java.security.cert.CertificateFactory;
import java.security.cert.X509Certificate;
import org.apache.commons.codec.binary.Base64;

public class CertUtil {

  public static String certToPem(X509Certificate cert) throws CertificateEncodingException {
    return "-----BEGIN CERTIFICATE-----\n"
        + new String(Base64.encodeBase64(cert.getEncoded(), true))
        + "-----END CERTIFICATE-----";
  }

  public static X509Certificate pemToCert(String certificateString) throws CertificateException {
    X509Certificate certificate = null;
    CertificateFactory cf = null;
    try {
      if (certificateString != null && !certificateString.trim().isEmpty()) {
        certificateString =
            certificateString
                .replace("-----BEGIN CERTIFICATE-----\n", "")
                .replace("-----END CERTIFICATE-----", ""); // NEED FOR PEM FORMAT CERT STRING
        byte[] certificateData = Base64.decodeBase64(certificateString);
        cf = CertificateFactory.getInstance("X509");
        certificate =
            (X509Certificate) cf.generateCertificate(new ByteArrayInputStream(certificateData));
      }
    } catch (CertificateException e) {
      throw new CertificateException(e);
    }
    return certificate;
  }
}
