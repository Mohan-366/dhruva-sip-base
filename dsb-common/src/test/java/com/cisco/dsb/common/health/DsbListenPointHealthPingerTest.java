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
import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import javax.net.ssl.KeyManager;
import javax.net.ssl.SSLSocketFactory;
import java.io.IOException;
import java.net.*;
import java.util.ArrayList;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;

public class DsbListenPointHealthPingerTest {


  @InjectMocks
  DsbListenPointHealthPinger dsbListenPointHealthPinger;
  @Mock
  CommonConfigurationProperties commonConfigurationProperties;
  @Mock
  DsbTrustManager dsbTrustManager;
  @Mock
  KeyManager keyManager;
  @Mock SocketFactory socketFactory;
  @Mock
  ServiceHealthManager serviceHealthManagerMock;
  @Mock
  SSLSocketFactory mockedSslSocketFactory;

  SIPListenPoint testListenPoint;

  DatagramSocket testDatagramSocket;
  ServerSocket testServerSocket;


  String networkName;
  String host;
  int port ;
  String transport;




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
  public void testDsbHealthPingerTCPNegative() throws IOException {

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


      Mockito.when(socketFactory.getSocket(testListenPoint.getTransport().name(),testListenPoint.getHostIPAddress(),testListenPoint.getPort())).thenThrow(new ConnectException());

    /* There is no server socket listening for TCP transport so, while trying to ping, it will fail, and return a service state as offline */
    ServiceHealth pingResult = dsbListenPointHealthPinger.ping();

    Assert.assertEquals(ServiceState.OFFLINE, pingResult.getServiceState());
  }


    @Test(description = "test for validating dsb health pinger where we will encounter exception but not related to connection to the listenPoint")
    public void testDsbHealthPingerTCPForOtherExceptions() throws IOException {

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


        Mockito.when(socketFactory.getSocket(testListenPoint.getTransport().name(),testListenPoint.getHostIPAddress(),testListenPoint.getPort())).thenThrow(new RuntimeException());


        /* There is  server socket listening for TCP transport, faced some other exceptions not related to connection to the socket. so, while trying to ping, it will not fail, and return a service state as offline */
        ServiceHealth pingResult = dsbListenPointHealthPinger.ping();

        Assert.assertEquals(ServiceState.ONLINE, pingResult.getServiceState());
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

      Socket mockedClientSocket = mock(Socket.class);
      Mockito.when(socketFactory.getSocket(testListenPoint.getTransport().name(),testListenPoint.getHostIPAddress(),testListenPoint.getPort())).thenReturn(mockedClientSocket);

      ServiceHealth pingResult = dsbListenPointHealthPinger.ping();

    Assert.assertEquals(ServiceState.ONLINE, pingResult.getServiceState());
  }

  @Test(
      description =
          "test for validating implementation of if listenpoints are available or not, when listen point is available")
  public void testIsListeningTCPPositive() throws IOException {

    networkName = "testNetwork1";
    host = "127.0.0.1";
    port = 6071;
    transport = "TCP";

    Socket mockedClientSocket = mock(Socket.class);
    Mockito.when(socketFactory.getSocket(transport, host,port)).thenReturn(mockedClientSocket);


    boolean listening = dsbListenPointHealthPinger.isListening(networkName, host, port, transport);

    Assert.assertEquals(listening, true);
  }

  @Test(
      description =
          "test for validating implementation of if listenpoints are available or not, when listen point is not available")
  public void testIsListeningTCPNegative() throws IOException {

    networkName = "testNetwork2";
    host = "127.0.0.1";
    port = 6071;
    transport = "TCP";


    Mockito.when(socketFactory.getSocket(transport,host,port)).thenThrow(new SocketTimeoutException());

    // no server socket listening
    boolean listening = dsbListenPointHealthPinger.isListening(networkName, host, port, transport);

    Assert.assertEquals(listening, false);
  }

  @Test(description = "test for validating implementation of if listenpoints are available or not for udp transport")
  public void testIsListeningUDPPositive() throws IOException {

     networkName = "testNetwork1";
     host = "127.0.0.1";
     port = 6070;
     transport = "UDP";


     DatagramSocket mockedDatagramSocket = mock(DatagramSocket.class);
     Mockito.when(socketFactory.getSocket(transport,host,port)).thenReturn(mockedDatagramSocket);

    boolean listening = dsbListenPointHealthPinger.isListening(networkName, host, port, transport);

    Assert.assertEquals(listening, true);
  }


  @Test(description = "test for validating implementation of if listenpoints are available or not for UDP transport negative scenario")
  public void testIsListeningUDPNegative() throws IOException {

    networkName = "testNetwork1";
    host = "127.0.0.1";
    port = 6070;
    transport = "UDP";


    DatagramSocket mockedDatagramSocket = mock(DatagramSocket.class);
    doThrow(new PortUnreachableException()).when(mockedDatagramSocket).send(any());
    Mockito.when(socketFactory.getSocket(transport,host,port)).thenReturn(mockedDatagramSocket);

    boolean listening = dsbListenPointHealthPinger.isListening(networkName, host, port, transport);

    Assert.assertEquals(listening, false);
  }

  @Test(
      description =
          "test for validating implementation of if listenpoints are available or not when transport is tls and SslSocketFactory not initialized")
  public void testIsListeningTlsWithoutSslContextInitialization() throws IOException {

    dsbListenPointHealthPinger.setSslSocketFactory(null);

     networkName = "testNetwork1";
     host = "127.0.0.1";
     port = 6073;
     transport = "TLS";

    boolean listening = dsbListenPointHealthPinger.isListening(networkName, host, port, transport);

    Assert.assertEquals(listening, false);
  }

  @Test(
      description =
          "test for validating implementation of if listenpoints are available or not when transport is tls and SslSocketFactory is initialized")
  public void testIsListeningTlsPositive() throws Exception {

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

     dsbListenPointHealthPinger.setSslSocketFactory(mockedSslSocketFactory);

     Socket mockedClientSocket = mock(Socket.class);
     Mockito.when(mockedSslSocketFactory.createSocket(testListenPoint.getHostIPAddress(),testListenPoint.getPort())).thenReturn(mockedClientSocket);


    boolean listening =
        dsbListenPointHealthPinger.isListening(
            testListenPoint.getName(),
            testListenPoint.getHostIPAddress(),
            testListenPoint.getPort(),
            testListenPoint.getTransport().name());

    Assert.assertEquals(listening, true);
  }

  @Test(
      description =
          "test for validating implementation of if listenpoints are available or not when transport is tls and SslSocketFactory is initialized")
  public void testIsListeningTlsNegative() throws Exception {

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

    dsbListenPointHealthPinger.setSslSocketFactory(mockedSslSocketFactory);

    Mockito.when(mockedSslSocketFactory.createSocket(testListenPoint.getHostIPAddress(), testListenPoint.getPort())).thenThrow(new ConnectException());


    // there is no server socket listening on the same port, so result will be false
    boolean listening =
        dsbListenPointHealthPinger.isListening(
            testListenPoint.getName(),
            testListenPoint.getHostIPAddress(),
            testListenPoint.getPort(),
            testListenPoint.getTransport().name());

    Assert.assertEquals(listening, false);
  }

}
