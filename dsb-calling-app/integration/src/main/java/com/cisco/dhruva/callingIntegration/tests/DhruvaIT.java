package com.cisco.dhruva.callingIntegration.tests;

import com.cisco.dhruva.callingIntegration.CallingTestProperties;
import com.cisco.dhruva.callingIntegration.DhruvaTestConfig;
import com.cisco.dhruva.callingIntegration.util.IntegrationTestListener;
import com.cisco.dhruva.callingIntegration.util.TestSuiteListener;
import com.cisco.wx2.test.BaseTestConfig;
import gov.nist.javax.sip.header.HeaderFactoryImpl;
import gov.nist.javax.sip.header.SIPHeader;
import java.io.IOException;
import java.text.ParseException;
import java.util.ArrayList;
import java.util.List;
import java.util.Properties;
import javax.annotation.PostConstruct;
import javax.sip.header.Header;
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

@Listeners({IntegrationTestListener.class, TestSuiteListener.class})
@ContextConfiguration(classes = {BaseTestConfig.class, DhruvaTestConfig.class})
public class DhruvaIT extends AbstractTestNGSpringContextTests {

  public static final String PAID_HEADER = "P-Asserted-Identity";
  public static final String PPID_HEADER = "P-Preferred-Identity";
  public static final String RPID_HEADER = "RPID-Privacy";
  public static final String X_BROADWORKS_DNC = "X-BroadWorks-DNC";
  public static final String X_BROADWORKS_CORRELATION_INFO = "X-BroadWorks-Correlation-Info";
  public static final String DIVERSION = "Diversion";
  protected static Header paidRemote;
  protected static Header ppidRemote;
  protected static Header rpidRemote;
  protected static Header paidLocal;
  protected static Header ppidLocal;
  protected static Header rpidLocal;
  protected static Header xBroadworksDnc;
  protected static Header xBroadWorksCorrelationInfo;
  protected static Header diversionLocal;

  protected static String paidHeaderValueRemote;
  protected static String ppidHeaderValueRemote;
  protected static String rpidHeaderValueRemote;

  protected static String paidHeaderValueLocal;
  protected static String ppidHeaderValueLocal;
  protected static String rpidHeaderValueLocal;

  protected static String diversionValueRemote1;
  protected static String diversionValueRemote2;
  protected static String diversionValueRemote3;
  protected static String diversionValueRemote4;
  protected static String diversionValueLocal;
  protected static List<SIPHeader> diversionRemote;

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

  private static final Logger LOGGER = LoggerFactory.getLogger(DhruvaIT.class);

  @Autowired private CallingTestProperties testPro;

  @PostConstruct
  public void init() throws ParseException {
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

    paidHeaderValueRemote =
        "\"host@pstn.com\" <sip:+10982345764@10.10.10.10:5061;x-cisco-number=+19702870206>";
    ppidHeaderValueRemote = "<sip:+10982345764@10.10.10.10:5061>";
    rpidHeaderValueRemote = "<sip:+10982345764@10.10.10.10:5061>";

    paidHeaderValueLocal =
        "\"host@pstn.com\" <sip:+10982345764@127.0.0.1:5061;x-cisco-number=+19702870206>";
    ppidHeaderValueLocal = "<sip:+10982345764@127.0.0.1:5061>";
    rpidHeaderValueLocal = "<sip:+10982345764@127.0.0.1:5061>";

    antaresARecord = testPro.getAntaresARecord();
    nsARecord = testPro.getNsARecord();
    as1ARecord = testPro.getAs1ARecord();
    as2ARecord = testPro.getAs2ARecord();

    dhruvaPublicUrl = testPro.getDhruvaPublicUrl();
    injectedDnsUuid = testPro.getInjectedDnsUuid();
    sipOverrideUrl = dhruvaPublicUrl + "/admin/SipRoutingOverrides/" + injectedDnsUuid;

    HeaderFactoryImpl headerFactory = new HeaderFactoryImpl();
    paidRemote = headerFactory.createHeader(PAID_HEADER, paidHeaderValueRemote);
    ppidRemote = headerFactory.createHeader(PPID_HEADER, ppidHeaderValueRemote);
    rpidRemote = headerFactory.createHeader(RPID_HEADER, rpidHeaderValueRemote);
    paidLocal = headerFactory.createHeader(PAID_HEADER, paidHeaderValueLocal);
    ppidLocal = headerFactory.createHeader(PPID_HEADER, ppidHeaderValueLocal);
    rpidLocal = headerFactory.createHeader(RPID_HEADER, rpidHeaderValueLocal);
    xBroadworksDnc =
        headerFactory.createHeader(
            X_BROADWORKS_DNC,
            "network-address=\"sip:+15085431199@10.21.0.214;user=phone\";"
                + "user-id=\"ciy6vwddyv@31134724.cisco-bcld.com\";net-ind=InterNetwork");
    xBroadWorksCorrelationInfo =
        headerFactory.createHeader(
            X_BROADWORKS_CORRELATION_INFO, "279bcde4-62aa-453a-a0d6-8dadd338fb82");
    diversionValueRemote1 = "<sip:+10982345764@1.1.1.1:5061>";
    diversionValueRemote2 = "<sip:+10982345764@2.2.2.2:5061>";
    diversionValueRemote3 = "<sip:+10982345764@3.3.3.3:5061>";
    diversionValueRemote4 = "<sip:+10982345764@3.3.3.3:5061>";
    diversionRemote = new ArrayList<>();
    diversionRemote.add((SIPHeader) headerFactory.createHeader(DIVERSION, diversionValueRemote1));
    diversionRemote.add((SIPHeader) headerFactory.createHeader(DIVERSION, diversionValueRemote2));
    diversionRemote.add((SIPHeader) headerFactory.createHeader(DIVERSION, diversionValueRemote3));
    diversionRemote.add((SIPHeader) headerFactory.createHeader(DIVERSION, diversionValueRemote4));
    diversionValueLocal = "<sip:+10982345764@127.0.0.1:5061>";
    diversionLocal = headerFactory.createHeader(DIVERSION, diversionValueLocal);
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
      if (response != null && response.getStatusLine().getStatusCode() == 200) {
        LOGGER.info("Sip override Successful. Response = {}", response);
      } else {
        LOGGER.info("Sip override failed. Response = {}", response);
      }
      httpClient.close();
    } catch (Exception ex) {
      LOGGER.error("Sip override failed!!! Exception occurred during dns injection : " + ex);
      httpClient.close();
      throw ex;
    }
  }

  public void deleteDns() throws IOException {
    CloseableHttpClient httpClient = HttpClientBuilder.create().build();
    try {
      java.net.URI deleteDnsUri = java.net.URI.create(sipOverrideUrl);
      HttpDelete dns = new HttpDelete(deleteDnsUri);
      CloseableHttpResponse response = httpClient.execute(dns);
      if (response != null && response.getStatusLine().getStatusCode() == 200) {
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
