package com.cisco.dsb.common.sip.tls;

import com.cisco.dsb.common.config.DhruvaConfig;
import com.cisco.dsb.common.config.sip.CommonConfigurationProperties;
import com.cisco.dsb.common.sip.stack.dto.DhruvaNetwork;
import java.net.*;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import javax.net.ssl.KeyManager;
import javax.net.ssl.TrustManager;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.testng.Assert;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.Test;

public class DsbNetworkLayerTest {
  @Mock CommonConfigurationProperties commonConfigurationProperties;
  @Mock TrustManager trustManager;
  @Mock KeyManager keyManager;
  @InjectMocks DhruvaConfig dhruvaConfig;
  DsbNetworkLayer networkLayer = new DsbNetworkLayer();
  InetAddress myaddr = null;
  InetAddress addr = null;

  {
    try {
      myaddr = InetAddress.getByName("127.0.0.1");
      addr = InetAddress.getByName("127.0.0.3");
    } catch (UnknownHostException e) {
      throw new RuntimeException(e);
    }
  }

  int port = 5060;
  Map<String, Integer> trafficClassMap = new ConcurrentHashMap<>();

  public DsbNetworkLayerTest() throws UnknownHostException {}

  @BeforeClass
  public void before() throws Exception {
    MockitoAnnotations.initMocks(this);
    DhruvaNetwork.setDhruvaConfigProperties(commonConfigurationProperties);
  }

  @Test(description = "Verifying trafficClass of UDP socket")
  public void testUDPSocket() throws Exception {
    trafficClassMap.clear();
    trafficClassMap.put(myaddr.toString(), 0x70);
    commonConfigurationProperties = new CommonConfigurationProperties();
    commonConfigurationProperties.setTrafficClassMap(trafficClassMap);
    networkLayer.init(trustManager, keyManager, commonConfigurationProperties);
    DatagramSocket socket = networkLayer.createDatagramSocket(port, myaddr);
    Assert.assertEquals(socket.getTrafficClass(), 0x70);
    socket.close();
  }

  @Test(description = "verifying default trafficClass of UDP socket")
  public void testDefaultTrafficClassOfUDPSocket() throws Exception {
    trafficClassMap.clear();
    commonConfigurationProperties = new CommonConfigurationProperties();
    commonConfigurationProperties.setTrafficClassMap(trafficClassMap);
    networkLayer.init(trustManager, keyManager, commonConfigurationProperties);
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
    commonConfigurationProperties = new CommonConfigurationProperties();
    commonConfigurationProperties.setTrafficClassMap(trafficClassMap);
    networkLayer.init(trustManager, keyManager, commonConfigurationProperties);
    networkLayer.setSocketOptions(socket);
    Assert.assertEquals(socket.getTrafficClass(), 0x70);
    socket.close();
  }
}
