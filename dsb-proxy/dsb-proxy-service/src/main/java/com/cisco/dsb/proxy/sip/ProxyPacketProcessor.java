package com.cisco.dsb.proxy.sip;

import com.cisco.dsb.proxy.handlers.OptionsPingResponseListener;
import gov.nist.javax.sip.DialogTimeoutEvent;
import gov.nist.javax.sip.IOExceptionEventExt;
import gov.nist.javax.sip.IOExceptionEventExt.Reason;
import gov.nist.javax.sip.SipListenerExt;
import gov.nist.javax.sip.message.SIPResponse;
import javax.sip.*;
import javax.sip.message.Request;
import lombok.CustomLog;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

@Component
@CustomLog
public class ProxyPacketProcessor implements SipListenerExt {

  @Autowired ProxyEventListener proxyEventListener;
  private OptionsPingResponseListener optionsPingResponseListener;

  @Override
  public void processRequest(RequestEvent requestEvent) {
    logger.debug("received request event from sip stack");
    proxyEventListener.request(requestEvent);
  }

  @Override
  public void processResponse(ResponseEvent responseEvent) {
    if (((SIPResponse) responseEvent.getResponse())
        .getCSeq()
        .getMethod()
        .equalsIgnoreCase("OPTIONS")) {
      logger.debug("OPTIONS Response received: {}", responseEvent.getResponse());
      if (optionsPingResponseListener == null) {
        logger.error("No listener registered for OPTIONS Ping Response. Dropping it.");
        return;
      }
      optionsPingResponseListener.processResponse(responseEvent);
    } else {
      logger.debug("received response event from sip stack");
      proxyEventListener.response(responseEvent);
    }
  }

  @Override
  public void processTimeout(TimeoutEvent timeoutEvent) {
    logger.error("received timeout event from sip stack");
    if (!timeoutEvent.isServerTransaction()
        && timeoutEvent.getClientTransaction().getRequest().getMethod().equals(Request.OPTIONS)) {
      if (optionsPingResponseListener == null) {
        logger.error("No listener registered for OPTIONS Ping Response. Dropping it.");
        return;
      }
      optionsPingResponseListener.processTimeout(timeoutEvent);
      return;
    }
    proxyEventListener.timeOut(timeoutEvent);
  }

  @Override
  public void processIOException(IOExceptionEvent ioExceptionEvent) {
    logger.error(
        "received IO exception event from sip stack for host {} port {} transport {}",
        ioExceptionEvent.getHost(),
        ioExceptionEvent.getPort(),
        ioExceptionEvent.getTransport());
    boolean keepAliveTimeoutFired =
        (ioExceptionEvent instanceof IOExceptionEventExt
            && ((IOExceptionEventExt) ioExceptionEvent).getReason() == Reason.KeepAliveTimeout);
    if (keepAliveTimeoutFired) {
      logger.error(
          "KeepAlive Time out reason : {} ", ((IOExceptionEventExt) ioExceptionEvent).getReason());
    }
  }

  @Override
  public void processTransactionTerminated(TransactionTerminatedEvent transactionTerminatedEvent) {
    logger.debug("received transaction terminated event from sip stack");
    proxyEventListener.transactionTerminated(transactionTerminatedEvent);
  }

  @Override
  public void processDialogTerminated(DialogTerminatedEvent dialogTerminatedEvent) {
    logger.debug("received dialog terminated event from sip stack");
  }

  @Override
  public void processDialogTimeout(DialogTimeoutEvent timeoutEvent) {
    logger.debug("received dialog timeout event from sip stack");
  }

  public void registerOptionsListener(OptionsPingResponseListener listener) {
    this.optionsPingResponseListener = listener;
  }
}
