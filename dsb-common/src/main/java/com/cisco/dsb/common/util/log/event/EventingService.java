package com.cisco.dsb.common.util.log.event;

import java.util.List;
import lombok.CustomLog;
import reactor.core.publisher.Flux;

@CustomLog
public class EventingService {

  private boolean allowUnmaskedEvents;
  private List<Class<? extends DhruvaEvent>> interestedEvents;

  public EventingService(boolean allowUnmaskedEvents) {
    this.allowUnmaskedEvents = allowUnmaskedEvents;
  }

  public void register(List<Class<? extends DhruvaEvent>> interestedEvents) {
    this.interestedEvents = interestedEvents;
  }

  public Flux<DhruvaEvent> getLoggingEventFlux(List<DhruvaEvent> events) {
    return Flux.fromIterable(events)
        .filter(event -> event.getClass() == LoggingEvent.class)
        .doOnNext(
            event -> {
              if (interestedEvents != null && interestedEvents.contains(LoggingEvent.class)) {
                logger.debug("Start publishing logging events");
                ConsumerFactory.getConsumerFactory()
                    .getLoggerConsumer(event, allowUnmaskedEvents)
                    .handleEvent();
              }
            });
  }

  /*public Flux<DhruvaEvent> getMatsEventFlux(List<DhruvaEvent> events) {
    return Flux.fromIterable(events)
            .filter(event -> event.getClass() == MatsEvent.class)
            .doOnNext(event -> {
                if (interestedEvents != null && interestedEvents.contains(MatsEvent.class)) {
                    logger.debug("Start publishing mats events");
                    ConsumerFactory.getConsumerFactory().getMatsConsumer(event).handleEvent();
                }
            });
  }*/

  public void publishEvents(List<DhruvaEvent> events) {
    getLoggingEventFlux(events).subscribe();
    /*getMatsEventFlux(events).subscribe();*/
  }
}
