package com.cisco.dsb.common.transport;

import com.cisco.wx2.certs.common.util.X509CertificateGenerator;
import java.io.IOException;
import java.security.GeneralSecurityException;
import java.security.cert.X509Certificate;
import org.testng.Assert;
import org.testng.annotations.Test;

public class CertUtilTest {

  @Test(description = "checks cert->pem conversion and pem->cert conversion")
  public void testCertToPem() throws IOException, GeneralSecurityException {
    X509CertificateGenerator generator =
        X509CertificateGenerator.getDefaultIntegrationTestCertificateGenerator();
    X509Certificate cert = generator.createCertificate("CN=Cloud Calling Entity", null);

    String pem = CertUtil.certToPem(cert);

    X509Certificate certRestored = CertUtil.pemToCert(pem);
    String pemRestored = CertUtil.certToPem(certRestored);

    Assert.assertEquals(pem, pemRestored);
  }
}
