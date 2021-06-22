package com.cisco.dhruva.sip.controller;

import com.cisco.dhruva.ProxyService;
import com.cisco.dhruva.sip.proxy.sinks.DhruvaSink;
import com.cisco.dsb.common.messaging.DSIPRequestMessage;
import com.cisco.dsb.common.messaging.DSIPResponseMessage;
import com.cisco.dsb.exception.DhruvaException;
import com.cisco.dsb.util.log.DhruvaLoggerFactory;
import com.cisco.dsb.util.log.Logger;
import org.springframework.stereotype.Component;

@Component
public class ProxyAppAdaptor implements AppAdaptorInterface {
  Logger logger = DhruvaLoggerFactory.getLogger(ProxyService.class);
  //TODO get ref for request and reponse sink wrapper class
  @Override
  public void handleRequest(DSIPRequestMessage requestMessage) throws DhruvaException {
    DhruvaSink.requestSink.tryEmitNext(requestMessage);
  }

  @Override
  public void handleResponse(DSIPResponseMessage responseMessage) throws DhruvaException {
    logger.info("Sending response to App -callid {}",responseMessage.getCallId());
    DhruvaSink.responseSink.tryEmitNext(responseMessage);
  }
}
