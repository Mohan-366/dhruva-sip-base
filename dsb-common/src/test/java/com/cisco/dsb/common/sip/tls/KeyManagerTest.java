package com.cisco.dsb.common.sip.tls;

import static org.mockito.Mockito.when;

import com.cisco.dsb.common.config.sip.DhruvaSIPConfigProperties;
import com.cisco.dsb.common.sip.tls.DsbNetworkLayer.KeyStoreInfo;
import javax.net.ssl.KeyManager;
import javax.net.ssl.X509ExtendedKeyManager;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.testng.Assert;
import org.testng.annotations.BeforeTest;
import org.testng.annotations.Test;

public class KeyManagerTest {

  @Mock DhruvaSIPConfigProperties dhruvaSIPConfigProperties;

  @BeforeTest
  public void before() {
    MockitoAnnotations.initMocks(this);
  }

  @Test
  public void testKeyStoreCreation() {
    String keystoreFile =
        Thread.currentThread().getContextClassLoader().getResource("keystore.jks").getPath();
    String keystorePassword = "dsb123";
    String keystoreType = "jks";
    when(dhruvaSIPConfigProperties.getKeyStoreFilePath()).thenReturn(keystoreFile);
    when(dhruvaSIPConfigProperties.getKeyStorePassword()).thenReturn(keystorePassword);
    when(dhruvaSIPConfigProperties.getKeyStoreType()).thenReturn(keystoreType);
    KeyStoreInfo keyStoreInfo = DsbNetworkLayer.createKeyStore(dhruvaSIPConfigProperties);
    Assert.assertEquals(keyStoreInfo.password, keystorePassword);
  }

  @Test
  public void testKeyManager() {
    String keystoreFile =
        Thread.currentThread().getContextClassLoader().getResource("keystore.jks").getPath();
    String keystorePassword = "dsb123";
    String keystoreType = "jks";
    when(dhruvaSIPConfigProperties.getKeyStoreFilePath()).thenReturn(keystoreFile);
    when(dhruvaSIPConfigProperties.getKeyStorePassword()).thenReturn(keystorePassword);
    when(dhruvaSIPConfigProperties.getKeyStoreType()).thenReturn(keystoreType);
    KeyManager keyManager = DsbNetworkLayer.createKeyManager(dhruvaSIPConfigProperties);
    Assert.assertTrue(keyManager instanceof X509ExtendedKeyManager);
  }
}
