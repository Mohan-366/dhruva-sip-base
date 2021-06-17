/*
 * Copyright (c) 2020  by Cisco Systems, Inc.All Rights Reserved.
 */

package com.cisco.dhruva.common.metric;

import com.cisco.wx2.metrics.InfluxPoint;
import com.cisco.wx2.server.InfluxDBClientHelper;
import java.time.Instant;
import javax.inject.Inject;

public class InfluxMetric implements Metric {

  @Inject private static InfluxDBClientHelper influxDBClientHelper;

  private InfluxPoint influxPoint = new InfluxPoint();

  public static InfluxMetric newInstance() {
    return new InfluxMetric();
  }

  @Override
  public Metric measurement(String measurement) {
    influxPoint.setMeasurement(measurement);
    return this;
  }

  @Override
  public String measurement() {
    return influxPoint.getMeasurement();
  }

  @Override
  public Metric timestamp(Instant timestamp) {
    influxPoint.setTimestamp(timestamp);
    return this;
  }

  @Override
  public Metric tag(String tagName, boolean value) {
    influxPoint.addTag(tagName, value);
    return this;
  }

  @Override
  public Metric tag(String tagName, int value) {
    influxPoint.addTag(tagName, value);
    return this;
  }

  @Override
  public Metric tag(String tagName, String value) {
    influxPoint.addTag(tagName, value);
    return this;
  }

  @Override
  public Metric field(String fieldName, Object value) {
    influxPoint.addField(fieldName, value);
    return this;
  }

  @Override
  public InfluxPoint get() {
    return influxPoint;
  }
}
