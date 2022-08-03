package com.cisco.dhruva.callingIntegration.tests;

import static org.cafesip.sipunit.SipAssert.*;
import static org.testng.Assert.assertTrue;
import static org.testng.AssertJUnit.assertEquals;
import static org.testng.AssertJUnit.assertNotNull;

import com.cisco.dhruva.callingIntegration.util.Token;
import gov.nist.javax.sip.address.SipUri;
import gov.nist.javax.sip.header.SIPHeader;
import java.io.IOException;
import java.text.ParseException;
import java.util.ArrayList;
import java.util.EventObject;
import java.util.List;
import java.util.ListIterator;
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

public class DialOutIT extends DhruvaIT {

  private static final Logger LOGGER = LoggerFactory.getLogger(DialOutIT.class);

  private SipPhone pstnUsPoolB;
  private SipPhone pstnUsPoolASg1;
  private SipPhone pstnUsPoolAsg2;
  private SipPhone antares;
  private SipPhone wxc;

  /** Initialize the sipStack and a user agent for the test. */
  @BeforeClass
  public void setUp() throws Exception {
    nsStack = new SipStack(Token.UDP, nsPort, getProperties("nsAgent"));
    antaresStack = new SipStack(Token.UDP, antaresPort, getProperties("antaresAgent"));
    pstnUsPoolBStack = new SipStack(Token.UDP, pstnUsPoolBPort, getProperties("UsPoolB-PstnAgent"));
    pstnUsPoolASg1Stack =
        new SipStack(Token.UDP, pstnUsPoolASg1Port, getProperties("UsPoolASGE-1-PstnAgent"));
    pstnUsPoolASg2Stack =
        new SipStack(Token.UDP, pstnUsPoolASg2Port, getProperties("UsPoolASGE-2-PstnAgent"));
  }

  @AfterClass
  public void tearDown() {
    nsStack.dispose();
    antaresStack.dispose();
    pstnUsPoolBStack.dispose();
    pstnUsPoolASg1Stack.dispose();
    pstnUsPoolASg2Stack.dispose();
  }

  @Test(description = "Tests the call-flow from 'WxCalling core (AS) -> Dhruva -> Antares'")
  public void testDialOutWxC() throws InvalidArgumentException, ParseException, IOException {
    injectDNS();
    wxc =
        nsStack.createSipPhone(
            dhruvaAddress, Token.UDP, dhruvaNetCcPort, wxcContactAddr); // simulates AS behaviours
    antares = antaresStack.createSipPhone(antaresContactAddr);
    antares.setLoopback(true);

    AddressFactory wxcAddrFactory = wxc.getParent().getAddressFactory();
    HeaderFactory wxcHeaderFactory = wxc.getParent().getHeaderFactory();

    URI request_uri =
        wxcAddrFactory.createURI(
            "sip:antares-it-guest@" + antaresARecord + ";x-cisco-test;dtg=CcpFusionIN");
    CallIdHeader callId = wxc.getParent().getSipProvider().getNewCallId();
    CSeqHeader cseq = wxcHeaderFactory.createCSeqHeader((long) 1, Request.INVITE);
    FromHeader from_header =
        wxcHeaderFactory.createFromHeader(wxc.getAddress(), wxc.generateNewTag());
    Address toAddress = wxcAddrFactory.createAddress(wxcAddrFactory.createURI(antaresContactAddr));
    ToHeader to_header = wxcHeaderFactory.createToHeader(toAddress, null);
    MaxForwardsHeader max_forwards = wxcHeaderFactory.createMaxForwardsHeader(5);
    List<ViaHeader> via_headers = wxc.getViaHeaders();
    Request invite =
        wxc.getParent()
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
    Address contactAddress = wxcAddrFactory.createAddress(wxcContactAddr);
    invite.addHeader(wxcHeaderFactory.createContactHeader(contactAddress));
    invite.addHeader(paidRemote);
    invite.addHeader(ppidRemote);
    invite.addHeader(rpidRemote);

    // ---- ---- ---- ---- ---- ---- ---- ---- ----
    // WxC (AS) -> INVITE -> Antares (via Dhruva)
    antares.listenRequestMessage();
    SipTransaction trans = wxc.sendRequestWithTransaction(invite, true, null);
    assertNotNull("AS initiate call failed", trans);
    LOGGER.info("INVITE successfully sent by AS !!!");
    RequestEvent incReq = antares.waitRequest(timeout);
    assertNotNull("Antares wait incoming call failed - " + antares.format(), incReq);
    LOGGER.info("INVITE successfully received by Antares !!!");

    SipRequest antaresRcvdInv = new SipRequest(incReq.getRequest());
    assertEquals(
        "Requri assertion failed",
        "sip:antares-it-guest@"
            + testHostAddress
            + ":"
            + antaresPort
            + ";x-cisco-test;dtg=CcpFusionIN;calltype=DialOut;x-cisco-dpn=eccse10099;x-cisco-opn=iccse10099",
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
        "<sip:rr$n=net_cc@" + dhruvaAddress + ":" + dhruvaNetAntaresPort + ";transport=udp;lr>");
    testDialOutWxCtoB2BNormRequest(antaresRcvdInv);

    // WxC (AS) will receive 100 from Dhruva
    EventObject responseEvent = wxc.waitResponse(trans, timeout);
    assertNotNull("AS await 100 response failed - " + wxc.format(), responseEvent);
    assertEquals(Response.TRYING, ((ResponseEvent) responseEvent).getResponse().getStatusCode());
    LOGGER.info("100 Trying successfully received by AS from Dhruva !!!");

    // Dhruva <- 100 <- Antares
    Response response_100 =
        antares
            .getParent()
            .getMessageFactory()
            .createResponse(Response.TRYING, incReq.getRequest());
    SipTransaction antaresTrans = antares.sendReply(incReq, response_100);
    assertNotNull("Antares send 100 failed - " + antares.format(), antaresTrans);
    LOGGER.info("100 Trying successfully sent by Antares to Dhruva !!!");

    // WxC (AS) <- 180 <- Antares (via Dhruva)
    URI antaresContact =
        antares
            .getParent()
            .getAddressFactory()
            .createURI("sip:antares-it-guest@" + testHostAddress + ":" + antaresPort);
    Address antaresContactAddr =
        antares.getParent().getAddressFactory().createAddress(antaresContact);
    String to = antares.generateNewTag();
    // Antares sending 180
    List<Header> additionalHeaders = new ArrayList<>();
    additionalHeaders.add(paidRemote);
    additionalHeaders.add(ppidRemote);
    additionalHeaders.add(rpidRemote);
    additionalHeaders.addAll(diversionRemote);
    antares.sendReply(
        antaresTrans,
        Response.RINGING,
        null,
        to,
        antaresContactAddr,
        -1,
        (ArrayList<Header>) additionalHeaders,
        null,
        null);
    assertLastOperationSuccess("Antares send 180 failed - " + antares.format(), antares);
    LOGGER.info("180 Ringing successfully sent by Antares to Dhruva !!!");
    // AS receiving 180
    responseEvent = wxc.waitResponse(trans, timeout);
    assertNotNull("AS await 180 response failed - " + wxc.format(), responseEvent);
    assertEquals(Response.RINGING, ((ResponseEvent) responseEvent).getResponse().getStatusCode());
    testDialOutWxCtoB2BNormResponse(((ResponseEvent) responseEvent).getResponse());
    LOGGER.info("180 Ringing successfully received by AS from Dhruva !!!");

    // ---- ---- ---- ---- ---- ----
    // WxC (AS) <- 200 <- Antares
    Response response_200 =
        antares.getParent().getMessageFactory().createResponse(Response.OK, incReq.getRequest());
    response_200.addHeader(
        antares.getParent().getHeaderFactory().createContactHeader(antaresContactAddr));
    response_200.addHeader(paidRemote);
    response_200.addHeader(ppidRemote);
    response_200.addHeader(rpidRemote);
    response_200.addHeader(diversionRemote.get(0));
    response_200.addHeader(diversionRemote.get(1));
    response_200.addHeader(diversionRemote.get(2));
    response_200.addHeader(diversionRemote.get(3));
    // Antares sending 200
    antares.sendReply(antaresTrans, response_200);
    assertLastOperationSuccess("Antares send 200 failed - " + antares.format(), antares);
    LOGGER.info("200 OK successfully sent by Antares to Dhruva !!!");
    // AS receiving 200
    responseEvent = wxc.waitResponse(trans, timeout);
    assertNotNull("AS await 200 response failed - " + wxc.format(), responseEvent);
    ResponseEvent respEvent = ((ResponseEvent) responseEvent);
    Response rcvdResponse = respEvent.getResponse();
    assertEquals(Response.OK, rcvdResponse.getStatusCode());
    testDialOutWxCtoB2BNormResponse(rcvdResponse);
    LOGGER.info("200 OK successfully received by AS from Dhruva !!!");

    // WxC (AS) -> ACKs -> Antares (via Dhruva)
    CSeqHeader cSeqHeader = (CSeqHeader) rcvdResponse.getHeader(CSeqHeader.NAME);
    if (cSeqHeader.getMethod().equals(Request.INVITE)) {
      try {
        Request ack = respEvent.getDialog().createAck(cSeqHeader.getSeqNumber());
        ack.addHeader(paidRemote);
        ack.addHeader(ppidRemote);
        ack.addHeader(rpidRemote);
        respEvent.getDialog().sendAck(ack);
        LOGGER.info("ACK successfully sent by AS !!!");
      } catch (SipException e) {
        LOGGER.error("AS failed to send ACK for received 200 !!!");
      }
    }
    incReq = antares.waitRequest(timeout);
    assertNotNull("Antares await ACK for 200 failed - " + antares.format(), incReq);
    assertEquals(Request.ACK, incReq.getRequest().getMethod());
    testDialOutWxCtoB2BNormRequest(new SipRequest(incReq.getRequest()));
    LOGGER.info("ACK successfully received by Antares !!!");

    deleteDns();
    antares.dispose();
    wxc.dispose();
  }

  @Test(
      description =
          "Tests the call-flow from 'Antares -> Dhruva -> PSTN'"
              + "Also includes SG & SGE failover scenario (i.e) call gets routed to PSTN Pool 1(has only one SGE) which returns an error response code."
              + "Dhruva now tries the next PSTN Pool 2(has 2 SGEs), wherein the 1st chosen SGE also returns an error response and the 2nd SGE accepts the call")
  public void testDailOutB2B() throws InvalidArgumentException, ParseException {
    antares =
        antaresStack.createSipPhone(
            dhruvaAddress, Token.UDP, dhruvaNetAntaresPort, antaresContactAddr);

    pstnUsPoolB = pstnUsPoolBStack.createSipPhone(pstnContactAddr);
    pstnUsPoolB.setLoopback(true);
    pstnUsPoolASg1 = pstnUsPoolASg1Stack.createSipPhone(pstnContactAddr);
    pstnUsPoolASg1.setLoopback(true);
    pstnUsPoolAsg2 = pstnUsPoolASg2Stack.createSipPhone(pstnContactAddr);
    pstnUsPoolAsg2.setLoopback(true);

    AddressFactory antaresAddrFactory = antares.getParent().getAddressFactory();
    HeaderFactory antaresHeaderFactory = antares.getParent().getHeaderFactory();

    URI request_uri =
        antaresAddrFactory.createURI(
            "sip:pstn-it-guest@"
                + antaresARecord
                + ";x-cisco-test;dtg=CcpFusionUS;calltype=DialOut;x-cisco-dpn=eccse10099;x-cisco-opn=iccse10099");
    CallIdHeader callId = antares.getParent().getSipProvider().getNewCallId();
    CSeqHeader cseq = antaresHeaderFactory.createCSeqHeader((long) 1, Request.INVITE);
    FromHeader from_header =
        antaresHeaderFactory.createFromHeader(antares.getAddress(), antares.generateNewTag());
    Address toAddress =
        antaresAddrFactory.createAddress(antaresAddrFactory.createURI(pstnContactAddr));

    // Calling Dial out header format To:
    // <sip:+18776684488@10.252.103.171:5060;user=phone;dtg=DhruBwFxSIUS>
    SipUri sipURI = (SipUri) toAddress.getURI();
    sipURI.setParameter("dtg", "CcpFusionUS");
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
    invite.addHeader(paidRemote);
    invite.addHeader(ppidRemote);
    invite.addHeader(rpidRemote);
    invite.addHeader(diversionRemote.get(0));
    invite.addHeader(diversionRemote.get(1));
    invite.addHeader(diversionRemote.get(2));
    invite.addHeader(diversionRemote.get(3));
    invite.addHeader((xBroadworksDnc));
    invite.addHeader(xBroadWorksCorrelationInfo);

    // ---- ---- ---- ---- ---- ---- ---- ---- ----
    // antares -> INVITE -> PSTN (via Dhruva)
    pstnUsPoolB.listenRequestMessage();
    pstnUsPoolASg1.listenRequestMessage();
    pstnUsPoolAsg2.listenRequestMessage();

    SipTransaction antaresTrans = antares.sendRequestWithTransaction(invite, true, null);
    assertNotNull("Antares initiate call failed", antaresTrans);
    LOGGER.info("INVITE successfully sent by Antares !!!");
    RequestEvent incReq = pstnUsPoolB.waitRequest(timeout);
    assertNotNull("PSTN UsPoolB wait incoming call failed - " + pstnUsPoolB.format(), incReq);
    LOGGER.info("INVITE successfully received by PSTN UsPoolB!!!");

    SipRequest pstnRcvdInv = new SipRequest(incReq.getRequest());
    assertEquals(
        "Requri assertion failed",
        "sip:pstn-it-guest@" + testHostAddress + ":" + pstnUsPoolBPort + ";x-cisco-test",
        pstnRcvdInv.getRequestURI());
    assertHeaderContains(
        "Via header assertion failed",
        pstnRcvdInv,
        ViaHeader.NAME,
        "SIP/2.0/UDP " + dhruvaAddress + ":" + dhruvaNetSpPort);
    assertHeaderContains(
        "Route header assertion failed",
        pstnRcvdInv,
        RouteHeader.NAME,
        "<sip:" + testHostAddress + ":" + pstnUsPoolBPort + ";transport=udp;lr>");
    assertHeaderContains(
        "Record-Route assertion failed",
        pstnRcvdInv,
        RecordRouteHeader.NAME,
        "<sip:rr$n=net_antares@" + dhruvaAddress + ":" + dhruvaNetSpPort + ";transport=udp;lr>");
    // To: <sip:pstn-it-guest@127.0.0.1;dtg=CcpFusionUS;user=phone>
    // dtg param in To header must not be there
    ToHeader toTest = (ToHeader) pstnRcvdInv.getMessage().getHeader(ToHeader.NAME);
    assertEquals(
        "To header assertion failed",
        "<sip:pstn-it-guest@" + testHostAddress + ">",
        toTest.getAddress().toString());
    testDialOutB2BToPSTNNormRequest(pstnRcvdInv);

    // antares will receive 100 from Dhruva
    EventObject responseEvent = antares.waitResponse(antaresTrans, timeout);
    assertNotNull("Antares await 100 response failed - " + antares.format(), responseEvent);
    assertEquals(Response.TRYING, ((ResponseEvent) responseEvent).getResponse().getStatusCode());
    LOGGER.info("100 Trying successfully received by Antares from Dhruva !!!");

    // ---- ---- ---- ---- ---- ---- ---- ---- ----
    // Dhruva <- 100 <- PSTN UsPoolB
    Response response_100 =
        pstnUsPoolB
            .getParent()
            .getMessageFactory()
            .createResponse(Response.TRYING, incReq.getRequest());
    SipTransaction pstnTrans = pstnUsPoolB.sendReply(incReq, response_100);
    assertNotNull("PSTN UsPoolB send 100 failed - " + pstnUsPoolB.format(), pstnTrans);
    LOGGER.info("100 Trying successfully sent by PSTN UsPoolB to Dhruva !!!");

    // ---- ---- ---- ---- ---- ---- ---- ---- ----
    // Dhruva <- 502 <- PSTN UsPoolB
    URI pstnUsPoolBContactUri =
        pstnUsPoolB
            .getParent()
            .getAddressFactory()
            .createURI("sip:pstn-it-guest@" + testHostAddress + ":" + pstnUsPoolBPort);
    Address pstnUsPoolBContactAddr =
        pstnUsPoolB.getParent().getAddressFactory().createAddress(pstnUsPoolBContactUri);
    String to = pstnUsPoolB.generateNewTag();
    // PSTN UsPoolA SGE 2 sending 180
    pstnUsPoolB.sendReply(pstnTrans, Response.BAD_GATEWAY, null, to, pstnUsPoolBContactAddr, -1);
    assertLastOperationSuccess(
        "PSTN UsPoolB send 502 failed - " + pstnUsPoolB.format(), pstnUsPoolB);
    LOGGER.info("502 Bad Gateway successfully sent by PSTN UsPoolB to Dhruva !!!");

    // ---- ---- ---- ---- ---- ---- ---- ---- ----
    // Dhruva (fails-over to next SG) -> INVITE -> PSTN UsPoolA SGE 1
    incReq = pstnUsPoolASg1.waitRequest(timeout);
    assertNotNull(
        "PSTN UsPoolA SGE 1 wait incoming call failed - " + pstnUsPoolASg1.format(), incReq);
    LOGGER.info("INVITE successfully received by PSTN UsPoolA SGE 1 !!!");
    pstnRcvdInv = new SipRequest(incReq.getRequest());
    assertEquals(
        "Requri assertion failed",
        "sip:pstn-it-guest@" + testHostAddress + ":" + pstnUsPoolASg1Port + ";x-cisco-test",
        pstnRcvdInv.getRequestURI());
    assertHeaderContains(
        "Via header assertion failed",
        pstnRcvdInv,
        ViaHeader.NAME,
        "SIP/2.0/UDP " + dhruvaAddress + ":" + dhruvaNetSpPort);
    assertHeaderContains(
        "Route header assertion failed",
        pstnRcvdInv,
        RouteHeader.NAME,
        "<sip:" + testHostAddress + ":" + pstnUsPoolASg1Port + ";transport=udp;lr>");
    assertHeaderContains(
        "Record-Route assertion failed",
        pstnRcvdInv,
        RecordRouteHeader.NAME,
        "<sip:rr$n=net_antares@" + dhruvaAddress + ":" + dhruvaNetSpPort + ";transport=udp;lr>");

    // ---- ---- ---- ---- ---- ---- ---- ---- ----
    // Dhruva <- 100 <- PSTN UsPoolA SGE 1
    response_100 =
        pstnUsPoolASg1
            .getParent()
            .getMessageFactory()
            .createResponse(Response.TRYING, incReq.getRequest());
    pstnTrans = pstnUsPoolASg1.sendReply(incReq, response_100);
    assertNotNull("PSTN UsPoolA SGE 1 send 100 failed - " + pstnUsPoolASg1.format(), pstnTrans);
    LOGGER.info("100 Trying successfully sent by PSTN UsPoolA SGE 1 to Dhruva !!!");

    // ---- ---- ---- ---- ---- ---- ---- ---- ----
    // Dhruva <- 502 <- PSTN UsPoolA SGE 1
    URI pstnUsPoolASg1ContactUri =
        pstnUsPoolASg1
            .getParent()
            .getAddressFactory()
            .createURI("sip:pstn-it-guest@" + testHostAddress + ":" + pstnUsPoolASg1Port);
    Address pstnUsPoolASg1ContactAddr =
        pstnUsPoolASg1.getParent().getAddressFactory().createAddress(pstnUsPoolASg1ContactUri);
    to = pstnUsPoolASg1.generateNewTag();
    // PSTN UsPoolA SGE 2 sending 180
    pstnUsPoolASg1.sendReply(
        pstnTrans, Response.BAD_GATEWAY, null, to, pstnUsPoolASg1ContactAddr, -1);
    assertLastOperationSuccess(
        "PSTN UsPoolA SGE 1 send 502 failed - " + pstnUsPoolASg1.format(), pstnUsPoolASg1);
    LOGGER.info("502 Bad Gateway successfully sent by PSTN UsPoolA SGE 1 to Dhruva !!!");

    // ---- ---- ---- ---- ---- ---- ---- ---- ----
    // Dhruva (fails-over to next SGE) -> INVITE -> PSTN UsPoolA SGE 2
    incReq = pstnUsPoolAsg2.waitRequest(timeout);
    assertNotNull(
        "PSTN UsPoolA SGE 2 wait incoming call failed - " + pstnUsPoolAsg2.format(), incReq);
    LOGGER.info("INVITE successfully received by PSTN UsPoolA SGE 2 !!!");

    pstnRcvdInv = new SipRequest(incReq.getRequest());
    assertEquals(
        "Requri assertion failed",
        "sip:pstn-it-guest@" + testHostAddress + ":" + pstnUsPoolASg2Port + ";x-cisco-test",
        pstnRcvdInv.getRequestURI());
    assertHeaderContains(
        "Via header assertion failed",
        pstnRcvdInv,
        ViaHeader.NAME,
        "SIP/2.0/UDP " + dhruvaAddress + ":" + dhruvaNetSpPort);
    assertHeaderContains(
        "Route header assertion failed",
        pstnRcvdInv,
        RouteHeader.NAME,
        "<sip:" + testHostAddress + ":" + pstnUsPoolASg2Port + ";transport=udp;lr>");
    assertHeaderContains(
        "Record-Route assertion failed",
        pstnRcvdInv,
        RecordRouteHeader.NAME,
        "<sip:rr$n=net_antares@" + dhruvaAddress + ":" + dhruvaNetSpPort + ";transport=udp;lr>");

    // ---- ---- ---- ---- ---- ---- ---- ---- ----
    // Dhruva <- 100 <- PSTN UsPoolA SGE 2
    response_100 =
        pstnUsPoolAsg2
            .getParent()
            .getMessageFactory()
            .createResponse(Response.TRYING, incReq.getRequest());
    pstnTrans = pstnUsPoolAsg2.sendReply(incReq, response_100);
    assertNotNull("PSTN UsPoolA SGE 2 send 100 failed - " + pstnUsPoolAsg2.format(), pstnTrans);
    LOGGER.info("100 Trying successfully sent by PSTN UsPoolA SGE 2 to Dhruva !!!");

    // ---- ---- ---- ---- ---- ---- ---- ---- ----
    // antares <- 180 <- PSTN UsPoolA SGE 2 (via Dhruva)
    URI pstnUsPoolASg2ContactUri =
        pstnUsPoolAsg2
            .getParent()
            .getAddressFactory()
            .createURI("sip:pstn-it-guest@" + testHostAddress + ":" + pstnUsPoolASg2Port);
    Address pstnUsPoolASg2ContactAddr =
        pstnUsPoolAsg2.getParent().getAddressFactory().createAddress(pstnUsPoolASg2ContactUri);
    to = pstnUsPoolAsg2.generateNewTag();
    // PSTN UsPoolA SGE 2 sending 180
    pstnUsPoolAsg2.sendReply(pstnTrans, Response.RINGING, null, to, pstnUsPoolASg2ContactAddr, -1);
    assertLastOperationSuccess(
        "PSTN UsPoolA SGE 2 send 180 failed - " + pstnUsPoolAsg2.format(), pstnUsPoolAsg2);
    LOGGER.info("180 Ringing successfully sent by PSTN UsPoolA SGE 2 to Dhruva !!!");
    // antares receiving 180
    responseEvent = antares.waitResponse(antaresTrans, timeout);

    // TODO @Shri
    // This is temp fix for intermittent IT test failure
    // Occasionally we receive 100 trying instead of 180 expected.
    // This may be due to delay that happens sometimes, Dhruva may just send 100 trying
    // before 180 is received.
    // We need to find a way to come over the timing issues which plagues generally all tests
    // historically
    if (((ResponseEvent) responseEvent).getResponse().getStatusCode() == Response.TRYING) {
      // ignore and again wait for 180
      responseEvent = antares.waitResponse(antaresTrans, timeout);
    }
    assertNotNull("Antares await 180 response failed - " + antares.format(), responseEvent);
    assertEquals(Response.RINGING, ((ResponseEvent) responseEvent).getResponse().getStatusCode());
    LOGGER.info("180 Ringing successfully received by Antares from Dhruva !!!");

    // ---- ---- ---- ---- ---- ----
    // antares <- 200 <- PSTN UsPoolA SGE 2 (via Dhruva)
    Response response_200 =
        pstnUsPoolAsg2
            .getParent()
            .getMessageFactory()
            .createResponse(Response.OK, incReq.getRequest());
    response_200.addHeader(
        pstnUsPoolAsg2
            .getParent()
            .getHeaderFactory()
            .createContactHeader(pstnUsPoolASg2ContactAddr));
    // PSTN UsPoolA SGE 2 sending 200
    pstnUsPoolAsg2.sendReply(pstnTrans, response_200);
    assertLastOperationSuccess(
        "PSTN UsPoolA SGE 2 send 200 failed - " + pstnUsPoolAsg2.format(), pstnUsPoolAsg2);
    LOGGER.info("200 OK successfully sent by PSTN UsPoolA SGE 2 to Dhruva !!!");
    // antares receiving 200
    responseEvent = antares.waitResponse(antaresTrans, timeout);
    assertNotNull("Antares await 200 response failed - " + antares.format(), responseEvent);
    ResponseEvent respEvent = ((ResponseEvent) responseEvent);
    Response rcvdResponse = respEvent.getResponse();
    assertEquals(Response.OK, rcvdResponse.getStatusCode());
    LOGGER.info("200 OK successfully received by Antares from Dhruva !!!");

    // ---- ---- ---- ---- ---- ---- ---- ---- ----
    // Antares -> ACKs -> PSTN UsPoolA SGE 2 (via Dhruva)
    CSeqHeader cSeqHeader = (CSeqHeader) rcvdResponse.getHeader(CSeqHeader.NAME);
    if (cSeqHeader.getMethod().equals(Request.INVITE)) {
      try {
        Request ack = respEvent.getDialog().createAck(cSeqHeader.getSeqNumber());
        ack.addHeader(paidRemote);
        ack.addHeader(ppidRemote);
        ack.addHeader(rpidRemote);
        ack.addHeader(xBroadworksDnc);
        ack.addHeader(xBroadWorksCorrelationInfo);
        ack.addHeader(diversionRemote.get(0));
        ack.addHeader(diversionRemote.get(1));
        ack.addHeader(diversionRemote.get(2));
        ack.addHeader(diversionRemote.get(3));
        respEvent.getDialog().sendAck(ack);
        LOGGER.info("ACK successfully sent by Antares !!!");
      } catch (SipException e) {
        LOGGER.error("Antares failed to send ACK for received 200 !!!");
      }
    }
    incReq = pstnUsPoolAsg2.waitRequest(timeout);
    SipRequest incAck = new SipRequest(incReq.getRequest());
    assertNotNull(
        "PSTN UsPoolA SGE 2 await ACK for 200 failed - " + pstnUsPoolAsg2.format(), incReq);
    assertEquals(Request.ACK, incReq.getRequest().getMethod());
    testDialOutB2BToPSTNNormRequest(incAck);

    LOGGER.info("ACK successfully received by PSTN UsPoolA SGE 2 !!!");

    pstnUsPoolB.dispose();
    pstnUsPoolASg1.dispose();
    pstnUsPoolAsg2.dispose();
    antares.dispose();
  }

  private void testDialOutWxCtoB2BNormRequest(SipRequest sipRequest) {
    assertHeaderContains("PAID assertion failed", sipRequest, PAID_HEADER, paidHeaderValueRemote);
    assertHeaderContains("PPID assertion failed", sipRequest, PPID_HEADER, ppidHeaderValueRemote);
    assertHeaderContains("RPID assertion failed", sipRequest, RPID_HEADER, rpidHeaderValueRemote);
  }

  private void testDialOutWxCtoB2BNormResponse(Response response) {
    assertEquals(paidLocal, (response.getHeader(PAID_HEADER)));
    assertEquals(ppidLocal, response.getHeader(PPID_HEADER));
    assertEquals(rpidLocal, response.getHeader(RPID_HEADER));
    ListIterator<SIPHeader> diversionHeadersReceived = (response.getHeaders(DIVERSION));
    assertTrue(diversionHeadersReceived != null);
    int count = 0;
    while (diversionHeadersReceived.hasNext()) {
      assertEquals(
          diversionLocal.toString().trim(), diversionHeadersReceived.next().toString().trim());
      count++;
    }
    assertTrue(count == 4);
  }

  private void testDialOutB2BToPSTNNormRequest(SipRequest sipRequest) {
    assertHeaderContains("PAID assertion failed", sipRequest, PAID_HEADER, paidHeaderValueLocal);
    assertHeaderContains("PPID assertion failed", sipRequest, PPID_HEADER, ppidHeaderValueLocal);
    assertHeaderContains("RPID assertion failed", sipRequest, RPID_HEADER, rpidHeaderValueLocal);
    assertEquals(null, sipRequest.getMessage().getHeader(X_BROADWORKS_DNC));
    assertEquals(null, sipRequest.getMessage().getHeader(X_BROADWORKS_CORRELATION_INFO));

    ListIterator<SIPHeader> diversionHeadersReceived =
        sipRequest.getMessage().getHeaders(DIVERSION);
    assertTrue(diversionHeadersReceived != null);
    int count = 0;
    while (diversionHeadersReceived.hasNext()) {
      assertEquals(
          diversionLocal.toString().trim(), diversionHeadersReceived.next().toString().trim());
      count++;
    }
    assertTrue(count == 4);
  }

  // as of now we do not normalize PSTN to B2B responses
  private void testDialOutB2BToPSTNNormResponse(Response response) {}
}
