package com.cisco.dhruva.application;

import static com.cisco.dhruva.util.Constants.ACK;
import static com.cisco.dhruva.util.Constants.BYE;
import static com.cisco.dhruva.util.Constants.CANCEL;
import static com.cisco.dhruva.util.Constants.INVITE;
import static com.cisco.dhruva.util.Constants.RESPONSE;
import static com.cisco.dhruva.util.Constants.RE_INVITE;
import static com.cisco.dhruva.util.TestLog.TEST_LOGGER;
import static org.testng.Assert.assertTrue;

import com.cisco.dhruva.input.TestInput.Direction;
import com.cisco.dhruva.input.TestInput.Message;
import com.cisco.dhruva.input.TestInput.ProxyCommunication;
import com.cisco.dhruva.input.TestInput.Type;
import com.cisco.dhruva.user.UA;
import com.cisco.dhruva.user.UAC;
import com.cisco.dhruva.util.CustomConsumer;
import com.cisco.dhruva.util.TestMessage;
import gov.nist.javax.sip.address.AddressFactoryImpl;
import gov.nist.javax.sip.address.AddressImpl;
import gov.nist.javax.sip.address.SipUri;
import gov.nist.javax.sip.header.Contact;
import java.text.ParseException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import javax.sip.address.Address;
import javax.sip.header.Header;
import javax.sip.message.Response;
import org.cafesip.sipunit.SipCall;
import org.cafesip.sipunit.SipResponse;
import org.cafesip.sipunit.SipTransaction;
import org.junit.Assert;

public class MessageHandler {
  private static SipTransaction clientMidCallINVITETransaction;
  private static SipTransaction serverMidCallINVITETransaction;
  private static SipTransaction clientMidCallCancelTransaction;
  private static SipTransaction serverMidCallCancelTransaction;
  private static Map<String, CustomConsumer> consumerMapSend = new HashMap<>();
  private static Map<String, CustomConsumer> consumerMapReceive = new HashMap<>();
  private static final int INVITE_TIMEOUT = 5000; // to handle multiple uas
  private static final int TIMEOUT = 2000;
  private static boolean isOptionalReceived = false;

  static {
    CustomConsumer<SipCall, Message, UA> consumerSendInvite = MessageHandler::sendInvite;
    CustomConsumer<SipCall, Message, UA> consumerSendAck = MessageHandler::sendAck;
    CustomConsumer<SipCall, Message, UA> consumerSendResponse = MessageHandler::sendResponse;
    CustomConsumer<SipCall, Message, UA> consumerSendReInvite = MessageHandler::sendReInvite;
    CustomConsumer<SipCall, Message, UA> consumerSendCancel = MessageHandler::sendCancel;
    CustomConsumer<SipCall, Message, UA> consumerSendBye = MessageHandler::sendBye;
    CustomConsumer<SipCall, Message, UA> consumerWaitForInvite = MessageHandler::waitForInvite;
    CustomConsumer<SipCall, Message, UA> consumerWaitForAck = MessageHandler::waitForAck;
    CustomConsumer<SipCall, Message, UA> consumerWaitForBye = MessageHandler::waitForBye;
    CustomConsumer<SipCall, Message, UA> consumerWaitForReInvite = MessageHandler::waitForReInvite;
    CustomConsumer<SipCall, Message, UA> consumerWaitForResponse = MessageHandler::waitForResponse;
    CustomConsumer<SipCall, Message, UA> consumerWaitForCancel = MessageHandler::waitForCancel;
    consumerMapSend.put(INVITE, consumerSendInvite);
    consumerMapSend.put(ACK, consumerSendAck);
    consumerMapSend.put(CANCEL, consumerSendCancel);
    consumerMapSend.put(BYE, consumerSendBye);
    consumerMapSend.put(RE_INVITE, consumerSendReInvite);
    consumerMapSend.put(RESPONSE, consumerSendResponse);

    consumerMapReceive.put(INVITE, consumerWaitForInvite);
    consumerMapReceive.put(ACK, consumerWaitForAck);
    consumerMapReceive.put(CANCEL, consumerWaitForCancel);
    consumerMapReceive.put(BYE, consumerWaitForBye);
    consumerMapReceive.put(RE_INVITE, consumerWaitForReInvite);
    consumerMapReceive.put(RESPONSE, consumerWaitForResponse);
  }

  public static void actOnMessage(Message message, SipCall call, UA ua) throws ParseException {
    if (message.getDirection() == Direction.sends) {
      if (message.getType().equals(Type.request)) {
        consumerMapSend.get(message.getName().toUpperCase()).consume(call, message, ua);
      } else {
        consumerMapSend.get(RESPONSE).consume(call, message, ua);
      }
    } else {
      if (message.getType().equals(Type.request)) {
        consumerMapReceive.get(message.getName().toUpperCase()).consume(call, message, ua);
      } else {
        consumerMapReceive.get(RESPONSE).consume(call, message, ua);
      }
    }
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
    ProxyCommunication proxyCommunication = ((UAC) ua).getProxyCommunication();
    if (proxyCommunication == null) {
      TEST_LOGGER.error(
          "proxy communication is null. Please add clientCommunicationInfo in config.");
    } else {
      if (!call.initiateOutgoingCall(
          ruri,
          proxyCommunication.getIp()
              + ":"
              + proxyCommunication.getPort()
              + ";lr/"
              + proxyCommunication.getTransport())) {
        TEST_LOGGER.error("Unable to initiate a call. Ending test.");
      }
    }
  }

  private static void sendAck(SipCall call, Message message, UA ua) {
    if ((message.getForRequest() != null)
        && message.getForRequest().equalsIgnoreCase("Re-INVITE")) {
      call.sendReinviteOkAck(clientMidCallINVITETransaction);
    } else {
      call.sendInviteOkAck();
    }
  }

  private static void sendBye(SipCall call, Message message, UA ua) {
    if (!call.disconnect()) {
      TEST_LOGGER.error("Error while disconnecting the call");
      Assert.fail();
    }
  }

  private static void sendReInvite(SipCall call, Message message, UA ua) {
    if ((clientMidCallINVITETransaction = call.sendReinvite(null, null, (String) null, null, null))
        == null) {
      TEST_LOGGER.error("Error sending Re-INVITE");
      Assert.fail();
    }
  }

  private static void sendCancel(SipCall call, Message message, UA ua) {
    if ((clientMidCallCancelTransaction = call.sendCancel()) == null) {
      TEST_LOGGER.error("Error Sending Cancel");
      Assert.fail();
    }
  }

  private static void sendResponse(SipCall call, Message message, UA ua) throws ParseException {
    String reasonPhrase = message.getParameters().getResponseParameters().getReasonPhrase();
    String forRequest = message.getForRequest();
    String responseCode = message.getParameters().getResponseParameters().getResponseCode();
    if (reasonPhrase.equalsIgnoreCase("Ringing")) {
      if (!call.sendIncomingCallResponse(Response.RINGING, null, -1)) {
        TEST_LOGGER.error("Error sending 180 Ringing");
        Assert.fail();
      }
    } else if (reasonPhrase.equalsIgnoreCase("OK") && forRequest.equalsIgnoreCase("INVITE")) {
      if (!call.sendIncomingCallResponse(Response.OK, null, -1)) {
        TEST_LOGGER.error("Error sending 200 to client");
        Assert.fail();
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
      String contactListString =
          message.getParameters().getResponseParameters().getHeaderAdditions().get("contact");
      if (contactListString == null || contactListString.isEmpty()) {
        TEST_LOGGER.error("contacts header cannot be empty in redirect message. Please add");
        Assert.fail();
      }
      String[] contactListFromTestMessage = contactListString.split(",");
      ArrayList<Header> contactArrayList = new ArrayList<>();
      Arrays.stream(contactListFromTestMessage)
          .forEach(
              contactString -> {
                Contact contactHeader = new Contact();
                SipUri uri = null;
                try {
                  uri = (SipUri) new AddressFactoryImpl().createURI(contactString);
                } catch (ParseException e) {
                  TEST_LOGGER.error(
                      "Error: Unable to parse contact header {} from Redirect message, ",
                      contactString);
                }
                Address address = new AddressImpl();
                try {
                  address.setDisplayName("uas");
                } catch (ParseException e) {
                  TEST_LOGGER.error(
                      "Error: Unable to set display name for contact header {} from Redirect message, ",
                      contactString);
                }
                try {
                  uri.setParameter("transport", "udp");
                } catch (ParseException e) {
                  TEST_LOGGER.error(
                      "Error: Unable to set transport for contact header {} from Redirect message, ",
                      contactString);
                }
                uri.setLrParam();
                address.setURI(uri);
                contactHeader.setAddress(address);
                TEST_LOGGER.info("Adding contact header, {} in contactList", contactHeader);
                contactArrayList.add(contactHeader);
              });

      //      Contact multipleContactHeader = new Contact();
      //      multipleContactHeader.setContactList(contactList);
      call.sendIncomingCallResponse(
          Response.MOVED_TEMPORARILY, null, -1, null, contactArrayList, null);
    } else if (responseCode.equalsIgnoreCase(String.valueOf(Response.REQUEST_TERMINATED))) {
      call.sendIncomingCallResponse(Response.REQUEST_TERMINATED, null, -1, null, null, null);
    } else if (reasonPhrase.equalsIgnoreCase("OK") && forRequest.equalsIgnoreCase("CANCEL")) {
      call.respondToCancel(serverMidCallCancelTransaction, Response.OK, "OK", -1);
    } else {
      if (!call.sendMessageResponse(
          Integer.parseInt(message.getParameters().getResponseParameters().getResponseCode()),
          message.getParameters().getResponseParameters().getReasonPhrase(),
          -1)) {
        TEST_LOGGER.error("Error sending response message: {}", message);
      }
    }
  }

  private static void waitForInvite(SipCall call, Message message, UA ua) {
    if (message.getName().equalsIgnoreCase("INVITE")) {
      if (call.waitForIncomingCall(INVITE_TIMEOUT)) {
        assertTrue(call.getLastReceivedRequest().isInvite());
        ua.addTestMessage(new TestMessage(call.getLastReceivedRequest(), message));
      } else {
        TEST_LOGGER.error("Error: INVITE not received");
        Assert.fail();
      }
    }
  }

  private static void waitForAck(SipCall call, Message message, UA ua) {
    call.listenForAck();
    if (call.waitForAck(TIMEOUT)) {
      assertTrue(call.getLastReceivedRequest().isAck());
      TEST_LOGGER.info("Received message: {}", call.getLastReceivedRequest());
      ua.addTestMessage(new TestMessage(call.getLastReceivedRequest(), message));

    } else {
      TEST_LOGGER.error("Error: ACK not received");
      Assert.fail();
    }
  }

  private static void waitForBye(SipCall call, Message message, UA ua) {
    if (call.listenForDisconnect()) {
      if (call.waitForDisconnect(TIMEOUT)) {
        assertTrue(call.getLastReceivedRequest().isBye());
        ua.addTestMessage(new TestMessage(call.getLastReceivedRequest(), message));
      } else {
        TEST_LOGGER.error("Error: BYE not received");
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
      TEST_LOGGER.error("Error: Re-INVITE not received");
      Assert.fail();
    }
  }

  private static void waitForCancel(SipCall call, Message message, UA ua) {
    call.listenForCancel();

    serverMidCallCancelTransaction = call.waitForCancel(TIMEOUT);
    if (serverMidCallCancelTransaction != null) {
      ua.addTestMessage(new TestMessage(call.getLastReceivedRequest(), message));
    } else {
      TEST_LOGGER.error("Error: Cancel not received");
      Assert.fail();
    }
  }

  private static void waitForResponse(SipCall call, Message message, UA ua) {
    Integer expectedResponseCode = (Integer) message.getValidation().get("responseCode");
    if (message.getForRequest().equalsIgnoreCase("Re-INVITE")) {
      call.listenForReinvite();
      if (call.waitReinviteResponse(clientMidCallINVITETransaction, TIMEOUT)) {
        if (call.getLastReceivedResponse().getStatusCode() == 100) {
          call.waitReinviteResponse(clientMidCallINVITETransaction, TIMEOUT);
        }
      }
      SipResponse lastReceivedResponse = call.getLastReceivedResponse();
      if (lastReceivedResponse != null
          && lastReceivedResponse.getStatusCode() == expectedResponseCode) {
        TEST_LOGGER.info("Received {} for Re-INVITE", expectedResponseCode);
        ua.addTestMessage(new TestMessage(call.getLastReceivedResponse(), message));
      } else {
        TEST_LOGGER.error("Error: response {} for Re-INVITE not received", expectedResponseCode);
        Assert.fail();
      }

    } else if (message.getForRequest().equalsIgnoreCase("CANCEL")) {
      call.waitForCancelResponse(clientMidCallCancelTransaction, TIMEOUT);
      SipResponse lastReceivedResponse = call.getLastReceivedResponse();
      if (lastReceivedResponse != null
          && lastReceivedResponse.getStatusCode() == expectedResponseCode) {
        TEST_LOGGER.info("Received {} for Cancel", expectedResponseCode);
        ua.addTestMessage(new TestMessage(call.getLastReceivedResponse(), message));
      } else {
        TEST_LOGGER.error("Error: response {} not received", expectedResponseCode);
        Assert.fail();
      }
    } else {
      if (isOptionalReceived) {
        isOptionalReceived = false;
      } else {
        call.waitOutgoingCallResponse(TIMEOUT);
        if (call.getLastReceivedResponse().getStatusCode() == 100) {
          call.waitOutgoingCallResponse(TIMEOUT);
        }
      }
      if (call.getLastReceivedResponse() != null
          && call.getLastReceivedResponse().getStatusCode() >= expectedResponseCode
          && call.getLastReceivedResponse()
              .getResponseEvent()
              .getResponse()
              .getHeader("CSeq")
              .toString()
              .contains(message.getForRequest())) {
        TEST_LOGGER.info(
            "Received response: {} for message: {}",
            call.getLastReceivedResponse().getResponseEvent().getResponse(),
            message);
        if (call.getLastReceivedResponse().getStatusCode() != expectedResponseCode) {
          if (message.isOptional()) {
            TEST_LOGGER.info("Ignoring response {} as it is optional", expectedResponseCode);
            isOptionalReceived = true;
          }
        } else {
          ua.addTestMessage(new TestMessage(call.getLastReceivedResponse(), message));
        }
      } else {
        TEST_LOGGER.error("Error: response {} not received", expectedResponseCode);
        Assert.fail();
      }
    }
  }
}
