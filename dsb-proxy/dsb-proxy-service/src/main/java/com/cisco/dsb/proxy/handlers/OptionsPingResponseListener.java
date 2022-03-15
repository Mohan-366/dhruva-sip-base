package com.cisco.dsb.proxy.handlers;

import javax.sip.ResponseEvent;

public interface OptionsPingResponseListener {

  public void processResponse(ResponseEvent responseEvent);
}
