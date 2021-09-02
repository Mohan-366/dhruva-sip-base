package com.cisco.dsb.common.sip.bean;

import com.cisco.dsb.common.config.sip.DhruvaSIPConfigProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Getter;
import org.apache.commons.lang3.builder.EqualsBuilder;
import org.apache.commons.lang3.builder.HashCodeBuilder;

public class SIPProxy {

  @Getter private boolean errorAggregator;
  @Getter private boolean createDNSServerGroup;
  @Getter private boolean processRouteHeader;
  @Getter private boolean processRegisterRequest;
  @Getter private long timerCIntervalInMilliSec;

  private SIPProxy(SIPProxyBuilder proxyBuilder) {
    this.errorAggregator = proxyBuilder.errorAggregator;
    this.createDNSServerGroup = proxyBuilder.createDNSServerGroup;
    this.processRouteHeader = proxyBuilder.processRouteHeader;
    this.processRegisterRequest = proxyBuilder.processRegisterRequest;
    this.timerCIntervalInMilliSec = proxyBuilder.timerCIntervalInMilliSec;
  }

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
    if (other == null || other.getClass() != this.getClass()) {
      return false;
    }

    SIPProxy otherProxy = (SIPProxy) other;
    return new EqualsBuilder()
        .append(errorAggregator, otherProxy.isErrorAggregator())
        .append(createDNSServerGroup, otherProxy.isCreateDNSServerGroup())
        .append(processRouteHeader, otherProxy.isProcessRouteHeader())
        .append(processRegisterRequest, otherProxy.isProcessRegisterRequest())
        .append(timerCIntervalInMilliSec, otherProxy.getTimerCIntervalInMilliSec())
        .isEquals();
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

  public static class SIPProxyBuilder {
    @JsonProperty private boolean errorAggregator;

    @JsonProperty private boolean createDNSServerGroup;

    @JsonProperty private boolean processRouteHeader;

    @JsonProperty private boolean processRegisterRequest;

    @JsonProperty private long timerCIntervalInMilliSec;

    public SIPProxyBuilder() {
      this.errorAggregator = DhruvaSIPConfigProperties.DEFAULT_PROXY_ERROR_AGGREGATOR_ENABLED;
      this.createDNSServerGroup =
          DhruvaSIPConfigProperties.DEFAULT_PROXY_CREATE_DNSSERVERGROUP_ENABLED;
      this.processRouteHeader =
          DhruvaSIPConfigProperties.DEFAULT_PROXY_PROCESS_ROUTE_HEADER_ENABLED;
      this.processRegisterRequest =
          DhruvaSIPConfigProperties.DEFAULT_PROXY_PROCESS_REGISTER_REQUEST;
      this.timerCIntervalInMilliSec = DhruvaSIPConfigProperties.DEFAULT_TIMER_C_DURATION_MILLISEC;
    }

    public SIPProxyBuilder setErrorAggregator(boolean errorAggregator) {
      this.errorAggregator = errorAggregator;
      return this;
    }

    public SIPProxyBuilder setCreateDNSServergroup(boolean createDNSServerGroup) {
      this.createDNSServerGroup = createDNSServerGroup;
      return this;
    }

    public SIPProxyBuilder setProcessRouteHeader(boolean processRouteHeader) {
      this.processRouteHeader = processRouteHeader;
      return this;
    }

    public SIPProxyBuilder setProcessRegisterRequest(boolean processRegisterRequest) {
      this.processRegisterRequest = processRegisterRequest;
      return this;
    }

    public SIPProxyBuilder setTimerCIntervalInMilliSec(long timerCIntervalInMilliSec) {
      this.timerCIntervalInMilliSec = timerCIntervalInMilliSec;
      return this;
    }

    public SIPProxy build() {
      return new SIPProxy(this);
    }
  }
}
