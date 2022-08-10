package com.cisco.dhruva.antares.integration;

import org.springframework.core.env.Environment;
import org.springframework.core.env.StandardEnvironment;

public class CallingTestProperties {
  private static final String DEFAULT_TEST_LISTEN_ADDRESS = "127.0.0.1";
  private static final Integer DEFAULT_PSTN_PORT = 4000;
  private static final Integer DEFAULT_NS_PORT = 7000;
  private static final Integer DEFAULT_AS_PORT = 7001;

  private static final String DEFAULT_DHRUVA_LISTEN_ADDRESS = "127.0.0.1";
  private static final Integer DEFAULT_DHRUVA_NET_SP_PORT = 5065;
  private static final Integer DEFAULT_DHRUVA_NET_CC_PORT = 5060;

  private static final Environment env = new StandardEnvironment();

  public String getTestAddress() {
    return env.getProperty("testHost", DEFAULT_TEST_LISTEN_ADDRESS);
  }

  public int getTestPstnPort() {
    return env.getProperty("testPstnPort", Integer.class, DEFAULT_PSTN_PORT);
  }

  public int getTestNsPort() {
    return env.getProperty("testNsPort", Integer.class, DEFAULT_NS_PORT);
  }

  public int getTestAsPort() {
    return env.getProperty("testAsPort", Integer.class, DEFAULT_AS_PORT);
  }

  public String getDhruvaAddress() {
    return env.getProperty("dhruvaHost", DEFAULT_DHRUVA_LISTEN_ADDRESS);
  }

  public int getDhruvaNetSpPort() {
    return env.getProperty("dhruvaNetSpPort", Integer.class, DEFAULT_DHRUVA_NET_SP_PORT);
  }

  public int getDhruvaNetCcPort() {
    return env.getProperty("dhruvaNetCcPort", Integer.class, DEFAULT_DHRUVA_NET_CC_PORT);
  }
}
