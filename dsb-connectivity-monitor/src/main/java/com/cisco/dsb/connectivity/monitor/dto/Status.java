package com.cisco.dsb.connectivity.monitor.dto;

import java.util.concurrent.atomic.AtomicLong;
import lombok.CustomLog;
import lombok.Getter;

@Getter
@CustomLog
public class Status {
  private boolean up;
  private final AtomicLong timestamp;

  public Status(boolean up, long timestamp) {
    this.up = up;
    this.timestamp = new AtomicLong(timestamp);
  }
  /**
   * Status is considered valid if currentTime - timestamp is less than validity. If the status is
   * deemed invalid then update the timestamp atomically to currentTime NOTE: Supports concurrent
   * access. Updates timestamp atomically if expired.
   *
   * @param validity - amount of time(in ms) for which this status can be considered valid
   * @return true if status is expired, false otherwise
   */
  public boolean updateIfExpired(long validity) {
    long prevTs = timestamp.get();
    long currentTs = System.currentTimeMillis();
    logger.debug("Prev Timestamp ={}, CurrentTs ={} Validity ={}", prevTs, currentTs, validity);
    // 500ms is added because of rogue behaviour of delayElements of flux. Delay is not very
    // accurate and can repeat
    // even before timeperiod by couple of 100ms. Having a tolerance of 500ms makes sure we ping the
    // element if the repeat
    // was triggered with (Tp-500)ms
    if (currentTs - prevTs < validity - 500) {
      return false;
    }
    return timestamp.compareAndSet(prevTs, System.currentTimeMillis());
  }

  public Status setUp(boolean up) {
    this.up = up;
    return this;
  }

  @Override
  public boolean equals(Object obj) {
    if (this == obj) return true;
    if (obj instanceof Status) {
      Status that = ((Status) obj);
      return this.up == that.up;
    }
    return false;
  }
}
