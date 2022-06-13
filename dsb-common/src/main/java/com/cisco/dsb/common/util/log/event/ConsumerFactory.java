package com.cisco.dsb.common.util.log.event;

import lombok.Setter;

public class ConsumerFactory {

  @Setter private static ConsumerFactory consumerFactory;

  public static ConsumerFactory getConsumerFactory() {
    if (consumerFactory == null) {
      consumerFactory = new ConsumerFactory();
    }
    return consumerFactory;
  }

  public EventConsumer getLoggerConsumer(DhruvaEvent event, boolean allowUnmasked) {
    return new LoggerConsumer(event, allowUnmasked);
  }

  /*public EventConsumer getMatsConsumer(DhruvaEvent event) {
    return new MatsConsumer(event);
  }*/
}
