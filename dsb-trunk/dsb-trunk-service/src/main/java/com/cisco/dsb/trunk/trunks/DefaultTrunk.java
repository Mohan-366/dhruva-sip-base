package com.cisco.dsb.trunk.trunks;

import com.cisco.dsb.proxy.messaging.ProxySIPRequest;
import com.cisco.dsb.proxy.messaging.ProxySIPResponse;
import com.cisco.dsb.trunk.trunks.AbstractTrunk.TrunkCookie;
import reactor.core.publisher.Mono;

public class DefaultTrunk extends AbstractTrunk {

  @Override
  public ProxySIPRequest processIngress(ProxySIPRequest proxySIPRequest) {
    return proxySIPRequest;
  }

  @Override
  public Mono<ProxySIPResponse> processEgress(ProxySIPRequest proxySIPRequest) {
    return sendToProxy(proxySIPRequest);
  }

  @Override
  protected void doPostRouteNorm(TrunkCookie cookie) {}

  @Override
  protected boolean enableRedirection() {
    return true;
  }

  @Override
  protected void applyEgressNorm(ProxySIPRequest proxySIPRequest) {}
}
