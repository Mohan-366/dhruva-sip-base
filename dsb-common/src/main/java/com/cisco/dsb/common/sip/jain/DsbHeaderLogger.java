package com.cisco.dsb.common.sip.jain;

import com.cisco.dsb.common.util.log.DhruvaLoggerFactory;
import com.cisco.dsb.common.util.log.Logger;
import gov.nist.javax.sip.message.SIPMessage;

/** Log SIP messages only when debug is enabled. */
public class DsbHeaderLogger extends DhruvaServerLogger {

  private static final Logger logger = DhruvaLoggerFactory.getLogger(DsbHeaderLogger.class);

  @Override
  protected void log(
      SIPMessage message, String from, String to, String status, boolean sender, long time) {
    if (logger.isDebugEnabled()) {
      // If debug is enabled, then use the super.log method to log the entire message content.
      super.log(message, from, to, status, sender, time);
    }
  }
}
