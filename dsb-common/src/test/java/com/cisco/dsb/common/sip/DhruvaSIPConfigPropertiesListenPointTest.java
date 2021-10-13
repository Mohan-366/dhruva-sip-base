package com.cisco.dsb.common.sip;

import static com.cisco.dsb.common.config.sip.DhruvaSIPConfigProperties.SIP_LISTEN_POINTS;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;
import static org.mockito.Mockito.when;

import com.cisco.dsb.common.config.sip.DhruvaSIPConfigProperties;
import com.cisco.dsb.common.sip.bean.SIPListenPoint;
import com.cisco.dsb.common.sip.tls.TLSAuthenticationType;
import com.cisco.dsb.common.transport.Transport;
import java.util.ArrayList;
import java.util.List;
import org.springframework.core.env.Environment;
import org.testng.Assert;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.BeforeTest;
import org.testng.annotations.Test;

public class DhruvaSIPConfigPropertiesListenPointTest {

  DhruvaSIPConfigProperties sipConfigProperties;

  Environment env;

  SIPListenPoint defaultListenPoint;

  SIPListenPoint udpListenPoint;

  SIPListenPoint tcpListenPoint;

  SIPListenPoint tlsListenPoint;

  @BeforeTest
  void init() {
    env = mock(Environment.class);
    defaultListenPoint =
        new SIPListenPoint.SIPListenPointBuilder()
            .setName("TCPNetwork")
            .setHostIPAddress("127.0.0.1")
            .setTransport(Transport.TCP)
            .setPort(5060)
            .setRecordRoute(true)
            .setAttachExternalIP(false)
            .build();
    udpListenPoint =
        new SIPListenPoint.SIPListenPointBuilder()
            .setName("TCPNetwork")
            .setHostIPAddress("0.0.0.0")
            .setTransport(Transport.UDP)
            .setPort(5061)
            .setRecordRoute(true)
            .setAttachExternalIP(false)
            .build();

    tcpListenPoint =
        new SIPListenPoint.SIPListenPointBuilder()
            .setName("TCPNetwork")
            .setHostIPAddress("10.78.98.21")
            .setTransport(Transport.TCP)
            .setPort(5062)
            .setRecordRoute(true)
            .setAttachExternalIP(false)
            .build();

    tlsListenPoint =
        new SIPListenPoint.SIPListenPointBuilder()
            .setName("TLSNetwork")
            .setHostIPAddress("10.78.98.21")
            .setTransport(Transport.TLS)
            .setPort(5063)
            .setRecordRoute(true)
            .setAttachExternalIP(false)
            .setTlsAuthType(TLSAuthenticationType.MTLS)
            .setCertServiceEnable(true)
            .build();
  }

  @BeforeMethod
  public void setup() {
    reset(env);
    doAnswer(invocationOnMock -> invocationOnMock.getArgument(2))
        .when(env)
        .getProperty(anyString(), any(), any());
  }

  @Test
  public void getListenPointsWithDefaultValues() {
    sipConfigProperties = new DhruvaSIPConfigProperties(env);
    when(env.getProperty(SIP_LISTEN_POINTS)).thenReturn(null);
    List<SIPListenPoint> defaultListenPointList = new ArrayList<SIPListenPoint>();
    List<SIPListenPoint> listenPoints = sipConfigProperties.getListeningPoints();
    defaultListenPointList.add(defaultListenPoint);

    Assert.assertEquals(listenPoints, defaultListenPointList);
  }

  @Test
  public void getListenPointsFromJSONConfig() {
    when(env.getProperty(SIP_LISTEN_POINTS))
        .thenReturn(
            "[{\"name\":\"TCPNetwork\",\"hostIPAddress\":\"10.78.98.21\",\"transport\":\"TCP\",\"port\":5062,\"recordRoute\":true,\"attachExternalIP\":false}]");
    sipConfigProperties = new DhruvaSIPConfigProperties(env);

    List<SIPListenPoint> expectedListenPointList = new ArrayList<SIPListenPoint>();
    expectedListenPointList.add(tcpListenPoint);
    Assert.assertEquals(sipConfigProperties.getListeningPoints(), expectedListenPointList);
  }

  @Test
  public void getListenPointsFromInvalidJSONConfig() {
    when(env.getProperty("sipListenPoints"))
        .thenReturn(
            "[{\"name\":\"TCPNetwork\",hostIPAddress\":\"10.78.98.21\",\"transport\":\"TCP\",\"port\":5061,\"recordRoute\":true,\"attachExternalIP\":false}]");
    sipConfigProperties = new DhruvaSIPConfigProperties(env);
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
            "[{\"name\":\"TCPNetwork\",\"hostIPAddress\":\"10.78.98.21\",\"transport\":\"TCP\",\"port\":5062,\"recordRoute\":true,\"attachExternalIP\":false},{\"name\":\"UDPNetwork\",\"hostIPAddress\":\"10.78.98.21\",\"transport\":\"UDP\",\"port\":5060,\"recordRoute\":false,\"attachExternalIP\":false}]");
    sipConfigProperties = new DhruvaSIPConfigProperties(env);
    List<SIPListenPoint> expectedListenPointList = new ArrayList<SIPListenPoint>();
    expectedListenPointList.add(tcpListenPoint);
    expectedListenPointList.add(udpListenPoint);
    Assert.assertEquals(sipConfigProperties.getListeningPoints(), expectedListenPointList);
  }

  @Test
  void getListenPointsFromJSONConfigListForTLS() {
    when(env.getProperty("sipListenPoints"))
        .thenReturn(
            "[{\"name\":\"TLSNetwork\",\"hostIPAddress\":\"10.78.98.21\",\"transport\":\"TLS\",\"port\":5063,\"recordRoute\":true,\"attachExternalIP\":false, \"tlsAuthType\": \"MTLS\", \"enableCertService\": true}]");
    sipConfigProperties = new DhruvaSIPConfigProperties(env);
    List<SIPListenPoint> expectedListenPointList = new ArrayList<SIPListenPoint>();
    expectedListenPointList.add(tlsListenPoint);
    Assert.assertEquals(sipConfigProperties.getListeningPoints(), expectedListenPointList);
  }
}
