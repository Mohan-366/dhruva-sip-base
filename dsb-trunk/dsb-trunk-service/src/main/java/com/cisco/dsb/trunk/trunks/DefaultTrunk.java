package com.cisco.dsb.trunk.trunks;

import com.cisco.dsb.common.maintanence.Maintenance;
import com.cisco.dsb.common.normalization.Normalization;
import com.cisco.dsb.proxy.messaging.ProxySIPRequest;
import com.cisco.dsb.proxy.messaging.ProxySIPResponse;
import com.cisco.dsb.trunk.MaintenanceMode;
import com.cisco.dsb.trunk.TrunkConfigurationProperties;
import lombok.NonNull;
import reactor.core.publisher.Mono;

public class DefaultTrunk extends AbstractTrunk {

  @Override
  public ProxySIPRequest processIngress(
      ProxySIPRequest proxySIPRequest,
      @NonNull Normalization normalization,
      Maintenance maintenance,
      TrunkConfigurationProperties configurationProperties) {
    MaintenanceMode maintenanceMode =
        getMaintenanceMode().apply(maintenance, configurationProperties);
    if (maintenanceMode.isInMaintenanceMode().test(proxySIPRequest)) {
      return maintenanceMode.maintenanceBehaviour().apply(proxySIPRequest);
    }
    normalization.ingressNormalize().accept(proxySIPRequest);
    return proxySIPRequest;
  }

  @Override
  public Mono<ProxySIPResponse> processEgress(
      ProxySIPRequest proxySIPRequest, @NonNull Normalization normalization) {
    return sendToProxy(proxySIPRequest, normalization);
  }

  @Override
  protected boolean enableRedirection() {
    return true;
  }
}
