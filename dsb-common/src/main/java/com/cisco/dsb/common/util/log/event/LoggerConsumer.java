package com.cisco.dsb.common.util.log.event;

import com.cisco.dsb.common.util.log.LogUtils;
import gov.nist.javax.sip.message.SIPMessage;
import lombok.CustomLog;

@CustomLog
public class LoggerConsumer extends EventConsumer {

  private boolean allowUnmasked;

  public LoggerConsumer(DhruvaEvent event, boolean allowUnmasked) {
    super(event);
    this.allowUnmasked = allowUnmasked;
  }

  public void sendMaskedEvent() {
    LoggingEvent loggingEvent = (LoggingEvent) event;
    String msg = null;

    // Mask/obfuscate the sip msg (or) normal string msg whichever is available
    if (loggingEvent.getSipMsgPayload() != null) {
      // this sub-type is set only when sip msg is there (i.e) for SIPMESSAGE events only
      loggingEvent.setEventSubType(Event.EventSubType.PIIMASKED);
      // do not include appRecord (passport data) for masked event, as there is PII info in this
      if (loggingEvent.getEventInfoMap() != null
          && loggingEvent.getEventInfoMap().get("appRecord") != null) {
        loggingEvent.getEventInfoMap().replace("appRecord", null);
      }
      msg = LogUtils.obfuscateObject((SIPMessage) loggingEvent.getSipMsgPayload(), false);
    } else if (loggingEvent.getMsgPayload() != null) {
      msg = loggingEvent.getMsgPayload();
    }
    sendMsg(msg);
  }

  public void sendUnmaskedEvent() {
    LoggingEvent loggingEvent = (LoggingEvent) event;
    String msg = null;

    if (allowUnmasked) {
      if (loggingEvent.getSipMsgPayload() != null) {
        // this sub-type is set only when sip msg is there (i.e) for SIPMESSAGE events only
        loggingEvent.setEventSubType(Event.EventSubType.PIIUNMASKED);
        msg = loggingEvent.getSipMsgPayload().toString();

      } else if (loggingEvent.getMsgPayload() != null) {
        msg = loggingEvent.getMsgPayload();
      }
      sendMsg(msg);
    }
  }

  public void sendMsg(String msg) {
    LoggingEvent loggingEvent = (LoggingEvent) event;
    if (loggingEvent.getErrorType() == null) {
      logger.emitEvent(
          loggingEvent.getEventType(),
          loggingEvent.getEventSubType(),
          msg,
          loggingEvent.getEventInfoMap());
    } else {
      logger.emitEvent(
          loggingEvent.getEventType(),
          loggingEvent.getEventSubType(),
          loggingEvent.getErrorType(),
          msg,
          loggingEvent.getEventInfoMap(),
          loggingEvent.getThrowable());
    }
    logger.debug("Event {} sent to Logging 2.0 backend", loggingEvent.getEventType());
  }

  @Override
  public void handleEvent() {
    sendUnmaskedEvent();
    sendMaskedEvent();
  }
}
