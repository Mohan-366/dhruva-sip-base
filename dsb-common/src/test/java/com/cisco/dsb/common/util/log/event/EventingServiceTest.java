package com.cisco.dsb.common.util.log.event;

import static org.mockito.Mockito.*;

import com.google.common.collect.ImmutableList;
import java.util.ArrayList;
import java.util.List;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.Test;

public class EventingServiceTest {

  @AfterMethod
  public void reset() {
    ConsumerFactory.setConsumerFactory(null);
  }

  @Test(
      description =
          "test with no event registration & registration for an event that is not actually published")
  public void testNoEventRegistration() {
    EventingService eventingService = new EventingService(false);
    // register for events
    ImmutableList<Class<? extends DhruvaEvent>> interestedEvents =
        ImmutableList.of(MatsEvent.class);
    eventingService.register(interestedEvents);

    List<DhruvaEvent> events = new ArrayList<>();
    events.add(mock(LoggingEvent.class));

    ConsumerFactory mockConsumerFactory = mock(ConsumerFactory.class);
    ConsumerFactory.setConsumerFactory(mockConsumerFactory);
    LoggerConsumer mockLoggerConsumer = mock(LoggerConsumer.class);

    // publish and verify
    eventingService.publishEvents(events);

    verify(mockConsumerFactory, times(0)).getLoggerConsumer(any(LoggingEvent.class), anyBoolean());
    verify(mockLoggerConsumer, times(0)).handleEvent();

    // publish and verify - no registration
    eventingService.register(null);

    eventingService.publishEvents(events);

    verify(mockConsumerFactory, times(0)).getLoggerConsumer(any(LoggingEvent.class), anyBoolean());
    verify(mockLoggerConsumer, times(0)).handleEvent();
  }

  @Test(
      description =
          "test that registers interest for logging event and "
              + "verifies invocation of appropriate consumer")
  public void testLoggingEvent() {
    EventingService eventingService = new EventingService(false);
    // register for events
    ImmutableList<Class<? extends DhruvaEvent>> interestedEvents =
        ImmutableList.of(LoggingEvent.class);
    eventingService.register(interestedEvents);

    // create events
    LoggingEvent loggingEvent =
        new LoggingEvent.LoggingEventBuilder().eventType(Event.EventType.SIPMESSAGE).build();
    List<DhruvaEvent> events = new ArrayList<>();
    events.add(loggingEvent);

    ConsumerFactory mockConsumerFactory = mock(ConsumerFactory.class);
    ConsumerFactory.setConsumerFactory(mockConsumerFactory);
    LoggerConsumer mockLogConsumer = mock(LoggerConsumer.class);

    when(mockConsumerFactory.getLoggerConsumer(loggingEvent, false)).thenReturn(mockLogConsumer);

    // publish events
    eventingService.publishEvents(events);
    verify(mockLogConsumer).handleEvent();
  }
}
