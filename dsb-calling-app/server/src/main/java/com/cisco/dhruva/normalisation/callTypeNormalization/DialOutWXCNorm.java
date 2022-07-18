package com.cisco.dhruva.normalisation.callTypeNormalization;

import static com.cisco.dhruva.normalisation.callTypeNormalization.NormalizeUtil.normalize;
import static com.cisco.dhruva.normalisation.callTypeNormalization.NormalizeUtil.normalizeResponse;

import com.cisco.dsb.common.normalization.Normalization;
import com.cisco.dsb.common.sip.stack.dto.DhruvaNetwork;
import com.cisco.dsb.common.sip.util.EndPoint;
import com.cisco.dsb.proxy.messaging.ProxySIPRequest;
import com.cisco.dsb.proxy.messaging.ProxySIPResponse;
import com.cisco.dsb.proxy.sip.ProxyCookieImpl;
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
  List<String> headersToReplaceWithOwnIPInResponse = Arrays.asList("To",
      "P-Asserted-Identity", "P-Preferred-Identity", "RPID-Privacy", "Diversion");
  List<String> headersToReplaceWithRemoteIPInResponse = Arrays.asList("From");
  List<String> headersToRemoveInResponse = Arrays.asList("Server", "User-Agent");
  private Consumer<ProxySIPRequest> preNormConsumer =
      proxySIPRequest -> {
        logger.debug("DialOutWXC preNormalization triggered.");
        normalize(proxySIPRequest.getRequest(), paramsToAdd);
      };

  private BiConsumer<TrunkCookie, EndPoint> postNormConsumer =
      (cookie, endPoint) -> {
        logger.debug("DialOutWXC postNormalization triggered for rUri host change.");
        normalize(cookie.getClonedRequest().getRequest(), endPoint);
      };

  private Consumer<ProxySIPResponse> responseNorm =
      proxySIPResponse -> {
        normalizeResponse(
            proxySIPResponse,
            headersToReplaceWithOwnIPInResponse,
            headersToReplaceWithRemoteIPInResponse,
            headersToRemoveInResponse);
      };

  private Consumer<ProxySIPRequest> responseNormConsumerSetter =
      (proxySIPRequest -> {
        ((ProxyCookieImpl) proxySIPRequest.getCookie()).setResponseNormConsumer(responseNorm);
        ((ProxyCookieImpl) proxySIPRequest.getCookie())
            .setRequestIncomingNetwork(
                DhruvaNetwork.getNetwork(proxySIPRequest.getNetwork()).get());
      });

  @Override
  public Consumer<ProxySIPRequest> preNormalize() {
    return preNormConsumer;
  }

  @Override
  public BiConsumer<TrunkCookie, EndPoint> postNormalize() {
    return postNormConsumer;
  }

  @Override
  public Consumer setNormForFutureResponse() {
    return responseNormConsumerSetter;
  }
}
