package com.cisco.dhruva.normalization;

import com.cisco.dsb.common.normalization.Normalization;
import com.cisco.dsb.common.sip.util.EndPoint;
import com.cisco.dsb.proxy.messaging.ProxySIPRequest;
import com.cisco.dsb.trunk.trunks.AbstractTrunk.TrunkCookie;
import gov.nist.javax.sip.message.SIPRequest;
import java.util.function.BiConsumer;
import java.util.function.Consumer;

public class SampleNormalization implements Normalization {
  private Consumer<SIPRequest> ingressNormConsumer = sipRequest -> {};

  private Consumer<SIPRequest> preNormConsumer = sipRequest -> {};

  private Consumer<ProxySIPRequest> responseNormConsumer = proxySIPRequest -> {};

  private BiConsumer<TrunkCookie, EndPoint> postNormConsumer = (trunkCookie, endPoint) -> {};

  private Consumer<SIPRequest> egressMidCallPostNormConsumer = sipRequest -> {};

  @Override
  public Consumer<SIPRequest> ingressNormalize() {
    return ingressNormConsumer;
  }

  @Override
  public Consumer<SIPRequest> egressPreNormalize() {
    return preNormConsumer;
  }

  @Override
  public BiConsumer<TrunkCookie, EndPoint> egressPostNormalize() {
    return postNormConsumer;
  }

  @Override
  public Consumer<SIPRequest> egressMidCallPostNormalize() {
    return egressMidCallPostNormConsumer;
  }

  @Override
  public Consumer<ProxySIPRequest> setNormForFutureResponse() {
    return responseNormConsumer;
  }
}
