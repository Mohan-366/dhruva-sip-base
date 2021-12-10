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
public class DialInB2B implements CallType {
  private TrunkManager trunkManager;
  private CallingAppConfigurationProperty configurationProperty;

  @Autowired
  public DialInB2B(
      TrunkManager trunkManager, CallingAppConfigurationProperty configurationProperty) {
    this.trunkManager = trunkManager;
    this.configurationProperty = configurationProperty;
  }

  @Override
  public TrunkType getIngressTrunk() {
    return TrunkType.B2B;
  }

  @Override
  public TrunkType getEgressTrunk() {
    return TrunkType.Calling_Core;
  }

  @Override
  public String getIngressKey() {
    // TODO- move this logic to TrunkManger once filter for ingress is implemented
    return configurationProperty.getB2bEgress();
  }

  @Override
  public String getEgressKey(ProxySIPRequest proxySIPRequest) {
    return configurationProperty.getCallingEgress();
  }

  @Override
  public TrunkManager getTrunkManager() {
    return trunkManager;
  }
}
