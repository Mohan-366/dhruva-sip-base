package com.cisco.dsb.common.dto;

import lombok.Builder;
import lombok.CustomLog;
import lombok.Data;
import lombok.EqualsAndHashCode;

@CustomLog
@Data
@Builder
@EqualsAndHashCode
public class RateLimitInfo {
  String remoteIP;
  String localIP;
  String policyName;
  boolean isRequest;
  Action action;

  public enum Action {
    DENY,
    RATE_LIMIT
  }
}
