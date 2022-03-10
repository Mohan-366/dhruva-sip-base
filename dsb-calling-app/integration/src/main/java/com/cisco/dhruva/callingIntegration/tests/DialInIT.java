package com.cisco.dhruva.callingIntegration.tests;

import static org.cafesip.sipunit.SipAssert.assertHeaderContains;
import static org.cafesip.sipunit.SipAssert.assertLastOperationSuccess;
import static org.junit.Assert.*;

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

  @BeforeMethod
  public void injectDnsOverrides() throws IOException {
    injectDNS();
  }

  @AfterMethod
  public void deleteDnsOverrides() throws IOException {
    deleteDns();
  }

  @Test
  public void testDialInPstn() throws InvalidArgumentException, ParseException {
    pstn = pstnStack.createSipPhone(dhruvaAddress, Token.UDP, dhruvaNetSpPort, pstnContactAddr);
    antares = antaresStack.createSipPhone(antaresContactAddr);
    antares.setLoopback(true);

    AddressFactory pstnAddrFactory = pstn.getParent().getAddressFactory();
    HeaderFactory pstnHeaderFactory = pstn.getParent().getHeaderFactory();

    URI request_uri = pstnAddrFactory.createURI("sip:antares-it-guest@cisco.com;x-cisco-test");
    CallIdHeader callId = pstn.getParent().getSipProvider().getNewCallId();
    CSeqHeader cseq = pstnHeaderFactory.createCSeqHeader((long) 1, Request.INVITE);
    FromHeader from_header =
        pstnHeaderFactory.createFromHeader(pstn.getAddress(), pstn.generateNewTag());
    Address toAddress =
        pstnAddrFactory.createAddress(pstnAddrFactory.createURI(antaresContactAddr));
    ToHeader to_header = pstnHeaderFactory.createToHeader(toAddress, null);
    MaxForwardsHeader max_forwards = pstnHeaderFactory.createMaxForwardsHeader(5);
    List<ViaHeader> via_headers = pstn.getViaHeaders();
    Request invite =
        pstn.getParent()
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
    Address contactAddress = pstnAddrFactory.createAddress(pstnContactAddr);
    invite.addHeader(pstnHeaderFactory.createContactHeader(contactAddress));

    // ---- ---- ---- ---- ---- ---- ---- ---- ----
    // pstn -> INVITE -> antares (via Dhruva)
    antares.listenRequestMessage();
    SipTransaction pstnTrans = pstn.sendRequestWithTransaction(invite, true, null);
    assertLastOperationSuccess("PSTN initiate call failed - " + pstn.format(), pstn);
    LOGGER.info("INVITE successfully sent by PSTN !!!");
    RequestEvent incReq = antares.waitRequest(timeout);
    assertLastOperationSuccess("Antares wait incoming call failed - " + antares.format(), antares);
    LOGGER.info("INVITE successfully received by Antares !!!");

    SipRequest antaresRcvdInv = new SipRequest(incReq.getRequest());
    assertEquals(
        "Requri assertion failed",
        "sip:antares-it-guest@"
            + antaresARecord
            + ";x-cisco-test;calltype=DialIn;x-cisco-dpn=iccse10099;x-cisco-opn=eccse10099",
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
    EventObject responseEvent = pstn.waitResponse(pstnTrans, timeout);
    assertNotNull("PSTN await 100 response failed - " + pstn.format(), responseEvent);
    assertEquals(Response.TRYING, ((ResponseEvent) responseEvent).getResponse().getStatusCode());
    LOGGER.info("100 Trying successfully received by PSTN from Dhruva !!!");

    // ---- ---- ---- ---- ---- ----
    // dhruva <- 100 <- antares
    Response response_100 =
        antares
            .getParent()
            .getMessageFactory()
            .createResponse(Response.TRYING, incReq.getRequest());
    SipTransaction antaresTrans = antares.sendReply(incReq, response_100);
    assertNotNull("Antares send 100 failed - " + antares.format(), antaresTrans);
    LOGGER.info("100 Trying successfully sent by Antares to Dhruva !!!");

    // ---- ---- ---- ---- ---- ----
    // pstn <- 180 <- antares
    URI antaresContact =
        antares
            .getParent()
            .getAddressFactory()
            .createURI("sip:antares-it-guest@" + testHostAddress + ":" + antaresPort);
    Address antaresContactAddr =
        antares.getParent().getAddressFactory().createAddress(antaresContact);
    String to = antares.generateNewTag();
    // antares sending 180
    antares.sendReply(antaresTrans, Response.RINGING, null, to, antaresContactAddr, -1);
    assertLastOperationSuccess("Antares send 180 failed - " + antares.format(), antares);
    LOGGER.info("180 Ringing successfully sent by Antares to Dhruva !!!");
    // pstn receiving 180
    responseEvent = pstn.waitResponse(pstnTrans, timeout);
    assertNotNull("PSTN await 180 response failed - " + pstn.format(), responseEvent);
    assertEquals(Response.RINGING, ((ResponseEvent) responseEvent).getResponse().getStatusCode());
    LOGGER.info("180 Ringing successfully received by PSTN from Dhruva !!!");

    // ---- ---- ---- ---- ---- ----
    // pstn <- 200 <- antares
    Response response_200 =
        antares.getParent().getMessageFactory().createResponse(Response.OK, incReq.getRequest());
    response_200.addHeader(
        antares.getParent().getHeaderFactory().createContactHeader(antaresContactAddr));
    // antares sending 200
    antares.sendReply(antaresTrans, response_200);
    assertLastOperationSuccess("Antares send 200 failed - " + antares.format(), antares);
    LOGGER.info("200 OK successfully sent by Antares to Dhruva !!!");
    // pstn receiving 200
    responseEvent = pstn.waitResponse(pstnTrans, timeout);
    assertNotNull("Pstn await 200 response - " + pstn.format(), responseEvent);
    ResponseEvent respEvent = ((ResponseEvent) responseEvent);
    Response rcvdResponse = respEvent.getResponse();
    assertEquals(Response.OK, rcvdResponse.getStatusCode());
    LOGGER.info("200 OK successfully received by PSTN from Dhruva !!!");

    // ---- ---- ---- ---- ---- ----
    // pstn -> ACK -> antares
    CSeqHeader cSeqHeader = (CSeqHeader) rcvdResponse.getHeader(CSeqHeader.NAME);
    if (cSeqHeader.getMethod().equals(Request.INVITE)) {
      try {
        Request ack = respEvent.getDialog().createAck(cSeqHeader.getSeqNumber());
        respEvent.getDialog().sendAck(ack);
        LOGGER.info("ACK for 200 OK successfully sent by PSTN !!!");
      } catch (SipException e) {
        LOGGER.error("PSTN failed to send ACK for received 200 !!!");
      }
    }
    incReq = antares.waitRequest(timeout);
    assertNotNull("Antares wait for ACK failed - " + antares.format(), incReq);
    assertEquals(Request.ACK, incReq.getRequest().getMethod());
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
            "sip:wxc-it-guest@"
                + antaresARecord
                + ";x-cisco-test;calltype=DialIn;x-cisco-dpn=iccse10099;x-cisco-opn=eccse10099");
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
        "Requri assertion failed",
        "sip:wxc-it-guest@" + nsARecord + ";x-cisco-test",
        nsRcvdInv.getRequestURI());
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
    Response response_302 =
        wxc.getParent()
            .getMessageFactory()
            .createResponse(Response.MOVED_TEMPORARILY, incReq.getRequest());
    String toTag = wxc.generateNewTag();
    ((ToHeader) response_302.getHeader(ToHeader.NAME)).setTag(toTag);

    // TODO: add the below contact, and add failover scenario [after non-2xx ACK bug is fixed]
    /*URI as1Contact =
            wxc.getParent()
                    .getAddressFactory()
                    .createURI("sip:wxc-it-guest@test1.as.com" + ":" + wxcPort + ";transport=UDP");
    Address contact1 = wxc.getParent().getAddressFactory().createAddress(as1Contact);
    ContactHeader as1ContactHeader = wxc.getParent().getHeaderFactory().createContactHeader(contact1);
    as1ContactHeader.setParameter("q", "0.5");
    response_302.addHeader(as1ContactHeader);*/
    URI as2Contact =
        wxc.getParent()
            .getAddressFactory()
            .createURI("sip:wxc-it-guest@" + as2ARecord + ":" + wxcPort + ";transport=UDP");
    Address contact2 = wxc.getParent().getAddressFactory().createAddress(as2Contact);
    ContactHeader as2ContactHeader =
        wxc.getParent().getHeaderFactory().createContactHeader(contact2);
    as2ContactHeader.setParameter("q", "0.4");
    response_302.addHeader(as2ContactHeader);
    // NS sending 302
    SipTransaction trans_302 = wxc.sendReply(incReq, response_302);
    assertNotNull("NS failed to send 302 - " + wxc.format(), trans_302);
    LOGGER.info("302 Moved Temporarily successfully sent by NS to Dhruva !!!");

    /*// ---- ---- ---- ---- ---- ----
    // dhruva -> INVITE -> as1
    incReq = wxc.waitRequest(timeout);
    assertNotNull("AS 1 await INVITE failed - " + wxc.format(), incReq);
    LOGGER.info("INVITE successfully received by AS 1 from Dhruva !!!");
    SipRequest as1RcvdInv = new SipRequest(incReq.getRequest());
    assertEquals(
        "Requri assertion failed",
        "sip:wxc-it-guest@test1.as.com;x-cisco-test",
        as1RcvdInv.getRequestURI());
    assertHeaderContains(
        "Via header assertion failed",
        as1RcvdInv,
        ViaHeader.NAME,
        "SIP/2.0/UDP " + dhruvaAddress + ":" + dhruvaNetCcPort);
    assertHeaderContains(
        "Route header assertion failed",
        as1RcvdInv,
        RouteHeader.NAME,
        "<sip:" + testHostAddress + ":" + wxcPort + ";transport=udp;lr>");
    assertHeaderContains(
        "Record-Route assertion failed",
        as1RcvdInv,
        RecordRouteHeader.NAME,
        "<sip:rr$n=net_antares@" + dhruvaAddress + ":" + dhruvaNetCcPort + ";transport=udp;lr>");

    // dhruva <- 400 <- as1
    Response response_404 =
            wxc.getParent()
                    .getMessageFactory()
                    .createResponse(Response.NOT_FOUND, incReq.getRequest());
    SipTransaction as1Trans = wxc.sendReply(incReq, response_404);
    assertNotNull("AS 1 send 404 failed -  " + wxc.format(), as1Trans);
    LOGGER.info("404 Trying successfully sent by AS 1 to Dhruva !!!");*/

    // ---- ---- ---- ---- ---- ----
    // dhruva -> INVITE -> as2
    incReq = wxc.waitRequest(timeout);
    assertNotNull("AS 2 await INVITE failed - " + wxc.format(), incReq);
    LOGGER.info("INVITE successfully received by AS 2 from Dhruva !!!");
    SipRequest as2RcvdInv = new SipRequest(incReq.getRequest());
    assertEquals(
        "Requri assertion failed",
        "sip:wxc-it-guest@" + as2ARecord + ";x-cisco-test",
        as2RcvdInv.getRequestURI());
    assertHeaderContains(
        "Via header assertion failed",
        as2RcvdInv,
        ViaHeader.NAME,
        "SIP/2.0/UDP " + dhruvaAddress + ":" + dhruvaNetCcPort);
    assertHeaderContains(
        "Route header assertion failed",
        as2RcvdInv,
        RouteHeader.NAME,
        "<sip:" + testHostAddress + ":" + wxcPort + ";transport=udp;lr>");
    assertHeaderContains(
        "Record-Route assertion failed",
        as2RcvdInv,
        RecordRouteHeader.NAME,
        "<sip:rr$n=net_antares@" + dhruvaAddress + ":" + dhruvaNetCcPort + ";transport=udp;lr>");

    // dhruva <- 100 <- as2
    Response response_100 =
        wxc.getParent().getMessageFactory().createResponse(Response.TRYING, incReq.getRequest());
    SipTransaction as2Trans = wxc.sendReply(incReq, response_100);
    assertNotNull("AS 2 send 100 failed - " + wxc.format(), as2Trans);
    LOGGER.info("100 Trying successfully sent by AS 2 to Dhruva !!!");

    // antares <- 180 <- as2 (via Dhruva)
    URI asContact =
        wxc.getParent()
            .getAddressFactory()
            .createURI("sip:wxc-it-guest@" + testHostAddress + ":" + wxcPort);
    Address asContactAddr = wxc.getParent().getAddressFactory().createAddress(asContact);
    String to = wxc.generateNewTag();
    // as2 sending 180
    wxc.sendReply(as2Trans, Response.RINGING, null, to, asContactAddr, -1);
    assertLastOperationSuccess("AS 2 send 180 failed - " + wxc.format(), wxc);
    LOGGER.info("180 Ringing successfully sent by AS 2 to Dhruva !!!");
    // antares receiving 180
    responseEvent = antares.waitResponse(trans, timeout);
    assertNotNull("Antares await 180 response failed - " + antares.format(), responseEvent);
    assertEquals(Response.RINGING, ((ResponseEvent) responseEvent).getResponse().getStatusCode());
    LOGGER.info("180 Ringing successfully received by Antares from Dhruva !!!");

    // ---- ---- ---- ---- ---- ----
    // antares <- 200 <- as2 (via Dhruva)
    Response response_200 =
        wxc.getParent().getMessageFactory().createResponse(Response.OK, incReq.getRequest());
    response_200.addHeader(wxc.getParent().getHeaderFactory().createContactHeader(asContactAddr));
    // as2 sending 200
    wxc.sendReply(as2Trans, response_200);
    assertLastOperationSuccess("AS 2 send 200 failed - " + wxc.format(), wxc);
    LOGGER.info("200 OK successfully sent by AS 2 to Dhruva !!!");
    // antares receiving 200
    responseEvent = antares.waitResponse(trans, timeout);
    assertNotNull("Antares await 200 response failed - " + antares.format(), responseEvent);
    ResponseEvent respEvent = ((ResponseEvent) responseEvent);
    Response rcvdResponse = respEvent.getResponse();
    assertEquals(Response.OK, rcvdResponse.getStatusCode());
    LOGGER.info("200 OK successfully received by Antares from Dhruva !!!");

    // Antares -> ACKs -> as2 (via Dhruva)
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
    assertNotNull("AS 2 await ACK for 200 failed - " + wxc.format(), incReq);
    assertEquals(Request.ACK, incReq.getRequest().getMethod());
    LOGGER.info("ACK successfully received by AS 2 !!!");

    wxc.dispose();
    antares.dispose();
  }
}
