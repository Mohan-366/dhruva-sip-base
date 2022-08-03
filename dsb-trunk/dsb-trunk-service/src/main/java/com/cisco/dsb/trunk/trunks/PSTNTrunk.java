package com.cisco.dsb.trunk.trunks;

import com.cisco.dsb.common.normalization.Normalization;
import com.cisco.dsb.proxy.messaging.ProxySIPRequest;
import com.cisco.dsb.proxy.messaging.ProxySIPResponse;
import lombok.Builder;
import lombok.NoArgsConstructor;
import lombok.NonNull;
import reactor.core.publisher.Mono;

@NoArgsConstructor
public class PSTNTrunk extends AbstractTrunk {

  @Builder(setterPrefix = "set")
  public PSTNTrunk(String name, Ingress ingress, Egress egress, boolean enableCircuitBreaker) {
    super(name, ingress, egress, enableCircuitBreaker);
  }

  @Override
  public ProxySIPRequest processIngress(
      ProxySIPRequest proxySIPRequest, @NonNull Normalization normalization) {
    normalization.ingressNormalize().accept(proxySIPRequest);
    return proxySIPRequest;
  }

  @Override
  public Mono<ProxySIPResponse> processEgress(
      ProxySIPRequest proxySIPRequest, @NonNull Normalization normalization) {
    normalization.egressPreNormalize().accept(proxySIPRequest);
    normalization.setNormForFutureResponse().accept(proxySIPRequest);
    return sendToProxy(proxySIPRequest, normalization);
  }

  @Override
  protected boolean enableRedirection() {
    return false;
  }
}
