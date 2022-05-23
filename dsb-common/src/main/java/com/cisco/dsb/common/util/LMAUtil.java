package com.cisco.dsb.common.util;

import com.cisco.dsb.common.sip.stack.dto.BindingInfo;
import com.cisco.dsb.common.sip.stack.dto.DhruvaNetwork;
import com.cisco.dsb.common.transport.Transport;
import com.cisco.dsb.common.util.log.event.Event;
import gov.nist.javax.sip.message.SIPMessage;
import java.util.Locale;
import java.util.Optional;
import javax.sip.SipProvider;
import org.apache.commons.lang3.StringUtils;
import org.jetbrains.annotations.NotNull;

public class LMAUtil {

  public static void emitSipMessageEvent(
      SipProvider sipProvider,
      SIPMessage message,
      Event.MESSAGE_TYPE messageType,
      Event.DIRECTION directionType,
      boolean isInternallyGenerated,
      boolean isMidDialogRequest,
      long dhruvaProcessDelayInMilis) {

    Transport transportType;

    if (sipProvider == null) {
      transportType = getTransportTypeFromDhruvaNetwork(message);
    } else {
      transportType = getTransportType(sipProvider);
    }

    BindingInfo messageBindingInfo = LMAUtil.populateBindingInfo(message, transportType);

    Event.emitMessageEvent(
        messageBindingInfo,
        message,
        directionType,
        messageType,
        isInternallyGenerated,
        isMidDialogRequest,
        dhruvaProcessDelayInMilis);
  }

  public static Transport getTransportTypeFromDhruvaNetwork(SIPMessage message) {
    Transport transportType;
    String network = String.valueOf(message.getApplicationData());
    transportType =
        (!StringUtils.isEmpty(network) && DhruvaNetwork.getProviderFromNetwork(network).isPresent())
            ? getTransportType(DhruvaNetwork.getProviderFromNetwork(network).get())
            : Transport.NONE;
    return transportType;
  }

  public static BindingInfo populateBindingInfo(
      @NotNull SIPMessage sipMessage, Transport transportType) {
    return new BindingInfo.BindingInfoBuilder()
        .setLocalAddress(sipMessage.getLocalAddress())
        .setLocalPort(sipMessage.getLocalPort())
        .setRemotePort(sipMessage.getRemotePort())
        .setRemoteAddress(sipMessage.getRemoteAddress())
        .setNetwork(transportType.name())
        .setRemoteAddressStr(
            Optional.ofNullable(String.valueOf(sipMessage.getRemoteAddress())).orElse(""))
        .build();
  }

  public static Transport getTransportType(SipProvider sipProvider) {
    return sipProvider != null && sipProvider.getListeningPoints() != null
        ? Transport.getTypeFromString(
                sipProvider.getListeningPoints()[0].getTransport().toUpperCase(Locale.ROOT))
            .orElse(Transport.NONE)
        : Transport.NONE;
  }

  public static Event.EventSubType getEventSubTypeFromTransport(String transport) {
    return StringUtils.equalsIgnoreCase(transport, "UDP")
        ? Event.EventSubType.UDPCONNECTION
        : StringUtils.equalsIgnoreCase(transport, "TLS")
            ? Event.EventSubType.TLSCONNECTION
            : Event.EventSubType.TCPCONNECTION;
  }
}
