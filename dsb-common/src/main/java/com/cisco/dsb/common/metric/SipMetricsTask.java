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

  private long getSplitTimeStopWatch(String metric) {
    return metricsService.getSplitTimeStopWatch(metricsContext.getCallId(), metric);
  }

  // Record timing operation for given metric
  private void time(String metric) {
    final long duration = getTime(metric);

    if (duration > 0) metricsService.time(metric, duration, TimeUnit.MILLISECONDS, metricsContext);
    else logger.debug("duration is invalid, skipping sending the metric: " + metric);
  }

  private void timeStopWatch(String metric) {
    long splitDuration = getSplitTimeStopWatch(metric);
    // Stop the stopwatch and clean the cache
    final long duration = getTimeStopWatch(metric);
    logger.debug("total duration {} split duration", duration, splitDuration);
    // If splitDuration is invalid due to invalid state , consider the total duration returned by
    // stop api of stop watch
    if (splitDuration < 0 && duration > 0) {
      splitDuration = duration;
    }
    // We will consider split time
    if (splitDuration > 0)
      metricsService.time(metric, splitDuration, TimeUnit.MILLISECONDS, metricsContext);
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
      case proxyNewRequestRetryNextElement:
        logger.debug(
            "split the request timer {} for event {}",
            metricsContext.getCallId(),
            metricsContext.state);
        metricsService.splitStopWatch(metricsContext.getCallId(), incomingRequest);
        break;
      case proxyNewRequestFinalResponseProcessed:
      case proxyRequestCancelReceived:
        logger.debug(
            "incoming new request processing latency time end {}", metricsContext.getCallId());
        timeStopWatch(incomingRequest);
        break;
      default:
        break;
    }
  }
}
