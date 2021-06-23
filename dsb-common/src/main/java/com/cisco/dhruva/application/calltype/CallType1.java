package com.cisco.dhruva.application.calltype;

import com.cisco.dhruva.sip.proxy.sinks.DhruvaSink;
import com.cisco.dsb.common.messaging.DSIPMessage;
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
public class CallType1 implements CallType {
  Logger logger = DhruvaLoggerFactory.getLogger(CallType1.class);
  Sinks.Many<DSIPMessage> sink;
  Flux<DSIPMessage> flux;

  @PostConstruct
  public void init() {
    sink = Sinks.many().unicast().onBackpressureBuffer();
    flux = sink.asFlux();
    flux.subscribe(process());
  }

  @Override
  public Predicate<DSIPMessage> filter() {
    return dsipMessage -> false;
  }

  @Override
  public Consumer<DSIPMessage> process() {
    return dsipMessage -> {
      logger.info(
          "SIP Message received by CallType1, callID {}, forwarding back to proxyLayer",
          dsipMessage.getCallId());
      addCallIdCallTypeMapping().apply(dsipMessage);
      DhruvaSink.routeResultSink.tryEmitNext(dsipMessage);
    };
  }

  @Override
  public Sinks.Many<DSIPMessage> getSink() {

    return sink;
  }
}
