package com.cisco.dsb.common.config.sip;

import com.cisco.dsb.common.config.RoutePolicy;
import com.cisco.dsb.common.config.TruststoreConfigurationProperties;
import com.cisco.dsb.common.exception.DhruvaRuntimeException;
import com.cisco.dsb.common.maintanence.MaintenancePolicy;
import com.cisco.dsb.common.servergroup.OptionsPingPolicy;
import com.cisco.dsb.common.servergroup.SGType;
import com.cisco.dsb.common.servergroup.ServerGroup;
import com.cisco.dsb.common.sip.bean.SIPListenPoint;
import com.cisco.dsb.common.sip.header.ListenIfHeader;
import com.cisco.dsb.common.transport.Transport;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import lombok.CustomLog;
import lombok.Getter;
import lombok.Setter;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.cloud.context.config.annotation.RefreshScope;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;

@Component
@EnableConfigurationProperties
@ConfigurationProperties(prefix = "common")
@CustomLog
@RefreshScope
public class CommonConfigurationProperties {
  @Autowired Environment environment;
  // Defaults for listenPoint builder and sipProxy builder
  public static final String DEFAULT_NETWORK_NAME = "TCPNetwork";
  public static final String DEFAULT_HOST_IP = "127.0.0.1";
  public static final Transport DEFAULT_TRANSPORT = Transport.TCP;
  public static final int DEFAULT_TRANSACTION_TIMEOUT = 32000;
  public static final int DEFAULT_PING_TIMEOUT_UDP = 5000;
  public static final int DEFAULT_PING_TIMEOUT_TCP = 500;
  public static int DEFAULT_PORT = 5060;
  public static final boolean DEFAULT_RECORD_ROUTE_ENABLED = true;
  public static final boolean DEFAULT_ATTACH_EXTERNAL_IP = false;
  public static final boolean DEFAULT_ENABLE_RATE_LIMITING = false;
  public static final Integer DEFAULT_TRAFFIC_CLASS = 0x68;
  public static final ListenIfHeader.HostnameType DEFAULT_EXTERNAL_HOSTNAME_TYPE =
      ListenIfHeader.HostnameType.LOCAL_IP;

  @Getter private TruststoreConfigurationProperties truststoreConfig;
  @Getter @Setter private boolean useRedisAsCache = false;

  @Getter @Setter private String sipCertificate;
  @Getter @Setter private String sipPrivateKey;

  @Getter @Setter private int udpEventloopThreadCount = 1;
  @Getter @Setter private int tlsEventloopThreadCount = 20;
  @Getter @Setter private int connectionIdleTimeout = 14400;

  @Getter @Setter private boolean hostPortEnabled = false;

  @Getter @Setter private int tlsHandShakeTimeOutMilliSeconds = 5000;
  @Getter @Setter private long connectionWriteTimeoutInMllis = 60000;
  @Getter private final boolean nioEnabled = false;

  // All Keep Alive time related values in seconds
  @Getter @Setter private long keepAlivePeriod = 20L;
  @Getter @Setter private String reliableKeepAlivePeriod = "25";
  @Getter @Setter private String minKeepAliveTimeSeconds = "20";
  @Getter @Setter private boolean logKeepAlivesEnabled = false;

  // metric emitting interval configs
  @Getter @Setter private int cpsMetricInterval = 1;
  @Getter @Setter private int trunkLBMetricInterval = 120;

  @Getter @Setter private int udpConnectionMetricInterval = 30;

  // DSBNetworkLayer is using this as static variable
  @Getter private static int socketConnectionTimeout = 8000;

  // TODO: this is hack, inject spring object everywhere instead of static field
  public void setSocketConnectionTimeout(int timeout) {
    socketConnectionTimeout = timeout;
  }

  @Getter @Setter private String dhruvaUserAgent = "WX2Dhruva";

  @Getter
  private List<SIPListenPoint> listenPoints =
      Collections.singletonList(SIPListenPoint.SIPListenPointBuilder().build());

  @Getter @Setter private int listenPointRetryCount = 0;
  @Getter @Setter private int listenPointRetryDelay = 10;
  @Getter private int dnsCacheSize = 1000;
  @Getter private long timeOutDnsCache = 32_000L;
  @Getter private long timeOutDns = 10_000L;
  @Getter @Setter private long dnsLookupTimeoutMillis = 10_000L;
  @Getter private Map<String, ServerGroup> serverGroups = new HashMap<>();

  @Getter private Map<String, RoutePolicy> routePolicyMap = new HashMap<>();

  @Getter private Map<String, OptionsPingPolicy> optionsPingPolicyMap = new HashMap<>();

  @Getter private Map<String, MaintenancePolicy> maintenancePolicyMap = new HashMap<>();

  // initial delay for triggering custom check for /ping once server is running
  @Getter @Setter private long customMonitorPingInitialDelayInSec = 30L;
  // time on which custom health checks will be evaluated periodically
  @Getter @Setter private long customMonitorPingPeriodInSec = 30L;

  // to toggle reactors scheduler metrics support
  @Getter @Setter private boolean reactorSchedulerMetricsEnabled = true;

  // toggle to enable emitting unmasked events
  @Getter @Setter private boolean emitUnmaskedEvents = true;

  @Getter @Setter private Map<String, Integer> trafficClassMap = new ConcurrentHashMap<>();

  @Autowired
  public void setTruststoreConfiguration(TruststoreConfigurationProperties truststore) {
    this.truststoreConfig = truststore;
  }

  public void setDnsCacheSize(int size) {
    if (size > 0) this.dnsCacheSize = size;
  }

  public void setTimeOutDnsCache(int timeOutCache) {
    if (timeOutCache > 0) this.timeOutDnsCache = timeOutCache;
  }

  public void setTimeOutDns(int timeOutDns) {
    if (timeOutDns > 0) this.timeOutDns = timeOutDns;
  }

  public void setListenPoints(List<SIPListenPoint> listenPoints) {
    this.listenPoints = listenPoints;
    listenPoints.forEach(
        sipListenPoint -> {
          sipListenPoint.init();
          // For each listenPoint create a HashMap entry
          updateTrafficMap(sipListenPoint);
        });
  }

  public void setRoutePolicy(Map<String, RoutePolicy> routePolicyMap) {
    logger.info("Configuring ServerGroups route policy");
    this.serverGroups
        .values()
        .forEach(
            serverGroup -> {
              RoutePolicy routePolicy = routePolicyMap.get(serverGroup.getRoutePolicyConfig());
              if (routePolicy != null) {
                logger.info("SG: {} RoutePolicy {}", serverGroup, routePolicy.getName());
                serverGroup.setRoutePolicyFromConfig(routePolicy);
              }
            });
    updateMap(this.routePolicyMap, routePolicyMap);
  }

  public void setOptionsPingPolicy(Map<String, OptionsPingPolicy> optionsPingPolicyMap) {
    logger.info("Configuring ServerGroups OptionsPingPolicy");
    this.serverGroups
        .values()
        .forEach(
            serverGroup -> {
              OptionsPingPolicy optionsPingPolicy =
                  optionsPingPolicyMap.get(serverGroup.getOptionsPingPolicyConfig());
              if (optionsPingPolicy != null) {
                logger.info(
                    "SG: {} OptionsPingPolicy {}", serverGroup, optionsPingPolicy.getName());
                serverGroup.setOptionsPingPolicyFromConfig(optionsPingPolicy);
              }
            });
    updateMap(this.optionsPingPolicyMap, optionsPingPolicyMap);
  }

  public void setServerGroups(Map<String, ServerGroup> serverGroups) {
    // update SG map
    updateMap(this.serverGroups, serverGroups);
    this.serverGroups
        .values()
        .forEach(
            sg -> {
              String network = sg.getNetworkName();

              listenPoints.stream()
                  .filter(lp -> lp.getName().equals(network))
                  .findFirst()
                  .ifPresentOrElse(
                      listenPoint -> {
                        updateTransport(sg, listenPoint.getTransport());
                        // Once we are able to identify ingress, SG can have different hostNameType
                        // than listenPoint
                        updateHostNameType(sg, listenPoint);
                      },
                      () -> {
                        throw new DhruvaRuntimeException(
                            "SGName: "
                                + sg.getHostName()
                                + "; listenPoint: \""
                                + network
                                + "\" not found");
                      });
            });
  }

  public void setMaintenancePolicy(Map<String, MaintenancePolicy> maintenancePolicyMap) {
    logger.info("Configuring maintenance policies");
    this.maintenancePolicyMap = maintenancePolicyMap;
  }

  private void updateTransport(ServerGroup sg, Transport transport) {
    sg.setTransport(transport);

    if (SGType.STATIC.equals(sg.getSgType())) {
      Optional.ofNullable(sg.getElements())
          .ifPresent(
              serverGroupElements ->
                  serverGroupElements.forEach(
                      serverGroupElement -> serverGroupElement.setTransport(transport)));
    }
  }

  private void updateHostNameType(ServerGroup sg, SIPListenPoint listenPoint) {
    sg.setViaHostName(listenPoint.getExternalHostnameType());
    sg.setRrHostName(listenPoint.getExternalHostnameType());
  }

  private void updateTrafficMap(SIPListenPoint sipListenPoint) {
    String key = "/" + sipListenPoint.getHostIPAddress();
    trafficClassMap.put(key, sipListenPoint.getTrafficClass());
  }

  private <K, V> void updateMap(Map<K, V> oldMap, Map<K, V> newMap) {
    Set<K> removeKeys = new HashSet<>();
    oldMap.putAll(newMap);
    oldMap.keySet().stream().filter(key -> !newMap.containsKey(key)).forEach(removeKeys::add);
    removeKeys.forEach(oldMap::remove);
  }
}
