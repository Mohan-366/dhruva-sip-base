package com.cisco.dhruva.sip.proxy.handlers;

import com.cisco.dhruva.ProxyService;
import com.cisco.dhruva.sip.proxy.SipProxyManager;
import com.cisco.dsb.common.context.ExecutionContext;
import com.cisco.dsb.common.messaging.DSIPResponseMessage;
import com.cisco.dsb.common.messaging.MessageConvertor;
import com.cisco.dsb.exception.DhruvaException;
import com.cisco.dsb.util.SpringApplicationContext;
import javax.sip.ResponseEvent;
import javax.sip.SipProvider;
import org.springframework.context.ApplicationContext;

public class SipResponseHandler extends ResponseEventHandler {
  SipProxyManager sipProxyManager;

  public SipResponseHandler(ProxyService proxyStack, ResponseEvent responseEvent) {
    super(proxyStack, responseEvent);
    ApplicationContext applicationContext = SpringApplicationContext.getAppContext();
    if (applicationContext == null) throw new DhruvaException("spring app context null");
    sipProxyManager = applicationContext.getBean(SipProxyManager.class);
  }

  @Override
  public void executeRun() throws Exception {
    DSIPResponseMessage dsipResponseMessage =
        (DSIPResponseMessage)
            MessageConvertor.convertJainSipResponseMessageToDhruvaMessage(
                this.response,
                (SipProvider) responseEvent.getSource(),
                responseEvent.getClientTransaction(),
                new ExecutionContext());

    sipProxyManager.response(dsipResponseMessage);
  }
}
