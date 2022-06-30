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
public class DialOutWXCNorm implements Normalization {
  private List<String[]> paramsToAdd =
      Arrays.asList(
          new String[] {"requestUri", SipParamConstants.CALLTYPE, SipParamConstants.DIAL_OUT_TAG},
          new String[] {"requestUri", SipParamConstants.X_CISCO_DPN, SipParamConstants.DPN_OUT},
          new String[] {"requestUri", SipParamConstants.X_CISCO_OPN, SipParamConstants.OPN_OUT});
  private Consumer<ProxySIPRequest> preNormConsumer =
      proxySIPRequest -> {
        logger.debug("DialOutWXC preNormalization triggered.");
        normalize(proxySIPRequest.getRequest(), null, paramsToAdd);
      };

  private BiConsumer<TrunkCookie, EndPoint> postNormConsumer =
      (cookie, endPoint) -> {
        logger.debug("DialOutWXC postNormalization triggered for rUri host change.");
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
