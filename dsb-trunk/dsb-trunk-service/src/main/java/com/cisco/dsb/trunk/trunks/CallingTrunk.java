package com.cisco.dsb.trunk.trunks;

import com.cisco.dsb.common.exception.DhruvaRuntimeException;
import com.cisco.dsb.common.exception.ErrorCode;
import com.cisco.dsb.common.servergroup.ServerGroup;
import com.cisco.dsb.proxy.messaging.ProxySIPRequest;
import com.cisco.dsb.proxy.messaging.ProxySIPResponse;
import com.cisco.dsb.trunk.util.SipParamConstants;
import gov.nist.javax.sip.address.SipUri;
import java.text.ParseException;
import reactor.core.publisher.Mono;

public class CallingTrunk extends AbstractTrunk {

  @Override
  public ProxySIPRequest processIngress(ProxySIPRequest proxySIPRequest) {
    SipUri rUri = ((SipUri) proxySIPRequest.getRequest().getRequestURI());
    try {
      // Move this to APP as it's used by APP to id the call type
      rUri.setParameter(SipParamConstants.CALLTYPE, SipParamConstants.DIAL_OUT_TAG);
    } catch (ParseException e) {
      throw new DhruvaRuntimeException(
          ErrorCode.APP_REQ_PROC, "Unable to add CallType:DialIn param to rUri", e);
    }
    return proxySIPRequest;
  }

  @Override
  public Mono<ProxySIPResponse> processEgress(ProxySIPRequest proxySIPRequest) {
    return sendToProxy(proxySIPRequest);
  }

  @Override
  protected void doPostRouteNorm(TrunkCookie cookie) {
    SipUri rUri = ((SipUri) cookie.getClonedRequest().getRequest().getRequestURI());
    String host;
    if (cookie.getRedirectionSG() != null) {
      host = cookie.getRedirectionSG().getHostName();
    } else {
      host = ((ServerGroup) cookie.getSgLoadBalancer().getCurrentElement()).getHostName();
    }
    try {
      rUri.setHost(host);
    } catch (ParseException e) {
      throw new DhruvaRuntimeException(
          ErrorCode.APP_REQ_PROC,
          "Unable to change Host portion of rUri",
          e); // should this be Trunk.RETRY??
    }
  }

  @Override
  protected boolean enableRedirection() {
    return true;
  }

  @Override
  protected void applyEgressNorm(ProxySIPRequest proxySIPRequest) {
    // No policies to be applied
  }
}
