package com.cisco.dhruva.sip.proxy;

import javax.sip.RequestEvent;
import javax.sip.ResponseEvent;

public interface ProxyEventListener {
  void request(RequestEvent requestMessage);

  void response(ResponseEvent responseMessage);
}
