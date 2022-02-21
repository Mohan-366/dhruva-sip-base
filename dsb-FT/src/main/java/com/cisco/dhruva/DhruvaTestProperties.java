package com.cisco.dhruva;

import org.springframework.core.env.Environment;
import org.springframework.core.env.StandardEnvironment;
import org.springframework.util.SocketUtils;

public class DhruvaTestProperties {
  private static final String DEFAULT_TEST_LISTEN_ADDRESS = "127.0.0.1";
  private static final Integer DEFAULT_TEST_SERVER_PORT_UDP = SocketUtils.findAvailableUdpPort();
  private static final Integer DEFAULT_TEST_SERVER_PORT_TCP = SocketUtils.findAvailableTcpPort();
  private static final Integer DEFAULT_TEST_SERVER_PORT_TLS = SocketUtils.findAvailableTcpPort();

  private static final String DEFAULT_DHRUVA_HOST = "127.0.0.1";
  private static final Integer DEFAULT_DHRUVA_PORT_UDP = 5062;
  private static final Integer DEFAULT_DHRUVA_PORT_TCP = 5060;
  private static final Integer DEFAULT_DHRUVA_PORT_TLS = 5061;

  private static final Environment env = new StandardEnvironment();

  public String getTestAddress() {
    return env.getProperty("testHost", DEFAULT_TEST_LISTEN_ADDRESS);
  }

  public int getTestUdpPort() {
    return env.getProperty("testUdpPort", Integer.class, DEFAULT_TEST_SERVER_PORT_UDP);
  }

  public int getTestTcpPort() {
    return env.getProperty("testTcpPort", Integer.class, DEFAULT_TEST_SERVER_PORT_TCP);
  }

  public int getTestTlsPort() {
    return env.getProperty("testTlsPort", Integer.class, DEFAULT_TEST_SERVER_PORT_TLS);
  }

  public String getDhruvaHost() {
    return env.getProperty("dhruvaHost", DEFAULT_DHRUVA_HOST);
  }

  public int getDhruvaUdpPort() {
    return env.getProperty("dhruvaSipUdpPort", Integer.class, DEFAULT_DHRUVA_PORT_UDP);
  }

  public int getDhruvaTcpPort() {
    return env.getProperty("dhruvaSipTcpPort", Integer.class, DEFAULT_DHRUVA_PORT_TCP);
  }

  public int getDhruvaTlsPort() {
    return env.getProperty("dhruvaSipTlsPort", Integer.class, DEFAULT_DHRUVA_PORT_TLS);
  }
}
