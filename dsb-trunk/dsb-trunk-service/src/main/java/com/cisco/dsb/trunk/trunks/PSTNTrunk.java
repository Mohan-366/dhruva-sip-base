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
  public PSTNTrunk(String name, Ingress ingress, Egress egress, boolean enableCircuitBreaker) {
    super(name, ingress, egress, enableCircuitBreaker);
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
    applyEgressNorm(proxySIPRequest);
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

  @Override
  protected void applyEgressNorm(ProxySIPRequest proxySIPRequest) {
    // apply pre Route normalisation specific to PSTN Trunk- None as of now
    // remove DTG params
    SipUri rUri = (SipUri) proxySIPRequest.getRequest().getRequestURI();
    rUri.removeParameter(SipParamConstants.DTG);
    // Remove dtg parameter in To header.We should not leak internal routing details.
    // Calling Dial out header format To:
    // <sip:+18776684488@10.252.103.171:5060;user=phone;dtg=DhruBwFxSIUS>
    SipUri sipURI = (SipUri) proxySIPRequest.getRequest().getToHeader().getAddress().getURI();
    sipURI.removeParameter(SipParamConstants.DTG);
  }
}
