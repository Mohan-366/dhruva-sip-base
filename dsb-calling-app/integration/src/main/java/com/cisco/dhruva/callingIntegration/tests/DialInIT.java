package com.cisco.dhruva.callingIntegration.tests;

import static org.cafesip.sipunit.SipAssert.assertHeaderContains;
import static org.cafesip.sipunit.SipAssert.assertLastOperationSuccess;
import static org.junit.Assert.*;

import com.cisco.dhruva.callingIntegration.util.Token;
import java.text.ParseException;
import java.util.EventObject;
import java.util.List;
import javax.sip.InvalidArgumentException;
import javax.sip.RequestEvent;
import javax.sip.ResponseEvent;
import javax.sip.SipException;
import javax.sip.address.Address;
import javax.sip.address.AddressFactory;
import javax.sip.address.URI;
import javax.sip.header.*;
import javax.sip.message.Request;
import javax.sip.message.Response;
import org.cafesip.sipunit.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.testng.annotations.*;

public class DialInIT extends DhruvaIT {

  private static final Logger LOGGER = LoggerFactory.getLogger(DialInIT.class);

  private SipPhone pstn;
  private SipPhone antares;
  private SipPhone wxc;

  /** Initialize the sipStack and a user agent for the test. */
  @BeforeClass
  public void setUp() throws Exception {
    setUpStacks();
  }

  @AfterClass
  public void tearDown() {
    destroyStacks();
  }

  @Test
  public void testDialInPstn() throws InvalidArgumentException, ParseException {
    pstn = pstnStack.createSipPhone(dhruvaAddress, Token.UDP, dhruvaNetSpPort, pstnContactAddr);
    antares = antaresStack.createSipPhone(antaresContactAddr);
    antares.setLoopback(true);

    SipCall pstnCall = pstn.createSipCall();
    SipCall antaresCall = antares.createSipCall();

    // ---- ---- ---- ---- ---- ---- ---- ---- ----
    // pstn -> INVITE -> antares (via Dhruva)
    antaresCall.listenForIncomingCall();
    pstnCall.initiateOutgoingCall(antaresContactAddr, null);
    assertLastOperationSuccess("PSTN initiate call failed - " + pstnCall.format(), pstnCall);
    LOGGER.info("INVITE successfully sent by PSTN !!!");
    antaresCall.waitForIncomingCall(timeout);
    assertLastOperationSuccess(
        "Antares wait incoming call failed - " + antaresCall.format(), antaresCall);
    LOGGER.info("INVITE successfully received by Antares !!!");
    SipRequest antaresRcvdInv = antaresCall.getLastReceivedRequest();
    assertEquals(
        "Requri assertion failed",
        "sip:antares-it-guest@test.beech.com;calltype=DialIn;x-cisco-dpn=iccse10099;x-cisco-opn=eccse10099",
        antaresRcvdInv.getRequestURI());
    assertHeaderContains(
        "Via header assertion failed",
        antaresRcvdInv,
        ViaHeader.NAME,
        "SIP/2.0/UDP " + dhruvaAddress + ":" + dhruvaNetAntaresPort);
    assertHeaderContains(
        "Route header assertion failed",
        antaresRcvdInv,
        RouteHeader.NAME,
        "<sip:" + testHostAddress + ":" + antaresPort + ";transport=udp;lr>");
    assertHeaderContains(
        "Record-Route assertion failed",
        antaresRcvdInv,
        RecordRouteHeader.NAME,
        "<sip:rr$n=net_sp@" + dhruvaAddress + ":" + dhruvaNetAntaresPort + ";transport=udp;lr>");

    // pstn receives 100 from dhruva
    pstnCall.waitOutgoingCallResponse(timeout);
    assertLastOperationSuccess("PSTN await 100 response failed - " + pstnCall.format(), pstnCall);
    assertEquals(Response.TRYING, pstnCall.getLastReceivedResponse().getStatusCode());
    LOGGER.info("100 Trying successfully received by PSTN from Dhruva !!!");

    // ---- ---- ---- ---- ---- ----
    // dhruva <- 100 <- antares
    antaresCall.sendIncomingCallResponse(Response.TRYING, null, -1);
    assertLastOperationSuccess("Antares send 100 failed - " + antaresCall.format(), antaresCall);
    LOGGER.info("100 Trying successfully sent by Antares to Dhruva !!!");

    // ---- ---- ---- ---- ---- ----
    // pstn <- 180 <- antares
    antaresCall.sendIncomingCallResponse(Response.RINGING, null, -1);
    assertLastOperationSuccess("Antares send 180 failed - " + antaresCall.format(), antaresCall);
    LOGGER.info("180 Ringing successfully sent by Antares to Dhruva !!!");
    // pstn receiving 180
    pstnCall.waitOutgoingCallResponse(timeout);
    assertLastOperationSuccess("PSTN await 180 response failed - " + pstnCall.format(), pstnCall);
    assertEquals(Response.RINGING, pstnCall.getLastReceivedResponse().getStatusCode());
    LOGGER.info("180 Ringing successfully received by PSTN from Dhruva !!!");

    // ---- ---- ---- ---- ---- ----
    // pstn <- 200 <- antares
    antaresCall.sendIncomingCallResponse(Response.OK, null, -1);
    assertLastOperationSuccess("Antares send 200 failed - " + antaresCall.format(), antaresCall);
    LOGGER.info("200 OK successfully sent by Antares to Dhruva !!!");
    // pstn receiving 200
    pstnCall.waitOutgoingCallResponse(timeout);
    assertLastOperationSuccess("pstn await 200 response - " + pstnCall.format(), pstnCall);
    assertEquals(Response.OK, pstnCall.getLastReceivedResponse().getStatusCode());
    LOGGER.info("200 OK successfully received by PSTN from Dhruva !!!");

    // ---- ---- ---- ---- ---- ----
    // pstn -> ACK -> antares
    antaresCall.listenForAck();
    pstnCall.sendInviteOkAck();
    assertLastOperationSuccess("PSTN failed to send ACK - " + pstnCall.format(), pstnCall);
    LOGGER.info("ACK for 200 OK successfully sent by PSTN !!!");
    antaresCall.waitForAck(timeout);
    assertLastOperationSuccess(
        "Antares wait for ACK failed - " + antaresCall.format(), antaresCall);
    LOGGER.info("ACK for 200 OK successfully received by Antares !!!");

    // NOTE : This SipCall object must not be used again after calling dispose() method. BYE is
    // sent to the far end if the call dialog is in the confirmed state.
    // cleanup
    antares.dispose();
    pstn.dispose();
  }

  @Test
  public void testDailInB2B() throws InvalidArgumentException, ParseException {
    antares =
        antaresStack.createSipPhone(
            dhruvaAddress, Token.UDP, dhruvaNetAntaresPort, antaresContactAddr);
    wxc = wxcStack.createSipPhone(wxcContactAddr); // simulates both NS and AS behaviours
    wxc.setLoopback(true);

    AddressFactory antaresAddrFactory = antares.getParent().getAddressFactory();
    HeaderFactory antaresHeaderFactory = antares.getParent().getHeaderFactory();

    URI request_uri =
        antaresAddrFactory.createURI(
            "sip:wxc-it-guest@test.beech.com;calltype=DialIn;x-cisco-dpn=iccse10099;x-cisco-opn=eccse10099");
    CallIdHeader callId = antares.getParent().getSipProvider().getNewCallId();
    CSeqHeader cseq = antaresHeaderFactory.createCSeqHeader((long) 1, Request.INVITE);
    FromHeader from_header =
        antaresHeaderFactory.createFromHeader(antares.getAddress(), antares.generateNewTag());
    Address toAddress =
        antaresAddrFactory.createAddress(antaresAddrFactory.createURI(wxcContactAddr));
    ToHeader to_header = antaresHeaderFactory.createToHeader(toAddress, null);
    MaxForwardsHeader max_forwards = antaresHeaderFactory.createMaxForwardsHeader(5);
    List<ViaHeader> via_headers = antares.getViaHeaders();
    Request invite =
        antares
            .getParent()
            .getMessageFactory()
            .createRequest(
                request_uri,
                Request.INVITE,
                callId,
                cseq,
                from_header,
                to_header,
                via_headers,
                max_forwards);
    Address contactAddress = antaresAddrFactory.createAddress(antaresContactAddr);
    invite.addHeader(antaresHeaderFactory.createContactHeader(contactAddress));

    // ---- ---- ---- ---- ---- ---- ---- ---- ----
    // antares -> INVITE -> NS (via Dhruva)
    wxc.listenRequestMessage();
    SipTransaction trans = antares.sendRequestWithTransaction(invite, true, null);
    assertNotNull("Antares initiate call failed", trans);
    LOGGER.info("INVITE successfully sent by Antares !!!");
    RequestEvent incReq = wxc.waitRequest(timeout);
    assertNotNull("NS wait incoming call failed - " + wxc.format(), incReq);
    LOGGER.info("INVITE successfully received by NS !!!");

    SipRequest nsRcvdInv = new SipRequest(incReq.getRequest());
    assertEquals(
        "Requri assertion failed", "sip:wxc-it-guest@ns1.cc.com", nsRcvdInv.getRequestURI());
    assertHeaderContains(
        "Via header assertion failed",
        nsRcvdInv,
        ViaHeader.NAME,
        "SIP/2.0/UDP " + dhruvaAddress + ":" + dhruvaNetCcPort);
    assertHeaderContains(
        "Route header assertion failed",
        nsRcvdInv,
        RouteHeader.NAME,
        "<sip:" + testHostAddress + ":" + wxcPort + ";transport=udp;lr>");
    assertHeaderContains(
        "Record-Route assertion failed",
        nsRcvdInv,
        RecordRouteHeader.NAME,
        "<sip:rr$n=net_antares@" + dhruvaAddress + ":" + dhruvaNetCcPort + ";transport=udp;lr>");

    // antares will receive 100 from Dhruva
    EventObject responseEvent = antares.waitResponse(trans, timeout);
    assertNotNull("Antares await 100 response failed - " + antares.format(), responseEvent);
    assertEquals(Response.TRYING, ((ResponseEvent) responseEvent).getResponse().getStatusCode());
    LOGGER.info("100 Trying successfully received by Antares from Dhruva !!!");

    // ---- ---- ---- ---- ---- ----
    // dhruva <- 302 <- ns
    URI calleeContact =
        wxc.getParent()
            .getAddressFactory()
            .createURI("sip:wxc-it-guest@" + testHostAddress + ":" + wxcPort + ";transport=UDP");
    Address contact = wxc.getParent().getAddressFactory().createAddress(calleeContact);
    String toTag = wxc.generateNewTag();
    // NS sending 302
    SipTransaction trans_302 =
        wxc.sendReply(incReq, Response.MOVED_TEMPORARILY, null, toTag, contact, -1);
    assertNotNull("NS failed to send 302 - " + wxc.format(), trans_302);
    LOGGER.info("302 Moved Temporarily successfully sent by NS to Dhruva !!!");

    // ---- ---- ---- ---- ---- ----
    // dhruva -> INVITE -> as
    incReq = wxc.waitRequest(timeout);
    assertNotNull("AS await INVITE failed - " + wxc.format(), incReq);
    LOGGER.info("INVITE successfully received by AS from Dhruva !!!");
    SipRequest asRcvdInv = new SipRequest(incReq.getRequest());
    assertEquals(
        "Requri assertion failed",
        "sip:wxc-it-guest@" + testHostAddress,
        asRcvdInv.getRequestURI());
    assertHeaderContains(
        "Via header assertion failed",
        asRcvdInv,
        ViaHeader.NAME,
        "SIP/2.0/UDP " + dhruvaAddress + ":" + dhruvaNetCcPort);
    assertHeaderContains(
        "Route header assertion failed",
        asRcvdInv,
        RouteHeader.NAME,
        "<sip:" + testHostAddress + ":" + wxcPort + ";transport=udp;lr>");
    assertHeaderContains(
        "Record-Route assertion failed",
        asRcvdInv,
        RecordRouteHeader.NAME,
        "<sip:rr$n=net_antares@" + dhruvaAddress + ":" + dhruvaNetCcPort + ";transport=udp;lr>");

    // dhruva <- 100 <- as
    Response response_100 =
        wxc.getParent().getMessageFactory().createResponse(Response.TRYING, incReq.getRequest());
    SipTransaction asTrans = wxc.sendReply(incReq, response_100);
    assertNotNull("AS send 100 failed - " + wxc.format(), asTrans);
    LOGGER.info("100 Trying successfully sent by AS to Dhruva !!!");

    // antares <- 180 <- as (via Dhruva)
    URI asContact =
        wxc.getParent()
            .getAddressFactory()
            .createURI("sip:wxc-it-guest@" + testHostAddress + ":" + wxcPort);
    Address asContactAddr = wxc.getParent().getAddressFactory().createAddress(asContact);
    String to = wxc.generateNewTag();
    // as sending 180
    wxc.sendReply(asTrans, Response.RINGING, null, to, asContactAddr, -1);
    assertLastOperationSuccess("AS send 180 failed - " + wxc.format(), wxc);
    LOGGER.info("180 Ringing successfully sent by AS to Dhruva !!!");
    // antares receiving 180
    responseEvent = antares.waitResponse(trans, timeout);
    assertNotNull("Antares await 180 response failed - " + antares.format(), responseEvent);
    assertEquals(Response.RINGING, ((ResponseEvent) responseEvent).getResponse().getStatusCode());
    LOGGER.info("180 Ringing successfully received by Antares from Dhruva !!!");

    // ---- ---- ---- ---- ---- ----
    // dhruva <- 200 <- as
    Response response_200 =
        wxc.getParent().getMessageFactory().createResponse(Response.OK, incReq.getRequest());
    response_200.addHeader(wxc.getParent().getHeaderFactory().createContactHeader(asContactAddr));
    // as sending 200
    wxc.sendReply(asTrans, response_200);
    assertLastOperationSuccess("AS send 200 failed - " + wxc.format(), wxc);
    LOGGER.info("200 OK successfully sent by AS to Dhruva !!!");
    // antares receiving 200
    responseEvent = antares.waitResponse(trans, timeout);
    assertNotNull("Antares await 200 response failed - " + antares.format(), responseEvent);
    ResponseEvent respEvent = ((ResponseEvent) responseEvent);
    Response rcvdResponse = respEvent.getResponse();
    assertEquals(Response.OK, rcvdResponse.getStatusCode());
    LOGGER.info("200 OK successfully received by Antares from Dhruva !!!");

    // Antares -> ACKs -> as (via Dhruva)
    CSeqHeader cSeqHeader = (CSeqHeader) rcvdResponse.getHeader(CSeqHeader.NAME);
    if (cSeqHeader.getMethod().equals(Request.INVITE)) {
      try {
        Request ack = respEvent.getDialog().createAck(cSeqHeader.getSeqNumber());
        respEvent.getDialog().sendAck(ack);
        LOGGER.info("ACK successfully sent by Antares !!!");
      } catch (SipException e) {
        LOGGER.error("Antares failed to send ACK for received 200 !!!");
      }
    }
    incReq = wxc.waitRequest(timeout);
    assertNotNull("AS await ACK for 200 failed - " + wxc.format(), incReq);
    assertEquals(Request.ACK, incReq.getRequest().getMethod());
    LOGGER.info("ACK successfully received by AS !!!");

    antares.dispose();
    wxc.dispose();
  }
}
