package com.cisco.dsb.common.sip.tls;

import com.cisco.dsb.common.config.sip.DhruvaSIPConfigProperties;
import com.google.common.base.Strings;
import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import gov.nist.core.net.NetworkLayer;
import gov.nist.javax.sip.SipStackImpl;
import gov.nist.javax.sip.stack.SIPTransactionStack;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.ConnectException;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.SocketException;
import java.net.SocketTimeoutException;
import java.security.GeneralSecurityException;
import java.security.KeyStore;
import java.security.SecureRandom;
import java.util.Objects;
import javax.net.ssl.KeyManager;
import javax.net.ssl.KeyManagerFactory;
import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLServerSocket;
import javax.net.ssl.SSLServerSocketFactory;
import javax.net.ssl.SSLSocket;
import javax.net.ssl.SSLSocketFactory;
import javax.net.ssl.TrustManager;
import lombok.CustomLog;
import org.jetbrains.annotations.NotNull;

@CustomLog
public class DsbNetworkLayer implements NetworkLayer {
  private SSLSocketFactory sslSocketFactory;
  private SSLServerSocketFactory sslServerSocketFactory;
  private static int trafficClass = 0x60; // Match traffic classification as CUCM
  // Default connection timeout milliseconds.
  private int connectionTimeout = DhruvaSIPConfigProperties.getSocketConnectionTimeout();
  protected SIPTransactionStack sipStack;

  public DsbNetworkLayer() {}

  public void init(@NotNull TrustManager trustManager, @NotNull KeyManager keyManager)
      throws Exception {

    Objects.requireNonNull(trustManager, "trustManager must not be null");
    Objects.requireNonNull(keyManager, "keyManager must not be null");
    SecureRandom secureRandom = new SecureRandom();
    secureRandom.nextInt();

    SSLContext sslContext = SSLContext.getInstance("TLS");
    sslContext.init(new KeyManager[] {keyManager}, new TrustManager[] {trustManager}, secureRandom);

    sslServerSocketFactory = sslContext.getServerSocketFactory();
    sslSocketFactory = sslContext.getSocketFactory();

    logger.info("initialized with TrustManager: {}", trustManager.getClass());
  }

  public enum KeyStoreType {
    KEY_STORE_LOADED_FROM_ENV,
    KEY_STORE_LOADED_FROM_FILE,
    KEY_STORE_BUILT_FROM_PEM,
    DEFAULT
  }

  public static class KeyStoreInfo {
    public KeyStore keyStore;
    public KeyStoreType keyStoreType;
    public String password;

    public KeyStoreInfo(KeyStore keyStore, KeyStoreType keyStoreType, String password) {
      this.keyStore = keyStore;
      this.keyStoreType = keyStoreType;
      this.password = password;
    }
  }

  @SuppressFBWarnings(
      value = {"HARD_CODE_PASSWORD", "PATH_TRAVERSAL_IN"},
      justification = "baseline suppression")
  public static KeyStoreInfo createKeyStore(DhruvaSIPConfigProperties props) {
    // Key store loaded from key store file (or base 64 encoded file loaded in environment variable)
    // Production deployment should use key store file (PKCS12 or JKS), base 64 encoded
    final String keyStoreFilename = props.getKeyStoreFilePath();
    final String keyStorePassword = props.getKeyStorePassword();
    final String keyStoreType = props.getKeyStoreType();

    KeyStoreInfo keyStoreLoadedFromFile = null;
    if (!Strings.isNullOrEmpty(keyStoreFilename)
        && !Strings.isNullOrEmpty(keyStoreFilename)
        && !Strings.isNullOrEmpty(keyStorePassword)
        && !Strings.isNullOrEmpty(keyStoreType)) {
      try (InputStream file = new FileInputStream(new File(keyStoreFilename))) {
        KeyStore keyStore = KeyStore.getInstance(keyStoreType);
        keyStore.load(file, keyStorePassword.toCharArray());
        keyStoreLoadedFromFile =
            new KeyStoreInfo(keyStore, KeyStoreType.KEY_STORE_LOADED_FROM_ENV, keyStorePassword);
        logger.info(
            "Loaded key store of type {} from file {}. Entries: {}",
            keyStoreType,
            keyStoreFilename,
            keyStore.size());
      } catch (IOException | GeneralSecurityException | RuntimeException e) {
        logger.warn("Failed to load key store from environment, base 64 encoded data", e);
        keyStoreLoadedFromFile = null;
      }
    } else {
      logger.error(
          "Could not create keystore from file: {} of type {}", keyStoreFilename, keyStoreType);
    }
    return keyStoreLoadedFromFile;
  }

  public static KeyManager createKeyManager(DhruvaSIPConfigProperties sipProperties) {
    DsbNetworkLayer.KeyStoreInfo info = DsbNetworkLayer.createKeyStore(sipProperties);
    if (info == null) {
      logger.error("Could not create keymanager as keystore is null. Returning null");
      return null;
    }
    return DsbNetworkLayer.createKeyManager(info.keyStore, info.password);
  }

  public static KeyManager createKeyManager(KeyStore keyStore, String password) {
    KeyManagerFactory keyManagerFactory;
    try {
      keyManagerFactory = KeyManagerFactory.getInstance(KeyManagerFactory.getDefaultAlgorithm());
      keyManagerFactory.init(keyStore, password.toCharArray());
      logger.info("Key manager factory initialized. SIP TLS settings OK.");
    } catch (GeneralSecurityException e) {
      logger.warn("Failed to initialize key manager factory", e);
      keyManagerFactory = null;
    }

    KeyManager[] keyManager = keyManagerFactory == null ? null : keyManagerFactory.getKeyManagers();
    if (keyManager == null || keyManager.length == 0) {
      throw new IllegalStateException("Unable to create SIP TLS key manager");
    }

    if (keyManager.length > 1) {
      logger.warn("More than one possible SIP key. Choosing the first one.");
    }
    return keyManager[0];
  }

  @Override
  @SuppressFBWarnings(value = "UNENCRYPTED_SERVER_SOCKET", justification = "baseline suppression")
  public ServerSocket createServerSocket(int port, int backlog, InetAddress bindAddress)
      throws IOException {

    return new DsbServerSocket(port, backlog, bindAddress);
  }

  @SuppressFBWarnings(value = "UNENCRYPTED_SERVER_SOCKET", justification = "baseline suppression")
  static class DsbServerSocket extends ServerSocket {
    public DsbServerSocket(int port, int backlog, InetAddress bindAddr) throws IOException {
      super(port, backlog, bindAddr);
    }

    @Override
    public Socket accept() throws IOException {
      return setSocketOptions(super.accept());
    }
  }

  private DatagramSocket createDataGramSocket(int port, InetAddress address)
      throws SocketException {
    DatagramSocket socket =
        port >= 0 && address != null ? new DatagramSocket(port, address) : new DatagramSocket();
    socket.setTrafficClass(trafficClass);
    return socket;
  }

  @Override
  public DatagramSocket createDatagramSocket() throws SocketException {
    return createDataGramSocket(-1, null);
  }

  @Override
  public DatagramSocket createDatagramSocket(int port, InetAddress laddr) throws SocketException {
    return createDataGramSocket(port, laddr);
  }

  @Override
  public SSLServerSocket createSSLServerSocket(int port, int backlog, InetAddress bindAddress)
      throws IOException {
    return (SSLServerSocket) sslServerSocketFactory.createServerSocket(port, backlog, bindAddress);
  }

  @Override
  public SSLSocket createSSLSocket(InetAddress address, int port) throws IOException {
    return createSSLSocket(address, port, null);
  }

  @Override
  public SSLSocket createSSLSocket(InetAddress address, int port, InetAddress myAddress)
      throws IOException {
    return (SSLSocket) connectSocket(sslSocketFactory.createSocket(), address, port, myAddress, 0);
  }

  @Override
  public Socket createSocket(InetAddress address, int port) throws IOException {
    return createSocket(address, port, null, 0);
  }

  @Override
  public Socket createSocket(InetAddress address, int port, InetAddress myAddress)
      throws IOException {
    return createSocket(address, port, myAddress, 0);
  }

  /**
   * Creates a new Socket, binds it to myAddress:myPort and connects it to address:port.
   *
   * @param address the InetAddress that we'd like to connect to.
   * @param port the port that we'd like to connect to
   * @param myAddress the address that we are supposed to bind on or null for the "any" address.
   * @param myPort the port that we are supposed to bind on or 0 for a random one.
   * @return a new Socket, bound on myAddress:myPort and connected to address:port.
   * @throws IOException if binding or connecting the socket fail for a reason (exception relayed
   *     from the corresponding Socket methods)
   */
  @Override
  @SuppressFBWarnings(value = "UNENCRYPTED_SOCKET", justification = "baseline suppression")
  public Socket createSocket(InetAddress address, int port, InetAddress myAddress, int myPort)
      throws IOException {
    return connectSocket(new Socket(), address, port, myAddress, myPort);
  }

  private Socket connectSocket(
      Socket socket, InetAddress address, int port, InetAddress myAddress, int myPort)
      throws IOException {
    setSocketOptions(socket);
    // Valid myPort value is between 0 and 65535, a null myAddress will assign the wildcard address
    socket.bind(new InetSocketAddress(myAddress, myPort));

    try {
      InetSocketAddress remoteAddress = new InetSocketAddress(address, port);
      socket.connect(remoteAddress, connectionTimeout);
      logger.info("Socket connected from {} to {}", socket.getLocalSocketAddress(), remoteAddress);
    } catch (SocketTimeoutException e) {
      throw new ConnectException(
          "Socket timeout error (" + connectionTimeout + " milliSeconds)" + address + ":" + port);
    }
    return socket;
  }

  private static Socket setSocketOptions(Socket socket) throws SocketException {
    socket.setTcpNoDelay(true);
    socket.setTrafficClass(trafficClass);
    return socket;
  }

  @Override
  public void setSipStack(SipStackImpl sipStackImpl) {
    this.sipStack = sipStackImpl;
  }
}
