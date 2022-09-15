package com.cisco.dsb.common.util.log.event;

import java.util.List;
import java.util.function.Supplier;
import lombok.CustomLog;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Sinks;

@CustomLog
public class EventingService {

  private final boolean allowUnmaskedEvents;
  private List<Class<? extends DhruvaEvent>> interestedEvents;
  private final Sinks.Many<DhruvaEvent> sink;
  private final Flux<DhruvaEvent> flux;

  public EventingService(boolean allowUnmaskedEvents) {
    this.allowUnmaskedEvents = allowUnmaskedEvents;
    this.sink = Sinks.many().multicast().onBackpressureBuffer();
    this.flux = this.sink.asFlux();
    registerSubscription();
  }

  public void register(List<Class<? extends DhruvaEvent>> interestedEvents) {
    this.interestedEvents = interestedEvents;
  }

  private void registerSubscription() {
    this.flux
        .filter(
            event ->
                event.getClass() == LoggingEvent.class
                    && interestedEvents != null
                    && interestedEvents.contains(LoggingEvent.class))
        .subscribe(
            event -> {
              logger.debug("Start publishing logging events");
              consumerFactory().get().getLoggerConsumer(allowUnmaskedEvents).handleEvent(event);
            });
  }

  private Supplier<ConsumerFactory> consumerFactory() {
    return ConsumerFactory::getConsumerFactory;
  }

  public void publishEvents(List<DhruvaEvent> events) {
    events.forEach(this.sink::tryEmitNext);
  }
}
