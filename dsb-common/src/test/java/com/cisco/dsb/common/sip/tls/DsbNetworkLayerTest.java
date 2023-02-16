package com.cisco.dsb.common.sip.tls;

import static org.mockito.Mockito.*;

import com.cisco.dsb.common.config.sip.CommonConfigurationProperties;
import com.cisco.dsb.common.sip.stack.dto.DhruvaNetwork;
import java.io.IOException;
import java.net.*;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import javax.net.ssl.KeyManager;
import javax.net.ssl.SSLServerSocket;
import javax.net.ssl.SSLServerSocketFactory;
import javax.net.ssl.TrustManager;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.MockitoAnnotations;
import org.testng.Assert;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.Test;

public class DsbNetworkLayerTest {
  @Mock CommonConfigurationProperties commonConfigurationProperties;
  @Mock TrustManager trustManager;
  @Mock KeyManager keyManager;
  DsbNetworkLayer networkLayer = new DsbNetworkLayer();
  InetAddress myaddr = InetAddress.getByName("127.0.0.1");

  int port = 5060;
  Map<String, Integer> trafficClassMap = new ConcurrentHashMap<>();

  public DsbNetworkLayerTest() throws UnknownHostException {}

  @BeforeClass
  public void before() throws Exception {
    MockitoAnnotations.initMocks(this);
    DhruvaNetwork.setDhruvaConfigProperties(commonConfigurationProperties);
    commonConfigurationProperties = new CommonConfigurationProperties();
    commonConfigurationProperties.setTrafficClassMap(trafficClassMap);
    networkLayer.init(trustManager, keyManager, commonConfigurationProperties);
  }

  @Test(description = "Verifying trafficClass of UDP socket")
  public void testUDPSocket() throws Exception {
    trafficClassMap.clear();
    trafficClassMap.put(myaddr.toString(), 0x70);
    DatagramSocket socket = networkLayer.createDatagramSocket(port, myaddr);
    Assert.assertEquals(socket.getTrafficClass(), 0x70);
    socket.close();
  }

  @Test(description = "verifying default trafficClass of UDP socket")
  public void testDefaultTrafficClassOfUDPSocket() throws Exception {
    trafficClassMap.clear();
    DatagramSocket socket = networkLayer.createDatagramSocket(port, myaddr);
    Assert.assertEquals(
        socket.getTrafficClass(), CommonConfigurationProperties.DEFAULT_TRAFFIC_CLASS);
    socket.close();
  }

  @Test(description = "Verifying trafficClass of TCP socket")
  public void testTCPSocket() throws Exception {
    Socket socket = new Socket();
    socket.bind(new InetSocketAddress(myaddr, port));

    trafficClassMap.put(myaddr.toString(), 0x70);
    networkLayer.setSocketOptions(socket);
    Assert.assertEquals(socket.getTrafficClass(), 0x70);
    socket.close();
  }

  @Test(description = "Verify reuseaddr options on DGRAM socket")
  public void testDataGramBindOptions() throws UnknownHostException, SocketException {
    DatagramSocket datagramSocket =
        networkLayer.createDatagramSocket(49876, InetAddress.getLocalHost());
    Assert.assertTrue(datagramSocket.getReuseAddress());
    DatagramSocket datagramSocket1 =
        networkLayer.createDatagramSocket(49876, InetAddress.getLocalHost());
    Assert.assertTrue(datagramSocket.isBound());
    Assert.assertTrue(datagramSocket1.isBound());
    datagramSocket.close();
    datagramSocket1.close();
  }

  @Test(description = "Verify reuseaddr options on tcp/tls server socket")
  public void testServerSocketBindOptions() throws IOException {
    ServerSocket serverSocket =
        networkLayer.createServerSocket(12345, 32, InetAddress.getLocalHost());
    ServerSocket serverSocket1 =
        networkLayer.createServerSocket(12345, 32, InetAddress.getLocalHost());
    Assert.assertTrue(serverSocket.isBound());
    Assert.assertTrue(serverSocket1.isBound());
    Assert.assertTrue(serverSocket.getReuseAddress());
    Assert.assertTrue(serverSocket.getOption(StandardSocketOptions.SO_REUSEPORT));

    Assert.assertTrue(serverSocket1.getReuseAddress());
    Assert.assertTrue(serverSocket1.getOption(StandardSocketOptions.SO_REUSEPORT));
  }

  @Test(description = "Verify reuseaddr options on tcp/tls server socket")
  public void testSSLServerSocketBindOptions() throws IOException {
    SSLServerSocketFactory mockfactory = Mockito.mock(SSLServerSocketFactory.class);
    ;
    networkLayer.sslServerSocketFactory = mockfactory;
    SSLServerSocket sslServerSocket = mock(SSLServerSocket.class);
    when(mockfactory.createServerSocket()).thenReturn(sslServerSocket);
    networkLayer.createSSLServerSocket(12345, 32, InetAddress.getLocalHost());
    SocketAddress socketAddress = new InetSocketAddress(InetAddress.getLocalHost(), 12345);
    verify(sslServerSocket, times(1)).setOption(eq(StandardSocketOptions.SO_REUSEPORT), eq(true));
    verify(sslServerSocket, times(1)).bind(eq(socketAddress), eq(32));
  }
}
