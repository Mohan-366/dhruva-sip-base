package com.cisco.dsb.trunks;

import com.cisco.dsb.proxy.messaging.ProxySIPRequest;
import lombok.Builder;
import lombok.NoArgsConstructor;

@NoArgsConstructor
public class PSTNTrunk extends AbstractTrunk implements TrunkPluginInterface {
  @Builder(setterPrefix = "set")
  public PSTNTrunk(String name, Ingress ingress, Egress egress) {
    super(name, ingress, egress);
  }

  @Override
  public ProxySIPRequest handleIngress(ProxySIPRequest proxySIPRequest) {
    return null;
  }

  @Override
  public ProxySIPRequest handleEgress(ProxySIPRequest proxySIPRequest) {
    return null;
  }

  @Override
  public boolean supports(Type type) {
    return false;
    // return type.getT1() == AbstractTrunk.Type.PSTN && trunkMap.contains(type.getT2());
  }
}
