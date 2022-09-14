package com.cisco.dsb.common.ratelimiter;

import static com.cisco.dsb.common.ratelimiter.RateLimitConstants.POLICY_VALUE_DELIMITER;
import static org.testng.Assert.*;

import gov.nist.javax.sip.message.SIPMessage;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutionException;
import java.util.regex.Pattern;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

public class DsbRateLimitContextTest {
  private DsbRateLimitContext dsbRateLimitContext;
  @Mock SIPMessage sipMessage;
  DsbRateLimiter dsbRateLimiter;

  AllowAndDenyList allowAndDenyList;
  String policyName = "policy";
  List<String> denyList = Arrays.asList("2.2.2.2", "3.3.3.3", "4.4.4.4");
  List<String> allowList = Arrays.asList("5.5.5.5", "6.6.6.6", "7.7.7.7");
  List<String> denyRangeList = Arrays.asList("8.8.8.8/24", "9.9.9.0/24");
  List<String> allowRangeList = Arrays.asList("10.10.10.0/24", "11.11.11.11/24");
  MessageMetaData messageMetaData;

  @BeforeClass
  public void setup() {
    MockitoAnnotations.openMocks(this);

    dsbRateLimiter = new DsbRateLimiter();
    dsbRateLimiter.init();
  }

  @BeforeMethod
  public void setupPerTest() {
    Map<String, AllowAndDenyList> allowDenyListMap = new HashMap<>();
    allowAndDenyList =
        AllowAndDenyList.builder()
            .setAllowIPList(allowList)
            .setAllowIPRangeList(allowRangeList)
            .setDenyIPList(denyList)
            .setDenyIPRangeList(denyRangeList)
            .build();

    allowDenyListMap.put(policyName, allowAndDenyList);
    dsbRateLimiter.setAllowDenyListsMap(allowDenyListMap);
    messageMetaData =
        MessageMetaData.builder()
            .localIP("1.1.1.1")
            .remoteIP("2.2.2.2")
            .message(sipMessage)
            .userID("2.2.2.2")
            .build();
  }

  @Test(description = "message remote_ip matches configured deny ip")
  public void testIsUserPropertyMatchForDenyIP() throws ExecutionException {
    messageMetaData.setRemoteIP("2.2.2.2");
    dsbRateLimitContext = new DsbRateLimitContext(messageMetaData, dsbRateLimiter);
    boolean isMatch =
        dsbRateLimitContext.isUserPropertyMatch(
            DsbRateLimitAttribute.DENY_IP.toString(),
            policyName + POLICY_VALUE_DELIMITER + "1.1.1.1" + POLICY_VALUE_DELIMITER + "3.3.3.3");
    assertTrue(isMatch);
  }

  @Test(description = "message remote_ip matches configured deny range cidr")
  public void testIsUserPropertyMatchForDenyRange() throws ExecutionException {
    messageMetaData.setRemoteIP("9.9.9.1");
    dsbRateLimitContext = new DsbRateLimitContext(messageMetaData, dsbRateLimiter);
    boolean isMatch =
        dsbRateLimitContext.isUserPropertyMatch(
            DsbRateLimitAttribute.DENY_IP.toString(),
            policyName + POLICY_VALUE_DELIMITER + "1.1.1.1" + POLICY_VALUE_DELIMITER + "3.3.3.3");
    assertTrue(isMatch);
  }

  @Test(description = "message remote_ip does not match configured deny")
  public void testIsUserPropertyMatchNotDeny() throws ExecutionException {
    messageMetaData.setRemoteIP("5.5.5.5");
    dsbRateLimitContext = new DsbRateLimitContext(messageMetaData, dsbRateLimiter);
    boolean isMatch =
        dsbRateLimitContext.isUserPropertyMatch(
            DsbRateLimitAttribute.DENY_IP.toString(),
            policyName + POLICY_VALUE_DELIMITER + "1.1.1.1" + POLICY_VALUE_DELIMITER + "3.3.3.3");
    assertFalse(isMatch);
  }

  @Test(description = "message remote_ip matches configured allow ip")
  public void testIsUserPropertyMatchForAllowIP() throws ExecutionException {
    messageMetaData.setRemoteIP("5.5.5.5");
    dsbRateLimitContext = new DsbRateLimitContext(messageMetaData, dsbRateLimiter);
    boolean isMatch =
        dsbRateLimitContext.isUserPropertyMatch(
            DsbRateLimitAttribute.ALLOW_IP.toString(),
            policyName + POLICY_VALUE_DELIMITER + "1.1.1.1" + POLICY_VALUE_DELIMITER + "3.3.3.3");
    assertTrue(isMatch);
  }

  @Test(description = "message remote_ip matches configured allow range cidr")
  public void testIsUserPropertyMatchForAllowRange() throws ExecutionException {
    messageMetaData.setRemoteIP("10.10.10.1");
    dsbRateLimitContext = new DsbRateLimitContext(messageMetaData, dsbRateLimiter);
    boolean isMatch =
        dsbRateLimitContext.isUserPropertyMatch(
            DsbRateLimitAttribute.ALLOW_IP.toString(),
            policyName + POLICY_VALUE_DELIMITER + "1.1.1.1" + POLICY_VALUE_DELIMITER + "3.3.3.3");
    assertTrue(isMatch);
  }

  @Test(description = "message remote ip qualifies for network rate limit on lp 1.1.1.1")
  public void testIsUserPropertyMatchNetworkRateLimit() throws ExecutionException {
    messageMetaData.setRemoteIP("12.12.12.12");
    dsbRateLimitContext = new DsbRateLimitContext(messageMetaData, dsbRateLimiter);
    boolean isMatch =
        dsbRateLimitContext.isUserPropertyMatch(
            DsbRateLimitAttribute.DHRUVA_NETWORK_RATE_LIMIT.toString(),
            policyName + POLICY_VALUE_DELIMITER + "1.1.1.1" + POLICY_VALUE_DELIMITER + "3.3.3.3");
    assertTrue(isMatch);
  }

  @Test(description = "message remote ip does not qualify for network rate limit on lp 1.1.1.1")
  public void testIsUserPropertyMatchNetworkRateLimitNoMatch() throws ExecutionException {
    messageMetaData.setRemoteIP("12.12.12.12");
    messageMetaData.setLocalIP("2.2.2.2");
    messageMetaData.setUserID("12.12.12.12");
    dsbRateLimitContext = new DsbRateLimitContext(messageMetaData, dsbRateLimiter);
    boolean isMatch =
        dsbRateLimitContext.isUserPropertyMatch(
            DsbRateLimitAttribute.DHRUVA_NETWORK_RATE_LIMIT.toString(),
            policyName + POLICY_VALUE_DELIMITER + "1.1.1.1" + POLICY_VALUE_DELIMITER + "3.3.3.3");
    assertFalse(isMatch);
  }

  @Test(description = "remote ip null should return match as false for deny_ip attribute")
  public void testIsUserPropertyMatchRemoteIPNull() throws ExecutionException {
    messageMetaData.setRemoteIP(null);
    messageMetaData.setLocalIP("1.1.1.1");
    dsbRateLimitContext = new DsbRateLimitContext(messageMetaData, dsbRateLimiter);
    boolean isMatch =
        dsbRateLimitContext.isUserPropertyMatch(
            DsbRateLimitAttribute.DENY_IP.toString(),
            policyName + POLICY_VALUE_DELIMITER + "1.1.1.1" + POLICY_VALUE_DELIMITER + "3.3.3.3");
    assertFalse(isMatch);
  }

  @Test(description = "no match for deny attribute when allowDenyList object is null for a policy")
  public void testIsUserPropertyMatchAllowDenyListNull() throws ExecutionException {
    messageMetaData.setRemoteIP("12.12.12.12");
    messageMetaData.setLocalIP("1.1.1.1");
    dsbRateLimiter.setAllowDenyListsMap(new HashMap<>());
    dsbRateLimitContext = new DsbRateLimitContext(messageMetaData, dsbRateLimiter);
    boolean isMatch =
        dsbRateLimitContext.isUserPropertyMatch(
            DsbRateLimitAttribute.DENY_IP.toString(),
            policyName + POLICY_VALUE_DELIMITER + "1.1.1.1" + POLICY_VALUE_DELIMITER + "3.3.3.3");
    assertFalse(isMatch);
  }

  @Test(description = "no match for deny attribute when policy value is invalid")
  public void testIsUserPropertyMatchInvalidPolicy() throws ExecutionException {
    dsbRateLimitContext = new DsbRateLimitContext(messageMetaData, dsbRateLimiter);
    boolean isMatch =
        dsbRateLimitContext.isUserPropertyMatch(
            DsbRateLimitAttribute.DENY_IP.toString(), policyName + POLICY_VALUE_DELIMITER);
    assertFalse(isMatch);
  }

  @Test(description = "no match for null attribute")
  public void testIsUserPropertyMatchNullAttribute() throws ExecutionException {
    dsbRateLimitContext = new DsbRateLimitContext(messageMetaData, dsbRateLimiter);
    boolean isMatch =
        dsbRateLimitContext.isUserPropertyMatch(
            null,
            policyName + POLICY_VALUE_DELIMITER + "1.1.1.1" + POLICY_VALUE_DELIMITER + "3.3.3.3");
    assertFalse(isMatch);
  }

  @Test(description = "no match for invalid attribute")
  public void testIsUserPropertyMatch() throws ExecutionException {
    dsbRateLimiter.setAllowDenyListsMap(new HashMap<>());
    dsbRateLimitContext = new DsbRateLimitContext(messageMetaData, dsbRateLimiter);
    boolean isMatch =
        dsbRateLimitContext.isUserPropertyMatch(
            "invalid_attribute",
            policyName + POLICY_VALUE_DELIMITER + "1.1.1.1" + POLICY_VALUE_DELIMITER + "3.3.3.3");
    assertFalse(isMatch);
  }

  @Test
  public void testIsUserPropertyMatchForRegex() {
    // Testing unused method. Should always return false.
    Pattern p = Pattern.compile("some_pattern");
    assertFalse(dsbRateLimitContext.isUserPropertyMatch("val", p));
  }

  @Test
  public void testIsClientIdMatch() throws ExecutionException {
    // Testing unused method. Should always return false.
    MessageMetaData messageMetaData =
        MessageMetaData.builder()
            .localIP("1.1.1.1")
            .remoteIP("2.2.2.2")
            .message(sipMessage)
            .userID("2.2.2.2")
            .build();
    dsbRateLimitContext = new DsbRateLimitContext(messageMetaData, dsbRateLimiter);
    assertFalse(dsbRateLimitContext.isClientIdMatch("some_string"));
  }
}
