package com.cisco.dsb.common.sip.tls;

import io.netty.handler.ssl.util.LazyX509Certificate;
import java.io.UnsupportedEncodingException;
import java.util.ArrayList;
import nl.jqno.equalsverifier.EqualsVerifier;
import org.testng.Assert;
import org.testng.annotations.Test;

public class HostNameValidationHelperTest {
  @Test
  void testMatch() throws UnsupportedEncodingException {
    ArrayList<String> hostnames = new ArrayList<>();
    Assert.assertFalse(
        HostNameValidationHelper.match(
            hostnames, new LazyX509Certificate("AAAAAAAA".getBytes("UTF-8"))));
  }

  @Test
  void testMatch2() {
    Assert.assertFalse(HostNameValidationHelper.match(null, null));
  }

  @Test
  void testMatch3() throws UnsupportedEncodingException {
    ArrayList<String> stringList = new ArrayList<>();
    stringList.add(null);
    Assert.assertFalse(
        HostNameValidationHelper.match(
            stringList, new LazyX509Certificate("AAAAAAAA".getBytes("UTF-8"))));
  }

  @Test
  void testMatch4() {
    ArrayList<String> stringList = new ArrayList<>();
    stringList.add("foo");
    Assert.assertFalse(HostNameValidationHelper.match(stringList, null));
  }

  @Test
  void testMatch5() {
    ArrayList<String> stringList = new ArrayList<>();
    stringList.add(null);
    Assert.assertFalse(
        HostNameValidationHelper.match(
            stringList,
            new LazyX509Certificate(new byte[] {0, 'A', 'A', 'A', 'A', 'A', 'A', 'A'})));
  }

  @Test
  void testToString() throws UnsupportedEncodingException {
    Assert.assertEquals(
        "Subject: null, Common Names and SANs: null",
        HostNameValidationHelper.toString(new LazyX509Certificate("AAAAAAAA".getBytes("UTF-8"))));
    Assert.assertEquals(
        "Subject: null, Common Names and SANs: null", HostNameValidationHelper.toString(null));
  }

  @Test
  public void testEqualsAndHashCodeOfCertificateInfo() {
    EqualsVerifier.simple().forClass(CertificateInfo.class).verify();
  }
}
