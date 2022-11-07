package com.cisco.dhruva.ratelimiter;

import static com.cisco.dsb.common.ratelimiter.RateLimitConstants.ALL;
import static com.cisco.dsb.common.ratelimiter.RateLimitConstants.ALLOW_IP_LIST_POLICY;
import static com.cisco.dsb.common.ratelimiter.RateLimitConstants.DENY_IP_LIST_POLICY;
import static com.cisco.dsb.common.ratelimiter.RateLimitConstants.NETWORK_LEVEL_POLICY_PREFIX;
import static com.cisco.dsb.common.ratelimiter.RateLimitConstants.POLICY_VALUE_DELIMITER;
import static com.cisco.dsb.common.ratelimiter.RateLimitConstants.PROCESS;
import static com.cisco.dsb.common.ratelimiter.RateLimitConstants.UNDERSCORE;

import com.cisco.dhruva.application.CallingAppConfigurationProperty;
import com.cisco.dsb.common.config.sip.CommonConfigurationProperties;
import com.cisco.dsb.common.executor.DhruvaExecutorService;
import com.cisco.dsb.common.executor.ExecutorType;
import com.cisco.dsb.common.ratelimiter.AllowAndDenyList;
import com.cisco.dsb.common.ratelimiter.DsbRateLimitAttribute;
import com.cisco.dsb.common.ratelimiter.DsbRateLimiter;
import com.cisco.dsb.common.ratelimiter.MessageMetaData;
import com.cisco.dsb.common.ratelimiter.PolicyNetworkAssociation;
import com.cisco.dsb.common.ratelimiter.RateLimitPolicy;
import com.cisco.dsb.common.ratelimiter.RateLimitPolicy.Type;
import com.cisco.dsb.common.ratelimiter.RateLimiterConfigurator;
import com.cisco.dsb.common.servergroup.ServerGroup;
import com.cisco.dsb.common.sip.stack.dto.DhruvaNetwork;
import com.cisco.wx2.ratelimit.policy.Policy;
import com.cisco.wx2.ratelimit.policy.RateAction;
import com.cisco.wx2.ratelimit.policy.UserMatcher;
import com.cisco.wx2.ratelimit.policy.UserMatcher.Mode;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.function.Consumer;
import lombok.CustomLog;
import lombok.Getter;
import lombok.NonNull;
import lombok.Setter;
import org.apache.commons.collections4.CollectionUtils;
import org.springframework.cloud.context.environment.EnvironmentChangeEvent;
import org.springframework.context.ApplicationListener;
import org.springframework.stereotype.Component;

@CustomLog
@Component
public class CallingAppRateLimiterConfigurator
    implements RateLimiterConfigurator, ApplicationListener<EnvironmentChangeEvent> {

  private DsbRateLimiter dsbRateLimiter;
  private List<RateLimitPolicy> rateLimitPolicyList;
  private Map<String, PolicyNetworkAssociation> rateLimiterNetworkMap;
  private CallingAppConfigurationProperty callingAppConfigurationProperty;
  private CommonConfigurationProperties commonConfigurationProperties;
  @Getter @Setter private List<Policy> policies;
  @Getter @Setter Map<String, AllowAndDenyList> allowDenyListsMap = new HashMap<>();
  private final String RATE_LIMITER_CONFIGURATOR_REFRESH_KEY = "rateLimit";
  private final String SERVERGROUP_REFRESH_KEY = "serverGroups";
  private DhruvaExecutorService dhruvaExecutorService;

  public CallingAppRateLimiterConfigurator(
      CallingAppConfigurationProperty callingAppConfigurationProperty,
      CommonConfigurationProperties commonConfigurationProperties,
      DsbRateLimiter dsbRateLimiter,
      DhruvaExecutorService dhruvaExecutorService) {
    this.dsbRateLimiter = dsbRateLimiter;
    this.callingAppConfigurationProperty = callingAppConfigurationProperty;
    this.commonConfigurationProperties = commonConfigurationProperties;
    this.rateLimitPolicyList = callingAppConfigurationProperty.getRateLimitPolicyList();
    this.rateLimiterNetworkMap = callingAppConfigurationProperty.getRateLimiterNetworkMap();
    this.dhruvaExecutorService = dhruvaExecutorService;
    dhruvaExecutorService.startExecutorService(ExecutorType.CONFIG_UPDATE);
  }

  @Override
  public void configure() {
    if (CollectionUtils.isEmpty(rateLimitPolicyList)) {
      logger.info("No rate-limit policies configured.");
      return;
    }
    createAllowDenyListMap();
    setDsbRateLimiterUserIdSetter(configureUserIdSetter());
    List<Policy> policies = createPolicies();
    dsbRateLimiter.setPolicies(policies);
    dsbRateLimiter.setAllowDenyListsMap(allowDenyListsMap);
  }

  @Override
  public void onApplicationEvent(EnvironmentChangeEvent event) {
    if (event.getKeys().stream()
        .anyMatch(
            key -> {
              // Refresh when RateLimiter or SG config has changes.
              return (key.contains(RATE_LIMITER_CONFIGURATOR_REFRESH_KEY)
                  || key.contains((SERVERGROUP_REFRESH_KEY)));
            })) {
      logger.info("RateLimiter environment config changed with keys :{}", event.getKeys());
      dhruvaExecutorService
          .getExecutorThreadPool(ExecutorType.CONFIG_UPDATE)
          .execute(new RefreshHandle());
    }
  }

  public void setDsbRateLimiterUserIdSetter(Consumer<MessageMetaData> userIdSetter) {
    dsbRateLimiter.setUserIdSetter(userIdSetter);
  }

  @Override
  public List<Policy> createPolicies() {
    List<Policy> policies = new ArrayList<>();
    rateLimitPolicyList.forEach(
        rateLimitPolicy -> {
          logger.info("Configuring rateLimiterPolicy: {}", rateLimitPolicy.getName());
          String policyValue = getPolicyValue(rateLimitPolicy);

          if (rateLimitPolicy.getDenyList() != null) {
            policies.add(
                Policy.builder(DENY_IP_LIST_POLICY + UNDERSCORE + rateLimitPolicy.getName())
                    .matcher(
                        new UserMatcher(UserMatcher.Mode.MATCH_ALL)
                            .addProperty(DsbRateLimitAttribute.DENY_IP.toString(), policyValue))
                    .deny()
                    .build());
          }
          // here we also check the allowDenyListMap because autoBuild option can add its own
          // allowList
          if (rateLimitPolicy.getAllowList() != null
              || (allowDenyListsMap.get(rateLimitPolicy.getName()) != null
                  && allowDenyListsMap.get(rateLimitPolicy.getName()).getAllowIPList() != null)) {
            policies.add(
                Policy.builder(ALLOW_IP_LIST_POLICY + UNDERSCORE + rateLimitPolicy.getName())
                    .matcher(
                        new UserMatcher(UserMatcher.Mode.MATCH_ALL)
                            .addProperty(DsbRateLimitAttribute.ALLOW_IP.toString(), policyValue))
                    .allow()
                    .build());
          }
          if (rateLimitPolicy.getRateLimit() != null) {
            policies.add(
                Policy.builder(NETWORK_LEVEL_POLICY_PREFIX + UNDERSCORE + rateLimitPolicy.getName())
                    .matcher(
                        (new UserMatcher(Mode.MATCH_ALL))
                            .addProperty(
                                DsbRateLimitAttribute.DHRUVA_NETWORK_RATE_LIMIT.toString(),
                                policyValue))
                    .action(
                        (new RateAction(
                            rateLimitPolicy.getRateLimit().getPermits(),
                            rateLimitPolicy.getRateLimit().getInterval(),
                            (rateLimitPolicy.getType() == Type.NETWORK) ? null : PROCESS)))
                    .build());
          }
        });
    return policies;
  }

  @Override
  public void createAllowDenyListMap() {
    allowDenyListsMap.clear();
    if (Objects.isNull(rateLimitPolicyList)) {
      return;
    }
    rateLimitPolicyList.forEach(
        rateLimitPolicy -> {
          AllowAndDenyList allowAndDenyList = new AllowAndDenyList();
          createAllowRangeList(rateLimitPolicy, allowAndDenyList);
          if (rateLimitPolicy.isAutoBuild()) {
            if (Objects.isNull(allowAndDenyList.getAllowIPList())) {
              allowAndDenyList.setAllowIPList(new HashSet<>());
            }
            createAllowIPListFromConfig(rateLimitPolicy, allowAndDenyList.getAllowIPList());
          }
          createDenyRangeList(rateLimitPolicy, allowAndDenyList);
          if (allowAndDenyList.isNotEmpty()) {
            allowDenyListsMap.put(rateLimitPolicy.getName(), allowAndDenyList);
          }
        });
  }

  protected String getPolicyValue(RateLimitPolicy rateLimitPolicy) {
    StringBuilder policyValueSB = new StringBuilder();
    policyValueSB.append(rateLimitPolicy.getName());
    StringBuilder networkInterfaces = new StringBuilder();
    if (rateLimitPolicy.getType().equals(Type.NETWORK)) {
      for (String network : rateLimiterNetworkMap.get(rateLimitPolicy.getName()).getNetworks()) {
        Optional<DhruvaNetwork> dhruvaNetwork = DhruvaNetwork.getNetwork(network);
        if (dhruvaNetwork.isPresent()) {
          networkInterfaces.append(dhruvaNetwork.get().getListenPoint().getHostIPAddress());
          networkInterfaces.append(POLICY_VALUE_DELIMITER);
        } else {
          logger.error(
              "No such network exists: {}. Cannot add policy {}",
              network,
              rateLimitPolicy.getName());
        }
      }
      policyValueSB.append(POLICY_VALUE_DELIMITER);
      policyValueSB.append(networkInterfaces.toString());
    } else {
      policyValueSB.append(POLICY_VALUE_DELIMITER);
      policyValueSB.append(ALL);
    }
    return policyValueSB.toString();
  }

  protected void createAllowIPListFromConfig(
      RateLimitPolicy rateLimitPolicy, @NonNull Set<String> allowIPList) {
    Set<String> sgAllowIPList = new HashSet<>();
    PolicyNetworkAssociation policyNetworkAssociation =
        rateLimiterNetworkMap.get(rateLimitPolicy.getName());
    Map<String, ServerGroup> serverGroupMap = commonConfigurationProperties.getServerGroups();
    if (Objects.isNull(policyNetworkAssociation) || Objects.isNull(serverGroupMap)) {
      return;
    }
    for (String network : policyNetworkAssociation.getNetworks()) {
      serverGroupMap
          .values()
          .forEach(
              serverGroup -> {
                if (serverGroup.getNetworkName().equals(network)) {
                  serverGroup
                      .getElements()
                      .forEach(
                          serverGroupElement -> {
                            String ip = serverGroupElement.getIpAddress();
                            if (ip != null) {
                              sgAllowIPList.add(ip);
                            }
                          });
                }
              });
    }
    if (CollectionUtils.isNotEmpty(sgAllowIPList)) {
      allowIPList.addAll(sgAllowIPList);
    }
  }

  protected void createAllowRangeList(
      RateLimitPolicy rateLimitPolicy, AllowAndDenyList allowAndDenyList) {
    if (rateLimitPolicy.getAllowList() != null) {
      Set<String> allowIPList = new HashSet<>(Arrays.asList(rateLimitPolicy.getAllowList()));
      separateRangeIPFromIPList(allowIPList, allowAndDenyList, true);
    }
  }

  protected void createDenyRangeList(
      RateLimitPolicy rateLimitPolicy, AllowAndDenyList allowAndDenyList) {
    if (rateLimitPolicy.getDenyList() != null) {
      Set<String> denyIPList = new HashSet<>(Arrays.asList(rateLimitPolicy.getDenyList()));
      separateRangeIPFromIPList(denyIPList, allowAndDenyList, false);
    }
  }

  protected class RefreshHandle implements Runnable {
    @Override
    public void run() {
      int initialFetchTime = 500;
      int maxFetchTime = 1500;
      if (!rateLimiterConfigUpdated(initialFetchTime, maxFetchTime)) {
        /*
         * Whether or not RL config is changed we will attempt to reconfigure it since SGs could have
         * been updated as RL autoBuild is based on SG configuration.
         */
        logger.error("RateLimiter Maps are not updated. Update will happen if SGs are updated.");
      }
      configure();
    }
  }

  private void separateRangeIPFromIPList(
      @NonNull Set<String> ipList, @NonNull AllowAndDenyList allowAndDenyList, Boolean isAllow) {
    Set<String> ipRangeList = new HashSet<>();
    for (String ip : ipList) {
      if (ip.contains("/")) {
        ipRangeList.add(ip);
        ipList.remove(ip);
      }
    }
    if (isAllow) {
      allowAndDenyList.setAllowIPList(ipList);
      allowAndDenyList.setAllowIPRangeList(ipRangeList);
    } else {
      allowAndDenyList.setDenyIPList(ipList);
      allowAndDenyList.setDenyIPRangeList(ipRangeList);
    }
  }

  private Consumer<MessageMetaData> configureUserIdSetter() {
    return messageMetaData -> messageMetaData.setUserID(messageMetaData.getRemoteIP());
  }

  /*
   * Checking for updated values after a certain time interval and retrying if it is not available
   * is a hack added to overcome a bug in the kubernetes client which takes some time (few milli seconds)
   * to update the Bean under refreshScope after doing get on that object when a config refresh happens .
   */
  protected boolean rateLimiterConfigUpdated(int initialFetchTime, int maxFetchTime) {
    boolean isUpdated = false;
    long startTime = System.currentTimeMillis();
    long endTime = startTime + maxFetchTime;
    logger.info("Change detected in RateLimiter Config. Fetching latest.");
    int i = 0;
    while (System.currentTimeMillis() < endTime) {
      try {
        Thread.sleep(initialFetchTime);
      } catch (InterruptedException e) {
        logger.error("Exception occurred for sleep.");
      }
      logger.info("Retry count: {}", i++);
      logger.info("New List");
      logger.info(callingAppConfigurationProperty.getRateLimitPolicyList().toString());
      logger.info("Old List");
      if (rateLimitPolicyList != null) logger.info(rateLimitPolicyList.toString());
      logger.info("New Map");
      callingAppConfigurationProperty
          .getRateLimiterNetworkMap()
          .forEach((key, value) -> logger.info("{}:{}", key, value));
      logger.info("Old Map");
      if (rateLimiterNetworkMap != null)
        rateLimiterNetworkMap.forEach((key, value) -> logger.info("{}:{}", key, value));
      if (!callingAppConfigurationProperty.getRateLimiterNetworkMap().equals(rateLimiterNetworkMap)
          || !callingAppConfigurationProperty
              .getRateLimitPolicyList()
              .equals(rateLimitPolicyList)) {
        rateLimitPolicyList = callingAppConfigurationProperty.getRateLimitPolicyList();
        rateLimiterNetworkMap = callingAppConfigurationProperty.getRateLimiterNetworkMap();
        isUpdated = true;
        break;
      }
    }
    return isUpdated;
  }
}
