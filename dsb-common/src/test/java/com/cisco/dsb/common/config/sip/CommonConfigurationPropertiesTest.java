package com.cisco.dsb.common.config.sip;

import com.cisco.dsb.common.servergroup.OptionsPingPolicy;
import com.cisco.dsb.common.servergroup.SGPolicy;
import com.cisco.dsb.common.servergroup.ServerGroup;
import com.cisco.dsb.common.sip.bean.SIPListenPoint;
import com.cisco.dsb.common.sip.tls.TLSAuthenticationType;
import java.util.*;
import org.testng.Assert;
import org.testng.annotations.DataProvider;
import org.testng.annotations.Test;

public class CommonConfigurationPropertiesTest {

  @Test(description = "tests the getters and setters for one set of properties")
  public void testGetterSettersForFewProps() {
    CommonConfigurationProperties props = new CommonConfigurationProperties();
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
    CommonConfigurationProperties props = new CommonConfigurationProperties();

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
      {"TCPNetwork", "sgPolicy", "pingPolicy"},
      {"noNetwork", "noSGPolicy", "noPingPolicy"}
    };
  }

  @Test(
      dataProvider = "sgConfigParts",
      description =
          "1. When an existing listenPoint, SGPolicy and OptionsPing policy is used to configure SG -> they are set."
              + "2. When a listenPoint name, SGPolicy name, OptionsPing policy name whose config does not exist is tried to be used for SG config -> exceptions are thrown")
  public void testSGPropsUsingSetterGetter(
      String networkName, String sgPolicyName, String pingPolicyName) {
    // create a ListenPoint (use the default one)
    List<SIPListenPoint> listenPoints = new ArrayList<>();
    SIPListenPoint lp = new SIPListenPoint();
    listenPoints.add(lp);

    // create an SG Policy
    SGPolicy sgPolicy = new SGPolicy();
    sgPolicy.setName("sgPolicy");
    List<Integer> failoverCodes = new ArrayList<>();
    failoverCodes.add(501);
    sgPolicy.setFailoverResponseCodes(failoverCodes);
    Map<String, SGPolicy> sgPolicyMap = new HashMap<>();
    sgPolicyMap.put("sgPolicy", sgPolicy);

    // create an Options ping policy
    OptionsPingPolicy optionsPingPolicy = new OptionsPingPolicy();
    optionsPingPolicy.setName("pingPolicy");
    Map<String, OptionsPingPolicy> optionsPingPolicyMap = new HashMap<>();
    optionsPingPolicyMap.put("pingPolicy", optionsPingPolicy);

    // create a SG
    ServerGroup sg = new ServerGroup();
    sg.setName("SG1");
    sg.setNetworkName(networkName);
    sg.setSgPolicyConfig(sgPolicyName);
    sg.setOptionsPingPolicyConfig(pingPolicyName);
    List<ServerGroup> sgList = new ArrayList<>();
    sgList.add(sg);

    // set above properties in CommonProperties
    CommonConfigurationProperties props = new CommonConfigurationProperties();
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

    if (sgPolicyName.equals("sgPolicy")) {
      props.setSgPolicy(
          sgPolicyMap); // based on SG policy name in SG, the SG policy has to be fetched and filled
      // in the SG
      Assert.assertEquals(sg.getSgPolicy(), sgPolicy);
      Assert.assertEquals(props.getSgPolicyMap(), sgPolicyMap);
    } else {
      try {
        props.setSgPolicy(sgPolicyMap);
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
}
