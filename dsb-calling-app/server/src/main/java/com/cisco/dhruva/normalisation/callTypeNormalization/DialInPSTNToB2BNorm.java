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
public class DialInPSTNToB2BNorm extends Normalization {
  private List<String[]> paramsToAdd =
      Arrays.asList(
          new String[] {
            SipConstants.REQUEST_URI, SipParamConstants.CALLTYPE, SipParamConstants.DIAL_IN_TAG
          },
          new String[] {
            SipConstants.REQUEST_URI, SipParamConstants.X_CISCO_DPN, SipParamConstants.DPN_IN
          },
          new String[] {
            SipConstants.REQUEST_URI, SipParamConstants.X_CISCO_OPN, SipParamConstants.OPN_IN
          });
  List<HeaderToNormalize> headersToReplaceWithOwnIPInResponse =
      Arrays.asList(
          new HeaderToNormalize(SipConstants.P_ASSERTED_IDENTITY, false),
          new HeaderToNormalize(SipConstants.P_PREFERRED_IDENTITY, false),
          new HeaderToNormalize(SipConstants.RPID_PRIVACY, false),
          new HeaderToNormalize(SipConstants.DIVERSION, true),
          new HeaderToNormalize(SipConstants.TO, false),
          new HeaderToNormalize(SipConstants.REMOTE_PARTY_ID, false));
  List<String> headersToRemoveInResponse =
      Arrays.asList(SipConstants.X_BROAD_WORKS_CORRELATION_INFO, SipConstants.X_BROAD_WORKS_DNC);
  List<HeaderToNormalize> headersToReplaceWithRemoteIPInResponse =
      Collections.singletonList(new HeaderToNormalize(SipConstants.FROM, false));

  private Consumer<ProxySIPRequest> ingressNormConsumer = ProxySIPRequest -> {};

  private Consumer<ProxySIPRequest> egressPreNormConsumer =
      proxySIPRequest -> {
        if (logger.isDebugEnabled()) {
          paramsToAdd.forEach(
              paramsToAdd ->
                  logger.debug(
                      "DialInPSTNToB2B Request Pre-normalization: paramsToAdd {}:{}",
                      paramsToAdd[0],
                      paramsToAdd[1]));
        }
        normalize(proxySIPRequest.getRequest(), paramsToAdd, null);
      };

  private BiConsumer<TrunkCookie, EndPoint> egressPostNormConsumer =
      (cookie, endPoint) -> {
        logger.debug("DialInPSTNToB2B Request Post-Normalization triggered for rUri host change.");
        normalize(cookie.getClonedRequest().getRequest(), endPoint);
      };

  private Consumer<ProxySIPRequest> egressMidCallPostNormConsumer = proxySIPRequest -> {};

  public Consumer<ProxySIPResponse> responseNorm =
      proxySIPResponse -> {
        if (logger.isDebugEnabled()) {
          headersToReplaceWithRemoteIPInResponse.forEach(
              headerForIPReplacement ->
                  logger.debug(
                      "DialInPSTNToB2B Response Normalization: headersToReplaceWithRemoteIP: {}",
                      headerForIPReplacement.header));
          headersToReplaceWithOwnIPInResponse.forEach(
              headerForIPReplacement ->
                  logger.debug(
                      "DialInPSTNToB2B Response Normalization: headersToReplaceWithOwnIP: {}",
                      headerForIPReplacement.header));
          logger.debug(
              "DialInPSTNToB2B Response Normalization:  headersToRemove: {}",
              headersToRemoveInResponse);
        }
        normalizeResponse(
            proxySIPResponse,
            headersToReplaceWithOwnIPInResponse,
            headersToReplaceWithRemoteIPInResponse,
            headersToRemoveInResponse);
      };

  @Override
  public Consumer<ProxySIPRequest> ingressNormalize() {
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
  public Consumer<ProxySIPRequest> egressMidCallPostNormalize() {
    return egressMidCallPostNormConsumer;
  }

  @Override
  public Consumer<ProxySIPRequest> setNormForFutureResponse() {
    setResponseNorm(responseNorm);
    return getResponseNormConsumerSetter();
  }
}
