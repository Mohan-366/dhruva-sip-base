package com.cisco.dhruva.ratelimiter;

import static com.cisco.dsb.common.ratelimiter.RateLimitConstants.ALLOW_IP_LIST_POLICY;
import static com.cisco.dsb.common.ratelimiter.RateLimitConstants.DENY_IP_LIST_POLICY;
import static com.cisco.dsb.common.ratelimiter.RateLimitConstants.NETWORK_LEVEL_POLICY_PREFIX;
import static com.cisco.dsb.common.ratelimiter.RateLimitConstants.PROCESS;
import static org.mockito.Mockito.when;
import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertFalse;
import static org.testng.Assert.assertNull;
import static org.testng.Assert.assertTrue;

import com.cisco.dhruva.application.CallingAppConfigurationProperty;
import com.cisco.dsb.common.config.sip.CommonConfigurationProperties;
import com.cisco.dsb.common.exception.DhruvaException;
import com.cisco.dsb.common.ratelimiter.AllowAndDenyList;
import com.cisco.dsb.common.ratelimiter.DsbRateLimitAttribute;
import com.cisco.dsb.common.ratelimiter.DsbRateLimiter;
import com.cisco.dsb.common.ratelimiter.PolicyNetworkAssociation;
import com.cisco.dsb.common.ratelimiter.RateLimitPolicy;
import com.cisco.dsb.common.ratelimiter.RateLimitPolicy.RateLimit;
import com.cisco.dsb.common.ratelimiter.RateLimitPolicy.Type;
import com.cisco.dsb.common.servergroup.ServerGroup;
import com.cisco.dsb.common.servergroup.ServerGroupElement;
import com.cisco.dsb.common.sip.bean.SIPListenPoint;
import com.cisco.dsb.common.sip.stack.dto.DhruvaNetwork;
import com.cisco.wx2.ratelimit.policy.Policy;
import com.cisco.wx2.ratelimit.policy.RateAction;
import com.cisco.wx2.ratelimit.policy.UserMatcher;
import com.cisco.wx2.ratelimit.policy.UserMatcher.Mode;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.concurrent.atomic.AtomicInteger;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.Test;

public class CallingAppRateLimiterConfiguratorTest {
  @Mock CommonConfigurationProperties commonConfigurationProperties;
  @Mock CallingAppConfigurationProperty callingAppConfigurationProperty;
  private DsbRateLimiter dsbRateLimiter;
  private Map<String, Policy> expectedPoliciesMap = new HashMap<>();
  private RateLimitPolicy rateLimitPolicyNetwork;
  private RateLimitPolicy rateLimitPolicyGlobal;
  private RateLimit rateLimitPstn;
  private RateLimit rateLimitGlobal;
  private CallingAppRateLimiterConfigurator callingAppRateLimiterConfigurator;
  Map<String, PolicyNetworkAssociation> rateLimiterNetworkMap = new HashMap<>();
  List<RateLimitPolicy> rateLimitPolicyList = new ArrayList<>();
  String networkName;
  String hostAddress;
  private static final String UNDERSCORE = "_";
  String[] allowListPstn = {"1.1.1.1", "2.2.2.2"};
  String[] denyListPstn = {"3.3.3.3"};
  String[] allowListGlobal = {"4.4.4.4", "5.5.5.5", "10.10.10.0/24"};
  String[] denyListGlobal = {"8.8.8.8"};

  @BeforeClass
  public void setup() throws DhruvaException {
    MockitoAnnotations.openMocks(this);
    dsbRateLimiter = new DsbRateLimiter();
    rateLimitPstn = RateLimit.builder().setInterval("1s").setPermits(10).build();
    rateLimitGlobal = RateLimit.builder().setInterval("1s").setPermits(100).build();
    rateLimitPolicyNetwork =
        RateLimitPolicy.builder()
            .setName("rateLimitPolicyPstn")
            .setType(Type.NETWORK)
            .setAllowList(allowListPstn)
            .setDenyList(denyListPstn)
            .setRateLimit(rateLimitPstn)
            .setAutoBuild(true)
            .build();
    rateLimitPolicyGlobal =
        RateLimitPolicy.builder()
            .setName("rateLimitPolicyGlobal")
            .setType(Type.GLOBAL)
            .setAllowList(allowListGlobal)
            .setDenyList(denyListGlobal)
            .setRateLimit(rateLimitGlobal)
            .setAutoBuild(true)
            .build();
    rateLimitPolicyList.add(rateLimitPolicyNetwork);
    rateLimitPolicyList.add(rateLimitPolicyGlobal);
    when(callingAppConfigurationProperty.getRateLimitPolicyList()).thenReturn(rateLimitPolicyList);
    PolicyNetworkAssociation policyNetworkAssociationNetwork =
        PolicyNetworkAssociation.builder()
            .setPolicyName(rateLimitPolicyNetwork.getName())
            .setNetworks(new String[] {"n1"})
            .build();
    networkName = "n1";
    hostAddress = "1.1.1.1";
    SIPListenPoint lp1 =
        SIPListenPoint.SIPListenPointBuilder()
            .setName(networkName)
            .setHostIPAddress(hostAddress)
            .build();
    DhruvaNetwork.createNetwork(networkName, lp1);
    rateLimiterNetworkMap.put(rateLimitPolicyNetwork.getName(), policyNetworkAssociationNetwork);
    when(callingAppConfigurationProperty.getRateLimiterNetworkMap())
        .thenReturn(rateLimiterNetworkMap);
    ServerGroupElement serverGroupElement1 =
        ServerGroupElement.builder().setIpAddress("6.6.6.6").build();
    ServerGroupElement serverGroupElement2 =
        ServerGroupElement.builder().setIpAddress("7.7.7.7").build();
    ServerGroup serverGroup1 =
        ServerGroup.builder()
            .setName("s1")
            .setNetworkName(networkName)
            .setElements(Arrays.asList(serverGroupElement1, serverGroupElement2))
            .build();
    ServerGroupElement serverGroupElement3 =
        ServerGroupElement.builder().setIpAddress("12.12.12.12").build();
    ServerGroupElement serverGroupElement4 =
        ServerGroupElement.builder().setIpAddress("13.13.13.13").build();
    ServerGroup serverGroup2 =
        ServerGroup.builder()
            .setName("s2")
            .setNetworkName("n2")
            .setElements(Arrays.asList(serverGroupElement3, serverGroupElement4))
            .build();
    Map<String, ServerGroup> serverGroupMap = new HashMap<>();
    serverGroupMap.put(serverGroup1.getName(), serverGroup1);
    serverGroupMap.put(serverGroup2.getName(), serverGroup2);
    when(commonConfigurationProperties.getServerGroups()).thenReturn(serverGroupMap);

    callingAppRateLimiterConfigurator =
        new CallingAppRateLimiterConfigurator(
            callingAppConfigurationProperty, commonConfigurationProperties, dsbRateLimiter);
    addExpectedPolicies();
  }

  @Test
  public void testConfigure() {
    callingAppRateLimiterConfigurator.configure();
    // test policies
    List<Policy> policies = dsbRateLimiter.getPolicies();
    assertTrue(
        policies != null && !policies.isEmpty() && policies.size() == expectedPoliciesMap.size());
    // Equating the policies using their matchers and actions since direct equals gives an error.
    policies.forEach(
        policy -> {
          Policy expectedPolicy = expectedPoliciesMap.get(policy.getName());
          AtomicInteger i = new AtomicInteger();
          policy
              .getMatchers()
              .forEach(
                  matcher ->
                      assertEquals(
                          matcher.toString(),
                          expectedPolicy.getMatchers().get(i.getAndIncrement()).toString()));
          if (policy.getAssertAction() == null) {
            assertNull(expectedPolicy.getAssertAction());
            AtomicInteger j = new AtomicInteger();
            policy
                .getActions()
                .forEach(
                    action ->
                        assertEquals(
                            action.toString(),
                            expectedPolicy.getActions().get(j.getAndIncrement()).toString()));
          } else {
            assertEquals(
                policy.getAssertAction().getId(), expectedPolicy.getAssertAction().getId());
          }
        });

    // test lists
    dsbRateLimiter
        .getAllowDenyListsMap()
        .entrySet()
        .forEach(
            entry -> {
              String policyName = entry.getKey();
              if (policyName.contains("Pstn")) {
                assertPolicyLists(entry, rateLimitPolicyNetwork);
              } else if (policyName.contains("Global")) {
                assertPolicyLists(entry, rateLimitPolicyGlobal);
              }
            });
  }

  @Test
  public void testMapsUpdated() {
    assertFalse(callingAppRateLimiterConfigurator.mapsUpdated());

    List<RateLimitPolicy> rateLimitPolicyListNew = new ArrayList<>();
    Map<String, PolicyNetworkAssociation> rateLimiterNetworkMapNew = new HashMap<>();
    rateLimitPolicyListNew.addAll(rateLimitPolicyList);
    rateLimitPolicyListNew.add(
        RateLimitPolicy.builder()
            .setName("PolicyNew")
            .setType(Type.NETWORK)
            .setRateLimit(RateLimit.builder().setInterval("5").setPermits(2).build())
            .build());
    rateLimiterNetworkMapNew.putAll(rateLimiterNetworkMap);
    rateLimiterNetworkMapNew.put(
        "PolicyNew",
        PolicyNetworkAssociation.builder()
            .setPolicyName("PolicyNew")
            .setNetworks(new String[] {"n3"})
            .build());
    when(callingAppConfigurationProperty.getRateLimitPolicyList())
        .thenReturn(rateLimitPolicyListNew);
    assertTrue(callingAppRateLimiterConfigurator.mapsUpdated());

    when(callingAppConfigurationProperty.getRateLimitPolicyList()).thenReturn(rateLimitPolicyList);
    when(callingAppConfigurationProperty.getRateLimiterNetworkMap())
        .thenReturn(rateLimiterNetworkMapNew);
    assertTrue(callingAppRateLimiterConfigurator.mapsUpdated());
    when(callingAppConfigurationProperty.getRateLimitPolicyList()).thenReturn(rateLimitPolicyList);
    when(callingAppConfigurationProperty.getRateLimiterNetworkMap())
        .thenReturn(rateLimiterNetworkMap);
    assertFalse(callingAppRateLimiterConfigurator.mapsUpdated());
  }

  private void assertPolicyLists(
      Entry<String, AllowAndDenyList> entry, RateLimitPolicy rateLimitPolicy) {
    List<String> allDeny = new ArrayList<>();
    allDeny.addAll(entry.getValue().getDenyIPList());
    allDeny.addAll(entry.getValue().getDenyIPRangeList());

    List<String> expectedAllowList = new ArrayList<>();
    List<String> allAllow = new ArrayList<>();
    allAllow.addAll(entry.getValue().getAllowIPList());
    allAllow.addAll(entry.getValue().getAllowIPRangeList());
    if (rateLimitPolicy.isAutoBuild()) {
      List<String> sgAllowList = new ArrayList<>();
      commonConfigurationProperties
          .getServerGroups()
          .values()
          .forEach(
              serverGroup -> {
                PolicyNetworkAssociation policyNetworkAssociation =
                    rateLimiterNetworkMap.get(rateLimitPolicy.getName());
                if (policyNetworkAssociation == null) {
                  return;
                }
                for (String network : policyNetworkAssociation.getNetworks()) {
                  if (network.equals(serverGroup.getNetworkName())) {
                    serverGroup
                        .getElements()
                        .forEach(
                            serverGroupElement ->
                                sgAllowList.add(serverGroupElement.getIpAddress()));
                  }
                }
              });
      if (!sgAllowList.isEmpty()) {
        expectedAllowList.addAll(sgAllowList);
      }
    }
    expectedAllowList.addAll(Arrays.asList(rateLimitPolicy.getAllowList()));
    assertEquals(allAllow.size(), expectedAllowList.size());
    allAllow.forEach(item -> assertTrue(expectedAllowList.contains(item)));
    assertEquals(allDeny, Arrays.asList(rateLimitPolicy.getDenyList()));
  }

  private void addExpectedPolicies() {
    String policyValueNetwork =
        callingAppRateLimiterConfigurator.getPolicyValue(rateLimitPolicyNetwork);
    expectedPoliciesMap.put(
        DENY_IP_LIST_POLICY + UNDERSCORE + rateLimitPolicyNetwork.getName(),
        Policy.builder(DENY_IP_LIST_POLICY + UNDERSCORE + rateLimitPolicyNetwork.getName())
            .matcher(
                new UserMatcher(UserMatcher.Mode.MATCH_ALL)
                    .addProperty(DsbRateLimitAttribute.DENY_IP.toString(), policyValueNetwork))
            .deny()
            .build());
    expectedPoliciesMap.put(
        ALLOW_IP_LIST_POLICY + UNDERSCORE + rateLimitPolicyNetwork.getName(),
        Policy.builder(ALLOW_IP_LIST_POLICY + UNDERSCORE + rateLimitPolicyNetwork.getName())
            .matcher(
                new UserMatcher(UserMatcher.Mode.MATCH_ALL)
                    .addProperty(DsbRateLimitAttribute.ALLOW_IP.toString(), policyValueNetwork))
            .allow()
            .build());
    expectedPoliciesMap.put(
        NETWORK_LEVEL_POLICY_PREFIX + UNDERSCORE + rateLimitPolicyNetwork.getName(),
        Policy.builder(NETWORK_LEVEL_POLICY_PREFIX + UNDERSCORE + rateLimitPolicyNetwork.getName())
            .matcher(
                (new UserMatcher(Mode.MATCH_ALL))
                    .addProperty(
                        DsbRateLimitAttribute.DHRUVA_NETWORK_RATE_LIMIT.toString(),
                        policyValueNetwork))
            .action((new RateAction(rateLimitPstn.getPermits(), rateLimitPstn.getInterval(), null)))
            .build());

    String policyValueGlobal =
        callingAppRateLimiterConfigurator.getPolicyValue(rateLimitPolicyGlobal);
    expectedPoliciesMap.put(
        DENY_IP_LIST_POLICY + UNDERSCORE + rateLimitPolicyGlobal.getName(),
        Policy.builder(DENY_IP_LIST_POLICY + UNDERSCORE + rateLimitPolicyGlobal.getName())
            .matcher(
                new UserMatcher(UserMatcher.Mode.MATCH_ALL)
                    .addProperty(DsbRateLimitAttribute.DENY_IP.toString(), policyValueGlobal))
            .deny()
            .build());
    expectedPoliciesMap.put(
        ALLOW_IP_LIST_POLICY + UNDERSCORE + rateLimitPolicyGlobal.getName(),
        Policy.builder(ALLOW_IP_LIST_POLICY + UNDERSCORE + rateLimitPolicyGlobal.getName())
            .matcher(
                new UserMatcher(UserMatcher.Mode.MATCH_ALL)
                    .addProperty(DsbRateLimitAttribute.ALLOW_IP.toString(), policyValueGlobal))
            .allow()
            .build());
    expectedPoliciesMap.put(
        NETWORK_LEVEL_POLICY_PREFIX + UNDERSCORE + rateLimitPolicyGlobal.getName(),
        Policy.builder(NETWORK_LEVEL_POLICY_PREFIX + UNDERSCORE + rateLimitPolicyGlobal.getName())
            .matcher(
                (new UserMatcher(Mode.MATCH_ALL))
                    .addProperty(
                        DsbRateLimitAttribute.DHRUVA_NETWORK_RATE_LIMIT.toString(),
                        policyValueGlobal))
            .action(
                (new RateAction(
                    rateLimitGlobal.getPermits(), rateLimitGlobal.getInterval(), PROCESS)))
            .build());
  }
}
