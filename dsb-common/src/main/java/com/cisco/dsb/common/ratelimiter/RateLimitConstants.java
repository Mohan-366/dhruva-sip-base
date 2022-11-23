package com.cisco.dsb.common.ratelimiter;

public class RateLimitConstants {
  public static final String ALLOW_IP_LIST_POLICY = "allowIPListPolicy";
  public static final String DENY_IP_LIST_POLICY = "denyIPListPolicy";
  public static final String PROCESS = "PROCESS";
  public static final String ALL = "all";
  public static final String NETWORK_LEVEL_POLICY_PREFIX = "networkLevelPolicy";
  public static final String POLICY_VALUE_DELIMITER = "--";
  public static final String UNDERSCORE = "_";
  public static final int DENY_CODE = 429;
  public static final int RATE_LIMIT_CODE = -1;
  public static final String DEFAULT_RATE_LIMITED_RESPONSE_REASON = "Fraud Control";

  private RateLimitConstants() {}
}
