package com.cisco.dsb.common.config.sip;

import static org.mockito.Mockito.when;

import com.cisco.dsb.common.config.CertConfigurationProperties;
import com.cisco.dsb.common.config.RoutePolicy;
import com.cisco.dsb.common.config.TruststoreConfigurationProperties;
import com.cisco.dsb.common.exception.DhruvaRuntimeException;
import com.cisco.dsb.common.maintanence.MaintenancePolicy;
import com.cisco.dsb.common.servergroup.OptionsPingPolicy;
import com.cisco.dsb.common.servergroup.SGType;
import com.cisco.dsb.common.servergroup.ServerGroup;
import com.cisco.dsb.common.servergroup.ServerGroupElement;
import com.cisco.dsb.common.sip.bean.SIPListenPoint;
import com.cisco.dsb.common.transport.Transport;
import gov.nist.javax.sip.stack.ClientAuthType;
import java.net.UnknownHostException;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.springframework.core.env.Environment;
import org.testng.Assert;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.DataProvider;
import org.testng.annotations.Test;

public class CommonConfigurationPropertiesTest {

  @Mock Environment env;
  @InjectMocks private CommonConfigurationProperties props;

  @BeforeMethod
  public void beforeTest() {
    MockitoAnnotations.initMocks(this);
  }

  @Test(description = "tests the getters and setters for one set of properties")
  public void testGetterSettersForFewProps() {

    props.setUseRedisAsCache(true);
    props.setSipCertificate("sipCertificate");
    props.setSipPrivateKey("sipPrivateKey");
    props.setUdpEventloopThreadCount(10);
    props.setTlsEventloopThreadCount(10);
    props.setConnectionIdleTimeout(36000);
    props.setHostPortEnabled(true);
    when(env.getProperty("testHost")).thenReturn("1.1.1.1");
    props.setHostInfo("testHost");
    props.setTlsHandShakeTimeOutMilliSeconds(10000);
    props.setConnectionWriteTimeoutInMllis(10000);

    props.setKeepAlivePeriod(10);
    props.setReliableKeepAlivePeriod("10");
    props.setMinKeepAliveTimeSeconds("10");
    props.setLogKeepAlivesEnabled(true);
    props.setSocketConnectionTimeout(10000);
    props.setDhruvaUserAgent("TestAgent");

    Assert.assertTrue(props.isUseRedisAsCache());
    Assert.assertEquals(props.getSipCertificate(), "sipCertificate");
    Assert.assertEquals(props.getSipPrivateKey(), "sipPrivateKey");
    Assert.assertEquals(props.getUdpEventloopThreadCount(), 10);
    Assert.assertEquals(props.getTlsEventloopThreadCount(), 10);
    Assert.assertEquals(props.getConnectionIdleTimeout(), 36000);
    Assert.assertTrue(props.isHostPortEnabled());
    Assert.assertEquals(props.getHostInfo(), "1.1.1.1");
    Assert.assertEquals(props.getTlsHandShakeTimeOutMilliSeconds(), 10000);
    Assert.assertEquals(props.getConnectionWriteTimeoutInMllis(), 10000);
    Assert.assertFalse(props.isNioEnabled());
    Assert.assertEquals(props.getKeepAlivePeriod(), 10);
    Assert.assertEquals(props.getReliableKeepAlivePeriod(), "10");
    Assert.assertEquals(props.getMinKeepAliveTimeSeconds(), "10");
    Assert.assertTrue(props.isLogKeepAlivesEnabled());
    Assert.assertEquals(CommonConfigurationProperties.getSocketConnectionTimeout(), 10000);
    Assert.assertEquals(props.getDhruvaUserAgent(), "TestAgent");
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

  @Test
  public void testTlsConfiguration() {
    List<String> cipherSuites = new ArrayList<>();
    cipherSuites.add("Cipher1");
    List<String> tlsProtocols = new ArrayList<>();
    tlsProtocols.add("TLSv1.1");
    tlsProtocols.add("TLSv1.2");
    TruststoreConfigurationProperties truststoreConfigurationProperties =
        new TruststoreConfigurationProperties();
    truststoreConfigurationProperties.setCiphers(cipherSuites);
    truststoreConfigurationProperties.setTrustStoreFilePath("/path/to/truststore");
    truststoreConfigurationProperties.setTrustStoreType("pkcs12");
    truststoreConfigurationProperties.setTrustStorePassword("trustPass");
    truststoreConfigurationProperties.setKeyStoreFilePath("/path/to/keystore");
    truststoreConfigurationProperties.setKeyStoreType("jks");
    truststoreConfigurationProperties.setKeyStorePassword("keyPass");
    truststoreConfigurationProperties.setOcspResponseTimeoutInSeconds(10);
    truststoreConfigurationProperties.setTlsProtocols(tlsProtocols);

    CertConfigurationProperties certConfigurationProperties = new CertConfigurationProperties();
    certConfigurationProperties.setAcceptedIssuersEnabled(true);
    certConfigurationProperties.setOcsp(true);
    certConfigurationProperties.setClientAuthType(ClientAuthType.Enabled);
    certConfigurationProperties.setRevocationSoftfail(true);
    certConfigurationProperties.setTrustedSipSources("akg.cisco.com,akg.webex.com");
    certConfigurationProperties.setTrustAllCerts(false);
    certConfigurationProperties.setRequiredTrustedSipSources(true);

    props.setTruststoreConfiguration(truststoreConfigurationProperties);
    props.setListenPoints(
        Collections.singletonList(
            SIPListenPoint.SIPListenPointBuilder()
                .setCertPolicy(certConfigurationProperties)
                .build()));

    Assert.assertEquals(props.getTruststoreConfig(), truststoreConfigurationProperties);
    Assert.assertEquals(
        props.getListenPoints().get(0).getCertPolicy(), certConfigurationProperties);
  }

  @Test(description = "Test trafficClassMap")
  public void testTrafficClassMap() throws UnknownHostException {
    List<SIPListenPoint> listenPoints = new ArrayList<>();
    SIPListenPoint lp = new SIPListenPoint();
    listenPoints.add(lp);
    String key = "/" + lp.getHostIPAddress();
    Map<String, Integer> expectedTrafficClassMap = new ConcurrentHashMap<>();
    expectedTrafficClassMap.put(key, lp.getTrafficClass());

    props.setListenPoints(listenPoints);
    Assert.assertEquals(props.getTrafficClassMap(), expectedTrafficClassMap);
    Assert.assertTrue(lp.toString().contains("trafficClass = 104"));
  }
}
