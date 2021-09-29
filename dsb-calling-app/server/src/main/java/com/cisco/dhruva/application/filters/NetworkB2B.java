package com.cisco.dhruva.application.filters;

import com.cisco.dhruva.application.SIPConfig;
import com.cisco.dsb.proxy.messaging.ProxySIPRequest;
import java.util.function.Predicate;

public class NetworkB2B extends FilterNode {
  NetworkB2B() {
    super(new FilterId(FilterId.Id.NETWORK_B2B));
  }

  @Override
  public Predicate<ProxySIPRequest> filter() {
    return proxySIPRequest -> proxySIPRequest.getNetwork().equalsIgnoreCase(SIPConfig.NETWORK_B2B);
  }
}
