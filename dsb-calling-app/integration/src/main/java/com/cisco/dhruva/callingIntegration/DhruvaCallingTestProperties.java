package com.cisco.dhruva.callingIntegration;

import org.springframework.core.env.Environment;
import org.springframework.core.env.StandardEnvironment;

public class DhruvaCallingTestProperties {
  private static final String DEFAULT_TEST_LISTEN_ADDRESS = "127.0.0.1";
  private static final Integer DEFAULT_PSTN_PORT = 4000;
  private static final Integer DEFAULT_ANTARES_PORT = 6000;
  private static final Integer DEFAULT_WXC_PORT = 7000;

  private static final String DEFAULT_ANTARES_ARECORD = "test.beech.com";
  private static final String DEFAULT_NS_ARECORD = "test1.ns.cisco.com";
  private static final String DEFAULT_AS_1_ARECORD = "test1.as.com";
  private static final String DEFAULT_AS_2_ARECORD = "test2.as.com";

  private static final String DEFAULT_DHRUVA_LISTEN_ADDRESS = "127.0.0.1";
  private static final Integer DEFAULT_DHRUVA_NET_SP_PORT = 5065;
  private static final Integer DEFAULT_DHRUVA_NET_ANTARES_PORT = 5062;
  private static final Integer DEFAULT_DHRUVA_NET_CC_PORT = 5080;
  private static final String DEFAULT_DHRUVA_PUBLIC_URL = "http://localhost:8080/api/v1";

  private static final String DEFAULT_INJECTED_DNS_UUID = "2cd3da89-2e9c-48a8-8fa8-d7296018b7e7";
  private static final Environment env = new StandardEnvironment();

  public String getTestAddress() {
    return env.getProperty("testHost", DEFAULT_TEST_LISTEN_ADDRESS);
  }

  public int getTestPstnPort() {
    return env.getProperty("testPstnPort", Integer.class, DEFAULT_PSTN_PORT);
  }

  public int getTestAntaresPort() {
    return env.getProperty("testAntaresPort", Integer.class, DEFAULT_ANTARES_PORT);
  }

  public int getTestWxCPort() {
    return env.getProperty("testWxCPort", Integer.class, DEFAULT_WXC_PORT);
  }

  public String getAntaresARecord() {
    return env.getProperty("antaresARecord", DEFAULT_ANTARES_ARECORD);
  }

  public String getNsARecord() {
    return env.getProperty("nsARecord", DEFAULT_NS_ARECORD);
  }

  public String getAs1ARecord() {
    return env.getProperty("as1ARecord", DEFAULT_AS_1_ARECORD);
  }

  public String getAs2ARecord() {
    return env.getProperty("as2ARecord", DEFAULT_AS_2_ARECORD);
  }

  public String getDhruvaAddress() {
    return env.getProperty("dhruvaHost", DEFAULT_DHRUVA_LISTEN_ADDRESS);
  }

  public int getDhruvaNetSpPort() {
    return env.getProperty("dhruvaNetSpPort", Integer.class, DEFAULT_DHRUVA_NET_SP_PORT);
  }

  public int getDhruvaNetAntaresPort() {
    return env.getProperty("dhruvaNetAntaresPort", Integer.class, DEFAULT_DHRUVA_NET_ANTARES_PORT);
  }

  public int getDhruvaNetCcPort() {
    return env.getProperty("dhruvaNetCcPort", Integer.class, DEFAULT_DHRUVA_NET_CC_PORT);
  }

  public String getInjectedDnsUuid() {
    return env.getProperty("injectedDnsUuid", DEFAULT_INJECTED_DNS_UUID);
  }

  public String getDhruvaPublicUrl() {
    return env.getProperty("dhruvaPublicUrl", DEFAULT_DHRUVA_PUBLIC_URL);
  }
}
