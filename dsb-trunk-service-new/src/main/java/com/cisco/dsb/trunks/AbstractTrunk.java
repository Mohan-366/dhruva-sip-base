package com.cisco.dsb.trunks;

import com.cisco.dsb.common.loadbalancer.LBType;
import com.cisco.dsb.common.loadbalancer.LoadBalancable;
import com.cisco.dsb.common.servergroup.ServerGroup;
import java.util.ArrayList;
import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.apache.commons.lang3.builder.EqualsBuilder;

/** Abstract class for all kinds of trunks */
@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
public abstract class AbstractTrunk implements LoadBalancable {
  public enum Type {
    PSTN,
    BEECH,
    Calling_Core
  }

  private String name;
  private Ingress ingress;
  private Egress egress;

  @Override
  public boolean equals(Object a) {
    if (a instanceof AbstractTrunk) {
      AbstractTrunk b = (AbstractTrunk) a;
      return new EqualsBuilder()
          .append(this.name, b.name)
          .append(this.ingress, b.ingress)
          .append(this.egress, b.egress)
          .isEquals();
    }
    return false;
  }

  @Override
  public List<ServerGroup> getElements() {
    return new ArrayList<>(egress.getServerGroupMap().values());
  }

  @Override
  public LBType getLbType() {
    return egress.getLbType();
  }
}
