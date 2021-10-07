package com.cisco.dsb.common.sip.tls;

import static com.cisco.dsb.common.sip.tls.CertTrustManagerProperties.DHRUVA_SERVICE_PASS;
import static com.cisco.dsb.common.sip.tls.CertTrustManagerProperties.DHRUVA_SERVICE_USER;
import static org.mockito.Mockito.when;

import com.cisco.dsb.common.config.sip.DhruvaSIPConfigProperties;
import com.cisco.dsb.common.util.CertUtil;
import com.cisco.wx2.certs.client.CertsX509TrustManager;
import java.security.cert.CertificateException;
import java.security.cert.CertificateExpiredException;
import java.security.cert.X509Certificate;
import javax.net.ssl.TrustManager;
import javax.net.ssl.X509TrustManager;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.testng.Assert;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.Test;

public class DsbTrustManagerTest {

  @Mock DhruvaSIPConfigProperties dhruvaSIPConfigProperties;
  @Mock CertsX509TrustManager mockCertsX509TrustManager;
  private String keystorePath;

  @BeforeClass
  public void before() {
    MockitoAnnotations.initMocks(this);
    System.out.println("checking resources path: " + DsbTrustManagerTest.class.getClassLoader()
        .getResource("logback-included.xml").getPath());
    keystorePath = DsbTrustManagerTest.class.getClassLoader().getResource("keystore.jks").getPath();
    System.out.println("keystore file path: " + keystorePath);
  }

  @Test(
      description =
          "fetch system trust manager of a trust manager factory and check for valid certificate in keystore")
  public void testSystemTrustManager() throws Exception {
    when(dhruvaSIPConfigProperties.getEnableCertService()).thenReturn(false);
    when(dhruvaSIPConfigProperties.getTlsAuthType()).thenReturn(TLSAuthenticationType.MTLS);
    when(dhruvaSIPConfigProperties.getTrustStoreFilePath()).thenReturn(keystorePath);
    when(dhruvaSIPConfigProperties.getTrustStoreType()).thenReturn("jks");
    when(dhruvaSIPConfigProperties.getTrustStorePassword()).thenReturn("dsb123");
    when(dhruvaSIPConfigProperties.isTlsCertRevocationSoftFailEnabled()).thenReturn(true);
    when(dhruvaSIPConfigProperties.isTlsOcspEnabled()).thenReturn(true);
    TrustManager tm = DhruvaTrustManagerFactory.getTrustManager(dhruvaSIPConfigProperties);
    Assert.assertNotNull(tm);
    Assert.assertTrue(tm instanceof DsbTrustManager);
    Assert.assertTrue(tm instanceof X509TrustManager);
    X509Certificate cert = CertUtil.pemToCert(("server.crt.pem"));

    X509Certificate[] certs = {cert};
    ((X509TrustManager) tm).checkClientTrusted(certs, "RSA");
    ((X509TrustManager) tm).checkServerTrusted(certs, "RSA");
  }

  @Test(
      description = "get systemTrustManager and check for invalid date in cert in keystore",
      expectedExceptions = {CertificateExpiredException.class})
  public void testSystemTrustManagerForInvalidDateInCertificate() throws Exception {
    when(dhruvaSIPConfigProperties.getEnableCertService()).thenReturn(false);
    when(dhruvaSIPConfigProperties.getTlsAuthType()).thenReturn(TLSAuthenticationType.MTLS);
    when(dhruvaSIPConfigProperties.getTrustStoreFilePath()).thenReturn(keystorePath);
    when(dhruvaSIPConfigProperties.getTrustStoreType()).thenReturn("jks");
    when(dhruvaSIPConfigProperties.getTrustStorePassword()).thenReturn("dsb123");
    when(dhruvaSIPConfigProperties.isTlsCertRevocationSoftFailEnabled()).thenReturn(true);
    when(dhruvaSIPConfigProperties.isTlsOcspEnabled()).thenReturn(true);
    TrustManager tm = DhruvaTrustManagerFactory.getTrustManager(dhruvaSIPConfigProperties);
    Assert.assertNotNull(tm);
    Assert.assertTrue(tm instanceof DsbTrustManager);
    Assert.assertTrue(tm instanceof X509TrustManager);
    X509Certificate cert = CertUtil.pemToCert(("invalid_date.crt.pem"));

    X509Certificate[] certs = {cert};
    ((X509TrustManager) tm).checkClientTrusted(certs, "RSA");
  }

  @Test(
      description = "get systemTrustManager and check for certificate not in keystore",
      expectedExceptions = {CertificateException.class})
  public void testSystemTrustManagerForUntrustedCertificate() throws Exception {
    when(dhruvaSIPConfigProperties.getEnableCertService()).thenReturn(false);
    when(dhruvaSIPConfigProperties.getTlsAuthType()).thenReturn(TLSAuthenticationType.MTLS);
    when(dhruvaSIPConfigProperties.getTrustStoreFilePath()).thenReturn(keystorePath);
    when(dhruvaSIPConfigProperties.getTrustStoreType()).thenReturn("jks");
    when(dhruvaSIPConfigProperties.getTrustStorePassword()).thenReturn("dsb123");
    when(dhruvaSIPConfigProperties.isTlsCertRevocationSoftFailEnabled()).thenReturn(true);
    when(dhruvaSIPConfigProperties.isTlsOcspEnabled()).thenReturn(true);
    TrustManager tm = DhruvaTrustManagerFactory.getTrustManager(dhruvaSIPConfigProperties);
    Assert.assertNotNull(tm);
    Assert.assertTrue(tm instanceof DsbTrustManager);
    Assert.assertTrue(tm instanceof X509TrustManager);
    X509Certificate cert = CertUtil.pemToCert(("not_in_keystore.crt.pem"));

    X509Certificate[] certs = {cert};
    ((X509TrustManager) tm).checkClientTrusted(certs, "RSA");
  }

  @Test(description = "get systemTrustManager and check for certificate not in keystore")
  public void testCertTrustManager() throws Exception {
    when(dhruvaSIPConfigProperties.getEnableCertService()).thenReturn(true);
    when(dhruvaSIPConfigProperties.getTlsAuthType()).thenReturn(TLSAuthenticationType.MTLS);
    when(dhruvaSIPConfigProperties.getTrustStoreFilePath()).thenReturn(keystorePath);
    when(dhruvaSIPConfigProperties.getTrustStoreType()).thenReturn("jks");
    when(dhruvaSIPConfigProperties.getTrustStorePassword()).thenReturn("dsb123");
    when(dhruvaSIPConfigProperties.isTlsCertRevocationSoftFailEnabled()).thenReturn(true);
    when(dhruvaSIPConfigProperties.isTlsOcspEnabled()).thenReturn(true);
    CertTrustManagerFactory.setCertsX509TrustManager(mockCertsX509TrustManager);
    System.setProperty(DHRUVA_SERVICE_PASS, "dummyPass");
    System.setProperty(DHRUVA_SERVICE_USER, "dummyUserId");
    System.setProperty("dhruvaClientId", "dummyClientId");
    System.setProperty("dhruvaClientSecret", "dummyClientPassword");
    TrustManager tm = DhruvaTrustManagerFactory.getTrustManager(dhruvaSIPConfigProperties);
    Assert.assertNotNull(tm);
    Assert.assertEquals(tm, mockCertsX509TrustManager);
  }

  @Test(description = "test permissive trust manager creation")
  public void testPermissiveTrustManager() throws Exception {
    when(dhruvaSIPConfigProperties.getEnableCertService()).thenReturn(false);
    when(dhruvaSIPConfigProperties.getTlsAuthType()).thenReturn(TLSAuthenticationType.NONE);

    TrustManager tm = DhruvaTrustManagerFactory.getTrustManager(dhruvaSIPConfigProperties);
    Assert.assertNotNull(tm);
    Assert.assertFalse(tm instanceof DsbTrustManager);
    Assert.assertTrue(tm instanceof X509TrustManager);
    X509Certificate[] certs = {CertUtil.pemToCert("not_in_keystore.crt.pem")};
    ((X509TrustManager) tm).checkClientTrusted(certs, "");
  }
}
