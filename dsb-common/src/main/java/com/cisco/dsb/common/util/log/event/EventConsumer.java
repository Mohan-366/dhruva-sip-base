package com.cisco.dsb.common.util.log.event;

public abstract class EventConsumer {

  protected DhruvaEvent event;

  public EventConsumer(DhruvaEvent event) {
    this.event = event;
  }

  public abstract void handleEvent();
}
