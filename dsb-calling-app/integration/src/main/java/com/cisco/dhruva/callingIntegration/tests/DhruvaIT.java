package com.cisco.dhruva.callingIntegration.tests;

import com.cisco.dhruva.callingIntegration.DhruvaCallingTestProperties;
import com.cisco.dhruva.callingIntegration.DhruvaTestConfig;
import com.cisco.dhruva.callingIntegration.util.IntegrationTestListener;
import com.cisco.dhruva.callingIntegration.util.Token;
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
  protected static int dhruvaNetAntaresPort;
  protected static int dhruvaNetCcPort;

  protected static int pstnPort;
  protected static String pstnContactAddr;

  protected static int antaresPort;
  protected static String antaresContactAddr;

  protected static int wxcPort;
  protected static String wxcContactAddr;

  protected static final int timeout = 3000;

  protected SipStack pstnStack;
  protected SipStack antaresStack;
  protected SipStack wxcStack;

  @Autowired private DhruvaCallingTestProperties testPro;

  @PostConstruct
  public void init() {
    testHostAddress = testPro.getTestAddress();
    dhruvaAddress = testPro.getDhruvaAddress();

    dhruvaNetSpPort = testPro.getDhruvaNetSpPort();
    dhruvaNetAntaresPort = testPro.getDhruvaNetAntaresPort();
    dhruvaNetCcPort = testPro.getDhruvaNetCcPort();

    pstnPort = testPro.getTestPstnPort();
    antaresPort = testPro.getTestAntaresPort();
    wxcPort = testPro.getTestWxCPort();

    pstnContactAddr = "sip:pstn-it-guest@" + testHostAddress;
    antaresContactAddr = "sip:antares-it-guest@" + testHostAddress;
    wxcContactAddr = "sip:wxc-it-guest@" + testHostAddress;
  }

  private Properties getProperties(String stackName) {
    Properties props = new Properties();
    props.setProperty("javax.sip.STACK_NAME", stackName);
    props.setProperty("gov.nist.javax.sip.READ_TIMEOUT", "1000");
    props.setProperty("gov.nist.javax.sip.CACHE_SERVER_CONNECTIONS", "false");
    props.setProperty("gov.nist.javax.sip.TRACE_LEVEL", "32");
    props.setProperty("javax.sip.IP_ADDRESS", testHostAddress);
    return props;
  }

  /** Initialize the sipStack and a user agent for the test. */
  public void setUpStacks() throws Exception {
    pstnStack = new SipStack(Token.UDP, pstnPort, getProperties("pstnAgent"));
    antaresStack = new SipStack(Token.UDP, antaresPort, getProperties("antaresAgent"));
    wxcStack = new SipStack(Token.UDP, wxcPort, getProperties("wxcAgent"));
  }

  public void destroyStacks() {
    pstnStack.dispose();
    antaresStack.dispose();
    wxcStack.dispose();
  }
}
