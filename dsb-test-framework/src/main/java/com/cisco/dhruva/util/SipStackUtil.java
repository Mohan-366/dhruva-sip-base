package com.cisco.dhruva.util;

import com.cisco.dhruva.input.TestInput.Transport;
import java.util.HashMap;
import java.util.Map;
import java.util.Properties;
import org.cafesip.sipunit.SipStack;

public class SipStackUtil {

  private static Map<String, SipStack> sipStackUAC = new HashMap<>();
  private static Map<String, SipStack> sipStackUAS = new HashMap<>();
  private static int uasStackCount = 0;

  public static SipStack getSipStackUAC(String ip, int port, Transport transport) throws Exception {
    return getSipStack(true, ip, port, transport);
  }

  public static SipStack getSipStackUAS(String ip, int port, Transport transport) throws Exception {
    return getSipStack(false, ip, port, transport);
  }

  private static SipStack getSipStack(boolean isUac, String ip, int port, Transport transport)
      throws Exception {
    String key = ip + ":" + port + ":" + transport;
    Map<String, SipStack> sipStack = isUac ? sipStackUAC : sipStackUAS;
    boolean lpExists = sipStack.entrySet().stream().anyMatch(entry -> entry.getKey().equals(key));
    if (lpExists) {
      return sipStack.get(key);
    }
    Properties properties = getProperties(isUac, ip);
    SipStack sipStackNew = new SipStack(transport.name(), port, properties);
    sipStack.put(key, sipStackNew);
    return sipStackNew;
  }

  private static Properties getProperties(boolean isUac, String ip) {
    String testAgent;
    if (isUac) {
      testAgent = "testAgentUAC";
    } else {
      testAgent = "testAgentUAS_" + ++uasStackCount;
    }
    Properties properties = new Properties();
    System.out.println("KALPA: STACK_NAME: " + testAgent);
    properties.setProperty("javax.sip.STACK_NAME", testAgent);
    properties.setProperty("gov.nist.javax.sip.TRACE_LEVEL", "32");
    properties.setProperty("gov.nist.javax.sip.DEBUG_LOG", testAgent + "_debug.txt");
    properties.setProperty("gov.nist.javax.sip.SERVER_LOG", testAgent + "_log.txt");
    properties.setProperty("gov.nist.javax.sip.READ_TIMEOUT", "1000");
    properties.setProperty("gov.nist.javax.sip.CACHE_SERVER_CONNECTIONS", "false");
    properties.setProperty("javax.sip.IP_ADDRESS", ip);
    return properties;
  }
}
