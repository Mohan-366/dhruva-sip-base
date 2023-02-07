package com.cisco.dhruva.util;

import com.cisco.dhruva.input.TestInput.Transport;
import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import java.io.FileNotFoundException;
import java.util.HashMap;
import java.util.Map;
import java.util.Properties;
import org.cafesip.sipunit.SipStack;
import org.springframework.util.ResourceUtils;

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
    if (transport.equals(Transport.tls)) {
      addTlsProps(properties);
    }
    SipStack sipStackNew = new SipStack(transport.name(), port, properties);
    sipStack.put(key, sipStackNew);
    return sipStackNew;
  }

  @SuppressFBWarnings(
      value = {"HARD_CODE_PASSWORD"},
      justification = "baseline suppression")
  private static void addTlsProps(Properties properties) throws FileNotFoundException {

    properties.setProperty("gov.nist.javax.sip.TLS_CLIENT_AUTH_TYPE", "Disabled");
    properties.setProperty("gov.nist.javax.sip.TLS_CLIENT_PROTOCOLS", "TLSv1.2");
    properties.setProperty(
        "gov.nist.javax.sip.ENABLED_CIPHER_SUITES",
        "TLS_ECDHE_ECDSA_WITH_AES_256_GCM_SHA384,TLS_ECDHE_ECDSA_WITH_AES_128_GCM_SHA256,TLS_ECDHE_ECDSA_WITH_AES_256_CBC_SHA384,TLS_ECDHE_ECDSA_WITH_AES_128_CBC_SHA256,TLS_ECDHE_RSA_WITH_AES_256_GCM_SHA384,TLS_ECDHE_RSA_WITH_AES_128_GCM_SHA256,TLS_ECDHE_RSA_WITH_AES_256_CBC_SHA384,TLS_ECDHE_RSA_WITH_AES_128_CBC_SHA256,TLS_RSA_WITH_AES_256_GCM_SHA384,TLS_RSA_WITH_AES_256_CBC_SHA256,TLS_RSA_WITH_AES_128_GCM_SHA256,TLS_RSA_WITH_AES_128_CBC_SHA256,TLS_DHE_DSS_WITH_AES_256_GCM_SHA384,TLS_DHE_RSA_WITH_AES_256_CBC_SHA256,TLS_DHE_DSS_WITH_AES_256_CBC_SHA256,TLS_DHE_DSS_WITH_AES_128_GCM_SHA256,TLS_DHE_DSS_WITH_AES_128_CBC_SHA256");
    String absFilePath = getAbsFilePath("classpath:tlscert/ts.p12");

    properties.setProperty("javax.net.ssl.keyStore", absFilePath);
    properties.setProperty("javax.net.ssl.keyStorePassword", "dsb123");
    properties.setProperty("javax.net.ssl.keyStoreType", "pkcs12");
    properties.setProperty("javax.net.ssl.trustStore", absFilePath);
    properties.setProperty("javax.net.ssl.trustStorePassword", "dsb123");
    properties.setProperty("javax.net.ssl.trustStoreType", "pkcs12");
  }

  private static Properties getProperties(boolean isUac, String ip) {
    String testAgent;
    if (isUac) {
      testAgent = "testAgentUAC";
    } else {
      testAgent = "testAgentUAS_" + ++uasStackCount;
    }
    Properties properties = new Properties();
    properties.setProperty("javax.sip.STACK_NAME", testAgent);
    properties.setProperty("gov.nist.javax.sip.TRACE_LEVEL", "32");
    properties.setProperty("gov.nist.javax.sip.DEBUG_LOG", testAgent + "_debug.txt");
    properties.setProperty("gov.nist.javax.sip.SERVER_LOG", testAgent + "_log.txt");
    properties.setProperty("gov.nist.javax.sip.READ_TIMEOUT", "1000");
    properties.setProperty("gov.nist.javax.sip.CACHE_SERVER_CONNECTIONS", "false");
    properties.setProperty("javax.sip.IP_ADDRESS", ip);
    return properties;
  }

  private static String getAbsFilePath(String filePath) throws FileNotFoundException {
    String keyAbsPath = ResourceUtils.getFile(filePath).getAbsolutePath();
    return keyAbsPath;
  }
}
