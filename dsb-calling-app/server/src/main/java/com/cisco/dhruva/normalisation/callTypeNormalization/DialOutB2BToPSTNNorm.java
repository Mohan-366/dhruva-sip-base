package com.cisco.dhruva.normalisation.callTypeNormalization;

import static com.cisco.dhruva.normalisation.callTypeNormalization.NormalizeUtil.normalize;

import com.cisco.dhruva.application.CallingAppConfigurationProperty;
import com.cisco.dhruva.normalisation.callTypeNormalization.NormalizeUtil.HeaderToNormalize;
import com.cisco.dsb.common.sip.stack.dto.DhruvaNetwork;
import com.cisco.dsb.common.sip.util.EndPoint;
import com.cisco.dsb.common.sip.util.SipConstants;
import com.cisco.dsb.proxy.messaging.ProxySIPRequest;
import com.cisco.dsb.trunk.trunks.AbstractTrunk.TrunkCookie;
import com.cisco.dsb.trunk.util.SipParamConstants;
import gov.nist.javax.sip.message.SIPRequest;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import lombok.CustomLog;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

@CustomLog
@Component
public class DialOutB2BToPSTNNorm extends Normalization {

  private String outgoingNetworkString;

  @Autowired
  public DialOutB2BToPSTNNorm(CallingAppConfigurationProperty callingAppConfigurationProperty) {
    this.outgoingNetworkString = callingAppConfigurationProperty.getNetworkPSTN();
  }

  List<String[]> paramsToRemoveIngress =
      Arrays.asList(
          new String[] {SipConstants.REQUEST_URI, SipParamConstants.X_CISCO_OPN},
          new String[] {SipConstants.REQUEST_URI, SipParamConstants.X_CISCO_DPN},
          new String[] {SipConstants.REQUEST_URI, SipParamConstants.CALLTYPE});
  List<String[]> paramsToRemovePreEgress =
      Arrays.asList(
          new String[] {SipConstants.REQUEST_URI, SipParamConstants.DTG},
          new String[] {SipConstants.TO, SipParamConstants.DTG});
  List<HeaderToNormalize> headersToReplaceWithOwnIP =
      Arrays.asList(
          new HeaderToNormalize(SipConstants.P_ASSERTED_IDENTITY, false),
          new HeaderToNormalize(SipConstants.P_PREFERRED_IDENTITY, false),
          new HeaderToNormalize(SipConstants.RPID_PRIVACY, false),
          new HeaderToNormalize(SipConstants.DIVERSION, true),
          new HeaderToNormalize(SipConstants.FROM, false),
          new HeaderToNormalize(SipConstants.REMOTE_PARTY_ID, false));
  List<HeaderToNormalize> headersToReplaceWithRemoteIP =
      Collections.singletonList(new HeaderToNormalize(SipConstants.TO, false));
  List<String> headersToRemove =
      Arrays.asList(SipConstants.X_BROAD_WORKS_CORRELATION_INFO, SipConstants.X_BROAD_WORKS_DNC);

  private Consumer<ProxySIPRequest> ingressNormConsumer =
      proxySIPRequest -> {
        if (logger.isDebugEnabled()) {
          paramsToRemoveIngress.forEach(
              paramsToRemove ->
                  logger.debug(
                      "DialOutB2BToPSTN ingress-normalization: paramsToRemove {}:{}",
                      paramsToRemove[0],
                      paramsToRemove[1]));
        }
        normalize(proxySIPRequest.getRequest(), null, paramsToRemoveIngress);
      };

  private Consumer<ProxySIPRequest> egressPreNormConsumer =
      proxySIPRequest -> {
        // apply pre Route normalisation specific to PSTN Trunk- None as of now
        // remove DTG params.
        // Remove dtg parameter in To header.We should not leak internal routing details.
        // Calling Dial out header format To:
        // <sip:+18776684488@10.252.103.171:5060;user=phone;dtg=DhruBwFxSIUS>
        DhruvaNetwork outgoingNetwork = DhruvaNetwork.getNetwork(outgoingNetworkString).get();
        logger.debug(
            "DialOutB2BToPSTN egress-pre-normalization headersToRemove: {}", headersToRemove);
        if (logger.isDebugEnabled()) {
          headersToReplaceWithOwnIP.forEach(
              headerForIPReplacement ->
                  logger.debug(
                      "DialOutB2BToPSTN egress-pre-normalization: headersToReplaceWithOwnIP: {}",
                      headerForIPReplacement.header));
        }
        normalize(
            proxySIPRequest.getRequest(),
            outgoingNetwork,
            headersToReplaceWithOwnIP,
            headersToRemove);
        if (logger.isDebugEnabled()) {
          paramsToRemovePreEgress.forEach(
              paramsToRemove ->
                  logger.debug(
                      "DialOutB2BToPSTN egress-pre-normalization: paramsToRemove {}:{}",
                      paramsToRemove[0],
                      paramsToRemove[1]));
        }
        normalize(proxySIPRequest.getRequest(), null, paramsToRemovePreEgress);
      };

  private BiConsumer<TrunkCookie, EndPoint> egressPostNormConsumer =
      (cookie, endPoint) -> {
        if (logger.isDebugEnabled()) {
          logger.debug("DialOutB2BToPSTN egress-post-normalization triggered for rUri host change");
          headersToReplaceWithRemoteIP.forEach(
              headerForIPReplacement ->
                  logger.debug(
                      "DialOutB2BToPSTN Post-normalization: headersToReplaceWithRemoteIP: {}",
                      headerForIPReplacement.header));
        }
        SIPRequest request = cookie.getClonedRequest().getRequest();
        normalize(request, endPoint, headersToReplaceWithRemoteIP);
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
    return getResponseNormConsumerSetter();
  }
}
