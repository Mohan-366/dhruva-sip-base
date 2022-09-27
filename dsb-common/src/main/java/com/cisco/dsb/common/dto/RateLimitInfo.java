package com.cisco.dsb.common.dto;

import lombok.Builder;
import lombok.CustomLog;
import lombok.Data;
import org.apache.commons.lang3.builder.EqualsBuilder;

@CustomLog
@Data
@Builder
public class RateLimitInfo {
  String remoteIP;
  String localIP;
  String policyName;
  boolean isRequest;
  Action action;

  @Override
  public boolean equals(Object obj) {
    if (this == obj) {
      return true;
    }
    if (obj instanceof RateLimitInfo) {
      RateLimitInfo rateLimitInfo = (RateLimitInfo) obj;
      return new EqualsBuilder()
          .append(rateLimitInfo.remoteIP, this.remoteIP)
          .append(rateLimitInfo.localIP, this.localIP)
          .append(rateLimitInfo.policyName, this.policyName)
          .append(rateLimitInfo.isRequest, this.isRequest)
          .append(rateLimitInfo.action, this.action)
          .isEquals();
    }
    return false;
  }

  public enum Action {
    DENY,
    RATE_LIMIT
  }
}
