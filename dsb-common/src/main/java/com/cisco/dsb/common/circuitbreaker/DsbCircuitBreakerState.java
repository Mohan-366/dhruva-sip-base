package com.cisco.dsb.common.circuitbreaker;

/*
 * This enum is an abstraction for CircuitBreakerState in Resilience4j.
 * We only use states CLOSED, OPEN and HALF-OPEN as of now.
 *
 * CLOSED: Circuit is closed. Calls will be allowed to endpoint
 * OPEN: Circuit is open. Calls will be blocked to endpoint.
 * HALF-OPEN: Circuit will allow configured number of calls before again opening or closing the
 * circuit based on endpoint response.
 */
public enum DsbCircuitBreakerState {
  DISABLED(3, false),
  METRICS_ONLY(5, true),
  CLOSED(0, true),
  OPEN(1, true),
  FORCED_OPEN(4, false),
  HALF_OPEN(2, true);

  public final boolean allowPublish;
  private final int order;

  private DsbCircuitBreakerState(int order, boolean allowPublish) {
    this.order = order;
    this.allowPublish = allowPublish;
  }
}
