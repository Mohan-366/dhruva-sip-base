/*
 * Copyright (c) 2020  by Cisco Systems, Inc.All Rights Reserved.
 */

package com.cisco.dsb.common.util.log.event;

import com.cisco.dsb.common.record.DhruvaAppRecord;
import com.cisco.dsb.common.servergroup.ServerGroupElement;
import com.cisco.dsb.common.sip.stack.dto.BindingInfo;
import com.cisco.dsb.common.ua.SessionId;
import com.cisco.dsb.common.util.LMAUtil;
import com.cisco.dsb.common.util.SpringApplicationContext;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Maps;
import gov.nist.javax.sip.header.CSeq;
import gov.nist.javax.sip.message.SIPRequest;
import gov.nist.javax.sip.message.SIPResponse;
import java.util.*;
import javax.sip.message.Message;
import lombok.CustomLog;
import lombok.Setter;

@CustomLog
public class Event {

  @Setter
  private static EventingService eventingService =
      SpringApplicationContext.getAppContext() == null
          ? null
          : SpringApplicationContext.getAppContext().getBean(EventingService.class);

  private static final String ISMIDDIALOG = "isMidDialog";
  private static final String ISINTERNALLYGENERATED = "isInternallyGenerated";

  private static final String ISRETRANSMITTED = "isRetransmitted";
  public static String DIRECTION_KEY = "direction";
  public static String REMOTEIP = "remoteIp";
  public static String REMOTEPORT = "remotePort";
  public static String LOCALIP = "localIp";
  public static String LOCALPORT = "localPort";

  public enum EventType {
    CONNECTION,
    SIPMESSAGE,
    SERVERGROUP_ELEMENT_EVENT,
    SERVERGROUP_EVENT
  }

  public enum MESSAGE_TYPE {
    REQUEST,
    RESPONSE
  }

  public enum DIRECTION {
    IN,
    OUT
  }

  public enum EventSubType {
    UDPCONNECTION(EventType.CONNECTION),
    TCPCONNECTION(EventType.CONNECTION),
    TLSCONNECTION(EventType.CONNECTION),
    PIIMASKED(EventType.SIPMESSAGE),
    PIIUNMASKED(EventType.SIPMESSAGE);

    private EventType eventType;

    EventSubType(EventType eventType) {
      this.eventType = eventType;
    }

    public EventType getEventType() {
      return this.eventType;
    }
  }

  public enum ErrorType {
    ConnectionError,
    BufferSizeExceeded,
    SslHandShakeFailed,
    ConnectionInActive,
    ServerGroupElementDown,
    ServerGroupDown
  }

  public static void emitMessageEvent(
      BindingInfo messageBindingInfo,
      Message message,
      DIRECTION direction,
      MESSAGE_TYPE sipMessageType,
      boolean isInternallyGenerated,
      boolean isMidDialog,
      boolean isRetransmitted,
      DhruvaAppRecord appRecord,
      EventingService eventService) {

    String remoteAddress =
        messageBindingInfo.getRemoteAddress() != null
            ? Optional.ofNullable(messageBindingInfo.getRemoteAddress().getHostAddress()).orElse("")
            : "";

    Map<String, String> messageInfoMap =
        Maps.newHashMap(
            ImmutableMap.of(
                "sipMessageType",
                sipMessageType.name(),
                "cseqMethod",
                String.valueOf(message.getHeader(CSeq.NAME)),
                Event.REMOTEIP,
                remoteAddress));

    if (MESSAGE_TYPE.REQUEST.equals(sipMessageType)) {
      SIPRequest sipRequest = (SIPRequest) message;
      messageInfoMap.put("sipMethod", String.valueOf(sipRequest.getMethod()));
    } else {
      SIPResponse sipResponse = (SIPResponse) message;
      messageInfoMap.put("responseCode", String.valueOf(sipResponse.getStatusCode()));
      messageInfoMap.put("reasonPhrase", String.valueOf(sipResponse.getReasonPhrase()));
    }

    CSeq cseqHeaderValue = (CSeq) message.getHeader(CSeq.NAME);
    if (cseqHeaderValue != null) {
      messageInfoMap.put("cseqMethod", cseqHeaderValue.encodeBody());
    }

    messageInfoMap.put(Event.REMOTEPORT, String.valueOf(messageBindingInfo.getRemotePort()));
    messageInfoMap.put(Event.DIRECTION_KEY, direction.name());

    String localHostAddress =
        messageBindingInfo.getLocalAddress() != null
            ? Optional.ofNullable(messageBindingInfo.getLocalAddress().getHostAddress()).orElse("")
            : "";
    messageInfoMap.put(Event.LOCALIP, localHostAddress);
    messageInfoMap.put(Event.LOCALPORT, String.valueOf(messageBindingInfo.getLocalPort()));
    messageInfoMap.put(Event.ISMIDDIALOG, String.valueOf(isMidDialog));
    // DSB TODO
    messageInfoMap.put(Event.ISINTERNALLYGENERATED, String.valueOf(isInternallyGenerated));
    messageInfoMap.put(Event.ISRETRANSMITTED, String.valueOf(isRetransmitted));
    if (appRecord != null) {
      messageInfoMap.put("appRecord", appRecord.toString());
    }

    SessionId sessionId = SessionId.extractFromSipEvent(message);
    if (sessionId != null) {
      messageInfoMap.put("localSessionId", sessionId.getLocalSessionId());
      messageInfoMap.put("remoteSessionId", sessionId.getRemoteSessionId());
    }
    // TODO LMAUtil and Event classes needs to be refactored and made spring classes
    if (eventingService == null && eventService != null) {
      eventingService = eventService;
    }

    if (eventingService != null) {
      LoggingEvent event =
          new LoggingEvent.LoggingEventBuilder()
              .eventType(EventType.SIPMESSAGE)
              .eventInfoMap(messageInfoMap)
              .sipMsgPayload(message)
              .build();
      // generate other necessary events
      List<DhruvaEvent> events = new ArrayList<>();
      events.add(event);
      eventingService.publishEvents(events);
    }

    // DSB TODO
    //    SipMessageWrapper sipMsg = new SipMessageWrapper(message, direction);
    //    Consumer<SipMessageWrapper> consumer1 = DiagnosticsUtil::sendCDSEvents;
    //    consumer1.accept(sipMsg);
  }

  public static void emitSGElementUpEvent(ServerGroupElement sge, String networkName) {
    Map<String, String> eventInfoMap =
        Maps.newHashMap(
            ImmutableMap.of(
                Event.REMOTEIP,
                sge.getIpAddress(),
                Event.REMOTEPORT,
                String.valueOf(sge.getPort()),
                "network",
                networkName));
    String msg = "ServerGroup Element UP: " + sge;

    if (eventingService != null) {
      LoggingEvent event =
          new LoggingEvent.LoggingEventBuilder()
              .eventType(EventType.SERVERGROUP_ELEMENT_EVENT)
              .eventInfoMap(eventInfoMap)
              .msgPayload(msg)
              .build();
      // generate other necessary events
      List<DhruvaEvent> events = new ArrayList<>();
      events.add(event);
      eventingService.publishEvents(events);
    }
  }

  public static void emitSGEvent(String serverGroupName, boolean isDown) {
    String msg;
    Map<String, String> eventInfoMap =
        Maps.newHashMap(ImmutableMap.of("serverGroupName", serverGroupName));
    if (isDown) {
      eventInfoMap.put("errorType", ErrorType.ServerGroupDown.name());
      eventInfoMap.put("status", "down");
      msg = "ServerGroup DOWN: " + serverGroupName;
    } else {
      eventInfoMap.put("status", "up");
      msg = "ServerGroup UP: " + serverGroupName;
    }

    if (eventingService != null) {
      LoggingEvent event =
          new LoggingEvent.LoggingEventBuilder()
              .eventType(EventType.SERVERGROUP_EVENT)
              .eventInfoMap(eventInfoMap)
              .msgPayload(msg)
              .build();
      // generate other necessary events
      List<DhruvaEvent> events = new ArrayList<>();
      events.add(event);
      eventingService.publishEvents(events);
    }
  }

  public static void emitSGElementDownEvent(
      Integer errorCode, String errorReason, ServerGroupElement sge, String networkName) {
    Map<String, String> eventInfoMap =
        Maps.newHashMap(
            ImmutableMap.of(
                "errorType",
                ErrorType.ServerGroupElementDown.name(),
                "errorReason",
                errorReason,
                Event.REMOTEIP,
                sge.getIpAddress(),
                Event.REMOTEPORT,
                String.valueOf(sge.getPort()),
                "network",
                networkName));
    if (errorCode != null) {
      eventInfoMap.put("errorCode", String.valueOf(errorCode));
    }
    eventInfoMap.put("transport", sge.getTransport().name());

    String msg = "ServerGroup Element DOWN: " + sge.toUniqueElementString() + " : " + sge;

    if (eventingService != null) {
      LoggingEvent event =
          new LoggingEvent.LoggingEventBuilder()
              .eventType(EventType.SERVERGROUP_ELEMENT_EVENT)
              .eventInfoMap(eventInfoMap)
              .msgPayload(msg)
              .build();
      // generate other necessary events
      List<DhruvaEvent> events = new ArrayList<>();
      events.add(event);
      eventingService.publishEvents(events);
    }
  }

  public static void emitConnectionErrorEvent(
      String transport, Map<String, String> additionalKeyValueInfo, Exception ex) {

    if (eventingService != null) {
      LoggingEvent event =
          new LoggingEvent.LoggingEventBuilder()
              .eventType(EventType.CONNECTION)
              .eventSubType(LMAUtil.getEventSubTypeFromTransport(transport))
              .eventInfoMap(additionalKeyValueInfo)
              .errorType(ErrorType.ConnectionError)
              .throwable(ex)
              .msgPayload(ex.getMessage())
              .build();
      // generate other necessary events
      List<DhruvaEvent> events = new ArrayList<>();
      events.add(event);
      eventingService.publishEvents(events);
    }
  }

  public static void emitHandshakeFailureEvent(
      String transport, Map<String, String> additionalKeyValueInfo, Exception ex) {

    if (eventingService != null) {
      LoggingEvent event =
          new LoggingEvent.LoggingEventBuilder()
              .eventType(EventType.CONNECTION)
              .eventSubType(LMAUtil.getEventSubTypeFromTransport(transport))
              .eventInfoMap(additionalKeyValueInfo)
              .errorType(ErrorType.SslHandShakeFailed)
              .throwable(ex)
              .msgPayload(ex.getMessage())
              .build();
      // generate other necessary events
      List<DhruvaEvent> events = new ArrayList<>();
      events.add(event);
      eventingService.publishEvents(events);
    }
  }
}
