/*
 * Copyright (c) 2020  by Cisco Systems, Inc.All Rights Reserved.
 */

package com.cisco.dsb.common.metric;

import com.cisco.dsb.common.config.DhruvaProperties;
import com.cisco.wx2.metrics.InfluxPoint;
import com.cisco.wx2.server.InfluxDBClientHelper;
import java.time.Instant;
import java.util.Set;
import java.util.stream.Collectors;
import javax.annotation.PreDestroy;
import javax.inject.Inject;
import org.apache.commons.lang3.StringUtils;

public class InfluxClient implements MetricClient {

  @Inject private InfluxDBClientHelper influxDBClientHelper;
  @Inject private DhruvaProperties dhruvaProperties;

  public static final String INSTANCE_NAME_KEY = "instanceName";

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

  private String getInstanceName() {

    StringBuilder instanceName = new StringBuilder();

    if (StringUtils.isNotBlank(dhruvaProperties.getEnvironment())) {
      instanceName.append(dhruvaProperties.getEnvironment());
    }
    if (StringUtils.isNotBlank(dhruvaProperties.getPodNameEnvVar())) {
      instanceName.append("-");
      instanceName.append(dhruvaProperties.getPodNameEnvVar());
    }

    return (instanceName == null) ? "" : instanceName.toString();
  }

  @PreDestroy
  private void destroy() {
    influxDBClientHelper.shutdown();
  }
}
