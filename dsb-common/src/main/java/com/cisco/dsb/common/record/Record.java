package com.cisco.dsb.common.record;

import com.cisco.wx2.util.Utilities;

public class Record {
  private final long time;
  private final DhruvaState state;
  private final Utilities.Checks checks;

  public Record(DhruvaState state, long time, Utilities.Checks checks) {
    this.time = time;
    this.state = state;
    this.checks = checks;
  }

  public long getTime() {
    return time;
  }

  public DhruvaState getState() {
    return state;
  }

  public Utilities.Checks getChecks() {
    return checks;
  }

  @Override
  public String toString() {
    return time + "=" + state + "=" + checks.toStringAndClear();
  }
}
