package com.cisco.dsb.proxy.normalization;

import com.cisco.dsb.proxy.messaging.ProxySIPResponse;
import com.cisco.dsb.proxy.sip.ProxyCookieImpl;
import java.util.function.Consumer;
import lombok.CustomLog;

@CustomLog
public class NormalizationUtil {
  public static void doResponseNormalization(ProxySIPResponse proxySIPResponse) {
    Consumer responseNormConsumer =
        ((ProxyCookieImpl) proxySIPResponse.getCookie()).getResponseNormConsumer();
    if (responseNormConsumer != null) {
      logger.info("Applying Normalization to response");
      responseNormConsumer.accept(proxySIPResponse);
    } else {
      logger.error("Cannot apply normalization. responseConsumer null.");
    }
  }
}
