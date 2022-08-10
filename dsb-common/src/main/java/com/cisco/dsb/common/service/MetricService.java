/*
 * Copyright (c) 2020  by Cisco Systems, Inc.All Rights Reserved.
 */

package com.cisco.dsb.common.service;

import static com.cisco.dsb.common.util.log.event.Event.DIRECTION.OUT;
import static com.cisco.dsb.common.util.log.event.Event.MESSAGE_TYPE.REQUEST;
import static com.cisco.dsb.common.util.log.event.Event.MESSAGE_TYPE.RESPONSE;

import com.cisco.dsb.common.dto.ConnectionInfo;
import com.cisco.dsb.common.executor.DhruvaExecutorService;
import com.cisco.dsb.common.executor.ExecutorType;
import com.cisco.dsb.common.metric.*;
import com.cisco.dsb.common.sip.util.SipUtils;
import com.cisco.dsb.common.transport.Transport;
import com.cisco.dsb.common.util.log.event.Event.DIRECTION;
import com.cisco.dsb.common.util.log.event.Event.MESSAGE_TYPE;
import com.cisco.wx2.dto.health.ServiceHealth;
import com.cisco.wx2.dto.health.ServiceState;
import com.cisco.wx2.util.Token;
import com.google.common.base.Joiner;
import com.google.common.base.Strings;
import com.google.common.cache.Cache;
import com.google.common.cache.CacheBuilder;
import com.google.common.cache.RemovalCause;
import gov.nist.javax.sip.stack.MessageChannel;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicIntegerArray;
import java.util.function.Supplier;
import javax.annotation.Nullable;
import lombok.CustomLog;
import lombok.Getter;
import lombok.Setter;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.time.StopWatch;
import org.jetbrains.annotations.NotNull;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;

@Service
@CustomLog
public class MetricService {

  private static final String DHRUVA = "dhruva";
  private static final String DOT = ".";
  private static final String UPSTREAM_SERVICE_HEALTH_MEASUREMENT_NAME = "service.upstream.health";
  private static final String TRUNK_LOADBALANCER_MEASUREMENT = "trunkLbs";
  private static final String TRUNK_TAG = "trunk";
  private static final String TRUNK_ALGO_TAG = "trunkAlgo";
  private static final String SERVERGROUP_ELEMENT_TAG = "serverGroupElement";
  private static final String LBCOUNT_FIELD = "lbcount";
  private static final String TRUNK_STATUS_FIELD = "status";
  private static final String TRUNK_SG_MAP_FIELD = "mapping";
  private static final String TRUNK_FIELD = "trunkName";
  private static final String SERVERGROUP_TAG = "trunkServerGroup";
  private static final String SERVERGROUP_STATUS_FIELD = "status";

  private final ScheduledExecutorService scheduledExecutor;
  private DhruvaExecutorService dhruvaExecutorService;
  MetricClient metricClient;
  private final Executor executorService;

  @Getter @Setter private Map<String, AtomicInteger> cpsCounterMap;
  @Getter @Setter private Map<String, AtomicIntegerArray> cpsTrunkCounterMap;

  @Getter @Setter private Map<String, String[]> trunkStatusMap;
  @Getter @Setter private Map<String, String> serverGroupStatusMap;

  @Getter @Setter private Map<String, ConcurrentHashMap<String, Long>> trunkLBMap;

  @Getter @Setter private Map<String, String> trunkLBAlgorithm;

  @Getter @Setter private Map<String, ConnectionInfo> connectionInfoMap;

  @Getter private final Cache<String, Long> timers;
  @Getter private final HashMap<String, StopWatch> stopWatchTimers = new HashMap<>();
  private static final long SCAVENGE_EVERY_X_HOURS = 12L;
  public static final Joiner joiner = Joiner.on(Token.Chars.Dot).skipNulls();

  @Autowired
  public MetricService(
      MetricClient metricClient,
      DhruvaExecutorService dhruvaExecutorService,
      @Qualifier("asyncMetricsExecutor") Executor executorService) {
    this.metricClient = metricClient;
    this.dhruvaExecutorService = dhruvaExecutorService;
    dhruvaExecutorService.startScheduledExecutorService(ExecutorType.METRIC_SERVICE);
    scheduledExecutor =
        this.dhruvaExecutorService.getScheduledExecutorThreadPool(ExecutorType.METRIC_SERVICE);
    this.executorService = executorService;

    timers =
        CacheBuilder.newBuilder()
            .expireAfterWrite(SCAVENGE_EVERY_X_HOURS, TimeUnit.HOURS)
            .maximumSize(200000L)
            .removalListener(
                (notification) -> {
                  if (notification.getCause() == RemovalCause.SIZE) {
                    logger.warn(
                        "Timer removed due to cache size. Consider increasing size or decreasing TTL.");
                  }
                })
            .build();

    this.cpsCounterMap = new HashMap<>();
    this.cpsTrunkCounterMap = new HashMap<>();
    this.trunkStatusMap = new HashMap<>();
    this.serverGroupStatusMap = new HashMap<>();
    this.trunkLBMap = new ConcurrentHashMap<>();
    this.trunkLBAlgorithm = new HashMap<>();

    this.connectionInfoMap = new HashMap<>();
  }

  public void registerPeriodicMetric(
      String measurement, Supplier<Set<Metric>> metricSupplier, int interval, TimeUnit timeUnit) {
    scheduledExecutor.scheduleAtFixedRate(
        getMetricFromSupplier(measurement, metricSupplier), interval, interval, timeUnit);
  }

  public void insertConnectionInfo(String id, ConnectionInfo connectionInfo) {
    if (this.connectionInfoMap != null) {
      this.connectionInfoMap.computeIfAbsent(id, value -> connectionInfo);
    }
  }
  /**
   * API to emit metric for successful call per secord for each calltypes
   *
   * @param interval
   * @param timeUnit
   */
  public void emitCPSMetricPerInterval(int interval, TimeUnit timeUnit) {
    this.registerPeriodicMetric("cps", this.cpsMetricSupplier(), interval, timeUnit);
  }

  public void emitTrunkCPSMetricPerInterval(int interval, TimeUnit timeUnit) {
    this.registerPeriodicMetric(
        "trunkcps", this.cpsTrunkMetricSupplier("trunkcps"), interval, timeUnit);
  }

  public void emitTrunkLBDistribution(int interval, TimeUnit timeUnit) {
    this.registerPeriodicMetric(
        TRUNK_LOADBALANCER_MEASUREMENT,
        this.sendLBDistribution(TRUNK_LOADBALANCER_MEASUREMENT),
        interval,
        timeUnit);
  }

  /**
   * Currently used to emit establised connection info for UDP transports
   *
   * @param interval
   * @param timeUnit
   */
  public void emitConnectionInfoMetricPerInterval(int interval, TimeUnit timeUnit) {
    this.registerPeriodicMetric(
        "connection", this.connectionInfoMetricSupplier(), interval, timeUnit);
  }

  private Supplier<Set<Metric>> connectionInfoMetricSupplier() {
    {
      Supplier<Set<Metric>> connectionInfoSupplier =
          () -> {
            Set<Metric> connectionInfoMetricSet = new HashSet<>();
            for (Map.Entry<String, ConnectionInfo> entry : connectionInfoMap.entrySet()) {
              if (entry.getValue() != null) {
                ConnectionInfo connectionInfo = (ConnectionInfo) entry.getValue();
                Metric connectionMetric =
                    this.createConnectionMetric(
                        connectionInfo.getDirection(),
                        connectionInfo.getMessageChannel(),
                        connectionInfo.getConnectionState());
                connectionInfoMetricSet.add(connectionMetric);
              }
            }
            connectionInfoMap.clear();
            return connectionInfoMetricSet;
          };
      return connectionInfoSupplier;
    }
  }

  /**
   * This supplier is used to evaluate no. of calls processed each second for all the calltypes
   * available and return the result in form of a metric, to be pushed in influxDB
   *
   * @return
   */
  public Supplier<Set<Metric>> cpsMetricSupplier() {
    Supplier<Set<Metric>> cpsSupplier =
        () -> {
          Set<Metric> cpsMetricSet = new HashSet<>();
          for (Map.Entry<String, AtomicInteger> entry : cpsCounterMap.entrySet()) {
            if (entry.getValue().get() != 0) {
              Metric cpsMetricForCallType = Metrics.newMetric().measurement("cps");
              cpsMetricForCallType.tag("callType", entry.getKey());
              cpsMetricForCallType.field("count", entry.getValue());
              cpsMetricSet.add(cpsMetricForCallType);
              entry.getValue().set(0);
            }
          }
          return cpsMetricSet;
        };
    return cpsSupplier;
  }

  /**
   * This supplier is used to evaluate no. of calls processed (inbound and outbound )each second for
   * all the trunk available and return the result in form of a metric, to be pushed in influxDB
   *
   * @return
   */
  public Supplier<Set<Metric>> cpsTrunkMetricSupplier(String measurement) {
    Supplier<Set<Metric>> cpsTrunkSupplier =
        () -> {
          Set<Metric> cpsMetricSet = new HashSet<>();
          for (Map.Entry<String, AtomicIntegerArray> entry : cpsTrunkCounterMap.entrySet()) {
            Metric cpsMetricForTrunk = Metrics.newMetric().measurement(measurement);
            cpsMetricForTrunk.tag("trunk", entry.getKey());

            if (entry.getValue().get(0) != 0) {
              cpsMetricForTrunk.field("inboundCount", entry.getValue().get(0));
              entry.getValue().set(0, 0);
            }
            if (entry.getValue().get(1) != 0) {
              cpsMetricForTrunk.field("outboundCount", entry.getValue().get(1));
              entry.getValue().set(1, 0);
            }
            cpsMetricSet.add(cpsMetricForTrunk);
          }
          return cpsMetricSet;
        };
    return cpsTrunkSupplier;
  }

  public Supplier<Set<Metric>> sendLBDistribution(String measurement) {
    Supplier<Set<Metric>> lbTrunkSupplier =
        () -> {
          Set<Metric> trunkLBSetMetricDistribution = new HashSet<>();
          trunkLBMap.forEach(
              (trunkName, lbCount) -> {
                Map<String, Long> lbValues = trunkLBMap.get(trunkName);
                if (lbValues.isEmpty()) return;
                for (Map.Entry<String, Long> entry : lbValues.entrySet()) {
                  Metric lbMetricTrunk = Metrics.newMetric().measurement(measurement);
                  lbMetricTrunk.tag(TRUNK_TAG, trunkName);
                  lbMetricTrunk.tag(TRUNK_ALGO_TAG, trunkLBAlgorithm.get(trunkName));
                  lbMetricTrunk.tag(SERVERGROUP_ELEMENT_TAG, entry.getKey());
                  lbMetricTrunk.field(LBCOUNT_FIELD, entry.getValue());
                  trunkLBSetMetricDistribution.add(lbMetricTrunk);
                }
              });
          return trunkLBSetMetricDistribution;
        };
    return lbTrunkSupplier;
  }

  public void emitTrunkStatusSupplier(String measurement) {

    Set<Metric> cpsMetricSet = new HashSet<>();
    for (Map.Entry<String, String[]> entry : trunkStatusMap.entrySet()) {
      Metric cpsMetricForTrunk = Metrics.newMetric().measurement(measurement);
      cpsMetricForTrunk.tag(TRUNK_TAG, entry.getKey());

      if (entry.getValue() != null) {

        cpsMetricForTrunk.field(TRUNK_STATUS_FIELD, entry.getValue()[0]);
        cpsMetricForTrunk.field(TRUNK_SG_MAP_FIELD, entry.getValue()[1]);
        // added as field as well, need for table Mapping in grafana
        cpsMetricForTrunk.field(TRUNK_FIELD, entry.getKey());
      }

      cpsMetricSet.add(cpsMetricForTrunk);
    }
    sendMetric(cpsMetricSet);
  }

  public void emitServerGroupStatusSupplier(String measurement) {

    Set<Metric> cpsMetricSet = new HashSet<>();
    for (Map.Entry<String, String> entry : serverGroupStatusMap.entrySet()) {
      Metric cpsMetricForTrunk = Metrics.newMetric().measurement(measurement);
      cpsMetricForTrunk.tag(SERVERGROUP_TAG, entry.getKey());

      if (entry.getValue() != null) {
        cpsMetricForTrunk.field(SERVERGROUP_STATUS_FIELD, entry.getValue());
      }

      cpsMetricSet.add(cpsMetricForTrunk);
    }
    sendMetric(cpsMetricSet);
  }

  @NotNull
  private Runnable getMetricFromSupplier(String measurement, Supplier<Set<Metric>> metricSupplier) {
    return () -> {
      Set<Metric> metrics = metricSupplier.get();
      metrics.forEach(metric -> metric.measurement(prefixDhruvaToMeasurementName(measurement)));
      sendMetric(metrics);
    };
  }

  public void emitServiceHealth(ServiceHealth health, boolean includeUpstreamService) {
    sendServiceHealthMetric("service.health", health);

    if (includeUpstreamService && health.getUpstreamServices() != null) {
      for (ServiceHealth upstreamHealth : health.getUpstreamServices()) {
        sendUpstreamHealthMetric(upstreamHealth);
      }
    }
  }

  public void sendUpstreamHealthMetric(ServiceHealth upstreamServiceHealth) {
    this.sendServiceHealthMetric(UPSTREAM_SERVICE_HEALTH_MEASUREMENT_NAME, upstreamServiceHealth);
  }

  public void sendServiceHealthMetric(String measurementName, ServiceHealth serviceHealth) {
    Metric metric =
        Metrics.newMetric()
            .measurement(measurementName)
            .tag("state", serviceHealth.getServiceState().toString())
            .field("message", serviceHealth.getMessage())
            .tag("name", serviceHealth.getServiceName())
            .field(
                "availability",
                serviceHealth.getServiceState() == ServiceState.ONLINE ? 100.0 : 0.0)
            .field("optional", serviceHealth.isOptional());

    sendMetric(metric);
  }

  public void emitConnectionMetrics(
      String direction, MessageChannel channel, String connectionState) {
    try {
      if (channel == null) {
        return;
      }

      Metric connectionMetric = createConnectionMetric(direction, channel, connectionState);

      sendMetric(connectionMetric);

    } catch (Exception e) {
      // Only debug here since TLS connections with no handshake session are common and cause noisy
      logger.debug("Unable to emit connection metric", e);
    }
  }

  @NotNull
  private Metric createConnectionMetric(
      String direction, MessageChannel channel, String connectionState) {
    String id = SipUtils.getConnectionId(direction, channel.getTransport().toUpperCase(), channel);
    String localAddress = channel.getHost();
    int localPort = channel.getPort();
    // String viaAddress = channel.getViaHost();
    // int viaPort = channel.getViaPort();
    String remoteAddress = channel.getPeerAddress();
    int remotePort = channel.getPeerPort();

    String transport = channel.getTransport().toUpperCase();

    Metric connectionMetric = Metrics.newMetric().measurement("connection");

    connectionMetric.tag("direction", direction);
    connectionMetric.tag("transport", transport);
    connectionMetric.field("localAddress", localAddress);
    connectionMetric.field("localPort", localPort);
    if (StringUtils.equalsIgnoreCase(OUT.toString(), direction)) {
      connectionMetric.field("localPortStr", "Ephemeral port");
    } else {
      connectionMetric.field("localPortStr", String.valueOf(localPort));
    }
    connectionMetric.field("remoteAddress", remoteAddress);
    connectionMetric.field("remotePort", remotePort);
    connectionMetric.field("id", id);
    connectionMetric.tag("connectionState", connectionState);
    return connectionMetric;
  }

  public void emitConnectionErrorMetric(
      MessageChannel channel, boolean isClient, String exceptionMessage) {
    try {
      if (channel == null) {
        return;
      }

      // String id = SipUtils.getConnectionId(direction, channel.getTransport(), channel);
      String localAddress = channel.getHost();
      int localPort = channel.getPort();
      String localPortStr = String.valueOf(localPort);

      String remoteAddress = channel.getPeerAddress();
      int remotePort = channel.getPeerPort();

      String transport = channel.getTransport().toUpperCase();

      Metric connectionMetric = Metrics.newMetric().measurement("connection.failure");

      connectionMetric.tag("transport", transport);
      connectionMetric.field("localAddress", localAddress);
      connectionMetric.field("localPort", localPort);

      if (isClient) {
        connectionMetric.field("localPortStr", "Ephemeral port");
      } else {
        connectionMetric.field("localPortStr", localPortStr);
      }
      connectionMetric.field("remoteAddress", remoteAddress);
      connectionMetric.field("remotePort", remotePort);
      connectionMetric.field("errorMessage", exceptionMessage);

      sendMetric(connectionMetric);

    } catch (Exception e) {
      logger.debug("Unable to emit connection failure metric", e);
    }
  }

  public void sendDNSMetric(
      String query, String queryType, long totalDurationsMillis, String errorMsg) {
    Metric metric =
        Metrics.newMetric()
            .measurement("dns")
            .field("dnsProcessingDelayMillis", totalDurationsMillis)
            .field("query", query)
            .tag("queryType", queryType)
            .tag("failureReason", errorMsg);

    sendMetric(metric);
  }

  public void sendTrunkMetric(String trunk, int response, String callId) {
    Metric metric =
        Metrics.newMetric()
            .measurement("trunkMetric")
            .tag("trunk", trunk)
            .field("response", response)
            .field("callId", callId);

    sendMetric(metric);
  }

  public void sendSipMessageMetric(
      String method,
      String callId,
      String cseq,
      MESSAGE_TYPE messageType,
      Transport transport,
      DIRECTION direction,
      boolean isMidCall,
      boolean isInternallyGenerated,
      long dhruvaProcessingDelayInMillis,
      String requestUri,
      String callType) {

    Metric metric =
        Metrics.newMetric()
            .measurement("sipMessage")
            .tag("method", method)
            .tag("messageType", messageType.name())
            .tag("direction", direction.name())
            .tag("isMidCall", isMidCall)
            .tag("transport", transport.name())
            .tag("isInternallyGenerated", isInternallyGenerated)
            .tag("callType", callType)
            .field("callId", callId)
            .field("cSeq", cseq);

    if (messageType == RESPONSE) {
      metric.field("responseCode", Integer.valueOf(method));
      metric.field("responseReason", requestUri);
    } else if (messageType == REQUEST) {
      metric.field("requestUri", requestUri);
      // metric.field("callType", callType);
    }

    if (direction == OUT && !isInternallyGenerated) {
      metric.field("processingDelayInMillis", dhruvaProcessingDelayInMillis);
    }
    sendMetric(metric);
  }

  @NotNull
  private String prefixDhruvaToMeasurementName(String measurement) {
    return DHRUVA + DOT + measurement;
  }

  private void sendMetric(Metric metric) {
    metric.measurement(prefixDhruvaToMeasurementName(metric.measurement()));
    metricClient.sendMetric(metric);
  }

  private void sendMetric(Set<Metric> metrics) {
    metricClient.sendMetrics(metrics);
  }

  public void time(
      String metric, long duration, TimeUnit timeUnit, @Nullable SipMetricsContext context) {
    String callId = null;
    if (context != null) {
      callId = context.getCallId();
    }
    update(
        metric,
        1,
        duration,
        timeUnit,
        context != null ? context.isSuccessful() : null,
        callId,
        context.getCallType());
  }

  public void update(
      String metric,
      long count,
      long duration,
      TimeUnit timeUnit,
      Boolean isSuccess,
      @Nullable String callId,
      String callType) {
    update(metric, count, duration, timeUnit, 0, null, isSuccess, callId, callType);
  }

  public void update(
      String metric,
      long count,
      long duration,
      TimeUnit durationTimeUnit,
      long expectedDuration,
      TimeUnit expectedTimeUnit,
      Boolean isSuccess,
      String callId,
      String callType) {

    update(
        createMetric(
            metric,
            count,
            duration,
            durationTimeUnit,
            expectedDuration,
            expectedTimeUnit,
            isSuccess,
            callId,
            callType));
  }

  public void update(Metric influxPoint) {
    update(influxPoint, true);
  }

  /**
   * Writes a single point to InfluxDB
   *
   * @param metric
   */
  private void update(Metric metric, boolean includeStandard) {
    sendMetric(metric);
  }

  /**
   * Increase count and/or duration for given metric. No-op if both {count, duration} <= 0. All
   * durations are normalized to milliseconds.
   *
   * @param metric What are you tracking?
   * @param count Must be > 0 to emit metric
   * @param duration Must be > 0 to emit metric
   * @param durationTimeUnit What TimeUnit is duration?
   * @param expectedDuration How long did you expect this to take?
   * @param expectedTimeUnit What TimeUnit is expectedDuration?
   * @param isSuccess If event that generated this metric was successful
   * @param callType holds information of internal calltype derived by dhruva, e.g: dialInPstn,
   *     dialInB2B
   */
  private static Metric createMetric(
      String metric,
      long count,
      long duration,
      TimeUnit durationTimeUnit,
      long expectedDuration,
      TimeUnit expectedTimeUnit,
      Boolean isSuccess,
      String callId,
      String callType) {

    long normalizedDuration =
        duration > 0 && durationTimeUnit != null
            ? TimeUnit.MILLISECONDS.convert(duration, durationTimeUnit)
            : 0L;

    long normalizedExpectedDuration =
        expectedDuration > 0 && expectedTimeUnit != null
            ? TimeUnit.MILLISECONDS.convert(expectedDuration, expectedTimeUnit)
            : 0L;

    Metric point = Metrics.newMetric().measurement(metric);

    if (count > 0 || duration > 0) {
      if (count > 0) {
        point.field("count", count);
      }

      if (normalizedDuration > 0) {
        point.field("duration", normalizedDuration);
      }

      if (normalizedExpectedDuration > 0) {
        point.field("durationExpected", expectedDuration);
      }

      if (isSuccess != null) {
        point.field("eventSuccess", isSuccess);
      }
      if (callId != null) {
        point.field("callId", callId);
      }
      if (callType != null) {
        point.tag("callType", callType);
      }
    }

    return point;
  }

  // Starts timing operation on the given (metric, callId) pair
  public void startTimer(String callId, String metric) {
    if (!Strings.isNullOrEmpty(metric) && !Strings.isNullOrEmpty(callId)) {
      String key = joiner.join(callId, metric);
      timers.put(key, System.nanoTime());
    }
  }

  public void startStopWatch(String callId, String metric) {
    if (!Strings.isNullOrEmpty(metric) && !Strings.isNullOrEmpty(callId)) {
      String key = joiner.join(callId, metric);
      StopWatch stopWatch = stopWatchTimers.get(key);
      if (stopWatch != null && !stopWatch.isStarted()) {
        stopWatch.start();
      } else {
        stopWatch = new StopWatch();
        stopWatch.start();
        stopWatchTimers.put(key, stopWatch);
      }
    }
  }

  public void pauseStopWatch(String callId, String metric) {
    if (!Strings.isNullOrEmpty(metric) && !Strings.isNullOrEmpty(callId)) {
      String key = joiner.join(callId, metric);
      StopWatch stopWatch = stopWatchTimers.get(key);
      if (stopWatch != null) {
        stopWatch.suspend();
      }
    }
  }

  public void splitStopWatch(String callId, String metric) {
    if (!Strings.isNullOrEmpty(metric) && !Strings.isNullOrEmpty(callId)) {
      String key = joiner.join(callId, metric);
      StopWatch stopWatch = stopWatchTimers.get(key);
      if (stopWatch != null) {
        stopWatch.split();
      }
    }
  }

  public void resumeStopWatch(String callId, String metric) {
    if (!Strings.isNullOrEmpty(metric) && !Strings.isNullOrEmpty(callId)) {
      String key = joiner.join(callId, metric);
      StopWatch stopWatch = stopWatchTimers.get(key);
      if (stopWatch != null) {
        stopWatch.resume();
      }
    }
  }

  public long getSplitTimeStopWatch(String callId, String metric) {
    final long noValidDuration = -1L;
    if (!Strings.isNullOrEmpty(metric) && !Strings.isNullOrEmpty(callId)) {
      String key = joiner.join(callId, metric);
      StopWatch stopWatch = stopWatchTimers.get(key);
      if (stopWatch != null) {
        TimeUnit timeUnit = TimeUnit.MILLISECONDS;
        try {
          return timeUnit.convert(stopWatch.getSplitNanoTime(), TimeUnit.NANOSECONDS);
        } catch (IllegalStateException e) {
          logger.info("exception while we get split time {}", e.getMessage());
          return noValidDuration;
        }
      }
    }
    return noValidDuration;
  }

  public long endStopWatch(String callId, String metric) {
    final long noValidDuration = -1L;
    if (!Strings.isNullOrEmpty(metric) && !Strings.isNullOrEmpty(callId)) {
      String key = joiner.join(callId, metric);
      StopWatch stopWatch = stopWatchTimers.get(key);
      if (stopWatch != null) {
        stopWatch.stop();
        long retVal = stopWatch.getTime(TimeUnit.MILLISECONDS);
        stopWatchTimers.remove(key);
        return retVal;
      }
    }
    return noValidDuration;
  }

  // Stops timing operation on the given (metric, callId) pair and calculates
  // the delta between start/stop operations. Returns negative one if no valid
  // duration for metric & callId combo exists. Duration is in milliseconds.
  public long stopTimer(String callId, String metric) {
    final long noValidDuration = -1L;
    if (!Strings.isNullOrEmpty(metric) && !Strings.isNullOrEmpty(callId)) {
      String key = joiner.join(callId, metric);
      Long startTime = timers.getIfPresent(key);
      if (startTime != null) {
        timers.invalidate(key);
        return TimeUnit.MILLISECONDS.convert(System.nanoTime() - startTime, TimeUnit.NANOSECONDS);
      }
      return noValidDuration;
    }
    return noValidDuration;
  }

  public void handleMetricsEvent(SipMetricsContext metricsContext) {
    if (metricsContext != null) {
      try {
        SipMetricsTask task = new SipMetricsTask(this, metricsContext);
        executorService.execute(task);
      } catch (RejectedExecutionException e) {
        logger.info("The metrics task was rejected by the executor", e);
      }
    }
  }
}
