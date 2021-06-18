/*
 * Copyright (c) 2020  by Cisco Systems, Inc.All Rights Reserved.
 */

package com.cisco.dsb.common.metric;

public final class Metrics {

  public enum MetricType {
    INFLUX
  }

  public static Metric newMetric() {
    return new InfluxMetric();
  }
}
