package com.cisco.dhruva.callingIntegration.tests;

import com.cisco.dhruva.callingIntegration.DhruvaCallingTestProperties;
import com.cisco.dhruva.callingIntegration.DhruvaTestConfig;
import com.cisco.dhruva.callingIntegration.util.IntegrationTestListener;
import com.cisco.wx2.test.BaseTestConfig;
import java.io.IOException;
import java.util.Properties;
import javax.annotation.PostConstruct;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpDelete;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.entity.StringEntity;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClientBuilder;
import org.apache.http.message.BasicHeader;
import org.apache.http.protocol.HTTP;
import org.cafesip.sipunit.SipStack;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
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

  protected static int pstnUsPoolBPort;
  protected static int pstnUsPoolASg1Port;
  protected static int pstnUsPoolASg2Port;
  protected static String pstnContactAddr;

  protected static int antaresPort;
  protected static String antaresContactAddr;
  protected static String antaresARecord;

  protected static int nsPort;
  protected static int as1Port;
  protected static int as2Port;
  protected static String wxcContactAddr;
  protected static String nsARecord;
  protected static String as1ARecord;
  protected static String as2ARecord;

  protected static final int timeout = 3000;

  protected static String dhruvaPublicUrl;
  protected static String injectedDnsUuid;
  protected static String sipOverrideUrl;

  protected SipStack pstnUsPoolBStack;
  protected SipStack pstnUsPoolASg1Stack;
  protected SipStack pstnUsPoolASg2Stack;
  protected SipStack antaresStack;
  protected SipStack nsStack;
  protected SipStack as1Stack;
  protected SipStack as2Stack;

  private static final Logger LOGGER = LoggerFactory.getLogger(DialInIT.class);

  @Autowired private DhruvaCallingTestProperties testPro;

  @PostConstruct
  public void init() {
    testHostAddress = testPro.getTestAddress();
    dhruvaAddress = testPro.getDhruvaAddress();

    dhruvaNetSpPort = testPro.getDhruvaNetSpPort();
    dhruvaNetAntaresPort = testPro.getDhruvaNetAntaresPort();
    dhruvaNetCcPort = testPro.getDhruvaNetCcPort();

    pstnUsPoolBPort = testPro.getTestPstnUsPoolBPort();
    pstnUsPoolASg1Port = testPro.getTestPstnUsPoolASG1Port();
    pstnUsPoolASg2Port = testPro.getTestPstnUsPoolASG2Port();

    antaresPort = testPro.getTestAntaresPort();
    nsPort = testPro.getTestNsPort();
    as1Port = testPro.getTestAs1Port();
    as2Port = testPro.getTestAs2Port();

    pstnContactAddr = "sip:pstn-it-guest@" + testHostAddress;
    antaresContactAddr = "sip:antares-it-guest@" + testHostAddress;
    wxcContactAddr = "sip:wxc-it-guest@" + testHostAddress;

    antaresARecord = testPro.getAntaresARecord();
    nsARecord = testPro.getNsARecord();
    as1ARecord = testPro.getAs1ARecord();
    as2ARecord = testPro.getAs2ARecord();

    dhruvaPublicUrl = testPro.getDhruvaPublicUrl();
    injectedDnsUuid = testPro.getInjectedDnsUuid();
    sipOverrideUrl = dhruvaPublicUrl + "/admin/SipRoutingOverrides/" + injectedDnsUuid;
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

  public void injectDNS() throws IOException {
    LOGGER.info("Sip override url : {}", sipOverrideUrl);
    CloseableHttpClient httpClient = HttpClientBuilder.create().build();
    try {
      java.net.URI dnsUri = java.net.URI.create(sipOverrideUrl);
      HttpPost dns = new HttpPost(dnsUri);
      String body =
          "{"
              + "\"dnsARecords\": "
              + "["
              + "{"
              + "\"name\": \""
              + antaresARecord
              + "\","
              + "\"ttl\": 36000,"
              + "\"address\": \""
              + testHostAddress
              + "\","
              + "\"injectAction\": 2"
              + "},"
              + "{"
              + "\"name\": \""
              + nsARecord
              + "\","
              + "\"ttl\": 36000,"
              + "\"address\": \""
              + testHostAddress
              + "\","
              + "\"injectAction\": 2"
              + "},"
              + "{"
              + "\"name\": \""
              + as1ARecord
              + "\","
              + "\"ttl\": 3600,"
              + "\"address\": \""
              + testHostAddress
              + "\","
              + "\"injectAction\": 2"
              + "},"
              + "{"
              + "\"name\": \""
              + as2ARecord
              + "\","
              + "\"ttl\": 3600,"
              + "\"address\": \""
              + testHostAddress
              + "\","
              + "\"injectAction\": 2"
              + "}"
              + "],"
              + "\"dnsSRVRecords\": [{}]}'";
      StringEntity se = new StringEntity(body);
      se.setContentType(new BasicHeader(HTTP.CONTENT_TYPE, "application/json"));
      dns.setEntity(se);
      CloseableHttpResponse response = httpClient.execute(dns);
      LOGGER.info("Sip override Response : {}", response);
      if (response.getStatusLine().getStatusCode() == 200) {
        LOGGER.info("Sip override Successful. Response = {}", response);
      } else {
        LOGGER.info("Sip override failed. Response = {}", response);
      }
    } catch (Exception ex) {
      // handle exception here
    } finally {
      httpClient.close();
    }
  }

  public void deleteDns() throws IOException {
    CloseableHttpClient httpClient = HttpClientBuilder.create().build();
    try {
      java.net.URI deleteDnsUri = java.net.URI.create(sipOverrideUrl);
      HttpDelete dns = new HttpDelete(deleteDnsUri);
      CloseableHttpResponse response = httpClient.execute(dns);
      LOGGER.info("Sip override delete Response : {}", response);
      if (response.getStatusLine().getStatusCode() == 200) {
        LOGGER.info("Sip override delete Successful. Response = {}", response);
      } else {
        LOGGER.info("Sip override delete failed. Response = {}", response);
      }
    } catch (Exception ex) {
      // handle exception here
    } finally {
      httpClient.close();
    }
  }
}
