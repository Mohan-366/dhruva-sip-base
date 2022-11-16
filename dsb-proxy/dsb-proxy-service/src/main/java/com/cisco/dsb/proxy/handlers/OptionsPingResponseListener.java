package com.cisco.dsb.proxy.handlers;

import javax.sip.ResponseEvent;
import javax.sip.TimeoutEvent;

public interface OptionsPingResponseListener {

  public void processResponse(ResponseEvent responseEvent);

  public void processTimeout(TimeoutEvent timeoutEvent);
}
