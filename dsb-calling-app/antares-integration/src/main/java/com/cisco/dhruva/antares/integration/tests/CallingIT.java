package com.cisco.dhruva.antares.integration.tests;

import static org.cafesip.sipunit.SipAssert.*;
import static org.testng.AssertJUnit.assertEquals;
import static org.testng.AssertJUnit.assertNotNull;

import com.cisco.dhruva.antares.integration.util.Token;
import gov.nist.javax.sip.header.ims.PAssertedIdentityHeader;
import java.text.ParseException;
import java.util.EventObject;
import java.util.List;
import javax.sip.*;
import javax.sip.address.Address;
import javax.sip.address.AddressFactory;
import javax.sip.address.URI;
import javax.sip.header.*;
import javax.sip.message.Request;
import javax.sip.message.Response;
import org.cafesip.sipunit.*;
import org.cafesip.sipunit.SipStack;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.testng.annotations.AfterClass;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.Test;

public class CallingIT extends DhruvaIT {

  private static final Logger LOGGER = LoggerFactory.getLogger(CallingIT.class);

  private SipPhone pstn;
  private SipPhone as;

  /** Initialize the sipStack and a user agent for the test. */
  @BeforeClass
  public void setUp() throws Exception {
    pstnStack = new SipStack(Token.UDP, pstnPort, getProperties("PstnAgent"));
    nsStack = new SipStack(Token.UDP, nsPort, getProperties("NsAgent"));
    asStack = new SipStack(Token.UDP, asPort, getProperties("AsAgent"));
  }

  @AfterClass
  public void tearDown() {
    pstnStack.dispose();
    nsStack.dispose();
    asStack.dispose();
  }

  @Test
  public void dialInFullIT()
      throws InvalidArgumentException, ParseException, SipException, InterruptedException {
    pstn = pstnStack.createSipPhone(dhruvaAddress, Token.UDP, dhruvaNetSpPort, pstnContactAddr);
    SipPhone ns = nsStack.createSipPhone(wxcContactAddr);
    ns.setLoopback(true);
    as = asStack.createSipPhone(wxcContactAddr);
    as.setLoopback(true);

    AddressFactory pstnAddrFactory = pstn.getParent().getAddressFactory();
    HeaderFactory pstnHeaderFactory = pstn.getParent().getHeaderFactory();

    String paidLocal = pstnUser + dhruvaAddress + Token.COLON + pstnPort;
    String fromLocal = pstnUser + dhruvaAddress;

    String toRemote = wxcUser + dhruvaAddress;

    URI request_uri =
        pstnAddrFactory.createURI(wxcUser + dhruvaAddress + Token.COLON + dhruvaNetSpPort);
    CallIdHeader callId = pstn.getParent().getSipProvider().getNewCallId();
    CSeqHeader cseq = pstnHeaderFactory.createCSeqHeader((long) 1, Request.INVITE);
    FromHeader from_header =
        pstnHeaderFactory.createFromHeader(pstn.getAddress(), pstn.generateNewTag());
    Address toAddress =
        pstnAddrFactory.createAddress(pstnAddrFactory.createURI(wxcUser + dhruvaAddress));
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
    Address contactAddress =
        pstnAddrFactory.createAddress(pstnContactAddr + Token.COLON + pstnPort);
    invite.addHeader(pstnHeaderFactory.createContactHeader(contactAddress));
    PAssertedIdentityHeader paid_header =
        (PAssertedIdentityHeader)
            pstnHeaderFactory.createHeader(
                PAssertedIdentityHeader.NAME, pstnContactAddr + Token.COLON + pstnPort);
    invite.addHeader(paid_header);
    String sdp =
        "v=0\r\n"
            + "o=Sonus_UAC 370409 264169 IN IP4 "
            + testHostAddress
            + "\r\n"
            + "s=SIP Media Capabilities\r\n"
            + "c=IN IP4 "
            + testHostAddress
            + "\r\n"
            + "t=0 0\r\n"
            + "m=audio 19632 RTP/AVP 0 18 101\r\n"
            + "a=rtpmap:0 PCMU/8000\r\n"
            + "a=rtpmap:18 G729/8000\r\n"
            + "a=fmtp:18 annexb=no\r\n"
            + "a=rtpmap:101 telephone-event/8000\r\n"
            + "a=fmtp:101 0-15\r\n"
            + "a=sendrecv\r\n"
            + "a=ptime:20\r\n";
    ContentTypeHeader contentTypeHeader =
        pstnHeaderFactory.createContentTypeHeader("application", "sdp");
    byte[] contents = sdp.getBytes();
    invite.setContent(contents, contentTypeHeader);

    // ---- ---- ---- ---- ---- ---- ---- ---- ----
    // PSTN -> INVITE -> NS (via Dhruva, Antares)
    as.listenRequestMessage();
    ns.listenRequestMessage();
    SipTransaction pstnTrans = pstn.sendRequestWithTransaction(invite, true, null);
    assertLastOperationSuccess("PSTN initiate call failed - " + pstn.format(), pstn);
    LOGGER.info("INVITE successfully sent by PSTN !!!");
    RequestEvent nsIncReq = ns.waitRequest(timeout);
    assertNotNull("NS wait incoming call failed - " + ns.format(), nsIncReq);
    LOGGER.info("INVITE successfully received by NS !!!");
    validateWxCInvReq(nsPort, new SipRequest(nsIncReq.getRequest()), fromLocal, paidLocal);

    // ---- ---- ---- ---- ---- ---- ---- ---- ----
    // PSTN <- 100 <- dhruva
    EventObject responseEvent = pstn.waitResponse(pstnTrans, timeout);
    assertNotNull("PSTN await 100 response failed - " + pstn.format(), responseEvent);
    Response pstnIncResp = ((ResponseEvent) responseEvent).getResponse();
    assertEquals(Response.TRYING, pstnIncResp.getStatusCode());
    LOGGER.info("100 Trying successfully received by PSTN from Dhruva !!!");

    // ---- ---- ---- ---- ---- ----
    // Dhruva <- 302 <- NS
    Response response_302 =
        ns.getParent()
            .getMessageFactory()
            .createResponse(Response.MOVED_TEMPORARILY, nsIncReq.getRequest());
    String toTag = ns.generateNewTag();
    ((ToHeader) response_302.getHeader(ToHeader.NAME)).setTag(toTag);
    URI asContact =
        ns.getParent()
            .getAddressFactory()
            .createURI(
                wxcUser + testHostAddress + Token.COLON + asPort + ";user=phone;transport=udp");
    Address contact = ns.getParent().getAddressFactory().createAddress(asContact);
    ContactHeader asContactHeader = ns.getParent().getHeaderFactory().createContactHeader(contact);
    asContactHeader.setParameter("q", "0.5");
    response_302.addHeader(asContactHeader);
    // NS sending 302 with AS's info
    SipTransaction trans_302 = ns.sendReply(nsIncReq, response_302);
    assertNotNull("NS failed to send 302 - " + ns.format(), trans_302);
    LOGGER.info("302 Moved Temporarily successfully sent by NS to Dhruva !!!");

    // ---- ---- ---- ---- ---- ----
    // Dhruva -> INVITE -> AS
    RequestEvent asIncReq = as.waitRequest(timeout);
    assertNotNull("AS await INVITE failed - " + as.format(), asIncReq);
    LOGGER.info("INVITE successfully received by AS from Dhruva !!!");
    validateWxCInvReq(asPort, new SipRequest(asIncReq.getRequest()), fromLocal, paidLocal);

    // ---- ---- ---- ---- ---- ----
    // Dhruva <- 100 <- AS
    Response response_100 =
        as.getParent().getMessageFactory().createResponse(Response.TRYING, asIncReq.getRequest());
    SipTransaction asTrans = as.sendReply(asIncReq, response_100);
    assertNotNull("AS send 100 failed - " + as.format(), asTrans);
    LOGGER.info("100 Trying successfully sent by AS to Dhruva !!!");

    // ---- ---- ---- ---- ---- ----
    // PSTN <- 180 <- AS (via Dhruva, Antares)
    Response response_180 =
        as.getParent().getMessageFactory().createResponse(Response.RINGING, asIncReq.getRequest());
    String to = as.generateNewTag();
    ((ToHeader) response_180.getHeader(ToHeader.NAME)).setTag(to);
    URI asContactUri =
        as.getParent()
            .getAddressFactory()
            .createURI(Token.SIP_COLON + testHostAddress + Token.COLON + asPort);
    Address asContactAddr = as.getParent().getAddressFactory().createAddress(asContactUri);
    asContactHeader = as.getParent().getHeaderFactory().createContactHeader(asContactAddr);
    response_180.addHeader(asContactHeader);
    paid_header =
        (PAssertedIdentityHeader)
            as.getParent()
                .getHeaderFactory()
                .createHeader(PAssertedIdentityHeader.NAME, wxcContactAddr);
    response_180.addHeader(paid_header);
    Header xBroadWorksCorInfoHeader =
        as.getParent()
            .getHeaderFactory()
            .createHeader(X_BROADWORKS_CORRELATION_INFO, "c3098754-bc38-45cd-bd6a-0230f85d9741");
    response_180.addHeader(xBroadWorksCorInfoHeader);
    // AS sending 180
    as.sendReply(asTrans, response_180);
    assertLastOperationSuccess("AS send 180 failed - " + as.format(), as);
    LOGGER.info("180 Ringing successfully sent by AS to PSTN !!!");
    // PSTN receiving 180
    responseEvent = pstn.waitResponse(pstnTrans, timeout);
    assertNotNull("PSTN await 180 response failed - " + pstn.format(), responseEvent);
    pstnIncResp = ((ResponseEvent) responseEvent).getResponse();
    assertEquals(Response.RINGING, pstnIncResp.getStatusCode());
    LOGGER.info("180 Ringing successfully received by PSTN from AS !!!");
    validatePstnResp(new SipResponse(pstnIncResp), toRemote);

    // ---- ---- ---- ---- ---- ----
    // PSTN <- 200 <- AS (via Dhruva, Antares)
    Response response_200 =
        as.getParent().getMessageFactory().createResponse(Response.OK, asIncReq.getRequest());
    response_200.addHeader(asContactHeader);
    response_200.addHeader(paid_header);
    response_200.addHeader(xBroadWorksCorInfoHeader);
    sdp =
        "v=0\r\n"
            + "o=BroadWorks 443778 1 IN IP4 "
            + testHostAddress
            + "\r\n"
            + "s=-\r\n"
            + "c=IN IP4 "
            + testHostAddress
            + "\r\n"
            + "t=0 0\r\n"
            + "m=audio 21530 RTP/AVP 0 101\r\n"
            + "a=rtpmap:0 PCMU/8000\r\n"
            + "a=rtpmap:101 telephone-event/8000\r\n"
            + "a=sendrecv\r\n";
    contentTypeHeader =
        as.getParent().getHeaderFactory().createContentTypeHeader("application", "sdp");
    contents = sdp.getBytes();
    response_200.setContent(contents, contentTypeHeader);
    // AS sending 200
    as.sendReply(asTrans, response_200);
    assertLastOperationSuccess("AS send 200 failed - " + as.format(), as);
    LOGGER.info("200 OK successfully sent by AS to PSTN !!!");
    // PSTN receiving 200
    responseEvent = pstn.waitResponse(pstnTrans, timeout);
    assertNotNull("PSTN await 200 response - " + pstn.format(), responseEvent);
    ResponseEvent pstnRespEvent = ((ResponseEvent) responseEvent);
    pstnIncResp = pstnRespEvent.getResponse();
    assertEquals(Response.OK, pstnIncResp.getStatusCode());
    LOGGER.info("200 OK successfully received by PSTN from AS !!!");
    validatePstnResp(new SipResponse(pstnIncResp), toRemote);

    // ---- ---- ---- ---- ---- ----
    // PSTN -> ACK -> AS  (via Dhruva, Antares)
    CSeqHeader cSeqHeader = (CSeqHeader) pstnIncResp.getHeader(CSeqHeader.NAME);
    if (cSeqHeader.getMethod().equals(Request.INVITE)) {
      try {
        // PSTN sending ACK
        Request ack = pstnRespEvent.getDialog().createAck(cSeqHeader.getSeqNumber());
        pstnRespEvent.getDialog().sendAck(ack);
        LOGGER.info("ACK for 200 OK successfully sent by PSTN !!!");
      } catch (SipException e) {
        LOGGER.error("PSTN failed to send ACK for received 200 !!!");
      }
    }
    // AS receiving ACK
    asIncReq = as.waitRequest(timeout);
    assertNotNull("AS await ACK for 200 failed - " + as.format(), asIncReq);
    assertEquals(Request.ACK, asIncReq.getRequest().getMethod());
    LOGGER.info("ACK successfully received by AS !!!");
    validateWxCMidCallReq(new SipRequest(asIncReq.getRequest()), fromLocal);

    Thread.sleep(5000);

    // ---- ---- ---- ---- ---- ----
    // PSTN -> BYE -> AS
    // PSTN <- 200
    // 200 <- AS
    Dialog dialog = pstnRespEvent.getDialog();
    Request bye = dialog.createRequest(Request.BYE);
    // PSTN sending BYE
    pstnTrans = pstn.sendRequestWithTransaction(bye, false, dialog);
    assertLastOperationSuccess("PSTN send BYE failed - " + pstn.format(), pstn);
    LOGGER.info("BYE successfully sent by PSTN !!!");

    // PSTN receiving 200 for BYE
    responseEvent = pstn.waitResponse(pstnTrans, timeout);
    assertNotNull("PSTN await 200 for BYE response failed - " + pstn.format(), responseEvent);
    pstnRespEvent = ((ResponseEvent) responseEvent);
    pstnIncResp = pstnRespEvent.getResponse();
    assertEquals(Response.OK, pstnIncResp.getStatusCode());
    LOGGER.info("200 OK for BYE successfully received by PSTN from Dhruva !!!");
    validatePstnRespFromTo(new SipResponse(pstnIncResp), toRemote);

    // AS receiving BYE
    asIncReq = as.waitRequest(timeout);
    assertNotNull("AS await BYE failed - " + as.format(), asIncReq);
    assertEquals(Request.BYE, asIncReq.getRequest().getMethod());
    LOGGER.info("BYE successfully received by AS !!!");
    validateWxCMidCallReq(new SipRequest(asIncReq.getRequest()), fromLocal);

    // AS sending 200 for BYE
    response_200 =
        as.getParent().getMessageFactory().createResponse(Response.OK, asIncReq.getRequest());
    as.sendReply(asIncReq, response_200);
    assertLastOperationSuccess("AS send 200 for BYE failed - " + as.format(), as);
    LOGGER.info("200 OK for BYE successfully sent by AS to PSTN !!!");

    as.dispose();
    ns.dispose();
    pstn.dispose();
  }

  @Test
  public void dialOutFullIT()
      throws InvalidArgumentException, ParseException, InterruptedException, SipException {
    as = asStack.createSipPhone(dhruvaAddress, Token.UDP, dhruvaNetCcPort, wxcContactAddr);
    pstn = pstnStack.createSipPhone(pstnContactAddr);
    pstn.setLoopback(true);

    AddressFactory asAddrFactory = as.getParent().getAddressFactory();
    HeaderFactory asHeaderFactory = as.getParent().getHeaderFactory();

    String fromLocal = wxcUser + dhruvaAddress;
    String toLocal = pstnContactAddr + Token.COLON + dhruvaNetCcPort + ";user=phone";

    String toRemote =
        pstnUser + dhruvaAddress + Token.COLON + dhruvaNetCcPort + ";user=phone;dtg=CcpFusionIN";

    URI request_uri =
        asAddrFactory.createURI(
            pstnUser
                + dhruvaAddress
                + Token.COLON
                + dhruvaNetCcPort
                + ";user=phone;transport=udp;dtg=CcpFusionIN");
    CallIdHeader callId = as.getParent().getSipProvider().getNewCallId();
    CSeqHeader cseq = asHeaderFactory.createCSeqHeader((long) 1, Request.INVITE);
    FromHeader from_header = asHeaderFactory.createFromHeader(as.getAddress(), as.generateNewTag());
    Address toAddress = asAddrFactory.createAddress(asAddrFactory.createURI(toRemote));
    ToHeader to_header = asHeaderFactory.createToHeader(toAddress, null);
    MaxForwardsHeader max_forwards = asHeaderFactory.createMaxForwardsHeader(5);
    List<ViaHeader> via_headers = as.getViaHeaders();
    Request invite =
        as.getParent()
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
    Address contactAddress =
        asAddrFactory.createAddress(Token.SIP_COLON + testHostAddress + Token.COLON + asPort);
    invite.addHeader(asHeaderFactory.createContactHeader(contactAddress));
    PAssertedIdentityHeader paid_header =
        (PAssertedIdentityHeader)
            asHeaderFactory.createHeader(PAssertedIdentityHeader.NAME, wxcContactAddr);
    invite.addHeader(paid_header);
    Header xBroadWorksDNCHeader =
        asHeaderFactory.createHeader(
            X_BROADWORKS_DNC,
            "network-address=\""
                + wxcContactAddr
                + ";user=phone\";user-id=\"a5qcpajae4@29496224.int10.bcld.webex.com\";net-ind=InterNetwork");
    invite.addHeader(xBroadWorksDNCHeader);
    Header xCiscoOrgIDHeader =
        asHeaderFactory.createHeader("X-Cisco-Org-ID", "df5eefb4-6d73-473e-8305-7b4b04271c06");
    invite.addHeader(xCiscoOrgIDHeader);
    Header xBroadWorksCorInfoHeader =
        asHeaderFactory.createHeader(
            X_BROADWORKS_CORRELATION_INFO, "8e5b226c-27a7-46cf-8539-59bafe6ca812");
    invite.addHeader(xBroadWorksCorInfoHeader);
    String sdp =
        "v=0\r\n"
            + "o=BroadWorks 63255 1 IN IP4 "
            + testHostAddress
            + "\r\n"
            + "s=-\r\n"
            + "c=IN IP4 "
            + testHostAddress
            + "\r\n"
            + "t=0 0\r\n"
            + "m=audio 20668 RTP/AVP 9 18 0 8 120 101\r\n"
            + "a=sendrecv\r\n"
            + "a=rtpmap:9 G722/8000\r\n"
            + "a=rtpmap:18 G729/8000\r\n"
            + "a=fmtp:18 annexb=no\r\n"
            + "a=rtpmap:0 PCMU/8000\r\n"
            + "a=rtpmap:8 PCMA/8000\r\n"
            + "a=rtpmap:120 opus/48000/2\r\n"
            + "a=rtpmap:101 telephone-event/8000\r\n";
    ContentTypeHeader contentTypeHeader =
        asHeaderFactory.createContentTypeHeader("application", "sdp");
    byte[] contents = sdp.getBytes();
    invite.setContent(contents, contentTypeHeader);

    // ---- ---- ---- ---- ---- ---- ---- ---- ----
    // AS -> INVITE -> PSTN (via Dhruva,Antares)
    pstn.listenRequestMessage();
    SipTransaction asTrans = as.sendRequestWithTransaction(invite, true, null);
    assertNotNull("AS initiate call failed", asTrans);
    LOGGER.info("INVITE successfully sent by AS !!!");
    RequestEvent pstnIncReq = pstn.waitRequest(timeout);
    assertNotNull("PSTN wait incoming call failed - " + pstn.format(), pstnIncReq);
    LOGGER.info("INVITE successfully received by PSTN !!!");
    validatePstnReq(new SipRequest(pstnIncReq.getRequest()), fromLocal);

    // ---- ---- ---- ---- ---- ---- ---- ---- ----
    // AS <- 100 <- Dhruva
    EventObject responseEvent = as.waitResponse(asTrans, timeout);
    assertNotNull("AS await 100 response failed - " + as.format(), responseEvent);
    assertEquals(Response.TRYING, ((ResponseEvent) responseEvent).getResponse().getStatusCode());
    LOGGER.info("100 Trying successfully received by AS from Dhruva !!!");

    // ---- ---- ---- ---- ---- ---- ---- ---- ----
    // Dhruva <- 100 <- PSTN
    Response response_100 =
        pstn.getParent()
            .getMessageFactory()
            .createResponse(Response.TRYING, pstnIncReq.getRequest());
    SipTransaction pstnTrans = pstn.sendReply(pstnIncReq, response_100);
    assertNotNull("PSTN send 100 failed - " + pstn.format(), pstnTrans);
    LOGGER.info("100 Trying successfully sent by PSTN to Dhruva !!!");

    // ---- ---- ---- ---- ---- ---- ---- ---- ----
    // AS <- 180 <- PSTN (via Dhruva,Antares)
    Response response_180 =
        pstn.getParent()
            .getMessageFactory()
            .createResponse(Response.RINGING, pstnIncReq.getRequest());
    String to = pstn.generateNewTag();
    ((ToHeader) response_180.getHeader(ToHeader.NAME)).setTag(to);
    URI pstnContactUri =
        pstn.getParent().getAddressFactory().createURI(pstnContactAddr + Token.COLON + pstnPort);
    Address pstnContactAddress = pstn.getParent().getAddressFactory().createAddress(pstnContactUri);
    ContactHeader pstnContactHeader =
        pstn.getParent().getHeaderFactory().createContactHeader(pstnContactAddress);
    response_180.addHeader(pstnContactHeader);
    // PSTN sending 180
    pstn.sendReply(pstnTrans, response_180);
    assertLastOperationSuccess("PSTN send 180 failed - " + pstn.format(), pstn);
    LOGGER.info("180 Ringing successfully sent by PSTN to AS !!!");
    // AS receiving 180
    responseEvent = as.waitResponse(asTrans, timeout);
    assertNotNull("AS await 180 response failed - " + as.format(), responseEvent);
    Response asIncResp = ((ResponseEvent) responseEvent).getResponse();
    assertEquals(Response.RINGING, asIncResp.getStatusCode());
    LOGGER.info("180 Ringing successfully received by AS from PSTN !!!");
    validateWxCResp(new SipResponse(asIncResp), toRemote);

    // ---- ---- ---- ---- ---- ----
    // AS <- 200 <- PSTN (via Dhruva,Antares)
    Response response_200 =
        pstn.getParent().getMessageFactory().createResponse(Response.OK, pstnIncReq.getRequest());
    response_200.addHeader(pstnContactHeader);
    sdp =
        "v=0\r\n"
            + "o=Sonus_UAC 415038 671381 IN IP4 "
            + testHostAddress
            + "\r\n"
            + "s=SIP Media Capabilities\r\n"
            + "c=IN IP4 "
            + testHostAddress
            + "\r\n"
            + "t=0 0\r\n"
            + "m=audio 59634 RTP/AVP 18 101\r\n"
            + "a=rtpmap:18 G729/8000\r\n"
            + "a=fmtp:18 annexb=no\r\n"
            + "a=rtpmap:101 telephone-event/8000\r\n"
            + "a=fmtp:101 0-15\r\n"
            + "a=sendrecv\r\n"
            + "a=ptime:20\r\n";
    contentTypeHeader =
        pstn.getParent().getHeaderFactory().createContentTypeHeader("application", "sdp");
    contents = sdp.getBytes();
    response_200.setContent(contents, contentTypeHeader);
    // PSTN sending 200
    pstn.sendReply(pstnTrans, response_200);
    assertLastOperationSuccess("PSTN send 200 failed - " + pstn.format(), pstn);
    LOGGER.info("200 OK successfully sent by PSTN to AS !!!");
    // AS receiving 200
    responseEvent = as.waitResponse(asTrans, timeout);
    assertNotNull("AS await 200 response failed - " + as.format(), responseEvent);
    ResponseEvent respEvent = ((ResponseEvent) responseEvent);
    asIncResp = respEvent.getResponse();
    assertEquals(Response.OK, asIncResp.getStatusCode());
    LOGGER.info("200 OK successfully received by AS from PSTN !!!");
    validateWxCResp(new SipResponse(asIncResp), toRemote);

    // ---- ---- ---- ---- ---- ----
    // AS -> ACK -> PSTN (via Dhruva, Antares)
    CSeqHeader cSeqHeader = (CSeqHeader) asIncResp.getHeader(CSeqHeader.NAME);
    if (cSeqHeader.getMethod().equals(Request.INVITE)) {
      try {
        // AS sending ACK
        Request ack = respEvent.getDialog().createAck(cSeqHeader.getSeqNumber());
        ack.addHeader(asHeaderFactory.createContactHeader(contactAddress));
        respEvent.getDialog().sendAck(ack);
        LOGGER.info("ACK for 200 OK successfully sent by AS !!!");
      } catch (SipException e) {
        LOGGER.error("AS failed to send ACK for received 200 !!!");
      }
    }
    // PSTN receiving ACK
    pstnIncReq = pstn.waitRequest(timeout);
    assertNotNull("PSTN await ACK for 200 failed - " + pstn.format(), pstnIncReq);
    assertEquals(Request.ACK, pstnIncReq.getRequest().getMethod());
    LOGGER.info("ACK successfully received by PSTN !!!");
    validatePstnMidCallReq(new SipRequest(pstnIncReq.getRequest()), fromLocal, toLocal);

    Thread.sleep(5000);

    // ---- ---- ---- ---- ---- ----
    // AS -> BYE -> PSTN
    // AS <- 200
    // 200 <- PSTN
    Dialog dialog = respEvent.getDialog();
    Request bye = dialog.createRequest(Request.BYE);

    // AS sending BYE
    asTrans = as.sendRequestWithTransaction(bye, false, dialog);
    assertLastOperationSuccess("AS send BYE failed - " + as.format(), as);
    LOGGER.info("BYE successfully sent by AS !!!");

    // AS receiving 200 for BYE
    responseEvent = as.waitResponse(asTrans, timeout);
    assertNotNull("AS await 200 for BYE response failed - " + as.format(), responseEvent);
    ResponseEvent asRespEvent = ((ResponseEvent) responseEvent);
    asIncResp = asRespEvent.getResponse();
    assertEquals(Response.OK, asIncResp.getStatusCode());
    LOGGER.info("200 OK for BYE successfully received by AS from Dhruva !!!");
    validateWxCResp(new SipResponse(asIncResp), toRemote);

    // PSTN receiving BYE
    pstnIncReq = pstn.waitRequest(timeout);
    assertNotNull("PSTN await BYE failed - " + pstn.format(), pstnIncReq);
    assertEquals(Request.BYE, pstnIncReq.getRequest().getMethod());
    LOGGER.info("BYE successfully received by PSTN !!!");
    validatePstnMidCallReq(new SipRequest(pstnIncReq.getRequest()), fromLocal, toLocal);

    // PSTN sending 200 for BYE
    response_200 =
        pstn.getParent().getMessageFactory().createResponse(Response.OK, pstnIncReq.getRequest());
    pstn.sendReply(pstnIncReq, response_200);
    assertLastOperationSuccess("PSTN send 200 for BYE failed - " + pstn.format(), pstn);
    LOGGER.info("200 OK for BYE successfully sent by PSTN to AS !!!");

    pstn.dispose();
    as.dispose();
  }

  private void validateWxCInvReq(
      int wxcPort, SipRequest incSipReq, String fromLocal, String paidLocal) {
    assertEquals(
        "Requri assertion failed",
        wxcContactAddr + Token.COLON + wxcPort,
        incSipReq.getRequestURI());
    assertHeaderContains("FROM assertion failed", incSipReq, FromHeader.NAME, fromLocal);
    assertHeaderContains("TO assertion failed", incSipReq, ToHeader.NAME, wxcContactAddr);
    assertHeaderContains(
        "PAID assertion failed", incSipReq, PAssertedIdentityHeader.NAME, paidLocal);
    LOGGER.info("Validation of WxC Invite SUCCESS");
  }

  private void validateWxCMidCallReq(SipRequest asIncSipReq, String fromLocal) {
    assertEquals(
        "Requri assertion failed",
        Token.SIP_COLON + testHostAddress + Token.COLON + asPort,
        asIncSipReq.getRequestURI());
    assertHeaderContains("FROM assertion failed", asIncSipReq, FromHeader.NAME, fromLocal);
    assertHeaderContains("TO assertion failed", asIncSipReq, ToHeader.NAME, wxcContactAddr);
    LOGGER.info("Validation of WxC mid-call requests(ACK, BYE) SUCCESS");
  }

  private void validatePstnResp(SipResponse pstnIncSipResp, String toRemote) {
    validatePstnRespFromTo(pstnIncSipResp, toRemote);
    assertHeaderContains(
        "PAID assertion failed", pstnIncSipResp, PAssertedIdentityHeader.NAME, toRemote);
    assertHeaderNotPresent(
        "X-BroadWorks-Correlation-Info Absence check failed",
        pstnIncSipResp,
        X_BROADWORKS_CORRELATION_INFO);
    LOGGER.info("Validation of PSTN responses SUCCESS");
  }

  private void validatePstnRespFromTo(SipResponse pstnIncSipResp, String toRemote) {
    assertHeaderContains("FROM assertion failed", pstnIncSipResp, FromHeader.NAME, pstnContactAddr);
    assertHeaderContains("TO assertion failed", pstnIncSipResp, ToHeader.NAME, toRemote);
    LOGGER.info("Validation of From, To of PSTN responses SUCCESS");
  }

  private void validatePstnReq(SipRequest pstnIncSipReq, String fromLocal) {
    assertEquals(
        "Requri assertion failed",
        pstnContactAddr + Token.COLON + pstnPort + ";user=phone;transport=udp",
        pstnIncSipReq.getRequestURI());
    assertHeaderContains("FROM assertion failed", pstnIncSipReq, FromHeader.NAME, fromLocal);
    assertHeaderContains(
        "TO assertion failed",
        pstnIncSipReq,
        ToHeader.NAME,
        pstnContactAddr + Token.COLON + dhruvaNetCcPort + ";user=phone");
    assertHeaderContains(
        "PAID assertion failed", pstnIncSipReq, PAssertedIdentityHeader.NAME, fromLocal);
    assertHeaderNotPresent(
        "X-BroadWorks-Correlation-Info Absence check failed",
        pstnIncSipReq,
        X_BROADWORKS_CORRELATION_INFO);
    assertHeaderNotPresent(
        "X-BroadWorks-DNC Absence check failed", pstnIncSipReq, X_BROADWORKS_DNC);
    LOGGER.info("Validation of PSTN Request SUCCESS");
  }

  private void validateWxCResp(SipResponse asIncSipResp, String toRemote) {
    assertHeaderContains("FROM assertion failed", asIncSipResp, FromHeader.NAME, wxcContactAddr);
    assertHeaderContains("TO assertion failed", asIncSipResp, ToHeader.NAME, toRemote);
    LOGGER.info("Validation of WxC responses SUCCESS");
  }

  private void validatePstnMidCallReq(SipRequest pstnIncSipReq, String fromLocal, String toLocal) {
    assertEquals(
        "Requri assertion failed",
        pstnContactAddr + Token.COLON + pstnPort,
        pstnIncSipReq.getRequestURI());
    assertHeaderContains("FROM assertion failed", pstnIncSipReq, FromHeader.NAME, fromLocal);
    assertHeaderContains("TO assertion failed", pstnIncSipReq, ToHeader.NAME, toLocal);
    LOGGER.info("Validation of PSTN mid-call requests(ACK, BYE) SUCCESS");
  }
}
