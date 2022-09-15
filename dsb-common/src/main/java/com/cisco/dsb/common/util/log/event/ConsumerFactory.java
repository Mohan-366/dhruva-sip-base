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

  public EventConsumer getLoggerConsumer(boolean allowUnmasked) {
    return new LoggerConsumer(allowUnmasked);
  }
}
