package com.cisco.dhruva.sip.proxy;

import javax.sip.RequestEvent;
import javax.sip.ResponseEvent;

// TODO this is message hand-off to app layer. We have to send msg to request and response sinks
public interface ProxyEventListener {
  void request(RequestEvent requestMessage);

  void response(ResponseEvent responseMessage);
}
