package com.cisco.dhruva.callingIntegration;

import com.cisco.dhruva.client.DsbClientFactory;
import com.cisco.wx2.test.TestProperties;
import com.google.common.base.Preconditions;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.net.URI;

@Configuration
public class DhruvaTestConfig {

  @Bean
  public DhruvaCallingTestProperties dhruvaCallingTestProperties() {
    return new DhruvaCallingTestProperties();
  }

  @Bean
  public DsbClientFactory dsbClientFactory(
      TestProperties testProperties, DhruvaCallingTestProperties dhruvaCallingTestProperties) {
    Preconditions.checkNotNull(testProperties);

    URI dhruvaPublicUri = URI.create(dhruvaCallingTestProperties.getDhruvaPublicUrl());
    return DsbClientFactory.builder(testProperties, dhruvaPublicUri).build();
  }
}
