package com.cisco.dsb.proxy.sip;

import com.cisco.dsb.common.util.log.DhruvaLoggerFactory;
import com.cisco.dsb.common.util.log.Logger;
import javax.sip.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

@Component
public class ProxyPacketProcessor implements SipListener {

  @Autowired ProxyEventListener proxyEventListener;
  Logger logger = DhruvaLoggerFactory.getLogger(ProxyPacketProcessor.class);

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
        "received IO exception event from sip stack for host {} port {} transport {}",
        ioExceptionEvent.getHost(),
        ioExceptionEvent.getPort(),
        ioExceptionEvent.getTransport());
  }

  @Override
  public void processTransactionTerminated(TransactionTerminatedEvent transactionTerminatedEvent) {
    logger.info("received transaction terminated event from sip stack");
    /*proxyEventListener.transactionTerminated(transactionTerminatedEvent);*/
  }

  @Override
  public void processDialogTerminated(DialogTerminatedEvent dialogTerminatedEvent) {
    logger.info("received dialog terminated event from sip stack");
  }
}
