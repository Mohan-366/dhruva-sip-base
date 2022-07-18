package com.cisco.dhruva.normalisation.callTypeNormalization;

import static com.cisco.dhruva.normalisation.callTypeNormalization.NormalizeUtil.normalize;

import com.cisco.dsb.common.normalization.Normalization;
import com.cisco.dsb.common.sip.stack.dto.DhruvaNetwork;
import com.cisco.dsb.common.sip.util.EndPoint;
import com.cisco.dsb.proxy.messaging.ProxySIPRequest;
import com.cisco.dsb.proxy.messaging.ProxySIPResponse;
import com.cisco.dsb.proxy.sip.ProxyCookieImpl;
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
public class DialInB2BNorm implements Normalization {

  List<String> headersToReplaceWithOwnIP =
      Arrays.asList(
          "P-Asserted-Identity", "P-Preferred-Identity", "RPID-Privacy", "Diversion", "From");
  List<String> headersToReplaceWithRemoteIP = Arrays.asList("To");
  List<String> headersToRemove = Arrays.asList("Server", "User-Agent");
  List<String[]> paramsToRemove =
      Arrays.asList(
          new String[] {"requestUri", SipParamConstants.X_CISCO_OPN},
          new String[] {"requestUri", SipParamConstants.X_CISCO_DPN},
          new String[] {"requestUri", SipParamConstants.CALLTYPE});
  private static final String OUTGOING_NETWORK_NAME = "net_cc";
  private Consumer<ProxySIPRequest> preNormConsumer =
      proxySIPRequest -> {
        DhruvaNetwork outgoingNetwork = DhruvaNetwork.getNetwork(OUTGOING_NETWORK_NAME).get();
        logger.debug(
            "DialInB2BN Pre-normalization triggered for "
                + "\nheadersToReplaceWithOwnIP: {}"
                + "\nheadersToRemove: {}"
                + "\n paramsToRemove: {}",
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
            "DialInB2BN Post-normalization triggered for rUri host change,"
                + " headersToReplaceWithRemoteIP: {}",
            headersToReplaceWithRemoteIP);
        SIPRequest request = cookie.getClonedRequest().getRequest();
        normalize(request, endPoint, headersToReplaceWithRemoteIP);
      };

  private Consumer<ProxySIPResponse> responseNorm =
      proxySIPResponse -> {
        // no norm to apply as of now
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
