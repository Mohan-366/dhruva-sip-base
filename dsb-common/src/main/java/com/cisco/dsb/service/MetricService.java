/*
 * Copyright (c) 2020  by Cisco Systems, Inc.All Rights Reserved.
 */

package com.cisco.dsb.service;

import static com.cisco.dsb.util.log.event.Event.DIRECTION.OUT;
import static com.cisco.dsb.util.log.event.Event.MESSAGE_TYPE.REQUEST;
import static com.cisco.dsb.util.log.event.Event.MESSAGE_TYPE.RESPONSE;

import com.cisco.dsb.common.executor.DhruvaExecutorService;
import com.cisco.dsb.common.executor.ExecutorType;
import com.cisco.dsb.common.metric.Metric;
import com.cisco.dsb.common.metric.MetricClient;
import com.cisco.dsb.common.metric.Metrics;
import com.cisco.dsb.transport.Connection;
import com.cisco.dsb.transport.Transport;
import com.cisco.dsb.util.log.event.Event.DIRECTION;
import com.cisco.dsb.util.log.event.Event.MESSAGE_TYPE;
import java.util.Set;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;
import org.jetbrains.annotations.NotNull;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

@Service
public class MetricService {

  private static final String DHRUVA = "dhruva";
  private static final String DOT = ".";
  private final ScheduledThreadPoolExecutor scheduledExecutor;
  private DhruvaExecutorService executorService;
  MetricClient metricClient;

  @Autowired
  public MetricService(MetricClient metricClient, DhruvaExecutorService executorService) {
    this.metricClient = metricClient;
    this.executorService = executorService;
    executorService.startScheduledExecutorService(ExecutorType.METRIC_SERVICE, 4);
    scheduledExecutor =
        this.executorService.getScheduledExecutorThreadPool(ExecutorType.METRIC_SERVICE);
  }

  public void registerPeriodicMetric(
      String measurement, Supplier<Set<Metric>> metricSupplier, int interval, TimeUnit timeUnit) {
    scheduledExecutor.scheduleAtFixedRate(
        getMetricFromSupplier(measurement, metricSupplier), interval, interval, timeUnit);
  }

  @NotNull
  private Runnable getMetricFromSupplier(String measurement, Supplier<Set<Metric>> metricSupplier) {
    return () -> {
      Set<Metric> metrics = metricSupplier.get();
      metrics.forEach(metric -> metric.measurement(prefixDhruvaToMeasurementName(measurement)));
      sendMetric(metrics);
    };
  }

  public void sendConnectionMetric(
      String localIp,
      int localPort,
      String remoteIp,
      int remotePort,
      Transport transport,
      DIRECTION direction,
      Connection.STATE connectionState) {

    Metric metric =
        Metrics.newMetric()
            .measurement("connection")
            .tag("transport", transport.toString())
            .tag("direction", direction.name())
            .tag("connectionState", connectionState.name())
            .field("localIp", localIp)
            .field("localPort", localPort)
            .field("remoteIp", remoteIp)
            .field("remotePort", remotePort);
    sendMetric(metric);
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
      String requestUri) {

    Metric metric =
        Metrics.newMetric()
            .measurement("sipMessage")
            .tag("method", method)
            .tag("messageType", messageType.name())
            .tag("direction", direction.name())
            .tag("isMidCall", isMidCall)
            .tag("transport", transport.name())
            .tag("isInternallyGenerated", isInternallyGenerated)
            .field("callId", callId)
            .field("cSeq", cseq);

    if (messageType == RESPONSE) {
      metric.field("responseCode", Integer.valueOf(method));
      metric.field("responseReason", requestUri);
    } else if (messageType == REQUEST) {
      metric.field("requestUri", requestUri);
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
}
