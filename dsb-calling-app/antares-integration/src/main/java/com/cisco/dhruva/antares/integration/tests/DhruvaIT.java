package com.cisco.dhruva.antares.integration.tests;

import com.cisco.dhruva.antares.integration.CallingTestProperties;
import com.cisco.dhruva.antares.integration.DhruvaTestConfig;
import com.cisco.dhruva.antares.integration.util.IntegrationTestListener;
import com.cisco.dhruva.antares.integration.util.Token;
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

  protected String testHostAddress;
  protected String dhruvaAddress;
  protected int dhruvaNetSpPort;
  protected int dhruvaNetCcPort;

  protected int pstnPort;
  protected String pstnContactAddr;
  protected String pstnUser;

  protected int nsPort;
  protected int asPort;
  protected String wxcContactAddr;
  protected String wxcUser;

  protected static final int timeout = 10000;
  protected static final String X_BROADWORKS_CORRELATION_INFO = "X-BroadWorks-Correlation-Info";
  protected static final String X_BROADWORKS_DNC = "X-BroadWorks-DNC";

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

    pstnUser = Token.SIP_COLON + "+19876543210" + Token.AT_SIGN;
    pstnContactAddr = pstnUser + testHostAddress;

    wxcUser = Token.SIP_COLON + "+10123456789" + Token.AT_SIGN;
    wxcContactAddr = wxcUser + testHostAddress;
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
