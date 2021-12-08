package com.cisco.dhruva.normalisation.rules;

import com.cisco.dhruva.normalisation.apis.UriNormalisationImpl;
import com.cisco.dsb.proxy.messaging.ProxySIPRequest;
import com.cisco.dsb.trunk.util.SipParamConstants;
import com.google.common.collect.ImmutableList;
import gov.nist.javax.sip.address.SipUri;
import gov.nist.javax.sip.message.SIPRequest;
import javax.sip.message.Request;
import lombok.CustomLog;
import org.jeasy.rules.annotation.Action;
import org.jeasy.rules.annotation.Condition;
import org.jeasy.rules.annotation.Fact;
import org.jeasy.rules.annotation.Rule;
import org.jeasy.rules.api.Facts;

@CustomLog
@Rule(
    name = "RemoveOpnDpnCallTypeRule",
    description = "Normalisation policy that removes opn,dpn params to reqUri")
public class RemoveOpnDpnCallTypeRule {

  private static ImmutableList<String> paramsToBeRemoved =
      ImmutableList.of(
          SipParamConstants.X_CISCO_OPN, SipParamConstants.X_CISCO_DPN, SipParamConstants.CALLTYPE);

  @Condition
  public boolean isInvite(@Fact("proxyRequest") ProxySIPRequest fact) {
    SIPRequest request = fact.getRequest();
    return request.getMethod().equals(Request.INVITE);
  }

  @Action
  public void removeOpnDpnCallTypeParams(Facts facts) {
    logger.debug("Removing opn,dpn,callType params from reqUri");

    ProxySIPRequest proxySIPRequest = facts.get("proxyRequest");
    SIPRequest request = proxySIPRequest.getRequest();
    SipUri reqUri = (SipUri) request.getRequestURI();

    logger.debug("R-URI before modification: {}", reqUri);
    UriNormalisationImpl uriNorm = new UriNormalisationImpl(reqUri);
    reqUri = uriNorm.removeParams(paramsToBeRemoved).getUri();
    logger.debug("R-URI after modification: {}", reqUri);
  }
}
