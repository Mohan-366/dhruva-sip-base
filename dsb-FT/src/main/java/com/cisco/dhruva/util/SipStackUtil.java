package com.cisco.dhruva.util;

import static org.testng.Assert.assertTrue;

import com.cisco.dhruva.util.TestInput.Direction;
import com.cisco.dhruva.util.TestInput.Message;
import com.cisco.dhruva.util.TestInput.NicIpPort;
import com.cisco.dhruva.util.TestInput.Transport;
import com.cisco.dhruva.util.TestInput.Type;
import java.util.HashMap;
import java.util.Map;
import java.util.Properties;
import javax.sip.message.Response;
import org.cafesip.sipunit.SipCall;
import org.cafesip.sipunit.SipStack;

public class SipStackUtil {
  private static Map<String, SipStack> sipStackUAC = new HashMap<>();
  private static Map<String, SipStack> sipStackUAS = new HashMap<>();

  private static final Properties defaultPropertiesUAC = new Properties();
  private static final Properties defaultPropertiesUAS = new Properties();

  static {
    defaultPropertiesUAC.setProperty("javax.sip.STACK_NAME", "testAgent1");
    // By default, the trace level is 16 which shows the messages and their contents.
    defaultPropertiesUAC.setProperty("gov.nist.javax.sip.TRACE_LEVEL", "32");
    // Additionally, the gov.nist.javax.sip.TRACE_LEVEL property should be 32 for debugging to be
    // enabled.
    defaultPropertiesUAC.setProperty("gov.nist.javax.sip.DEBUG_LOG", "testAgent1_debug.txt");
    // Additionally, the gov.nist.javax.sip.TRACE_LEVEL should be 16 or higher for this to be
    // available
    defaultPropertiesUAC.setProperty("gov.nist.javax.sip.SERVER_LOG", "testAgent1_log.txt");
    defaultPropertiesUAC.setProperty("gov.nist.javax.sip.READ_TIMEOUT", "1000");
    defaultPropertiesUAC.setProperty("gov.nist.javax.sip.CACHE_SERVER_CONNECTIONS", "false");
    //    defaultProperties1.setProperty("javax.sip.IP_ADDRESS", "127.0.0.1");

    defaultPropertiesUAS.setProperty("javax.sip.STACK_NAME", "testAgent2");
    //    defaultProperties2.setProperty("javax.sip.IP_ADDRESS", "127.0.0.1");
    defaultPropertiesUAS.setProperty("gov.nist.javax.sip.TRACE_LEVEL", "32");
    defaultPropertiesUAS.setProperty("gov.nist.javax.sip.DEBUG_LOG", "testAgent2_debug.txt");
    defaultPropertiesUAS.setProperty("gov.nist.javax.sip.SERVER_LOG", "testAgent2_log.txt");
    defaultPropertiesUAS.setProperty("gov.nist.javax.sip.READ_TIMEOUT", "1000");
    defaultPropertiesUAS.setProperty("gov.nist.javax.sip.CACHE_SERVER_CONNECTIONS", "false");
  }

  private static Properties propertiesUAC = new Properties(defaultPropertiesUAC);
  private static Properties propertiesUAS = new Properties(defaultPropertiesUAS);

  public static SipStack getSipStackUAC(int port, Transport transport) throws Exception {
    String key = port + ":" + transport;
    boolean lpExists = sipStackUAC.entrySet().stream().anyMatch(entry -> entry.getKey() == key);
    if (lpExists) {
      return sipStackUAC.get(key);
    }
    SipStack sipStack = new SipStack(transport.name(), port, propertiesUAC);
    sipStackUAC.put(key, sipStack);
    return sipStack;
  }

  public static SipStack getSipStackUAS(int port, Transport transport) throws Exception {
    String key = port + ":" + transport;
    boolean lpExists = sipStackUAS.entrySet().stream().anyMatch(entry -> entry.getKey() == key);
    if (lpExists) {
      return sipStackUAS.get(key);
    }
    SipStack sipStack = new SipStack(transport.name(), port, propertiesUAS);
    sipStackUAS.put(key, sipStack);
    return sipStack;
  }

  public static void actOnMessage(
      Message message, SipCall call, String stackIp, boolean isUac, NicIpPort proxyCommunication) {
    System.out.println("UAS: " + message);
    if (message.getDirection() == Direction.sends) {
      if (message.getType().equals(Type.request)) {
        if (message.getName().equalsIgnoreCase("INVITE")) {
          String ruri =
              message.parameters.getRequestParameters().getHeaderAdditions().get("requestUri");
          if (ruri == null) {
            ruri = "sip:" + (isUac ? "uas@" : "uac@") + stackIp + ":" + "5061";
          }
          if (proxyCommunication == null) {
            System.out.println(
                "proxy communication is null.If, UAC please add clientCommunicationInfo in config. If UAS, you mustn't be here!");
          } else {
            if (!call.initiateOutgoingCall(
                ruri,
                proxyCommunication.getIp()
                    + ":"
                    + proxyCommunication.getPort()
                    + ";lr/"
                    + Token.TCP)) {
              System.out.println("Unable to initiate a call. Ending test.");
            } else {
              System.out.println("UAC: Sending INVITE message: ");
            }
          }
        } else if (message.getName().equalsIgnoreCase("ACK")) {
          System.out.println("UAC: Sending ACK message: ");
          while (true) {
            if (call.getLastReceivedResponse() != null
                && (call.getLastReceivedResponse().getStatusCode() >= 200)) {
              if (!call.sendInviteOkAck()) {
                System.out.println("Unnable to send ACK for INVITE");
              }
              break;
            }
          }
        } else if (message.getName().equalsIgnoreCase("BYE")) {
          System.out.println("UAC: Sending BYE message: ");
          if (!call.disconnect()) {
            System.out.println("unable to disconnect the call");
          }
        }
      } else { // message is a response

        String reasonPhrase = message.getParameters().getResponseParameters().getResponsePhrase();
        String forRequest = message.getForRequest();
        if (reasonPhrase.equalsIgnoreCase("Ringing")) {
          while (!call.sendIncomingCallResponse(Response.RINGING, null, -1)) {
            System.out.println("UAS: Trying to send 180 to client");
          }
        } else if (reasonPhrase.equalsIgnoreCase("OK") && forRequest.equalsIgnoreCase("INVITE")) {
          while (!call.sendIncomingCallResponse(Response.OK, null, -1)) {
            System.out.println("UAS: Trying to send 200 to client");
          }
        } else {
          call.sendMessageResponse(
              Integer.parseInt(message.getParameters().getResponseParameters().getResponseCode()),
              message.getParameters().getResponseParameters().getResponsePhrase(),
              -1);
        }
      }
    } else { // direction is receives
      if (message.getType().equals(Type.request)) {
        if (message.getName().equalsIgnoreCase("INVITE")) {
          while (true) {
            if (call.waitForIncomingCall(1000)) {
              System.out.println("Received INVITE request");
              break;
            }
          }
        } else if (message.getName().equalsIgnoreCase("ACK")) {
          call.listenForAck();
          call.waitForAck(3000);
          System.out.println("UAS: messages received: " + call.getAllReceivedRequests());
          //                    assertTrue(callA.getLastReceivedRequest().isAck());
        } else if (message.getName().equalsIgnoreCase("BYE")) {
          if (call.listenForDisconnect()) {
            while (!call.waitForDisconnect(3000)) {}
            assertTrue(call.getLastReceivedRequest().isBye());
            call.respondToDisconnect();
          }
        }
      } else {
        while (true) {
          call.waitOutgoingCallResponse(1000);
          if (call.getLastReceivedResponse() != null
              && call.getLastReceivedResponse().getStatusCode()
                  == (Integer) message.getValidation().get("responseCode")
              && call.getLastReceivedResponse()
                  .getResponseEvent()
                  .getResponse()
                  .getHeader("CSeq")
                  .toString()
                  .contains(message.getForRequest())) {
            System.out.println(
                "Received response: "
                    + call.getLastReceivedResponse().getResponseEvent().getResponse());
            break;
          }
        }
      }
    }
  }

  public static void actOnMessage2(
      Message message, SipCall call, String stackIp, boolean isUac, NicIpPort proxyCommunication) {
    System.out.println("UAS: " + message);
    if (message.getDirection() == Direction.sends) {
      if (message.getType().equals(Type.request)) {
        if (message.getName().equalsIgnoreCase("INVITE")) {
          String ruri =
              message.parameters.getRequestParameters().getHeaderAdditions().get("requestUri");
          if (ruri == null) {
            ruri = "sip:" + (isUac ? "uas@" : "uac@") + stackIp + ":" + "5061";
          }
          if (proxyCommunication == null) {
            System.out.println(
                "proxy communication is null.If, UAC please add clientCommunicationInfo in config. If UAS, you mustn't be here!");
          } else {
            if (!call.initiateOutgoingCall(
                ruri,
                proxyCommunication.getIp()
                    + ":"
                    + proxyCommunication.getPort()
                    + ";lr/"
                    + Token.TCP)) {
              System.out.println("Unable to initiate a call. Ending test.");
            } else {
              System.out.println("UAC: Sending INVITE message: ");
            }
          }
        } else if (message.getName().equalsIgnoreCase("ACK")) {
          System.out.println("UAC: Sending ACK message: ");
          while (true) {
            if (call.getLastReceivedResponse() != null
                && (call.getLastReceivedResponse().getStatusCode() >= 200)) {
              if (!call.sendInviteOkAck()) {
                System.out.println("Unnable to send ACK for INVITE");
              }
              break;
            }
          }
        } else if (message.getName().equalsIgnoreCase("BYE")) {
          System.out.println("UAC: Sending BYE message: ");
          if (!call.disconnect()) {
            System.out.println("unable to disconnect the call");
          }
        }
      } else { // message is a response

        String reasonPhrase = message.getParameters().getResponseParameters().getResponsePhrase();
        String forRequest = message.getForRequest();
        if (reasonPhrase.equalsIgnoreCase("Ringing")) {
          while (!call.sendIncomingCallResponse(Response.RINGING, null, -1)) {
            System.out.println("UAS: Trying to send 180 to client");
          }
        } else if (reasonPhrase.equalsIgnoreCase("OK") && forRequest.equalsIgnoreCase("INVITE")) {
          while (!call.sendIncomingCallResponse(Response.OK, null, -1)) {
            System.out.println("UAS: Trying to send 200 to client");
          }
        } else {
          call.sendMessageResponse(
              Integer.parseInt(message.getParameters().getResponseParameters().getResponseCode()),
              message.getParameters().getResponseParameters().getResponsePhrase(),
              -1);
        }
      }
    } else { // direction is receives
      if (message.getType().equals(Type.request)) {
        if (message.getName().equalsIgnoreCase("INVITE")) {
          while (true) {
            if (call.waitForIncomingCall(1000)) {
              System.out.println("Received INVITE request");
              break;
            }
          }
        } else if (message.getName().equalsIgnoreCase("ACK")) {
          call.listenForAck();
          call.waitForAck(3000);
          System.out.println("UAS: messages received: " + call.getAllReceivedRequests());
          //                    assertTrue(callA.getLastReceivedRequest().isAck());
        } else if (message.getName().equalsIgnoreCase("BYE")) {
          if (call.listenForDisconnect()) {
            while (!call.waitForDisconnect(3000)) {}
            assertTrue(call.getLastReceivedRequest().isBye());
            call.respondToDisconnect();
          }
        }
      } else {
        while (true) {
          call.waitOutgoingCallResponse(1000);
          if (call.getLastReceivedResponse() != null
              && call.getLastReceivedResponse().getStatusCode()
                  == (Integer) message.getValidation().get("responseCode")
              && call.getLastReceivedResponse()
                  .getResponseEvent()
                  .getResponse()
                  .getHeader("CSeq")
                  .toString()
                  .contains(message.getForRequest())) {
            System.out.println(
                "Received response: "
                    + call.getLastReceivedResponse().getResponseEvent().getResponse());
            break;
          }
        }
      }
    }
  }
}
