package com.cisco.dhruva.antares.integration.tests;

import com.cisco.dhruva.antares.integration.CallingTestProperties;
import com.cisco.dhruva.antares.integration.DhruvaTestConfig;
import com.cisco.dhruva.antares.integration.util.IntegrationTestListener;
import com.cisco.wx2.test.BaseTestConfig;
import java.util.Properties;
import javax.annotation.PostConstruct;
import org.cafesip.sipunit.SipStack;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.testng.AbstractTestNGSpringContextTests;
import org.testng.annotations.Listeners;

@Listeners({IntegrationTestListener.class})
@ContextConfiguration(classes = {BaseTestConfig.class, DhruvaTestConfig.class})
public class DhruvaIT extends AbstractTestNGSpringContextTests {

  protected static String testHostAddress;
  protected static String dhruvaAddress;
  protected static int dhruvaNetSpPort;
  protected static int dhruvaNetCcPort;

  protected static int pstnPort;
  protected static String pstnContactAddr;

  protected static int nsPort;
  protected static int asPort;
  protected static String wxcContactAddr;

  protected static final int timeout = 10000;

  protected SipStack pstnStack;
  protected SipStack nsStack;
  protected SipStack asStack;

  @Autowired private CallingTestProperties testPro;

  @PostConstruct
  public void init() {
    testHostAddress = testPro.getTestAddress();
    dhruvaAddress = testPro.getDhruvaAddress();

    dhruvaNetSpPort = testPro.getDhruvaNetSpPort();
    dhruvaNetCcPort = testPro.getDhruvaNetCcPort();

    pstnPort = testPro.getTestPstnPort();
    nsPort = testPro.getTestNsPort();
    asPort = testPro.getTestAsPort();

    pstnContactAddr = "sip:+19876543210@" + testHostAddress;
    wxcContactAddr = "sip:+10123456789@" + testHostAddress;
  }

  protected Properties getProperties(String stackName) {
    Properties props = new Properties();
    props.setProperty("javax.sip.STACK_NAME", stackName);
    props.setProperty("gov.nist.javax.sip.READ_TIMEOUT", "1000");
    props.setProperty("gov.nist.javax.sip.CACHE_SERVER_CONNECTIONS", "false");
    props.setProperty("gov.nist.javax.sip.TRACE_LEVEL", "32");
    props.setProperty("javax.sip.IP_ADDRESS", testHostAddress);
    return props;
  }
}
