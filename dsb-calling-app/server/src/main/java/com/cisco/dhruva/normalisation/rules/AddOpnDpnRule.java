package com.cisco.dhruva.normalisation.rules;

import com.cisco.dhruva.application.constants.SipParamConstants;
import com.cisco.dhruva.normalisation.apis.UriNormalisationImpl;
import com.cisco.dsb.proxy.messaging.ProxySIPRequest;
import com.google.common.collect.ImmutableMap;
import gov.nist.javax.sip.address.SipUri;
import gov.nist.javax.sip.message.SIPRequest;
import java.text.ParseException;
import javax.sip.message.Request;
import lombok.CustomLog;
import org.jeasy.rules.annotation.Action;
import org.jeasy.rules.annotation.Condition;
import org.jeasy.rules.annotation.Fact;
import org.jeasy.rules.annotation.Rule;
import org.jeasy.rules.api.Facts;

@CustomLog
@Rule(
    name = "AddOpnDpnRule",
    description = "Normalisation policy that adds opn,dpn params to reqUri")
public class AddOpnDpnRule {

  private static ImmutableMap<String, String> paramsToBeAdded =
      ImmutableMap.of(
          SipParamConstants.X_CISCO_DPN,
          SipParamConstants.X_CISCO_DPN_VALUE,
          SipParamConstants.X_CISCO_OPN,
          SipParamConstants.X_CISCO_OPN_VALUE);

  @Condition
  public boolean isInvite(@Fact("proxyRequest") ProxySIPRequest fact) {
    SIPRequest request = fact.getRequest();
    return request.getMethod().equals(Request.INVITE);
  }

  @Action
  public void addOpnDpnParams(Facts facts) throws ParseException {
    logger.debug("Adding opn, dpn params to reqUri");
    ProxySIPRequest proxySIPRequest = facts.get("proxyRequest");
    SIPRequest request = proxySIPRequest.getRequest();
    SipUri reqUri = (SipUri) request.getRequestURI();

    logger.debug("R-URI before modification: {}", reqUri);
    UriNormalisationImpl uriNorm = new UriNormalisationImpl(reqUri);
    reqUri = uriNorm.setParams(paramsToBeAdded).getUri();
    logger.debug("R-URI after modification: {}", reqUri);
  }
}
