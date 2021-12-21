package com.cisco.dsb.trunk.trunks;

import com.cisco.dsb.common.exception.DhruvaRuntimeException;
import com.cisco.dsb.common.exception.ErrorCode;
import com.cisco.dsb.common.servergroup.ServerGroup;
import com.cisco.dsb.proxy.messaging.ProxySIPRequest;
import com.cisco.dsb.proxy.messaging.ProxySIPResponse;
import com.cisco.dsb.trunk.util.SipParamConstants;
import gov.nist.javax.sip.address.SipUri;
import java.text.ParseException;
import lombok.Builder;
import lombok.NoArgsConstructor;
import reactor.core.publisher.Mono;

@NoArgsConstructor
public class PSTNTrunk extends AbstractTrunk {
  @Builder(setterPrefix = "set")
  public PSTNTrunk(String name, Ingress ingress, Egress egress) {
    super(name, ingress, egress, null, null);
  }

  @Override
  public ProxySIPRequest processIngress(ProxySIPRequest proxySIPRequest) {
    SipUri rUri = ((SipUri) proxySIPRequest.getRequest().getRequestURI());
    try {
      rUri.setParameter(SipParamConstants.CALLTYPE, SipParamConstants.DIAL_IN_TAG);
    } catch (ParseException e) {
      throw new DhruvaRuntimeException(
          ErrorCode.APP_REQ_PROC, "Unable to add CallType:DialIn param to rUri", e);
    }
    return proxySIPRequest;
  }

  @Override
  public Mono<ProxySIPResponse> processEgress(ProxySIPRequest proxySIPRequest) {
    // apply pre Route normalisation specific to PSTN Trunk- None as of now
    // remove DTG params
    SipUri rUri = (SipUri) proxySIPRequest.getRequest().getRequestURI();
    rUri.removeParameter(SipParamConstants.DTG);

    return sendToProxy(proxySIPRequest);
  }

  @Override
  protected void doPostRouteNorm(TrunkCookie cookie) {
    SipUri rUri = ((SipUri) cookie.getClonedRequest().getRequest().getRequestURI());
    try {
      rUri.setHost(((ServerGroup) cookie.getSgLoadBalancer().getCurrentElement()).getHostName());
    } catch (ParseException e) {
      throw new DhruvaRuntimeException(
          ErrorCode.APP_REQ_PROC,
          "Unable to change Host portion of rUri",
          e); // should this be Trunk.RETRY??
    }
  }

  @Override
  protected boolean enableRedirection() {
    return false;
  }
}
