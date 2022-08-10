package com.cisco.dhruva.antares.integration;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class DhruvaTestConfig {

  @Bean
  public CallingTestProperties callingTestProperties() {
    return new CallingTestProperties();
  }
}
