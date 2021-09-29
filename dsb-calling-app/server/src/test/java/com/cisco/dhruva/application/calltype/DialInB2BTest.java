package com.cisco.dhruva.application.calltype;

import static org.mockito.Mockito.*;

import com.cisco.dhruva.application.SIPConfig;
import com.cisco.dhruva.application.constants.SipParamConstants;
import com.cisco.dhruva.normalisation.RuleListenerImpl;
import com.cisco.dhruva.normalisation.rules.RemoveOpnDpnCallTypeRule;
import com.cisco.dsb.common.exception.DhruvaException;
import com.cisco.dsb.common.sip.bean.SIPListenPoint;
import com.cisco.dsb.common.sip.stack.dto.DhruvaNetwork;
import com.cisco.dsb.proxy.messaging.ProxySIPRequest;
import com.cisco.dsb.proxy.messaging.ProxySIPResponse;
import com.cisco.dsb.proxy.sip.ProxyCookieImpl;
import com.cisco.dsb.trunk.dto.Destination;
import gov.nist.javax.sip.address.AddressImpl;
import gov.nist.javax.sip.address.SipUri;
import gov.nist.javax.sip.header.Contact;
import gov.nist.javax.sip.header.ContactList;
import gov.nist.javax.sip.header.To;
import gov.nist.javax.sip.message.SIPRequest;
import gov.nist.javax.sip.message.SIPResponse;
import java.text.ParseException;
import java.util.TreeSet;
import javax.sip.InvalidArgumentException;
import javax.sip.message.Request;
import javax.sip.message.Response;
import org.junit.Assert;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.BeforeTest;
import org.testng.annotations.DataProvider;
import org.testng.annotations.Test;
import reactor.core.publisher.Mono;

public class DialInB2BTest {
  @Mock ProxySIPRequest proxySIPRequest;
  @Mock SIPRequest sipRequest;
  @Mock ProxySIPRequest clonedProxySIPRequest;
  @Mock SIPRequest clonedRequest;
  @Mock SIPListenPoint sipListenPoint;
  @Mock ProxySIPResponse proxySIPResponse;
  @Mock SIPResponse sipResponse;
  @Mock ProxyCookieImpl cookie;
  Object normRule;

  @BeforeTest
  public void init() throws DhruvaException {
    MockitoAnnotations.initMocks(this);
    when(sipListenPoint.getName()).thenReturn(SIPConfig.NETWORK_CALLING_CORE);
    DhruvaNetwork.createNetwork(SIPConfig.NETWORK_CALLING_CORE, sipListenPoint);
    normRule = new RemoveOpnDpnCallTypeRule();
  }

  @BeforeMethod
  public void setup() {
    reset(
        proxySIPRequest,
        clonedProxySIPRequest,
        clonedRequest,
        sipRequest,
        proxySIPResponse,
        sipResponse,
        cookie);
    when(proxySIPRequest.getRequest()).thenReturn(sipRequest);
    when(proxySIPRequest.clone()).thenReturn(clonedProxySIPRequest);
    when(proxySIPResponse.getResponse()).thenReturn(sipResponse);
    when(clonedProxySIPRequest.getRequest()).thenReturn(clonedRequest);
    when(proxySIPRequest.getCookie()).thenReturn(cookie);
    when(clonedProxySIPRequest.getCookie()).thenReturn(cookie);
    when(proxySIPResponse.getCookie()).thenReturn(cookie);
  }

  @DataProvider
  public Object[] testTag() {
    return new Object[] {false, true};
  }

  @Test(
      description =
          "request has OPN,DPN,calltype, remove these params"
              + "'RemoveOpnDpnCallTypeRule' rule condition and action succeeds",
      dataProvider = "testTag")
  public void testProcessRequest(boolean testCall) throws ParseException {
    DialInB2B dialInB2B = new DialInB2B(new RuleListenerImpl(), normRule);
    when(sipRequest.getMethod()).thenReturn(Request.INVITE);
    SipUri sipUri = new SipUri();
    sipUri.setParameter(SipParamConstants.X_CISCO_DPN, SipParamConstants.X_CISCO_DPN_VALUE);
    sipUri.setParameter(SipParamConstants.X_CISCO_OPN, SipParamConstants.X_CISCO_OPN_VALUE);
    sipUri.setParameter(SipParamConstants.CALLTYPE, SipParamConstants.DIAL_IN_TAG);
    if (testCall) sipUri.setParameter(SipParamConstants.TEST_CALL, null);
    when(sipRequest.getRequestURI()).thenReturn(sipUri);
    when(clonedRequest.getRequestURI()).thenReturn(sipUri);
    doAnswer(
            invocationOnMock -> {
              Destination destination = invocationOnMock.getArgument(0);
              when(clonedProxySIPRequest.getDestination()).thenReturn(destination);
              return null;
            })
        .when(clonedProxySIPRequest)
        .setDestination(any(Destination.class));

    // call
    dialInB2B.processRequest().accept(Mono.just(proxySIPRequest));

    // verify
    Assert.assertFalse(
        sipUri.hasParameter(SipParamConstants.X_CISCO_DPN)
            || sipUri.hasParameter(SipParamConstants.X_CISCO_OPN)
            || sipUri.hasParameter(SipParamConstants.CALLTYPE));
    Destination destination = clonedProxySIPRequest.getDestination();
    Assert.assertEquals(destination.getDestinationType(), Destination.DestinationType.A);
    Assert.assertEquals(destination.getUri(), proxySIPRequest.getRequest().getRequestURI());
    if (testCall)
      Assert.assertEquals(
          destination.getAddress(),
          String.join(":", SIPConfig.NS_A_RECORD) + ":" + SipParamConstants.INJECTED_DNS_UUID);
    else Assert.assertEquals(destination.getAddress(), String.join(":", SIPConfig.NS_A_RECORD));
    verify(clonedProxySIPRequest, times(1)).proxy();
    verify(proxySIPRequest, times(0)).proxy();
    verify(cookie, times(1)).setCalltype(dialInB2B);
    verify(cookie, times(1)).setRequestTo(DialInB2B.TO_NS);
  }

  @Test(
      description =
          "Do not remove opn,dpn,calltype params for a non-INVITE request."
              + "'RemoveOpnDpnCallTypeRule' rule condition fails")
  public void testNonInvite() throws ParseException {
    DialInB2B dialInB2B = new DialInB2B(new RuleListenerImpl(), normRule);
    SipUri sipUri = new SipUri();
    sipUri.setParameter(SipParamConstants.X_CISCO_DPN, SipParamConstants.X_CISCO_DPN_VALUE);
    sipUri.setParameter(SipParamConstants.X_CISCO_OPN, SipParamConstants.X_CISCO_OPN_VALUE);
    sipUri.setParameter(SipParamConstants.CALLTYPE, SipParamConstants.DIAL_IN_TAG);

    To toHeader = new To();
    when(clonedRequest.getTo()).thenReturn(toHeader);
    when(sipRequest.getRequestURI()).thenReturn(sipUri);
    when(clonedRequest.getRequestURI()).thenReturn(sipUri);
    when(sipRequest.getMethod()).thenReturn(Request.ACK);
    doAnswer(
            invocationOnMock -> {
              Destination destination = invocationOnMock.getArgument(0);
              when(clonedProxySIPRequest.getDestination()).thenReturn(destination);
              return null;
            })
        .when(clonedProxySIPRequest)
        .setDestination(any(Destination.class));

    // call
    dialInB2B.processRequest().accept(Mono.just(proxySIPRequest));

    // verify
    Assert.assertTrue(
        sipUri.hasParameter(SipParamConstants.X_CISCO_DPN)
            && sipUri.hasParameter(SipParamConstants.X_CISCO_OPN)
            && sipUri.hasParameter(SipParamConstants.CALLTYPE));
    verify(clonedProxySIPRequest, times(1)).proxy();
    verify(cookie, times(1)).setRequestTo(DialInB2B.TO_NS);
  }

  @Test
  public void testRequestException() throws ParseException, DhruvaException {
    DialInB2B dialInB2B = new DialInB2B(new RuleListenerImpl(), normRule);
    SipUri sipUri = new SipUri();
    sipUri.setParameter(SipParamConstants.X_CISCO_DPN, SipParamConstants.X_CISCO_DPN_VALUE);
    sipUri.setParameter(SipParamConstants.X_CISCO_OPN, SipParamConstants.X_CISCO_OPN_VALUE);
    sipUri.setParameter(SipParamConstants.CALLTYPE, SipParamConstants.DIAL_IN_TAG);
    sipUri.setParameter(SipParamConstants.TEST_CALL, null);
    DhruvaNetwork.removeNetwork(SIPConfig.NETWORK_CALLING_CORE);
    when(sipRequest.getRequestURI()).thenReturn(sipUri);
    when(clonedRequest.getRequestURI()).thenReturn(sipUri);
    when(sipRequest.getMethod()).thenReturn(Request.INVITE);

    // call
    dialInB2B.processRequest().accept(Mono.just(proxySIPRequest));
    DhruvaNetwork.createNetwork(SIPConfig.NETWORK_CALLING_CORE, sipListenPoint);

    // verify
    Assert.assertFalse(
        sipUri.hasParameter(SipParamConstants.X_CISCO_DPN)
            || sipUri.hasParameter(SipParamConstants.X_CISCO_OPN)
            || sipUri.hasParameter(SipParamConstants.CALLTYPE));
    verify(proxySIPRequest, times(1)).reject(Response.SERVER_INTERNAL_ERROR);
  }

  @DataProvider
  public Object[][] dnsInjection() {
    return new Object[][] {{true, true}, {false, true}, {false, false}};
  }

  @Test(description = "302 from NS", dataProvider = "dnsInjection")
  public void test302NS(Boolean dnsInjection, Boolean contact)
      throws InvalidArgumentException, ParseException {
    DialInB2B dialInB2B = new DialInB2B(new RuleListenerImpl(), normRule);
    ContactList contacts = new ContactList();
    Float[] qValues = {1.0f, 0.7f, 0.3f, 1.0f};
    AddressImpl[] addresses = new AddressImpl[4];
    SipUri rUri = new SipUri();
    rUri.setHost("test.cisco.com");
    rUri.setUser("dhruva");
    if (dnsInjection) rUri.setParameter(SipParamConstants.TEST_CALL, null);
    for (int i = 0; i < 4; i++) {
      Contact contact1 = new Contact();
      SipUri sipUri = new SipUri();
      sipUri.setHost("test" + i + ".as.com");
      if (i != 2) sipUri.setPort(5060 + i);
      addresses[i] = new AddressImpl();
      addresses[i].setURI(sipUri);
      contact1.setAddress(addresses[i]);
      contact1.setQValue(qValues[i]);
      contacts.add(contact1);
    }
    when(sipResponse.getStatusCode()).thenReturn(302);
    if (contact) when(sipResponse.getContactHeaders()).thenReturn(contacts);
    dialInB2B.setO_proxySipRequest(proxySIPRequest);
    when(sipRequest.getRequestURI()).thenReturn(rUri);
    when(clonedRequest.getRequestURI()).thenReturn(rUri);
    doAnswer(
            invocationOnMock -> {
              Destination destination = invocationOnMock.getArgument(0);
              when(clonedProxySIPRequest.getDestination()).thenReturn(destination);
              return null;
            })
        .when(clonedProxySIPRequest)
        .setDestination(any(Destination.class));
    when(cookie.getRequestTo()).thenReturn(DialInB2B.TO_NS);
    doAnswer(
            invocationOnMock -> {
              String requestTo = invocationOnMock.getArgument(0);
              when(cookie.getRequestTo()).thenReturn(requestTo);
              assert requestTo.equals(DialInB2B.TO_AS);
              return null;
            })
        .when(cookie)
        .setCalltype(anyString());
    // call
    dialInB2B.processResponse().accept(Mono.just(proxySIPResponse));

    // verify
    /*
    1. sortedContacts
    2. update original proxySipRequests with clone of sipRequest
    3. without DNS injection
    4. verify destination is set to proper AS
    5. proxy the request
    6. requestTo of cookie is changed to AS
     */
    if (contact) {
      TreeSet<Contact> sortedContacts = dialInB2B.getSortedContacts();
      Assert.assertEquals(
          "test3.as.com", ((SipUri) sortedContacts.pollLast().getAddress().getURI()).getHost());
      Assert.assertEquals(
          "test1.as.com", ((SipUri) sortedContacts.pollLast().getAddress().getURI()).getHost());
      Assert.assertEquals(
          "test2.as.com", ((SipUri) sortedContacts.pollLast().getAddress().getURI()).getHost());
      Destination destination = clonedProxySIPRequest.getDestination();
      Assert.assertEquals(
          destination.getNetwork(), DhruvaNetwork.getNetwork(SIPConfig.NETWORK_CALLING_CORE).get());
      Assert.assertEquals(destination.getUri(), rUri);
      if (dnsInjection)
        Assert.assertEquals(
            "test0.as.com:5060:" + SipParamConstants.INJECTED_DNS_UUID, destination.getAddress());
      else Assert.assertEquals("test0.as.com:5060", destination.getAddress());
      Assert.assertEquals(destination.getDestinationType(), Destination.DestinationType.A);
      verify(clonedProxySIPRequest, times(1)).proxy();
    } else {
      verify(cookie, times(0)).setCalltype(dialInB2B);
      verify(proxySIPRequest, times(1)).reject(Response.NOT_FOUND);
    }
  }

  @Test(description = "non 302 from NS, proxy the response as it is")
  public void nonRedirectFromNS() {
    DialInB2B dialInB2B = new DialInB2B(new RuleListenerImpl(), normRule);
    when(proxySIPResponse.getResponseClass()).thenReturn(4);
    when(sipResponse.getStatusCode()).thenReturn(Response.NOT_FOUND);
    when(cookie.getRequestTo()).thenReturn(DialInB2B.TO_NS);
    dialInB2B.processResponse().accept(Mono.just(proxySIPResponse));

    // verify
    verify(proxySIPResponse, times(1)).proxy();
    verifyNoInteractions(clonedProxySIPRequest, clonedRequest);
  }

  @Test(description = "Response from AS,200Ok")
  public void testResponseAS() {
    DialInB2B dialInB2B = new DialInB2B(new RuleListenerImpl(), normRule);
    TreeSet<Contact> contacts = mock(TreeSet.class);
    Contact contact = new Contact();
    AddressImpl address = new AddressImpl();
    address.setURI(new SipUri());
    dialInB2B.setSortedContacts(contacts);
    when(contacts.pollLast()).thenReturn(contact);
    when(proxySIPResponse.getResponseClass()).thenReturn(2);
    when(cookie.getRequestTo()).thenReturn(DialInB2B.TO_AS);
    dialInB2B.processResponse().accept(Mono.just(proxySIPResponse));

    verify(proxySIPResponse, times(1)).proxy();
    verifyNoInteractions(proxySIPRequest, clonedProxySIPRequest);
  }

  @Test(description = "1st response 404, try next, receive 200ok")
  public void testTryNextInContact() throws ParseException {
    DialInB2B dialInB2B = new DialInB2B(new RuleListenerImpl(), normRule);
    TreeSet<Contact> contacts = mock(TreeSet.class);
    Contact contact = new Contact();
    AddressImpl address = new AddressImpl();
    SipUri contactUri = new SipUri();
    SipUri rUri = new SipUri();
    contactUri.setHost("test2.as.com");
    contactUri.setPort(5061);
    rUri.setUser("akgowda");
    rUri.setHost("test.cisco.com");
    address.setURI(contactUri);
    contact.setAddress(address);
    dialInB2B.setSortedContacts(contacts);
    when(contacts.pollLast()).thenReturn(contact);
    when(proxySIPResponse.getResponseClass()).thenReturn(4);
    when(clonedRequest.getRequestURI()).thenReturn(rUri);
    dialInB2B.setO_proxySipRequest(proxySIPRequest);
    when(cookie.getRequestTo()).thenReturn(DialInB2B.TO_AS);
    dialInB2B.processResponse().accept(Mono.just(proxySIPResponse));

    verify(clonedProxySIPRequest, times(1)).proxy();

    ProxySIPResponse proxySIPResponse_200 = mock(ProxySIPResponse.class);
    when(proxySIPResponse_200.getResponseClass()).thenReturn(2);
    when(proxySIPResponse_200.getCookie()).thenReturn(cookie);
    dialInB2B.processResponse().accept(Mono.just(proxySIPResponse_200));
    verify(proxySIPResponse_200, times(1)).proxy();
    verify(cookie, times(1)).setRequestTo(DialInB2B.TO_AS);
  }

  @Test(
      description =
          "RequestTo not set in cookie, reject the call as we don't know if response is from NS or AS")
  public void testProcessException() throws DhruvaException {
    DialInB2B dialInB2B = new DialInB2B(new RuleListenerImpl(), normRule);
    TreeSet<Contact> contacts = mock(TreeSet.class);
    Contact contact = new Contact();
    AddressImpl address = new AddressImpl();
    address.setURI(new SipUri());
    when(contacts.pollLast()).thenReturn(contact);
    dialInB2B.setSortedContacts(contacts);
    dialInB2B.setO_proxySipRequest(proxySIPRequest);
    when(sipRequest.getRequestURI()).thenReturn(mock(SipUri.class));
    DhruvaNetwork.removeNetwork(SIPConfig.NETWORK_CALLING_CORE);
    contact.setAddress(address);
    dialInB2B.processResponse().accept(Mono.just(proxySIPResponse));

    DhruvaNetwork.createNetwork(SIPConfig.NETWORK_CALLING_CORE, sipListenPoint);
    verify(proxySIPRequest, times(1)).reject(Response.SERVER_INTERNAL_ERROR);
  }
}
