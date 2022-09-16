package com.cisco.dhruva.normalisation.callTypeNormalization;

import static com.cisco.dhruva.normalisation.callTypeNormalization.NormalizeUtil.normalize;
import static com.cisco.dhruva.normalisation.callTypeNormalization.NormalizeUtil.replaceIPInHeader;

import com.cisco.dhruva.application.CallingAppConfigurationProperty;
import com.cisco.dhruva.normalisation.callTypeNormalization.NormalizeUtil.HeaderToNormalize;
import com.cisco.dsb.common.sip.stack.dto.DhruvaNetwork;
import com.cisco.dsb.common.sip.util.EndPoint;
import com.cisco.dsb.common.sip.util.SipConstants;
import com.cisco.dsb.proxy.messaging.ProxySIPRequest;
import com.cisco.dsb.trunk.trunks.AbstractTrunk.TrunkCookie;
import com.cisco.dsb.trunk.util.SipParamConstants;
import gov.nist.javax.sip.address.SipUri;
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

  private Consumer<ProxySIPRequest> egressMidCallPostNormConsumer =
      proxySIPRequest -> {
        if (logger.isDebugEnabled()) {
          headersToReplaceWithRemoteIP.forEach(
              headerForIPReplacement ->
                  logger.debug(
                      "Request Post-normalization for mid-call: headersToReplaceWithRemoteIP: {}",
                      headerForIPReplacement.header));
        }
        SipUri rUri = ((SipUri) proxySIPRequest.getRequest().getRequestURI());
        replaceIPInHeader(
            proxySIPRequest.getRequest(), headersToReplaceWithRemoteIP, rUri.getHost());
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
    return getResponseNormConsumerSetter();
  }
}
