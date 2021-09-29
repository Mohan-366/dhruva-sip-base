package com.cisco.dsb.common.metric;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import java.util.concurrent.TimeUnit;

/**
 * Allows adding @DsbTimed annotation to any method in order to time it and send the data to
 * InfluxDB via our SipMetricsService.
 */
@Target({ElementType.METHOD})
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface DsbTimed {
  /**
   * The influxdb measurement name
   *
   * <p>Things to keep in mind:
   *
   * <p>- Don't start metric name with "l2sip" as we automatically prefix that. Otherwise you'll end
   * up with "l2sip.l2sip.metric.name"
   *
   * <p>- The field "duration" will automatically be added to given value. For instance, don't use
   * "handlerMethod.time" just use "handlerMethod"
   */
  String name();

  /** Timeout duration' */
  long timeout() default 0;

  /** Unit of time that timeout value is given in */
  TimeUnit timeunit() default TimeUnit.MILLISECONDS;
}
