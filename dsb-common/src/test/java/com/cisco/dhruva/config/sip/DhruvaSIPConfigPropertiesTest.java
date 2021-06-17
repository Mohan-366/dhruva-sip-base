package com.cisco.dhruva.config.sip;

import static org.mockito.Mockito.when;

import com.cisco.dhruva.sip.bean.SIPListenPoint;
import com.cisco.dhruva.transport.TLSAuthenticationType;
import com.cisco.dhruva.transport.Transport;
import java.util.ArrayList;
import java.util.List;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.springframework.core.env.Environment;
import org.springframework.mock.env.MockEnvironment;
import org.testng.Assert;
import org.testng.annotations.BeforeTest;
import org.testng.annotations.Test;

public class DhruvaSIPConfigPropertiesTest {

  @InjectMocks DhruvaSIPConfigProperties sipConfigProperties;

  @Mock Environment env = new MockEnvironment();

  SIPListenPoint defaultListenPoint;

  SIPListenPoint tcpListenPoint;

  SIPListenPoint tlsListenPoint;

  @BeforeTest
  void init() {
    MockitoAnnotations.initMocks(this);
    defaultListenPoint =
        new SIPListenPoint.SIPListenPointBuilder()
            .setName("UDPNetwork")
            .setHostIPAddress("0.0.0.0")
            .setTransport(Transport.UDP)
            .setPort(5060)
            .setRecordRoute(true)
            .setAttachExternalIP(false)
            .build();

    tcpListenPoint =
        new SIPListenPoint.SIPListenPointBuilder()
            .setName("TCPNetwork")
            .setHostIPAddress("10.78.98.21")
            .setTransport(Transport.TCP)
            .setPort(5061)
            .setRecordRoute(true)
            .setAttachExternalIP(false)
            .build();

    tlsListenPoint =
        new SIPListenPoint.SIPListenPointBuilder()
            .setName("TLSNetwork")
            .setHostIPAddress("10.78.98.21")
            .setTransport(Transport.TLS)
            .setPort(5062)
            .setRecordRoute(true)
            .setAttachExternalIP(false)
            .setTlsAuthType(TLSAuthenticationType.MTLS)
            .setCertServiceEnable(true)
            .build();
  }

  @Test
  public void getListenPointsWithDefaultValues() {
    when(env.getProperty("sipListenPoints")).thenReturn(null);

    List<SIPListenPoint> defaultListenPointList = new ArrayList<SIPListenPoint>();
    List<SIPListenPoint> listenPoints = sipConfigProperties.getListeningPoints();
    defaultListenPointList.add(defaultListenPoint);

    Assert.assertEquals(listenPoints, defaultListenPointList);
  }

  @Test
  public void getListenPointsFromJSONConfig() {
    when(env.getProperty("sipListenPoints"))
        .thenReturn(
            "[{\"name\":\"TCPNetwork\",\"hostIPAddress\":\"10.78.98.21\",\"transport\":\"TCP\",\"port\":5061,\"recordRoute\":true,\"attachExternalIP\":false}]");

    List<SIPListenPoint> expectedListenPointList = new ArrayList<SIPListenPoint>();
    expectedListenPointList.add(tcpListenPoint);
    Assert.assertEquals(sipConfigProperties.getListeningPoints(), expectedListenPointList);
  }

  @Test
  public void getListenPointsFromInvalidJSONConfig() {
    when(env.getProperty("sipListenPoints"))
        .thenReturn(
            "[{\"name\":\"TCPNetwork\",hostIPAddress\":\"10.78.98.21\",\"transport\":\"TCP\",\"port\":5061,\"recordRoute\":true,\"attachExternalIP\":false}]");
    List<SIPListenPoint> expectedListenPointList = new ArrayList<SIPListenPoint>();
    expectedListenPointList.add(defaultListenPoint);
    Assert.assertEquals(sipConfigProperties.getListeningPoints(), expectedListenPointList);
  }

  @Test
  void getListenPointsFromJSONConfigList() {

    SIPListenPoint udpListenPoint =
        new SIPListenPoint.SIPListenPointBuilder()
            .setName("UDPNetwork")
            .setHostIPAddress("10.78.98.21")
            .setTransport(Transport.UDP)
            .setPort(5060)
            .setRecordRoute(false)
            .build();

    when(env.getProperty("sipListenPoints"))
        .thenReturn(
            "[{\"name\":\"TCPNetwork\",\"hostIPAddress\":\"10.78.98.21\",\"transport\":\"TCP\",\"port\":5061,\"recordRoute\":true,\"attachExternalIP\":false},{\"name\":\"UDPNetwork\",\"hostIPAddress\":\"10.78.98.21\",\"transport\":\"UDP\",\"port\":5060,\"recordRoute\":false,\"attachExternalIP\":false}]");
    List<SIPListenPoint> expectedListenPointList = new ArrayList<SIPListenPoint>();
    expectedListenPointList.add(tcpListenPoint);
    expectedListenPointList.add(udpListenPoint);
    Assert.assertEquals(sipConfigProperties.getListeningPoints(), expectedListenPointList);
  }

  @Test
  void getListenPointsFromJSONConfigListForTLS() {

    when(env.getProperty("sipListenPoints"))
        .thenReturn(
            "[{\"name\":\"TLSNetwork\",\"hostIPAddress\":\"10.78.98.21\",\"transport\":\"TLS\",\"port\":5062,\"recordRoute\":true,\"attachExternalIP\":false, \"tlsAuthType\": \"MTLS\", \"enableCertService\": true}]");
    List<SIPListenPoint> expectedListenPointList = new ArrayList<SIPListenPoint>();
    expectedListenPointList.add(tlsListenPoint);
    System.out.println(sipConfigProperties.getListeningPoints());
    Assert.assertEquals(sipConfigProperties.getListeningPoints(), expectedListenPointList);
  }
}
