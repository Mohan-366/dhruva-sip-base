package com.cisco.dsb.common.sip.tls;

import com.cisco.dsb.common.config.sip.DhruvaSIPConfigProperties;
import com.cisco.dsb.common.dto.TrustedSipSources;
import com.cisco.dsb.common.sip.dns.DnsCache;
import com.cisco.dsb.common.sip.stack.dto.DhruvaNetwork;
import com.cisco.dsb.common.sip.stack.dto.GlobalDnsCache;
import com.cisco.dsb.common.util.ThreadLocals;
import com.cisco.dsb.common.util.log.DhruvaLoggerFactory;
import com.cisco.dsb.common.util.log.Logger;
import com.cisco.wx2.certs.client.CertsClientFactory;
import com.cisco.wx2.certs.common.util.RevocationManager;
import com.cisco.wx2.server.organization.OrganizationCollectionCache;
import com.cisco.wx2.util.SslUtil;
import com.google.common.base.Strings;
import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.security.AccessController;
import java.security.KeyStore;
import java.security.PrivilegedExceptionAction;
import java.security.cert.*;
import java.security.cert.PKIXRevocationChecker.Option;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.concurrent.TimeUnit;
import javax.net.ssl.CertPathTrustManagerParameters;
import javax.net.ssl.SSLEngine;
import javax.net.ssl.TrustManager;
import javax.net.ssl.TrustManagerFactory;
import javax.net.ssl.X509ExtendedTrustManager;
import javax.net.ssl.X509TrustManager;
import org.jetbrains.annotations.NotNull;

public class DsbTrustManager extends X509ExtendedTrustManager {

  private static Logger logger = DhruvaLoggerFactory.getLogger(DsbTrustManager.class);
  private static DsbTrustManager trustAllTrustManagerInstance = new DsbTrustManager();
  private static String trustStoreFile;
  private static String trustStoreType;
  private static String trustStorePassword;
  private static String javaHome;
  private static boolean softFailEnabled;
  private static boolean enableOcsp;
  private X509TrustManager trustManager;
  private static DsbTrustManager systemTrustManager = null;
  private TrustedSipSources trustedSipSources = new TrustedSipSources();
  private boolean requireTrustedSipSources = false;

  public static void initTransportProperties(DhruvaSIPConfigProperties dhruvaSIPConfigProperties) {
    System.setProperty(
        "com.sun.security.ocsp.timeout",
        String.valueOf(dhruvaSIPConfigProperties.getOcspResponseTimeoutSeconds()));
    trustStoreFile = dhruvaSIPConfigProperties.getTrustStoreFilePath();
    trustStoreType = dhruvaSIPConfigProperties.getTrustStoreType();
    trustStorePassword = dhruvaSIPConfigProperties.getTrustStorePassword();
    softFailEnabled = dhruvaSIPConfigProperties.isTlsCertRevocationSoftFailEnabled();
    enableOcsp = dhruvaSIPConfigProperties.isTlsOcspEnabled();
    javaHome = System.getProperty("java.home");
  }

  public static DsbTrustManager createInstance(
      CertsClientFactory certsClientFactory,
      OrganizationCollectionCache orgCollectionCache,
      RevocationManager revocationManager,
      long revocationTimeout,
      TimeUnit revocationTimeoutUnit,
      long orgCacheSize) {
    return new DsbTrustManager(
        certsClientFactory,
        orgCollectionCache,
        revocationManager,
        revocationTimeout,
        revocationTimeoutUnit,
        orgCacheSize);
  }

  /** Creates an DsbTrustManager that uses CertsX509TrustManager. */
  public static DsbTrustManager createPermissiveInstance() {
    return new DsbTrustManager(new SslUtil.PermissiveTrustManager());
  }

  public static synchronized DsbTrustManager getSystemTrustManager() throws Exception {
    if (systemTrustManager == null) {
      systemTrustManager = createSystemTrustManager();
    }
    return systemTrustManager;
  }

  public DsbTrustManager(X509TrustManager trustManager) {
    this.trustManager = trustManager;
  }

  /**
   * Get an DsbTrustManager that uses that trusts all certificates and does NOT perform any hostname
   * or sipSource validation.
   */
  public static DsbTrustManager getTrustAllCertsInstance() {
    return trustAllTrustManagerInstance;
  }

  private DsbTrustManager() {}

  private static DsbTrustManager createSystemTrustManager() throws Exception {

    TrustManagerFactory tmf =
        TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm());
    KeyStore ts = getCacertsKeyStore();

    CertPathBuilder cpb = CertPathBuilder.getInstance("PKIX");
    PKIXRevocationChecker rc = (PKIXRevocationChecker) cpb.getRevocationChecker();
    rc.setOptions(getRevocationOptions());

    PKIXBuilderParameters pkixParams = new PKIXBuilderParameters(ts, new X509CertSelector());
    pkixParams.addCertPathChecker(rc);
    tmf.init(new CertPathTrustManagerParameters(pkixParams));
    TrustManager[] tms = tmf.getTrustManagers();

    for (TrustManager tm : tms) {
      if (tm instanceof X509TrustManager) {
        logger.info(
            "Initializing trust manager with {} certs as trust anchors",
            ((X509TrustManager) tm).getAcceptedIssuers().length);
        return new DsbTrustManager((X509TrustManager) tm);
      }
    }

    throw new RuntimeException("Unable to find system trust manager");
  }

  private DsbTrustManager(
      CertsClientFactory certsClientFactory,
      OrganizationCollectionCache orgCollectionCache,
      RevocationManager revocationManager,
      long revocationTimeout,
      TimeUnit revocationTimeoutUnit,
      long orgCacheSize) {
    this.trustManager =
        CertTrustManagerFactory.getCertsX509TrustManager(
            certsClientFactory,
            orgCollectionCache,
            revocationManager,
            revocationTimeout,
            revocationTimeoutUnit,
            orgCacheSize,
            false);
  }

  @NotNull
  private static EnumSet<Option> getRevocationOptions() {

    EnumSet<Option> options = EnumSet.of(Option.ONLY_END_ENTITY);

    if (softFailEnabled) {
      options.add(Option.SOFT_FAIL);
    }
    if (!enableOcsp) {
      options.add(Option.PREFER_CRLS);
    }
    return options;
  }

  @Override
  public void checkClientTrusted(X509Certificate[] chain, String authType, Socket socket)
      throws CertificateException {
    try {
      checkClientTrusted(chain, authType);
    } catch (CertificateException e) {
      // If a customer fails TLS certificate validation on inbound connections, this is the only log
      // we may see in Kibana,
      // so log the failure with information about the certificate and host.
      if (chain.length > 0) {
        throw new CertificateException(
            String.format(
                "Client certificate %s from host address %s failed certificate validation.",
                HostNameValidationHelper.toString(chain[0]),
                socket.getRemoteSocketAddress().toString()),
            e);
      }
      throw e;
    }
  }

  @Override
  public void checkServerTrusted(X509Certificate[] chain, String authType, Socket socket)
      throws CertificateException {
    final InetSocketAddress remoteAddress = (InetSocketAddress) socket.getRemoteSocketAddress();
    final String remoteAddressString = remoteAddress.getAddress().getHostAddress();
    checkServerTrusted(chain, authType, remoteAddressString, true);
  }

  @Override
  public void checkClientTrusted(X509Certificate[] chain, String authType, SSLEngine sslEngine)
      throws CertificateException {
    checkClientTrusted(chain, authType);
  }

  @Override
  public void checkServerTrusted(X509Certificate[] chain, String authType, SSLEngine sslEngine)
      throws CertificateException {
    checkServerTrusted(chain, authType, sslEngine.getPeerHost(), false);
  }

  public void checkServerTrusted(
      X509Certificate[] chain, String authType, String peerAddress, boolean useCallId)
      throws CertificateException {
    if (trustManager != null) {
      validateHostname(chain, peerAddress, useCallId);
    }
    try {
      checkServerTrusted(chain, authType);
    } catch (CertificateException e) {
      if (chain.length > 0) {
        throw new CertificateException(
            String.format(
                "Server certificate %s from host address %s failed certificate validation.",
                HostNameValidationHelper.toString(chain[0]), peerAddress),
            e);
      }
      throw e;
    }
  }

  @Override
  public void checkClientTrusted(X509Certificate[] x509Certificates, String s)
      throws CertificateException {
    if (this.trustManager == null) {
      return;
    }
    trustManager.checkClientTrusted(x509Certificates, s);
    //    for (X509Certificate cert : x509Certificates) {
    //      cert.checkValidity();
    //    }
  }

  @Override
  public void checkServerTrusted(X509Certificate[] chain, String authType)
      throws CertificateException {
    if (logger.isDebugEnabled()) {
      for (X509Certificate certificate : chain) {
        logger.debug(
            "SubjectDN: {}, IssuerDN: {}", certificate.getSubjectDN(), certificate.getIssuerDN());
      }
    }
    //    validateTrustedSipSources(chain);

    if (trustManager == null) {
      return;
    }

    trustManager.checkServerTrusted(chain, authType);
  }

  @Override
  public X509Certificate[] getAcceptedIssuers() {
    if (DhruvaNetwork.getIsAcceptedIssuersEnabled()) {
      return trustManager.getAcceptedIssuers();
    } else {
      return new X509Certificate[0];
    }
  }

  public TrustedSipSources getTrustedSipSources() {
    return trustedSipSources;
  }

  public boolean getRequireTrustedSipSources() {
    return requireTrustedSipSources;
  }

  @SuppressFBWarnings(value = {"PATH_TRAVERSAL_IN"})
  private static KeyStore getCacertsKeyStore() throws Exception {
    File storeFile;
    FileInputStream fis = null;
    final String sep = File.separator;
    KeyStore ks;
    try {
      if (trustStoreFile != null) {
        storeFile = new File(trustStoreFile);
        fis = getFileInputStream(storeFile);
      } else {
        storeFile = new File(javaHome + sep + "lib" + sep + "security" + sep + "jssecacerts");
        if ((fis = getFileInputStream(storeFile)) == null) {
          storeFile = new File(javaHome + sep + "lib" + sep + "security" + sep + "cacerts");
          fis = getFileInputStream(storeFile);
        }
      }

      if (fis != null) {
        trustStoreFile = storeFile.getPath();
      } else {
        trustStoreFile = "No File Available, using empty keystore.";
      }

      ks = KeyStore.getInstance(trustStoreType);

      char[] trustStorePass = null;
      if (trustStorePassword.length() != 0) {
        trustStorePass = trustStorePassword.toCharArray();
      }

      // if trustStoreFile is NONE, fis will be null
      ks.load(fis, trustStorePass);
    } finally {
      if (fis != null) {
        fis.close();
      }
    }

    return ks;
  }

  private static FileInputStream getFileInputStream(final File file) throws Exception {
    return AccessController.doPrivileged(
        (PrivilegedExceptionAction<FileInputStream>)
            () -> {
              try {
                if (file.exists()) {
                  return new FileInputStream(file);
                } else {
                  return null;
                }
              } catch (FileNotFoundException e) {
                return null;
              }
            });
  }

  private void validateHostname(X509Certificate[] chain, String address, boolean useCallId)
      throws CertificateException {
    validateChain(chain);

    // Perform hostname validation similar to HTTPS protocol for
    // https://cwe.mitre.org/data/definitions/297.html
    // As a side note, if we didn't implement our own TrustManager, we could use the super simple
    // method of enabling hostname
    // validation using the JSSE internal ExtendedTrustManager:
    // SSLParameters sslParams = new SSLParameters();
    // sslParams.setEndpointIdentificationAlgorithm("HTTPS");
    // sslSocket.setSSLParameters(sslParams);
    // Attempt to lookup hostnames from IP address from DNS cache
    List<String> hostnames = null;
    if (useCallId) {
      String callId = ThreadLocals.getCallId();
      if (Strings.isNullOrEmpty(callId)) {
        logger.info("Did not find any call ID for this connection. Skipping hostname validation.");
        return;
      }

      hostnames = DnsCache.getInstance().getHostNamesFromAddress(callId, address);
    } else {
      hostnames = GlobalDnsCache.getInstance().getHostNamesFromAddress(address);
    }

    if (hostnames == null || hostnames.isEmpty()) {
      logger.info(
          "Did not find any address names associated with IP {}. Skipping hostname validation.",
          address);
      return;
    }

    if (!HostNameValidationHelper.match(hostnames, chain[0])) {
      throw new CertificateException(
          "Hostname validation failed for hosts "
              + hostnames
              + ", and certificate "
              + HostNameValidationHelper.toString(chain[0]));
    }

    logger.debug(
        "Hostname validation passed for hosts {}, and certificate {}.",
        hostnames,
        HostNameValidationHelper.toString(chain[0]));
  }

  private void validateTrustedSipSources(X509Certificate[] chain) throws CertificateException {
    // Trust everything if no whitelist is provided and "requireTrustedSipSources" config is FALSE
    // This should always be true for non-media fusion nodes.
    if (trustedSipSources.isEmpty() && !requireTrustedSipSources) {
      return;
    }

    validateChain(chain);
    CertificateInfo certificateInfo = CertificateInfo.createFromX509Certificate(chain[0]);

    if (!HostNameValidationHelper.match(
        new ArrayList(trustedSipSources.getTrustedSipSources()), chain[0])) {
      logger.warn(
          "Rejected connection from untrusted source.  trustedSipSources: {}, certificate: {}",
          trustedSipSources,
          certificateInfo);

      throw new CertificateException(
          "Hostname validation failed for certificate " + certificateInfo);
    }
    logger.debug(
        "Trusting connection with  trustedSipSources: {}, certificate: {}",
        trustedSipSources,
        certificateInfo);
  }

  private void validateChain(X509Certificate[] chain) throws CertificateException {
    if (chain == null || chain.length == 0) {
      logger.warn("Empty certificate chain.");
      throw new CertificateException("Empty certificate chain.");
    }
  }
}
