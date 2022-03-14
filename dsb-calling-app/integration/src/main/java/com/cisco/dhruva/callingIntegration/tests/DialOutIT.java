package com.cisco.dhruva.callingIntegration.tests;

import static org.cafesip.sipunit.SipAssert.assertHeaderContains;
import static org.cafesip.sipunit.SipAssert.assertLastOperationSuccess;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;

import com.cisco.dhruva.callingIntegration.util.Token;
import java.io.IOException;
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

public class DialOutIT extends DhruvaIT {

  private static final Logger LOGGER = LoggerFactory.getLogger(DialOutIT.class);

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

  @BeforeMethod
  public void injectDnsOverrides() throws IOException {
    injectDNS();
  }

  @AfterMethod
  public void deleteDnsOverrides() throws IOException {
    deleteDns();
  }

  @Test
  public void testDialOutWxC() throws InvalidArgumentException, ParseException {
    wxc =
        wxcStack.createSipPhone(
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
            + antaresARecord
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
    antares.sendReply(antaresTrans, Response.RINGING, null, to, antaresContactAddr, -1);
    assertLastOperationSuccess("Antares send 180 failed - " + antares.format(), antares);
    LOGGER.info("180 Ringing successfully sent by Antares to Dhruva !!!");
    // AS receiving 180
    responseEvent = wxc.waitResponse(trans, timeout);
    assertNotNull("AS await 180 response failed - " + wxc.format(), responseEvent);
    assertEquals(Response.RINGING, ((ResponseEvent) responseEvent).getResponse().getStatusCode());
    LOGGER.info("180 Ringing successfully received by AS from Dhruva !!!");

    // ---- ---- ---- ---- ---- ----
    // WxC (AS) <- 200 <- Antares
    Response response_200 =
        antares.getParent().getMessageFactory().createResponse(Response.OK, incReq.getRequest());
    response_200.addHeader(
        antares.getParent().getHeaderFactory().createContactHeader(antaresContactAddr));
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
    LOGGER.info("200 OK successfully received by AS from Dhruva !!!");

    // WxC (AS) -> ACKs -> Antares (via Dhruva)
    CSeqHeader cSeqHeader = (CSeqHeader) rcvdResponse.getHeader(CSeqHeader.NAME);
    if (cSeqHeader.getMethod().equals(Request.INVITE)) {
      try {
        Request ack = respEvent.getDialog().createAck(cSeqHeader.getSeqNumber());
        respEvent.getDialog().sendAck(ack);
        LOGGER.info("ACK successfully sent by AS !!!");
      } catch (SipException e) {
        LOGGER.error("AS failed to send ACK for received 200 !!!");
      }
    }
    incReq = antares.waitRequest(timeout);
    assertNotNull("Antares await ACK for 200 failed - " + antares.format(), incReq);
    assertEquals(Request.ACK, incReq.getRequest().getMethod());
    LOGGER.info("ACK successfully received by Antares !!!");

    antares.dispose();
    wxc.dispose();
  }

  @Test
  public void testDailOutB2B() throws InvalidArgumentException, ParseException {
    antares =
        antaresStack.createSipPhone(
            dhruvaAddress, Token.UDP, dhruvaNetAntaresPort, antaresContactAddr);
    pstn = pstnStack.createSipPhone(pstnContactAddr);
    pstn.setLoopback(true);

    AddressFactory antaresAddrFactory = antares.getParent().getAddressFactory();
    HeaderFactory antaresHeaderFactory = antares.getParent().getHeaderFactory();

    URI request_uri =
        antaresAddrFactory.createURI(
            "sip:pstn-it-guest@"
                + antaresARecord
                + ";x-cisco-test;dtg=CcpFusionIN;calltype=DialOut;x-cisco-dpn=eccse10099;x-cisco-opn=iccse10099");
    CallIdHeader callId = antares.getParent().getSipProvider().getNewCallId();
    CSeqHeader cseq = antaresHeaderFactory.createCSeqHeader((long) 1, Request.INVITE);
    FromHeader from_header =
        antaresHeaderFactory.createFromHeader(antares.getAddress(), antares.generateNewTag());
    Address toAddress =
        antaresAddrFactory.createAddress(antaresAddrFactory.createURI(pstnContactAddr));
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
    // antares -> INVITE -> PSTN (via Dhruva)
    pstn.listenRequestMessage();
    SipTransaction trans = antares.sendRequestWithTransaction(invite, true, null);
    assertNotNull("Antares initiate call failed", trans);
    LOGGER.info("INVITE successfully sent by Antares !!!");
    RequestEvent incReq = pstn.waitRequest(timeout);
    assertNotNull("PSTN wait incoming call failed - " + pstn.format(), incReq);
    LOGGER.info("INVITE successfully received by PSTN !!!");

    SipRequest pstnRcvdInv = new SipRequest(incReq.getRequest());
    assertEquals(
        "Requri assertion failed",
        "sip:pstn-it-guest@InPoolA;x-cisco-test",
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
        "<sip:" + testHostAddress + ":" + pstnPort + ";transport=udp;lr>");
    assertHeaderContains(
        "Record-Route assertion failed",
        pstnRcvdInv,
        RecordRouteHeader.NAME,
        "<sip:rr$n=net_antares@" + dhruvaAddress + ":" + dhruvaNetSpPort + ";transport=udp;lr>");

    // antares will receive 100 from Dhruva
    EventObject responseEvent = antares.waitResponse(trans, timeout);
    assertNotNull("Antares await 100 response failed - " + antares.format(), responseEvent);
    assertEquals(Response.TRYING, ((ResponseEvent) responseEvent).getResponse().getStatusCode());
    LOGGER.info("100 Trying successfully received by Antares from Dhruva !!!");

    // Dhruva <- 100 <- PSTN
    Response response_100 =
        pstn.getParent().getMessageFactory().createResponse(Response.TRYING, incReq.getRequest());
    SipTransaction pstnTrans = pstn.sendReply(incReq, response_100);
    assertNotNull("PSTN send 100 failed - " + pstn.format(), pstnTrans);
    LOGGER.info("100 Trying successfully sent by PSTN to Dhruva !!!");

    // antares <- 180 <- PSTN (via Dhruva)
    URI pstnContact =
        pstn.getParent()
            .getAddressFactory()
            .createURI("sip:pstn-it-guest@" + testHostAddress + ":" + pstnPort);
    Address pstnContactAddr = pstn.getParent().getAddressFactory().createAddress(pstnContact);
    String to = pstn.generateNewTag();
    // PSTN sending 180
    pstn.sendReply(pstnTrans, Response.RINGING, null, to, pstnContactAddr, -1);
    assertLastOperationSuccess("PSTN send 180 failed - " + pstn.format(), pstn);
    LOGGER.info("180 Ringing successfully sent by PSTN to Dhruva !!!");
    // antares receiving 180
    responseEvent = antares.waitResponse(trans, timeout);
    assertNotNull("Antares await 180 response failed - " + antares.format(), responseEvent);
    assertEquals(Response.RINGING, ((ResponseEvent) responseEvent).getResponse().getStatusCode());
    LOGGER.info("180 Ringing successfully received by Antares from Dhruva !!!");

    // ---- ---- ---- ---- ---- ----
    // dhruva <- 200 <- PSTN
    Response response_200 =
        pstn.getParent().getMessageFactory().createResponse(Response.OK, incReq.getRequest());
    response_200.addHeader(
        pstn.getParent().getHeaderFactory().createContactHeader(pstnContactAddr));
    // PSTN sending 200
    pstn.sendReply(pstnTrans, response_200);
    assertLastOperationSuccess("PSTN send 200 failed - " + pstn.format(), pstn);
    LOGGER.info("200 OK successfully sent by PSTN to Dhruva !!!");
    // antares receiving 200
    responseEvent = antares.waitResponse(trans, timeout);
    assertNotNull("Antares await 200 response failed - " + antares.format(), responseEvent);
    ResponseEvent respEvent = ((ResponseEvent) responseEvent);
    Response rcvdResponse = respEvent.getResponse();
    assertEquals(Response.OK, rcvdResponse.getStatusCode());
    LOGGER.info("200 OK successfully received by Antares from Dhruva !!!");

    // Antares -> ACKs -> PSTN (via Dhruva)
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
    incReq = pstn.waitRequest(timeout);
    assertNotNull("PSTN await ACK for 200 failed - " + pstn.format(), incReq);
    assertEquals(Request.ACK, incReq.getRequest().getMethod());
    LOGGER.info("ACK successfully received by PSTN !!!");

    pstn.dispose();
    antares.dispose();
  }
}
