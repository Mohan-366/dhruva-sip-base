package com.cisco.dsb.trunk;

import com.cisco.dsb.common.circuitbreaker.DsbCircuitBreaker;
import com.cisco.dsb.common.config.RoutePolicy;
import com.cisco.dsb.common.config.sip.CommonConfigurationProperties;
import com.cisco.dsb.common.exception.DhruvaRuntimeException;
import com.cisco.dsb.common.servergroup.DnsServerGroupUtil;
import com.cisco.dsb.common.servergroup.SGType;
import com.cisco.dsb.common.servergroup.ServerGroup;
import com.cisco.dsb.connectivity.monitor.service.OptionsPingController;
import com.cisco.dsb.trunk.trunks.*;
import com.cisco.dsb.trunk.util.SipParamConstants;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import lombok.CustomLog;
import lombok.Getter;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.cloud.context.config.annotation.RefreshScope;
import org.springframework.stereotype.Component;

@Component
@EnableConfigurationProperties
@ConfigurationProperties(prefix = "trunk")
@RefreshScope
@CustomLog
public class TrunkConfigurationProperties {
  private CommonConfigurationProperties commonConfigurationProperties;
  private DnsServerGroupUtil dnsServerGroupUtil;
  @Getter private OptionsPingController optionsPingController;
  private DsbCircuitBreaker dsbCircuitBreaker;

  @Autowired
  public void setCommonConfigurationProperties(
      CommonConfigurationProperties commonConfigurationProperties) {
    this.commonConfigurationProperties = commonConfigurationProperties;
  }

  @Autowired
  public void setDnsServerGroupUtil(DnsServerGroupUtil dnsServerGroupUtil) {
    this.dnsServerGroupUtil = dnsServerGroupUtil;
  }

  @Autowired
  public void setOptionsPingController(OptionsPingController optionsPingController) {
    this.optionsPingController = optionsPingController;
  }

  @Autowired
  public void setDsbCircuitBreaker(DsbCircuitBreaker dsbCircuitBreaker) {
    this.dsbCircuitBreaker = dsbCircuitBreaker;
  }

  // Key is name of trunk
  // <String,PSTNTrunk> pstnTrunks;
  @Getter private Map<String, PSTNTrunk> pstnTrunkMap = new HashMap<>();
  @Getter private Map<String, B2BTrunk> b2BTrunkMap = new HashMap<>();
  @Getter private Map<String, CallingTrunk> callingTrunkMap = new HashMap<>();
  @Getter private Map<String, DefaultTrunk> defaultTrunkMap = new HashMap<>();
  @Getter private Map<String, Collection<ServerGroup>> trunkServerGroupHashMap = new HashMap<>();
  @Getter private Map<ServerGroup, Set<String>> serverToTrunkMap = new HashMap<>();

  public void setPSTN(Map<String, PSTNTrunk> pstnTrunkMap) {
    updateMap(this.pstnTrunkMap, pstnTrunkMap);
    this.pstnTrunkMap.values().forEach(this::createSGFromConfig);
    this.pstnTrunkMap.put(
        SipParamConstants.DEFAULT_DTG_VALUE_FOR_MIDCALL, getDefaultPSTNTrunkForMidCall());
  }

  public void setB2B(Map<String, B2BTrunk> b2BTrunkMap) {
    updateMap(this.b2BTrunkMap, createChildTrunksForB2B(b2BTrunkMap));
    this.b2BTrunkMap.values().forEach(this::createSGFromConfig);
  }

  public void setCallingCore(Map<String, CallingTrunk> callingTrunkMap) {
    updateMap(this.callingTrunkMap, callingTrunkMap);
    this.callingTrunkMap.values().forEach(this::createSGFromConfig);
  }

  public void setDefault(Map<String, DefaultTrunk> defaultTrunkMap) {
    updateMap(this.defaultTrunkMap, defaultTrunkMap);
    this.defaultTrunkMap.values().forEach(this::createSGFromConfig);
  }

  private void createSGFromConfig(AbstractTrunk trunk) {
    logger.info("Configuring Trunk {}", trunk);
    RoutePolicy routePolicy =
        this.commonConfigurationProperties
            .getRoutePolicyMap()
            .get(trunk.getEgress().getRoutePolicyConfig());
    trunk.getEgress().setRoutePolicyFromConfig(routePolicy);
    List<ServerGroups> serverGroupsConfig = trunk.getEgress().getServerGroupsConfig();
    Map<String, ServerGroup> serverGroupMap = trunk.getEgress().getServerGroupMap();
    serverGroupsConfig.forEach(
        sgName -> {
          ServerGroup sg = commonConfigurationProperties.getServerGroups().get(sgName.getSg());
          if (sg == null)
            throw new DhruvaRuntimeException(
                String.format(
                    "Unable to Configure Egress, servergroup %s not present", sgName.getSg()));
          serverGroupMap.put(sgName.getSg(), sg);
          sg.setWeight(sgName.getWeight());
          sg.setPriority(sgName.getPriority());

          Set<String> trunkName = serverToTrunkMap.computeIfAbsent(sg, k -> new HashSet<>());
          trunkName.add(trunk.getName());
        });
    trunkServerGroupHashMap.put(trunk.getName(), trunk.getElements());
    trunk.setDnsServerGroupUtil(dnsServerGroupUtil);
    trunk.setOptionsPingController(optionsPingController);
    trunk.setLoadBalancerMetric(this.createSGECounterForLBMetric(trunk.getElements()));
    if (trunk.isEnableCircuitBreaker()) {
      trunk.setDsbCircuitBreaker(dsbCircuitBreaker);
      logger.info("Setting DsbCircuitBreaker for {}", trunk.getName());
    }
  }

  private <K, V> void updateMap(Map<K, V> oldMap, Map<K, V> newMap) {
    Set<K> removeKeys = new HashSet<>();
    oldMap.putAll(newMap);
    oldMap.keySet().stream().filter(key -> !newMap.containsKey(key)).forEach(removeKeys::add);
    removeKeys.stream().forEach(oldMap::remove);
  }

  private Map<String, B2BTrunk> createChildTrunksForB2B(Map<String, B2BTrunk> b2BTrunkMap) {
    Map<String, B2BTrunk> childTrunkMap = new HashMap<>();
    b2BTrunkMap
        .keySet()
        .forEach(
            key -> {
              if (key.toLowerCase(Locale.ROOT).contains("antares")) {
                childTrunkMap.put(key, new AntaresTrunk(b2BTrunkMap.get(key)));
              }
            });

    return childTrunkMap;
  }

  public ConcurrentHashMap<String, Long> createSGECounterForLBMetric(
      Collection<ServerGroup> serverGroups) {
    ConcurrentHashMap<String, Long> serverGroupElements = new ConcurrentHashMap<>();
    serverGroups.forEach(
        serverGroup -> {
          if (serverGroup.getSgType() != SGType.STATIC) return;
          if (serverGroup.getElements() == null) return;
          serverGroup
              .getElements()
              .forEach(
                  serverGroupElement -> {
                    serverGroupElements.put(serverGroupElement.toUniqueElementString(), 0l);
                  });
        });
    return serverGroupElements;
  }

  private PSTNTrunk getDefaultPSTNTrunkForMidCall() {
    Egress egress = new Egress();
    Map<String, String> defaultDtgMap = new HashMap<>();
    defaultDtgMap.put(SipParamConstants.DTG, SipParamConstants.DEFAULT_DTG_VALUE_FOR_MIDCALL);
    egress.setSelector(defaultDtgMap);
    Ingress ingress = new Ingress();
    ingress.setName("default-midcall-ingress");
    PSTNTrunk defaultPSTNTrunk = new PSTNTrunk("default-midcall-egress", ingress, egress, false);
    return defaultPSTNTrunk;
  }
}
