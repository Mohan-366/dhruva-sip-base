package com.cisco.dsb.common.sip.tls;

import static org.mockito.Mockito.when;

import com.cisco.dsb.common.config.sip.CommonConfigurationProperties;
import com.cisco.dsb.common.sip.tls.DsbNetworkLayer.KeyStoreInfo;
import javax.net.ssl.KeyManager;
import javax.net.ssl.X509ExtendedKeyManager;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.testng.Assert;
import org.testng.annotations.BeforeTest;
import org.testng.annotations.Test;

public class KeyManagerTest {

  @Mock CommonConfigurationProperties commonConfigurationProperties;

  @BeforeTest
  public void before() {
    MockitoAnnotations.initMocks(this);
  }

  @Test
  public void testKeyStoreCreation() {
    String keystoreFile =
        KeyManagerTest.class.getClassLoader().getResource("keystore.jks").getPath();
    String keystorePassword = "dsb123";
    String keystoreType = "jks";
    when(commonConfigurationProperties.getTlsKeyStoreFilePath()).thenReturn(keystoreFile);
    when(commonConfigurationProperties.getTlsKeyStorePassword()).thenReturn(keystorePassword);
    when(commonConfigurationProperties.getTlsKeyStoreType()).thenReturn(keystoreType);
    KeyStoreInfo keyStoreInfo = DsbNetworkLayer.createKeyStore(commonConfigurationProperties);
    Assert.assertEquals(keyStoreInfo.password, keystorePassword);
  }

  @Test
  public void testKeyManager() {
    String keystoreFile =
        KeyManagerTest.class.getClassLoader().getResource("keystore.jks").getPath();
    String keystorePassword = "dsb123";
    String keystoreType = "jks";
    when(commonConfigurationProperties.getTlsKeyStoreFilePath()).thenReturn(keystoreFile);
    when(commonConfigurationProperties.getTlsKeyStorePassword()).thenReturn(keystorePassword);
    when(commonConfigurationProperties.getTlsKeyStoreType()).thenReturn(keystoreType);
    KeyManager keyManager = DsbNetworkLayer.createKeyManager(commonConfigurationProperties);
    Assert.assertTrue(keyManager instanceof X509ExtendedKeyManager);
  }
}
