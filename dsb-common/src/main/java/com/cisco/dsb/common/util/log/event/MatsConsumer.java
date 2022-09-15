package com.cisco.dsb.common.util.log.event;

import lombok.CustomLog;

@CustomLog
public class MatsConsumer extends EventConsumer {

  @Override
  public void handleEvent(DhruvaEvent event) {
    // does not do anything
  }
}
