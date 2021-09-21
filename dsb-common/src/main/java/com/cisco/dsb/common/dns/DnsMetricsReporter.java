package com.cisco.dsb.common.dns;

import com.cisco.dsb.common.dns.metrics.DnsReporter;
import com.cisco.dsb.common.dns.metrics.DnsTimingContext;
import com.cisco.dsb.common.service.MetricService;
import lombok.CustomLog;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

@Component
@CustomLog
public class DnsMetricsReporter implements DnsReporter {

  @Autowired public MetricService metricsService;


  @Override
  public DnsTimingContext resolveTimer() {
    return new DnsTimingContext() {
      private final long start = System.currentTimeMillis();

      @Override
      public void stop(String query, String queryType, String errorMsg) {
        final long now = System.currentTimeMillis();
        final long diff = now - start;
        metricsService.sendDNSMetric(query, queryType, diff, errorMsg);
      }
    };
  }

  @Override
  public void reportFailure(String query, String queryType, Throwable error) {
    logger.error(
        "error while resolving query {} of type {}. Exception cause: {}",
        query,
        queryType,
        error.getCause());
  }

  @Override
  public void reportEmpty(String query, String queryType) {
    logger.error("got empty records while resolving query {} type {} ", query, queryType);
  }
}
