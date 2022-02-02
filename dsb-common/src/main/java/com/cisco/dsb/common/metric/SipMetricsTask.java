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

  private long getTimeStopWatch(String metric) {
    return metricsService.endStopWatch(metricsContext.getCallId(), metric);
  }

  // Record timing operation for given metric
  private void time(String metric) {
    final long duration = getTime(metric);

    if (duration > 0) metricsService.time(metric, duration, TimeUnit.MILLISECONDS, metricsContext);
    else logger.debug("duration is invalid, skipping sending the metric: " + metric);
  }

  private void timeStopWatch(String metric) {
    final long duration = getTimeStopWatch(metric);

    if (duration > 0) metricsService.time(metric, duration, TimeUnit.MILLISECONDS, metricsContext);
    else logger.debug("duration is invalid, skipping sending the metric: {}", metric);
  }

  @Override
  @SuppressWarnings({"checkstyle:cyclomaticcomplexity", "checkstyle:methodlength"})
  public void run() {
    handleState();
  }

  private void handleState() {
    switch (metricsContext.state) {
      case proxyNewRequestReceived:
        logger.debug("incoming new request timer start {}", metricsContext.getCallId());
        metricsService.startStopWatch(metricsContext.getCallId(), incomingRequest);
        break;
      case proxyNewRequestSendSuccess:
      case proxyNewRequestSendFailure:
        logger.debug("pause the request timer {}", metricsContext.getCallId());
        metricsService.pauseStopWatch(metricsContext.getCallId(), incomingRequest);
        break;
      case proxyNewRequestRetryNextElement:
        logger.debug("resume the request timer {}", metricsContext.getCallId());
        metricsService.resumeStopWatch(metricsContext.getCallId(), incomingRequest);
        break;
      case proxyNewRequestFinalResponseProcessed:
        logger.debug(
            "incoming new request processing latency time end {}", metricsContext.getCallId());
        timeStopWatch(incomingRequest);
        break;
      default:
        break;
    }
  }
}
