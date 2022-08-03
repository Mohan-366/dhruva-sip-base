package com.cisco.dhruva.application.calltype;

import com.cisco.dhruva.application.CallingAppConfigurationProperty;
import com.cisco.dhruva.normalisation.callTypeNormalization.DialInB2BToCallingCoreNorm;
import com.cisco.dsb.common.normalization.Normalization;
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
  private Normalization normalization;

  @Autowired
  public DialInB2B(
      TrunkManager trunkManager,
      CallingAppConfigurationProperty configurationProperty,
      DialInB2BToCallingCoreNorm dialInB2BToCallingCoreNorm) {
    this.trunkManager = trunkManager;
    this.configurationProperty = configurationProperty;
    this.normalization = dialInB2BToCallingCoreNorm;
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

  public Normalization getNormalization() {
    return normalization;
  }
}
