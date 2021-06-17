/*
 * Copyright (c) 2020  by Cisco Systems, Inc.All Rights Reserved.
 */

package com.cisco.dhruva.common.metric;

import com.cisco.wx2.metrics.InfluxPoint;
import com.cisco.wx2.server.InfluxDBClientHelper;
import java.time.Instant;
import java.util.Set;
import java.util.stream.Collectors;
import javax.inject.Inject;

public class InfluxClient implements MetricClient {

  @Inject private InfluxDBClientHelper influxDBClientHelper;

  @Override
  public void sendMetric(Metric metric) {
    metric.timestamp(Instant.now());
    influxDBClientHelper.writePointAsync((InfluxPoint) metric.get());
  }

  @Override
  public void sendMetrics(Set<Metric> metrics) {
    Set<InfluxPoint> influxPoints =
        metrics.stream()
            .map(metric -> metric.timestamp(Instant.now()))
            .map(metric -> (InfluxPoint) metric.get())
            .collect(Collectors.toSet());
    if (!influxPoints.isEmpty()) {
      influxDBClientHelper.writePoints(influxPoints);
    }
  }
}
