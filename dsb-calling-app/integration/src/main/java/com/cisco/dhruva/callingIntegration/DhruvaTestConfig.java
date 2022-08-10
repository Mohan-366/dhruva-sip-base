package com.cisco.dhruva.callingIntegration;

import com.cisco.dhruva.client.CallingAppClientFactory;
import com.cisco.wx2.test.TestProperties;
import com.google.common.base.Preconditions;
import java.net.URI;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class DhruvaTestConfig {

  @Bean
  public CallingTestProperties callingTestProperties() {
    return new CallingTestProperties();
  }

  @Bean
  public CallingAppClientFactory callingAppClientFactory(
      TestProperties testProperties, CallingTestProperties callingTestProperties) {
    Preconditions.checkNotNull(testProperties);

    URI dhruvaPublicUri = URI.create(callingTestProperties.getDhruvaPublicUrl());
    return CallingAppClientFactory.builder(testProperties, dhruvaPublicUri).build();
  }
}
