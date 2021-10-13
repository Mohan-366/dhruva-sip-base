package com.cisco.dsb.common.util;

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

  public static X509Certificate pemToCert(String certificateFile) throws CertificateException {
    X509Certificate certificate = null;
    CertificateFactory cf = null;
    try {
      cf = CertificateFactory.getInstance("X509");
      certificate =
          (X509Certificate)
              cf.generateCertificate(
                  CertUtil.class.getClassLoader().getResourceAsStream(certificateFile));
      //      }
    } catch (CertificateException e) {
      throw new CertificateException(e);
    }
    return certificate;
  }
}
