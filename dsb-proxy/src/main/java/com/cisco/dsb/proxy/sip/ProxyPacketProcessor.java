package com.cisco.dsb.proxy.sip;

import gov.nist.javax.sip.DialogTimeoutEvent;
import gov.nist.javax.sip.IOExceptionEventExt;
import gov.nist.javax.sip.IOExceptionEventExt.Reason;
import gov.nist.javax.sip.SipListenerExt;
import javax.sip.*;
import lombok.CustomLog;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

@Component
@CustomLog
public class ProxyPacketProcessor implements SipListenerExt {

  @Autowired ProxyEventListener proxyEventListener;

  @Override
  public void processRequest(RequestEvent requestEvent) {
    logger.debug("received request event from sip stack");
    proxyEventListener.request(requestEvent);
  }

  @Override
  public void processResponse(ResponseEvent responseEvent) {
    logger.debug("received response event from sip stack");
    proxyEventListener.response(responseEvent);
  }

  @Override
  public void processTimeout(TimeoutEvent timeoutEvent) {
    logger.info("received timeout event from sip stack");
    proxyEventListener.timeOut(timeoutEvent);
  }

  @Override
  public void processIOException(IOExceptionEvent ioExceptionEvent) {
    logger.info(
        "KALPA: received IO exception event from sip stack for host {} port {} transport {}",
        ioExceptionEvent.getHost(),
        ioExceptionEvent.getPort(),
        ioExceptionEvent.getTransport());
    boolean keepAliveTimeoutFired =
        (ioExceptionEvent instanceof IOExceptionEventExt
            && ((IOExceptionEventExt) ioExceptionEvent).getReason() == Reason.KeepAliveTimeout);

    logger.info(
        "KALPA: KeepAlive Time out {} reason : {} ",
        keepAliveTimeoutFired,
        ((IOExceptionEventExt) ioExceptionEvent).getReason());
  }

  @Override
  public void processTransactionTerminated(TransactionTerminatedEvent transactionTerminatedEvent) {
    if (transactionTerminatedEvent.isServerTransaction()) {
      ServerTransaction trans = transactionTerminatedEvent.getServerTransaction();
      logger.info(trans.getRequest().getRequestURI().toString());
    }
    logger.info("received transaction terminated event from sip stack");
    proxyEventListener.transactionTerminated(transactionTerminatedEvent);
  }

  @Override
  public void processDialogTerminated(DialogTerminatedEvent dialogTerminatedEvent) {
    logger.info("received dialog terminated event from sip stack");
  }

  @Override
  public void processDialogTimeout(DialogTimeoutEvent timeoutEvent) {
    logger.info("received dialog timeout event from sip stack");
  }
}
