package com.cisco.dsb.common.config.sip;

import com.cisco.dsb.common.config.RoutePolicy;
import com.cisco.dsb.common.exception.DhruvaRuntimeException;
import com.cisco.dsb.common.maintanence.MaintenancePolicy;
import com.cisco.dsb.common.servergroup.OptionsPingPolicy;
import com.cisco.dsb.common.servergroup.SGType;
import com.cisco.dsb.common.servergroup.ServerGroup;
import com.cisco.dsb.common.servergroup.ServerGroupElement;
import com.cisco.dsb.common.sip.bean.SIPListenPoint;
import com.cisco.dsb.common.sip.tls.TLSAuthenticationType;
import com.cisco.dsb.common.transport.Transport;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.testng.Assert;
import org.testng.annotations.BeforeTest;
import org.testng.annotations.DataProvider;
import org.testng.annotations.Test;

public class CommonConfigurationPropertiesTest {

  private CommonConfigurationProperties props;

  @BeforeTest
  public void setup() {
    props = new CommonConfigurationProperties();
  }

  @Test(description = "tests the getters and setters for one set of properties")
  public void testGetterSettersForFewProps() {
    List<String> cipherSuites = new ArrayList<>();
    cipherSuites.add("Cipher1");
    List<String> tlsProtocols = new ArrayList<>();
    tlsProtocols.add("TLSv1.1");

    props.setEnableCertService(true);
    props.setUseRedisAsCache(true);
    props.setTlsAuthType(TLSAuthenticationType.NONE);
    props.setSipCertificate("sipCertificate");
    props.setSipPrivateKey("sipPrivateKey");
    props.setUdpEventloopThreadCount(10);
    props.setTlsEventloopThreadCount(10);
    props.setConnectionIdleTimeout(36000);
    props.setTlsCiphers(cipherSuites);
    props.setHostPortEnabled(true);
    props.setHostInfo("testHost");
    props.setAcceptedIssuersEnabled(true);
    props.setTlsHandShakeTimeOutMilliSeconds(10000);
    props.setConnectionWriteTimeoutInMllis(10000);
    props.setTlsOcspResponseTimeoutInSeconds(10);
    props.setTlsTrustStoreFilePath("/path/to/truststore");
    props.setTlsTrustStoreType("pkcs12");
    props.setTlsTrustStorePassword("trustPass");
    props.setTlsKeyStoreFilePath("/path/to/keystore");
    props.setTlsKeyStoreType("jks");
    props.setTlsKeyStorePassword("keyPass");
    props.setTlsCertRevocationEnableSoftFail(true);
    props.setTlsCertEnableOcsp(true);
    props.setClientAuthType("Enabled");
    props.setNioEnabled(true);
    props.setKeepAlivePeriod(10);
    props.setReliableKeepAlivePeriod("10");
    props.setMinKeepAliveTimeSeconds("10");
    props.setLogKeepAlivesEnabled(true);
    props.setSocketConnectionTimeout(10000);
    props.setDhruvaUserAgent("TestAgent");
    props.setTlsProtocols(tlsProtocols);

    Assert.assertTrue(props.isEnableCertService());
    Assert.assertTrue(props.isUseRedisAsCache());
    Assert.assertEquals(props.getTlsAuthType(), TLSAuthenticationType.NONE);
    Assert.assertEquals(props.getSipCertificate(), "sipCertificate");
    Assert.assertEquals(props.getSipPrivateKey(), "sipPrivateKey");
    Assert.assertEquals(props.getUdpEventloopThreadCount(), 10);
    Assert.assertEquals(props.getTlsEventloopThreadCount(), 10);
    Assert.assertEquals(props.getConnectionIdleTimeout(), 36000);
    Assert.assertEquals(props.getTlsCiphers(), cipherSuites);
    Assert.assertTrue(props.isHostPortEnabled());
    Assert.assertEquals(props.getHostInfo(), "testHost");
    Assert.assertTrue(props.isAcceptedIssuersEnabled());
    Assert.assertEquals(props.getTlsHandShakeTimeOutMilliSeconds(), 10000);
    Assert.assertEquals(props.getConnectionWriteTimeoutInMllis(), 10000);
    Assert.assertEquals(props.getTlsOcspResponseTimeoutInSeconds(), 10);
    Assert.assertEquals(props.getTlsTrustStoreFilePath(), "/path/to/truststore");
    Assert.assertEquals(props.getTlsTrustStoreType(), "pkcs12");
    Assert.assertEquals(props.getTlsTrustStorePassword(), "trustPass");
    Assert.assertEquals(props.getTlsKeyStoreFilePath(), "/path/to/keystore");
    Assert.assertEquals(props.getTlsKeyStoreType(), "jks");
    Assert.assertEquals(props.getTlsKeyStorePassword(), "keyPass");
    Assert.assertTrue(props.isTlsCertRevocationEnableSoftFail());
    Assert.assertTrue(props.isTlsCertEnableOcsp());
    Assert.assertEquals(props.getClientAuthType(), "Enabled");
    Assert.assertTrue(props.isNioEnabled());
    Assert.assertEquals(props.getKeepAlivePeriod(), 10);
    Assert.assertEquals(props.getReliableKeepAlivePeriod(), "10");
    Assert.assertEquals(props.getMinKeepAliveTimeSeconds(), "10");
    Assert.assertTrue(props.isLogKeepAlivesEnabled());
    Assert.assertEquals(CommonConfigurationProperties.getSocketConnectionTimeout(), 10000);
    Assert.assertEquals(props.getDhruvaUserAgent(), "TestAgent");
    Assert.assertEquals(props.getTlsProtocols(), tlsProtocols);
  }

  @Test(description = "tests getter setters of dns properties")
  public void testDnsGetterSetters() {

    props.setDnsCacheSize(0);
    Assert.assertEquals(props.getDnsCacheSize(), 1000); // retains default value
    props.setDnsCacheSize(100);
    Assert.assertEquals(props.getDnsCacheSize(), 100);

    props.setTimeOutDnsCache(0);
    Assert.assertEquals(props.getTimeOutDnsCache(), 32_000L); // retains default value
    props.setTimeOutDnsCache(10000);
    Assert.assertEquals(props.getTimeOutDnsCache(), 10000);

    props.setTimeOutDns(0);
    Assert.assertEquals(props.getTimeOutDns(), 10_000L); // retains default value
    props.setTimeOutDns(1000);
    Assert.assertEquals(props.getTimeOutDns(), 1000);

    props.setDnsLookupTimeoutMillis(500);
    Assert.assertEquals(props.getDnsLookupTimeoutMillis(), 500);
  }

  @DataProvider
  private Object[][] sgConfigParts() {
    return new Object[][] {
      {"TCPNetwork", "sgRoutePolicy", "pingPolicy"},
      {"noNetwork", "noSGRoutePolicy", "noPingPolicy"}
    };
  }

  @Test(
      dataProvider = "sgConfigParts",
      description =
          "1. When an existing listenPoint, SG-RoutePolicy and OptionsPing policy is used to configure SG -> they are set."
              + "2. When a listenPoint name, SG-RoutePolicy name, OptionsPing policy name whose config does not exist is tried to be used for SG config -> exceptions are thrown")
  public void testSGPropsUsingSetterGetter(
      String networkName, String routePolicyName, String pingPolicyName) {
    // create a ListenPoint (use the default one)
    List<SIPListenPoint> listenPoints = new ArrayList<>();
    SIPListenPoint lp = new SIPListenPoint();
    listenPoints.add(lp);

    // create an SG Policy
    RoutePolicy routePolicy = new RoutePolicy();
    routePolicy.setName("sgRoutePolicy");
    List<Integer> failoverCodes = new ArrayList<>();
    failoverCodes.add(501);
    routePolicy.setFailoverResponseCodes(failoverCodes);
    Map<String, RoutePolicy> routePolicyMap = new HashMap<>();
    routePolicyMap.put("sgRoutePolicy", routePolicy);

    // create an Options ping policy
    OptionsPingPolicy optionsPingPolicy = new OptionsPingPolicy();
    optionsPingPolicy.setName("pingPolicy");
    Map<String, OptionsPingPolicy> optionsPingPolicyMap = new HashMap<>();
    optionsPingPolicyMap.put("pingPolicy", optionsPingPolicy);

    // create a SG
    ServerGroup sg = new ServerGroup();
    sg.setName("SG1");
    sg.setNetworkName(networkName);
    sg.setRoutePolicyConfig(routePolicyName);
    sg.setOptionsPingPolicyConfig(pingPolicyName);
    Map<String, ServerGroup> sgList = new HashMap<>();
    sgList.put("SG1", sg);

    // set above properties in CommonProperties
    props.setListenPoints(listenPoints);
    Assert.assertEquals(props.getListenPoints(), listenPoints);

    if (networkName.equals("TCPNetwork")) {
      props.setServerGroups(sgList);
      Assert.assertEquals(props.getServerGroups().size(), 1);
    } else {
      try {
        props.setServerGroups(sgList);
      } catch (RuntimeException ignored) {
      }
    }

    if (routePolicyName.equals("sgRoutePolicy")) {
      props.setRoutePolicy(
          routePolicyMap); // based on SG policy name in SG, the SG policy has to be fetched and
      // filled
      // in the SG
      Assert.assertEquals(sg.getRoutePolicy(), routePolicy);
      Assert.assertEquals(props.getRoutePolicyMap(), routePolicyMap);
    } else {
      try {
        props.setRoutePolicy(routePolicyMap);
      } catch (RuntimeException ignored) {
      }
    }

    if (pingPolicyName.equals("pingPolicy")) {
      props.setOptionsPingPolicy(
          optionsPingPolicyMap); // based on OP policy name in SG, the OP policy has to be fetched
      // and filled in the SG
      Assert.assertEquals(sg.getOptionsPingPolicy(), optionsPingPolicy);
      Assert.assertEquals(props.getOptionsPingPolicyMap(), optionsPingPolicyMap);
    } else {
      try {
        props.setOptionsPingPolicy(optionsPingPolicyMap);
      } catch (RuntimeException ignored) {
      }
    }
  }

  @Test
  public void testSetSG_NoListenPoint_ThrowError() {
    try {
      props.setServerGroups(getStaticServerGroupMap());
    } catch (DhruvaRuntimeException dhruvaRuntimeException) {
      Assert.assertEquals(
          "SGName: ARecordHost; listenPoint: \"ARecordNetwork\" not found",
          dhruvaRuntimeException.getMessage());
    }
  }

  @Test
  public void testSetSG_StaticSG() {

    Map<String, ServerGroup> serverGroups = getStaticServerGroupMap();

    List<SIPListenPoint> listenPoints = new ArrayList<>();
    SIPListenPoint listenPointForStatic =
        SIPListenPoint.SIPListenPointBuilder()
            .setName("StaticNetwork")
            .setTransport(Transport.MULTICAST)
            .build();

    SIPListenPoint listenPointForARecord =
        SIPListenPoint.SIPListenPointBuilder()
            .setName("ARecordNetwork")
            .setTransport(Transport.SCTP)
            .build();

    listenPoints.add(listenPointForStatic);
    listenPoints.add(listenPointForARecord);
    props.setListenPoints(listenPoints);
    props.setServerGroups(serverGroups);
    ServerGroup statciServerGroup = serverGroups.get("StaticHost");
    ServerGroup aRecordServerGroup = serverGroups.get("ARecordHost");

    Assert.assertEquals(Transport.MULTICAST, statciServerGroup.getTransport());
    Assert.assertTrue(
        statciServerGroup.getElements().stream()
            .allMatch(sg -> Transport.MULTICAST.equals(sg.getTransport())));

    Assert.assertEquals(Transport.SCTP, aRecordServerGroup.getTransport());
    Assert.assertNull(aRecordServerGroup.getElements());
  }

  private Map<String, ServerGroup> getStaticServerGroupMap() {
    Map<String, ServerGroup> serverGroupMap = new HashMap<>();
    List<ServerGroupElement> sgElems = new ArrayList<>();
    ServerGroupElement sgElem1 =
        ServerGroupElement.builder().setIpAddress("127.0.0.2").setPort(8181).build();
    ServerGroupElement sgElem2 =
        ServerGroupElement.builder().setIpAddress("127.0.0.3").setPort(8182).build();

    sgElems.addAll(Arrays.asList(sgElem1, sgElem2));

    ServerGroup sgStatic =
        ServerGroup.builder()
            .setHostName("StaticHost")
            .setNetworkName("StaticNetwork")
            .setSgType(SGType.STATIC)
            .setElements(sgElems)
            .build();

    ServerGroup sgArecord =
        ServerGroup.builder()
            .setHostName("ARecordHost")
            .setNetworkName("ARecordNetwork")
            .setSgType(SGType.A_RECORD)
            .build();

    serverGroupMap.put("StaticHost", sgStatic);
    serverGroupMap.put("ARecordHost", sgArecord);

    Assert.assertTrue(
        sgElems.stream().allMatch(serverGroupElement -> serverGroupElement.getTransport() == null));
    Assert.assertEquals(Transport.UDP, sgStatic.getTransport());
    Assert.assertEquals(Transport.UDP, sgArecord.getTransport());

    return serverGroupMap;
  }

  @Test
  public void testMaintenancePolicyMap() {
    MaintenancePolicy mPolicyWithValue =
        MaintenancePolicy.builder().setName("mPolicyWithValue").build();
    Map<String, MaintenancePolicy> maintenancePolicyMap = new HashMap<>();
    maintenancePolicyMap.put(mPolicyWithValue.getName(), mPolicyWithValue);

    props.setMaintenancePolicy(maintenancePolicyMap);
    Assert.assertEquals(props.getMaintenancePolicyMap(), maintenancePolicyMap);
  }
}
