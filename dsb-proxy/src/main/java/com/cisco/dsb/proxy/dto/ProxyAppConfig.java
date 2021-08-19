package com.cisco.dsb.proxy.dto;

import com.cisco.dsb.proxy.messaging.ProxySIPRequest;
import com.cisco.dsb.proxy.messaging.ProxySIPResponse;
import java.util.Objects;
import java.util.function.Consumer;
import lombok.Builder;
import lombok.Getter;

@Builder
@Getter
public class ProxyAppConfig {
  Consumer<ProxySIPRequest> requestConsumer;
  Consumer<ProxySIPResponse> responseConsumer;
  @Builder.Default boolean midDialog = false;
  @Builder.Default boolean _1xx = false;
  @Builder.Default boolean _2xx = false;
  @Builder.Default boolean _3xx = false;
  @Builder.Default boolean _4xx = false;
  @Builder.Default boolean _5xx = false;
  @Builder.Default boolean _6xx = false;
  boolean[] responsesInterested;

  /**
   * Throws arrayIndexOutOfBoundException is responseClass is not between 1 and 6(inclusive)
   *
   * @param responseClass
   * @return
   */
  public boolean getInterest(int responseClass) throws ArrayIndexOutOfBoundsException {
    if (Objects.isNull(responsesInterested))
      responsesInterested = new boolean[] {_1xx, _2xx, _3xx, _4xx, _5xx, _6xx};
    return responsesInterested[responseClass - 1];
  }
}
