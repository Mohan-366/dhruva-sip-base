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
public class DialOutWxC implements CallType {

  private final CallingAppConfigurationProperty configurationProperty;
  private TrunkManager trunkManager;

  @Autowired
  public DialOutWxC(
      TrunkManager trunkManager, CallingAppConfigurationProperty configurationProperty) {
    this.trunkManager = trunkManager;
    this.configurationProperty = configurationProperty;
  }

  @Override
  public TrunkType getIngressTrunk() {
    return TrunkType.Calling_Core;
  }

  @Override
  public TrunkType getEgressTrunk() {
    return TrunkType.B2B;
  }

  @Override
  public String getIngressKey() {
    // This should be AS, but for now NS and AS have same behavior for ingress
    return configurationProperty.getCallingEgress();
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
