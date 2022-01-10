package com.cisco.dsb.common.sip.util;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.atLeast;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import gov.nist.core.Host;
import gov.nist.core.HostPort;
import gov.nist.javax.sip.header.Via;
import gov.nist.javax.sip.message.SIPRequest;
import gov.nist.javax.sip.stack.TLSMessageChannel;
import java.io.UnsupportedEncodingException;
import java.text.ParseException;
import javax.net.ssl.SSLPeerUnverifiedException;
import org.testng.annotations.Test;

public class SipUtilsTest {
  @Test
  void testGenerateBranchId() {

    SipUtils.generateBranchId(new SIPRequest(), true);
  }

  @Test
  void testGenerateBranchId2() {
    assertNull(SipUtils.generateBranchId(new SIPRequest(), false));
  }

  @Test
  void testToHexString() throws UnsupportedEncodingException {
    assertEquals("4141414141414141", SipUtils.toHexString("AAAAAAAA".getBytes("UTF-8")));
  }

  @Test
  void testGetSslSession() throws SSLPeerUnverifiedException {
    assertNull(SipUtils.getSslSession(null));
  }

  @Test
  void testGetSslSession2() throws SSLPeerUnverifiedException {
    TLSMessageChannel tlsMessageChannel = mock(TLSMessageChannel.class);
    when(tlsMessageChannel.getHandshakeCompletedListener()).thenReturn(null);
    when(tlsMessageChannel.getPeerHostPort()).thenReturn(new HostPort());
    when(tlsMessageChannel.getHostPort()).thenReturn(new HostPort());
    when(tlsMessageChannel.isSecure()).thenReturn(true);
    assertThrows(SSLPeerUnverifiedException.class, () -> SipUtils.getSslSession(tlsMessageChannel));
    verify(tlsMessageChannel).getHandshakeCompletedListener();
    verify(tlsMessageChannel).getHostPort();
    verify(tlsMessageChannel).getPeerHostPort();
    verify(tlsMessageChannel).isSecure();
  }

  @Test
  void testGetSslSession3() throws SSLPeerUnverifiedException {
    TLSMessageChannel tlsMessageChannel = mock(TLSMessageChannel.class);
    when(tlsMessageChannel.getHandshakeCompletedListener()).thenReturn(null);
    when(tlsMessageChannel.getPeerHostPort()).thenReturn(new HostPort());
    when(tlsMessageChannel.getHostPort()).thenReturn(new HostPort());
    when(tlsMessageChannel.isSecure()).thenReturn(false);
    assertNull(SipUtils.getSslSession(tlsMessageChannel));
    verify(tlsMessageChannel).isSecure();
  }

  @Test
  void testGetConnectionProtocol() throws SSLPeerUnverifiedException {
    assertNull(SipUtils.getConnectionProtocol(null));
  }

  @Test
  void testGetConnectionProtocol2() throws SSLPeerUnverifiedException {
    TLSMessageChannel tlsMessageChannel = mock(TLSMessageChannel.class);
    when(tlsMessageChannel.getHandshakeCompletedListener()).thenReturn(null);
    when(tlsMessageChannel.getPeerHostPort()).thenReturn(new HostPort());
    when(tlsMessageChannel.getHostPort()).thenReturn(new HostPort());
    when(tlsMessageChannel.isSecure()).thenReturn(true);
    assertThrows(
        SSLPeerUnverifiedException.class, () -> SipUtils.getConnectionProtocol(tlsMessageChannel));
    verify(tlsMessageChannel).getHandshakeCompletedListener();
    verify(tlsMessageChannel).getHostPort();
    verify(tlsMessageChannel).getPeerHostPort();
    verify(tlsMessageChannel, atLeast(1)).isSecure();
  }

  @Test
  void testGetConnectionProtocol3() throws SSLPeerUnverifiedException {
    TLSMessageChannel tlsMessageChannel = mock(TLSMessageChannel.class);
    when(tlsMessageChannel.getHandshakeCompletedListener()).thenReturn(null);
    when(tlsMessageChannel.getPeerHostPort()).thenReturn(new HostPort());
    when(tlsMessageChannel.getHostPort()).thenReturn(new HostPort());
    when(tlsMessageChannel.isSecure()).thenReturn(false);
    assertEquals("TCP", SipUtils.getConnectionProtocol(tlsMessageChannel));
    verify(tlsMessageChannel).isSecure();
  }

  @Test
  void testGetConnectionId() {
    assertNull(SipUtils.getConnectionId("foo", "foo", null));
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
    assertFalse(SipUtils.isInetAddress("42"));
    assertFalse(SipUtils.isInetAddress("foo"));
  }

  @Test
  void testIsHostIPAddr() {
    assertFalse(SipUtils.isHostIPAddr("localhost"));
    assertFalse(SipUtils.isHostIPAddr(null));
    assertFalse(SipUtils.isHostIPAddr("42"));
    assertFalse(SipUtils.isHostIPAddr("42localhost"));
  }

  @Test
  void testGetUserPortion() {
    assertNull(SipUtils.getUserPortion("Req Uri"));
    assertEquals("xxx", SipUtils.getUserPortion("sip:xxx@xxx"));
  }

  @Test
  void testGetHostPortion() {
    assertNull(SipUtils.getHostPortion("Req Uri"));
    assertEquals("xxx", SipUtils.getHostPortion("sip:xxx@xxx"));
  }

  @Test
  void testIsMidDialogRequest() {
    assertFalse(SipUtils.isMidDialogRequest(new SIPRequest()));
    assertFalse(SipUtils.isMidDialogRequest(null));
  }
}
