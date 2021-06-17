package com.cisco.dhruva.sip.bean;

import com.cisco.dhruva.config.sip.DhruvaSIPConfigProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import org.apache.commons.lang3.builder.EqualsBuilder;
import org.apache.commons.lang3.builder.HashCodeBuilder;

public class SIPProxy {

  private boolean errorAggregrator;
  private boolean createDNSServerGroup;
  private boolean processRouteHeader;

  private SIPProxy(SIPProxyBuilder proxyBuilder) {
    this.errorAggregrator = proxyBuilder.errorAggregrator;
    this.createDNSServerGroup = proxyBuilder.createDNSServerGroup;
    this.processRouteHeader = proxyBuilder.processRouteHeader;
  }

  public boolean isErrorAggregratorEnabled() {
    return errorAggregrator;
  }

  public boolean isCreateDNSServergroupEnabled() {
    return createDNSServerGroup;
  }

  public boolean isprocessRouteHeaderEnabled() {
    return processRouteHeader;
  }

  public String toString() {
    return new StringBuilder("SIPProxy isErrorAggregratorEnabled = ")
        .append(errorAggregrator)
        .append(" isCreateDNSServergroupEnabled = ")
        .append(createDNSServerGroup)
        .append("isprocessRouteHeaderEnabled")
        .append(processRouteHeader)
        .toString();
  }

  @Override
  public boolean equals(Object other) {
    if (other == null || other.getClass() != this.getClass()) {
      return false;
    }

    SIPProxy otherProxy = (SIPProxy) other;
    return new EqualsBuilder()
        .append(errorAggregrator, otherProxy.isErrorAggregratorEnabled())
        .append(createDNSServerGroup, otherProxy.isCreateDNSServergroupEnabled())
        .append(processRouteHeader, otherProxy.isprocessRouteHeaderEnabled())
        .isEquals();
  }

  @Override
  public int hashCode() {
    return new HashCodeBuilder().append(errorAggregrator).append(createDNSServerGroup).toHashCode();
  }

  public static class SIPProxyBuilder {
    @JsonProperty private boolean errorAggregrator;

    @JsonProperty private boolean createDNSServerGroup;

    @JsonProperty private boolean processRouteHeader;

    public SIPProxyBuilder() {
      this.errorAggregrator = DhruvaSIPConfigProperties.DEFAULT_PROXY_ERROR_AGGREGATOR_ENABLED;
      this.createDNSServerGroup =
          DhruvaSIPConfigProperties.DEFAULT_PROXY_CREATE_DNSSERVERGROUP_ENABLED;
      this.processRouteHeader =
          DhruvaSIPConfigProperties.DEFAULT_PROXY_PROCESS_ROUTE_HEADER_ENABLED;
    }

    public SIPProxyBuilder setErrorAggregrator(boolean errorAggregrator) {
      this.errorAggregrator = errorAggregrator;
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

    public SIPProxy build() {
      return new SIPProxy(this);
    }
  }
}
