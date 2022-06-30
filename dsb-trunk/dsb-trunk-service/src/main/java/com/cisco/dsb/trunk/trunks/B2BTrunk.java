package com.cisco.dsb.trunk.trunks;

import com.cisco.dsb.common.normalization.Normalization;
import com.cisco.dsb.proxy.messaging.ProxySIPRequest;
import com.cisco.dsb.proxy.messaging.ProxySIPResponse;
import reactor.core.publisher.Mono;

public class B2BTrunk extends AbstractTrunk {

  public B2BTrunk() {}

  public B2BTrunk(B2BTrunk b2BTrunk) {
    super(b2BTrunk);
  }

  // dummy implementation, can't create property binding for abstract class
  @Override
  public ProxySIPRequest processIngress(ProxySIPRequest proxySIPRequest) {
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
