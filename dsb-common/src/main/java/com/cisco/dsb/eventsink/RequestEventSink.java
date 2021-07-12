package com.cisco.dsb.eventsink;

import java.util.function.Consumer;
import javax.sip.RequestEvent;
import org.springframework.stereotype.Component;
import reactor.core.publisher.FluxSink;

@Component
public class RequestEventSink implements Consumer<FluxSink<RequestEvent>> {
  private FluxSink<RequestEvent> requestSink;

  @Override
  public void accept(FluxSink<RequestEvent> requestEventFluxSink) {
    requestSink = requestEventFluxSink;
  }

  public void emit(RequestEvent requestEvent) {
    requestSink.next(requestEvent);
  }
}
