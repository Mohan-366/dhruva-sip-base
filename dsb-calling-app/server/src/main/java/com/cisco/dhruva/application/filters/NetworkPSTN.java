package com.cisco.dhruva.application.filters;

import com.cisco.dhruva.application.CallingAppConfigurationProperty;
import com.cisco.dsb.proxy.messaging.ProxySIPRequest;
import java.util.function.Predicate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

@Component
public class NetworkPSTN extends FilterNode {
  private CallingAppConfigurationProperty configurationProperty;

  @Autowired
  public void setConfigurationProperty(
      CallingAppConfigurationProperty callingAppConfigurationProperty) {
    this.configurationProperty = callingAppConfigurationProperty;
  }

  NetworkPSTN() {
    super(new FilterId(FilterId.Id.NETWORK_PSTN));
  }

  @Override
  public Predicate<ProxySIPRequest> filter() {
    return proxySIPRequest ->
        proxySIPRequest.getNetwork().equalsIgnoreCase(configurationProperty.getNetworkPSTN());
  }
}
