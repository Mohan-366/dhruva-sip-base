package com.cisco.dsb.common.ratelimiter;

import static com.cisco.dsb.common.ratelimiter.RateLimitConstants.ALL;
import static com.cisco.dsb.common.ratelimiter.RateLimitConstants.POLICY_VALUE_DELIMITER;
import static com.cisco.dsb.common.ratelimiter.RateLimitConstants.PROCESS;
import static gov.nist.javax.sip.header.SIPHeaderNames.CALL_ID;
import static org.mockito.Mockito.when;

import com.cisco.wx2.ratelimit.policy.Policy;
import com.cisco.wx2.ratelimit.policy.RateAction;
import com.cisco.wx2.ratelimit.policy.UserMatcher;
import com.cisco.wx2.ratelimit.policy.UserMatcher.Mode;
import gov.nist.javax.sip.header.CallID;
import gov.nist.javax.sip.message.SIPRequest;
import gov.nist.javax.sip.stack.MessageChannel;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;
import javax.sip.message.Response;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.mockito.Spy;
import org.testng.Assert;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

public class DsbRateLimiterTest {

  DsbRateLimiter dsbRateLimiter;
  DsbRateLimiterValve dsbRateLimiterValve;
  @Spy SIPRequest sipRequest;
  @Mock Response response;
  @Mock MessageChannel messageChannel;
  @Mock CallID callID;

  @BeforeMethod
  public void setup() {
    MockitoAnnotations.openMocks(this);
    dsbRateLimiterValve = new DsbRateLimiterValve();
    dsbRateLimiter = new DsbRateLimiter();
    dsbRateLimiter.init();
    dsbRateLimiterValve.initFromApplication(dsbRateLimiter);
    when(callID.getCallId()).thenReturn("01234");
    when(response.getHeader(CALL_ID)).thenReturn(callID);
    when(callID.toString()).thenReturn("call-id: 12345");
    when(sipRequest.getCallId()).thenReturn(callID);
  }

  @Test
  public void testNetworkRateLimit() throws InterruptedException {
    String localAddress = "1.1.1.1", remoteAddress = "2.2.2.2";

    Policy rateLimitNetworkPolicy =
        Policy.builder("rateLimitNetworkPolicy")
            .matcher(
                (new UserMatcher(Mode.MATCH_ALL))
                    .addProperty(
                        DsbRateLimitAttribute.DHRUVA_NETWORK_RATE_LIMIT.toString(),
                        "rateLimitNetworkPolicy" + POLICY_VALUE_DELIMITER + localAddress))
            .action((new RateAction(2, "1s", null)))
            .build();
    when(messageChannel.getHost()).thenReturn(localAddress);
    when(messageChannel.getPeerAddress()).thenReturn(remoteAddress);
    dsbRateLimiter.addPolicies(Arrays.asList(rateLimitNetworkPolicy));
    // testing request rate limit.
    Boolean isRequestAllowed = dsbRateLimiterValve.processRequest(sipRequest, messageChannel);
    Assert.assertEquals(isRequestAllowed, true);
    isRequestAllowed = dsbRateLimiterValve.processRequest(sipRequest, messageChannel);
    Assert.assertEquals(isRequestAllowed, true);
    isRequestAllowed = dsbRateLimiterValve.processRequest(sipRequest, messageChannel);
    Assert.assertEquals(isRequestAllowed, false);
    Thread.sleep(1000);
    // testing response rate limit. Should be no different from request
    Boolean isResponseAllowed = dsbRateLimiterValve.processResponse(response, messageChannel);
    Assert.assertEquals(isResponseAllowed, true);
    isResponseAllowed = dsbRateLimiterValve.processResponse(response, messageChannel);
    Assert.assertEquals(isResponseAllowed, true);
    isResponseAllowed = dsbRateLimiterValve.processResponse(response, messageChannel);
    Assert.assertEquals(isResponseAllowed, false);
    Thread.sleep(1000);
    // testing request response mix
    isRequestAllowed = dsbRateLimiterValve.processRequest(sipRequest, messageChannel);
    Assert.assertEquals(isRequestAllowed, true);
    isResponseAllowed = dsbRateLimiterValve.processResponse(response, messageChannel);
    Assert.assertEquals(isResponseAllowed, true);
    isRequestAllowed = dsbRateLimiterValve.processRequest(sipRequest, messageChannel);
    Assert.assertEquals(isRequestAllowed, false);
  }

  @Test
  public void testGlobalRatelimit() {
    String localAddress = "1.1.1.1", remoteAddress = "2.2.2.2";
    Policy rateLimitNetworkPolicy =
        Policy.builder("rateLimitNetworkPolicy")
            .matcher(
                (new UserMatcher(Mode.MATCH_ALL))
                    .addProperty(
                        DsbRateLimitAttribute.DHRUVA_NETWORK_RATE_LIMIT.toString(),
                        "rateLimitNetworkPolicy" + POLICY_VALUE_DELIMITER + localAddress))
            .action((new RateAction(2, "1s", null)))
            .build();
    Policy rateLimitGlobalPolicy =
        Policy.builder("rateLimitGlobalPolicy")
            .matcher(
                (new UserMatcher(Mode.MATCH_ALL))
                    .addProperty(
                        DsbRateLimitAttribute.DHRUVA_NETWORK_RATE_LIMIT.toString(),
                        "rateLimitNetworkPolicy" + POLICY_VALUE_DELIMITER + localAddress))
            .action((new RateAction(1, "1s", PROCESS)))
            .build();
    when(messageChannel.getHost()).thenReturn(localAddress);
    when(messageChannel.getPeerAddress()).thenReturn(remoteAddress);
    dsbRateLimiter.addPolicies(Arrays.asList(rateLimitNetworkPolicy, rateLimitGlobalPolicy));
    Boolean isRequestAllowed = dsbRateLimiterValve.processRequest(sipRequest, messageChannel);
    Assert.assertEquals(isRequestAllowed, true);
    isRequestAllowed = dsbRateLimiterValve.processRequest(sipRequest, messageChannel);
    Assert.assertEquals(isRequestAllowed, false);
  }

  @Test
  public void testNetworkDenyList() {
    String localAddress = "1.1.1.1", remoteAddress = "10.10.10.10";
    AllowAndDenyList allowAndDenyList1 = new AllowAndDenyList();
    AllowAndDenyList allowAndDenyList2 = new AllowAndDenyList();
    List<String> denyList1 = Arrays.asList("20.20.20.20", remoteAddress);
    List<String> denyList2 = Arrays.asList("40.40.40.40");
    allowAndDenyList1.setDenyIPList(denyList1);
    allowAndDenyList2.setDenyIPList(denyList2);
    Map<String, AllowAndDenyList> allowDenyListMap = new HashMap<>();
    allowDenyListMap.put("denyListPolicy", allowAndDenyList1);
    allowDenyListMap.put("someOtherPolicy", allowAndDenyList2);
    dsbRateLimiter.setAllowDenyListsMap(allowDenyListMap);
    Policy rateLimitNetworkPolicy =
        Policy.builder("rateLimitNetworkPolicy")
            .matcher(
                (new UserMatcher(Mode.MATCH_ALL))
                    .addProperty(
                        DsbRateLimitAttribute.DHRUVA_NETWORK_RATE_LIMIT.toString(),
                        "rateLimitNetworkPolicy" + POLICY_VALUE_DELIMITER + localAddress))
            .action((new RateAction(2, "1s", null)))
            .build();
    Policy rateLimitGlobalPolicy =
        Policy.builder("rateLimitGlobalPolicy")
            .matcher(
                (new UserMatcher(Mode.MATCH_ALL))
                    .addProperty(
                        DsbRateLimitAttribute.DHRUVA_NETWORK_RATE_LIMIT.toString(),
                        "rateLimitNetworkPolicy" + POLICY_VALUE_DELIMITER + localAddress))
            .action((new RateAction(10, "1s", PROCESS)))
            .build();
    Policy denyListPolicy =
        Policy.builder("denyListPolicy")
            .matcher(
                (new UserMatcher(Mode.MATCH_ALL))
                    .addProperty(
                        DsbRateLimitAttribute.DENY_IP.toString(),
                        "denyListPolicy" + POLICY_VALUE_DELIMITER + localAddress))
            .deny()
            .build();
    when(messageChannel.getHost()).thenReturn(localAddress);
    when(messageChannel.getPeerAddress()).thenReturn(remoteAddress);
    dsbRateLimiter.addPolicies(
        Arrays.asList(denyListPolicy, rateLimitNetworkPolicy, rateLimitGlobalPolicy));
    Boolean isRequestAllowed = dsbRateLimiterValve.processRequest(sipRequest, messageChannel);
    Assert.assertEquals(isRequestAllowed, false);
  }

  @Test
  public void testAllowList() {
    String localAddress = "1.1.1.1", remoteAddress = "2.2.2.2";

    Policy rateLimitNetworkPolicy =
        Policy.builder("rateLimitNetworkPolicy")
            .matcher(
                (new UserMatcher(Mode.MATCH_ALL))
                    .addProperty(
                        DsbRateLimitAttribute.DHRUVA_NETWORK_RATE_LIMIT.toString(),
                        "rateLimitNetworkPolicy" + POLICY_VALUE_DELIMITER + localAddress))
            .action((new RateAction(0, "1s", null)))
            .build();
    when(messageChannel.getHost()).thenReturn(localAddress);
    when(messageChannel.getPeerAddress()).thenReturn(remoteAddress);
    dsbRateLimiter.addPolicies(Arrays.asList(rateLimitNetworkPolicy));
    Boolean isRequestAllowed = dsbRateLimiterValve.processRequest(sipRequest, messageChannel);
    Assert.assertEquals(isRequestAllowed, false);

    AllowAndDenyList allowAndDenyList = new AllowAndDenyList();
    List<String> allowList = Arrays.asList("20.20.20.20", remoteAddress);
    allowAndDenyList.setAllowIPList(allowList);
    Map<String, AllowAndDenyList> allowDenyListMap = new HashMap<>();
    Policy allowListPolicy =
        Policy.builder("allowListPolicy")
            .matcher(
                (new UserMatcher(Mode.MATCH_ALL))
                    .addProperty(
                        DsbRateLimitAttribute.ALLOW_IP.toString(),
                        "allowListPolicy" + POLICY_VALUE_DELIMITER + localAddress))
            .allow()
            .build();
    allowDenyListMap.put("allowListPolicy", allowAndDenyList);
    dsbRateLimiter.setAllowDenyListsMap(allowDenyListMap);
    dsbRateLimiter.setPolicies(Arrays.asList(allowListPolicy, rateLimitNetworkPolicy));
    isRequestAllowed = dsbRateLimiterValve.processRequest(sipRequest, messageChannel);
    Assert.assertEquals(isRequestAllowed, true);
    isRequestAllowed = dsbRateLimiterValve.processRequest(sipRequest, messageChannel);
    Assert.assertEquals(isRequestAllowed, true);
    isRequestAllowed = dsbRateLimiterValve.processRequest(sipRequest, messageChannel);
    Assert.assertEquals(isRequestAllowed, true);
  }

  @Test
  public void testAllowRangeList() {
    String localAddress = "1.1.1.1", remoteAddress = "192.168.0.15";
    Policy rateLimitNetworkPolicy =
        Policy.builder("rateLimitNetworkPolicy")
            .matcher(
                (new UserMatcher(Mode.MATCH_ALL))
                    .addProperty(
                        DsbRateLimitAttribute.DHRUVA_NETWORK_RATE_LIMIT.toString(),
                        "rateLimitNetworkPolicy" + POLICY_VALUE_DELIMITER + localAddress))
            .action((new RateAction(0, "1s", null)))
            .build();
    when(messageChannel.getHost()).thenReturn(localAddress);
    when(messageChannel.getPeerAddress()).thenReturn(remoteAddress);
    dsbRateLimiter.addPolicies(Arrays.asList(rateLimitNetworkPolicy));
    Boolean isRequestAllowed = dsbRateLimiterValve.processRequest(sipRequest, messageChannel);
    Assert.assertEquals(isRequestAllowed, false);

    Policy allowRangeListPolicy =
        Policy.builder("allowRangeListPolicy")
            .matcher(
                (new UserMatcher(Mode.MATCH_ALL))
                    .addProperty(
                        DsbRateLimitAttribute.ALLOW_IP.toString(),
                        "allowRangeListPolicy" + POLICY_VALUE_DELIMITER + localAddress))
            .allow()
            .build();
    AllowAndDenyList allowAndDenyList = new AllowAndDenyList();
    List<String> allowList = Arrays.asList("20.20.20.20");
    List<String> allowRangeList = Arrays.asList("192.168.0.15/24");
    allowAndDenyList.setAllowIPList(allowList);
    allowAndDenyList.setAllowIPRangeList(allowRangeList);
    Map<String, AllowAndDenyList> allowDenyListMap = new HashMap<>();
    allowDenyListMap.put("allowRangeListPolicy", allowAndDenyList);
    dsbRateLimiter.setAllowDenyListsMap(allowDenyListMap);
    dsbRateLimiter.setPolicies(Arrays.asList(allowRangeListPolicy, rateLimitNetworkPolicy));
    isRequestAllowed = dsbRateLimiterValve.processRequest(sipRequest, messageChannel);
    Assert.assertEquals(isRequestAllowed, true);
    isRequestAllowed = dsbRateLimiterValve.processRequest(sipRequest, messageChannel);
    Assert.assertEquals(isRequestAllowed, true);
    isRequestAllowed = dsbRateLimiterValve.processRequest(sipRequest, messageChannel);
    Assert.assertEquals(isRequestAllowed, true);
  }

  @Test
  public void testDenyRangeList() {
    String localAddress = "1.1.1.1", remoteAddress = "192.168.0.15";
    Policy rateLimitNetworkPolicy =
        Policy.builder("rateLimitNetworkPolicy")
            .matcher(
                (new UserMatcher(Mode.MATCH_ALL))
                    .addProperty(
                        DsbRateLimitAttribute.DHRUVA_NETWORK_RATE_LIMIT.toString(),
                        "rateLimitNetworkPolicy" + POLICY_VALUE_DELIMITER + localAddress))
            .action((new RateAction(20, "1s", null)))
            .build();
    when(messageChannel.getHost()).thenReturn(localAddress);
    when(messageChannel.getPeerAddress()).thenReturn(remoteAddress);
    dsbRateLimiter.addPolicies(Arrays.asList(rateLimitNetworkPolicy));
    Boolean isRequestAllowed = dsbRateLimiterValve.processRequest(sipRequest, messageChannel);
    Assert.assertEquals(isRequestAllowed, true);

    Policy denyListPolicy =
        Policy.builder("denyRangeListPolicy")
            .matcher(
                (new UserMatcher(Mode.MATCH_ALL))
                    .addProperty(
                        DsbRateLimitAttribute.DENY_IP.toString(),
                        "denyRangeListPolicy" + POLICY_VALUE_DELIMITER + localAddress))
            .deny()
            .build();
    AllowAndDenyList allowAndDenyList = new AllowAndDenyList();
    List<String> denyList = Arrays.asList("20.20.20.20");
    List<String> denyRangeList = Arrays.asList("192.168.0.15/24");
    allowAndDenyList.setDenyIPList(denyList);
    allowAndDenyList.setDenyIPRangeList(denyRangeList);
    Map<String, AllowAndDenyList> allowDenyListMap = new HashMap<>();
    allowDenyListMap.put("denyRangeListPolicy", allowAndDenyList);
    dsbRateLimiter.setAllowDenyListsMap(allowDenyListMap);
    dsbRateLimiter.setPolicies(Arrays.asList(denyListPolicy, rateLimitNetworkPolicy));
    isRequestAllowed = dsbRateLimiterValve.processRequest(sipRequest, messageChannel);
    Assert.assertEquals(isRequestAllowed, false);
  }

  @Test
  public void testDenyRangeListGlobal() {
    String localAddress = "1.1.1.1", remoteAddress = "192.168.0.15";
    Policy rateLimitNetworkPolicy =
        Policy.builder("rateLimitNetworkPolicy")
            .matcher(
                (new UserMatcher(Mode.MATCH_ALL))
                    .addProperty(
                        DsbRateLimitAttribute.DHRUVA_NETWORK_RATE_LIMIT.toString(),
                        "rateLimitNetworkPolicy" + POLICY_VALUE_DELIMITER + localAddress))
            .action((new RateAction(20, "1s", null)))
            .build();
    when(messageChannel.getHost()).thenReturn(localAddress);
    when(messageChannel.getPeerAddress()).thenReturn(remoteAddress);
    dsbRateLimiter.addPolicies(Arrays.asList(rateLimitNetworkPolicy));
    Boolean isRequestAllowed = dsbRateLimiterValve.processRequest(sipRequest, messageChannel);
    Assert.assertEquals(isRequestAllowed, true);

    Policy denyListPolicy =
        Policy.builder("denyRangeListPolicyNetwork")
            .matcher(
                (new UserMatcher(Mode.MATCH_ALL))
                    .addProperty(
                        DsbRateLimitAttribute.DENY_IP.toString(),
                        "denyRangeListPolicyNetwork" + POLICY_VALUE_DELIMITER + localAddress))
            .build();
    Policy denyListPolicyGlobal =
        Policy.builder("denyRangeListPolicyGlobal")
            .matcher(
                (new UserMatcher(Mode.MATCH_ALL))
                    .addProperty(
                        DsbRateLimitAttribute.DENY_IP.toString(),
                        "denyRangeListPolicyGlobal" + POLICY_VALUE_DELIMITER + ALL))
            .deny()
            .build();
    AllowAndDenyList allowAndDenyList = new AllowAndDenyList();
    List<String> denyList = Arrays.asList("20.20.20.20");
    List<String> denyRangeList = Arrays.asList("192.168.0.15/24");
    allowAndDenyList.setDenyIPList(denyList);
    allowAndDenyList.setDenyIPRangeList(denyRangeList);
    Map<String, AllowAndDenyList> allowDenyListMap = new HashMap<>();
    allowDenyListMap.put("denyRangeListPolicyGlobal", allowAndDenyList);
    AllowAndDenyList allowAndDenyListNetwork = new AllowAndDenyList();
    allowAndDenyListNetwork.setDenyIPList(Arrays.asList("5.5.5.5"));
    allowDenyListMap.put("denyRangeListPolicyNetwork", allowAndDenyListNetwork);
    dsbRateLimiter.setAllowDenyListsMap(allowDenyListMap);
    dsbRateLimiter.setPolicies(
        Arrays.asList(denyListPolicyGlobal, denyListPolicy, rateLimitNetworkPolicy));
    isRequestAllowed = dsbRateLimiterValve.processRequest(sipRequest, messageChannel);
    Assert.assertEquals(isRequestAllowed, false);
  }

  @Test
  public void testSetPolicy() {
    Policy denyListPolicy =
        Policy.builder("denyRangeListPolicy")
            .matcher(
                (new UserMatcher(Mode.MATCH_ALL))
                    .addProperty(
                        DsbRateLimitAttribute.DENY_IP.toString(),
                        "denyRangeListPolicy" + POLICY_VALUE_DELIMITER + "1.1.1.1"))
            .deny()
            .build();
    dsbRateLimiter.setPolicy(denyListPolicy);
    Assert.assertEquals(dsbRateLimiter.getPolicy("denyRangeListPolicy"), denyListPolicy);
    dsbRateLimiter.setPolicy(null); // Should have no effect
  }

  @Test
  public void testSetPolicies() {
    String localAddress = "1.1.1.1";
    Policy rateLimitNetworkPolicy =
        Policy.builder("rateLimitNetworkPolicy")
            .matcher(
                (new UserMatcher(Mode.MATCH_ALL))
                    .addProperty(
                        DsbRateLimitAttribute.DHRUVA_NETWORK_RATE_LIMIT.toString(),
                        "rateLimitNetworkPolicy" + POLICY_VALUE_DELIMITER + localAddress))
            .action((new RateAction(20, "1s", null)))
            .build();
    Policy denyListPolicy =
        Policy.builder("denyRangeListPolicy")
            .matcher(
                (new UserMatcher(Mode.MATCH_ALL))
                    .addProperty(
                        DsbRateLimitAttribute.DENY_IP.toString(),
                        "denyRangeListPolicy" + POLICY_VALUE_DELIMITER + localAddress))
            .deny()
            .build();
    List<Policy> policies = Arrays.asList(rateLimitNetworkPolicy, denyListPolicy);
    dsbRateLimiter.setPolicies(policies);
    dsbRateLimiter.setPolicies(null); // should have no effect
    Assert.assertEquals(dsbRateLimiter.getPolicies().size(), policies.size());
    dsbRateLimiter.getPolicies().stream()
        .forEach(
            policy -> {
              Assert.assertTrue(policies.contains(policy));
            });
  }

  @Test(description = "ratelimit should not be applied based on remote-ip but on call-id")
  public void testCallIDAsUserId() {
    String localAddress = "1.1.1.1", remoteAddress = "2.2.2.2";
    Consumer<MessageMetaData> consumer =
        messageMetaData -> {
          messageMetaData.setUserID(messageMetaData.getCallId());
        };
    when(messageChannel.getHost()).thenReturn(localAddress);
    when(messageChannel.getPeerAddress()).thenReturn(remoteAddress);
    when(callID.getCallId()).thenReturn("01234").thenReturn("56789").thenReturn("bcdef");
    when(sipRequest.getCallId()).thenReturn(callID);

    dsbRateLimiter.setUserIdSetter(consumer);
    Policy rateLimitNetworkPolicy =
        Policy.builder("rateLimitNewCall")
            .matcher(
                (new UserMatcher(Mode.MATCH_ALL))
                    .addProperty(DsbRateLimitAttribute.NEW_CALL.toString(), ""))
            .action((new RateAction(2, "1s", null)))
            .build();
    dsbRateLimiter.addPolicies(Arrays.asList(rateLimitNetworkPolicy));

    Boolean isRequestAllowed = dsbRateLimiterValve.processRequest(sipRequest, messageChannel);
    Assert.assertEquals(isRequestAllowed, true);
    isRequestAllowed = dsbRateLimiterValve.processRequest(sipRequest, messageChannel);
    Assert.assertEquals(isRequestAllowed, true);
    isRequestAllowed = dsbRateLimiterValve.processRequest(sipRequest, messageChannel);
    Assert.assertEquals(isRequestAllowed, true);
    isRequestAllowed = dsbRateLimiterValve.processRequest(sipRequest, messageChannel);
    Assert.assertEquals(isRequestAllowed, true);
    isRequestAllowed = dsbRateLimiterValve.processRequest(sipRequest, messageChannel);
    Assert.assertEquals(isRequestAllowed, false);
  }
}
