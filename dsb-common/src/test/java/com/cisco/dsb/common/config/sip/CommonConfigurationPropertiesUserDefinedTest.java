package com.cisco.dsb.common.config.sip;

import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertTrue;

import com.cisco.dsb.common.dto.TrustedSipSources;
import com.cisco.dsb.common.loadbalancer.LBType;
import com.cisco.dsb.common.servergroup.SGPolicy;
import com.cisco.dsb.common.servergroup.ServerGroup;
import com.cisco.dsb.common.servergroup.ServerGroupElement;
import com.cisco.dsb.common.sip.bean.SIPListenPoint;
import com.cisco.dsb.common.sip.tls.TLSAuthenticationType;
import com.cisco.dsb.common.transport.Transport;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.ConfigFileApplicationContextInitializer;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.testng.AbstractTestNGSpringContextTests;
import org.testng.annotations.Test;

@TestPropertySource(locations = "classpath:application-commonconfig.yaml")
@ContextConfiguration(
    classes = {CommonConfigurationProperties.class},
    initializers = {ConfigFileApplicationContextInitializer.class})
@ActiveProfiles("commonconfig")
public class CommonConfigurationPropertiesUserDefinedTest extends AbstractTestNGSpringContextTests {
  @Autowired CommonConfigurationProperties commonConfigurationProperties;

  @Test
  public void testAllUserDefinedProperties() {
    List<SIPListenPoint> listenPoints =
        Arrays.asList(
            SIPListenPoint.SIPListenPointBuilder()
                .setName("testNetwork")
                .setPort(5061)
                .setTransport(Transport.UDP)
                .setHostIPAddress("1.1.1.1")
                .setRecordRoute(false)
                .setTlsAuthType(TLSAuthenticationType.CLIENT)
                .setAttachExternalIP(true)
                .setEnableCertService(true)
                .build(),
            SIPListenPoint.SIPListenPointBuilder().setName("defaultNetwork").build());
    Map<String, SGPolicy> sgpolicy = new HashMap<>();
    sgpolicy.put(
        "policy1",
        SGPolicy.builder()
            .setName("policy1")
            .setFailoverResponseCodes(Arrays.asList(501, 502))
            .build());
    Map<String, ServerGroup> serverGroups = new HashMap<>();
    List<ServerGroupElement> sg1Elements =
        Arrays.asList(
            ServerGroupElement.builder()
                .setIpAddress("1.1.1.1")
                .setPort(5060)
                .setQValue(0.9f)
                .setWeight(10)
                .setTransport(Transport.UDP)
                .build(),
            ServerGroupElement.builder()
                .setIpAddress("2.2.2.2")
                .setPort(5070)
                .setQValue(0.9f)
                .setWeight(10)
                .setTransport(Transport.UDP)
                .build());
    ServerGroup sg1 =
        ServerGroup.builder()
            .setName("SG1")
            .setLbType(LBType.HIGHEST_Q)
            .setNetworkName("testNetwork")
            .setElements(sg1Elements)
            .setSgPolicy(sgpolicy.get("policy1"))
            .build();
    serverGroups.put(sg1.getName(), sg1);
    assertTrue(commonConfigurationProperties.isEnableCertService());
    assertTrue(commonConfigurationProperties.isUseRedisAsCache());
    assertEquals(commonConfigurationProperties.getTlsAuthType(), TLSAuthenticationType.MTLS);
    assertEquals(commonConfigurationProperties.getSipCertificate(), "sipCertificate");
    assertEquals(commonConfigurationProperties.getSipPrivateKey(), "sipPrivateKey");
    assertEquals(commonConfigurationProperties.getUdpEventloopThreadCount(), 10);
    assertEquals(commonConfigurationProperties.getTlsEventloopThreadCount(), 10);
    assertEquals(commonConfigurationProperties.getConnectionIdleTimeout(), 36_000);
    assertEquals(
        commonConfigurationProperties.getTlsCiphers(), Arrays.asList("Cipher1", "Cipher2"));
    assertTrue(commonConfigurationProperties.isHostPortEnabled());
    assertEquals(commonConfigurationProperties.getHostInfo(), "testHost");
    assertEquals(commonConfigurationProperties.getTlsHandShakeTimeOutMilliSeconds(), 10_000L);
    assertEquals(commonConfigurationProperties.getConnectionWriteTimeoutInMllis(), 10_000L);
    assertEquals(commonConfigurationProperties.getTlsOcspResponseTimeoutInSeconds(), 10);
    assertEquals(commonConfigurationProperties.getTlsTrustStoreFilePath(), "/path/to/truststore");
    assertEquals(commonConfigurationProperties.getTlsTrustStoreType(), "pkcs12");
    assertEquals(commonConfigurationProperties.getTlsTrustStorePassword(), "trustPass");
    assertTrue(commonConfigurationProperties.isTlsCertRevocationEnableSoftFail());
    assertTrue(commonConfigurationProperties.isTlsCertEnableOcsp());
    assertEquals(commonConfigurationProperties.getClientAuthType(), "Enabled");
    assertTrue(commonConfigurationProperties.isNioEnabled());
    assertEquals(commonConfigurationProperties.getKeepAlivePeriod(), 10);
    assertEquals(commonConfigurationProperties.getReliableKeepAlivePeriod(), "10");
    assertEquals(commonConfigurationProperties.getMinKeepAliveTimeSeconds(), "10");
    assertTrue(commonConfigurationProperties.isLogKeepAlivesEnabled());
    assertEquals(
        commonConfigurationProperties.getTrustedSipSources(),
        new TrustedSipSources("trusted.sip.source"));
    assertTrue(commonConfigurationProperties.isRequiredTrustedSipSources());
    assertEquals(CommonConfigurationProperties.getSocketConnectionTimeout(), 10_000L);
    assertEquals(commonConfigurationProperties.getDhruvaUserAgent(), "TestAgent");
    assertEquals(
        commonConfigurationProperties.getTlsProtocols(), Arrays.asList("TLSv1.2", "TLSv1.1"));
    assertEquals(commonConfigurationProperties.getListenPoints(), listenPoints);
    assertEquals(commonConfigurationProperties.getDnsCacheSize(), 100);
    assertEquals(commonConfigurationProperties.getTimeOutDnsCache(), 10_000);
    assertEquals(commonConfigurationProperties.getTimeOutDns(), 1_000);
    assertEquals(commonConfigurationProperties.getServerGroups(), serverGroups);
    assertEquals(commonConfigurationProperties.getSgPolicyMap(), sgpolicy);
  }
}
