package com.cisco.dhruva.application.filters;

import com.cisco.dhruva.application.SIPConfig;
import com.cisco.dsb.proxy.messaging.ProxySIPRequest;
import java.util.function.Predicate;

public class NetworkWxC extends FilterNode {
  NetworkWxC() {
    super(new FilterId(FilterId.Id.NETWORK_WXC));
  }

  @Override
  public Predicate<ProxySIPRequest> filter() {
    return proxySIPRequest ->
        proxySIPRequest.getNetwork().equalsIgnoreCase(SIPConfig.NETWORK_CALLING_CORE);
  }
}
