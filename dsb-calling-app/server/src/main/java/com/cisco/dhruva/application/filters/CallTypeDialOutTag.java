package com.cisco.dhruva.application.filters;

import com.cisco.dsb.proxy.messaging.ProxySIPRequest;
import com.cisco.dsb.trunk.util.SipParamConstants;
import gov.nist.javax.sip.address.SipUri;
import java.util.function.Predicate;

public class CallTypeDialOutTag extends FilterNode {
  CallTypeDialOutTag() {
    super(new FilterId(FilterId.Id.CALLTYPE_DIAL_OUT));
  }

  @Override
  public Predicate<ProxySIPRequest> filter() {
    return proxySIPRequest -> {
      String parameter =
          ((SipUri) proxySIPRequest.getRequest().getRequestURI())
              .getParameter(SipParamConstants.CALLTYPE);
      if (parameter == null) return false;
      return parameter.equals(SipParamConstants.DIAL_OUT_TAG);
    };
  }
}
