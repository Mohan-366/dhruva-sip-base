package com.cisco.dhruva.application.calltype;

import com.cisco.dhruva.application.constants.SipParamConstants;
import com.cisco.dhruva.application.exceptions.InvalidCallTypeException;
import com.cisco.dhruva.normalisation.RuleListenerImpl;
import com.cisco.dhruva.normalisation.rules.AddOpnDpnRule;
import com.cisco.dhruva.normalisation.rules.RemoveOpnDpnCallTypeRule;
import org.springframework.stereotype.Component;

@Component
public class CallTypeFactory {
  public CallType getCallType(CallType.CallTypes callTypes) throws InvalidCallTypeException {
    switch (callTypes) {
      case DIAL_IN_PSTN:
        return new DialInPSTN(
            new RuleListenerImpl(),
            new AddOpnDpnRule(SipParamConstants.DPN_IN, SipParamConstants.OPN_IN));
      case DIAL_IN_B2B:
        return new DialInB2B(new RuleListenerImpl(), new RemoveOpnDpnCallTypeRule());
      case DIAL_OUT_WXC:
        return new DialOutWxC(
            new RuleListenerImpl(),
            new AddOpnDpnRule(SipParamConstants.DPN_OUT, SipParamConstants.OPN_OUT));
      case DIAL_OUT_B2B:
        return new DialOutB2B(new RuleListenerImpl(), new RemoveOpnDpnCallTypeRule());
      default:
        throw new InvalidCallTypeException();
    }
  }
}
