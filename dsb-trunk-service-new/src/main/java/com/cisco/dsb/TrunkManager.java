package com.cisco.dsb;

import com.cisco.dsb.common.exception.DhruvaRuntimeException;
import com.cisco.dsb.proxy.messaging.ProxySIPRequest;
import com.cisco.dsb.trunks.AbstractTrunk;
import com.cisco.dsb.trunks.TrunkPluginInterface;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.plugin.core.PluginRegistry;
import org.springframework.plugin.core.SimplePluginRegistry;
import org.springframework.plugin.core.config.EnablePluginRegistries;
import org.springframework.stereotype.Service;

@Service
@EnablePluginRegistries(TrunkPluginInterface.class)
public class TrunkManager {
  private final PluginRegistry<TrunkPluginInterface, AbstractTrunk.Type> registry;
  private final TrunkConfigurationProperties configurationProperties;

  @Autowired
  public TrunkManager(TrunkConfigurationProperties configurationProperties) {
    this.configurationProperties = configurationProperties;
    this.registry =
        SimplePluginRegistry.of(
            configurationProperties.getBeechTrunk(),
            configurationProperties.getCallingTrunk(),
            configurationProperties.getPstnTrunks());
  }

  public ProxySIPRequest handleIngress(AbstractTrunk.Type type, ProxySIPRequest proxySIPRequest) {
    return this.registry
        .getPluginFor(type, () -> new DhruvaRuntimeException("Invalid Trunk Type"))
        .handleIngress(proxySIPRequest);
  }

  public ProxySIPRequest handleEgress(AbstractTrunk.Type type, ProxySIPRequest proxySIPRequest) {
    return this.registry
        .getPluginFor(type, () -> new DhruvaRuntimeException("Invalid Trunk Type"))
        .handleEgress(proxySIPRequest);
  }
}
