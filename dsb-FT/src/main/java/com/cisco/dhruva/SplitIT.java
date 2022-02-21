package com.cisco.dhruva;

import static org.testng.Assert.*;

import com.cisco.dhruva.util.TestInput;
import com.cisco.dhruva.util.Token;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.IOException;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.text.ParseException;
import java.util.Map;
import java.util.Properties;
import javax.sip.InvalidArgumentException;
import javax.sip.message.Response;
import org.cafesip.sipunit.SipCall;
import org.cafesip.sipunit.SipPhone;
import org.cafesip.sipunit.SipStack;
import org.json.simple.JSONObject;
import org.json.simple.parser.JSONParser;
import org.testng.ITestContext;
import org.testng.annotations.AfterClass;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.Ignore;
import org.testng.annotations.Test;

@Ignore
public class SplitIT {

  private TestInput testCases;
  private SipStack sipStack1;
  private SipStack sipStack2;

  private SipPhone ua;
  private SipPhone ub;

  private static final Properties defaultProperties1 = new Properties();
  private static final Properties defaultProperties2 = new Properties();

  static {
    defaultProperties1.setProperty("javax.sip.STACK_NAME", "testAgent1");
    // By default, the trace level is 16 which shows the messages and their contents.
    defaultProperties1.setProperty("gov.nist.javax.sip.TRACE_LEVEL", "32");
    // Additionally, the gov.nist.javax.sip.TRACE_LEVEL property should be 32 for debugging to be
    // enabled.
    defaultProperties1.setProperty("gov.nist.javax.sip.DEBUG_LOG", "testAgent1_debug.txt");
    // Additionally, the gov.nist.javax.sip.TRACE_LEVEL should be 16 or higher for this to be
    // available
    defaultProperties1.setProperty("gov.nist.javax.sip.SERVER_LOG", "testAgent1_log.txt");
    defaultProperties1.setProperty("gov.nist.javax.sip.READ_TIMEOUT", "1000");
    defaultProperties1.setProperty("gov.nist.javax.sip.CACHE_SERVER_CONNECTIONS", "false");
    //    defaultProperties1.setProperty("javax.sip.IP_ADDRESS", "127.0.0.1");

    defaultProperties2.setProperty("javax.sip.STACK_NAME", "testAgent2");
    //    defaultProperties2.setProperty("javax.sip.IP_ADDRESS", "127.0.0.1");
    defaultProperties2.setProperty("gov.nist.javax.sip.TRACE_LEVEL", "32");
    defaultProperties2.setProperty("gov.nist.javax.sip.DEBUG_LOG", "testAgent2_debug.txt");
    defaultProperties2.setProperty("gov.nist.javax.sip.SERVER_LOG", "testAgent2_log.txt");
    defaultProperties2.setProperty("gov.nist.javax.sip.READ_TIMEOUT", "1000");
    defaultProperties2.setProperty("gov.nist.javax.sip.CACHE_SERVER_CONNECTIONS", "false");
  }

  private Properties properties1 = new Properties(defaultProperties1);
  private Properties properties2 = new Properties(defaultProperties2);

  private void readTestcasesJsonFile()
      throws FileNotFoundException, IOException, ParseException,
          org.json.simple.parser.ParseException {
    String testFilePath = SplitIT.class.getClassLoader().getResource("testcases.json").getPath();
    ;
    ObjectMapper mapper = new ObjectMapper();
    JSONParser parser = new JSONParser();
    Object object = parser.parse(new FileReader(testFilePath));
    JSONObject jsonObject = (JSONObject) object;
    testCases = (TestInput) mapper.readValue(jsonObject.toJSONString(), TestInput.class);

    Map<String, Object> suiteSpecificCpProperties = testCases.getCpProperties();
    if (suiteSpecificCpProperties == null || suiteSpecificCpProperties.isEmpty()) {
      System.out.println("No suite specific CP properties defined");
    } else {
      suiteSpecificCpProperties.forEach(
          (property, value) -> System.setProperty(property, value.toString()));
    }
    System.out.println("Input JSON: \n" + jsonObject.toJSONString());
  }

  /** Initialize the sipStack and a user agent for the test. */
  @BeforeClass
  public void setUp(ITestContext context) throws Exception {
    sipStack1 = new SipStack(Token.TCP, 5000, properties1);
    sipStack2 = new SipStack(Token.TCP, 7000, properties2);
    // stack 1 and 2 has LPs with IP=InetAddress.getLocalHost().getHostAddress()
    SipStack.setTraceEnabled(true);

    readTestcasesJsonFile();
  }

  @AfterClass
  public void tearDown() {
    ua.dispose();
    sipStack1.dispose();
  }

  @Test
  public void testBasicInviteFlow()
      throws InvalidArgumentException, ParseException, UnknownHostException {
    // A -> PSTN
    // B -> AS
    System.out.println("KALPA: ip: " + InetAddress.getLocalHost().getHostAddress());

    // When ua wants to send to Proxy
    // ua = sipStack1.createSipPhone("127.0.0.1", Token.TCP, 7000, "sip:pstn-it-guest@" +
    // testHostAddr + ":5000");
    ua = sipStack1.createSipPhone("sip:pstn-it-guest@nist.gov");
    ub = sipStack2.createSipPhone("sip:as-it-guest@192.168.1.8:7000");
    ub.setLoopback(
        true); // this is for direct UA-UA testing (without proxy) - should not be the default

    SipCall callA = ua.createSipCall();
    SipCall callB = ub.createSipCall();

    /*
       1. Make B listen for calls
       2. A -> send INVITE -> B
    */
    callB.listenForIncomingCall(); // Starting from the time this method is called, any received
    // request(s) for this UA are collected
    // if you are interested to add any other headers to the msg, use other initiateOutgoingCall()
    // -> check it

    callA.initiateOutgoingCall(
        "sip:as-it-guest@192.168.1.5:7000", "127.0.0.1" + ":" + 5061 + ";lr/" + Token.TCP);
    //    assertLastOperationSuccess("initiate call - " + callA.format(), callA);

    // B gets INVITE
    callB.waitForIncomingCall(3000);
    //    assertLastOperationSuccess("b wait incoming call - " + callB.format(), callB);

    // Meanwhile A gets 100
    callA.waitOutgoingCallResponse(3000);
    //    assertLastOperationSuccess("await 100 response - " + callA.format(), callA);
    //    assertEquals(Response.TRYING, callA.getLastReceivedResponse().getStatusCode());

    // B -> sends 180 -> A
    callB.sendIncomingCallResponse(Response.RINGING, null, -1);
    //    assertLastOperationSuccess("b send RINGING - " + callB.format(), callB);
    // A receives 180
    callA.waitOutgoingCallResponse(3000);
    //    assertLastOperationSuccess("await 180 response - " + callA.format(), callA);
    //    assertEquals(Response.RINGING, callA.getLastReceivedResponse().getStatusCode());

    // B -> sends 200 -> A
    callB.sendIncomingCallResponse(Response.OK, null, -1);
    //    assertLastOperationSuccess("b send OK - " + callB.format(), callB);
    // A receives 200
    callA.waitOutgoingCallResponse(3000);
    //    assertLastOperationSuccess("await 200 response - " + callA.format(), callA);
    assertEquals(Response.OK, callA.getLastReceivedResponse().getStatusCode());

    // Make B listen for ACK
    callB.listenForAck();
    // A -> sends ACK -> B
    callA.sendInviteOkAck();
    //    assertLastOperationSuccess("Failure sending ACK - " + callA.format(), callA);
    // B receives ACK
    callB.waitForAck(3000);
    //    assertLastOperationSuccess("await ACK - " + callB.format(), callB);

    // Make B wait for BYE from A
    callB.listenForDisconnect();
    //    assertLastOperationSuccess("b listen disc - " + callB.format(), callB);
    // A -> sends BYE -> B
    callA.disconnect();
    //    assertLastOperationSuccess("a disc - " + callA.format(), callA);
    // B receives BYE
    callB.waitForDisconnect(3000);
    //    assertLastOperationSuccess("b wait disc - " + callB.format(), callB);

    ub.dispose();
    sipStack2.dispose();
  }
}
