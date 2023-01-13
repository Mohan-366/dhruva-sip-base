package com.cisco.dsb.trunk.trunks;

import com.cisco.dsb.common.maintanence.Maintenance;
import com.cisco.dsb.common.normalization.Normalization;
import com.cisco.dsb.proxy.messaging.ProxySIPRequest;
import com.cisco.dsb.proxy.messaging.ProxySIPResponse;
import com.cisco.dsb.trunk.MaintenanceMode;
import com.cisco.dsb.trunk.TrunkConfigurationProperties;
import reactor.core.publisher.Mono;

public class B2BTrunk extends AbstractTrunk {

  public B2BTrunk() {}

  public B2BTrunk(B2BTrunk b2BTrunk) {
    super(b2BTrunk);
  }

  // dummy implementation, can't create property binding for abstract class
  @Override
  public ProxySIPRequest processIngress(
      ProxySIPRequest proxySIPRequest,
      Normalization normalization,
      Maintenance maintenance,
      TrunkConfigurationProperties configurationProperties) {
    MaintenanceMode maintenanceMode =
        getMaintenanceMode().apply(maintenance, configurationProperties);
    if (maintenanceMode.isInMaintenanceMode().test(proxySIPRequest)) {
      return maintenanceMode.maintenanceBehaviour().apply(proxySIPRequest);
    }
    return null;
  }

  @Override
  public Mono<ProxySIPResponse> processEgress(
      ProxySIPRequest proxySIPRequest, Normalization normalization) {
    return null;
  }

  @Override
  protected boolean enableRedirection() {
    return true;
  }
}
