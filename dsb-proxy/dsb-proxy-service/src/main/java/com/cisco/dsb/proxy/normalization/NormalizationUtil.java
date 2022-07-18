package com.cisco.dsb.proxy.normalization;

import com.cisco.dsb.common.sip.stack.dto.DhruvaNetwork;
import com.cisco.dsb.proxy.messaging.ProxySIPResponse;
import com.cisco.dsb.proxy.sip.ProxyCookieImpl;
import gov.nist.javax.sip.address.SipUri;
import gov.nist.javax.sip.message.SIPResponse;
import java.text.ParseException;
import java.util.Optional;
import java.util.function.Consumer;
import javax.sip.header.ViaHeader;
import lombok.CustomLog;
import lombok.NonNull;

@CustomLog
public class NormalizationUtil {
  public static void doResponseNormalization(ProxySIPResponse proxySIPResponse) {
    Consumer responseNormConsumer =
        ((ProxyCookieImpl) proxySIPResponse.getCookie()).getResponseNormConsumer();
    if (responseNormConsumer != null) {
      logger.info("Appying Normalization to response");
      responseNormConsumer.accept(proxySIPResponse);
    } else {
      logger.error("Cannot apply normalization. responseConsumer null.");
    }
  }

  public static void doStrayResponseDefaultNormalization(
      @NonNull SIPResponse response, @NonNull String network, @NonNull ViaHeader via) {
    Optional<DhruvaNetwork> responseOutgoingNetwork = DhruvaNetwork.getNetwork(network);
    String remoteIPAddress = response.getTopmostVia().getHost();
    try {
      String ownIPAdress = responseOutgoingNetwork.get().getListenPoint().getHostIPAddress();

      ((SipUri) response.getTo().getAddress().getURI()).setHost(ownIPAdress);
      ((SipUri) response.getFrom().getAddress().getURI()).setHost(remoteIPAddress);
    } catch (ParseException e) {
      logger.error("Cannot normalize response {}", response, e);
    }
  }
}
