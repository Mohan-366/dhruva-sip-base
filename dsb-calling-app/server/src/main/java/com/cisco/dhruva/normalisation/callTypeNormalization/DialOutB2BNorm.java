package com.cisco.dhruva.normalisation.callTypeNormalization;

import static com.cisco.dhruva.normalisation.callTypeNormalization.NormalizeUtil.normalize;

import com.cisco.dsb.common.normalization.Normalization;
import com.cisco.dsb.common.sip.stack.dto.DhruvaNetwork;
import com.cisco.dsb.common.sip.util.EndPoint;
import com.cisco.dsb.proxy.messaging.ProxySIPRequest;
import com.cisco.dsb.trunk.trunks.AbstractTrunk.TrunkCookie;
import com.cisco.dsb.trunk.util.SipParamConstants;
import gov.nist.javax.sip.message.SIPRequest;
import java.util.Arrays;
import java.util.List;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import lombok.CustomLog;
import org.springframework.stereotype.Component;

@CustomLog
@Component
public class DialOutB2BNorm implements Normalization {

  List<String[]> paramsToRemove =
      Arrays.asList(
          new String[] {"requestUri", SipParamConstants.DTG},
          new String[] {"To", SipParamConstants.DTG},
          new String[] {"requestUri", SipParamConstants.X_CISCO_OPN},
          new String[] {"requestUri", SipParamConstants.X_CISCO_DPN},
          new String[] {"requestUri", SipParamConstants.CALLTYPE});
  List<String> headersToReplaceWithOwnIP =
      Arrays.asList(
          "P-Asserted-Identity", "P-Preferred-Identity", "RPID-Privacy", "Diversion", "From");
  List<String> headersToReplaceWithRemoteIP = Arrays.asList("To");
  List<String> headersToRemove = Arrays.asList("X-BroadWorks-Correlation-Info", "X-BroadWorks-DNC");
  private static final String OUTGOING_NETWORK_NAME = "net_sp";

  private Consumer<ProxySIPRequest> preNormConsumer =
      proxySIPRequest -> {
        // apply pre Route normalisation specific to PSTN Trunk- None as of now
        // remove DTG params.
        // Remove dtg parameter in To header.We should not leak internal routing details.
        // Calling Dial out header format To:
        // <sip:+18776684488@10.252.103.171:5060;user=phone;dtg=DhruBwFxSIUS>
        DhruvaNetwork outgoingNetwork = DhruvaNetwork.getNetwork(OUTGOING_NETWORK_NAME).get();
        logger.debug(
            "DialOutB2BN Pre-normalization "
                + "\nheadersToReplaceWithOwnIP: {}"
                + "\nheadersToRemove: {}"
                + "\nparamsToRemove: {}",
            headersToReplaceWithOwnIP,
            headersToRemove,
            paramsToRemove);
        normalize(
            proxySIPRequest.getRequest(),
            outgoingNetwork,
            headersToReplaceWithOwnIP,
            headersToRemove,
            paramsToRemove,
            null);
      };

  private BiConsumer<TrunkCookie, EndPoint> postNormConsumer =
      (cookie, endPoint) -> {
        logger.debug(
            "DialOutB2BN Post-normalization triggered for rUri host change,"
                + " headersToReplaceWithRemoteIP: {}",
            headersToReplaceWithRemoteIP);
        SIPRequest request = cookie.getClonedRequest().getRequest();
        normalize(request, endPoint, headersToReplaceWithRemoteIP);
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
