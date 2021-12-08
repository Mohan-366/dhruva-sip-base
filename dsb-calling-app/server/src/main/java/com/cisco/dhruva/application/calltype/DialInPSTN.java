package com.cisco.dhruva.application.calltype;

import com.cisco.dhruva.application.CallingAppConfigurationProperty;
import com.cisco.dsb.proxy.messaging.ProxySIPRequest;
import com.cisco.dsb.trunk.TrunkManager;
import com.cisco.dsb.trunk.trunks.TrunkType;
import lombok.CustomLog;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

@CustomLog
@Component
public class DialInPSTN implements CallType {

  private TrunkManager trunkManager;
  private CallingAppConfigurationProperty configurationProperty;

  @Autowired
  public DialInPSTN(
      TrunkManager trunkManager, CallingAppConfigurationProperty configurationProperty) {
    this.trunkManager = trunkManager;
    this.configurationProperty = configurationProperty;
  }

  @Override
  public TrunkType getIngressTrunk() {
    return TrunkType.PSTN;
  }

  @Override
  public TrunkType getEgressTrunk() {
    return TrunkType.B2B;
  }

  @Override
  public String getIngressKey() {
    return configurationProperty.getPstnIngress();
  }

  @Override
  public String getEgressKey(ProxySIPRequest proxySIPRequest) {
    return configurationProperty.getB2bEgress();
  }

  @Override
  public TrunkManager getTrunkManager() {
    return trunkManager;
  }
}
