package com.cisco.dsb.common.health;

import com.cisco.dsb.common.config.sip.CommonConfigurationProperties;
import com.cisco.dsb.common.sip.bean.SIPListenPoint;
import com.cisco.dsb.common.sip.tls.DsbTrustManager;
import com.cisco.dsb.common.transport.Transport;
import com.cisco.wx2.dto.health.ServiceHealth;
import com.cisco.wx2.dto.health.ServiceState;
import com.cisco.wx2.server.health.ServiceHealthManager;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.MockitoAnnotations;
import org.testng.Assert;
import org.testng.annotations.AfterClass;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import javax.net.ssl.KeyManager;
import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.util.ArrayList;
import java.util.List;

public class DsbListenPointHealthPingerTest {

  @InjectMocks DsbListenPointHealthPinger dsbListenPointHealthPinger;
  @Mock CommonConfigurationProperties commonConfigurationProperties;
  @Mock DsbTrustManager dsbTrustManager;
  @Mock KeyManager keyManager;
  @Mock ServiceHealthManager serviceHealthManagerMock;

  SIPListenPoint testListenPoint;

  DatagramSocket testDatagramSocket;
  ServerSocket testServerSocket;

  @BeforeMethod
  public void setUp() {
    MockitoAnnotations.initMocks(this);
  }

  @AfterMethod
  public void tearDown() {
    if (testDatagramSocket != null) {
      testDatagramSocket.close();
    }

    if (testServerSocket != null) {
      try {
        testServerSocket.close();
      } catch (IOException e) {
        e.printStackTrace();
      }
    }
  }

  @Test(description = "test for validating dsb health pinger")
  public void testDsbHealthPingerTCPNegative() {

    testListenPoint =
        SIPListenPoint.SIPListenPointBuilder()
            .setName("testNetwork")
            .setHostIPAddress("127.0.0.1")
            .setTransport(Transport.TCP)
            .setPort(6080)
            .setRecordRoute(false)
            .build();

    List<SIPListenPoint> listenPointList = new ArrayList<>();
    listenPointList.add(testListenPoint);
    Mockito.when(commonConfigurationProperties.getListenPoints()).thenReturn(listenPointList);

    /* There is no server socket listening for TCP transport so, while trying to ping, it will fail, and return a service state as offline */
    ServiceHealth pingResult = dsbListenPointHealthPinger.ping();

    Assert.assertEquals(ServiceState.OFFLINE, pingResult.getServiceState());
  }

  @Test(description = "test for validating dsb health pinger")
  public void testDsbHealthPingerTCPPositive() throws IOException {

    testListenPoint =
        SIPListenPoint.SIPListenPointBuilder()
            .setName("testNetwork")
            .setHostIPAddress("127.0.0.1")
            .setTransport(Transport.TCP)
            .setPort(6080)
            .setRecordRoute(false)
            .build();

    List<SIPListenPoint> listenPointList = new ArrayList<>();
    listenPointList.add(testListenPoint);
    Mockito.when(commonConfigurationProperties.getListenPoints()).thenReturn(listenPointList);

    /* There is  server socket listening for TCP transport so, while trying to ping, it will pass, and return a service state as online */
    testServerSocket = new ServerSocket(6080, 0, InetAddress.getByName("127.0.0.1"));

    ServiceHealth pingResult = dsbListenPointHealthPinger.ping();

    Assert.assertEquals(ServiceState.ONLINE, pingResult.getServiceState());
  }

  @Test(
      description =
          "test for validating implementation of if listenpoints are available or not, when listen point is available")
  public void testIsListeningTCPPositive() throws IOException {

    String networkName = "testNetwork1";
    String host = "127.0.0.1";
    int port = 6071;
    String transport = "TCP";

    testServerSocket = new ServerSocket(port, 0, InetAddress.getByName(host));

    boolean listening = dsbListenPointHealthPinger.isListening(networkName, host, port, transport);

    Assert.assertEquals(listening, true);
  }

  @Test(
      description =
          "test for validating implementation of if listenpoints are available or not, when listen point is not available")
  public void testIsListeningTCPNegative() throws IOException {

    String networkName = "testNetwork2";
    String host = "127.0.0.1";
    int port = 6071;
    String transport = "TCP";

    // no server socket listening
    boolean listening = dsbListenPointHealthPinger.isListening(networkName, host, port, transport);

    Assert.assertEquals(listening, false);
  }



  @Test(description = "test for validating implementation of if listenpoints are available or not")
  public void testIsListeningUDPPositive() throws IOException {

    String networkName = "testNetwork1";
    String host = "127.0.0.1";
    int port = 6070;
    String transport = "UDP";

    testDatagramSocket = new DatagramSocket(port, InetAddress.getByName(host));

    byte[] buf = new byte[128];
    DatagramPacket packet = new DatagramPacket(buf, buf.length, InetAddress.getByName(host), port);

    // testSocket.bind(new InetSocketAddress( InetAddress.getByName(host), port));

    boolean listening = dsbListenPointHealthPinger.isListening(networkName, host, port, transport);
    // testDatagramSocket.receive(packet);
    String recievedString = new String(packet.getData(), 0, packet.getLength());

    Assert.assertEquals(listening, true);
    // Assert.assertEquals(StringUtils.equalsIgnoreCase("ping",recievedString), true);
  }

  @Test(description = "test for validating implementation of if listenpoints are available or not when transport is tls and SslSocketFactory not initialized")
  public void testIsListeningTlsWithoutSslContextInitialization() throws IOException {

    dsbListenPointHealthPinger.setSslSocketFactory(null);
    String networkName = "testNetwork1";
    String host = "127.0.0.1";
    int port = 6073;
    String transport = "TLS";



    testServerSocket = new ServerSocket(port, 0, InetAddress.getByName(host));

    boolean listening = dsbListenPointHealthPinger.isListening(networkName, host, port, transport);

    Assert.assertEquals(listening, false);

  }

  @Test(description = "test for validating implementation of if listenpoints are available or not when transport is tls and SslSocketFactory is initialized")
  public void testIsListeningTlsWithSslContextInitialization() throws Exception {


    testListenPoint =
            SIPListenPoint.SIPListenPointBuilder()
                    .setName("testNetwork")
                    .setHostIPAddress("127.0.0.1")
                    .setTransport(Transport.TLS)
                    .setPort(6073)
                    .setRecordRoute(false)
                    .build();

    List<SIPListenPoint> listenPointList = new ArrayList<>();
    listenPointList.add(testListenPoint);
    Mockito.when(commonConfigurationProperties.getListenPoints()).thenReturn(listenPointList);

    dsbListenPointHealthPinger.initialize();

    // bound server socket on the same port
    testServerSocket = new ServerSocket(testListenPoint.getPort(), 0, InetAddress.getByName(testListenPoint.getHostIPAddress()));

    boolean listening = dsbListenPointHealthPinger.isListening(testListenPoint.getName(), testListenPoint.getHostIPAddress(), testListenPoint.getPort(), testListenPoint.getTransport().name());

    Assert.assertEquals(listening, true);

  }

  @Test(description = "test for validating implementation of if listenpoints are available or not when transport is tls and SslSocketFactory is initialized")
  public void testIsListeningTlsWithSslContextInitializationNegative() throws Exception {


    testListenPoint =
            SIPListenPoint.SIPListenPointBuilder()
                    .setName("testNetwork")
                    .setHostIPAddress("127.0.0.1")
                    .setTransport(Transport.TLS)
                    .setPort(6073)
                    .setRecordRoute(false)
                    .build();

    List<SIPListenPoint> listenPointList = new ArrayList<>();
    listenPointList.add(testListenPoint);
    Mockito.when(commonConfigurationProperties.getListenPoints()).thenReturn(listenPointList);

    dsbListenPointHealthPinger.initialize();

    // there is no server socket listening on the same port, so result will be false
    boolean listening = dsbListenPointHealthPinger.isListening(testListenPoint.getName(), testListenPoint.getHostIPAddress(), testListenPoint.getPort(), testListenPoint.getTransport().name());

    Assert.assertEquals(listening, false);

  }

  @AfterClass
  public void afterClass() {
    if (testDatagramSocket != null) {
      testDatagramSocket.close();
    }

    if(testServerSocket != null){
      try {
        testServerSocket.close();
      } catch (IOException e) {
        e.printStackTrace();
      }
    }
  }
}
