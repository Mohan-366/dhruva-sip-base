package com.cisco.dsb.sip.util;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import gov.nist.javax.sip.header.SIPHeader;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import org.testng.Assert;
import org.testng.annotations.DataProvider;
import org.testng.annotations.Test;

public class PeerCertInfoUtilsTest {
  @DataProvider
  public Object[][] headerValuesData() {
    return new Object[][] {
      // for cn, the entire value should be returned
      {"<cn=acme.com>", "acme.com"},
      // for valid sanrfc822name, all text after the last `@` has to be returned
      {"<sanrfc822name=@gmail.uw.edu:user@cs.washington.edu>", "cs.washington.edu"},
      // for invalid sanrfc822name, nothing should be added to the list
      {"<sanrfc822name=@gmail.uw.edu:user@>", null},
      // for sanregisteredid, nothing should be added to the list
      {"<sanregisteredid=myregisteredId>", null},
      // for sanipaddress, nothing should be added to the list
      {"<sanipaddress=someIPAddress>", null},
      // for sanuniformresourceidentifier, the domain has to be returned
      {"<sanuniformresourceidentifier=http://user.acme.com>", "user.acme.com"},
      // for invalid sanuniformresourceidentifier, nothing should be added to the list
      {"<sanuniformresourceidentifier=user acme com>", null},
      // for valid sandnsname, the entire value should be added to the list
      {"<sandnsname=acme.com>", "acme.com"},
      // for invalid sandnsname, nothing should be added to the list
      {"<sandnsname=>", null},
      // for unknown types, nothing should be added to the list.
      {"<unknown=somevalue>", null},
      {"", null},
      {null, null}
    };
  }

  @Test(dataProvider = "headerValuesData")
  public void testGetPeerCertInfo(String headerValue, String expectedSanEntry) {
    List<SIPHeader> sipHeaderList = new ArrayList<>();
    sipHeaderList.add(mockPeerCertInfoHeader(headerValue));

    List<String> parsedSans = PeerCertInfoUtils.getPeerCertInfo(sipHeaderList);

    if (expectedSanEntry == null) {
      Assert.assertTrue(parsedSans.isEmpty());
    } else {
      Assert.assertTrue(parsedSans.contains(expectedSanEntry));
    }
  }

  private SIPHeader mockPeerCertInfoHeader(String value) {
    SIPHeader sipHeader = mock(SIPHeader.class);
    when(sipHeader.getHeaderName()).thenReturn(SipConstants.X_Cisco_Peer_Cert_Info);
    when(sipHeader.getHeaderValue()).thenReturn(value);
    return sipHeader;
  }

  @DataProvider
  public Object[][] multiValueHeaderData() {
    return new Object[][] {
      {
        "<cn=acme.com>, <sanrfc822name=@gmail.uw.edu:user@cs.washington.edu>, <sanrfc822name=@gmail.uw.edu:user@>,"
            + "<sanregisteredid=myregisteredId>, <sanipaddress=someIPAddress>, <sanuniformresourceidentifier=http://user.acme.com>,"
            + "<sanuniformresourceidentifier=user acme com>, <unknown=somevalue>",
        "acme.com,cs.washington.edu,user.acme.com"
      }
    };
  }

  @Test(dataProvider = "multiValueHeaderData")
  public void testMultipleEntryPeerCertInfo(String headerValue, String expectedSanEntry) {
    List<SIPHeader> sipHeaderList = new ArrayList<>();
    SIPHeader sipHeader = mockPeerCertInfoHeader(headerValue);
    sipHeaderList.add(sipHeader);

    List<String> parsedSans = PeerCertInfoUtils.getPeerCertInfo(sipHeaderList);

    // Verify that multiple SANs from a single SIPHeader were added to the org domain list.
    String[] expectedValues = expectedSanEntry.split(",");
    Assert.assertEquals(expectedValues.length, parsedSans.size());
    for (String expectedValue : expectedValues) {
      Assert.assertTrue(parsedSans.contains(expectedValue));
    }
  }

  @DataProvider
  public Object[][] multipleHeadersData() {
    return new Object[][] {
      {
        "<cn=acme.com>",
        "<sanrfc822name=@gmail.uw.edu:user@cs.washington.edu>",
        "acme.com,cs.washington.edu"
      },
      {"<cn=acme.com>", null, "acme.com"},
      {null, "<sanrfc822name=@gmail.uw.edu:user@cs.washington.edu>", "cs.washington.edu"}
    };
  }

  @Test(dataProvider = "multipleHeadersData")
  public void testMultipleHeadersData(
      String headerValue1, String headerValue2, String expectedSanEntry) {
    List<SIPHeader> sipHeaderList = new ArrayList<>();
    SIPHeader sipHeader1 = mockPeerCertInfoHeader(headerValue1);
    SIPHeader sipHeader2 = mockPeerCertInfoHeader(headerValue2);
    sipHeaderList.add(sipHeader1);
    sipHeaderList.add(sipHeader2);

    List<String> parsedSans = PeerCertInfoUtils.getPeerCertInfo(sipHeaderList);

    // Verify that SANs from multiple SIPHeaders were added to the org domain list.
    String[] expectedValues = expectedSanEntry.split(",");
    Assert.assertEquals(expectedValues.length, parsedSans.size());
    for (String expectedValue : expectedValues) {
      Assert.assertTrue(parsedSans.contains(expectedValue));
    }
  }

  @Test
  public void testDomainLimit() {
    int maxDomains = 100;
    String largeSanList1 = "";
    String largeSanList2 = "";
    String largeSanList3 = "";

    for (int i = 0; i < 60; i++) {
      largeSanList1 += "<cn=someSan" + i + ".com>,";
    }
    for (int i = 60; i < 120; i++) {
      largeSanList2 += "<cn=someSan" + i + ".com>,";
    }
    for (int i = 120; i <= 180; i++) {
      largeSanList3 += "<cn=someSan" + i + ".com>,";
    }

    List<SIPHeader> sipHeaderList =
        Arrays.asList(
            mockPeerCertInfoHeader(largeSanList1),
            mockPeerCertInfoHeader(largeSanList2),
            mockPeerCertInfoHeader(largeSanList3));
    List<String> parsedSans = PeerCertInfoUtils.getPeerCertInfo(sipHeaderList, maxDomains);
    Assert.assertEquals(maxDomains, parsedSans.size());
  }
}
