package com.cisco.dhruva.normalisation.callTypeNormalization;

import com.cisco.dsb.common.sip.stack.dto.DhruvaNetwork;
import com.cisco.dsb.proxy.messaging.ProxySIPRequest;
import com.cisco.dsb.proxy.messaging.ProxySIPResponse;
import com.cisco.dsb.proxy.sip.ProxyCookieImpl;
import java.util.function.Consumer;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public abstract class Normalization implements com.cisco.dsb.common.normalization.Normalization {

  public Consumer<ProxySIPResponse> responseNorm = proxySIPResponse -> {};
  protected Consumer<ProxySIPRequest> responseNormConsumerSetter =
      (proxySIPRequest -> {
        ((ProxyCookieImpl) proxySIPRequest.getCookie()).setResponseNormConsumer(getResponseNorm());
        ((ProxyCookieImpl) proxySIPRequest.getCookie())
            .setRequestIncomingNetwork(
                DhruvaNetwork.getNetwork(proxySIPRequest.getNetwork()).get());
      });
}
