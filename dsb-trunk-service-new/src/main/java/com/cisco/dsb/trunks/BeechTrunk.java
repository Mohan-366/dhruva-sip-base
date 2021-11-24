package com.cisco.dsb.trunks;

import com.cisco.dsb.proxy.messaging.ProxySIPRequest;

public class BeechTrunk extends AbstractTrunk implements TrunkPluginInterface {

  @Override
  public boolean supports(Type type) {
    return type == Type.BEECH;
  }

  @Override
  public ProxySIPRequest handleIngress(ProxySIPRequest proxySIPRequest) {
    return proxySIPRequest;
  }

  @Override
  public ProxySIPRequest handleEgress(ProxySIPRequest proxySIPRequest) {
    return proxySIPRequest;
  }
}
