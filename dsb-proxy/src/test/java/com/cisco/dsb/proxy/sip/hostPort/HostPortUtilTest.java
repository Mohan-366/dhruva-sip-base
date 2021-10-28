package com.cisco.dsb.proxy.sip.hostPort;

import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;

import com.cisco.dsb.common.config.sip.CommonConfigurationProperties;
import com.cisco.dsb.common.exception.DhruvaException;
import com.cisco.dsb.common.service.SipServerLocatorService;
import com.cisco.dsb.common.sip.bean.SIPListenPoint;
import com.cisco.dsb.common.sip.jain.JainSipHelper;
import com.cisco.dsb.common.sip.stack.dto.DhruvaNetwork;
import com.cisco.dsb.common.sip.util.ListenIf;
import com.cisco.dsb.common.sip.util.ListenInterface;
import com.cisco.dsb.common.transport.Transport;
import com.cisco.dsb.proxy.ProxyConfigurationProperties;
import com.cisco.dsb.proxy.controller.ControllerConfig;
import java.net.InetAddress;
import java.net.UnknownHostException;
import javax.sip.address.SipURI;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.testng.Assert;
import org.testng.annotations.*;

public class HostPortUtilTest {
  ControllerConfig controllerConfig;
  @Mock SipServerLocatorService sipServerLocatorService;

  @Mock ProxyConfigurationProperties proxyConfigurationProperties;

  DhruvaNetwork dsNetwork, externalIpEnabledNetwork;
  SipURI privateNetworkInfo,
      publicNetworkInfo,
      publicNetworkWithHostIPInfo,
      publicNetworkWithHostFqdnInfo,
      unrecognizedNetworkInfo;
  String localIp = "127.0.0.1";
  String hostIp = "1.1.1.1";
  String unknownIp = "1.2.3.4";
  String hostFqdn = "dhruva.sjc.webex.com";
  boolean enableHostPort = true;
  boolean disableHostPort = false;

  @BeforeClass
  void init() throws Exception {
    MockitoAnnotations.initMocks(this);
    controllerConfig = new ControllerConfig(sipServerLocatorService, proxyConfigurationProperties);

    SIPListenPoint sipListenPointInternal = createInternalSipListenPoint();
    SIPListenPoint sipListenPointExternal = createExternalSipListenPoint();

    dsNetwork = DhruvaNetwork.createNetwork("Default", sipListenPointInternal);
    externalIpEnabledNetwork =
        DhruvaNetwork.createNetwork("External_IP_enabled", sipListenPointExternal);

    try {
      privateNetworkInfo = JainSipHelper.createSipURI("Default@127.0.0.1:5060;transport=udp;lr");
      publicNetworkInfo =
          JainSipHelper.createSipURI("External_IP_enabled@127.0.0.1:5061;transport=udp;lr");
      publicNetworkWithHostIPInfo =
          JainSipHelper.createSipURI("External_IP_enabled@1.1.1.1:5061;transport=udp;lr");
      publicNetworkWithHostFqdnInfo =
          JainSipHelper.createSipURI(
              "External_IP_enabled@dhruva.sjc.webex.com:5061;transport=udp;lr");
      unrecognizedNetworkInfo =
          JainSipHelper.createSipURI("Unrecognized@1.2.3.4:5678;transport=udp;lr");
    } catch (Exception ignored) {

    }
  }

  public SIPListenPoint createInternalSipListenPoint() {
    return SIPListenPoint.SIPListenPointBuilder()
        .setName("Default")
        .setHostIPAddress("127.0.0.1")
        .setPort(5060)
        .setTransport(Transport.UDP)
        .setAttachExternalIP(false)
        .setRecordRoute(true)
        .build();
  }

  public SIPListenPoint createExternalSipListenPoint() {
    return SIPListenPoint.SIPListenPointBuilder()
        .setName("External_IP_enabled")
        .setHostIPAddress("127.0.0.1")
        .setPort(5061)
        .setTransport(Transport.UDP)
        .setAttachExternalIP(false)
        .setRecordRoute(true)
        .build();
  }

  @BeforeMethod
  private void addListenInterfaces() throws Exception {

    // Add listen interfaces in DsControllerConfig
    try {
      controllerConfig.addListenInterface(
          dsNetwork,
          InetAddress.getByName(localIp),
          5060,
          Transport.UDP,
          InetAddress.getByName(localIp),
          false);
    } catch (DhruvaException ignored) {
    }

    try {
      controllerConfig.addListenInterface(
          externalIpEnabledNetwork,
          InetAddress.getByName(localIp),
          5061,
          Transport.UDP,
          InetAddress.getByName(localIp),
          true);

    } catch (DhruvaException ignored) {
    }
  }

  @AfterMethod
  private void resetDhruvaProp() {
    DhruvaNetwork.setDhruvaConfigProperties(null);
  }

  public class HostPortTestDataProvider {

    SipURI uri;
    ListenInterface listenIf;
    String expectedIp, hostInfoFromProps;
    boolean isHostPortEnabled;

    public HostPortTestDataProvider(
        SipURI uri, String expectedIp, String hostInfoFromProps, boolean isHostPortEnabled) {
      this.uri = uri;
      this.expectedIp = expectedIp;
      this.hostInfoFromProps = hostInfoFromProps;
      this.isHostPortEnabled = isHostPortEnabled;
    }

    public HostPortTestDataProvider(
        ListenInterface listenIf,
        String expectedIp,
        String hostInfoFromProps,
        boolean isHostPortEnabled) {
      this.listenIf = listenIf;
      this.expectedIp = expectedIp;
      this.hostInfoFromProps = hostInfoFromProps;
      this.isHostPortEnabled = isHostPortEnabled;
    }

    public String toString() {
      return "SipUri: {"
          + uri
          + "}; "
          + "Listen Interface: {"
          + listenIf
          + "}; "
          + "IP expected after conversion : {"
          + expectedIp
          + "}; "
          + "Host IP/FQDN: {"
          + hostInfoFromProps
          + "}; "
          + "When HostPort feature: {"
          + isHostPortEnabled
          + "}";
    }
  }

  @DataProvider
  public Object[] getUriAndExpectedIpForLocalToHost() {

    return new HostPortTestDataProvider[][] {
      {new HostPortTestDataProvider(privateNetworkInfo, localIp, hostIp, enableHostPort)},
      {new HostPortTestDataProvider(publicNetworkInfo, hostIp, hostIp, enableHostPort)},
      {new HostPortTestDataProvider(publicNetworkInfo, hostFqdn, hostFqdn, enableHostPort)},
      {new HostPortTestDataProvider(unrecognizedNetworkInfo, unknownIp, hostIp, enableHostPort)},
      {new HostPortTestDataProvider(privateNetworkInfo, localIp, null, disableHostPort)},
      {new HostPortTestDataProvider(publicNetworkInfo, localIp, null, disableHostPort)},
      {new HostPortTestDataProvider(unrecognizedNetworkInfo, unknownIp, null, disableHostPort)}
    };
  }

  @Test(dataProvider = "getUriAndExpectedIpForLocalToHost")
  public void testLocalIpToHostInfoConversion(HostPortTestDataProvider input) {

    CommonConfigurationProperties commonConfigurationProperties =
        mock(CommonConfigurationProperties.class);
    DhruvaNetwork.setDhruvaConfigProperties(commonConfigurationProperties);

    doReturn(input.isHostPortEnabled).when(commonConfigurationProperties).isHostPortEnabled();
    doReturn(input.hostInfoFromProps).when(commonConfigurationProperties).getHostInfo();

    Assert.assertEquals(
        HostPortUtil.convertLocalIpToHostInfo(controllerConfig, input.uri), input.expectedIp);
  }

  @DataProvider
  public Object[] getUriAndExpectedIpForHostToLocal() {

    return new HostPortTestDataProvider[][] {
      {new HostPortTestDataProvider(privateNetworkInfo, localIp, null, enableHostPort)},
      {new HostPortTestDataProvider(publicNetworkWithHostIPInfo, localIp, null, enableHostPort)},
      {new HostPortTestDataProvider(publicNetworkWithHostFqdnInfo, localIp, null, enableHostPort)},
      {new HostPortTestDataProvider(unrecognizedNetworkInfo, unknownIp, null, enableHostPort)},
      {new HostPortTestDataProvider(privateNetworkInfo, localIp, null, disableHostPort)},
      {new HostPortTestDataProvider(publicNetworkInfo, localIp, null, disableHostPort)},
      {new HostPortTestDataProvider(unrecognizedNetworkInfo, unknownIp, null, disableHostPort)}
    };
  }

  @Test(dataProvider = "getUriAndExpectedIpForHostToLocal")
  public void testHostInfoToLocalIpConversion(HostPortTestDataProvider input) {

    CommonConfigurationProperties commonConfigurationProperties =
        mock(CommonConfigurationProperties.class);
    DhruvaNetwork.setDhruvaConfigProperties(commonConfigurationProperties);

    doReturn(input.isHostPortEnabled).when(commonConfigurationProperties).isHostPortEnabled();

    Assert.assertEquals(
        HostPortUtil.reverseHostInfoToLocalIp(controllerConfig, input.uri), input.expectedIp);
  }

  @DataProvider
  public Object[] getListenInterfaceAndExpectedIpForLocalToHost() throws UnknownHostException {
    ListenIf listenIf1 =
        (ListenIf)
            controllerConfig.getInterface(
                InetAddress.getByName(privateNetworkInfo.getHost().toString()),
                Transport.getTypeFromString(privateNetworkInfo.getTransportParam()).get(),
                privateNetworkInfo.getPort());

    ListenIf listenIf2 =
        (ListenIf)
            controllerConfig.getInterface(
                InetAddress.getByName(publicNetworkInfo.getHost().toString()),
                Transport.getTypeFromString(privateNetworkInfo.getTransportParam()).get(),
                publicNetworkInfo.getPort());

    return new HostPortTestDataProvider[][] {
      {new HostPortTestDataProvider(listenIf1, localIp, hostIp, enableHostPort)},
      {new HostPortTestDataProvider(listenIf2, hostIp, hostIp, enableHostPort)},
      {new HostPortTestDataProvider(listenIf2, hostFqdn, hostFqdn, enableHostPort)},
      {new HostPortTestDataProvider(listenIf1, localIp, null, disableHostPort)},
      {new HostPortTestDataProvider(listenIf2, localIp, null, disableHostPort)},
    };
  }

  @Test(dataProvider = "getListenInterfaceAndExpectedIpForLocalToHost")
  public void testLocalIpToHostInfoUsingListenInterface(HostPortTestDataProvider input) {

    CommonConfigurationProperties commonConfigurationProperties =
        mock(CommonConfigurationProperties.class);
    DhruvaNetwork.setDhruvaConfigProperties(commonConfigurationProperties);

    doReturn(input.isHostPortEnabled).when(commonConfigurationProperties).isHostPortEnabled();
    doReturn(input.hostInfoFromProps).when(commonConfigurationProperties).getHostInfo();

    Assert.assertEquals(HostPortUtil.convertLocalIpToHostInfo(input.listenIf), input.expectedIp);
  }
}
