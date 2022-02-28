package com.cisco.dhruva.util;

import static org.testng.Assert.assertTrue;

import com.cisco.dhruva.util.TestInput.Direction;
import com.cisco.dhruva.util.TestInput.Message;
import com.cisco.dhruva.util.TestInput.NicIpPort;
import com.cisco.dhruva.util.TestInput.Transport;
import com.cisco.dhruva.util.TestInput.Type;
import gov.nist.javax.sip.address.AddressImpl;
import gov.nist.javax.sip.address.SipUri;
import gov.nist.javax.sip.header.Contact;
import java.text.ParseException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import java.util.Properties;
import javax.sip.address.Address;
import javax.sip.header.Header;
import javax.sip.message.Response;
import org.cafesip.sipunit.SipCall;
import org.cafesip.sipunit.SipResponse;
import org.cafesip.sipunit.SipStack;
import org.cafesip.sipunit.SipTransaction;

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
        defaultPropertiesUAS.setProperty("javax.sip.IP_ADDRESS", "127.0.0.1");
//    defaultPropertiesUAS.setProperty("gov.nist.javax.sip.TRACE_LEVEL", "32");
    defaultPropertiesUAS.setProperty("gov.nist.javax.sip.DEBUG_LOG", "testAgent2_debug.txt");
    defaultPropertiesUAS.setProperty("gov.nist.javax.sip.SERVER_LOG", "testAgent2_log.txt");
    defaultPropertiesUAS.setProperty("gov.nist.javax.sip.READ_TIMEOUT", "1000");
    defaultPropertiesUAS.setProperty("gov.nist.javax.sip.CACHE_SERVER_CONNECTIONS", "false");
  }

  private static Properties propertiesUAC = new Properties(defaultPropertiesUAC);
  private static Properties propertiesUAS = new Properties(defaultPropertiesUAS);

  private static SipTransaction clientMidCallINVITETransaction;
  private static SipTransaction serverMidCallINVITETransaction;
  private static SipTransaction clientMidCallCancelTransaction;
  private static SipTransaction serverMidCallCancelTransaction;
  private static final int TIMEOUT = 1000;

  public static SipStack getSipStackUAC(int port, Transport transport) throws Exception {
    String key = port + ":" + transport;
    System.out.println("KALPA: Printing uac stack map entries. Key required: " + key);
    sipStackUAC.entrySet().stream()
        .forEach(entry -> System.out.println(entry.getKey() + " : " + entry.getValue()));
    boolean lpExists =
        sipStackUAC.entrySet().stream().anyMatch(entry -> entry.getKey().equals(key));
    if (lpExists) {
      System.out.println("KALPA: UAC LP exists");
      return sipStackUAC.get(key);
    }
    System.out.println("KALPA: UAC LP doesn't exist. Creating new one.");
    SipStack sipStack = new SipStack(transport.name(), port, propertiesUAC);
    sipStackUAC.put(key, sipStack);
    return sipStack;
  }

  public static SipStack getSipStackUAS(int port, Transport transport) throws Exception {
    String key = port + ":" + transport;
    System.out.println("KALPA: Printing uas stack map entries. Key required:" + key);
    sipStackUAS.entrySet().stream()
        .forEach(entry -> System.out.println(entry.getKey() + " : " + entry.getValue()));
    boolean lpExists =
        sipStackUAS.entrySet().stream().anyMatch(entry -> entry.getKey().equals(key));
    if (lpExists) {
      System.out.println("KALPA: UAS LP exists");
      return sipStackUAS.get(key);
    }
    System.out.println("KALPA: UAS LP doesn't exist. Creating new one.");
    SipStack sipStack = new SipStack(transport.name(), port, propertiesUAS);
    sipStackUAS.put(key, sipStack);
    return sipStack;
  }

  public static void actOnMessage(
      Message message,
      SipCall call,
      String stackIp,
      boolean isUac,
      NicIpPort proxyCommunication,
      UA ua)
      throws ParseException {
    System.out.println("UAS: " + message);
    if (message.getDirection() == Direction.sends) {
      if (message.getType().equals(Type.request)) {
        if (message.getName().equalsIgnoreCase("INVITE")) {
          sendInvite(message, call, stackIp, isUac, proxyCommunication);
        } else if (message.getName().equalsIgnoreCase("ACK")) {
          sendAck(call, message);
        } else if (message.getName().equalsIgnoreCase("BYE")) {
          sendBye(call);
        } else if (message.getName().equalsIgnoreCase("Re-INVITE")) {
          sendReInvite(call);
        } else if (message.getName().equalsIgnoreCase("CANCEL")) {
          sendCancel(call);
        }
      } else { // message is a response
        sendResponse(message, call);
      }
    } else { // direction is receives
      if (message.getType().equals(Type.request)) {
        if (message.getName().equalsIgnoreCase("INVITE")) {
          waitForInvite(message, call, ua);
        } else if (message.getName().equalsIgnoreCase("ACK")) {
          waitForAck(message, call, ua);
        } else if (message.getName().equalsIgnoreCase("BYE")) {
          waitForBye(message, call, ua);
        } else if (message.getName().equalsIgnoreCase("Re-INVITE")) {
          waitForReInvite(message, call, ua);
        } else if (message.getName().equalsIgnoreCase("CANCEL")) {
          waitForCancel(message, call, ua);
        }
      } else {
        waitForResponse(message, call, ua);
      }
    }
  }

  private static void sendInvite(
      Message message, SipCall call, String stackIp, boolean isUac, NicIpPort proxyCommunication) {
    String ruri = message.parameters.getRequestParameters().getHeaderAdditions().get("requestUri");
    if (ruri == null) {
      ruri = "sip:" + (isUac ? "uas@" : "uac@") + stackIp + ":" + "5061";
    }
    if (proxyCommunication == null) {
      System.out.println(
          "proxy communication is null.If, UAC please add clientCommunicationInfo in config. If UAS, you mustn't be here!");
    } else {
      if (!call.initiateOutgoingCall(
          ruri,
          proxyCommunication.getIp() + ":" + proxyCommunication.getPort() + ";lr/" + Token.TCP)) {
        System.out.println("Unable to initiate a call. Ending test.");
      } else {
        System.out.println("UAC: Sending INVITE message: ");
      }
    }
  }

  private static void sendAck(SipCall call, Message message) {
    System.out.println("KALPA: Send ACK for message: " + message);
    if ((message.getForRequest() != null)
        && message.getForRequest().equalsIgnoreCase("Re-INVITE")) {
      System.out.println("Sending ACK message for Re-INVITE: ");
      while (true) {
        System.out.println(
            "In ACK. Last reponse received is \n: " + call.getLastReceivedResponse());
        if (call.getLastReceivedResponse().getStatusCode() >= 200) {
          call.sendReinviteOkAck(clientMidCallINVITETransaction);
          break;
        } else {
          System.out.println("Waiting for mid call response before sending ACK");
        }
      }
    } else {
      System.out.println("Sending ACK for fresh INVITE");
      while (true) {
        System.out.println("Before sending fresh ACK" + call.getLastReceivedResponse());
        if (call.getLastReceivedResponse() != null
            && (call.getLastReceivedResponse().getStatusCode() >= 200)) {
          if (!call.sendInviteOkAck()) {
            System.out.println("Unable to send ACK for INVITE");
          }
          break;
        }
      }
    }
  }

  private static void sendBye(SipCall call) {
    System.out.println("UAC: Sending BYE message: ");
    if (!call.disconnect()) {
      System.out.println("unable to disconnect the call");
    }
  }

  private static void sendReInvite(SipCall call) {
    System.out.println("Sending Re-INVITE");
    clientMidCallINVITETransaction = call.sendReinvite(null, null, (String) null, null, null);
  }

  private static void sendCancel(SipCall call) {
    while (true) {
      if (call.getLastReceivedResponse() != null
          && call.getLastReceivedResponse().getStatusCode() == Response.RINGING) {
        System.out.println(call.getLastReceivedResponse());
        break;
      } else {
        System.out.println("Still waiting for 180: " + call.getLastReceivedResponse());
      }
    }
    try {
      Thread.sleep(500);
    } catch (InterruptedException e) {
      e.printStackTrace();
    }
    if ((clientMidCallCancelTransaction = call.sendCancel()) != null) {
      System.out.println("Sending Cancel");
    } else {
      System.out.println(
          "Error Sending Cancel: " + call.getErrorMessage() + " : " + call.getException());
    }
  }

  private static void sendResponse(Message message, SipCall call) throws ParseException {
    String reasonPhrase = message.getParameters().getResponseParameters().getResponsePhrase();
    String forRequest = message.getForRequest();
    String responseCode = message.getParameters().getResponseParameters().getResponseCode();
    if (reasonPhrase.equalsIgnoreCase("Ringing")) {
      while (!call.sendIncomingCallResponse(Response.RINGING, null, -1)) {
        System.out.println("UAS: Trying to send 180 to client");
      }
    } else if (reasonPhrase.equalsIgnoreCase("OK") && forRequest.equalsIgnoreCase("INVITE")) {
      while (!call.sendIncomingCallResponse(Response.OK, null, -1)) {
        System.out.println("UAS: Trying to send 200 to client");
      }
    } else if (reasonPhrase.equalsIgnoreCase("OK") && forRequest.equalsIgnoreCase("Re-INVITE")) {
      call.respondToReinvite(
          serverMidCallINVITETransaction,
          SipResponse.OK,
          "ok reinvite response",
          -1,
          null,
          null,
          null,
          (String) null,
          null);
    } else if (reasonPhrase.equalsIgnoreCase("Redirect")) {
      Contact contactHeader = new Contact();
      Address address = new AddressImpl();
      address.setDisplayName("uas");
      SipUri uri = new SipUri();
      uri.setHost("192.168.1.5");
      uri.setPort(7001);
      uri.setUser("uas");
      uri.setParameter("transport", "tcp");
      uri.setLrParam();
      address.setURI(uri);
      contactHeader.setAddress(address);
      ArrayList<Header> headers = new ArrayList<>();
      headers.add(contactHeader);
      System.out.println("KALPA: Setting contact header as: " + contactHeader);
      call.sendIncomingCallResponse(
          Response.MOVED_TEMPORARILY,
          null,
          -1,
          null,
          new ArrayList<Header>(Arrays.asList(contactHeader)),
          null);
    } else if (responseCode.equalsIgnoreCase(String.valueOf(Response.REQUEST_TERMINATED))) {
      call.sendIncomingCallResponse(Response.REQUEST_TERMINATED, null, -1, null, null, null);
    } else if (reasonPhrase.equalsIgnoreCase("OK") && forRequest.equalsIgnoreCase("CANCEL")) {
      call.respondToCancel(serverMidCallCancelTransaction, Response.OK, "OK", -1);
    } else {
      if (!call.sendMessageResponse(
          Integer.parseInt(message.getParameters().getResponseParameters().getResponseCode()),
          message.getParameters().getResponseParameters().getResponsePhrase(),
          -1)) {
        System.out.println("Error sending response message: " + message);
      }
    }
  }

  private static void waitForInvite(Message message, SipCall call, UA ua) {
    if (message.getName().equalsIgnoreCase("INVITE")) {
      while (true) {
        if (call.waitForIncomingCall(TIMEOUT)) {
          assertTrue(call.getLastReceivedRequest().isInvite());
          ua.addTestMessage(new TestMessage(call.getLastReceivedRequest(), message));
          System.out.println("Received INVITE request");
          break;
        }
      }
    }
  }

  private static void waitForAck(Message message, SipCall call, UA ua) {
    call.listenForAck();
    while (true) {
      if (call.waitForAck(TIMEOUT)) {
        assertTrue(call.getLastReceivedRequest().isAck());
        System.out.println("Received message: " + call.getLastReceivedRequest());
        ua.addTestMessage(new TestMessage(call.getLastReceivedRequest(), message));
        break;
      } else {
        System.out.println("Waiting for ACK");
      }
    }
  }

  private static void waitForBye(Message message, SipCall call, UA ua) {
    if (call.listenForDisconnect()) {
      while (true) {
        if (call.waitForDisconnect(TIMEOUT)) {
          assertTrue(call.getLastReceivedRequest().isBye());
          System.out.println("Received message: " + call.getLastReceivedRequest());
          ua.addTestMessage(new TestMessage(call.getLastReceivedRequest(), message));
          break;
        } else {
          System.out.println("Waiting for BYE");
        }
      }
    }
  }

  private static void waitForReInvite(Message message, SipCall call, UA ua) {
    call.listenForReinvite();
    while (true) {
      serverMidCallINVITETransaction = call.waitForReinvite(TIMEOUT);
      if (serverMidCallINVITETransaction != null) {
        System.out.println(
            "KALPA: Received Re-INVITE: " + serverMidCallINVITETransaction.toString());
        ua.addTestMessage(new TestMessage(call.getLastReceivedRequest(), message));
        break;
      }
    }
  }

  private static void waitForCancel(Message message, SipCall call, UA ua) {
    if (call.listenForCancel()) {
      System.out.println("Listening for cancel");
      System.out.println(
          "KALPA: Error: " + call.getErrorMessage() + " Exception: " + call.getException());
    }
    while (true) {
      System.out.println("Waiting for cancel");
      serverMidCallCancelTransaction = call.waitForCancel(TIMEOUT);
      if (serverMidCallCancelTransaction != null) {
        System.out.println("KALPA: Received Cancel: " + serverMidCallCancelTransaction);
        ua.addTestMessage(new TestMessage(call.getLastReceivedRequest(), message));
        break;
      }
    }
  }

  private static void waitForResponse(Message message, SipCall call, UA ua) {
    if (message.getForRequest().equalsIgnoreCase("Re-INVITE")) {
      while (true) {
        if (call.waitReinviteResponse(clientMidCallINVITETransaction, TIMEOUT)) {
          if (call.getLastReceivedResponse().getStatusCode() >= 200) {
            System.out.println("Received 200 OK for Re-Invite:" + call.getLastReceivedResponse());
            ua.addTestMessage(new TestMessage(call.getLastReceivedResponse(), message));
            break;
          }
        }
      }
      //      call.waitReinviteResponse(clientMidCallINVITETransaction, 1000);
      //      while (call.getLastReceivedResponse().getStatusCode() == Response.TRYING) {
      //        call.waitReinviteResponse(clientMidCallINVITETransaction, 1000);
      //      }
      //      System.out.println("Received 200 OK for Re-Invite:" + call.getLastReceivedResponse());

    } else if (message.getForRequest().equalsIgnoreCase("CANCEL")) {
      while (true) {
        if (call.waitForCancelResponse(clientMidCallCancelTransaction, TIMEOUT)) {
          if (call.getLastReceivedResponse().getStatusCode() == 200) {
            System.out.println("Received 200 OK for Cancel:" + call.getLastReceivedResponse());
            ua.addTestMessage(new TestMessage(call.getLastReceivedResponse(), message));
            break;
          }
        }
      }
    } else {
      while (true) {
        call.waitOutgoingCallResponse(TIMEOUT);
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
          ua.addTestMessage(new TestMessage(call.getLastReceivedResponse(), message));
          break;
        }
      }
    }
  }
}
