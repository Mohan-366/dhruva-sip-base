package com.cisco.dsb.sip.jain;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.cisco.dsb.sip.parser.RemotePartyIDParser;
import com.cisco.dsb.sip.util.SipConstants;
import java.text.ParseException;
import javax.sip.SipFactory;
import javax.sip.address.SipURI;
import javax.sip.header.ContactHeader;
import org.testng.Assert;
import org.testng.annotations.DataProvider;
import org.testng.annotations.Test;

/**
 * TODO: all these testcases are poorted from 'JainSipUtilsTest.java' in l2sip. This is just a
 * subset. The assertions of each of these needs to be validated
 */
public class JainSipHelperTest {

  private static SipFactory sipFactory;

  static {
    sipFactory = SipFactory.getInstance();
    sipFactory.setPathName("gov.nist");
    RemotePartyIDParser.init();
  }

  @Test(description = "fetch parameters from a header (here: contact header is used)")
  public void testGetParameters() {
    ContactHeader contactHeader = mock(ContactHeader.class);
    when(contactHeader.toString())
        .thenReturn("sip:l2sipit-guest-6790308@192.168.1.77:5061;transport=tls");
    Assert.assertNull(
        JainSipHelper.getParameters(contactHeader), "Parameter should be returned as null");

    when(contactHeader.toString())
        .thenReturn("<sip:l2sipit-guest-6790308@192.168.1.77:5061;transport=tls;lr>");
    Assert.assertNull(
        JainSipHelper.getParameters(contactHeader), "Parameter should be returned as null");

    when(contactHeader.toString())
        .thenReturn("<sip:l2sipit-guest-6790308@192.168.1.77:5061;transport=tls;lr>;");
    Assert.assertEquals(
        JainSipHelper.getParameters(contactHeader).length(),
        0,
        "Parameters length should be returned as 0.");

    when(contactHeader.toString())
        .thenReturn("<sip:l2sipit-guest-6790308@192.168.1.77:5061;transport=tls;lr>;isfocus");
    Assert.assertEquals(
        JainSipHelper.getParameters(contactHeader),
        SipConstants.ISFOCUS_PARAMETER,
        "parameter isfocus should be returned.");
  }

  @Test(description = "checks creation of sanitized sip uri")
  public void testCreateSanitizedSipURI() {
    // typical input
    validateSipURI("sip:alice@example.com", "sip:alice@example.com");

    // Secure sips: scheme is sanitized to sip:
    validateSipURI("sips:alice@example.com", "sip:alice@example.com");

    // Case is ignored and sanitized in scheme
    validateSipURI("SIP:alice@example.com", "sip:alice@example.com");

    // Port, password, and parameters are ignored
    validateSipURI("sip:alice:password@example.com:5060;transport=tcp", "sip:alice@example.com");

    // Case is preserved in user name
    validateSipURI("ALICE@example.com", "sip:ALICE@example.com");

    // Whitespace is ignored and removed
    validateSipURI(" alice@example.com ", "sip:alice@example.com");

    // Domains are handled
    validateSipURI("example.com", "sip:example.com");

    // Uncommon, but allowable characters are handled
    /* validateSipURI(SipConstants.RFC3261.SIP_URI_USER_ALL + "@example.com",
    "sip:" + SipConstants.RFC3261.SIP_URI_USER_ALL + "@example.com");*/

    // Invalid URI is null
    validateSipURI(null, null);
    validateSipURI("", null);

    // ^ is disallowed in username by SIP RFC 3261
    /*validateSipURI(SipConstants.RFC3261.SIP_URI_USER_DISALLOWED + "@example.com", null);*/
  }

  private void validateSipURI(String sipUriIn, String expectedURI) {
    SipURI sipURI = null;
    try {
      sipURI = JainSipHelper.createSanitizedSipUri(sipUriIn);
    } catch (ParseException | IllegalArgumentException e) {
      // Do nothing
    }

    if (expectedURI == null) {
      Assert.assertNull(sipURI);
    } else {
      Assert.assertNotNull(sipURI);
      Assert.assertEquals(sipURI.toString(), expectedURI);
    }
  }

  @DataProvider
  private Object[][] createSipUriWithoutSchemeData() {
    return new Object[][] {
      // Positive test cases
      {"sip:user@example.com", "user@example.com"},
      {"sips:user@example.com", "user@example.com"},
      {"sip:user@example.com:5061", "user@example.com"},
      {"sip:user@example.com:5061;pname1=pvalue1;pname2=pvalue2", "user@example.com"},
      {"sip:user@example.com:5061?hname1=hvalue1&hname2=hvalue2", "user@example.com"},
      {
        "sip:user@example.com:5061;pname1=pvalue1;pname2=pvalue2?hname1=hvalue1&hname2=hvalue2",
        "user@example.com"
      },
      {"sip:USER@EXAMPLE.COM", "USER@EXAMPLE.COM"},

      // Degenerate cases
      {"user@example.com", "user@example.com"},
      {"example.com", "example.com"},

      // Negative test cases
      {"https://www.example.com", null},

      // Null/empty cases
      {"", null},
      {null, null}
    };
  }

  @Test(
      dataProvider = "createSipUriWithoutSchemeData",
      description = "checks creation of sip uri without scheme data")
  public void testCreateSipUriWithoutScheme(String sipUri, String expectedSipUri) {
    String userAtHostSipUri = null;
    try {
      userAtHostSipUri = JainSipHelper.getUserAtHost(JainSipHelper.createSanitizedSipUri(sipUri));
      Assert.assertNotNull(userAtHostSipUri);
    } catch (Exception e) {
      // Do nothing
    }
    Assert.assertEquals(userAtHostSipUri, expectedSipUri);
  }

  @Test(
      description =
          "Tests to validate if getDomain util does not throw any exception and return values as expected.")
  public void testGetDomain() throws ParseException {
    // 1. Input - null , expected outcome: null.
    Assert.assertNull(JainSipHelper.getDomain(null));

    // 2. Input - Tel URI, expected outcome: null.
    Assert.assertNull(
        JainSipHelper.getDomain(JainSipHelper.getAddressFactory().createTelURL("123456")));

    // 3. Input - SIP URI, expected outcome: host part.
    Assert.assertEquals(
        "host",
        JainSipHelper.getDomain(JainSipHelper.getAddressFactory().createSipURI("user", "host")));
  }

  @Test(description = "sip scheme addition test")
  public void addSipSchemeIfNeededTest() {
    // Positive test cases where scheme is added
    // basic test case
    testAddSipSchemeIfNeededHelper("alice@example.com", "sip:alice@example.com");

    // host only test case
    testAddSipSchemeIfNeededHelper("example.com", "sip:example.com");

    // test case doesn't change in user info part
    testAddSipSchemeIfNeededHelper("ALICE@example.com", "sip:ALICE@example.com");

    // test white space is ignored
    testAddSipSchemeIfNeededHelper(" alice@example.com ", "sip:alice@example.com");

    // Positive test cases where scheme already exists
    // basic test case
    testAddSipSchemeIfNeededHelper("sip:alice@example.com", "sip:alice@example.com");

    // test white space is ignored when checking for scheme
    testAddSipSchemeIfNeededHelper(" sip:alice@example.com ", "sip:alice@example.com");

    // test case is ignored in scheme
    testAddSipSchemeIfNeededHelper("SIP:alice@example.com", "SIP:alice@example.com");

    // test sips: scheme
    testAddSipSchemeIfNeededHelper("sips:alice@example.com", "sips:alice@example.com");

    // Degenerate test cases
    // test null case does nothing
    testAddSipSchemeIfNeededHelper(null, null);

    // test empty string does nothing
    testAddSipSchemeIfNeededHelper("", "");
  }

  private void testAddSipSchemeIfNeededHelper(String sipUrl, String expected) {
    Assert.assertEquals(JainSipHelper.addSipSchemeIfNeeded(sipUrl), expected);
  }

  @Test(description = "sip scheme removal test")
  public void testRemoveSipScheme() {
    // Positive test cases
    // test sip: scheme
    testRemoveSipSchemeHelper("sip:alice@example.com", "alice@example.com");

    // test sips: scheme
    testRemoveSipSchemeHelper("sips:alice@example.com", "alice@example.com");

    // test case insensitivity in scheme
    testRemoveSipSchemeHelper("SIP:alice@example.com", "alice@example.com");

    // test case doesn't change in rest of SIP url
    testRemoveSipSchemeHelper("sip:ALICE@example.com", "ALICE@example.com");

    // ignore white space
    testRemoveSipSchemeHelper(" sip:alice@example.com ", "alice@example.com");

    // Negative test cases
    // test null case
    testRemoveSipSchemeHelper(null, null);

    // test empty string case
    testRemoveSipSchemeHelper("", "");

    // test degenerate case of only scheme
    testRemoveSipSchemeHelper("sip:", "");

    // test that colon at end of scheme is required
    testRemoveSipSchemeHelper("sipalice@example.com", "sipalice@example.com");
  }

  private void testRemoveSipSchemeHelper(String sipUrl, String expected) {
    Assert.assertEquals(JainSipHelper.removeSipScheme(sipUrl), expected);
  }

  @DataProvider
  public Object[][] urlEncodeData() {
    return new Object[][] {
      {"always", "always"},
      {"disabled,l2sip=always", "disabled%2Cl2sip%3Dalways"},
      {null, null},
      {"", null}
    };
  }

  @Test(dataProvider = "urlEncodeData")
  public void testUrlEncode(String clear, String expectEncoded) {
    String encoded = JainSipHelper.urlEncode(clear);
    Assert.assertEquals(expectEncoded, encoded);
  }
}
