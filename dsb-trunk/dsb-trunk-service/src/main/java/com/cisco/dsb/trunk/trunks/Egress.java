package com.cisco.dsb.trunk.trunks;

import com.cisco.dsb.common.config.RoutePolicy;
import com.cisco.dsb.common.loadbalancer.LBType;
import com.cisco.dsb.common.servergroup.ServerGroup;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import lombok.CustomLog;
import lombok.Getter;
import lombok.Setter;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.builder.EqualsBuilder;
import org.apache.commons.lang3.builder.HashCodeBuilder;

@CustomLog
@Getter
public class Egress {

  private RoutePolicy routePolicy;
  private String routePolicyConfig;

  private LBType lbType = LBType.WEIGHT;
  private List<ServerGroups> serverGroupsConfig;

  private Map<String, String> selector;
  private final Map<String, ServerGroup> serverGroupMap = new HashMap<>();
  @Setter private int overallResponseTimeout = 300;

  public void setServerGroups(List<ServerGroups> serverGroups) {
    this.serverGroupsConfig = serverGroups;
  }

  public void setLbType(LBType lbType) {
    this.lbType = lbType;
  }

  public void setSelector(Map<String, String> selector) {
    this.selector = selector;
  }

  @Override
  public boolean equals(Object a) {
    if (this == a) return true;
    if (a instanceof Egress) {
      Egress b = (Egress) a;
      return new EqualsBuilder()
          .append(this.lbType, b.lbType)
          .append(this.serverGroupsConfig, b.serverGroupsConfig)
          .isEquals();
    }
    return false;
  }

  @Override
  public int hashCode() {
    return new HashCodeBuilder().append(lbType).append(serverGroupsConfig).toHashCode();
  }

  @Override
  public String toString() {
    return StringUtils.join(serverGroupsConfig);
  }

  public void setRoutePolicy(String routePolicyConfig) {
    this.routePolicyConfig = routePolicyConfig;
  }

  public void setRoutePolicyFromConfig(RoutePolicy routePolicy) {
    this.routePolicy = routePolicy;
  }

  public RoutePolicy getRoutePolicy() {
    if (this.routePolicy == null) {
      this.routePolicy =
          RoutePolicy.builder()
              .setName("defaultTrunkRoutePolicy")
              .setFailoverResponseCodes(Arrays.asList(502, 503))
              .build();
      logger.info(
          "RoutePolicy was not configured for trunk: {}. Using default policy: {}",
          this.toString(),
          routePolicy.toString());
    }
    return routePolicy;
  }
}
