package com.cisco.dsb.common.transport;

import static org.mockito.Mockito.mock;

import com.cisco.wx2.certs.client.CertsClientFactory;
import com.cisco.wx2.certs.client.CertsX509TrustManager;
import com.cisco.wx2.certs.common.util.RevocationManager;
import com.cisco.wx2.server.organization.OrganizationCollectionCache;
import com.cisco.wx2.util.SslUtil;
import java.security.KeyStore;
import java.security.KeyStoreException;
import java.security.NoSuchAlgorithmException;
import java.util.Optional;
import java.util.concurrent.TimeUnit;
import javax.net.ssl.TrustManagerFactory;
import javax.net.ssl.X509TrustManager;
import org.testng.Assert;
import org.testng.annotations.Test;

public class TrustManagerProviderTest {

  @Test(description = "fetch system trust manager of a trust manager factory")
  public void testSystemTrustManager() throws NoSuchAlgorithmException, KeyStoreException {
    String algorithm = TrustManagerFactory.getDefaultAlgorithm(); // algorithm = PKIX
    TrustManagerFactory tmf = TrustManagerFactory.getInstance(algorithm);
    tmf.init(
        (KeyStore) null); // creates an instance of X509TrustManager (this is also the system trust
    // manager)

    Optional<X509TrustManager> tm = TrustManagerProvider.getSystemTrustManager(tmf);
    Assert.assertNotNull(tm);
  }

  @Test(description = "test cert trust manager creation")
  public void testCertTrustManager() {
    CertsX509TrustManager ctm =
        TrustManagerProvider.getCertsX509TrustManager(
            mock(CertsClientFactory.class),
            mock(OrganizationCollectionCache.class),
            mock(RevocationManager.class),
            0,
            TimeUnit.MILLISECONDS,
            0,
            false);
    Assert.assertNotNull(ctm);
  }

  @Test(description = "test permissive trust manager creation")
  public void testPermissiveTrustManager() {
    SslUtil.PermissiveTrustManager ptm = TrustManagerProvider.getSslUtilPermissiveTrustManager();
    Assert.assertNotNull(ptm);
  }
}
