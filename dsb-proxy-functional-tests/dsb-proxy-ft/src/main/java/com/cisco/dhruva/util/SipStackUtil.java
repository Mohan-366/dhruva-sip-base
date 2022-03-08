package com.cisco.dhruva.util;

import static com.cisco.dhruva.util.FTLog.FT_LOGGER;
import static org.testng.Assert.assertTrue;

import com.cisco.dhruva.util.TestInput.Direction;
import com.cisco.dhruva.util.TestInput.Message;
import com.cisco.dhruva.util.TestInput.NicIpPort;
import com.cisco.dhruva.util.TestInput.Transport;
import com.cisco.dhruva.util.TestInput.Type;
import gov.nist.javax.sip.address.AddressFactoryImpl;
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
  private static Map<String, CustomConsumer> consumerMapSend = new HashMap<>();
  private static Map<String, CustomConsumer> consumerMapReceive = new HashMap<>();
  private static final int TIMEOUT = 1000;

  static {
    CustomConsumer<SipCall, Message, UA> consumerSendInvite = SipStackUtil::sendInvite;
    CustomConsumer<SipCall, Message, UA> consumerSendAck = SipStackUtil::sendAck;
    CustomConsumer<SipCall, Message, UA> consumerSendResponse = SipStackUtil::sendResponse;
    CustomConsumer<SipCall, Message, UA> consumerSendReInvite = SipStackUtil::sendReInvite;
    CustomConsumer<SipCall, Message, UA> consumerSendCancel = SipStackUtil::sendCancel;
    CustomConsumer<SipCall, Message, UA> consumerSendBye = SipStackUtil::sendBye;
    CustomConsumer<SipCall, Message, UA> consumerWaitForInvite = SipStackUtil::waitForInvite;
    CustomConsumer<SipCall, Message, UA> consumerWaitForAck = SipStackUtil::waitForAck;
    CustomConsumer<SipCall, Message, UA> consumerWaitForBye = SipStackUtil::waitForBye;
    CustomConsumer<SipCall, Message, UA> consumerWaitForReInvite = SipStackUtil::waitForReInvite;
    CustomConsumer<SipCall, Message, UA> consumerWaitForResponse = SipStackUtil::waitForResponse;
    CustomConsumer<SipCall, Message, UA> consumerWaitForCancel = SipStackUtil::waitForCancel;
    consumerMapSend.put("INVITE", consumerSendInvite);
    consumerMapSend.put("ACK", consumerSendAck);
    consumerMapSend.put("CANCEL", consumerSendCancel);
    consumerMapSend.put("BYE", consumerSendBye);
    consumerMapSend.put("RE-INVITE", consumerSendReInvite);
    consumerMapSend.put("RESPONSE", consumerSendResponse);

    consumerMapReceive.put("INVITE", consumerWaitForInvite);
    consumerMapReceive.put("ACK", consumerWaitForAck);
    consumerMapReceive.put("CANCEL", consumerWaitForCancel);
    consumerMapReceive.put("BYE", consumerWaitForBye);
    consumerMapReceive.put("RE-INVITE", consumerWaitForReInvite);
    consumerMapReceive.put("RESPONSE", consumerWaitForResponse);
  }

  public static SipStack getSipStackUAC(String ip, int port, Transport transport) throws Exception {
    return getSipStack(true, ip, port, transport);
  }

  public static SipStack getSipStackUAS(String ip, int port, Transport transport) throws Exception {
    return getSipStack(false, ip, port, transport);
  }

  public static void actOnMessage(Message message, SipCall call, UA ua) throws ParseException {
    if (message.getDirection() == Direction.sends) {
      if (message.getType().equals(Type.request)) {
        consumerMapSend.get(message.getName().toUpperCase()).consume(call, message, ua);
      } else {
        consumerMapSend.get("RESPONSE").consume(call, message, ua);
      }
    } else {
      if (message.getType().equals(Type.request)) {
        consumerMapReceive.get(message.getName().toUpperCase()).consume(call, message, ua);
      } else {
        consumerMapReceive.get("RESPONSE").consume(call, message, ua);
      }
    }
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

  private static void sendInvite(SipCall call, Message message, UA ua) {
    String stackIp;
    if (ua instanceof UAC) {
      stackIp = ((UAC) ua).getSipStack().getSipProvider().getListeningPoints()[0].getIPAddress();
    } else {
      Assert.fail("Only UAC sends INVITE");
      return;
    }

    String ruri =
        message.getParameters().getRequestParameters().getHeaderAdditions().get("requestUri");
    if (ruri == null) { // using default
      ruri = "sip:uas@" + stackIp + ":" + "5061";
    }
    NicIpPort proxyCommunication = ((UAC) ua).getProxyCommunication();
    if (proxyCommunication == null) {
      FT_LOGGER.error("proxy communication is null. Please add clientCommunicationInfo in config.");
    } else {
      if (!call.initiateOutgoingCall(
          ruri,
          proxyCommunication.getIp() + ":" + proxyCommunication.getPort() + ";lr/" + Token.TCP)) {
        FT_LOGGER.error("Unable to initiate a call. Ending test.");
      }
    }
  }

  private static void sendAck(SipCall call, Message message, UA ua) {
    if ((message.getForRequest() != null)
        && message.getForRequest().equalsIgnoreCase("Re-INVITE")) {
      if (!call.sendReinviteOkAck(clientMidCallINVITETransaction)) {
        FT_LOGGER.error("Error sending ACK for Re-INVITE");
        Assert.fail();
      }
    } else {
      if (!call.sendInviteOkAck()) {
        FT_LOGGER.error("Error sending ACK for INVITE");
        Assert.fail();
      }
    }
  }

  private static void sendBye(SipCall call, Message message, UA ua) {
    if (!call.disconnect()) {
      FT_LOGGER.error("Error while disconnecting the call");
      Assert.fail();
    }
  }

  private static void sendReInvite(SipCall call, Message message, UA ua) {
    if ((clientMidCallINVITETransaction = call.sendReinvite(null, null, (String) null, null, null))
        == null) {
      FT_LOGGER.error("Error sending Re-INVITE");
      Assert.fail();
    }
  }

  private static void sendCancel(SipCall call, Message message, UA ua) {
    if ((clientMidCallCancelTransaction = call.sendCancel()) == null) {
      FT_LOGGER.error("Error Sending Cancel");
      Assert.fail();
    }
  }

  private static void sendResponse(SipCall call, Message message, UA ua) throws ParseException {
    String reasonPhrase = message.getParameters().getResponseParameters().getReasonPhrase();
    String forRequest = message.getForRequest();
    String responseCode = message.getParameters().getResponseParameters().getResponseCode();
    if (reasonPhrase.equalsIgnoreCase("Ringing")) {
      if (!call.sendIncomingCallResponse(Response.RINGING, null, -1)) {
        FT_LOGGER.error("Error sending 180 Ringing");
        Assert.fail();
      }
    } else if (reasonPhrase.equalsIgnoreCase("OK") && forRequest.equalsIgnoreCase("INVITE")) {
      if (!call.sendIncomingCallResponse(Response.OK, null, -1)) {
        FT_LOGGER.error("Error sending 200 to client");
      }
    } else if (reasonPhrase.equalsIgnoreCase("OK") && forRequest.equalsIgnoreCase("Re-INVITE")) {
      call.respondToReinvite(
          serverMidCallINVITETransaction,
          SipResponse.OK,
          "OK",
          -1,
          null,
          null,
          null,
          (String) null,
          null);
    } else if (reasonPhrase.equalsIgnoreCase("Redirect")) {
      Contact contactHeader = new Contact();
      SipUri uri =
          (SipUri)
              new AddressFactoryImpl()
                  .createURI(
                      message.getParameters().responseParameters.headerAdditions.get("contact"));
      Address address = new AddressImpl();
      address.setDisplayName("uas");
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

  private static void waitForInvite(SipCall call, Message message, UA ua) {
    if (message.getName().equalsIgnoreCase("INVITE")) {
      if (call.waitForIncomingCall(TIMEOUT)) {
        assertTrue(call.getLastReceivedRequest().isInvite());
        ua.addTestMessage(new TestMessage(call.getLastReceivedRequest(), message));
      } else {
        FT_LOGGER.error("Error: INVITE not received");
        Assert.fail();
      }
    }
  }

  private static void waitForAck(SipCall call, Message message, UA ua) {
    call.listenForAck();
    if (call.waitForAck(TIMEOUT)) {
      assertTrue(call.getLastReceivedRequest().isAck());
      FT_LOGGER.info("Received message: {}", call.getLastReceivedRequest());
      ua.addTestMessage(new TestMessage(call.getLastReceivedRequest(), message));

    } else {
      FT_LOGGER.error("Error: ACK not received");
      Assert.fail();
    }
  }

  private static void waitForBye(SipCall call, Message message, UA ua) {
    if (call.listenForDisconnect()) {
      if (call.waitForDisconnect(TIMEOUT)) {
        assertTrue(call.getLastReceivedRequest().isBye());
        ua.addTestMessage(new TestMessage(call.getLastReceivedRequest(), message));
      } else {
        FT_LOGGER.error("Error: BYE not received");
        Assert.fail();
      }
    }
  }

  private static void waitForReInvite(SipCall call, Message message, UA ua) {
    call.listenForReinvite();
    serverMidCallINVITETransaction = call.waitForReinvite(TIMEOUT);
    if (serverMidCallINVITETransaction != null) {
      ua.addTestMessage(new TestMessage(call.getLastReceivedRequest(), message));
    } else {
      FT_LOGGER.error("Error: Re-INVITE not received");
      Assert.fail();
    }
  }

  private static void waitForCancel(SipCall call, Message message, UA ua) {
    call.listenForCancel();

    serverMidCallCancelTransaction = call.waitForCancel(TIMEOUT);
    if (serverMidCallCancelTransaction != null) {
      ua.addTestMessage(new TestMessage(call.getLastReceivedRequest(), message));
    } else {
      FT_LOGGER.error("Error: Cancel not received");
      Assert.fail();
    }
  }

  private static void waitForResponse(SipCall call, Message message, UA ua) {
    Integer expectedResponse = (Integer) message.getValidation().get("responseCode");
    if (message.getForRequest().equalsIgnoreCase("Re-INVITE")) {
      while (true) {
        call.waitReinviteResponse(clientMidCallINVITETransaction, TIMEOUT);
        SipResponse lastReceivedResponse = call.getLastReceivedResponse();
        if (lastReceivedResponse != null
            && lastReceivedResponse.getStatusCode() == expectedResponse) {
          FT_LOGGER.info("Received {} for Re-INVITE", expectedResponse);
          ua.addTestMessage(new TestMessage(call.getLastReceivedResponse(), message));
          break;
        } else {
          FT_LOGGER.warn("Waiting for {} response", expectedResponse);
        }
      }
    } else if (message.getForRequest().equalsIgnoreCase("CANCEL")) {
      while (true) {
        call.waitForCancelResponse(clientMidCallCancelTransaction, TIMEOUT);
        SipResponse lastReceivedResponse = call.getLastReceivedResponse();
        if (lastReceivedResponse != null
            && lastReceivedResponse.getStatusCode() == expectedResponse) {
          FT_LOGGER.info("Received {} for Cancel", expectedResponse);
          ua.addTestMessage(new TestMessage(call.getLastReceivedResponse(), message));
          break;
        } else {
          FT_LOGGER.warn("Waiting for {} response", expectedResponse);
        }
      }
    } else {
      while (true) {
        call.waitOutgoingCallResponse(TIMEOUT);
        if (call.getLastReceivedResponse() != null
            && call.getLastReceivedResponse().getStatusCode() == expectedResponse
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
        } else {
          FT_LOGGER.warn("Waiting for {} response", expectedResponse);
        }
      }
    }
  }
}
