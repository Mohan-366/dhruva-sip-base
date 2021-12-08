package com.cisco.dsb.common.loadbalancer;

import java.util.Collection;

/** Any Object that needs to loadbalance it's elements should implement this interface */
public interface LoadBalancable {
  /**
   * Return a list of elements to create a LoadBalancer
   *
   * @return elements to load balance
   */
  Collection<? extends LBElement> getElements();

  /**
   * Returns a type of load balancer to use while creating LoadBalancer
   *
   * @return
   */
  LBType getLbType();
}
