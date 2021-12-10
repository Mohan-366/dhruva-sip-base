package com.cisco.dhruva.application.calltype;

import com.cisco.dhruva.application.exceptions.InvalidCallTypeException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

@Component
public class CallTypeFactory {
  private DialInPSTN dialInPSTN;
  private DialInB2B dialInB2B;
  private DialOutWxC dialOutWxC;
  private DialOutB2B dialOutB2B;

  @Autowired
  public void setDialInPSTN(DialInPSTN dialInPSTN) {
    this.dialInPSTN = dialInPSTN;
  }

  @Autowired
  public void setDialInB2B(DialInB2B dialInB2B) {
    this.dialInB2B = dialInB2B;
  }

  @Autowired
  public void setDialOutWxC(DialOutWxC dialOutWxC) {
    this.dialOutWxC = dialOutWxC;
  }

  @Autowired
  public void setDialOutB2B(DialOutB2B dialOutB2B) {
    this.dialOutB2B = dialOutB2B;
  }

  public CallType getCallType(CallTypeEnum callTypeEnum) throws InvalidCallTypeException {
    switch (callTypeEnum) {
      case DIAL_IN_PSTN:
        return dialInPSTN;
      case DIAL_IN_B2B:
        return dialInB2B;
      case DIAL_OUT_WXC:
        return dialOutWxC;
      case DIAL_OUT_B2B:
        return dialOutB2B;
      default:
        throw new InvalidCallTypeException();
    }
  }
}
