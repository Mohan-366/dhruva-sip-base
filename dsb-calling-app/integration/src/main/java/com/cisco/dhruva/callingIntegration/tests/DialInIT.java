package com.cisco.dhruva.callingIntegration.tests;

import com.cisco.dhruva.callingIntegration.util.Token;
import gov.nist.javax.sip.header.SIPHeader;
import org.cafesip.sipunit.SipPhone;
import org.cafesip.sipunit.SipRequest;
import org.cafesip.sipunit.SipStack;
import org.cafesip.sipunit.SipTransaction;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.testng.annotations.AfterClass;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.Test;

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
import java.io.IOException;
import java.text.ParseException;
import java.util.ArrayList;
import java.util.EventObject;
import java.util.List;
import java.util.ListIterator;

import static org.cafesip.sipunit.SipAssert.assertHeaderContains;
import static org.cafesip.sipunit.SipAssert.assertLastOperationSuccess;
import static org.testng.Assert.assertTrue;
import static org.testng.AssertJUnit.assertEquals;
import static org.testng.AssertJUnit.assertNotNull;

public class DialInIT extends DhruvaIT {

  private static final Logger LOGGER = LoggerFactory.getLogger(DialInIT.class);

  private SipPhone pstn;
  private SipPhone antares;
  private SipPhone ns;
  private SipPhone as1;
  private SipPhone as2;

  /** Initialize the sipStack and a user agent for the test. */
  @BeforeClass
  public void setUp() throws Exception {
    pstnUsPoolBStack = new SipStack(Token.UDP, pstnUsPoolBPort, getProperties("UsPoolB-PstnAgent"));
    antaresStack = new SipStack(Token.UDP, antaresPort, getProperties("antaresAgent"));
    nsStack = new SipStack(Token.UDP, nsPort, getProperties("nsAgent"));
    as1Stack = new SipStack(Token.UDP, as1Port, getProperties("as1Agent"));
    as2Stack = new SipStack(Token.UDP, as2Port, getProperties("as2Agent"));
  }

  @AfterClass
  public void tearDown() {
    pstnUsPoolBStack.dispose();
    antaresStack.dispose();
    nsStack.dispose();
    as1Stack.dispose();
    as2Stack.dispose();
  }

  @Test(description = "Tests the call-flow from 'PSTN -> Dhruva -> Antares'")
  public void testDialInPstn() throws InvalidArgumentException, ParseException, IOException {
    injectDNS();
    pstn =
        pstnUsPoolBStack.createSipPhone(dhruvaAddress, Token.UDP, dhruvaNetSpPort, pstnContactAddr);
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
    invite.addHeader(paidRemote);
    invite.addHeader(ppidRemote);
    invite.addHeader(rpidRemote);

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
            + testHostAddress
            + ":"
            + antaresPort
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
    testDialInPSTNToB2BNormRequest(antaresRcvdInv);

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
    List<Header> additionalHeaders = new ArrayList<>();
    additionalHeaders.add(paidRemote);
    additionalHeaders.add(ppidRemote);
    additionalHeaders.add(rpidRemote);
    additionalHeaders.add(xBroadworksDnc);
    additionalHeaders.add(xBroadWorksCorrelationInfo);
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
    // pstn receiving 180
    responseEvent = pstn.waitResponse(pstnTrans, timeout);
    assertNotNull("PSTN await 180 response failed - " + pstn.format(), responseEvent);
    assertEquals(
        Response.RINGING, ((((ResponseEvent) responseEvent).getResponse()).getStatusCode()));
    testDialInPSTNToB2BNormResponse((((ResponseEvent) responseEvent).getResponse()));
    LOGGER.info("180 Ringing successfully received by PSTN from Dhruva !!!");

    // ---- ---- ---- ---- ---- ----
    // pstn <- 200 <- antares
    Response response_200 =
        antares.getParent().getMessageFactory().createResponse(Response.OK, incReq.getRequest());
    response_200.addHeader(
        antares.getParent().getHeaderFactory().createContactHeader(antaresContactAddr));
    response_200.addHeader(paidRemote);
    response_200.addHeader(ppidRemote);
    response_200.addHeader(rpidRemote);
    response_200.addHeader(xBroadworksDnc);
    response_200.addHeader(xBroadWorksCorrelationInfo);
    response_200.addHeader(diversionRemote.get(0));
    response_200.addHeader(diversionRemote.get(1));
    response_200.addHeader(diversionRemote.get(2));
    response_200.addHeader(diversionRemote.get(3));

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
    testDialInPSTNToB2BNormResponse((((ResponseEvent) responseEvent).getResponse()));
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
    deleteDns();
    antares.dispose();
    pstn.dispose();
  }

  @Test(
      description =
          "Tests the call-flow from 'Antares -> Dhruva -> NS/AS'"
              + "Also includes AS failover scenario (i.e) NS would reply to Dhruva with 302 containing AS's info(one or more) along with their q-values"
              + "If the chosen AS responds with an error response, then the next AS will be tried which responds successfully")
  public void testDailInB2B() throws InvalidArgumentException, ParseException, IOException {
    injectDNS();
    antares =
        antaresStack.createSipPhone(
            dhruvaAddress, Token.UDP, dhruvaNetAntaresPort, antaresContactAddr);
    ns = nsStack.createSipPhone(wxcContactAddr);
    ns.setLoopback(true);
    as1 = as1Stack.createSipPhone(wxcContactAddr);
    as1.setLoopback(true);
    as2 = as2Stack.createSipPhone(wxcContactAddr);
    as2.setLoopback(true);

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
    invite.addHeader(paidRemote);
    invite.addHeader(ppidRemote);
    invite.addHeader(rpidRemote);

    // ---- ---- ---- ---- ---- ---- ---- ---- ----
    // antares -> INVITE -> NS (via Dhruva)
    as1.listenRequestMessage();
    as2.listenRequestMessage();
    ns.listenRequestMessage();
    SipTransaction antaresTrans = antares.sendRequestWithTransaction(invite, true, null);
    assertNotNull("Antares initiate call failed", antaresTrans);
    LOGGER.info("INVITE successfully sent by Antares !!!");
    RequestEvent nsIncReq = ns.waitRequest(timeout);
    assertNotNull("NS wait incoming call failed - " + ns.format(), nsIncReq);
    LOGGER.info("INVITE successfully received by NS !!!");

    SipRequest nsRcvdInv = new SipRequest(nsIncReq.getRequest());
    assertEquals(
        "Requri assertion failed",
        "sip:wxc-it-guest@" + testHostAddress + ":" + nsPort + ";x-cisco-test",
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
        "<sip:" + testHostAddress + ":" + nsPort + ";transport=udp;lr>");
    assertHeaderContains(
        "Record-Route assertion failed",
        nsRcvdInv,
        RecordRouteHeader.NAME,
        "<sip:rr$n=net_antares@" + dhruvaAddress + ":" + dhruvaNetCcPort + ";transport=udp;lr>");
    testDialInB2BToCallingCoreNormRequest(nsRcvdInv);

    // ---- ---- ---- ---- ---- ---- ---- ---- ----
    // antares will receive 100 from Dhruva
    EventObject responseEvent = antares.waitResponse(antaresTrans, timeout);
    assertNotNull("Antares await 100 response failed - " + antares.format(), responseEvent);
    assertEquals(Response.TRYING, ((ResponseEvent) responseEvent).getResponse().getStatusCode());
    LOGGER.info("100 Trying successfully received by Antares from Dhruva !!!");

    // ---- ---- ---- ---- ---- ----
    // Dhruva <- 302 <- NS
    Response response_302 =
        ns.getParent()
            .getMessageFactory()
            .createResponse(Response.MOVED_TEMPORARILY, nsIncReq.getRequest());
    String toTag = ns.generateNewTag();
    ((ToHeader) response_302.getHeader(ToHeader.NAME)).setTag(toTag);
    URI as1Contact =
        ns.getParent()
            .getAddressFactory()
            .createURI("sip:wxc-it-guest@" + testHostAddress + ":" + as1Port + ";transport=UDP");
    Address contact1 = ns.getParent().getAddressFactory().createAddress(as1Contact);
    ContactHeader as1ContactHeader =
        ns.getParent().getHeaderFactory().createContactHeader(contact1);
    as1ContactHeader.setParameter("q", "0.5");
    response_302.addHeader(as1ContactHeader);
    URI as2Contact =
        ns.getParent()
            .getAddressFactory()
            .createURI("sip:wxc-it-guest@" + testHostAddress + ":" + as2Port + ";transport=UDP");
    Address contact2 = ns.getParent().getAddressFactory().createAddress(as2Contact);
    ContactHeader as2ContactHeader =
        ns.getParent().getHeaderFactory().createContactHeader(contact2);
    as2ContactHeader.setParameter("q", "0.4");
    response_302.addHeader(as2ContactHeader);
    // NS sending 302 with AS's info
    SipTransaction trans_302 = ns.sendReply(nsIncReq, response_302);
    assertNotNull("NS failed to send 302 - " + ns.format(), trans_302);
    LOGGER.info("302 Moved Temporarily successfully sent by NS to Dhruva !!!");

    // ---- ---- ---- ---- ---- ----
    // Dhruva -> INVITE -> as1
    RequestEvent as1IncReq = as1.waitRequest(timeout);
    assertNotNull("AS 1 await INVITE failed - " + as1.format(), as1IncReq);
    LOGGER.info("INVITE successfully received by AS 1 from Dhruva !!!");
    SipRequest as1RcvdInv = new SipRequest(as1IncReq.getRequest());
    assertEquals(
        "Requri assertion failed",
        "sip:wxc-it-guest@" + testHostAddress + ":" + as1Port + ";x-cisco-test",
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
        "<sip:" + testHostAddress + ":" + as1Port + ";transport=udp;lr>");
    assertHeaderContains(
        "Record-Route assertion failed",
        as1RcvdInv,
        RecordRouteHeader.NAME,
        "<sip:rr$n=net_antares@" + dhruvaAddress + ":" + dhruvaNetCcPort + ";transport=udp;lr>");
    testDialInB2BToCallingCoreNormRequest(as1RcvdInv);

    // ---- ---- ---- ---- ---- ----
    // antares <- 404 <- as1
    Response response_404 =
        as1.getParent()
            .getMessageFactory()
            .createResponse(Response.NOT_FOUND, as1IncReq.getRequest());
    SipTransaction as1Trans = as1.sendReply(as1IncReq, response_404);
    assertNotNull("AS 1 send 404 failed -  " + as1.format(), as1Trans);
    LOGGER.info("404 Not Found successfully sent by AS 1 to Dhruva !!!");

    // ---- ---- ---- ---- ---- ----
    // dhruva -> INVITE -> as2
    RequestEvent as2IncReq = as2.waitRequest(timeout);
    assertNotNull("AS 2 await INVITE failed - " + as2.format(), as2IncReq);
    LOGGER.info("INVITE successfully received by AS 2 from Dhruva !!!");
    SipRequest as2RcvdInv = new SipRequest(as2IncReq.getRequest());
    assertEquals(
        "Requri assertion failed",
        "sip:wxc-it-guest@" + testHostAddress + ":" + as2Port + ";x-cisco-test",
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
        "<sip:" + testHostAddress + ":" + as2Port + ";transport=udp;lr>");
    assertHeaderContains(
        "Record-Route assertion failed",
        as2RcvdInv,
        RecordRouteHeader.NAME,
        "<sip:rr$n=net_antares@" + dhruvaAddress + ":" + dhruvaNetCcPort + ";transport=udp;lr>");
    testDialInB2BToCallingCoreNormRequest(as2RcvdInv);

    // dhruva <- 100 <- as2
    Response response_100 =
        as2.getParent().getMessageFactory().createResponse(Response.TRYING, as2IncReq.getRequest());
    SipTransaction as2Trans = as2.sendReply(as2IncReq, response_100);
    assertNotNull("AS 2 send 100 failed - " + as2.format(), as2Trans);
    LOGGER.info("100 Trying successfully sent by AS 2 to Dhruva !!!");

    // antares <- 180 <- as2 (via Dhruva)
    URI as2ContactUri =
        as2.getParent()
            .getAddressFactory()
            .createURI("sip:wxc-it-guest@" + testHostAddress + ":" + as2Port);
    Address asContactAddr = as2.getParent().getAddressFactory().createAddress(as2ContactUri);
    String to = as2.generateNewTag();
    // as2 sending 180
    as2.sendReply(as2Trans, Response.RINGING, null, to, asContactAddr, -1);
    assertLastOperationSuccess("AS 2 send 180 failed - " + as2.format(), as2);
    LOGGER.info("180 Ringing successfully sent by AS 2 to Dhruva !!!");
    // antares receiving 180
    responseEvent = antares.waitResponse(antaresTrans, timeout);
    assertNotNull("Antares await 180 response failed - " + antares.format(), responseEvent);
    assertEquals(Response.RINGING, ((ResponseEvent) responseEvent).getResponse().getStatusCode());
    LOGGER.info("180 Ringing successfully received by Antares from Dhruva !!!");

    // ---- ---- ---- ---- ---- ----
    // antares <- 200 <- as2 (via Dhruva)
    Response response_200 =
        as2.getParent().getMessageFactory().createResponse(Response.OK, as2IncReq.getRequest());
    response_200.addHeader(as2.getParent().getHeaderFactory().createContactHeader(asContactAddr));
    // as2 sending 200
    as2.sendReply(as2Trans, response_200);
    assertLastOperationSuccess("AS 2 send 200 failed - " + as2.format(), as2);
    LOGGER.info("200 OK successfully sent by AS 2 to Dhruva !!!");
    // antares receiving 200
    responseEvent = antares.waitResponse(antaresTrans, timeout);
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
        ack.addHeader(paidRemote);
        ack.addHeader(ppidRemote);
        ack.addHeader(rpidRemote);
        respEvent.getDialog().sendAck(ack);
        LOGGER.info("ACK successfully sent by Antares !!!");
      } catch (SipException e) {
        LOGGER.error("Antares failed to send ACK for received 200 !!!");
      }
    }

    as2IncReq = as2.waitRequest(timeout);
    SipRequest as2IncAck = new SipRequest(as2IncReq.getRequest());
    assertNotNull("AS 2 await ACK for 200 failed - " + as2.format(), as2IncReq);
    assertEquals(Request.ACK, as2IncReq.getRequest().getMethod());
    testDialInB2BToCallingCoreNormRequest(as2IncAck);
    LOGGER.info("ACK successfully received by AS 2 !!!");

    deleteDns();
    as2.dispose();
    as1.dispose();
    ns.dispose();
    antares.dispose();
  }

  private void testDialInPSTNToB2BNormRequest(SipRequest sipRequest) {
    assertHeaderContains("PAID assertion failed", sipRequest, PAID_HEADER, paidHeaderValueRemote);
    assertHeaderContains("PPID assertion failed", sipRequest, PPID_HEADER, ppidHeaderValueRemote);
    assertHeaderContains("RPID assertion failed", sipRequest, RPID_HEADER, rpidHeaderValueRemote);
  }

  private void testDialInPSTNToB2BNormResponse(Response response) {
    assertEquals(paidLocal, (response.getHeader(PAID_HEADER)));
    assertEquals(ppidLocal, response.getHeader(PPID_HEADER));
    assertEquals(rpidLocal, response.getHeader(RPID_HEADER));
    assertEquals(null, response.getHeader(X_BROADWORKS_DNC));
    assertEquals(null, response.getHeader(X_BROADWORKS_CORRELATION_INFO));
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

  private void testDialInB2BToCallingCoreNormRequest(SipRequest sipRequest) {
    assertHeaderContains("PAID assertion failed", sipRequest, PAID_HEADER, paidHeaderValueLocal);
    assertHeaderContains("PPID assertion failed", sipRequest, PPID_HEADER, ppidHeaderValueLocal);
    assertHeaderContains("RPID assertion failed", sipRequest, RPID_HEADER, rpidHeaderValueLocal);
  }

  // as of now we do not normalize CC to B2B responses
  private void testDialInB2BToCallingCoreNormResponse(Response response) {}
}
