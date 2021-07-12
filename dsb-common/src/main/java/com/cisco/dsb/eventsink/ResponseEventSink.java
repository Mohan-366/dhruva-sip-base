package com.cisco.dsb.eventsink;

import java.util.function.Consumer;
import javax.sip.ResponseEvent;
import org.springframework.stereotype.Component;
import reactor.core.publisher.FluxSink;

@Component
public class ResponseEventSink implements Consumer<FluxSink<ResponseEvent>> {
  private FluxSink<ResponseEvent> responseSink;

  @Override
  public void accept(FluxSink<ResponseEvent> responseEventFluxSink) {
    responseSink = responseEventFluxSink;
  }

  public void emit(ResponseEvent responseEvent) {
    responseSink.next(responseEvent);
  }
}
