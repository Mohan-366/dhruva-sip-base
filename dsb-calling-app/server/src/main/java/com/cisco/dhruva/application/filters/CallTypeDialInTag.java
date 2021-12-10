package com.cisco.dhruva.application.filters;

import com.cisco.dsb.proxy.messaging.ProxySIPRequest;
import com.cisco.dsb.trunk.util.SipParamConstants;
import gov.nist.javax.sip.address.SipUri;
import java.util.function.Predicate;

public class CallTypeDialInTag extends FilterNode {
  CallTypeDialInTag() {
    super(new FilterId(FilterId.Id.CALLTYPE_DIAL_IN));
  }

  @Override
  public Predicate<ProxySIPRequest> filter() {
    return proxySIPRequest -> {
      String parameter =
          ((SipUri) proxySIPRequest.getRequest().getRequestURI())
              .getParameter(SipParamConstants.CALLTYPE);
      if (parameter == null) return false;
      return parameter.equals(SipParamConstants.DIAL_IN_TAG);
    };
  }
}
