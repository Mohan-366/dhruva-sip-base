package com.cisco.dhruva.sip.controller;


import com.cisco.dhruva.ProxyService;
import com.cisco.dhruva.sip.proxy.sinks.DhruvaSink;
import com.cisco.dsb.common.CommonContext;
import com.cisco.dsb.common.messaging.DSIPMessage;
import com.cisco.dsb.common.messaging.DSIPRequestMessage;
import com.cisco.dsb.common.messaging.DSIPResponseMessage;
import com.cisco.dsb.exception.DhruvaException;
import com.cisco.dsb.util.log.DhruvaLoggerFactory;
import com.cisco.dsb.util.log.Logger;
import java.util.Objects;
import java.util.function.Consumer;
import org.springframework.stereotype.Component;

@Component
public class ProxyAppAdaptor implements AppAdaptorInterface {
    Logger logger = DhruvaLoggerFactory.getLogger(ProxyAppAdaptor.class);
  //TODO get ref for request and reponse sink wrapper class
  @Override
  public void handleRequest(DSIPRequestMessage requestMessage) throws DhruvaException {
      /*Objects.requireNonNull(requestMessage);
      Objects.requireNonNull(requestMessage.getContext());
      requestMessage.getContext().set(CommonContext.PROXY_CONSUMER, handleMessageFromApp);*/
    DhruvaSink.requestSink.tryEmitNext(requestMessage);
  }

  @Override
  public void handleResponse(DSIPResponseMessage responseMessage) throws DhruvaException {
    logger.info("Sending response to App -callid {}",responseMessage.getCallId());
    DhruvaSink.responseSink.tryEmitNext(responseMessage);
      /*Consumer<DSIPMessage> handleMessageFromApp =
              (message) -> {
                  Objects.requireNonNull(message);
                  logger.info("onResponse: invoking message handler for message {}", message.getCallId());
                  AppMessageListener handler =
                          (AppMessageListener) message.getContext().get(CommonContext.APP_MESSAGE_HANDLER);
                  handler.onMessage(message);
              };*/
  }

}
