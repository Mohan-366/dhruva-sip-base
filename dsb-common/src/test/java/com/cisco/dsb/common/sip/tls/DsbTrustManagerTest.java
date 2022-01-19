package com.cisco.dsb.common.sip.tls;

import static com.cisco.dsb.common.sip.tls.CertTrustManagerProperties.DHRUVA_SERVICE_PASS;
import static com.cisco.dsb.common.sip.tls.CertTrustManagerProperties.DHRUVA_SERVICE_USER;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.cisco.dsb.common.config.DhruvaConfig;
import com.cisco.dsb.common.config.sip.CommonConfigurationProperties;
import com.cisco.dsb.common.sip.stack.dto.DhruvaNetwork;
import com.cisco.wx2.certs.client.CertsX509TrustManager;
import java.security.cert.CertificateException;
import java.security.cert.X509Certificate;
import javax.net.ssl.TrustManager;
import javax.net.ssl.X509TrustManager;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.mockito.Spy;
import org.testng.Assert;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.Test;

public class DsbTrustManagerTest {

  @Mock CommonConfigurationProperties commonConfigurationProperties;
  @Spy CertTrustManagerProperties certTrustManagerProperties = new CertTrustManagerProperties();
  @Mock CertsX509TrustManager mockCertsX509TrustManager;
  private String keystorePath;
  @InjectMocks DsbTrustManagerFactory dsbTrustManagerFactory;
  @InjectMocks DhruvaConfig dhruvaConfig;

  @BeforeClass
  public void before() {

    dsbTrustManagerFactory = spy(new DsbTrustManagerFactory());
    MockitoAnnotations.initMocks(this);
    DhruvaNetwork.setDhruvaConfigProperties(commonConfigurationProperties);
    keystorePath = DsbTrustManagerTest.class.getClassLoader().getResource("keystore.jks").getPath();
  }

  @Test(
      description =
          "fetch system trust manager of a trust manager factory and check for valid certificate in keystore")
  public void testSystemTrustManager() throws Exception {
    when(commonConfigurationProperties.isEnableCertService()).thenReturn(false);
    when(commonConfigurationProperties.getTlsAuthType()).thenReturn(TLSAuthenticationType.MTLS);
    when(commonConfigurationProperties.getTlsTrustStoreFilePath()).thenReturn(keystorePath);
    when(commonConfigurationProperties.getTlsTrustStoreType()).thenReturn("jks");
    when(commonConfigurationProperties.getTlsTrustStorePassword()).thenReturn("dsb123");
    when(commonConfigurationProperties.isTlsCertRevocationEnableSoftFail()).thenReturn(true);
    when(commonConfigurationProperties.isTlsCertEnableOcsp()).thenReturn(true);
    TrustManager tm = dhruvaConfig.dsbTrustManager();
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
      expectedExceptions = {CertificateException.class})
  public void testSystemTrustManagerForInvalidDateInCertificate() throws Exception {
    when(commonConfigurationProperties.isEnableCertService()).thenReturn(false);
    when(commonConfigurationProperties.getTlsAuthType()).thenReturn(TLSAuthenticationType.MTLS);
    when(commonConfigurationProperties.getTlsTrustStoreFilePath()).thenReturn(keystorePath);
    when(commonConfigurationProperties.getTlsTrustStoreType()).thenReturn("jks");
    when(commonConfigurationProperties.getTlsTrustStorePassword()).thenReturn("dsb123");
    when(commonConfigurationProperties.isTlsCertRevocationEnableSoftFail()).thenReturn(true);
    when(commonConfigurationProperties.isTlsCertEnableOcsp()).thenReturn(true);
    TrustManager tm = dhruvaConfig.dsbTrustManager();
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
    when(commonConfigurationProperties.isEnableCertService()).thenReturn(false);
    when(commonConfigurationProperties.getTlsAuthType()).thenReturn(TLSAuthenticationType.MTLS);
    when(commonConfigurationProperties.getTlsTrustStoreFilePath()).thenReturn(keystorePath);
    when(commonConfigurationProperties.getTlsTrustStoreType()).thenReturn("jks");
    when(commonConfigurationProperties.getTlsTrustStorePassword()).thenReturn("dsb123");
    when(commonConfigurationProperties.isTlsCertRevocationEnableSoftFail()).thenReturn(true);
    when(commonConfigurationProperties.isTlsCertEnableOcsp()).thenReturn(true);
    TrustManager tm = dhruvaConfig.dsbTrustManager();
    Assert.assertNotNull(tm);
    Assert.assertTrue(tm instanceof DsbTrustManager);
    Assert.assertTrue(tm instanceof X509TrustManager);
    X509Certificate cert = CertUtil.pemToCert(("not_in_keystore.crt.pem"));

    X509Certificate[] certs = {cert};
    ((X509TrustManager) tm).checkClientTrusted(certs, "RSA");
  }

  @Test(description = "getCertTrustManager ")
  public void testCertTrustManager() throws Exception {
    when(commonConfigurationProperties.isEnableCertService()).thenReturn(true);
    when(commonConfigurationProperties.getTlsAuthType()).thenReturn(TLSAuthenticationType.MTLS);
    when(commonConfigurationProperties.getTlsTrustStoreFilePath()).thenReturn(keystorePath);
    when(commonConfigurationProperties.getTlsTrustStoreType()).thenReturn("jks");
    when(commonConfigurationProperties.getTlsTrustStorePassword()).thenReturn("dsb123");
    when(commonConfigurationProperties.isTlsCertRevocationEnableSoftFail()).thenReturn(true);
    when(commonConfigurationProperties.isTlsCertEnableOcsp()).thenReturn(true);
    CertTrustManagerFactory.setCertsX509TrustManager(mockCertsX509TrustManager);
    System.setProperty(DHRUVA_SERVICE_PASS, "dummyPass");
    System.setProperty(DHRUVA_SERVICE_USER, "dummyUserId");
    System.setProperty("dhruvaClientId", "dummyClientId");
    System.setProperty("dhruvaClientSecret", "dummyClientPassword");
    TrustManager tm = dhruvaConfig.dsbTrustManager();
    Assert.assertNotNull(tm);
    X509Certificate cert = CertUtil.pemToCert(("server.crt.pem"));

    X509Certificate[] certs = {cert};
    ((X509TrustManager) tm).checkClientTrusted(certs, "RSA");
    verify((X509TrustManager) mockCertsX509TrustManager, times(1)).checkClientTrusted(any(), any());
  }

  @Test(description = "test permissive trust manager creation")
  public void testPermissiveTrustManager() throws Exception {
    when(commonConfigurationProperties.isEnableCertService()).thenReturn(false);
    when(commonConfigurationProperties.getTlsAuthType()).thenReturn(TLSAuthenticationType.NONE);

    TrustManager tm = dhruvaConfig.dsbTrustManager();
    Assert.assertNotNull(tm);
    Assert.assertTrue(tm instanceof DsbTrustManager);
    Assert.assertTrue(tm instanceof X509TrustManager);
    X509Certificate[] certs = {CertUtil.pemToCert("not_in_keystore.crt.pem")};
    ((X509TrustManager) tm).checkClientTrusted(certs, "");
  }

  @Test(
      description = "testing for null keymanager and trustManager",
      expectedExceptions = {IllegalArgumentException.class})
  public void testForNullValues() throws Exception {
    DsbNetworkLayer dsbNetworkLayer = new DsbNetworkLayer();
    dsbNetworkLayer.init(null, null);
  }
}
