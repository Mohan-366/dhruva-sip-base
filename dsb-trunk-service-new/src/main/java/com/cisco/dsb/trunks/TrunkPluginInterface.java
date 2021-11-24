package com.cisco.dsb.trunks;

import com.cisco.dsb.proxy.messaging.ProxySIPRequest;
import org.springframework.plugin.core.Plugin;

public interface TrunkPluginInterface extends Plugin<AbstractTrunk.Type> {
  ProxySIPRequest handleIngress(ProxySIPRequest proxySIPRequest);

  ProxySIPRequest handleEgress(ProxySIPRequest proxySIPRequest);
}
