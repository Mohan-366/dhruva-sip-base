package com.cisco.dhruva.normalisation.callTypeNormalization;

import static com.cisco.dhruva.normalisation.callTypeNormalization.NormalizeUtil.normalize;

import com.cisco.dsb.common.normalization.Normalization;
import com.cisco.dsb.common.sip.util.EndPoint;
import com.cisco.dsb.proxy.messaging.ProxySIPRequest;
import com.cisco.dsb.trunk.trunks.AbstractTrunk.TrunkCookie;
import com.cisco.dsb.trunk.util.SipParamConstants;
import java.util.Arrays;
import java.util.List;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import lombok.CustomLog;
import org.springframework.stereotype.Component;

@CustomLog
@Component
public class DialInPSTNNorm implements Normalization {
  private List<String[]> paramsToAdd =
      Arrays.asList(
          new String[] {"requestUri", SipParamConstants.CALLTYPE, SipParamConstants.DIAL_IN_TAG},
          new String[] {"requestUri", SipParamConstants.X_CISCO_DPN, SipParamConstants.DPN_IN},
          new String[] {"requestUri", SipParamConstants.X_CISCO_OPN, SipParamConstants.OPN_IN});
  private Consumer<ProxySIPRequest> preNormConsumer =
      proxySIPRequest -> {
        logger.debug("DialInPSTN Pre-normalization triggered for paramsToAdd: {}", paramsToAdd);
        normalize(proxySIPRequest.getRequest(), paramsToAdd);
      };

  private BiConsumer<TrunkCookie, EndPoint> postNormConsumer =
      (cookie, endPoint) -> {
        logger.debug("DialInPSTN Post-Normalization triggered for rUri host change.");
        normalize(cookie.getClonedRequest().getRequest(), endPoint);
      };

  @Override
  public Consumer<ProxySIPRequest> preNormalize() {
    return preNormConsumer;
  }

  @Override
  public BiConsumer<TrunkCookie, EndPoint> postNormalize() {
    return postNormConsumer;
  }
}
