package com.cisco.dsb.proxy.sip;

import com.cisco.dsb.common.service.ProxyService;
import com.cisco.dsb.proxy.handlers.ProxyEventHandler;
import com.cisco.dsb.proxy.handlers.SipRequestHandler;
import com.cisco.dsb.proxy.handlers.SipResponseHandler;
import com.cisco.dsb.proxy.handlers.SipTimeOutHandler;
import com.cisco.wx2.util.stripedexecutor.StripedExecutorService;
import javax.inject.Inject;
import javax.sip.*;
import lombok.CustomLog;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

@Component
@CustomLog
public class ProxyEventManager implements ProxyEventListener {

  /**
   * Thread pool executor for executing the request/response events. By processing in a thread, jain
   * sip stack thread get unblocked.
   */
  @Inject private StripedExecutorService executor;

  @Autowired ProxyService proxyService;

  // TODO DSB
  // Verify the total number min and max stripped threads allocated to the pool
  @Override
  public void request(RequestEvent requestMessage) {
    logger.debug("received request event, start processing the request in stripped executor");
    startProcessing(new SipRequestHandler(proxyService, requestMessage));
  }

  @Override
  public void response(ResponseEvent responseMessage) {
    logger.debug("received response event, start processing the request in stripped executor");
    startProcessing(new SipResponseHandler(proxyService, responseMessage));
  }

  @Override
  public void timeOut(TimeoutEvent timeoutEvent) {
    logger.debug("received time out event, start processing the request in stripped executor");
    startProcessing(new SipTimeOutHandler(proxyService, timeoutEvent));
  }

  @Override
  public void transactionTerminated(TransactionTerminatedEvent transactionTerminatedEvent) {
    /*if (!transactionTerminatedEvent.isServerTransaction()) {
      ClientTransaction clientTx = transactionTerminatedEvent.getClientTransaction();

      String method = clientTx.getRequest().getMethod();
      logger.info("Server Tx : " + method + " terminated ");
    }*/
  }

  private void startProcessing(ProxyEventHandler proxyEventHandler) {
    executor.submit(proxyEventHandler);
  }
}
