package com.cisco.dsb.common.record;

public class StartAndEnd {
  long startTime = -1;
  long endTime = -1;

  public long getStart() {
    return startTime;
  }

  public long getEnd() {
    return endTime;
  }

  boolean startNotFound() {
    return startTime == -1;
  }

  boolean endNotFound() {
    return endTime == -1;
  }
}
