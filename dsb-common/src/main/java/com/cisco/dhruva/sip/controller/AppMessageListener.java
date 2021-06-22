package com.cisco.dhruva.sip.controller;

import com.cisco.dsb.common.messaging.DSIPMessage;
import java.util.EventListener;

@FunctionalInterface
public interface AppMessageListener extends EventListener {

  /**
   * Invoked when a message is received for the proxy
   *
   * @param message the message that is received
   */
  void onMessage(DSIPMessage message);
}
