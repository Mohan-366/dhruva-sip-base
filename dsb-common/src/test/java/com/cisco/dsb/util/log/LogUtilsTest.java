package com.cisco.dsb.util.log;

import static org.mockito.Matchers.anyString;
import static org.mockito.Mockito.*;

import com.cisco.dsb.util.RequestHelper;
import gov.nist.javax.sip.message.SIPRequest;
import java.text.ParseException;
import javax.sip.SipFactory;
import javax.sip.address.AddressFactory;
import javax.sip.address.SipURI;
import javax.sip.address.URI;
import org.apache.commons.codec.digest.DigestUtils;
import org.testng.Assert;
import org.testng.annotations.Test;

@Test
public class LogUtilsTest {
  // private static Logger logger = DSBLogger.getLogger(LogUtilsTest.class);
  private static SipFactory sipFactory;

  static {
    sipFactory = SipFactory.getInstance();
    sipFactory.setPathName("gov.nist");
  }

  public void testMaskURI() throws Exception {
    succeedMaskURI(
        "sip:alice@f23-345.four.com;party=calling", "sip:xxxxx@XXXXXXXXXXXXXXXX;party=calling");
    succeedMaskURI("sip:A%41.b-c8:password@DUDE.ORG?h=3", "sip:x%xxxxxxx:xxxxxxxx@XXXXXXXX?h=3");
    succeedMaskURI("tel:+12062563309", "tel:+XXXXXXXXXXX");
    succeedMaskURI("tel:1-206-256-3309", "tel:X-XXX-XXX-XXXX");
  }

  private void succeedMaskURI(String inUri, String expectResult) throws Exception {
    // logger.info("Test maskURI({})->{}", dq(inUri), dq(expectResult));
    AddressFactory addressFactory = sipFactory.createAddressFactory();
    URI uri = addressFactory.createURI(inUri);
    String result = LogUtils.maskURI(uri);
    Assert.assertEquals(result, expectResult);
  }

  // TODO: test is failing, should be checked
  /*public void testObfuscateSipMessageForMATS() throws ParseException {
      SIPRequest sipRequest = (SIPRequest) RequestHelper.getInviteRequest();
      LogUtils.setObfuscateLog(true);
      String maskedRequest = LogUtils.obfuscateSipMessageForMATS(sipRequest);
      String obfuscatedInvite = RequestHelper.getSDPObfuscatedInviteString();
      Assert.assertEquals(maskedRequest,obfuscatedInvite);
  }*/

  public void testObfuscateSipMessageForMATSWWithNoUserInRequestUri() throws ParseException {
    SIPRequest sipRequest = (SIPRequest) RequestHelper.getDOInvite("sip:example.com");
    LogUtils.setObfuscateLog(true);
    String maskedRequest = LogUtils.obfuscateSipMessageForMATS(sipRequest);
    Assert.assertEquals(maskedRequest, sipRequest.encode());
  }

  public void testObfuscateRequestURIForMATSWithUserNotHavingEmptyPortionAftePlus()
      throws ParseException {
    SIPRequest sipRequest = (SIPRequest) RequestHelper.getDOInvite("sip:bob+@example.com");
    LogUtils.setObfuscateLog(true);
    String maskedRequest = LogUtils.obfuscateSipMessageForMATS(sipRequest);
    Assert.assertEquals(maskedRequest, sipRequest.encode());
  }

  public void testObfuscateRequestURIForMATSEscalateFormatWithUserNameStartingWithPlus()
      throws ParseException {
    SIPRequest sipRequest = (SIPRequest) RequestHelper.getDOInvite("sip:+bob@example.com");
    LogUtils.setObfuscateLog(true);
    String maskedRequest = LogUtils.obfuscateSipMessageForMATS(sipRequest);
    Assert.assertEquals(maskedRequest, sipRequest.encode());
  }

  public void testObfuscateRequestURIForMATSEscalateFormatWithParseException()
      throws ParseException {
    SIPRequest sipRequest = (SIPRequest) RequestHelper.getDOInvite("sip:bob+1342@example.com");
    LogUtils.setObfuscateLog(true);
    SipURI requestUri = spy(((SipURI) sipRequest.getRequestURI()));
    sipRequest.setRequestURI(requestUri);
    when(requestUri.clone()).thenReturn(requestUri);
    doAnswer(
            invocation -> {
              throw new ParseException("", 2);
            })
        .when(requestUri)
        .setUser(anyString());
    String maskedRequest = LogUtils.obfuscateSipMessageForMATS(sipRequest);
    Assert.assertEquals(maskedRequest, sipRequest.encode());
  }

  // TODO: test is failing, should be checked
  /*public void testObfuscateSipMessageForMATSWithEscalatedMeetingInviteWithPin()
      throws ParseException {
      SIPRequest sipRequest = (SIPRequest) RequestHelper.getInviteRequest();
      LogUtils.setObfuscateLog(true);

      SipURI requestUri = (SipURI) sipRequest.getRequestURI();
      String toUser = requestUri.getUser();
      SipURI toHeaderUri = (SipURI) sipRequest.getToHeader().getAddress().getURI();
      requestUri.setUser(requestUri.getUser() + "+12547");
      toHeaderUri.setUser(toHeaderUri.getUser() + "+12547");

      String maskedRequest = LogUtils.obfuscateSipMessageForMATS(sipRequest);

      //Expected Invite
      String expectedInvite = RequestHelper.getSDPObfuscatedInviteString();

      expectedInvite = expectedInvite.replace("INVITE sip:bob@example.com SIP/2.0",
          "INVITE sip:bob+*****@example.com SIP/2.0");
      expectedInvite = expectedInvite.replace("To: \"Bob\" <sip:bob@example.com>",
          "To: \"Bob\" <sip:bob+*****@example.com>");
      Assert.assertEquals(maskedRequest, expectedInvite);
  }*/

  public enum OBFUSCATE_AS {
    SIPURI,
    UUID,
    ALL_ELSE
  }

  public void testObfuscateString() throws Exception {
    runObfuscateString("sip:", "alice@f23-345.four.com;party=calling", OBFUSCATE_AS.SIPURI);
    runObfuscateString("tel:", "+12062563309", OBFUSCATE_AS.SIPURI);
    runObfuscateString("SIPS:", "1-206-256-3309", OBFUSCATE_AS.SIPURI);
    runObfuscateString("sips:", "", OBFUSCATE_AS.SIPURI);
    runObfuscateString("", "a64140b5-33a2-36e1-b64f-e1238217d773", OBFUSCATE_AS.UUID);
    runObfuscateString("http://", "1-206-256-3309", OBFUSCATE_AS.ALL_ELSE);
    runObfuscateString("", "alice@cisco.com", OBFUSCATE_AS.ALL_ELSE);
  }

  // For testing URI pass the scheme separately since it should not be obfuscated.
  private void runObfuscateString(String inScheme, String inString, OBFUSCATE_AS obfuscateAs) {
    String fullString = inScheme + inString;
    // logger.info("Test obfuscateString({}) expect obfuscateAs={}", dq(inString), obfuscateAs);
    String result = LogUtils.obfuscate(fullString);
    // logger.info("...result: {}", dq(result));
    if (obfuscateAs == OBFUSCATE_AS.SIPURI) {
      // The scheme should never be obfuscated
      Assert.assertTrue(result.startsWith(inScheme));
      // If passed just a scheme (boundary condition) then that's what the result should be.
      // Otherwise (below) expect the scheme followed by the rest of the URI hashed
      if (!result.equals(inScheme)) {
        Assert.assertTrue(
            result.contains(DigestUtils.md5Hex(inString))); // we search for this in kibana
        Assert.assertFalse(result.contains(inString));
      }
    } else if (obfuscateAs == OBFUSCATE_AS.UUID) {
      // UUIDs should not be obfuscated
      Assert.assertEquals(result, inString);
    } else {
      // Fallback, the entire string is obfuscated.
      Assert.assertFalse(result.contains(inString));
    }
  }

  private String dq(String s) {
    return (s == null) ? "null" : "\"" + s + "\"";
  }
}
