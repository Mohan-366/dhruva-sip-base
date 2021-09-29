package com.cisco.dhruva.application.calltype;

import com.cisco.dhruva.application.constants.SipParamConstants;
import com.cisco.dhruva.normalisation.RuleListenerImpl;
import com.cisco.dsb.common.exception.DhruvaRuntimeException;
import com.cisco.dsb.common.exception.ErrorCode;
import com.cisco.dsb.proxy.messaging.ProxySIPRequest;
import gov.nist.javax.sip.address.SipUri;
import java.text.ParseException;
import java.util.function.Function;
import lombok.CustomLog;

@CustomLog
public class DialInPSTN extends ToB2B {

  public DialInPSTN(RuleListenerImpl ruleListener, Object rule) {
    super(ruleListener, rule);
  }

  @Override
  protected Function<ProxySIPRequest, ProxySIPRequest> addCallType() {
    return proxySIPRequest -> {
      try {
        ((SipUri) proxySIPRequest.getRequest().getRequestURI())
            .setParameter(SipParamConstants.CALLTYPE, SipParamConstants.DIAL_IN_TAG);
      } catch (ParseException e) {
        throw new DhruvaRuntimeException(ErrorCode.APP_REQ_PROC, e.getMessage(), e);
      }
      logger.info("Adding CallType:DialIn to R-URI");
      return proxySIPRequest;
    };
  }
}
