package com.cisco.dhruva.sip.proxy;

import javax.sip.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

@Component
public class ProxyPacketProcessor implements SipListener {

  @Autowired ProxyEventListener proxyEventListener;

  @Override
  public void processRequest(RequestEvent requestEvent) {
    proxyEventListener.request(requestEvent);
  }

  @Override
  public void processResponse(ResponseEvent responseEvent) {
    proxyEventListener.response(responseEvent);
  }

  // TODO DSB, have different pipeline
  @Override
  public void processTimeout(TimeoutEvent timeoutEvent) {}

  @Override
  public void processIOException(IOExceptionEvent ioExceptionEvent) {}

  @Override
  public void processTransactionTerminated(TransactionTerminatedEvent transactionTerminatedEvent) {}

  @Override
  public void processDialogTerminated(DialogTerminatedEvent dialogTerminatedEvent) {}
}
