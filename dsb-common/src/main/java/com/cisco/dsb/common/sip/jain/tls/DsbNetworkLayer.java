package com.cisco.dsb.common.sip.jain.tls;

import gov.nist.core.net.NetworkLayer;
import gov.nist.javax.sip.SipStackImpl;
import java.io.IOException;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.SocketException;
import javax.net.ssl.SSLServerSocket;
import javax.net.ssl.SSLSocket;

/**
 * A variation of gov.nist.core.net.SslNetworkLayer that allows for custom trustManager, e.g.
 * CertsX509TrustManager.
 */
public class DsbNetworkLayer implements NetworkLayer {

  @Override
  public ServerSocket createServerSocket(int i, int i1, InetAddress inetAddress)
      throws IOException {
    return null;
  }

  @Override
  public SSLServerSocket createSSLServerSocket(int i, int i1, InetAddress inetAddress)
      throws IOException {
    return null;
  }

  @Override
  public Socket createSocket(InetAddress inetAddress, int i) throws IOException {
    return null;
  }

  @Override
  public Socket createSocket(InetAddress inetAddress, int i, InetAddress inetAddress1)
      throws IOException {
    return null;
  }

  @Override
  public Socket createSocket(InetAddress inetAddress, int i, InetAddress inetAddress1, int i1)
      throws IOException {
    return null;
  }

  @Override
  public SSLSocket createSSLSocket(InetAddress inetAddress, int i) throws IOException {
    return null;
  }

  @Override
  public SSLSocket createSSLSocket(InetAddress inetAddress, int i, InetAddress inetAddress1)
      throws IOException {
    return null;
  }

  @Override
  public DatagramSocket createDatagramSocket() throws SocketException {
    return null;
  }

  @Override
  public DatagramSocket createDatagramSocket(int i, InetAddress inetAddress)
      throws SocketException {
    return null;
  }

  @Override
  public void setSipStack(SipStackImpl sipStack) {}
}
