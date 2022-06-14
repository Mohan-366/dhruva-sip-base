package com.cisco.dsb.common.util.log.event;

import java.util.Map;
import javax.sip.message.Message;
import lombok.Getter;
import lombok.Setter;

public class LoggingEvent extends DhruvaEvent {

  @Getter @Setter private Event.EventType eventType;
  @Getter @Setter private Event.EventSubType eventSubType;
  @Getter @Setter private Map<String, String> eventInfoMap;
  @Getter @Setter private Event.ErrorType errorType;
  @Getter @Setter private Throwable throwable;
  @Getter @Setter private Message sipMsgPayload;
  @Getter @Setter private String msgPayload;

  public LoggingEvent(LoggingEventBuilder builder) {
    this.eventType = builder.eventType;
    this.eventSubType = builder.eventSubType;
    this.eventInfoMap = builder.eventInfoMap;
    this.errorType = builder.errorType;
    this.throwable = builder.throwable;
    this.sipMsgPayload = builder.sipMsgPayload;
    this.msgPayload = builder.msgPayload;
  }

  public static class LoggingEventBuilder {
    private Event.EventType eventType;
    private Event.EventSubType eventSubType;
    private Map<String, String> eventInfoMap;
    private Event.ErrorType errorType;
    private Throwable throwable;
    private Message sipMsgPayload;
    private String msgPayload;

    public LoggingEventBuilder() {}

    public LoggingEventBuilder eventType(Event.EventType eventType) {
      this.eventType = eventType;
      return this;
    }

    public LoggingEventBuilder eventSubType(Event.EventSubType eventSubType) {
      this.eventSubType = eventSubType;
      return this;
    }

    public LoggingEventBuilder eventInfoMap(Map<String, String> eventInfoMap) {
      this.eventInfoMap = eventInfoMap;
      return this;
    }

    public LoggingEventBuilder errorType(Event.ErrorType errorType) {
      this.errorType = errorType;
      return this;
    }

    public LoggingEventBuilder throwable(Throwable throwable) {
      this.throwable = throwable;
      return this;
    }

    public LoggingEventBuilder sipMsgPayload(Message sipMsgPayload) {
      this.sipMsgPayload = sipMsgPayload;
      return this;
    }

    public LoggingEventBuilder msgPayload(String msgPayload) {
      this.msgPayload = msgPayload;
      return this;
    }

    public LoggingEvent build() {
      return new LoggingEvent(this);
    }
  }
}
