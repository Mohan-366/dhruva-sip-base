package com.cisco.dsb.common.transport;

import java.security.KeyStoreException;
import java.security.NoSuchAlgorithmException;
import java.security.UnrecoverableKeyException;
import java.util.Optional;
import javax.net.ssl.KeyManager;
import javax.net.ssl.KeyManagerFactory;
import org.testng.Assert;
import org.testng.annotations.Test;

public class KeyManagerProviderTest {

  @Test(description = "fetch key managers for default key manager factory")
  public void testKeyManagerFactoryWithKeyManager()
      throws NoSuchAlgorithmException, UnrecoverableKeyException, KeyStoreException {
    String algorithm = KeyManagerFactory.getDefaultAlgorithm(); // algorithm = SunX509
    KeyManagerFactory kmf = KeyManagerFactory.getInstance(algorithm);
    kmf.init(null, null); // creates an instance SunX509KeyManagerImpl in this scenario

    Optional<KeyManager> km = KeyManagerProvider.getKeyManager(kmf);
    Assert.assertTrue(km.isPresent());
  }
}
