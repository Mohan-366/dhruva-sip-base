package com.cisco.dsb.common.metric;

import static com.cisco.dsb.common.service.MetricService.joiner;

import com.cisco.dsb.common.service.MetricService;
import com.google.common.base.Preconditions;
import java.util.concurrent.TimeUnit;
import lombok.CustomLog;

@CustomLog
public class SipMetricsTask implements Runnable {

  private final MetricService metricsService;
  private final SipMetricsContext metricsContext;

  // call state setup
  private static final String call = "call";

  private static final String incomingRequest = joiner.join(call, "latency");

  public SipMetricsTask(MetricService metricsService, SipMetricsContext ctx) {
    this.metricsService = Preconditions.checkNotNull(metricsService);
    this.metricsContext = Preconditions.checkNotNull(ctx);
  }

  // Returns timing duration in milliseconds
  private long getTime(String metric) {
    return metricsService.stopTimer(metricsContext.getCallId(), metric);
  }

  // Record timing operation for given metric
  private void time(String metric) {
    final long duration = getTime(metric);

    if (duration > 0) metricsService.time(metric, duration, TimeUnit.MILLISECONDS, metricsContext);
    else logger.debug("duration is invalid, skipping sending the metric: " + metric);
  }

  @Override
  @SuppressWarnings({"checkstyle:cyclomaticcomplexity", "checkstyle:methodlength"})
  public void run() {
    handleState();
  }

  private void handleState() {
    switch (metricsContext.state) {
      case latencyIncomingNewRequestStart:
        logger.debug("incoming new request timer start");
        metricsService.startTimer(metricsContext.getCallId(), incomingRequest);
        break;
      case latencyIncomingNewRequestEnd:
        logger.debug("incoming new request timer end");
        time(incomingRequest);
        break;
      default:
        break;
    }
  }
}
