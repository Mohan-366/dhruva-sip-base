package com.cisco.dsb.common.ratelimiter;

import com.cisco.wx2.ratelimit.policy.Policy;
import java.util.List;
import java.util.function.Consumer;

public interface RateLimiterConfigurator {

  void configure();
  // set the userIDSetter in dsbRateLimiter
  void setDsbRateLimiterUserIdSetter(Consumer<MessageMetaData> userIdSetter);

  List<Policy> createPolicies();

  void createAllowDenyListMap();
}
