package com.cisco.dhruva.normalization;

import com.cisco.dsb.common.normalization.Normalization;
import com.cisco.dsb.common.sip.util.EndPoint;
import com.cisco.dsb.proxy.messaging.ProxySIPRequest;
import com.cisco.dsb.trunk.trunks.AbstractTrunk.TrunkCookie;
import java.util.function.BiConsumer;
import java.util.function.Consumer;

public class SampleNormalization implements Normalization {
  private Consumer<ProxySIPRequest> preNormConsumer = proxySIPRequest -> {};

  private BiConsumer<TrunkCookie, EndPoint> postNormConsumer = (trunkCookie, endPoint) -> {};

  @Override
  public Consumer<ProxySIPRequest> preNormalize() {
    return preNormConsumer;
  }

  @Override
  public BiConsumer<TrunkCookie, EndPoint> postNormalize() {
    return postNormConsumer;
  }
}
