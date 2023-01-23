package com.cisco.dsb.common.sip.tls;

import com.cisco.dsb.common.config.sip.CommonConfigurationProperties;
import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import gov.nist.core.net.NetworkLayer;
import gov.nist.javax.sip.SipStackImpl;
import gov.nist.javax.sip.stack.SIPTransactionStack;
import java.io.IOException;
import java.net.*;
import java.security.SecureRandom;
import javax.net.ssl.KeyManager;
import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLServerSocket;
import javax.net.ssl.SSLServerSocketFactory;
import javax.net.ssl.SSLSocket;
import javax.net.ssl.SSLSocketFactory;
import javax.net.ssl.TrustManager;
import lombok.CustomLog;
import lombok.NonNull;

@CustomLog
public class DsbNetworkLayer implements NetworkLayer {
  private SSLSocketFactory sslSocketFactory;
  private SSLServerSocketFactory sslServerSocketFactory;
  private static int trafficClass = 0x60; // Match traffic classification as CUCM
  // Default connection timeout milliseconds.
  private int connectionTimeout = CommonConfigurationProperties.getSocketConnectionTimeout();
  protected SIPTransactionStack sipStack;

  public DsbNetworkLayer() {}

  public void init(@NonNull TrustManager trustManager, @NonNull KeyManager keyManager)
      throws Exception {
    SecureRandom secureRandom = new SecureRandom();
    secureRandom.nextInt();

    SSLContext sslContext = SSLContext.getInstance("TLS");
    sslContext.init(new KeyManager[] {keyManager}, new TrustManager[] {trustManager}, secureRandom);

    sslServerSocketFactory = sslContext.getServerSocketFactory();
    sslSocketFactory = sslContext.getSocketFactory();

    logger.info("initialized with TrustManager: {}", trustManager.getClass());
  }

  @Override
  @SuppressFBWarnings(value = "UNENCRYPTED_SERVER_SOCKET", justification = "baseline suppression")
  public ServerSocket createServerSocket(int port, int backlog, InetAddress bindAddress)
      throws IOException {

    ServerSocket serverSocket = setServerSocketOptions(new DsbServerSocket());
    serverSocket.bind(new InetSocketAddress(bindAddress, port), backlog);
    return serverSocket;
  }

  @SuppressFBWarnings(value = "UNENCRYPTED_SERVER_SOCKET", justification = "baseline suppression")
  static class DsbServerSocket extends ServerSocket {
    public DsbServerSocket(int port, int backlog, InetAddress bindAddr) throws IOException {
      super(port, backlog, bindAddr);
    }

    public DsbServerSocket() throws IOException {
      super();
    }

    @Override
    public Socket accept() throws IOException {
      return setSocketOptions(super.accept());
    }
  }

  private DatagramSocket createDataGramSocket() throws SocketException {
    DatagramSocket socket = new DatagramSocket(null);

    return setDatagramSocketOptions(socket);
  }

  @Override
  public DatagramSocket createDatagramSocket() throws SocketException {
    DatagramSocket socket = createDataGramSocket();
    socket.bind(new InetSocketAddress(0));
    return socket;
  }

  @Override
  public DatagramSocket createDatagramSocket(int port, InetAddress laddr) throws SocketException {
    DatagramSocket socket = createDataGramSocket();
    socket.bind(new InetSocketAddress(laddr, port));
    return socket;
  }

  @Override
  public SSLServerSocket createSSLServerSocket(int port, int backlog, InetAddress bindAddress)
      throws IOException {
    SSLServerSocket sslServerSocket = (SSLServerSocket) sslServerSocketFactory.createServerSocket();
    setServerSocketOptions(sslServerSocket);
    sslServerSocket.bind(new InetSocketAddress(bindAddress, port), backlog);
    return sslServerSocket;
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

  private static ServerSocket setServerSocketOptions(ServerSocket serverSocket) throws IOException {
    // NOTE: some of the properties set here can be overridden by
    // ConnectionOrientedMessageProcessor.
    // check out the start() to see such properties
    serverSocket.setOption(StandardSocketOptions.SO_REUSEPORT, true);
    return serverSocket;
  }

  private static DatagramSocket setDatagramSocketOptions(DatagramSocket datagramSocket)
      throws SocketException {
    datagramSocket.setTrafficClass(trafficClass);
    datagramSocket.setReuseAddress(true);
    return datagramSocket;
  }

  @Override
  public void setSipStack(SipStackImpl sipStackImpl) {
    this.sipStack = sipStackImpl;
  }
}
