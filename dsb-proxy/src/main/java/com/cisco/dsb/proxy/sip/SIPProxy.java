package com.cisco.dsb.proxy.sip;

import com.cisco.dsb.proxy.ProxyConfigurationProperties;
import lombok.*;
import org.apache.commons.lang3.builder.EqualsBuilder;
import org.apache.commons.lang3.builder.HashCodeBuilder;

@Getter
@Setter
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class SIPProxy {

  @Builder.Default
  private boolean errorAggregator =
      ProxyConfigurationProperties.DEFAULT_PROXY_ERROR_AGGREGATOR_ENABLED;

  @Builder.Default
  private boolean createDNSServerGroup =
      ProxyConfigurationProperties.DEFAULT_PROXY_CREATE_DNSSERVERGROUP_ENABLED;

  @Builder.Default
  private boolean processRouteHeader =
      ProxyConfigurationProperties.DEFAULT_PROXY_PROCESS_ROUTE_HEADER_ENABLED;

  @Builder.Default
  private boolean processRegisterRequest =
      ProxyConfigurationProperties.DEFAULT_PROXY_PROCESS_REGISTER_REQUEST;

  @Builder.Default
  private long timerCIntervalInMilliSec =
      ProxyConfigurationProperties.DEFAULT_TIMER_C_DURATION_MILLISEC;

  public String toString() {
    return new StringBuilder("SIPProxy isErrorAggregatorEnabled = ")
        .append(errorAggregator)
        .append(" isCreateDNSServergroupEnabled = ")
        .append(createDNSServerGroup)
        .append(" isProcessRouteHeaderEnabled = ")
        .append(processRouteHeader)
        .append(" isProcessRegisterRequest = ")
        .append(processRegisterRequest)
        .append(" timerCIntervalInMilliSec = ")
        .append(timerCIntervalInMilliSec)
        .toString();
  }

  @Override
  public boolean equals(Object other) {
    if (this == other) return true;
    if (other instanceof SIPProxy) {
      SIPProxy otherProxy = (SIPProxy) other;
      return new EqualsBuilder()
          .append(errorAggregator, otherProxy.isErrorAggregator())
          .append(createDNSServerGroup, otherProxy.isCreateDNSServerGroup())
          .append(processRouteHeader, otherProxy.isProcessRouteHeader())
          .append(processRegisterRequest, otherProxy.isProcessRegisterRequest())
          .append(timerCIntervalInMilliSec, otherProxy.getTimerCIntervalInMilliSec())
          .isEquals();
    }
    return false;
  }

  @Override
  public int hashCode() {
    return new HashCodeBuilder()
        .append(errorAggregator)
        .append(createDNSServerGroup)
        .append(processRouteHeader)
        .append(processRegisterRequest)
        .append(timerCIntervalInMilliSec)
        .toHashCode();
  }
}
