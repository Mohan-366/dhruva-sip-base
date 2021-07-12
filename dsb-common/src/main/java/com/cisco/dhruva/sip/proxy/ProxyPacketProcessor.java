package com.cisco.dhruva.sip.proxy;

import com.cisco.dsb.eventsink.RequestEventSink;
import com.cisco.dsb.eventsink.ResponseEventSink;
import javax.sip.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

@Component
public class ProxyPacketProcessor implements SipListener {

  @Autowired ProxyEventListener proxyEventListener;

  @Autowired RequestEventSink requestEventSink;

  @Autowired ResponseEventSink responseEventSink;

  @Override
  public void processRequest(RequestEvent requestEvent) {
    proxyEventListener.request(requestEvent);
  }

  @Override
  public void processResponse(ResponseEvent responseEvent) {
    proxyEventListener.response(responseEvent);
  }

  @Override
  public void processTimeout(TimeoutEvent timeoutEvent) {}

  @Override
  public void processIOException(IOExceptionEvent ioExceptionEvent) {}

  @Override
  public void processTransactionTerminated(TransactionTerminatedEvent transactionTerminatedEvent) {}

  @Override
  public void processDialogTerminated(DialogTerminatedEvent dialogTerminatedEvent) {}
}
