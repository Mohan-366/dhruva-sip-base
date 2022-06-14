package com.cisco.dsb.common.util.log.event;

public class MatsEvent extends DhruvaEvent {

  // set of all data to construct a MatsEvent
  public MatsEvent(MatsEventBuilder builder) {}

  public static class MatsEventBuilder {

    public MatsEventBuilder() {}

    public MatsEvent build() {
      return new MatsEvent(this);
    }
  }
}
