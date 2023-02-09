package com.cisco.dsb.common.sip.tls;

import static com.cisco.dsb.common.sip.tls.CertServiceTrustManagerProperties.DHRUVA_SERVICE_PASS;
import static com.cisco.dsb.common.sip.tls.CertServiceTrustManagerProperties.DHRUVA_SERVICE_USER;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;
import static org.testng.Assert.*;

import com.cisco.dsb.common.config.CertConfigurationProperties;
import com.cisco.dsb.common.config.TruststoreConfigurationProperties;
import com.cisco.dsb.common.config.sip.CommonConfigurationProperties;
import com.cisco.wx2.certs.client.CertsX509TrustManager;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.security.PrivilegedActionException;
import java.security.cert.CertificateException;
import java.security.cert.X509Certificate;
import javax.net.ssl.KeyManager;
import javax.net.ssl.X509TrustManager;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.BeforeTest;
import org.testng.annotations.DataProvider;
import org.testng.annotations.Test;

public class DsbTrustManagerTest {
  @Mock X509TrustManager x509TrustManager;
  private DsbTrustManagerFactory dsbTrustManagerFactory;
  private CommonConfigurationProperties commonConfigurationProperties;
  private TruststoreConfigurationProperties truststoreConfigurationProperties;
  private String keyStorePath;

  @DataProvider
  public static Object[][] getRevocation() {
    // format = {revocationEnabled,softfailEnabled}
    return new Object[][] {{true, true}, {true, false}, {false, true}, {false, false}};
  }

  @BeforeTest
  public void init() {
    MockitoAnnotations.openMocks(this);
    keyStorePath = DsbTrustManagerTest.class.getClassLoader().getResource("ts.p12").getPath();
    truststoreConfigurationProperties = new TruststoreConfigurationProperties();
    truststoreConfigurationProperties.setTrustStoreFilePath(keyStorePath);
    truststoreConfigurationProperties.setTrustStoreType("pkcs12");
    truststoreConfigurationProperties.setTrustStorePassword("dsb123");
    truststoreConfigurationProperties.setKeyStoreFilePath(keyStorePath);
    truststoreConfigurationProperties.setKeyStoreType("pkcs12");
    commonConfigurationProperties = new CommonConfigurationProperties();
    commonConfigurationProperties.setTruststoreConfiguration(truststoreConfigurationProperties);
    dsbTrustManagerFactory = new DsbTrustManagerFactory();
    dsbTrustManagerFactory.setCommonConfigurationProperties(commonConfigurationProperties);
  }

  @BeforeMethod
  public void setup() {
    reset(x509TrustManager);
  }

  @Test(
      description =
          "fetch system trust manager of a trust manager factory and check for valid certificate in keystore",
      dataProvider = "getRevocation")
  public void testSystemTrustManagerRevocation(boolean revoke, boolean softfail) throws Exception {
    CertConfigurationProperties certConfigurationProperties = new CertConfigurationProperties();
    certConfigurationProperties.setRevocationCheck(revoke);
    certConfigurationProperties.setRevocationSoftfail(softfail);
    certConfigurationProperties.setOcsp(false);
    DsbTrustManager tm = dsbTrustManagerFactory.getDsbTrustManager(certConfigurationProperties);
    X509Certificate cert = CertUtil.pemToCert(("cert.crt"));

    X509Certificate[] certs = {cert};
    boolean revokeException = false;
    try {
      tm.checkClientTrusted(certs, "RSA");
      tm.checkServerTrusted(certs, "RSA");
    } catch (CertificateException ex) {
      if (ex.getMessage().contains("revocation")) {
        revokeException = true;
      }
    }

    assertEquals(revokeException, revoke && !softfail);
  }

  @Test(
      description = "get systemTrustManager and check for invalid date in cert in keystore",
      expectedExceptions = {CertificateException.class})
  public void testSystemTrustManagerForInvalidDateInCertificate() throws Exception {
    DsbTrustManager tm = dsbTrustManagerFactory.getDsbTrustManager();
    X509Certificate cert = CertUtil.pemToCert(("expired_cert.crt"));

    X509Certificate[] certs = {cert};
    tm.checkServerTrusted(certs, "RSA");
  }

  @Test(
      description = "get systemTrustManager and check for certificate not in keystore",
      expectedExceptions = {CertificateException.class})
  public void testSystemTrustManagerForUntrustedCertificate() throws Exception {
    DsbTrustManager tm = dsbTrustManagerFactory.getDsbTrustManager();
    X509Certificate cert = CertUtil.pemToCert(("untrusted.crt"));

    X509Certificate[] certs = {cert};
    tm.checkClientTrusted(certs, "RSA");
  }

  @DataProvider
  public Object[][] getOCSP() {
    return new Object[][] {{true}, {false}};
  }

  @Test(description = "OCSP revocation check", dataProvider = "getOCSP")
  public void testOCSPValidation(boolean ocsp) throws Exception {
    CertConfigurationProperties certConfigurationProperties = new CertConfigurationProperties();
    certConfigurationProperties.setRevocationCheck(true);
    certConfigurationProperties.setOcsp(ocsp);
    DsbTrustManager tm = dsbTrustManagerFactory.getDsbTrustManager(certConfigurationProperties);
    X509Certificate cert = CertUtil.pemToCert(("cert.crt"));

    X509Certificate[] certs = {cert};
    boolean crlException = false;
    boolean ocspException = false;
    try {
      tm.checkServerTrusted(certs, "RSA");
    } catch (CertificateException ex) {
      if (ocsp && ex.getCause() != null && ex.getCause().getMessage().contains("OCSP"))
        ocspException = true;
      else if (ex.getMessage().contains("revocation")) crlException = true;
    }

    assertFalse(crlException);
    assertEquals(ocspException, ocsp);
  }

  @Test(description = "getCertTrustManager ")
  public void testCertServiceTrustManager() throws Exception {
    System.setProperty(DHRUVA_SERVICE_PASS, "dummyPass");
    System.setProperty(DHRUVA_SERVICE_USER, "dummyUserId");
    System.setProperty("dhruvaClientId", "dummyClientId");
    System.setProperty("dhruvaClientSecret", "dummyClientPassword");
    TruststoreConfigurationProperties truststoreConfigurationProperties =
        new TruststoreConfigurationProperties();
    truststoreConfigurationProperties.setEnableCertService(true);
    CommonConfigurationProperties commonConfigurationProperties =
        new CommonConfigurationProperties();
    commonConfigurationProperties.setTruststoreConfiguration(truststoreConfigurationProperties);
    DsbTrustManagerFactory dsbTrustManagerFactory = new DsbTrustManagerFactory();
    dsbTrustManagerFactory.setCommonConfigurationProperties(commonConfigurationProperties);
    dsbTrustManagerFactory.setCertTrustManagerProperties(new CertServiceTrustManagerProperties());
    DsbTrustManager trustManager = dsbTrustManagerFactory.getDsbTrustManager();

    assertTrue(trustManager.trustManager instanceof CertsX509TrustManager);
  }

  @Test(
      description = "testing for null commonConfigurationProperties",
      expectedExceptions = {NullPointerException.class})
  public void testForNullValues() throws Exception {
    DsbNetworkLayer dsbNetworkLayer = new DsbNetworkLayer();
    dsbNetworkLayer.init(null, null, null);
  }

  @Test
  public void testAllPermissive() throws CertificateException {
    DsbTrustManager dsbTrustManager = new DsbTrustManager(null);
    X509Certificate[] certs = {CertUtil.pemToCert("untrusted.crt")};
    dsbTrustManager.checkClientTrusted(certs, "");
    dsbTrustManager.checkServerTrusted(certs, "");
  }

  @DataProvider
  private Object[][] getValidations() {
    return new Object[][] {{true}, {false}};
  }

  @Test(
      description = "Test for certificate exception propagation.",
      dataProvider = "getValidations")
  public void testCertValidation(boolean valid) throws CertificateException {
    doAnswer(
            invocationOnMock -> {
              if (!valid) throw new CertificateException("Invalid Client Certificate");
              return null;
            })
        .when(x509TrustManager)
        .checkClientTrusted(any(), anyString());
    doAnswer(
            invocationOnMock -> {
              if (!valid) throw new CertificateException("Invalid Server Certificate");
              return null;
            })
        .when(x509TrustManager)
        .checkServerTrusted(any(), anyString());
    DsbTrustManager dsbTrustManager = new DsbTrustManager(x509TrustManager);
    X509Certificate[] certs = {CertUtil.pemToCert("untrusted.crt")};
    boolean gotClientException = false, gotServerException = false;
    try {
      dsbTrustManager.checkClientTrusted(certs, "");
    } catch (CertificateException ex) {
      gotClientException = true;
    }

    try {
      dsbTrustManager.checkServerTrusted(certs, "");
    } catch (CertificateException ex) {
      gotServerException = true;
    }
    assertEquals(!valid, gotClientException);
    assertEquals(!valid, gotServerException);
  }

  @Test(description = "Creation of Keymanager")
  public void testKeyManager() throws Exception {
    truststoreConfigurationProperties.setKeyStorePassword("dsb123");
    truststoreConfigurationProperties.setKeyStoreFilePath(keyStorePath);
    KeyManager keyManager = dsbTrustManagerFactory.getKeyManager();
    assertNotNull(keyManager);
  }

  @Test(description = "Testing various invalid scenarios while creating keystore")
  public void testKeyManagerFailure() throws Exception {
    truststoreConfigurationProperties.setKeyStorePassword("invalid");
    try {
      dsbTrustManagerFactory.getKeyManager();
      fail("Password is invalid, should throw IOException");
    } catch (IOException ioException) {
      if (!ioException.getMessage().contains("keystore password was incorrect")) {
        fail("IOException for incorrect password does not match");
      }
    }

    // invalid path
    truststoreConfigurationProperties.setKeyStoreFilePath("invalid");
    try {
      dsbTrustManagerFactory.getKeyManager();
      fail("Keystore path is invalid, should throw IOException");
    } catch (PrivilegedActionException privilegedActionException) {
      if (!(privilegedActionException.getCause() instanceof FileNotFoundException)) {
        fail("Exception for invalid file does not match", privilegedActionException.getCause());
      }
    }
  }
}
