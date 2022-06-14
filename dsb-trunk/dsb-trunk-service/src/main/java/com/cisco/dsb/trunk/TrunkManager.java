package com.cisco.dsb.trunk;

import com.cisco.dsb.common.config.sip.CommonConfigurationProperties;
import com.cisco.dsb.common.exception.DhruvaRuntimeException;
import com.cisco.dsb.common.executor.DhruvaExecutorService;
import com.cisco.dsb.common.service.MetricService;
import com.cisco.dsb.connectivity.monitor.service.OptionsPingController;
import com.cisco.dsb.proxy.messaging.ProxySIPRequest;
import com.cisco.dsb.proxy.messaging.ProxySIPResponse;
import com.cisco.dsb.trunk.trunks.AbstractTrunk;
import com.cisco.dsb.trunk.trunks.TrunkPluginInterface;
import com.cisco.dsb.trunk.trunks.TrunkPlugins;
import com.cisco.dsb.trunk.trunks.TrunkType;
import com.cisco.dsb.trunk.util.SipParamConstants;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import javax.annotation.PostConstruct;
import lombok.CustomLog;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.plugin.core.PluginRegistry;
import org.springframework.plugin.core.SimplePluginRegistry;
import org.springframework.plugin.core.config.EnablePluginRegistries;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

@Service
@EnablePluginRegistries(TrunkPluginInterface.class)
@CustomLog
public class TrunkManager {
  private final PluginRegistry<TrunkPluginInterface, TrunkType> registry;
  private final TrunkConfigurationProperties configurationProperties;
  private final TrunkPlugins trunkPlugins;
  private DhruvaExecutorService dhruvaExecutorService;
  private MetricService metricService;
  private static final int FIXED_DELAY_MILLIS = 120000;
  private OptionsPingController optionsPingController;

  @Autowired
  public TrunkManager(
      TrunkConfigurationProperties configurationProperties,
      TrunkPlugins trunkPlugins,
      MetricService metricService,
      CommonConfigurationProperties commonConfigurationProperties,
      DhruvaExecutorService dhruvaExecutorService,
      OptionsPingController optionsPingController) {
    this.configurationProperties = configurationProperties;
    this.trunkPlugins = trunkPlugins;
    this.registry =
        SimplePluginRegistry.of(
            trunkPlugins.getB2B(),
            trunkPlugins.getPSTN(),
            trunkPlugins.getCalling(),
            trunkPlugins.getDefault());

    this.metricService = metricService;
    this.optionsPingController = optionsPingController;
    metricService.emitTrunkCPSMetricPerInterval(
        commonConfigurationProperties.getCpsMetricInterval(), TimeUnit.SECONDS);
    metricService.emitTrunkLBDistribution(
        commonConfigurationProperties.getTrunkLBMetricInterval(), TimeUnit.SECONDS);
  }

  @PostConstruct
  public void init() {
    this.probeTrunkSGStatus();
  }

  @Scheduled(fixedRate = FIXED_DELAY_MILLIS)
  public void probeTrunkSGStatus() {

    if (metricService == null) return;
    configurationProperties
        .getTrunkServerGroupHashMap()
        .forEach(
            (trunk, serverGroups) -> {
              AtomicReference<String> trunkStatus = new AtomicReference<>("disabled");
              Set<String> serverGroupName = new HashSet<>();
              serverGroups.stream()
                  .forEach(
                      serverGroup -> {
                        Boolean serverStatus;
                        if (serverGroup.isPingOn()) {
                          serverStatus = optionsPingController.getStatus(serverGroup);
                          if (!trunkStatus.get().equals("true"))
                            trunkStatus.set(serverStatus.toString());
                          metricService
                              .getServerGroupStatusMap()
                              .put(serverGroup.getName(), serverStatus.toString());
                        } else {
                          metricService
                              .getServerGroupStatusMap()
                              .put(serverGroup.getName(), "disabled");
                        }

                        serverGroupName.add(serverGroup.getName());
                      });

              String[] arr = {trunkStatus.get(), String.join(",", serverGroupName)};
              metricService.getTrunkStatusMap().put(trunk, arr);
            });

    metricService.emitTrunkStatusSupplier("trunkstatus");
    metricService.emitServerGroupStatusSupplier("trunkServerGroupstatus");
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

      AbstractTrunk trunk = null;
      Map<String, AbstractTrunk> maps =
          (Map<String, AbstractTrunk>)
              this.registry
                  .getPluginFor(
                      type,
                      () ->
                          new DhruvaRuntimeException("Trunk Type \"" + type + "\" not registered"))
                  .getTrunkMap();

      switch (type) {
        case PSTN:
          for (Map.Entry<String, AbstractTrunk> map : maps.entrySet()) {
            Map<String, String> selector = map.getValue().getEgress().getSelector();
            if (selector == null || selector.isEmpty()) {
              logger.error("No selector provided for PSTN trunk, dtg cannot be fetched");
              break;
            }
            String dtgValue = selector.get(SipParamConstants.DTG);
            if (dtgValue == null) {
              logger.error("DTG key is not present in the selector");
              break;
            }
            if (dtgValue.equalsIgnoreCase(key)) {
              trunk = map.getValue();
              break;
            }
          }
          break;
        default:
          trunk = maps.get(key);
      }

      if (trunk == null)
        throw new DhruvaRuntimeException(
            "Key \"" + key + "\" does not match trunk of type " + type);
      return trunk.processEgress(proxySIPRequest);
    } catch (Exception ex) {
      logger.error("Unable to find trunk for Key:{} TrunkType:{}", key, type, ex);
      return Mono.error(ex);
    }
  }
  // TODO Akshay - we can expose response handling once best response is received
}
