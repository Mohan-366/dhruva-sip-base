package com.cisco.dhruva.application.calltype;

import com.cisco.dhruva.sip.proxy.sinks.DhruvaSink;
import com.cisco.dsb.common.messaging.ProxySIPRequest;
import com.cisco.dsb.common.messaging.ProxySIPResponse;
import com.cisco.dsb.util.log.DhruvaLoggerFactory;
import com.cisco.dsb.util.log.Logger;
import java.util.function.Consumer;
import java.util.function.Predicate;
import javax.annotation.PostConstruct;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Sinks;

// TODO can this be static type??? whole class???
@Component
public class CallType2 implements CallType {
  Logger logger = DhruvaLoggerFactory.getLogger(CallType2.class);
  Sinks.Many<ProxySIPRequest> sinkRequest;
  Sinks.Many<ProxySIPResponse> sinkResponse;
  Flux<ProxySIPRequest> fluxRequest;
  Flux<ProxySIPResponse> fluxResponse;

  @PostConstruct
  public void init() {
    sinkRequest = Sinks.many().unicast().onBackpressureBuffer();
    fluxRequest = sinkRequest.asFlux();
    fluxRequest.subscribe(processRequest());

    sinkResponse = Sinks.many().unicast().onBackpressureBuffer();
    fluxResponse = sinkResponse.asFlux();
    fluxResponse.subscribe(processResponse());
  }

  @Override
  public Predicate<ProxySIPRequest> filter() {
    return dsipMessage -> false;
  }

  @Override
  public Consumer<ProxySIPRequest> processRequest() {
    return dsipMessage -> {
      logger.info(
          "SIP Message received by CallType1, callID {}, forwarding back to proxyLayer",
          dsipMessage.getCallId());
      addCallIdCallTypeMapping().apply(dsipMessage);
      DhruvaSink.routeResultSink.tryEmitNext(dsipMessage);
    };
  }

  @Override
  public Consumer<ProxySIPResponse> processResponse() {
    return proxySIPResponse -> {
      logger.info(
          "SIP Message received by CallType1, callID {}, forwarding back to proxyLayer",
          proxySIPResponse.getCallId());
      DhruvaSink.routeResultSink.tryEmitNext(proxySIPResponse);
    };
  }

  @Override
  public Sinks.Many<ProxySIPRequest> getSinkRequest() {
    return sinkRequest;
  }

  @Override
  public Sinks.Many<ProxySIPResponse> getSinkResponse() {
    return sinkResponse;
  }
}
