/*
 * Copyright (c) 2020  by Cisco Systems, Inc.All Rights Reserved.
 */

package com.cisco.dsb.common.metric;

import java.time.Instant;
import java.util.function.Supplier;

public interface Metric extends Supplier {

  public Metric measurement(String measurement);

  public String measurement();

  public Metric timestamp(Instant timestamp);

  public Metric tag(String tagName, boolean value);

  public Metric tag(String tagName, int value);

  public Metric tag(String tagName, String value);

  public Metric field(String fieldName, Object value);
}
