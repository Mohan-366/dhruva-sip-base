package com.cisco.dsb.sip.proxy;

import com.cisco.dsb.service.ProxyService;
import com.cisco.dsb.sip.proxy.handlers.ProxyEventHandler;
import com.cisco.dsb.sip.proxy.handlers.SipRequestHandler;
import com.cisco.dsb.sip.proxy.handlers.SipResponseHandler;
import com.cisco.dsb.sip.proxy.handlers.SipTimeOutHandler;
import com.cisco.dsb.util.log.DhruvaLoggerFactory;
import com.cisco.dsb.util.log.Logger;
import com.cisco.wx2.util.stripedexecutor.StripedExecutorService;
import javax.inject.Inject;
import javax.sip.RequestEvent;
import javax.sip.ResponseEvent;
import javax.sip.TimeoutEvent;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

@Component
public class ProxyEventManager implements ProxyEventListener {

  private final Logger logger = DhruvaLoggerFactory.getLogger(ProxyEventManager.class);

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

  private void startProcessing(ProxyEventHandler proxyEventHandler) {
    executor.submit(proxyEventHandler);
  }
}
