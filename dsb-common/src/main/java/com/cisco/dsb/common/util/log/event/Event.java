/*
 * Copyright (c) 2020  by Cisco Systems, Inc.All Rights Reserved.
 */

package com.cisco.dsb.common.util.log.event;

import com.cisco.dsb.common.dto.RateLimitInfo;
import com.cisco.dsb.common.record.DhruvaAppRecord;
import com.cisco.dsb.common.servergroup.ServerGroupElement;
import com.cisco.dsb.common.sip.stack.dto.BindingInfo;
import com.cisco.dsb.common.ua.SessionId;
import com.cisco.dsb.common.util.LMAUtil;
import com.cisco.dsb.common.util.SpringApplicationContext;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Maps;
import gov.nist.javax.sip.address.SipUri;
import gov.nist.javax.sip.header.CSeq;
import gov.nist.javax.sip.message.SIPMessage;
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
  private static final String SIP_MESSAGE_TYPE = "sipMessageType";

  private static final String ISRETRANSMITTED = "isRetransmitted";
  public static String DIRECTION_KEY = "direction";
  public static String REMOTEIP = "remoteIp";
  public static String REMOTEPORT = "remotePort";
  public static String LOCALIP = "localIp";
  public static String LOCALPORT = "localPort";
  public static String CALLING_NUMBER = "callingNumber";
  public static String CALLED_NUMBER = "calledNumber";
  public static String INBOUND_NETWORK = "inboundNetwork";
  public static String OUTBOUND_NETWORK = "outboundNetwork";

  public enum EventType {
    CONNECTION,
    SIPMESSAGE,
    SERVERGROUP_ELEMENT_EVENT,
    SERVERGROUP_EVENT,
    RATE_LIMITER
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
      String inboundNetwork,
      String outboundNetwork,
      DhruvaAppRecord appRecord,
      EventingService eventService) {

    String remoteAddress =
        messageBindingInfo.getRemoteAddress() != null
            ? Optional.ofNullable(messageBindingInfo.getRemoteAddress().getHostAddress()).orElse("")
            : "";

    Map<String, String> messageInfoMap =
        Maps.newHashMap(
            ImmutableMap.of(
                SIP_MESSAGE_TYPE,
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
    // Populate To and From user portion
    SipUri fromSipUri = (SipUri) ((SIPMessage) message).getFromHeader().getAddress().getURI();
    String fromUser = fromSipUri.getUser();
    SipUri toSipUri = (SipUri) ((SIPMessage) message).getToHeader().getAddress().getURI();
    String toUser = toSipUri.getUser();
    messageInfoMap.put(Event.CALLED_NUMBER, toUser);
    messageInfoMap.put(Event.CALLING_NUMBER, fromUser);

    messageInfoMap.put(Event.INBOUND_NETWORK, inboundNetwork);
    messageInfoMap.put(Event.OUTBOUND_NETWORK, outboundNetwork);

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
  }

  public static void emitSGElementUpEvent(
      String sgName, ServerGroupElement sge, String networkName) {
    Map<String, String> eventInfoMap =
        Maps.newHashMap(
            ImmutableMap.of(
                Event.REMOTEIP,
                sge.getIpAddress(),
                Event.REMOTEPORT,
                String.valueOf(sge.getPort()),
                "network",
                networkName));
    String msg =
        "ServerGroup Element UP: "
            .concat(String.valueOf(sge))
            .concat(" belongs to ServerGroup: ")
            .concat(sgName);

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

  public static void emitSGElementDownEvent(
      String sgName,
      Integer errorCode,
      String errorReason,
      ServerGroupElement sge,
      String networkName) {
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

    String msg =
        "ServerGroup Element DOWN: "
            .concat(sge.toUniqueElementString())
            .concat(" : ")
            .concat(String.valueOf(sge))
            .concat(" belongs to ServerGroup: ")
            .concat(sgName);

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

  public static void emitRateLimiterEvent(RateLimitInfo rateLimitInfo, Message message) {
    if (eventingService != null) {
      Map<String, String> eventInfo = new HashMap<>();
      eventInfo.put(Event.REMOTEIP, rateLimitInfo.getRemoteIP());
      eventInfo.put(Event.LOCALIP, rateLimitInfo.getLocalIP());
      eventInfo.put("policyName", rateLimitInfo.getPolicyName());
      eventInfo.put("action", rateLimitInfo.getAction().name());
      eventInfo.put(
          Event.SIP_MESSAGE_TYPE,
          rateLimitInfo.isRequest() ? MESSAGE_TYPE.REQUEST.name() : MESSAGE_TYPE.RESPONSE.name());

      LoggingEvent event =
          new LoggingEvent.LoggingEventBuilder()
              .eventType(EventType.RATE_LIMITER)
              .eventInfoMap(eventInfo)
              .sipMsgPayload(message)
              .build();
      List<DhruvaEvent> events = new ArrayList<>();
      events.add(event);
      eventingService.publishEvents(events);
    }
  }
}
