package com.cisco.dsb.common.util.log.event;

public abstract class EventConsumer {

  public abstract void handleEvent(DhruvaEvent event);
}
