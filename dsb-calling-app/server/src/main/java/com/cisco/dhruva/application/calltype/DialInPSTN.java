package com.cisco.dhruva.application.calltype;

import com.cisco.dhruva.application.CallingAppConfigurationProperty;
import com.cisco.dhruva.normalisation.callTypeNormalization.DialInPSTNToB2BNorm;
import com.cisco.dsb.common.normalization.Normalization;
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
  private Normalization normalization;

  @Autowired
  public DialInPSTN(
      TrunkManager trunkManager,
      CallingAppConfigurationProperty configurationProperty,
      DialInPSTNToB2BNorm dialInPSTNToB2BNorm) {
    this.trunkManager = trunkManager;
    this.configurationProperty = configurationProperty;
    this.normalization = dialInPSTNToB2BNorm;
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

  @Override
  public Normalization getNormalization() {
    return normalization;
  }
}
