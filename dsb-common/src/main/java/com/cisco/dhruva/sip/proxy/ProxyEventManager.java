package com.cisco.dhruva.sip.proxy;

import com.cisco.dhruva.ProxyService;
import com.cisco.dhruva.sip.proxy.handlers.ProxyEventHandler;
import com.cisco.dhruva.sip.proxy.handlers.SipRequestHandler;
import com.cisco.dhruva.sip.proxy.handlers.SipResponseHandler;
import com.cisco.dsb.config.sip.DhruvaSIPConfigProperties;
import com.cisco.dsb.util.log.DhruvaLoggerFactory;
import com.cisco.dsb.util.log.Logger;
import com.cisco.wx2.util.stripedexecutor.StripedExecutorService;
import javax.inject.Inject;
import javax.sip.RequestEvent;
import javax.sip.ResponseEvent;
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

  @Override
  public void request(RequestEvent requestMessage) {
    startProcessing(new SipRequestHandler(proxyService, requestMessage));
  }

  @Override
  public void response(ResponseEvent responseMessage) {
    startProcessing(new SipResponseHandler(proxyService, responseMessage));
  }

  private void startProcessing(ProxyEventHandler proxyEventHandler) {
    executor.submit(proxyEventHandler);
  }
}
