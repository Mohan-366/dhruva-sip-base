package com.cisco.dsb.common.sip.tls;

import gov.nist.core.net.SecurityManagerProvider;
import java.util.Properties;
import javax.net.ssl.KeyManager;
import javax.net.ssl.TrustManager;

public class DsbSecurityManagerProvider implements SecurityManagerProvider {

  private KeyManager[] keyManagers;

  private TrustManager[] trustManagers;

  public DsbSecurityManagerProvider() {}

  /** The default JAIN SIP initializer method does nothing. */
  @Override
  public void init(Properties properties) {}

  /** Application specific initializer method. */
  public void init(TrustManager trustManager, KeyManager keyManager) {
    this.keyManagers = new KeyManager[] {keyManager};
    this.trustManagers = new TrustManager[] {trustManager};
  }

  @Override
  public KeyManager[] getKeyManagers(boolean client) {
    return keyManagers;
  }

  @Override
  public TrustManager[] getTrustManagers(boolean client) {
    return trustManagers;
  }
}
