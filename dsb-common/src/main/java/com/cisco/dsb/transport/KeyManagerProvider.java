package com.cisco.dsb.transport;

import java.util.Objects;
import java.util.Optional;
import javax.annotation.Nonnull;
import javax.net.ssl.KeyManager;
import javax.net.ssl.KeyManagerFactory;

public class KeyManagerProvider {

  /**
   * Given a KeyManagerFactory, list of key managers(if present) are fetched
   *
   * @param kmf - should be created & initialised(using kmf.init()) by the implementing apps before
   *     invoking this method. If it is not initialised, then kmf.getKeyManagers() will throw an
   *     'IllegalStateException: KeyManagerFactoryImpl is not initialized'
   * @return all/no KeyManagers wrapped in an Optional
   */
  public static Optional<KeyManager[]> getAllKeyManagers(@Nonnull KeyManagerFactory kmf) {
    Objects.requireNonNull(kmf, "Input KeyManagerFactory is null");
    return Optional.of(kmf.getKeyManagers());
  }

  /**
   * Given a KeyManagerFactory, first key manager from the list(if present) is returned
   *
   * @param kmf - should be created & initialised(using kmf.init()) by the implementing apps before
   *     invoking this method. If it is not initialised, then kmf.getKeyManagers() will throw an
   *     'IllegalStateException: KeyManagerFactoryImpl is not initialized'
   * @return first keyManager from the list/empty KeyManager wrapped in an Optional
   */
  public static Optional<KeyManager> getKeyManager(@Nonnull KeyManagerFactory kmf) {
    Objects.requireNonNull(kmf, "Input KeyManagerFactory is null");

    Optional<KeyManager[]> kms = KeyManagerProvider.getAllKeyManagers(kmf);
    if (!kms.isPresent() || kms.get().length == 0) {
      return Optional.empty();
    }
    return Optional.of(kms.get()[0]);
  }
}
