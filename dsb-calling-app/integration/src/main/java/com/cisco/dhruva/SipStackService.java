package com.cisco.dhruva;

import com.cisco.dhruva.util.Token;
import java.util.Properties;
import javax.annotation.PostConstruct;
import org.cafesip.sipunit.SipStack;
import org.springframework.stereotype.Component;

@Component
public class SipStackService {
  private static SipStack sipStack;
  private Properties properties;

  private DhruvaTestProperties testPro = new DhruvaTestProperties();
  private String testHost = testPro.getTestAddress();
  private int testUdpPort = testPro.getTestUdpPort();
  private int testTcpPort = testPro.getTestTcpPort();
  private int testTlsPort = testPro.getTestTlsPort();

  @PostConstruct
  public void init() {
    properties = new Properties();
    properties.setProperty("javax.sip.STACK_NAME", "TestDhruva");
    properties.setProperty("javax.sip.IP_ADDRESS", testHost);
  }

  public SipStack getSipStackUdp() throws Exception {
    if (sipStack != null) {
      sipStack.dispose();
    }
    properties.setProperty("javax.sip.IP_ADDRESS", testHost);
    sipStack = new SipStack(Token.UDP, testUdpPort, properties);
    return sipStack;
  }

  public SipStack getSipStackTcp() throws Exception {
    if (sipStack != null) {
      sipStack.dispose();
    }
    properties.setProperty("javax.sip.IP_ADDRESS", testHost);
    sipStack = new SipStack(Token.TCP, testTcpPort, properties);
    return sipStack;
  }

  public SipStack getSipStackTls() throws Exception {
    if (sipStack != null) {
      sipStack.dispose();
    }
    properties.setProperty("javax.sip.IP_ADDRESS", testHost);
    sipStack = new SipStack(Token.TLS, testTlsPort, properties);
    return sipStack;
  }
}
