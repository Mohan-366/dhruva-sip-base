package com.cisco.dhruva.application.filters;

import com.cisco.dhruva.application.CallingAppConfigurationProperty;
import com.cisco.dsb.proxy.messaging.ProxySIPRequest;
import com.cisco.dsb.trunk.util.SipParamConstants;
import gov.nist.javax.sip.address.SipUri;
import java.util.function.Predicate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

@Component
public class CallTypeDialInTagOrMidDialogDialIn extends FilterNode {
  private CallingAppConfigurationProperty configurationProperty;

  @Autowired
  public void setConfigurationProperty(
      CallingAppConfigurationProperty callingAppConfigurationProperty) {
    this.configurationProperty = callingAppConfigurationProperty;
  }

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
      return proxySIPRequest
          .getOutgoingNetwork()
          .equals(configurationProperty.getNetworkCallingCore());
    }
    return false;
  }
}
