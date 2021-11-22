package com.cisco.dsb.common.loadbalancer;

import com.cisco.dsb.common.messaging.models.AbstractSipRequest;

public class LBFactory {
  public static LoadBalancer get(LoadBalancable loadBalancable, AbstractSipRequest request) {
    LoadBalancer loadBalancer = LoadBalancer.of(loadBalancable);
    switch (loadBalancable.getLbType()) {
      case MS_ID: // no implementation for Key of MSID call
      case WEIGHT:
      case HUNT:
      case ONCE:
      case HIGHEST_Q:
        loadBalancer.setKey(null);
        break;
    }
    return loadBalancer;
  }
}
