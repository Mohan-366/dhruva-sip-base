package com.cisco.dsb.proxy.sip;

import com.cisco.dsb.common.sip.jain.JainSipHelper;
import com.cisco.dsb.proxy.util.RequestHelper;
import com.cisco.dsb.proxy.util.ResponseHelper;
import gov.nist.core.Host;
import gov.nist.javax.sip.address.SipUri;
import gov.nist.javax.sip.address.TelURLImpl;
import gov.nist.javax.sip.message.SIPRequest;
import gov.nist.javax.sip.message.SIPResponse;
import gov.nist.javax.sip.stack.MessageChannel;
import java.text.ParseException;
import javax.net.ssl.SSLException;
import javax.sip.address.SipURI;
import javax.sip.header.ContactHeader;
import org.testng.Assert;
import org.testng.annotations.Test;

public class ProxyUtilsTest {
  @Test(description = "tests update contact header function")
  void testUpdateContactHeader() throws ParseException {
    SIPRequest sipRequest = new SIPRequest();
    ContactHeader contactHeader =
        JainSipHelper.createContactHeader("dhruva", "webex", "1.2.3.4", 5060);
    sipRequest.setHeader(contactHeader);
    ProxyUtils.updateContactHeader(sipRequest, contactHeader, "10.1.1.1", "tcp", 5080);
    SipURI sipURI = (SipURI) sipRequest.getContactHeader().getAddress().getURI();
    Assert.assertEquals("10.1.1.1", sipURI.getHost());
    Assert.assertEquals(5080, sipURI.getPort());
    Assert.assertEquals("tcp", sipURI.getTransportParam());
  }

  @Test(description = "tests getter for cseq")
  void testGetCseqNumber() throws ParseException {
    SIPRequest request = (SIPRequest) RequestHelper.getInviteRequest();
    Assert.assertEquals("1", ProxyUtils.getCseqNumber(request));
  }

  @Test(description = "tests getter for cseq method")
  void testGetCseqMethod() throws ParseException {
    SIPRequest request = (SIPRequest) RequestHelper.getInviteRequest();
    Assert.assertEquals("INVITE", ProxyUtils.getCseqMethod(request));
  }

  @Test(description = "tests getter for peer certificate")
  void testGetPeerCertificate() throws SSLException {
    Assert.assertNull(ProxyUtils.getPeerCertificate(new SIPRequest()));
    Assert.assertNull(ProxyUtils.getPeerCertificate((SIPRequest) null));
    Assert.assertNull(ProxyUtils.getPeerCertificate((MessageChannel) null));
  }

  @Test(description = "test to fetch the response class")
  void testGetResponseClass() throws ParseException {
    SIPRequest request = (SIPRequest) RequestHelper.getInviteRequest();
    SIPResponse sipResponse = ResponseHelper.getSipResponse(200, request);
    // SIPResponse sipResponse = new SIPResponse();
    assert sipResponse != null;
    sipResponse.setReasonPhrase("Just cause");
    Assert.assertEquals(2, ProxyUtils.getResponseClass(sipResponse));
  }

  @Test(description = "test to verify recognize api")
  void testRecognize() throws ParseException {
    SipURI sipURI = new SipUri();
    sipURI.setHost("1.2.3.4");
    sipURI.setPort(8080);
    sipURI.setTransportParam("tls");
    Assert.assertTrue(ProxyUtils.recognize("1.2.3.4", 8080, "tls", sipURI));
  }

  @Test
  void testRecognize2() {
    SipUri sipUri = new SipUri();
    sipUri.setDefaultParm("Entering recognize(", "Value");
    Assert.assertFalse(ProxyUtils.recognize("localhost", 8080, "Transport", sipUri));
  }

  @Test
  void testRecognize3() throws IllegalArgumentException {
    SipUri sipUri = new SipUri();
    sipUri.setHost(new Host("Entering recognize("));
    Assert.assertFalse(ProxyUtils.recognize("localhost", 8080, "Transport", sipUri));
  }

  @Test
  void testRecognize4() {
    SipUri sipUri = new SipUri();
    sipUri.setMAddr("42 Main St");
    Assert.assertFalse(ProxyUtils.recognize("localhost", 8080, "Transport", sipUri));
  }

  @Test
  void testRecognize5() throws IllegalArgumentException {
    SipUri sipUri = new SipUri();
    sipUri.setHost(new Host("Entering recognize("));
    Assert.assertFalse(ProxyUtils.recognize("Entering recognize(", 8080, "Transport", sipUri));
  }

  @Test
  void testRecognize6() throws IllegalArgumentException {
    SipUri sipUri = new SipUri();
    sipUri.setHost(new Host("Entering recognize("));
    Assert.assertFalse(ProxyUtils.recognize("Entering recognize(", -1, "Transport", sipUri));
  }

  @Test
  void testRecognize7() throws IllegalArgumentException {
    SipUri sipUri = new SipUri();
    sipUri.setMAddr("42 Main St");
    sipUri.setHost(new Host("Entering recognize("));
    Assert.assertFalse(ProxyUtils.recognize("Entering recognize(", 8080, "Transport", sipUri));
  }

  @Test
  void testRecognize8() throws IllegalArgumentException {
    SipUri sipUri = new SipUri();
    sipUri.setPort(2);
    sipUri.setHost(new Host("Entering recognize("));
    Assert.assertFalse(ProxyUtils.recognize("Entering recognize(", 8080, "Transport", sipUri));
  }

  @Test
  void testRecognize9() throws IllegalArgumentException {
    SipUri sipUri = new SipUri();
    sipUri.setHost(new Host("Entering recognize("));
    Assert.assertFalse(ProxyUtils.recognize((SipURI) sipUri, new SipUri()));
  }

  @Test
  void testRecognize10() {
    TelURLImpl uri = new TelURLImpl();
    Assert.assertFalse(ProxyUtils.recognize(uri, new SipUri()));
  }

  @Test
  void testRecognize11() throws ParseException {
    SipURI sipURI = new SipUri();
    sipURI.setHost("1.2.3.4");
    sipURI.setPort(8090);
    sipURI.setTransportParam("tls");
    Assert.assertFalse(ProxyUtils.recognize("1.2.3.4", 8080, "tls", sipURI));
  }

  @Test
  void testRecognize12() throws ParseException {
    SipURI sipURI = new SipUri();
    sipURI.setHost("1.2.3.4");
    sipURI.setPort(8080);
    sipURI.setTransportParam("tcp");
    Assert.assertFalse(ProxyUtils.recognize("1.2.3.4", 8080, "tls", sipURI));
  }

  @Test
  void testCheckSipUriMatches1() throws IllegalArgumentException {
    SipUri sipUri = new SipUri();
    sipUri.setHost(new Host("Host Name"));
    Assert.assertFalse(ProxyUtils.checkSipUriMatches(sipUri, new SipUri()));
  }

  @Test
  void testCheckSipUriMatches2() throws IllegalArgumentException, ParseException {
    SipUri sipUri1 = new SipUri();
    SipUri sipUri2 = new SipUri();
    sipUri1.setHost("1.1.1.1");
    sipUri1.setPort(5060);
    sipUri1.setUser("webex");

    sipUri2.setHost("1.1.1.1");
    sipUri2.setPort(5060);
    sipUri2.setUser("Webex");

    Assert.assertTrue(ProxyUtils.checkSipUriMatches(sipUri1, sipUri2));
  }

  @Test
  void testCheckSipUriMatches3() throws IllegalArgumentException, ParseException {
    SipUri sipUri1 = new SipUri();
    SipUri sipUri2 = new SipUri();
    sipUri1.setHost("1.1.1.1");
    sipUri1.setPort(5060);
    sipUri1.setUser("webex");

    sipUri2.setHost("1.1.1.1");
    sipUri2.setPort(5080);
    sipUri2.setUser("Webex");

    Assert.assertFalse(ProxyUtils.checkSipUriMatches(sipUri1, sipUri2));
  }
}
