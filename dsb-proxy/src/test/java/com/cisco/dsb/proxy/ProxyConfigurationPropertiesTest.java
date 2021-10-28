package com.cisco.dsb.proxy;

import static org.testng.Assert.assertEquals;

import com.cisco.dsb.proxy.sip.SIPProxy;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.ConfigFileApplicationContextInitializer;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.testng.AbstractTestNGSpringContextTests;
import org.testng.annotations.Test;

@TestPropertySource(locations = "classpath:application-proxyconfig.yaml")
@ContextConfiguration(
    classes = {ProxyConfigurationProperties.class},
    initializers = {ConfigFileApplicationContextInitializer.class})
@ActiveProfiles("proxyconfig")
public class ProxyConfigurationPropertiesTest extends AbstractTestNGSpringContextTests {
  @Autowired ProxyConfigurationProperties proxyConfigurationProperties;

  @Test
  public void testUserDefinedProperties() {
    String allowedMethods = "INVITE,ACK,BYE";
    SIPProxy sipProxy =
        SIPProxy.builder()
            .createDNSServerGroup(true)
            .errorAggregator(true)
            .processRegisterRequest(true)
            .processRouteHeader(true)
            .timerCIntervalInMilliSec(10_000)
            .build();
    assertEquals(proxyConfigurationProperties.getSipProxy(), sipProxy);
    assertEquals(proxyConfigurationProperties.getAllowedMethods(), allowedMethods);
  }
}
