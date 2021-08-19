/*
 * Copyright (c) 2020  by Cisco Systems, Inc.All Rights Reserved.
 */

package com.cisco.dsb.common.util.log.event;

import static com.cisco.dsb.common.util.log.event.Event.DIRECTION.OUT;

import com.cisco.dsb.common.sip.stack.dto.BindingInfo;
import com.cisco.dsb.common.util.ObfuscateUtil;
import com.cisco.dsb.common.util.log.DhruvaLoggerFactory;
import com.cisco.dsb.common.util.log.Logger;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Maps;
import gov.nist.javax.sip.header.CSeq;
import java.util.Map;
import javax.sip.message.Message;

public class Event {

  private static final String ISMIDDIALOG = "isMidDialog";
  private static final String DHRUVA_PROCESSING_DELAY_IN_MILLIS = "dhruvaProcessingDelayInMillis";
  private static final String ISINTERNALLYGENERATED = "isInternallyGenerated";
  public static String DIRECTION = "direction";
  public static String REMOTEIP = "remoteIp";
  public static String REMOTEPORT = "remotePort";
  public static String LOCALIP = "localIp";
  public static String LOCALPORT = "localPort";

  private static Logger logger = DhruvaLoggerFactory.getLogger(Event.class);

  public enum EventType {
    CONNECTION,
    SIPMESSAGE
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
    ConnectionInActive
  }

  public static void emitMessageEvent(
      BindingInfo messageBindingInfo,
      Message message,
      DIRECTION direction,
      MESSAGE_TYPE sipMessageType,
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
                message.getHeader(CSeq.NAME).toString(),
                "requestUri",
                requestUri,
                Event.REMOTEIP,
                messageBindingInfo.getRemoteAddressStr()));
    messageInfoMap.put(Event.REMOTEPORT, String.valueOf(messageBindingInfo.getRemotePort()));
    messageInfoMap.put(Event.DIRECTION, direction.name());
    messageInfoMap.put(Event.LOCALIP, messageBindingInfo.getLocalAddress().getHostAddress());
    messageInfoMap.put(Event.LOCALPORT, String.valueOf(messageBindingInfo.getLocalPort()));
    messageInfoMap.put(Event.ISMIDDIALOG, String.valueOf(isMidDialog));
    // DSB TODO
    messageInfoMap.put(Event.ISINTERNALLYGENERATED, String.valueOf(true));
    if (direction == OUT) {
      messageInfoMap.put(
          Event.DHRUVA_PROCESSING_DELAY_IN_MILLIS, String.valueOf(dhruvaProcessingDelayInMillis));
    }

    logger.emitEvent(
        EventType.SIPMESSAGE, null, ObfuscateUtil.obfuscateMsg(message.toString()), messageInfoMap);
    // DSB TODO
    //    SipMessageWrapper sipMsg = new SipMessageWrapper(message, direction);
    //    Consumer<SipMessageWrapper> consumer1 = DiagnosticsUtil::sendCDSEvents;
    //    consumer1.accept(sipMsg);
  }
}
