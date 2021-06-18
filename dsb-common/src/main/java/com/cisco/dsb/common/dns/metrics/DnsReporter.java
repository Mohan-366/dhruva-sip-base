package com.cisco.dsb.common.dns.metrics;

/** Implement to report statistics for DNS request. */
public interface DnsReporter {
  /**
   * Report resolve timing.
   *
   * @return A new timing context.
   */
  DnsTimingContext resolveTimer();

  /** Report that an empty response has been received from a resolve. */
  void reportEmpty(String query, String queryType);

  /**
   * Report that a resolve resulting in a failure.
   *
   * @param error The exception causing the failure.
   */
  void reportFailure(String query, String queryType, Throwable error);
}
