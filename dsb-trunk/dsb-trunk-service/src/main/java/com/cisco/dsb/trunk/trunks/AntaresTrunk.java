package com.cisco.dsb.trunk.trunks;

import com.cisco.dsb.common.normalization.Normalization;
import com.cisco.dsb.proxy.messaging.ProxySIPRequest;
import com.cisco.dsb.proxy.messaging.ProxySIPResponse;
import lombok.NonNull;
import reactor.core.publisher.Mono;

public class AntaresTrunk extends B2BTrunk {

  public AntaresTrunk() {}

  public AntaresTrunk(B2BTrunk b2BTrunk) {
    super(b2BTrunk);
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
}
