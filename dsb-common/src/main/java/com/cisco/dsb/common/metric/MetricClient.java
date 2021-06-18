/*
 * Copyright (c) 2020  by Cisco Systems, Inc.All Rights Reserved.
 */

package com.cisco.dsb.common.metric;

import java.util.Set;

public interface MetricClient {

  public void sendMetric(Metric metric);

  void sendMetrics(Set<Metric> metrics);
}
