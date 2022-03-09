package com.cisco.dsb.proxy.sip;

import javax.sip.RequestEvent;
import javax.sip.ResponseEvent;
import javax.sip.TimeoutEvent;
import javax.sip.TransactionTerminatedEvent;

public interface ProxyEventListener {
  void request(RequestEvent requestMessage);

  void response(ResponseEvent responseMessage);

  void timeOut(TimeoutEvent timeoutEvent);

  void transactionTerminated(TransactionTerminatedEvent transactionTerminatedEvent);
}
