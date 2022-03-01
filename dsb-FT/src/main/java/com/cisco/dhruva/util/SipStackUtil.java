package com.cisco.dhruva.util;

import static com.cisco.dhruva.util.FTLog.FT_LOGGER;
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
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Properties;
import javax.sip.address.Address;
import javax.sip.message.Response;
import org.cafesip.sipunit.SipCall;
import org.cafesip.sipunit.SipResponse;
import org.cafesip.sipunit.SipStack;
import org.cafesip.sipunit.SipTransaction;
import org.junit.Assert;

public class SipStackUtil {

  private static Map<String, SipStack> sipStackUAC = new HashMap<>();
  private static Map<String, SipStack> sipStackUAS = new HashMap<>();

  private static SipTransaction clientMidCallINVITETransaction;
  private static SipTransaction serverMidCallINVITETransaction;
  private static SipTransaction clientMidCallCancelTransaction;
  private static SipTransaction serverMidCallCancelTransaction;
  private static final int TIMEOUT = 1000;

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

  public static void actOnMessage(
      Message message,
      SipCall call,
      String stackIp,
      boolean isUac,
      NicIpPort proxyCommunication,
      UA ua)
      throws ParseException {
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

  private static Properties getProperties(boolean isUac, String ip) {
    String testAgent;
    if (isUac) {
      testAgent = "testAgentUAC";
    } else {
      testAgent = "testAgentUAS";
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

  private static void sendInvite(
      Message message, SipCall call, String stackIp, boolean isUac, NicIpPort proxyCommunication) {
    String ruri = message.parameters.getRequestParameters().getHeaderAdditions().get("requestUri");
    if (ruri == null) {
      ruri = "sip:" + (isUac ? "uas@" : "uac@") + stackIp + ":" + "5061";
    }
    if (proxyCommunication == null) {
      FT_LOGGER.error(
          "proxy communication is null.If, UAC please add clientCommunicationInfo in config. If UAS, you mustn't be here!");
    } else {
      if (!call.initiateOutgoingCall(
          ruri,
          proxyCommunication.getIp() + ":" + proxyCommunication.getPort() + ";lr/" + Token.TCP)) {
        FT_LOGGER.error("Unable to initiate a call. Ending test.");
      } else {
        FT_LOGGER.info("Sending INVITE message: ");
      }
    }
  }

  private static void sendAck(SipCall call, Message message) {
    if ((message.getForRequest() != null)
        && message.getForRequest().equalsIgnoreCase("Re-INVITE")) {
      FT_LOGGER.info("Sending ACK message for Re-INVITE: ");
      while (true) {
        if (call.getLastReceivedResponse().getStatusCode() >= 200) {
          call.sendReinviteOkAck(clientMidCallINVITETransaction);
          break;
        } else {
          FT_LOGGER.info("Waiting for mid call response before sending ACK");
        }
      }
    } else {
      FT_LOGGER.info("Sending ACK for fresh INVITE");
      while (true) {
        if (call.getLastReceivedResponse() != null
            && (call.getLastReceivedResponse().getStatusCode() >= 200)) {
          if (!call.sendInviteOkAck()) {
            FT_LOGGER.error("Unable to send ACK for INVITE");
            Assert.fail();
          }
          break;
        }
      }
    }
  }

  private static void sendBye(SipCall call) {
    FT_LOGGER.info("Sending BYE");
    if (!call.disconnect()) {
      FT_LOGGER.error("unable to disconnect the call: {}", call.getErrorMessage());
      Assert.fail();
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
        FT_LOGGER.info("Still waiting for 180");
      }
    }
    try {
      Thread.sleep(500);
    } catch (InterruptedException e) {
      e.printStackTrace();
    }
    if ((clientMidCallCancelTransaction = call.sendCancel()) != null) {
      FT_LOGGER.info("Sending Cancel");
    } else {
      FT_LOGGER.error("Error Sending Cancel: {}", call.getErrorMessage());
      Assert.fail();
    }
  }

  private static void sendResponse(Message message, SipCall call) throws ParseException {
    String reasonPhrase = message.getParameters().getResponseParameters().getReasonPhrase();
    String forRequest = message.getForRequest();
    String responseCode = message.getParameters().getResponseParameters().getResponseCode();
    if (reasonPhrase.equalsIgnoreCase("Ringing")) {
      while (!call.sendIncomingCallResponse(Response.RINGING, null, -1)) {
        FT_LOGGER.info("Trying to send 180 to client");
      }
    } else if (reasonPhrase.equalsIgnoreCase("OK") && forRequest.equalsIgnoreCase("INVITE")) {
      while (!call.sendIncomingCallResponse(Response.OK, null, -1)) {
        FT_LOGGER.info("Trying to send 200 to client");
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
      uri.setHost("127.0.0.1");
      uri.setPort(7001);
      uri.setUser("uas");
      uri.setParameter("transport", "tcp");
      uri.setLrParam();
      address.setURI(uri);
      contactHeader.setAddress(address);
      FT_LOGGER.info("Setting contact header as: {}", contactHeader);
      call.sendIncomingCallResponse(
          Response.MOVED_TEMPORARILY,
          null,
          -1,
          null,
          new ArrayList<>(Collections.singletonList(contactHeader)),
          null);
    } else if (responseCode.equalsIgnoreCase(String.valueOf(Response.REQUEST_TERMINATED))) {
      call.sendIncomingCallResponse(Response.REQUEST_TERMINATED, null, -1, null, null, null);
    } else if (reasonPhrase.equalsIgnoreCase("OK") && forRequest.equalsIgnoreCase("CANCEL")) {
      call.respondToCancel(serverMidCallCancelTransaction, Response.OK, "OK", -1);
    } else {
      if (!call.sendMessageResponse(
          Integer.parseInt(message.getParameters().getResponseParameters().getResponseCode()),
          message.getParameters().getResponseParameters().getReasonPhrase(),
          -1)) {
        FT_LOGGER.error("Error sending response message: {}", message);
      }
    }
  }

  private static void waitForInvite(Message message, SipCall call, UA ua) {
    if (message.getName().equalsIgnoreCase("INVITE")) {
      while (true) {
        if (call.waitForIncomingCall(TIMEOUT)) {
          assertTrue(call.getLastReceivedRequest().isInvite());
          ua.addTestMessage(new TestMessage(call.getLastReceivedRequest(), message));
          FT_LOGGER.info("Received INVITE request");
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
        FT_LOGGER.info("Received message: {}", call.getLastReceivedRequest());
        ua.addTestMessage(new TestMessage(call.getLastReceivedRequest(), message));
        break;
      } else {
        FT_LOGGER.info("Waiting for ACK");
      }
    }
  }

  private static void waitForBye(Message message, SipCall call, UA ua) {
    if (call.listenForDisconnect()) {
      while (true) {
        if (call.waitForDisconnect(TIMEOUT)) {
          assertTrue(call.getLastReceivedRequest().isBye());
          FT_LOGGER.info("Received message: {}", call.getLastReceivedRequest());
          ua.addTestMessage(new TestMessage(call.getLastReceivedRequest(), message));
          break;
        } else {
          FT_LOGGER.info("Waiting for BYE");
        }
      }
    }
  }

  private static void waitForReInvite(Message message, SipCall call, UA ua) {
    call.listenForReinvite();
    while (true) {
      serverMidCallINVITETransaction = call.waitForReinvite(TIMEOUT);
      if (serverMidCallINVITETransaction != null) {
        FT_LOGGER.info("Received Re-INVITE");
        ua.addTestMessage(new TestMessage(call.getLastReceivedRequest(), message));
        break;
      }
    }
  }

  private static void waitForCancel(Message message, SipCall call, UA ua) {
    if (call.listenForCancel()) {
      FT_LOGGER.info("Listening for cancel");
    }
    while (true) {
      FT_LOGGER.info("Waiting for cancel");
      serverMidCallCancelTransaction = call.waitForCancel(TIMEOUT);
      if (serverMidCallCancelTransaction != null) {
        FT_LOGGER.info("Received Cancel");
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
            FT_LOGGER.info("Received 200 OK for Re-Invite");
            ua.addTestMessage(new TestMessage(call.getLastReceivedResponse(), message));
            break;
          }
        }
      }
    } else if (message.getForRequest().equalsIgnoreCase("CANCEL")) {
      while (true) {
        if (call.waitForCancelResponse(clientMidCallCancelTransaction, TIMEOUT)) {
          if (call.getLastReceivedResponse().getStatusCode() == 200) {
            FT_LOGGER.info("Received 200 OK for Cancel");
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
          FT_LOGGER.info(
              "Received response: {}",
              call.getLastReceivedResponse().getResponseEvent().getResponse());
          ua.addTestMessage(new TestMessage(call.getLastReceivedResponse(), message));
          break;
        }
      }
    }
  }
}
