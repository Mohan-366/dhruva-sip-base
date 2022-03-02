package com.cisco.dhruva;

import static org.testng.Assert.*;

import com.cisco.dhruva.callingIntegration.util.Token;
import java.text.ParseException;
import java.util.Properties;
import javax.sip.InvalidArgumentException;
import javax.sip.message.Response;
import org.cafesip.sipunit.SipCall;
import org.cafesip.sipunit.SipPhone;
import org.cafesip.sipunit.SipStack;
import org.testng.annotations.AfterClass;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.Test;

// import static org.cafesip.sipunit.SipAssert.assertLastOperationSuccess;

public class SplitIT {

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

    defaultProperties2.setProperty("javax.sip.STACK_NAME", "testAgent2");
    defaultProperties2.setProperty("gov.nist.javax.sip.TRACE_LEVEL", "32");
    defaultProperties2.setProperty("gov.nist.javax.sip.DEBUG_LOG", "testAgent2_debug.txt");
    defaultProperties2.setProperty("gov.nist.javax.sip.SERVER_LOG", "testAgent2_log.txt");
    defaultProperties2.setProperty("gov.nist.javax.sip.READ_TIMEOUT", "1000");
    defaultProperties2.setProperty("gov.nist.javax.sip.CACHE_SERVER_CONNECTIONS", "false");
  }

  private Properties properties1 = new Properties(defaultProperties1);
  private Properties properties2 = new Properties(defaultProperties2);

  /** Initialize the sipStack and a user agent for the test. */
  @BeforeClass
  public void setUp() throws Exception {
    sipStack1 = new SipStack(Token.TCP, 5000, properties1);
    sipStack2 = new SipStack(Token.TCP, 7000, properties2);
    // stack 1 and 2 has LPs with IP=InetAddress.getLocalHost().getHostAddress()
    SipStack.setTraceEnabled(true);
  }

  @AfterClass
  public void tearDown() {
    ua.dispose();
    sipStack1.dispose();
  }

  @Test
  public void testCallingPstnToNs() throws InvalidArgumentException, ParseException {
    // A -> PSTN
    // B -> NS

    // When ua wants to send to Proxy
    // ua = sipStack1.createSipPhone("127.0.0.1", Token.TCP, 7000, "sip:pstn-it-guest@" +
    // testHostAddr + ":5000");
    ua = sipStack1.createSipPhone("sip:pstn-it-guest@nist.gov");
    ub = sipStack2.createSipPhone("sip:ns-it-guest@nist.gov");
    ub.setLoopback(
        true); // this is for direct UA-UA testing (without proxy) - should not be the default

    SipCall callA = ua.createSipCall();
    SipCall callB = ub.createSipCall();

    // Make B listen for calls
    // A -> send INVITE -> B
    callB.listenForIncomingCall(); // Starting from the time this method is called, any received
    // request(s) for this UA are collected
    // if you are interested to add any other headers to the msg, use other initiateOutgoingCall()
    // -> check it
    callA.initiateOutgoingCall(
        "sip:ns-it-guest@nist.gov", ub.getStackAddress() + ":" + 7000 + ";lr/" + Token.TCP);
    //    assertLastOperationSuccess("initiate call - " + callA.format(), callA);

    // B gets INVITE
    callB.waitForIncomingCall(3000);
    //    assertLastOperationSuccess("b wait incoming call - " + callB.format(), callB);

    // B -> sends 302 -> Dhruva
    callB.sendIncomingCallResponse(Response.MOVED_TEMPORARILY, null, -1);
    //    assertLastOperationSuccess("b send 302 - " + callB.format(), callB);

    ub.dispose();
    //    sipStack2.dispose();
  }

  @Test
  public void testCallingPstnToAs() throws InvalidArgumentException, ParseException {
    // A -> PSTN
    // B -> AS

    // When ua wants to send to Proxy
    // ua = sipStack1.createSipPhone("127.0.0.1", Token.TCP, 7000, "sip:pstn-it-guest@" +
    // testHostAddr + ":5000");
    ua = sipStack1.createSipPhone("sip:pstn-it-guest@nist.gov");
    ub = sipStack2.createSipPhone("sip:as-it-guest@nist.gov");
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
        "sip:as-it-guest@nist.gov", ub.getStackAddress() + ":" + 7000 + ";lr/" + Token.TCP);
    //    assertLastOperationSuccess("initiate call - " + callA.format(), callA);

    // B gets INVITE
    callB.waitForIncomingCall(3000);
    //    assertLastOperationSuccess("b wait incoming call - " + callB.format(), callB);

    // Meanwhile A gets 100
    callA.waitOutgoingCallResponse(3000);
    //    assertLastOperationSuccess("await 100 response - " + callA.format(), callA);
    assertEquals(Response.TRYING, callA.getLastReceivedResponse().getStatusCode());

    // B -> sends 180 -> A
    callB.sendIncomingCallResponse(Response.RINGING, null, -1);
    //    assertLastOperationSuccess("b send RINGING - " + callB.format(), callB);
    // A receives 180
    callA.waitOutgoingCallResponse(3000);
    //    assertLastOperationSuccess("await 180 response - " + callA.format(), callA);
    assertEquals(Response.RINGING, callA.getLastReceivedResponse().getStatusCode());

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
    //    sipStack2.dispose();
  }
}
