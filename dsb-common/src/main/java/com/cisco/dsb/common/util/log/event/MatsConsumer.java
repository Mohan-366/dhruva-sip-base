package com.cisco.dsb.common.util.log.event;

import lombok.CustomLog;

@CustomLog
public class MatsConsumer extends EventConsumer {

  public MatsConsumer(DhruvaEvent event) {
    super(event);
  }

  @Override
  public void handleEvent() {
    // does not do anything
  }
}
