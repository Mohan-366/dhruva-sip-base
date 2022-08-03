package com.cisco.dhruva.util;

import com.cisco.dsb.common.normalization.Normalization;
import com.cisco.dsb.common.sip.util.EndPoint;
import com.cisco.dsb.proxy.messaging.ProxySIPRequest;
import com.cisco.dsb.trunk.trunks.AbstractTrunk.TrunkCookie;
import java.util.function.BiConsumer;
import java.util.function.Consumer;

public class NormalizationHelper implements Normalization {

  private Consumer<ProxySIPRequest> ingressNormConsumer = proxySIPRequest -> {};

  private Consumer<ProxySIPRequest> preNormConsumer = proxySIPRequest -> {};

  private Consumer<ProxySIPRequest> responseNormConsumer = proxySIPRequest -> {};

  private BiConsumer<TrunkCookie, EndPoint> postNormConsumer = (trunkCookie, endPoint) -> {};

  @Override
  public Consumer ingressNormalize() {
    return ingressNormConsumer;
  }

  @Override
  public Consumer<ProxySIPRequest> egressPreNormalize() {
    return preNormConsumer;
  }

  @Override
  public BiConsumer<TrunkCookie, EndPoint> egressPostNormalize() {
    return postNormConsumer;
  }

  @Override
  public Consumer setNormForFutureResponse() {
    return responseNormConsumer;
  }
}
