package com.cisco.dhruva.sip.proxy.handlers;

import com.cisco.dhruva.ProxyService;
import com.cisco.dhruva.sip.proxy.SipProxyManager;
import com.cisco.dsb.common.context.ExecutionContext;
import com.cisco.dsb.common.messaging.MessageConvertor;
import com.cisco.dsb.common.messaging.ProxySIPRequest;
import com.cisco.dsb.exception.DhruvaException;
import com.cisco.dsb.util.SpringApplicationContext;
import javax.sip.RequestEvent;
import javax.sip.SipProvider;
import org.springframework.context.ApplicationContext;
import reactor.core.publisher.Mono;

public class SipRequestHandler extends RequestEventHandler {
  SipProxyManager sipProxyManager;

  public SipRequestHandler(ProxyService proxyStack, RequestEvent requestEvent) {
    super(proxyStack, requestEvent);
    ApplicationContext applicationContext = SpringApplicationContext.getAppContext();
    if (applicationContext == null) throw new DhruvaException("spring app context null");
    sipProxyManager = applicationContext.getBean(SipProxyManager.class);
  }

  @Override
  public void executeRun() throws Exception {
    // Validate request, transform request, Invoke Controller
    ProxyService.proxyRequestHandler.accept(Mono.just(requestEvent));

    ProxySIPRequest proxySIPRequest =
        MessageConvertor.convertJainSipRequestMessageToDhruvaMessage(
            this.receivedRequest,
            (SipProvider) requestEvent.getSource(),
            requestEvent.getServerTransaction(),
            new ExecutionContext());

    sipProxyManager.request(proxySIPRequest);
  }
}
