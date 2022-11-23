package com.cisco.dsb.common.ratelimiter;

import static com.cisco.dsb.common.ratelimiter.RateLimitConstants.DENY_CODE;
import static com.cisco.dsb.common.ratelimiter.RateLimitConstants.RATE_LIMIT_CODE;

import com.cisco.dsb.common.dto.RateLimitInfo;
import com.cisco.dsb.common.ratelimiter.RateLimitPolicy.RateLimit.ResponseOptions;
import com.cisco.dsb.common.service.MetricService;
import com.cisco.wx2.ratelimit.RateLimitContext;
import com.cisco.wx2.ratelimit.RateLimiter;
import com.cisco.wx2.ratelimit.policy.Action;
import com.cisco.wx2.ratelimit.policy.Policy;
import com.cisco.wx2.ratelimit.provider.Counter;
import com.cisco.wx2.ratelimit.provider.Permit;
import com.google.common.cache.Cache;
import com.google.common.cache.CacheBuilder;
import com.google.common.cache.RemovalCause;
import com.google.common.cache.RemovalNotification;
import io.fabric8.kubernetes.client.utils.IpAddressMatcher;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;
import javax.annotation.Nonnull;
import javax.annotation.PostConstruct;
import javax.servlet.http.HttpServletRequest;
import lombok.CustomLog;
import lombok.Getter;
import lombok.Setter;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

@Component
@CustomLog
public class DsbRateLimiter extends RateLimiter {
  private MetricService metricService;

  @Autowired
  public DsbRateLimiter(MetricService metricService) {
    this.metricService = metricService;
  }
  // This must be set to identify the userID.
  @Getter @Setter
  private Consumer<MessageMetaData> userIdSetter =
      messageMetaData -> messageMetaData.setUserID(messageMetaData.getRemoteIP());

  @Getter private Map<String, AllowAndDenyList> allowDenyListsMap = new HashMap<>();

  @Getter @Setter @Nonnull
  Map<String, ResponseOptions> ratePolicyToResponseOptionsMap = new HashMap<>();

  // This cache holds the global PROCESS permit and the individual USER permits
  // keyed on REMOTE IP. A Permit is an aggregate of all Counters for a given
  // REMOTE IP.
  private Cache<String, Permit> permitCache;

  // This cache holds the individual counters that back the RateAction policies,
  // keyed on the String
  // {REMOTE IP}|{Policy ID}.
  private Cache<String, Counter> counterCache;
  // required to store CIRDUtilObjects per cidr in all/deny lists.
  @Getter private static Map<String, IpAddressMatcher> allowCIDRMap = new ConcurrentHashMap<>();

  @Getter private static Map<String, IpAddressMatcher> denyCIDRMap = new ConcurrentHashMap<>();

  @PostConstruct
  protected void init() {
    this.permitCache = makePermitCache();
    this.counterCache = makeCounterCache();
  }

  public void setPolicies(List<Policy> policies) {
    removePolicies();
    if (policies == null || policies.isEmpty()) {
      return;
    }
    for (Policy policy : policies) {
      logger.info("Adding policy {}", policy);
      addPolicy(policy);
    }
  }

  public void setPolicy(Policy policy) {
    if (policy == null) {
      return;
    }
    removePolicy(policy.getName());
    logger.info("Adding policy {}", policy);
    addPolicy(policy);
  }

  public Cache<String, Permit> getPermitCache() {
    return permitCache;
  }

  public Cache<String, Counter> getCounterCache() {
    return counterCache;
  }

  @Override
  public RateLimitContext createContext(HttpServletRequest request) {
    // TODO: Auto-generated method. Do nothing.
    return null;
  }

  public void setAllowDenyListsMap(@Nonnull Map<String, AllowAndDenyList> allowDenyListsMap) {
    this.allowDenyListsMap = allowDenyListsMap;
    addAllowCIDRObjects(allowDenyListsMap);
    addDenyCIDRObjects(allowDenyListsMap);
  }

  public void evaluateDsbContext(DsbRateLimitContext context) {
    try {
      Action.Enforcement enforcement = evaluate(context);
      boolean pass = enforcement.isPass() || !enforcement.getPolicy().enforce();
      String policyName = null;
      if (enforcement.getPolicy()
          != null) { // enforcement.getPolicy() will be null if messages passes all configured
        // policies
        policyName = enforcement.getPolicy().getName();
      }
      if (pass) {
        logger.debug(
            "Rate limit enforcement result - pass: {} for key: {}, policy: {}, limit: {}, reset: {}",
            true,
            context.getId(),
            policyName,
            enforcement.getLimit(),
            enforcement.getReset());
      } else {
        logger.error(
            "Rate limit enforcement result - pass: {} for key: {}, policy: {}, limit: {}, reset: {}",
            false,
            context.getId(),
            policyName,
            enforcement.getLimit(),
            enforcement.getReset());
        RateLimitInfo rateLimitInfo =
            RateLimitInfo.builder()
                .remoteIP(context.getRemoteIP())
                .localIP(context.getLocalIP())
                .policyName(policyName)
                .isRequest(context.isRequest())
                .action(
                    enforcement.getCode() == DENY_CODE
                        ? RateLimitInfo.Action.DENY
                        : RateLimitInfo.Action.RATE_LIMIT)
                .build();
        metricService.updateRateLimiterInfo(rateLimitInfo);
        if (enforcement.getCode() == RATE_LIMIT_CODE
            && context.isRequest) { // The message has been rate-limited.
          context.setResponseOptions(ratePolicyToResponseOptionsMap.get(policyName));
        }
      }
      context.setPass(pass);
    } catch (RuntimeException e) {
      logger.warn("Unable to evaluate rate limiting", e);
    }
  }

  private Cache<String, Permit> makePermitCache() {
    // In Minutes
    int permitCacheExpiryTime = 5;
    int permitCacheMaxSize = 2000;
    return CacheBuilder.newBuilder()
        .expireAfterWrite(permitCacheExpiryTime, TimeUnit.MINUTES)
        .maximumSize(permitCacheMaxSize)
        .removalListener(
            (RemovalNotification<String, Permit> removal) -> {
              if (removal.getCause() != RemovalCause.REPLACED) {
                removal.getValue().removeCounters();
              }
            })
        .build();
  }

  private Cache<String, Counter> makeCounterCache() {
    // In Minutes
    int counterCacheExpiryTime = 5;
    int counterCacheMaxSize = 2000; // Can be made configurable later
    return CacheBuilder.newBuilder()
        .expireAfterWrite(counterCacheExpiryTime, TimeUnit.MINUTES)
        .maximumSize(counterCacheMaxSize)
        .build();
  }

  private void addAllowCIDRObjects(Map<String, AllowAndDenyList> allowDenyListsMap) {
    allowCIDRMap.clear();
    allowDenyListsMap
        .values()
        .forEach(
            value -> {
              if (value.getAllowIPRangeList() == null) {
                return;
              }
              value
                  .getAllowIPRangeList()
                  .forEach(cidr -> allowCIDRMap.put(cidr, new IpAddressMatcher(cidr)));
            });
  }

  private void addDenyCIDRObjects(Map<String, AllowAndDenyList> allowDenyListsMap) {
    denyCIDRMap.clear();
    allowDenyListsMap
        .values()
        .forEach(
            value -> {
              if (value.getDenyIPRangeList() == null) {
                return;
              }
              value
                  .getDenyIPRangeList()
                  .forEach(cidr -> denyCIDRMap.put(cidr, new IpAddressMatcher(cidr)));
            });
  }
}
