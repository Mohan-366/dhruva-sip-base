package com.cisco.dhruva.application;

import static com.cisco.dhruva.util.Constants.*;
import static org.cafesip.sipunit.SipAssert.assertLastOperationSuccess;
import static org.testng.Assert.assertTrue;

import com.cisco.dhruva.input.TestInput.Direction;
import com.cisco.dhruva.input.TestInput.Message;
import com.cisco.dhruva.input.TestInput.ProxyCommunication;
import com.cisco.dhruva.input.TestInput.Type;
import com.cisco.dhruva.user.UA;
import com.cisco.dhruva.user.UAC;
import com.cisco.dhruva.user.UAS;
import com.cisco.dhruva.util.CustomConsumer;
import com.cisco.dhruva.util.TestMessage;
import java.text.ParseException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import java.util.function.Function;
import javax.sip.address.Address;
import javax.sip.address.AddressFactory;
import javax.sip.address.URI;
import javax.sip.header.ContactHeader;
import javax.sip.header.Header;
import javax.sip.header.HeaderFactory;
import org.apache.logging.log4j.util.Strings;
import org.cafesip.sipunit.SipCall;
import org.cafesip.sipunit.SipResponse;
import org.cafesip.sipunit.SipStack;
import org.cafesip.sipunit.SipTransaction;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.testng.Assert;

public class MessageHandler {
  private static SipTransaction clientMidCallINVITETransaction;
  private static SipTransaction serverMidCallINVITETransaction;
  private static SipTransaction clientMidCallCancelTransaction;
  private static SipTransaction serverMidCallCancelTransaction;
  private static Map<String, CustomConsumer> consumerMapSend = new HashMap<>();
  private static Map<String, CustomConsumer> consumerMapReceive = new HashMap<>();
  private static final int INVITE_TIMEOUT = 50000; // to handle multiple uas
  private static final int TIMEOUT = 2000;
  private static boolean isOptionalReceived = false;

  public static final Logger TEST_LOGGER = LoggerFactory.getLogger(MessageHandler.class);

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

  private static Function<UA, SipStack> getUaStack =
      ua -> {
        if (ua instanceof UAC) {
          return ((UAC) ua).getSipStack();
        } else {
          return ((UAS) ua).getSipStack();
        }
      };

  private static String getReqUri(Map<String, String> headerAdditions, SipStack uaStack) {
    if (headerAdditions == null || headerAdditions.get(REQUEST_URI) == null) {
      return "sip:uas@"
          + uaStack.getSipProvider().getListeningPoints()[0].getIPAddress()
          + ":"
          + "5061";
    }
    return headerAdditions.get(REQUEST_URI);
  }

  private static void sendInvite(SipCall call, Message message, UA ua) {
    if (ua instanceof UAS) {
      Assert.fail("Only UAC sends INVITE");
      return;
    }
    SipStack uaStack = getUaStack.apply(ua);
    AddressFactory uaAddressFactory = uaStack.getAddressFactory();
    HeaderFactory uaHeaderFactory = uaStack.getHeaderFactory();

    Map<String, String> headerAdditions = null;
    Map<String, String> headerReplacements = null;

    if (message.getParameters() != null) {
      headerAdditions = message.getParameters().getRequestParameters().getHeaderAdditions();
      headerReplacements = message.getParameters().getRequestParameters().getHeaderReplacements();
    }

    ProxyCommunication proxyCommunication = ((UAC) ua).getProxyCommunication();
    if (proxyCommunication == null) {
      TEST_LOGGER.error(
          "proxy communication is null. Please add clientCommunicationInfo in config.");
    } else {
      if (!call.initiateOutgoingCall(
          null,
          getReqUri(headerAdditions, uaStack),
          proxyCommunication.getIp()
              + ":"
              + proxyCommunication.getPort()
              + ";lr/"
              + proxyCommunication.getTransport(),
          getHeadersToAdd(headerAdditions, uaHeaderFactory, uaAddressFactory),
          getHeadersToReplace(headerReplacements, uaHeaderFactory, uaAddressFactory),
          null)) {
        assertLastOperationSuccess(
            "Unable to initiate a call. Ending test. - " + call.format(), call);
      }
    }
  }

  private static void sendAck(SipCall call, Message message, UA ua) {
    SipStack uaStack = getUaStack.apply(ua);
    HeaderFactory uaHeaderFactory = uaStack.getHeaderFactory();
    AddressFactory uaAddressFactory = uaStack.getAddressFactory();

    Map<String, String> headerAdditions = null;
    Map<String, String> headerReplacements = null;

    if (message.getParameters() != null) {
      headerAdditions = message.getParameters().getRequestParameters().getHeaderAdditions();
      headerReplacements = message.getParameters().getRequestParameters().getHeaderReplacements();
    }

    if (RE_INVITE.equalsIgnoreCase(message.getForRequest())) {
      call.sendReinviteOkAck(clientMidCallINVITETransaction);
    } else {
      call.sendInviteOkAck(
          getHeadersToAdd(headerAdditions, uaHeaderFactory, uaAddressFactory),
          getHeadersToReplace(headerReplacements, uaHeaderFactory, uaAddressFactory),
          null);
    }
  }

  private static void sendBye(SipCall call, Message message, UA ua) {
    if (!call.disconnect()) {
      assertLastOperationSuccess(
          "Error while disconnecting(BYE) the call - " + call.format(), call);
    }
  }

  private static void sendReInvite(SipCall call, Message message, UA ua) {
    try {
      Thread.sleep(500); // To avoid Re-INVITE reaching before the previous ACK
    } catch (InterruptedException e) {
    }
    if ((clientMidCallINVITETransaction = call.sendReinvite(null, null, (String) null, null, null))
        == null) {
      assertLastOperationSuccess("Error sending Re-INVITE - " + call.format(), call);
    }
  }

  private static void sendCancel(SipCall call, Message message, UA ua) {
    if ((clientMidCallCancelTransaction = call.sendCancel()) == null) {
      assertLastOperationSuccess("Error Sending Cancel - " + call.format(), call);
    }
  }

  private static void sendResponse(SipCall call, Message message, UA ua) {
    String forRequest = message.getForRequest();
    String reasonPhrase = null;
    String responseCode = null;
    int respCode = 0;

    SipStack uaStack = getUaStack.apply(ua);
    HeaderFactory uaHeaderFactory = uaStack.getHeaderFactory();
    AddressFactory uaAddressFactory = uaStack.getAddressFactory();

    Map<String, String> headerAdditions = null;
    Map<String, String> headerReplacements = null;

    if (message.getParameters() != null) {
      headerAdditions = message.getParameters().getResponseParameters().getHeaderAdditions();
      headerReplacements = message.getParameters().getResponseParameters().getHeaderReplacements();
      reasonPhrase = message.getParameters().getResponseParameters().getReasonPhrase();
      responseCode = message.getParameters().getResponseParameters().getResponseCode();
      respCode = Integer.parseInt(responseCode);
    }

    if (respCode == SipResponse.RINGING) {
      if (!call.sendIncomingCallResponse(
          SipResponse.RINGING,
          null,
          -1,
          getHeadersToAdd(headerAdditions, uaHeaderFactory, uaAddressFactory),
          getHeadersToReplace(headerReplacements, uaHeaderFactory, uaAddressFactory),
          null)) {
        assertLastOperationSuccess("Error sending '180' - " + call.format(), call);
      }
    } else if (respCode == SipResponse.OK && INVITE.equalsIgnoreCase(forRequest)) {
      if (!call.sendIncomingCallResponse(
          SipResponse.OK,
          null,
          -1,
          getHeadersToAdd(headerAdditions, uaHeaderFactory, uaAddressFactory),
          getHeadersToReplace(headerReplacements, uaHeaderFactory, uaAddressFactory),
          null)) {
        assertLastOperationSuccess("Error sending '200' for INVITE - " + call.format(), call);
      }
    } else if (respCode == SipResponse.OK && RE_INVITE.equalsIgnoreCase(forRequest)) {
      call.respondToReinvite(
          serverMidCallINVITETransaction,
          SipResponse.OK,
          null,
          -1,
          null,
          null,
          null,
          (String) null,
          null);
      assertLastOperationSuccess("Error sending '200' for Re-INVITE - " + call.format(), call);
    } else if (respCode == SipResponse.MOVED_TEMPORARILY) {
      if (headerReplacements == null || Strings.isEmpty(headerReplacements.get(CONTACT))) {
        Assert.fail("Contact header cannot be empty in redirect(302) message. Please add");
      }
      String contactListString = headerReplacements.get(CONTACT);
      String[] contactListFromTestMessage = contactListString.split(",");
      ArrayList<Header> contactArrayList = new ArrayList<>();
      Arrays.stream(contactListFromTestMessage)
          .forEach(
              contactString -> {
                URI asContact;
                try {
                  asContact = call.getAddressFactory().createURI(contactString);
                } catch (ParseException e) {
                  TEST_LOGGER.error("Exception while parsing contact", e);
                  throw new RuntimeException(e);
                }
                Address contact1 = call.getAddressFactory().createAddress(asContact);
                ContactHeader asContactHeader =
                    call.getHeaderFactory().createContactHeader(contact1);
                TEST_LOGGER.info("Adding contact header {}, in contactList", asContactHeader);
                contactArrayList.add(asContactHeader);
              });
      call.sendMessageResponse(
          SipResponse.MOVED_TEMPORARILY, null, -1, contactArrayList, null, null);
      assertLastOperationSuccess("Error sending '302' - " + call.format(), call);
    } else if (respCode == SipResponse.REQUEST_TERMINATED) {
      call.sendIncomingCallResponse(SipResponse.REQUEST_TERMINATED, null, -1, null, null, null);
      assertLastOperationSuccess("Error sending '487' - " + call.format(), call);
    } else if (respCode == SipResponse.OK && CANCEL.equalsIgnoreCase(forRequest)) {
      call.respondToCancel(serverMidCallCancelTransaction, SipResponse.OK, null, -1);
      assertLastOperationSuccess("Error sending '200' for CANCEL - " + call.format(), call);
    } else {
      if (!call.sendMessageResponse(respCode, reasonPhrase, -1)) {
        Assert.fail("Error sending " + respCode + " response, message: " + message);
      }
    }
  }

  private static void waitForInvite(SipCall call, Message message, UA ua) {
    if (INVITE.equalsIgnoreCase(message.getName())) {
      if (call.waitForIncomingCall(INVITE_TIMEOUT)) {
        assertTrue(call.getLastReceivedRequest().isInvite());
        ua.addTestMessage(new TestMessage(call.getLastReceivedRequest(), message));
      } else {
        assertLastOperationSuccess("Error: INVITE not received - " + call.format(), call);
      }
    }
  }

  private static void waitForAck(SipCall call, Message message, UA ua) {
    call.listenForAck();
    if (call.waitForAck(TIMEOUT)) {
      assertTrue(call.getLastReceivedRequest().isAck());
      ua.addTestMessage(new TestMessage(call.getLastReceivedRequest(), message));
    } else {
      assertLastOperationSuccess("Error: ACK not received - " + call.format(), call);
    }
  }

  private static void waitForBye(SipCall call, Message message, UA ua) {
    if (call.listenForDisconnect()) {
      if (call.waitForDisconnect(TIMEOUT)) {
        assertTrue(call.getLastReceivedRequest().isBye());
        ua.addTestMessage(new TestMessage(call.getLastReceivedRequest(), message));
      } else {
        assertLastOperationSuccess("Error: BYE not received - " + call.format(), call);
      }
    }
  }

  private static void waitForReInvite(SipCall call, Message message, UA ua) {
    call.listenForReinvite();
    serverMidCallINVITETransaction = call.waitForReinvite(TIMEOUT);
    if (serverMidCallINVITETransaction != null) {
      ua.addTestMessage(new TestMessage(call.getLastReceivedRequest(), message));
    } else {
      assertLastOperationSuccess("Error: Re-INVITE not received - " + call.format(), call);
    }
  }

  private static void waitForCancel(SipCall call, Message message, UA ua) {
    call.listenForCancel();
    serverMidCallCancelTransaction = call.waitForCancel(TIMEOUT);
    if (serverMidCallCancelTransaction != null) {
      ua.addTestMessage(new TestMessage(call.getLastReceivedRequest(), message));
    } else {
      assertLastOperationSuccess("Error: CANCEL not received - " + call.format(), call);
    }
  }

  private static void waitForResponse(SipCall call, Message message, UA ua) {
    Integer expectedResponseCode = (Integer) message.getValidation().get(RESPONSE_CODE);
    if (RE_INVITE.equalsIgnoreCase(message.getForRequest())) {
      call.listenForReinvite();
      if (call.waitReinviteResponse(clientMidCallINVITETransaction, TIMEOUT)) {
        if (call.getLastReceivedResponse().getStatusCode() == 100) {
          call.waitReinviteResponse(clientMidCallINVITETransaction, TIMEOUT);
        }
      }
      SipResponse lastReceivedResponse = call.getLastReceivedResponse();
      if (lastReceivedResponse != null
          && lastReceivedResponse.getStatusCode() == expectedResponseCode) {
        TEST_LOGGER.info("Received {} response for Re-INVITE", expectedResponseCode);
        ua.addTestMessage(new TestMessage(call.getLastReceivedResponse(), message));
      } else {
        Assert.fail("Error: response " + expectedResponseCode + " for Re-INVITE not received");
      }
    } else if (CANCEL.equalsIgnoreCase(message.getForRequest())) {
      call.waitForCancelResponse(clientMidCallCancelTransaction, TIMEOUT);
      SipResponse lastReceivedResponse = call.getLastReceivedResponse();
      if (lastReceivedResponse != null
          && lastReceivedResponse.getStatusCode() == expectedResponseCode) {
        TEST_LOGGER.info("Received {} response for CANCEL", expectedResponseCode);
        ua.addTestMessage(new TestMessage(call.getLastReceivedResponse(), message));
      } else {
        Assert.fail("Error: response " + expectedResponseCode + " for CANCEL not received");
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
            call.getLastReceivedResponse().getResponseEvent().getResponse().getStatusCode(),
            message.getForRequest());
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

  private static ArrayList<Header> getHeadersToAdd(
      Map<String, String> headerAdditions,
      HeaderFactory uaHeaderFactory,
      AddressFactory uaAddressFactory) {

    if (headerAdditions == null) {
      return null;
    }
    ArrayList<Header> headersToAdd = new ArrayList<>();
    headerAdditions.forEach(
        (key, value) -> {
          if (DIVERSION.equalsIgnoreCase(key)) {
            String[] values = value.split(",");
            Arrays.stream(values)
                .forEach(
                    val -> {
                      try {
                        headersToAdd.add(uaHeaderFactory.createHeader(key, val));
                      } catch (ParseException e) {
                        TEST_LOGGER.error("Error constructing {} header", key, e);
                      }
                    });
          } else {
            try {
              headersToAdd.add(uaHeaderFactory.createHeader(key, value));
            } catch (ParseException e) {
              TEST_LOGGER.error("Error constructing {} header", key, e);
            }
          }
        });
    return headersToAdd;
  }

  private static ArrayList<Header> getHeadersToReplace(
      Map<String, String> headerReplacements,
      HeaderFactory uaHeaderFactory,
      AddressFactory uaAddressFactory) {

    if (headerReplacements == null) {
      return null;
    }

    ArrayList<Header> headersToReplace = new ArrayList<>();
    String toUri = headerReplacements.get(TO);
    if (toUri != null) {
      try {
        Address toAddress = uaAddressFactory.createAddress(uaAddressFactory.createURI(toUri));
        headersToReplace.add(uaHeaderFactory.createToHeader(toAddress, null));
      } catch (ParseException e) {
        TEST_LOGGER.error("Error constructing To header", e);
      }
    }
    String contactUri = headerReplacements.get(CONTACT);
    if (contactUri != null) {
      try {
        Address contactAddress = uaAddressFactory.createAddress(contactUri);
        headersToReplace.add(uaHeaderFactory.createContactHeader(contactAddress));
      } catch (ParseException e) {
        TEST_LOGGER.error("Error constructing Contact header", e);
      }
    }
    return headersToReplace;
  }
}
