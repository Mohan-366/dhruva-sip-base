package com.cisco.dsb.common.metric;

import com.cisco.dsb.common.service.MetricService;
import com.cisco.wx2.util.Token;
import com.google.common.base.Strings;
import lombok.NonNull;

public class SipMetricsContext implements AutoCloseable {
  // SIP Call ID used to track metric timings
  // http://tools.ietf.org/html/rfc3261#section-8.1.1.4
  private String callId = Token.EmptyString;

  // Did the metric event that triggered this metric context succeed?
  private boolean success = false;

  private final MetricService metricsService;

  // Value to be set for a metric
  private final long value;

  public enum State {
    latencyIncomingNewRequestStart,
    latencyIncomingNewRequestEnd,
  }

  public State state;

  /**
   * Create a metrics context for emitting to InfluxDB.
   *
   * @param metricsService If null, no metric is sent
   * @param state Can't be null if metricsService not null
   * @param callId Sip call id related to this metric, if null or empty this field ignored
   * @param value Increase metric count by this amount
   * @param emitMetric If true, sends metric immediately and assumes event that generated this
   *     metric was successful
   */
  public SipMetricsContext(
      @NonNull MetricService metricsService,
      State state,
      String callId,
      long value,
      boolean emitMetric) {
    this.metricsService = metricsService;
    this.value = value;
    if (metricsService != null) {
      setCallId(callId);
      this.state = state;

      // If emitMetric is false one of these conditions must happen for metric to be sent:
      //
      //    (1) If using try-with-resources pattern, Java will then automatically
      //        call the close() method which sends the metric out.
      //
      //    (2) If not using try-with-resources, must call close() directly.
      //
      if (emitMetric) {
        this.success = true;
        close();
      }
    }
  }

  public SipMetricsContext(MetricService metricsService, State state, String callId) {
    this(metricsService, state, callId, false);
  }

  public SipMetricsContext(
      MetricService metricsService, State state, String callId, boolean emitMetric) {
    this(metricsService, state, callId, 1, emitMetric);
  }

  public SipMetricsContext(MetricService metricsService, State state, long value) {
    this(metricsService, state, null, value, true);
  }

  // Sets the callId for tracking metric events under this context. Note
  // that once set, it cannot be changed to ensure all metric timing
  // events follow the same ID.
  private void setCallId(String callId) {
    if (!Strings.isNullOrEmpty(callId) && Strings.isNullOrEmpty(this.callId)) {
      this.callId = callId;
    }
  }

  public String getCallId() {
    return callId;
  }

  public long getValue() {
    return value;
  }

  public void setState(State state) {
    this.state = state;
  }

  @Override
  public void close() {
    metricsService.handleMetricsEvent(this);
  }

  // The event that triggered this metric was successful.
  public void setSuccessful(String callId) {
    success = true;
    setCallId(callId);
  }

  public void setSuccessful() {
    setSuccessful(null);
  }

  public boolean isSuccessful() {
    return success;
  }
}
