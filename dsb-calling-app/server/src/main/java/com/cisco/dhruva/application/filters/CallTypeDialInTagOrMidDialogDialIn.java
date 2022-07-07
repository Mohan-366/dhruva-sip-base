package com.cisco.dhruva.application.filters;

import com.cisco.dsb.proxy.messaging.ProxySIPRequest;
import com.cisco.dsb.trunk.util.SipParamConstants;
import gov.nist.javax.sip.address.SipUri;
import java.util.function.Predicate;

public class CallTypeDialInTagOrMidDialogDialIn extends FilterNode {
  // TODO: KALPA pick network values from appProperties
  private static final String OUTGOING_NETWORK = "net_cc";

  CallTypeDialInTagOrMidDialogDialIn() {
    super(new FilterId(FilterId.Id.CALLTYPE_DIAL_IN_OR_MID_DIALOG_DIAL_IN));
  }

  @Override
  public Predicate<ProxySIPRequest> filter() {
    return proxySIPRequest -> {
      if (isMidDialogDialIn(proxySIPRequest)) {
        return true;
      }
      String parameter =
          ((SipUri) proxySIPRequest.getRequest().getRequestURI())
              .getParameter(SipParamConstants.CALLTYPE);
      if (parameter == null) return false;
      return parameter.equals(SipParamConstants.DIAL_IN_TAG);
    };
  }

  private boolean isMidDialogDialIn(ProxySIPRequest proxySIPRequest) {
    if (proxySIPRequest.isMidCall()) {
      return (OUTGOING_NETWORK).equals(proxySIPRequest.getOutgoingNetwork());
    }
    return false;
  }
}
