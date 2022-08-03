package com.cisco.dhruva.normalisation.callTypeNormalization;

import static com.cisco.dhruva.normalisation.callTypeNormalization.NormalizeUtil.normalize;
import static com.cisco.dhruva.normalisation.callTypeNormalization.NormalizeUtil.normalizeResponse;

import com.cisco.dhruva.normalisation.callTypeNormalization.NormalizeUtil.HeaderToNormalize;
import com.cisco.dsb.common.sip.util.EndPoint;
import com.cisco.dsb.common.sip.util.SipConstants;
import com.cisco.dsb.proxy.messaging.ProxySIPRequest;
import com.cisco.dsb.proxy.messaging.ProxySIPResponse;
import com.cisco.dsb.trunk.trunks.AbstractTrunk.TrunkCookie;
import com.cisco.dsb.trunk.util.SipParamConstants;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import lombok.CustomLog;
import org.springframework.stereotype.Component;

@CustomLog
@Component
public class DialOutWXCToB2BNorm extends Normalization {
  private List<String[]> paramsToAdd =
      Arrays.asList(
          new String[] {
            SipConstants.REQUEST_URI, SipParamConstants.CALLTYPE, SipParamConstants.DIAL_OUT_TAG
          },
          new String[] {
            SipConstants.REQUEST_URI, SipParamConstants.X_CISCO_DPN, SipParamConstants.DPN_OUT
          },
          new String[] {
            SipConstants.REQUEST_URI, SipParamConstants.X_CISCO_OPN, SipParamConstants.OPN_OUT
          });
  List<HeaderToNormalize> headersToReplaceWithOwnIPInResponse =
      Arrays.asList(
          new HeaderToNormalize(SipConstants.P_ASSERTED_IDENTITY, false),
          new HeaderToNormalize(SipConstants.P_PREFERRED_IDENTITY, false),
          new HeaderToNormalize(SipConstants.RPID_PRIVACY, false),
          new HeaderToNormalize(SipConstants.DIVERSION, true),
          new HeaderToNormalize(SipConstants.TO, false),
          new HeaderToNormalize(SipConstants.REMOTE_PARTY_ID, false));
  List<HeaderToNormalize> headersToReplaceWithRemoteIPInResponse =
      Collections.singletonList(new HeaderToNormalize(SipConstants.FROM, false));
  List<String> headersToRemoveInResponse =
      Arrays.asList(SipConstants.SERVER, SipConstants.USER_AGENT);

  private Consumer<ProxySIPRequest> ingressNormConsumer = proxySIPRequest -> {};
  private Consumer<ProxySIPRequest> egressPreNormConsumer =
      proxySIPRequest -> {
        if (logger.isDebugEnabled()) {
          paramsToAdd.forEach(
              paramsToAdd ->
                  logger.debug(
                      "DialOutWxcToB2B Pre-normalization: paramsToAdd {}:{}",
                      paramsToAdd[0],
                      paramsToAdd[1]));
        }
        normalize(proxySIPRequest.getRequest(), paramsToAdd, null);
      };

  private BiConsumer<TrunkCookie, EndPoint> egressPostNormConsumer =
      (cookie, endPoint) -> {
        logger.debug("DialOutWxcToB2B postNormalization triggered for rUri host change.");
        normalize(cookie.getClonedRequest().getRequest(), endPoint);
      };

  private Consumer<ProxySIPResponse> responseNorm =
      proxySIPResponse -> {
        if (logger.isDebugEnabled()) {
          headersToReplaceWithRemoteIPInResponse.forEach(
              headerForIPReplacement ->
                  logger.debug(
                      "DialOutWxcToB2B Response Normalization: headersToReplaceWithRemoteIP: {}",
                      headerForIPReplacement.header));
          headersToReplaceWithOwnIPInResponse.forEach(
              headerForIPReplacement ->
                  logger.debug(
                      "DialOutWxcToB2B Response Normalization: headersToReplaceWithOwnIP: {}",
                      headerForIPReplacement.header));
          logger.debug(
              "DialOutWxcToB2B Response Normalization:  headersToRemove: {}",
              headersToRemoveInResponse);
        }
        normalizeResponse(
            proxySIPResponse,
            headersToReplaceWithOwnIPInResponse,
            headersToReplaceWithRemoteIPInResponse,
            headersToRemoveInResponse);
      };

  @Override
  public Consumer ingressNormalize() {
    return ingressNormConsumer;
  }

  @Override
  public Consumer<ProxySIPRequest> egressPreNormalize() {
    return egressPreNormConsumer;
  }

  @Override
  public BiConsumer<TrunkCookie, EndPoint> egressPostNormalize() {
    return egressPostNormConsumer;
  }

  @Override
  public Consumer setNormForFutureResponse() {
    setResponseNorm(responseNorm);
    return getResponseNormConsumerSetter();
  }
}
