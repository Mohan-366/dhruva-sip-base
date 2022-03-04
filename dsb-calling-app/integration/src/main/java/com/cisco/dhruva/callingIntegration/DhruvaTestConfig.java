package com.cisco.dhruva.callingIntegration;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class DhruvaTestConfig {

  @Bean
  public DhruvaCallingTestProperties dhruvaCallingTestProperties() {
    return new DhruvaCallingTestProperties();
  }
}
