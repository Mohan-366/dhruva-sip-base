package com.cisco.dsb.trunk.trunks;

import com.cisco.dsb.common.exception.DhruvaRuntimeException;
import com.cisco.dsb.common.exception.ErrorCode;
import com.cisco.dsb.common.servergroup.ServerGroup;
import com.cisco.dsb.proxy.messaging.ProxySIPRequest;
import com.cisco.dsb.proxy.messaging.ProxySIPResponse;
import com.cisco.dsb.trunk.util.SipParamConstants;
import gov.nist.javax.sip.address.SipUri;
import java.text.ParseException;
import java.util.Objects;
import java.util.function.Function;
import reactor.core.publisher.Mono;

public class AntaresTrunk extends B2BTrunk {

  public AntaresTrunk() {}

  public AntaresTrunk(B2BTrunk b2BTrunk) {
    super(b2BTrunk);
  }

  @Override
  public ProxySIPRequest processIngress(ProxySIPRequest proxySIPRequest) {
    removeOpnDpn()
        .andThen(removeCallType())
        .apply(((SipUri) proxySIPRequest.getRequest().getRequestURI()));
    return proxySIPRequest;
  }

  @Override
  public Mono<ProxySIPResponse> processEgress(ProxySIPRequest proxySIPRequest) {
    // apply normalisation specific to Egress
    try {
      applyEgressNorm(proxySIPRequest);
    } catch (DhruvaRuntimeException ex) {
      return Mono.error(ex);
    }
    return sendToProxy(proxySIPRequest);
  }

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

  private Function<SipUri, SipUri> removeOpnDpn() {
    return rUri -> {
      rUri.removeParameter(SipParamConstants.X_CISCO_OPN);
      rUri.removeParameter(SipParamConstants.X_CISCO_DPN);
      return rUri;
    };
  }

  private Function<SipUri, SipUri> removeCallType() {
    return rUri -> {
      rUri.removeParameter(SipParamConstants.CALLTYPE);
      return rUri;
    };
  }

  protected void applyEgressNorm(ProxySIPRequest proxySIPRequest) {
    // apply normalisation specific to Trunk
    SipUri rUri = (SipUri) proxySIPRequest.getRequest().getRequestURI();
    try {
      if (Objects.equals(
          rUri.getParameter(SipParamConstants.CALLTYPE), SipParamConstants.DIAL_OUT_TAG)) {
        rUri.setParameter(SipParamConstants.X_CISCO_DPN, SipParamConstants.DPN_OUT);
        rUri.setParameter(SipParamConstants.X_CISCO_OPN, SipParamConstants.OPN_OUT);
      } else {
        rUri.setParameter(SipParamConstants.X_CISCO_DPN, SipParamConstants.DPN_IN);
        rUri.setParameter(SipParamConstants.X_CISCO_OPN, SipParamConstants.OPN_IN);
      }
    } catch (ParseException e) {
      throw new DhruvaRuntimeException(ErrorCode.APP_REQ_PROC, "Unable to add OPN/DPN params", e);
    }
  }
}
