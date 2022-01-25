package com.cisco.dsb.common.sip.util;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.testng.Assert.*;

import gov.nist.core.Host;
import gov.nist.core.HostPort;
import gov.nist.javax.sip.header.Via;
import gov.nist.javax.sip.message.SIPRequest;
import gov.nist.javax.sip.stack.HandshakeCompletedListenerImpl;
import gov.nist.javax.sip.stack.NioTlsMessageChannel;
import gov.nist.javax.sip.stack.TLSMessageChannel;
import java.io.UnsupportedEncodingException;
import java.text.ParseException;
import javax.net.ssl.HandshakeCompletedEvent;
import javax.net.ssl.SSLPeerUnverifiedException;
import javax.net.ssl.SSLSession;
import javax.sip.address.URI;
import javax.sip.header.*;
import org.testng.annotations.Test;

public class SipUtilsTest {
  @Test
  void testGenerateBranchIdForStateful() {
    String branch = SipUtils.generateBranchId(new SIPRequest(), true);
    assertNotNull(branch);
    assertTrue(branch.startsWith(SipUtils.BRANCH_MAGIC_COOKIE));
  }

  @Test(
      description =
          "request has a topMostVia header with branch starting with magic cookie."
              + "Uses that branch value to generate new branchId")
  void testGenerateBranchIdForStateLessCase1() {
    SIPRequest request = mock(SIPRequest.class);
    ViaHeader topmostViaHeader = mock(ViaHeader.class);
    when(request.getHeader(ViaHeader.NAME)).thenReturn(topmostViaHeader);
    when(topmostViaHeader.getBranch()).thenReturn("z9hG4bK-1234");

    String branch = SipUtils.generateBranchId(request, false);
    assertNotNull(branch);
    assertFalse(
        branch.startsWith(
            SipUtils.BRANCH_MAGIC_COOKIE)); // branch cookie's hashcode is part of digest
  }

  @Test(
      description =
          "request has a topMostVia header with branch not starting with magic cookie."
              + "Uses topmost-via, to, from, callId, cseq & requUri to generate new branchId")
  void testGenerateBranchIdForStateLessCase2() throws ParseException {
    SIPRequest request = mock(SIPRequest.class);

    ViaHeader topmostViaHeader = new Via();
    topmostViaHeader.setBranch("123456789");
    topmostViaHeader.setHost("bob");
    topmostViaHeader.setTransport("tcp");

    when(request.getHeader(ViaHeader.NAME)).thenReturn(topmostViaHeader);
    when(request.getHeader(ToHeader.NAME)).thenReturn(mock(ToHeader.class));
    when(request.getHeader(FromHeader.NAME)).thenReturn(mock(FromHeader.class));
    when(request.getHeader(CallIdHeader.NAME)).thenReturn(mock(CallIdHeader.class));
    when(request.getHeader(CSeqHeader.NAME)).thenReturn(mock(CSeqHeader.class));
    when(request.getRequestURI()).thenReturn(mock(URI.class));

    String branch = SipUtils.generateBranchId(request, false);
    assertNotNull(branch);
    assertFalse(branch.startsWith(SipUtils.BRANCH_MAGIC_COOKIE));
  }

  @Test(description = "request has no topMostVia header. So, branchId will be null")
  void testGenerateBranchIdForStateLessCase3() {
    SIPRequest request = mock(SIPRequest.class);
    when(request.getHeader(ViaHeader.NAME)).thenReturn(null);

    String branch = SipUtils.generateBranchId(request, false);
    assertNull(branch);
  }

  @Test
  void testToHexString() throws UnsupportedEncodingException {
    assertEquals("4141414141414141", SipUtils.toHexString("AAAAAAAA".getBytes("UTF-8")));
  }

  @Test(
      description =
          "Null SSL session is returned if messageChannel=null (or) messageChannel=insecure")
  void testGetSslSessionReturnsNull() throws SSLPeerUnverifiedException {
    assertNull(SipUtils.getSslSession(null));

    TLSMessageChannel tlsMessageChannel = mock(TLSMessageChannel.class);
    assertNull(SipUtils.getSslSession(tlsMessageChannel));
    verify(tlsMessageChannel).isSecure();
  }

  @Test(
      description =
          "SSl session is tested using a TLSMessageChannel where handshake is not completed."
              + "So, the api throws a SSLPeerUnverifiedException")
  void testGetSslSessionWithNoHandshakeCompletion() {
    TLSMessageChannel tlsMessageChannel = mock(TLSMessageChannel.class);
    when(tlsMessageChannel.isSecure()).thenReturn(true);
    when(tlsMessageChannel.getHandshakeCompletedListener()).thenReturn(null);

    assertThrows(SSLPeerUnverifiedException.class, () -> SipUtils.getSslSession(tlsMessageChannel));
    verify(tlsMessageChannel).isSecure();
    verify(tlsMessageChannel).getHostPort();
    verify(tlsMessageChannel).getPeerHostPort();
    verify(tlsMessageChannel).getHandshakeCompletedListener();
  }

  @Test(
      description =
          "SSl session is tested using a TLSMessageChannel where handshake is completed (listener returned but handshake completed event is null)."
              + "So, the api throws a SSLPeerUnverifiedException")
  void testGetSslSessionWithCompletedHandshakeCase1() {
    TLSMessageChannel tlsMessageChannel = mock(TLSMessageChannel.class);
    HandshakeCompletedListenerImpl handshakeCompletedListener =
        mock(HandshakeCompletedListenerImpl.class);

    when(tlsMessageChannel.isSecure()).thenReturn(true);
    when(tlsMessageChannel.getHandshakeCompletedListener()).thenReturn(handshakeCompletedListener);
    when(handshakeCompletedListener.getHandshakeCompletedEvent()).thenReturn(null);

    assertThrows(SSLPeerUnverifiedException.class, () -> SipUtils.getSslSession(tlsMessageChannel));
    verify(handshakeCompletedListener).getHandshakeCompletedEvent();
  }

  @Test(
      description =
          "SSl session is tested using a TLSMessageChannel where handshake is completed (listener returned, handshake completed event is available)."
              + "So,api returns an ssl session."
              + "Also, test getConnectionProtocol() api when we have a session")
  void testGetSslSessionAndConnectionProtocolWithCompletedHandshakeCase2()
      throws SSLPeerUnverifiedException {
    TLSMessageChannel tlsMessageChannel = mock(TLSMessageChannel.class);
    HandshakeCompletedListenerImpl handshakeCompletedListener =
        mock(HandshakeCompletedListenerImpl.class);
    HandshakeCompletedEvent handshakeCompletedEvent = mock(HandshakeCompletedEvent.class);
    SSLSession sslSession = mock(SSLSession.class);

    when(tlsMessageChannel.isSecure()).thenReturn(true);
    when(tlsMessageChannel.getHandshakeCompletedListener()).thenReturn(handshakeCompletedListener);
    when(handshakeCompletedListener.getHandshakeCompletedEvent())
        .thenReturn(handshakeCompletedEvent);
    when(handshakeCompletedEvent.getSession()).thenReturn(sslSession);
    when(sslSession.getProtocol()).thenReturn("TLS");

    assertEquals(SipUtils.getConnectionProtocol(tlsMessageChannel), "TLS");
    verify(handshakeCompletedEvent).getSession();
    verify(sslSession).getProtocol();
  }

  @Test(
      description =
          "SSl session is tested using a NioTlsMessageChannel"
              + "No handShakeCompletedEvent and SslSession available for this - only throws exception")
  void testGetSslSessionUsingTlsNioChannel() {
    NioTlsMessageChannel nioTlsMessageChannel = mock(NioTlsMessageChannel.class);
    HandshakeCompletedListenerImpl handshakeCompletedListener =
        mock(HandshakeCompletedListenerImpl.class);

    when(nioTlsMessageChannel.isSecure()).thenReturn(true);
    when(nioTlsMessageChannel.getHandshakeCompletedListener())
        .thenReturn(handshakeCompletedListener);

    assertThrows(
        SSLPeerUnverifiedException.class, () -> SipUtils.getSslSession(nioTlsMessageChannel));
    verify(nioTlsMessageChannel).isSecure();
    verify(nioTlsMessageChannel).getHostPort();
    verify(nioTlsMessageChannel).getPeerHostPort();
    verify(nioTlsMessageChannel).getHandshakeCompletedListener();
  }

  @Test(description = "no protocol returned for a null messageChannel")
  void testGetConnectionProtocol1() throws SSLPeerUnverifiedException {
    assertNull(SipUtils.getConnectionProtocol(null));
  }

  @Test(description = "TCP protocol returned for an insecure messageChannel")
  void testGetConnectionProtocol2() throws SSLPeerUnverifiedException {
    TLSMessageChannel tlsMessageChannel = mock(TLSMessageChannel.class);
    when(tlsMessageChannel.isSecure()).thenReturn(false);

    assertEquals("TCP", SipUtils.getConnectionProtocol(tlsMessageChannel));
  }

  @Test
  void testGetConnectionId() {
    assertNull(SipUtils.getConnectionId("foo", "foo", null));

    TLSMessageChannel tlsMessageChannel = mock(TLSMessageChannel.class);
    HostPort hostPort = new HostPort();
    hostPort.setHost(new Host("host"));
    HostPort peerHostPort = new HostPort();
    peerHostPort.setHost(new Host("peerHost"));

    when(tlsMessageChannel.getHostPort()).thenReturn(hostPort);
    when(tlsMessageChannel.getPeerHostPort()).thenReturn(peerHostPort);

    String expectedConnectionId = "foo-foo-host-peerHost";
    assertEquals(SipUtils.getConnectionId("foo", "foo", tlsMessageChannel), expectedConnectionId);
  }

  @Test
  void testGetViaHeaderSentByAddress() {
    assertNull(SipUtils.getViaHeaderSentByAddress(new Via()));
  }

  @Test
  void testGetViaHeaderSentByAddress2() {
    Via via = new Via();
    via.setHost((Host) null);
    assertNull(SipUtils.getViaHeaderSentByAddress(via));
  }

  @Test
  void testGetViaHeaderSentByAddress3() throws ParseException {
    Via via = new Via();
    via.setHost("localhost");
    assertEquals("localhost", SipUtils.getViaHeaderSentByAddress(via));
  }

  @Test
  void testGetViaHeaderReceivedAddress() {
    assertNull(SipUtils.getViaHeaderReceivedAddress(new Via()));
  }

  @Test
  void testIsInetAddress() {
    assertFalse(SipUtils.isInetAddress("foo"));
  }

  @Test
  void testIsMidDialogRequest() {
    assertFalse(SipUtils.isMidDialogRequest(new SIPRequest()));
    assertFalse(SipUtils.isMidDialogRequest(null));

    SIPRequest request = mock(SIPRequest.class);
    when(request.getToTag()).thenReturn("totag");
    assertTrue(SipUtils.isMidDialogRequest(request));
  }
}
