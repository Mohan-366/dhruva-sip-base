package com.cisco.dsb.trunk.trunks;

import com.cisco.dsb.common.loadbalancer.LBType;
import com.cisco.dsb.common.servergroup.ServerGroup;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import lombok.Getter;
import lombok.Setter;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.builder.EqualsBuilder;

public class Egress {
  @Getter private LBType lbType;
  @Getter private List<String> serverGroupsConfig;
  @Getter private final Map<String, ServerGroup> serverGroupMap = new HashMap<>();
  @Getter @Setter private int overallResponseTimeout = 300;

  public void setServerGroups(List<String> serverGroups) {
    this.serverGroupsConfig = serverGroups;
  }

  public void setLbType(LBType lbType) {
    this.lbType = lbType;
  }

  @Override
  public boolean equals(Object a) {
    if (a instanceof Egress) {
      Egress b = (Egress) a;
      return new EqualsBuilder()
          .append(this.lbType, b.lbType)
          .append(this.serverGroupsConfig, b.serverGroupsConfig)
          .append(this.serverGroupMap, b.serverGroupMap)
          .isEquals();
    }
    return false;
  }

  @Override
  public String toString() {
    return StringUtils.join(serverGroupsConfig);
  }
}