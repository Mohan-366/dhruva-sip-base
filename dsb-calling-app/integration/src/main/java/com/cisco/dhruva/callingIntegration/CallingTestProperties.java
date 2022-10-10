package com.cisco.dhruva.callingIntegration;

import com.cisco.wx2.server.config.Wx2Properties;
import java.net.URI;
import org.springframework.core.env.Environment;
import org.springframework.core.env.StandardEnvironment;

public class CallingTestProperties extends Wx2Properties {
  private static final String DEFAULT_TEST_LISTEN_ADDRESS = "127.0.0.1";
  private static final Integer DEFAULT_PSTN_US_POOLB_PORT = 4200;
  private static final Integer DEFAULT_PSTN_US_POOLA_SG1_PORT = 4101;
  private static final Integer DEFAULT_PSTN_US_POOLA_SG2_PORT = 4102;
  private static final Integer DEFAULT_ANTARES_PORT = 6000;
  private static final Integer DEFAULT_NS_PORT = 7000;
  private static final Integer DEFAULT_AS_1_PORT = 7001;
  private static final Integer DEFAULT_AS_2_PORT = 7002;

  private static final String DEFAULT_ANTARES_ARECORD = "test.beech.com";
  private static final String DEFAULT_NS_ARECORD = "test1.ns.cisco.com";
  private static final String DEFAULT_AS_1_ARECORD = "127.0.0.1";
  private static final String DEFAULT_AS_2_ARECORD = "127.0.0.1";

  private static final String DEFAULT_DHRUVA_LISTEN_ADDRESS = "127.0.0.1";
  private static final Integer DEFAULT_DHRUVA_NET_SP_PORT = 5065;
  private static final Integer DEFAULT_DHRUVA_NET_ANTARES_PORT = 5062;
  private static final Integer DEFAULT_DHRUVA_NET_CC_PORT = 5080;
  private static final String DEFAULT_DHRUVA_PUBLIC_URL = "http://localhost:8080/api/v1";
  private static final String DEFAULT_DHRUVA_HTTP_ENDPOINT =
      "http://localhost:8080/dsb_calling_app_server_war_exploded/api/v1/admin/SipRoutingOverrides/101";

  private static final String DEFAULT_INJECTED_DNS_UUID = "2cd3da89-2e9c-48a8-8fa8-d7296018b7e7";

  private static final String CLIENT_ID =
      "Ce5001cd059d994a40baf687224636e57cbd4941d41a506930597f65d0abcfa5d";
  private static final String CLIENT_SECRET =
      "38e5c06921d1c43829626e6a96c61c3541443b2809aba436cf9eec314ed71ce3";
  private static final String MACHINE_ACCOUNT_USER = "dhruva-int-machine-account-http";
  private static final String MACHINE_ACCOUNT_PASSWORD = "RITM.0270509.4OJfWz4pFmmU.4ddVsn.jzrYEmu";
  private static final String ORG_ID = "a93dde14-65b7-4a59-81dd-28962a8473e3";
  public static final String OAUTH_ENDPOINT_URL = "https://idbrokerbts.webex.com/idb";

  private static final Environment env = new StandardEnvironment();

  public CallingTestProperties() {
    super(env);
  }

  public String getTestAddress() {
    return env.getProperty("testHost", DEFAULT_TEST_LISTEN_ADDRESS);
  }

  public int getTestPstnUsPoolBPort() {
    return env.getProperty("testPstnUsPoolBPort", Integer.class, DEFAULT_PSTN_US_POOLB_PORT);
  }

  public int getTestPstnUsPoolASG1Port() {
    return env.getProperty("testPstnUsPoolASG1Port", Integer.class, DEFAULT_PSTN_US_POOLA_SG1_PORT);
  }

  public int getTestPstnUsPoolASG2Port() {
    return env.getProperty("testPstnUsPoolASG2Port", Integer.class, DEFAULT_PSTN_US_POOLA_SG2_PORT);
  }

  public int getTestAntaresPort() {
    return env.getProperty("testAntaresPort", Integer.class, DEFAULT_ANTARES_PORT);
  }

  public int getTestNsPort() {
    return env.getProperty("testNsPort", Integer.class, DEFAULT_NS_PORT);
  }

  public int getTestAs1Port() {
    return env.getProperty("testAs1Port", Integer.class, DEFAULT_AS_1_PORT);
  }

  public int getTestAs2Port() {
    return env.getProperty("testAs2Port", Integer.class, DEFAULT_AS_2_PORT);
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

  public String getDhruvaClientId() {
    return env.getProperty(
        CLIENT_ID,
        String.class,
        "Ce5001cd059d994a40baf687224636e57cbd4941d41a506930597f65d0abcfa5d");
  }

  public String getDhruvaClientSecret() {
    return env.getProperty(
        CLIENT_SECRET,
        String.class,
        "38e5c06921d1c43829626e6a96c61c3541443b2809aba436cf9eec314ed71ce3");
  }

  public String getDhruvaMachineAccountUser() {
    return env.getProperty(MACHINE_ACCOUNT_USER, String.class, "dhruva-int-machine-account-http");
  }

  public String getDhruvaMachineAccountPassword() {
    return env.getProperty(
        MACHINE_ACCOUNT_PASSWORD, String.class, "RITM.0270509.4OJfWz4pFmmU.4ddVsn.jzrYEmu");
  }

  public String getDhruvaOrgId() {
    return env.getProperty(ORG_ID, String.class, "a93dde14-65b7-4a59-81dd-28962a8473e3");
  }

  public URI getOAuthURL() {
    return URI.create(env.getProperty(OAUTH_ENDPOINT_URL, "https://idbrokerbts.webex.com/idb"));
  }

  public String getDefaultDhruvaHttpEndpoint() {
    return env.getProperty("dhruvaHttpUrl", DEFAULT_DHRUVA_HTTP_ENDPOINT);
  }
}
