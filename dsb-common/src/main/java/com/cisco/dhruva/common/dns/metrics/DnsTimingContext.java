package com.cisco.dhruva.common.dns.metrics;

/** Implement to handle timings when performing dns requests. */
@FunctionalInterface
public interface DnsTimingContext {
  void stop(String query, String queryType, String errorMsg);
}
