package com.cisco.dsb.trunks;

import com.cisco.dsb.proxy.messaging.ProxySIPRequest;
import java.util.Map;
import lombok.Getter;
import lombok.Setter;

public class PSTNTrunks implements TrunkPluginInterface {
  @Setter @Getter private Map<String, PSTNTrunk> trunkMap;

  @Override
  public ProxySIPRequest handleIngress(ProxySIPRequest proxySIPRequest) {
    return proxySIPRequest;
  }

  @Override
  public ProxySIPRequest handleEgress(ProxySIPRequest proxySIPRequest) {
    return proxySIPRequest;
  }

  @Override
  public boolean supports(AbstractTrunk.Type type) {
    return type == AbstractTrunk.Type.PSTN;
    // return type.getT1() == AbstractTrunk.Type.PSTN && trunkMap.contains(type.getT2())
  }

  @Override
  public boolean equals(Object a) {
    if (a instanceof PSTNTrunks) return this.trunkMap.equals(((PSTNTrunks) a).getTrunkMap());
    return false;
  }
}
