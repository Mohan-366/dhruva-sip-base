package com.cisco.dsb.common.util.log;

import gov.nist.core.StackLogger;
import gov.nist.javax.sip.message.SIPMessage;

public enum LogMsgType {
  LOGDEBUG {
    @Override
    public void apply(StackLogger logger, String message) {
      logger.logDebug(message);
    }
  },
  LOGINFO {
    @Override
    public void apply(StackLogger logger, String message) {
      logger.logInfo(message);
    }
  };

  protected static LogMsgType getLogMsgType(SIPMessage message, String status, boolean sender) {
    if (status != null && !sender) {
      return LogMsgType.LOGDEBUG;
    } else if (message.getCSeq().getMethod().equalsIgnoreCase("OPTIONS")) {
      return LogMsgType.LOGDEBUG;
    }
    return LogMsgType.LOGINFO;
  }

  public abstract void apply(StackLogger logger, String message);
}
