package com.cisco.dsb.trunk;

import com.cisco.dsb.common.exception.DhruvaRuntimeException;
import com.cisco.dsb.proxy.messaging.ProxySIPRequest;
import com.cisco.dsb.proxy.messaging.ProxySIPResponse;
import com.cisco.dsb.trunk.trunks.AbstractTrunk;
import com.cisco.dsb.trunk.trunks.TrunkPluginInterface;
import com.cisco.dsb.trunk.trunks.TrunkPlugins;
import com.cisco.dsb.trunk.trunks.TrunkType;
import lombok.CustomLog;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.plugin.core.PluginRegistry;
import org.springframework.plugin.core.SimplePluginRegistry;
import org.springframework.plugin.core.config.EnablePluginRegistries;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

@Service
@EnablePluginRegistries(TrunkPluginInterface.class)
@CustomLog
public class TrunkManager {
  private final PluginRegistry<TrunkPluginInterface, TrunkType> registry;
  private final TrunkConfigurationProperties configurationProperties;
  private final TrunkPlugins trunkPlugins;

  @Autowired
  public TrunkManager(
      TrunkConfigurationProperties configurationProperties, TrunkPlugins trunkPlugins) {
    this.configurationProperties = configurationProperties;
    this.trunkPlugins = trunkPlugins;
    this.registry =
        SimplePluginRegistry.of(
            trunkPlugins.getB2B(), trunkPlugins.getPSTN(), trunkPlugins.getCalling());
  }

  public ProxySIPRequest handleIngress(
      TrunkType type, ProxySIPRequest proxySIPRequest, String key) {

    AbstractTrunk trunk =
        this.registry
            .getPluginFor(
                type,
                () -> new DhruvaRuntimeException("Trunk Type \"" + type + "\" not registered"))
            .getTrunkMap()
            .get(key);
    if (trunk == null)
      throw new DhruvaRuntimeException("Key \"" + key + "\" does not match trunk of type " + type);
    return trunk.processIngress(proxySIPRequest);
  }

  public Mono<ProxySIPResponse> handleEgress(
      TrunkType type, ProxySIPRequest proxySIPRequest, String key) {
    try {
      AbstractTrunk trunk =
          this.registry
              .getPluginFor(
                  type,
                  () -> new DhruvaRuntimeException("Trunk Type \"" + type + "\" not registered"))
              .getTrunkMap()
              .get(key);
      if (trunk == null)
        throw new DhruvaRuntimeException(
            "Key \"" + key + "\" does not match trunk of type " + type);
      return trunk.processEgress(proxySIPRequest);
    } catch (Exception ex) {
      logger.error("Unable to find trunk for Key:{} TrunkType:{}", key, type);
      return Mono.error(ex);
    }
  }

  // TODO Akshay - we can expose response handling once best response is received
}
