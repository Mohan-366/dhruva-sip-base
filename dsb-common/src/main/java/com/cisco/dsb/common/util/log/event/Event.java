/*
 * Copyright (c) 2020  by Cisco Systems, Inc.All Rights Reserved.
 */

package com.cisco.dsb.common.util.log.event;

import static com.cisco.dsb.common.util.log.event.Event.DIRECTION.OUT;

import com.cisco.dsb.common.servergroup.ServerGroupElement;
import com.cisco.dsb.common.sip.stack.dto.BindingInfo;
import com.cisco.dsb.common.util.log.LogUtils;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Maps;
import gov.nist.javax.sip.header.CSeq;
import gov.nist.javax.sip.message.SIPMessage;
import java.util.Map;
import java.util.Optional;
import javax.sip.message.Message;
import lombok.CustomLog;

@CustomLog
public class Event {

  private static final String ISMIDDIALOG = "isMidDialog";
  private static final String DHRUVA_PROCESSING_DELAY_IN_MILLIS = "dhruvaProcessingDelayInMillis";
  private static final String ISINTERNALLYGENERATED = "isInternallyGenerated";
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
    TLSCONNECTION(EventType.CONNECTION);
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
      String sipMethod,
      String requestUri,
      boolean isMidDialog,
      long dhruvaProcessingDelayInMillis) {
    Map<String, String> messageInfoMap =
        Maps.newHashMap(
            ImmutableMap.of(
                "sipMessageType",
                sipMessageType.name(),
                "sipMethod",
                sipMethod,
                "cseqMethod",
                String.valueOf(message.getHeader(CSeq.NAME)),
                "requestUri",
                requestUri,
                Event.REMOTEIP,
                messageBindingInfo.getRemoteAddressStr()));

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
    if (direction == OUT) {
      messageInfoMap.put(
          Event.DHRUVA_PROCESSING_DELAY_IN_MILLIS, String.valueOf(dhruvaProcessingDelayInMillis));
    }

    logger.emitEvent(
        EventType.SIPMESSAGE,
        null,
        LogUtils.obfuscateObject((SIPMessage) message, false),
        messageInfoMap);
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
    logger.emitEvent(EventType.SERVERGROUP_ELEMENT_EVENT, null, msg, eventInfoMap);
  }

  public static void emitSGEvent(String serverGroupName, boolean isDown) {
    String msg;
    Map<String, String> eventInfoMap =
        Maps.newHashMap(ImmutableMap.of("serverGroupName", serverGroupName));
    if (isDown) {
      eventInfoMap.put("errorType", ErrorType.ServerGroupDown.name());
      eventInfoMap.put("status", "up");
      msg = "ServerGroup DOWN: " + serverGroupName;
    } else {
      eventInfoMap.put("status", "down");
      msg = "ServerGroup UP: " + serverGroupName;
    }

    logger.emitEvent(EventType.SERVERGROUP_EVENT, null, msg, eventInfoMap);
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

    String msg = "ServerGroup Element DOWN: " + sge;
    logger.emitEvent(EventType.SERVERGROUP_ELEMENT_EVENT, null, msg, eventInfoMap);
  }
}
