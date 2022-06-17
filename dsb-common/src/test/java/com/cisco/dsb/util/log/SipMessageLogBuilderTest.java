package com.cisco.dsb.util.log;

import com.cisco.dsb.common.util.log.SipMessageLogBuilder;
import gov.nist.javax.sip.address.AddressImpl;
import gov.nist.javax.sip.address.SipUri;
import gov.nist.javax.sip.header.HeaderFactoryImpl;
import gov.nist.javax.sip.header.RequestLine;
import gov.nist.javax.sip.message.SIPRequest;
import java.text.ParseException;
import java.util.Date;
import javax.sip.InvalidArgumentException;
import javax.sip.address.Address;
import javax.sip.header.CSeqHeader;
import javax.sip.header.FromHeader;
import javax.sip.header.Header;
import javax.sip.header.ToHeader;
import org.testng.Assert;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.Test;

public class SipMessageLogBuilderTest {

  SIPRequest sipRequest;

  @BeforeClass
  public void before() throws ParseException, InvalidArgumentException {
    sipRequest = new SIPRequest();
    SipUri requestUri = new SipUri();
    requestUri.setUser("bob");
    requestUri.setHost("bob@example.com");
    RequestLine requestLine = new RequestLine();
    requestLine.setMethod("INVITE");
    requestLine.setUri(requestUri);
    requestLine.setSipVersion("SIP/2.0");
    sipRequest.setRequestLine(requestLine);

    sipRequest.setCallId("1-2-3-4");
    SipUri toUri = new SipUri();
    toUri.setUser("bob");
    toUri.setHost("bob@example.com");
    Address toAddress = new AddressImpl();
    toAddress.setURI(toUri);
    ToHeader toHeader = new HeaderFactoryImpl().createToHeader(toAddress, "totag1234");
    sipRequest.setTo(toHeader);
    CSeqHeader cSeqHeader = new HeaderFactoryImpl().createCSeqHeader(1, "INVITE");
    sipRequest.setCSeq(cSeqHeader);

    SipUri fromUri = new SipUri();
    fromUri.setUser("alice");
    fromUri.setHost("alice@example.com");
    Address fromAddress = new AddressImpl();
    fromAddress.setURI(fromUri);
    FromHeader fromHeader = new HeaderFactoryImpl().createFromHeader(fromAddress, "fromTag4321");
    sipRequest.setFrom(fromHeader);
  }

  @Test
  public void testSipMessageLogBuilderForSessionId() throws ParseException {

    String localSessionId = "d5fe04c900804182bd50241916a470a5";
    String remoteSessionId = "00000000000000000000000000000000";
    Header sessionIdHeader =
        new HeaderFactoryImpl()
            .createHeader("Session-ID", localSessionId + ";remote=" + remoteSessionId);
    sipRequest.addHeader(sessionIdHeader);
    sipRequest.setHeader(sessionIdHeader);

    String formatter =
        new SipMessageLogBuilder()
            .buildWithContent(
                sipRequest, "1.1.1.1", "2.2.2.2", "received", false, new Date().getTime());

    Assert.assertTrue(formatter.contains("Session-ID: "));
    Assert.assertTrue(formatter.contains("localSessionId"));
    Assert.assertTrue(formatter.contains("remoteSessionId"));

    sipRequest.removeHeader("Session-ID");
  }

  @Test
  public void testSipMessageLogBuilderWithoutSessionId() {
    String formatter =
        new SipMessageLogBuilder()
            .buildWithContent(
                sipRequest, "1.1.1.1", "2.2.2.2", "received", false, new Date().getTime());
    Assert.assertFalse(formatter.contains("Session-ID: "));
    Assert.assertFalse(formatter.contains("localSessionId"));
    Assert.assertFalse(formatter.contains("remoteSessionId"));
  }
}
