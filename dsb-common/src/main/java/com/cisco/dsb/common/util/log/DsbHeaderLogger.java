package com.cisco.dsb.common.util.log;

import gov.nist.javax.sip.message.SIPMessage;
import lombok.CustomLog;

/** Log SIP messages only when debug is enabled. */
@CustomLog
public class DsbHeaderLogger extends DhruvaServerLogger {

  @Override
  protected void log(
      SIPMessage message, String from, String to, String status, boolean sender, long time) {
    if (logger.isDebugEnabled() || logger.isInfoEnabled()) {
      // If debug is enabled, then use the super.log method to log the entire message content.
      super.log(message, from, to, status, sender, time);
    }
  }
}
