package com.cisco.dsb.common.record;

import com.cisco.wx2.util.Utilities;
import com.google.common.base.Ticker;
import java.util.*;
import java.util.concurrent.ConcurrentLinkedQueue;
import javax.annotation.Nullable;
import lombok.CustomLog;

@CustomLog
public class DhruvaAppRecord {

  private final ConcurrentLinkedQueue<Record> history;
  private final Set<DhruvaState> statesAdded;
  private final long creationTimeSinceEpochMs;
  private final Ticker ticker;

  public static final String PASSPORT_KEY = "passport";

  public DhruvaAppRecord() {
    this.history = new ConcurrentLinkedQueue<>();
    this.statesAdded = Collections.synchronizedSet(new HashSet<>());
    this.creationTimeSinceEpochMs = System.currentTimeMillis();
    this.ticker = Ticker.systemTicker();
  }

  public static DhruvaAppRecord create() {
    return new DhruvaAppRecord();
  }

  public DhruvaState getState() {
    assert history.peek() != null;
    return history.peek().getState();
  }

  public ConcurrentLinkedQueue<Record> getHistory() {
    return history;
  }

  public void add(DhruvaState state, @Nullable Utilities.Checks checks) {
    history.add(new Record(state, now(), checks));
    statesAdded.add(state);
  }

  public void addIfNotAlready(DhruvaState state, Utilities.Checks checks) {
    if (!statesAdded.contains(state)) {
      add(state, checks);
    }
  }

  private long now() {
    return ticker.read();
  }

  public long firstTime() {
    if (history.peek() != null) {
      return history.peek().getTime();
    }
    return 0;
  }

  public long creationTimeSinceEpochMs() {
    return creationTimeSinceEpochMs;
  }

  public long calculateTimeBetween(StartAndEnd sae) {
    if (sae.startNotFound() || sae.endNotFound()) {
      return 0;
    }
    return sae.endTime - sae.startTime;
  }

  public StartAndEnd findFirstStartAndLastEndStates(DhruvaState startState, DhruvaState endState) {
    StartAndEnd sae = new StartAndEnd();
    for (Record item : history) {
      if (sae.startNotFound() && item.getState() == startState) {
        sae.startTime = item.getTime();
      } else if (item.getState() == endState) {
        sae.endTime = item.getTime();
      }
    }
    return sae;
  }

  @Override
  public String toString() {
    long startTime = history.size() > 0 ? firstTime() : 0;
    long now = now();

    StringBuilder sb = new StringBuilder();
    sb.append("CurrentRecord {");
    sb.append("start_ms=").append(creationTimeSinceEpochMs()).append(", ");
    Object[] a = history.toArray();
    sb.append('[');
    for (Object item : a) {
      Record r = (Record) item;
      sb.append("elapsed_time_ns:")
          .append(r.getTime() - startTime)
          .append('=')
          .append(r.getState())
          .append(": description= ")
          .append(r.getChecks() != null ? r.getChecks().toStringAndClear() : "None")
          .append(", ");
    }
    sb.append("total_time_elapsed_ns:").append(now - startTime).append('=').append("NOW");
    sb.append(']');

    sb.append('}');

    return sb.toString();
  }
}
