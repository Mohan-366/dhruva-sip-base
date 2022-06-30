package com.cisco.dsb.trunk.trunks;

import com.cisco.dsb.common.normalization.Normalization;
import com.cisco.dsb.proxy.messaging.ProxySIPRequest;
import com.cisco.dsb.proxy.messaging.ProxySIPResponse;
import reactor.core.publisher.Mono;

public class DefaultTrunk extends AbstractTrunk {

  @Override
  public ProxySIPRequest processIngress(ProxySIPRequest proxySIPRequest) {
    return proxySIPRequest;
  }

  @Override
  public Mono<ProxySIPResponse> processEgress(
      ProxySIPRequest proxySIPRequest, Normalization normalization) {
    return sendToProxy(proxySIPRequest, normalization);
  }

  @Override
  protected boolean enableRedirection() {
    return true;
  }
}
